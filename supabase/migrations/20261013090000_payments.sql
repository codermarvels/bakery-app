-- =====================================================================================
-- VSJ BAKERY — Stage 4: server-verified payments (Razorpay, test mode first)
--
-- These functions are callable ONLY with the service-role key, which exists only inside
-- Supabase Edge Functions. The app cannot call them, so a phone can never mark an order
-- as paid. An order becomes 'paid' only after the Edge Function has checked Razorpay's
-- signature AND asked Razorpay's own servers that the money was captured.
--
-- When a payment is confirmed, in ONE transaction:
--   payment → captured, order → paid, stock → reduced (one 'sale' ledger row per item).
-- If the order can no longer be fulfilled (cancelled meanwhile, already paid by another
-- payment, or an item sold out), the order is not marked paid and a refund is recorded
-- for the Edge Function to send back through Razorpay.
-- =====================================================================================

-- Stock check shared by prepare_payment and confirm_payment.
-- Returns the first problem as a sentence, or null when everything is available.
create function private.order_stock_problem(p_order_id uuid)
returns text
language sql
stable
security definer
set search_path = ''
as $$
    select case
               when not (p.is_active and c.is_active) then p.name || ' is no longer available'
               when p.stock_quantity = 0 then p.name || ' is sold out'
               else 'Only ' || p.stock_quantity || ' of ' || p.name || ' left'
           end
      from public.order_items oi
      join public.products p on p.id = oi.product_id
      join public.categories c on c.id = p.category_id
     where oi.order_id = p_order_id
       and (not (p.is_active and c.is_active) or p.stock_quantity < oi.quantity)
     order by p.name
     limit 1;
$$;

-- -------------------------------------------------------------------------------------
-- prepare_payment: called before opening the payment screen.
-- Checks the order belongs to the customer, is unpaid and can still be fulfilled.
-- Returns the amount (from the order, never from the phone) and any earlier Razorpay
-- order for the same amount that can be reused.
-- -------------------------------------------------------------------------------------
create function public.prepare_payment(p_order_id uuid, p_customer_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_order   public.orders%rowtype;
    v_problem text;
    v_reuse   text;
begin
    select * into v_order from public.orders where id = p_order_id for update;
    if not found or v_order.customer_id is distinct from p_customer_id then
        raise exception 'Order not found' using errcode = 'P0001';
    end if;
    if v_order.status <> 'pending_payment' then
        raise exception 'Order #% is not awaiting payment', v_order.order_number using errcode = 'P0001';
    end if;
    if v_order.total_paise <= 0 then
        raise exception 'Order total must be greater than zero' using errcode = 'P0001';
    end if;

    v_problem := private.order_stock_problem(p_order_id);
    if v_problem is not null then
        raise exception '%. Please cancel order #% and order again.', v_problem, v_order.order_number
            using errcode = 'P0001';
    end if;

    select provider_order_id into v_reuse
      from public.payments
     where order_id = p_order_id and status = 'created' and amount_paise = v_order.total_paise
     order by created_at desc
     limit 1;

    return jsonb_build_object(
        'order_id', v_order.id,
        'order_number', v_order.order_number,
        'amount_paise', v_order.total_paise,
        'reusable_provider_order_id', v_reuse
    );
end;
$$;

-- -------------------------------------------------------------------------------------
-- record_payment_attempt: stores the Razorpay order created for this order.
-- -------------------------------------------------------------------------------------
create function public.record_payment_attempt(
    p_order_id uuid, p_provider text, p_provider_order_id text, p_amount_paise bigint
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_order public.orders%rowtype;
    v_id    uuid;
begin
    select * into v_order from public.orders where id = p_order_id for update;
    if not found or v_order.status <> 'pending_payment' then
        raise exception 'Order is not awaiting payment' using errcode = 'P0001';
    end if;
    if p_amount_paise is distinct from v_order.total_paise then
        raise exception 'Payment amount does not match the order total' using errcode = 'P0001';
    end if;
    insert into public.payments (order_id, provider, provider_order_id, amount_paise, status)
    values (p_order_id, p_provider, p_provider_order_id, p_amount_paise, 'created')
    returning id into v_id;
    return v_id;
end;
$$;

-- -------------------------------------------------------------------------------------
-- confirm_payment: called only AFTER the Edge Function has verified the payment with
-- Razorpay. Safe to call more than once (the app and the webhook may both report it).
-- -------------------------------------------------------------------------------------
create function public.confirm_payment(
    p_provider_order_id text, p_provider_payment_id text, p_amount_paise bigint
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_payment public.payments%rowtype;
    v_order   public.orders%rowtype;
    v_problem text;
    v_reason  text;
    v_refund  uuid;
begin
    select * into v_payment from public.payments
     where provider_order_id = p_provider_order_id for update;
    if not found then
        raise exception 'Unknown payment order %', p_provider_order_id using errcode = 'P0001';
    end if;

    select * into v_order from public.orders where id = v_payment.order_id for update;

    -- Already handled earlier (by the app or the webhook): report the same outcome again.
    if v_payment.status = 'captured' then
        select id into v_refund from public.refunds where payment_id = v_payment.id limit 1;
        return jsonb_build_object(
            'result', case when v_refund is null then 'already_confirmed' else 'refund_recorded' end,
            'order_id', v_order.id,
            'order_number', v_order.order_number,
            'order_status', v_order.status
        );
    end if;

    if p_amount_paise is distinct from v_payment.amount_paise then
        raise exception 'Captured amount % does not match expected %', p_amount_paise, v_payment.amount_paise
            using errcode = 'P0001';
    end if;

    -- The money has been received, so the payment is recorded as captured either way.
    update public.payments
       set status = 'captured', provider_payment_id = p_provider_payment_id, verified_at = now()
     where id = v_payment.id;

    -- Can the order still be fulfilled?
    if v_order.status = 'cancelled' then
        v_reason := 'Order was cancelled before the payment completed';
    elsif v_order.status <> 'pending_payment' then
        v_reason := 'Order was already paid by another payment';
    elsif v_order.total_paise <> v_payment.amount_paise then
        v_reason := 'Order total changed during payment';
    else
        -- Lock the products so two payments cannot both take the last item.
        perform 1 from public.products p
          join public.order_items oi on oi.product_id = p.id
         where oi.order_id = v_order.id
           for update of p;
        v_problem := private.order_stock_problem(v_order.id);
        if v_problem is not null then
            v_reason := v_problem || ' when the payment completed';
        end if;
    end if;

    if v_reason is not null then
        if v_order.status = 'pending_payment' then
            update public.orders set status = 'cancelled', cancelled_at = now() where id = v_order.id;
        end if;
        insert into public.refunds (order_id, payment_id, amount_paise, reason, status)
        values (v_order.id, v_payment.id, v_payment.amount_paise, left(v_reason, 300), 'pending')
        returning id into v_refund;
        return jsonb_build_object(
            'result', 'refund_required',
            'order_id', v_order.id,
            'order_number', v_order.order_number,
            'refund_id', v_refund,
            'refund_amount_paise', v_payment.amount_paise,
            'provider_payment_id', p_provider_payment_id,
            'reason', v_reason
        );
    end if;

    -- All good: take the items out of stock and mark the order paid.
    insert into public.stock_movements (product_id, movement_type, quantity_delta, reason, order_id, created_by)
    select oi.product_id, 'sale', -oi.quantity, 'Order #' || v_order.order_number, v_order.id, null
      from public.order_items oi
     where oi.order_id = v_order.id;

    update public.orders set status = 'paid', paid_at = now() where id = v_order.id;

    return jsonb_build_object(
        'result', 'paid',
        'order_id', v_order.id,
        'order_number', v_order.order_number,
        'order_status', 'paid'
    );
end;
$$;

-- -------------------------------------------------------------------------------------
-- mark_payment_failed: a payment attempt failed or was abandoned (from the webhook).
-- -------------------------------------------------------------------------------------
create function public.mark_payment_failed(p_provider_order_id text)
returns void
language sql
security definer
set search_path = ''
as $$
    update public.payments set status = 'failed'
     where provider_order_id = p_provider_order_id and status = 'created';
$$;

-- -------------------------------------------------------------------------------------
-- complete_refund: records the result of sending a refund through Razorpay.
-- -------------------------------------------------------------------------------------
create function public.complete_refund(p_refund_id uuid, p_provider_refund_id text, p_succeeded boolean)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    update public.refunds
       set status = case when p_succeeded then 'processed' else 'failed' end::public.refund_status,
           provider_refund_id = coalesce(p_provider_refund_id, provider_refund_id),
           processed_at = case when p_succeeded then now() else processed_at end
     where id = p_refund_id and status <> 'processed';
end;
$$;

-- Service role only. The app (anon / authenticated) can never call these.
revoke all on function public.prepare_payment(uuid, uuid) from public, anon, authenticated;
revoke all on function public.record_payment_attempt(uuid, text, text, bigint) from public, anon, authenticated;
revoke all on function public.confirm_payment(text, text, bigint) from public, anon, authenticated;
revoke all on function public.mark_payment_failed(text) from public, anon, authenticated;
revoke all on function public.complete_refund(uuid, text, boolean) from public, anon, authenticated;
revoke all on function private.order_stock_problem(uuid) from public, anon, authenticated;
grant execute on function public.prepare_payment(uuid, uuid) to service_role;
grant execute on function public.record_payment_attempt(uuid, text, text, bigint) to service_role;
grant execute on function public.confirm_payment(text, text, bigint) to service_role;
grant execute on function public.mark_payment_failed(text) to service_role;
grant execute on function public.complete_refund(uuid, text, boolean) to service_role;

create index payments_status_idx on public.payments (status);
create unique index refunds_one_per_payment on public.refunds (payment_id);
