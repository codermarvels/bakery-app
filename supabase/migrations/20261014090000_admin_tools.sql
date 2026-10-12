-- =====================================================================================
-- VSJ BAKERY — Stage 5: administrator tools
--
--  * Counter sales: walk-in purchases recorded by the administrator. They reduce stock
--    straight away (same stock ledger as app orders) and appear in sales reports with
--    their payment method (cash / UPI to the shop's own account / card machine).
--    These payments are recorded by staff, not verified by a payment gateway, so every
--    counter sale stores WHO recorded it.
--  * Undoing a counter sale entered by mistake (reason required; stock is put back and a
--    refund line is recorded so reports stay correct).
--  * App order progress: paid → ready for pickup → collected.
--  * End-of-day stock count: the counted quantity becomes the stock, and any difference
--    from what the system expected is stored as an "unrecorded difference".
-- All functions are administrator-only; the reports role stays read-only.
-- =====================================================================================

-- ------------------------------------------------------------------------- orders
alter table public.orders
    add column channel text not null default 'app' check (channel in ('app', 'counter')),
    add column recorded_by uuid references auth.users (id) on delete set null;

-- Counter sales have no customer account.
alter table public.orders alter column customer_id drop not null;
alter table public.orders
    add constraint orders_app_orders_have_customer check (channel = 'counter' or customer_id is not null);

create index orders_channel_created_idx on public.orders (channel, created_at);

-- ------------------------------------------------------------------------- stock counts
create table public.stock_counts (
    id          uuid primary key default gen_random_uuid(),
    counted_by  uuid references auth.users (id) on delete set null default auth.uid(),
    note        text check (note is null or char_length(note) <= 300),
    created_at  timestamptz not null default now()
);

alter table public.stock_movements
    add column stock_count_id uuid references public.stock_counts (id) on delete restrict;

alter table public.stock_counts enable row level security;
revoke all on public.stock_counts from anon, authenticated;
grant select on public.stock_counts to authenticated;
grant select on public.stock_counts to service_role;

create policy "stock counts: staff read"
    on public.stock_counts for select to authenticated
    using ((select private.is_staff()));

-- ------------------------------------------------------------------------- helpers
-- Reads [{"product_id": "...", "quantity": n}, ...] into a temporary table, merging
-- duplicate lines. Shared by counter sales.
create function private.load_requested_items(p_items jsonb)
returns void
language plpgsql
set search_path = ''
as $$
begin
    if p_items is null or jsonb_typeof(p_items) <> 'array'
       or jsonb_array_length(p_items) = 0 or jsonb_array_length(p_items) > 50 then
        raise exception 'Add at least one item' using errcode = 'P0001';
    end if;

    create temporary table if not exists pg_temp.requested_items (
        product_id uuid primary key,
        quantity   integer not null
    ) on commit drop;
    truncate pg_temp.requested_items;

    begin
        insert into pg_temp.requested_items (product_id, quantity)
        select (item ->> 'product_id')::uuid, sum((item ->> 'quantity')::integer)
          from jsonb_array_elements(p_items) as item
         group by 1;
    exception when others then
        raise exception 'The sale contains an invalid item' using errcode = 'P0001';
    end;

    if exists (select 1 from pg_temp.requested_items
                where product_id is null or quantity is null or quantity < 1 or quantity > 1000) then
        raise exception 'Each item quantity must be between 1 and 1000' using errcode = 'P0001';
    end if;
end;
$$;
revoke all on function private.load_requested_items(jsonb) from public, anon, authenticated;

-- ------------------------------------------------------------------------- counter sale
create function public.record_counter_sale(p_items jsonb, p_payment_method text, p_note text default null)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_order_id uuid;
    v_number   bigint;
    v_total    bigint := 0;
    v_line     record;
begin
    perform private.require_admin();

    if p_payment_method is null or p_payment_method not in ('cash', 'upi', 'card') then
        raise exception 'Choose how the customer paid: cash, UPI or card' using errcode = 'P0001';
    end if;
    if p_note is not null and char_length(p_note) > 300 then
        raise exception 'Note is too long (300 characters maximum)' using errcode = 'P0001';
    end if;

    perform private.load_requested_items(p_items);

    if exists (select 1 from pg_temp.requested_items r
                where not exists (select 1 from public.products p where p.id = r.product_id)) then
        raise exception 'A product in this sale no longer exists' using errcode = 'P0001';
    end if;

    -- Lock the products so an app payment cannot take the same last items at once.
    for v_line in
        select r.product_id, r.quantity, p.name, p.price_paise, p.stock_quantity
          from pg_temp.requested_items r
          join public.products p on p.id = r.product_id
         order by r.product_id
           for update of p
    loop
        if v_line.stock_quantity < v_line.quantity then
            raise exception 'Only % of % in stock. Record production first if more was made.',
                v_line.stock_quantity, v_line.name using errcode = 'P0001';
        end if;
        v_total := v_total + v_line.price_paise * v_line.quantity;
    end loop;

    if v_total <= 0 then
        raise exception 'Sale total must be greater than zero' using errcode = 'P0001';
    end if;

    insert into public.orders (customer_id, channel, status, total_paise, pickup_note,
                               paid_at, collected_at, recorded_by)
    values (null, 'counter', 'collected', v_total, nullif(btrim(p_note), ''),
            now(), now(), auth.uid())
    returning id, order_number into v_order_id, v_number;

    insert into public.order_items (order_id, product_id, product_name, unit_price_paise, quantity, line_total_paise)
    select v_order_id, p.id, p.name, p.price_paise, r.quantity, p.price_paise * r.quantity
      from pg_temp.requested_items r
      join public.products p on p.id = r.product_id;

    insert into public.stock_movements (product_id, movement_type, quantity_delta, reason, order_id, created_by)
    select r.product_id, 'sale', -r.quantity, 'Counter sale #' || v_number, v_order_id, auth.uid()
      from pg_temp.requested_items r;

    -- Recorded by staff (not gateway-verified); provider shows how the money was taken.
    insert into public.payments (order_id, provider, amount_paise, status, verified_at)
    values (v_order_id, p_payment_method, v_total, 'captured', now());

    return jsonb_build_object('order_id', v_order_id, 'order_number', v_number, 'total_paise', v_total);
end;
$$;

-- ------------------------------------------------------------------------- undo counter sale
create function public.void_counter_sale(p_order_id uuid, p_reason text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_order   public.orders%rowtype;
    v_payment public.payments%rowtype;
begin
    perform private.require_admin();
    if p_reason is null or char_length(btrim(p_reason)) = 0 then
        raise exception 'A reason is required to undo a sale' using errcode = 'P0001';
    end if;

    select * into v_order from public.orders where id = p_order_id for update;
    if not found or v_order.channel <> 'counter' then
        raise exception 'Counter sale not found' using errcode = 'P0001';
    end if;
    if v_order.status = 'cancelled' then
        raise exception 'This sale has already been undone' using errcode = 'P0001';
    end if;

    update public.orders set status = 'cancelled', cancelled_at = now() where id = p_order_id;

    insert into public.stock_movements (product_id, movement_type, quantity_delta, reason, order_id, created_by)
    select oi.product_id, 'refund_restock', oi.quantity,
           left('Undo counter sale #' || v_order.order_number || ': ' || btrim(p_reason), 300),
           p_order_id, auth.uid()
      from public.order_items oi
     where oi.order_id = p_order_id;

    select * into v_payment from public.payments where order_id = p_order_id and status = 'captured' limit 1;
    if found then
        insert into public.refunds (order_id, payment_id, amount_paise, reason, status, created_by, processed_at)
        values (p_order_id, v_payment.id, v_payment.amount_paise, left(btrim(p_reason), 300),
                'processed', auth.uid(), now());
    end if;
end;
$$;

-- ------------------------------------------------------------------------- app order progress
create function public.set_order_status(p_order_id uuid, p_status text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_order public.orders%rowtype;
begin
    perform private.require_admin();

    select * into v_order from public.orders where id = p_order_id for update;
    if not found or v_order.channel <> 'app' then
        raise exception 'Order not found' using errcode = 'P0001';
    end if;

    if p_status = 'ready_for_pickup' and v_order.status = 'paid' then
        update public.orders set status = 'ready_for_pickup', ready_at = now() where id = p_order_id;
    elsif p_status = 'collected' and v_order.status in ('paid', 'ready_for_pickup') then
        update public.orders
           set status = 'collected', collected_at = now(), ready_at = coalesce(ready_at, now())
         where id = p_order_id;
    else
        raise exception 'Order #% is %, so it cannot be marked %',
            v_order.order_number, replace(v_order.status::text, '_', ' '), replace(coalesce(p_status, '?'), '_', ' ')
            using errcode = 'P0001';
    end if;
end;
$$;

-- ------------------------------------------------------------------------- end-of-day count
-- p_counts: [{"product_id": "...", "counted": 7}, ...]. The counted number becomes the
-- stock. Differences are stored as adjustments linked to this count.
create function public.record_stock_count(p_counts jsonb, p_note text default null)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_count_id    uuid;
    v_line        record;
    v_differences jsonb := '[]'::jsonb;
begin
    perform private.require_admin();

    if p_counts is null or jsonb_typeof(p_counts) <> 'array' or jsonb_array_length(p_counts) = 0 then
        raise exception 'Enter at least one counted product' using errcode = 'P0001';
    end if;
    if p_note is not null and char_length(p_note) > 300 then
        raise exception 'Note is too long (300 characters maximum)' using errcode = 'P0001';
    end if;

    create temporary table if not exists pg_temp.counted_items (
        product_id uuid primary key,
        counted    integer not null
    ) on commit drop;
    truncate pg_temp.counted_items;

    begin
        insert into pg_temp.counted_items (product_id, counted)
        select (c ->> 'product_id')::uuid, (c ->> 'counted')::integer
          from jsonb_array_elements(p_counts) as c;
    exception when others then
        raise exception 'Counts must be whole numbers, one per product' using errcode = 'P0001';
    end;

    if exists (select 1 from pg_temp.counted_items where counted is null or counted < 0 or counted > 100000) then
        raise exception 'Counted quantities must be between 0 and 100000' using errcode = 'P0001';
    end if;
    if exists (select 1 from pg_temp.counted_items c
                where not exists (select 1 from public.products p where p.id = c.product_id)) then
        raise exception 'A counted product no longer exists' using errcode = 'P0001';
    end if;

    insert into public.stock_counts (counted_by, note)
    values (auth.uid(), nullif(btrim(p_note), ''))
    returning id into v_count_id;

    for v_line in
        select c.product_id, c.counted, p.name, p.price_paise, p.stock_quantity
          from pg_temp.counted_items c
          join public.products p on p.id = c.product_id
         order by p.name
           for update of p
    loop
        if v_line.counted <> v_line.stock_quantity then
            insert into public.stock_movements
                (product_id, movement_type, quantity_delta, reason, stock_count_id, created_by)
            values (v_line.product_id, 'adjustment', v_line.counted - v_line.stock_quantity,
                    'End-of-day count: unrecorded difference', v_count_id, auth.uid());
            v_differences := v_differences || jsonb_build_object(
                'product_id', v_line.product_id,
                'name', v_line.name,
                'expected', v_line.stock_quantity,
                'counted', v_line.counted,
                'difference', v_line.counted - v_line.stock_quantity,
                'value_paise', (v_line.counted - v_line.stock_quantity)::bigint * v_line.price_paise
            );
        end if;
    end loop;

    return jsonb_build_object('count_id', v_count_id, 'differences', v_differences);
end;
$$;

-- ------------------------------------------------------------------------- privileges
revoke all on function public.record_counter_sale(jsonb, text, text) from public, anon;
revoke all on function public.void_counter_sale(uuid, text) from public, anon;
revoke all on function public.set_order_status(uuid, text) from public, anon;
revoke all on function public.record_stock_count(jsonb, text) from public, anon;
grant execute on function public.record_counter_sale(jsonb, text, text) to authenticated;
grant execute on function public.void_counter_sale(uuid, text) to authenticated;
grant execute on function public.set_order_status(uuid, text) to authenticated;
grant execute on function public.record_stock_count(jsonb, text) to authenticated;
