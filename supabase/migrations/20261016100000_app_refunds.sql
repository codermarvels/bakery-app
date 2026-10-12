-- =====================================================================================
-- VSJ BAKERY — Stage 7: the administrator can refund a paid app order
--
-- Flow (server only): the payments Edge Function checks the caller is the administrator,
-- calls start_app_refund (records the refund as pending, cancels the order and optionally
-- puts the items back in stock), sends the refund through Razorpay, then records the result
-- with complete_refund. A failed or interrupted refund can be retried; the function asks
-- Razorpay first, so a customer is never refunded twice.
--
-- Refunds always go through here (never only in the Razorpay dashboard), so sales reports
-- and stock stay correct.
-- =====================================================================================

create function public.start_app_refund(p_order_id uuid, p_admin_id uuid, p_reason text, p_restock boolean)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_order    public.orders%rowtype;
    v_payment  public.payments%rowtype;
    v_refund   public.refunds%rowtype;
    v_reason   text := left(btrim(coalesce(p_reason, '')), 300);
begin
    -- Checked again here, in addition to the Edge Function.
    if not exists (select 1 from public.profiles where id = p_admin_id and role = 'admin') then
        raise exception 'Only the administrator can refund orders' using errcode = '42501';
    end if;
    if char_length(v_reason) = 0 then
        raise exception 'A reason is required for a refund' using errcode = 'P0001';
    end if;

    select * into v_order from public.orders where id = p_order_id for update;
    if not found or v_order.channel <> 'app' then
        raise exception 'Order not found' using errcode = 'P0001';
    end if;

    select * into v_payment
      from public.payments
     where order_id = p_order_id and status = 'captured' and provider = 'razorpay'
     order by verified_at desc
     limit 1;
    if not found or v_payment.provider_payment_id is null then
        raise exception 'Order #% has no confirmed online payment to refund', v_order.order_number
            using errcode = 'P0001';
    end if;

    select * into v_refund from public.refunds where payment_id = v_payment.id;
    if found then
        if v_refund.status = 'processed' then
            raise exception 'Order #% has already been refunded', v_order.order_number using errcode = 'P0001';
        end if;
        -- Pending or failed earlier: send it again.
        return jsonb_build_object(
            'refund_id', v_refund.id,
            'provider_payment_id', v_payment.provider_payment_id,
            'amount_paise', v_refund.amount_paise,
            'order_number', v_order.order_number,
            'retry', true
        );
    end if;

    if v_order.status not in ('paid', 'ready_for_pickup', 'collected') then
        raise exception 'Only paid orders can be refunded' using errcode = 'P0001';
    end if;

    -- Items not yet handed over can go back on the shelf (the admin decides).
    if p_restock and v_order.status <> 'collected' then
        insert into public.stock_movements (product_id, movement_type, quantity_delta, reason, order_id, created_by)
        select oi.product_id, 'refund_restock', oi.quantity,
               left('Refund order #' || v_order.order_number || ': ' || v_reason, 300),
               p_order_id, p_admin_id
          from public.order_items oi
         where oi.order_id = p_order_id;
    end if;

    update public.orders set status = 'cancelled', cancelled_at = now() where id = p_order_id;

    insert into public.refunds (order_id, payment_id, amount_paise, reason, status, created_by)
    values (p_order_id, v_payment.id, v_payment.amount_paise, v_reason, 'pending', p_admin_id)
    returning * into v_refund;

    return jsonb_build_object(
        'refund_id', v_refund.id,
        'provider_payment_id', v_payment.provider_payment_id,
        'amount_paise', v_refund.amount_paise,
        'order_number', v_order.order_number,
        'retry', false
    );
end;
$$;

revoke all on function public.start_app_refund(uuid, uuid, text, boolean) from public, anon, authenticated;
grant execute on function public.start_app_refund(uuid, uuid, text, boolean) to service_role;
