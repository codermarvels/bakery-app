-- =====================================================================================
-- VSJ BAKERY — one unpaid order per customer
--
-- While a customer has an order that is still awaiting payment, a new checkout ADDS its
-- items to that order instead of creating another one. The server re-checks stock for the
-- combined quantities and re-prices every line at today's prices (nothing has been paid
-- yet, so current prices apply).
--
-- An order that already has a payment attempt is never changed (the amount sent to the
-- payment gateway must not move); in that case a new order is created instead.
-- =====================================================================================

create or replace function public.place_order(p_items jsonb, p_pickup_note text default null)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user      uuid := auth.uid();
    v_order_id  uuid;
    v_number    bigint;
    v_total     bigint := 0;
    v_merged    boolean := false;
    v_line      record;
begin
    if v_user is null then
        raise exception 'Please sign in to place an order' using errcode = '42501';
    end if;
    -- Staff accounts are for running the bakery; the reports role must stay read-only.
    if private.user_role() is distinct from 'customer' then
        raise exception 'Orders can only be placed from a customer account' using errcode = '42501';
    end if;

    if p_items is null or jsonb_typeof(p_items) <> 'array'
       or jsonb_array_length(p_items) = 0 or jsonb_array_length(p_items) > 50 then
        raise exception 'Your cart is empty or too large' using errcode = 'P0001';
    end if;
    if p_pickup_note is not null and char_length(p_pickup_note) > 300 then
        raise exception 'Pickup note is too long (300 characters maximum)' using errcode = 'P0001';
    end if;

    -- One checkout at a time per customer, so two quick taps cannot create two orders.
    perform pg_advisory_xact_lock(hashtextextended('place_order:' || v_user::text, 0));

    create temporary table if not exists pg_temp.requested_items (
        product_id uuid primary key,
        quantity   integer not null
    ) on commit drop;
    truncate pg_temp.requested_items;

    -- Parse and merge duplicate lines. Bad shapes or quantities are rejected.
    begin
        insert into pg_temp.requested_items (product_id, quantity)
        select (item ->> 'product_id')::uuid, sum((item ->> 'quantity')::integer)
          from jsonb_array_elements(p_items) as item
         group by 1;
    exception when others then
        raise exception 'Your cart contains an invalid item' using errcode = 'P0001';
    end;

    if exists (select 1 from pg_temp.requested_items
                where product_id is null or quantity is null or quantity < 1 or quantity > 100) then
        raise exception 'Each item quantity must be between 1 and 100' using errcode = 'P0001';
    end if;

    -- The customer's latest unpaid order that has no payment attempt yet, if any.
    select o.id, o.order_number into v_order_id, v_number
      from public.orders o
     where o.customer_id = v_user
       and o.status = 'pending_payment'
       and not exists (select 1 from public.payments pay where pay.order_id = o.id)
     order by o.created_at desc
     limit 1
       for update;

    if v_order_id is not null then
        v_merged := true;
        -- Add the items already in that order to this checkout.
        insert into pg_temp.requested_items (product_id, quantity)
        select oi.product_id, oi.quantity
          from public.order_items oi
         where oi.order_id = v_order_id
        on conflict (product_id) do update
            set quantity = pg_temp.requested_items.quantity + excluded.quantity;

        if exists (select 1 from pg_temp.requested_items where quantity > 100) then
            raise exception 'Each item quantity must be between 1 and 100 (including items already in order #%)', v_number
                using errcode = 'P0001';
        end if;
    end if;

    -- Every product must exist, be on sale, and have enough stock for the combined order.
    for v_line in
        select r.product_id, r.quantity, p.name, p.price_paise, p.stock_quantity,
               (p.id is not null and p.is_active and c.is_active) as on_sale
          from pg_temp.requested_items r
          left join public.products p on p.id = r.product_id
          left join public.categories c on c.id = p.category_id
    loop
        if not coalesce(v_line.on_sale, false) then
            if v_merged then
                raise exception 'An item in your unpaid order #% is no longer available. Please cancel that order and order again.', v_number
                    using errcode = 'P0001';
            end if;
            raise exception 'An item in your cart is no longer available. Please refresh your cart.'
                using errcode = 'P0001';
        end if;
        if v_line.stock_quantity < v_line.quantity then
            if v_line.stock_quantity = 0 then
                raise exception '% is sold out', v_line.name using errcode = 'P0001';
            end if;
            if v_merged then
                raise exception 'Only % of % left (including what is already in order #%)',
                    v_line.stock_quantity, v_line.name, v_number using errcode = 'P0001';
            end if;
            raise exception 'Only % of % left', v_line.stock_quantity, v_line.name using errcode = 'P0001';
        end if;
        v_total := v_total + v_line.price_paise * v_line.quantity;
    end loop;

    if v_total <= 0 then
        raise exception 'Order total must be greater than zero' using errcode = 'P0001';
    end if;

    if v_merged then
        update public.orders
           set total_paise = v_total,
               pickup_note = coalesce(nullif(btrim(p_pickup_note), ''), pickup_note)
         where id = v_order_id;
        delete from public.order_items where order_id = v_order_id;
    else
        insert into public.orders (customer_id, status, total_paise, pickup_note)
        values (v_user, 'pending_payment', v_total, nullif(btrim(p_pickup_note), ''))
        returning id, order_number into v_order_id, v_number;
    end if;

    insert into public.order_items (order_id, product_id, product_name, unit_price_paise, quantity, line_total_paise)
    select v_order_id, p.id, p.name, p.price_paise, r.quantity, p.price_paise * r.quantity
      from pg_temp.requested_items r
      join public.products p on p.id = r.product_id;

    return jsonb_build_object(
        'order_id', v_order_id,
        'order_number', v_number,
        'total_paise', v_total,
        'added_to_existing', v_merged
    );
end;
$$;

revoke all on function public.place_order(jsonb, text) from public, anon;
grant execute on function public.place_order(jsonb, text) to authenticated;
