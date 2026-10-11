-- =====================================================================================
-- VSJ BAKERY — Stage 3: catalogue visibility and server-side pickup orders
--
-- The app only sends WHICH products and HOW MANY. Everything that matters for money is
-- decided here on the server: current prices, availability and the order total.
-- Orders are created as 'pending_payment'. Stock is NOT deducted yet: that happens only
-- when a payment is verified by the server (Stage 4).
-- =====================================================================================

-- Customers should not see products whose category has been hidden by the admin.
drop policy "products: everyone reads active, staff read all" on public.products;

create policy "products: shoppers see items on sale, staff see all"
    on public.products for select to anon, authenticated
    using (
        (select private.is_staff())
        or (
            is_active
            and exists (
                select 1 from public.categories c
                where c.id = products.category_id and c.is_active
            )
        )
    );

-- -------------------------------------------------------------------------------------
-- place_order(items, pickup_note)
--   items: [{"product_id": "<uuid>", "quantity": 2}, ...]
--   returns: {"order_id": "...", "order_number": 123, "total_paise": 90000}
-- Errors meant for the customer are raised with SQLSTATE P0001 and a plain message.
-- -------------------------------------------------------------------------------------
create function public.place_order(p_items jsonb, p_pickup_note text default null)
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
    v_line      record;
    v_pending   integer;
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

    -- Basic protection against abandoned-order spam.
    select count(*) into v_pending
      from public.orders
     where customer_id = v_user and status = 'pending_payment'
       and created_at > now() - interval '1 day';
    if v_pending >= 5 then
        raise exception 'You have too many unpaid orders. Please pay for or cancel one first.'
            using errcode = 'P0001';
    end if;

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

    -- Every product must exist, be on sale, and have enough stock right now.
    for v_line in
        select r.product_id, r.quantity, p.name, p.price_paise, p.stock_quantity,
               (p.id is not null and p.is_active and c.is_active) as on_sale
          from pg_temp.requested_items r
          left join public.products p on p.id = r.product_id
          left join public.categories c on c.id = p.category_id
    loop
        if not coalesce(v_line.on_sale, false) then
            raise exception 'An item in your cart is no longer available. Please refresh your cart.'
                using errcode = 'P0001';
        end if;
        if v_line.stock_quantity < v_line.quantity then
            if v_line.stock_quantity = 0 then
                raise exception '% is sold out', v_line.name using errcode = 'P0001';
            end if;
            raise exception 'Only % of % left', v_line.stock_quantity, v_line.name using errcode = 'P0001';
        end if;
        v_total := v_total + v_line.price_paise * v_line.quantity;
    end loop;

    if v_total <= 0 then
        raise exception 'Order total must be greater than zero' using errcode = 'P0001';
    end if;

    insert into public.orders (customer_id, status, total_paise, pickup_note)
    values (v_user, 'pending_payment', v_total, nullif(btrim(p_pickup_note), ''))
    returning id, order_number into v_order_id, v_number;

    insert into public.order_items (order_id, product_id, product_name, unit_price_paise, quantity, line_total_paise)
    select v_order_id, p.id, p.name, p.price_paise, r.quantity, p.price_paise * r.quantity
      from pg_temp.requested_items r
      join public.products p on p.id = r.product_id;

    return jsonb_build_object('order_id', v_order_id, 'order_number', v_number, 'total_paise', v_total);
end;
$$;

-- -------------------------------------------------------------------------------------
-- cancel_my_order(order_id): a customer may cancel their own order while it is unpaid.
-- -------------------------------------------------------------------------------------
create function public.cancel_my_order(p_order_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if auth.uid() is null then
        raise exception 'Please sign in' using errcode = '42501';
    end if;
    update public.orders
       set status = 'cancelled', cancelled_at = now()
     where id = p_order_id
       and customer_id = auth.uid()
       and status = 'pending_payment';
    if not found then
        raise exception 'This order cannot be cancelled' using errcode = 'P0001';
    end if;
end;
$$;

revoke all on function public.place_order(jsonb, text) from public, anon;
revoke all on function public.cancel_my_order(uuid) from public, anon;
grant execute on function public.place_order(jsonb, text) to authenticated;
grant execute on function public.cancel_my_order(uuid) to authenticated;
