-- Tests for server-side pickup ordering (Stage 3). Run with: supabase test db
begin;
create extension if not exists pgtap with schema extensions;
select plan(27);

-- ---------------------------------------------------------------- fixtures (as owner)
insert into auth.users (id, email, raw_user_meta_data) values
    ('00000000-0000-0000-0000-0000000000c1', 'c1@test.local', '{"full_name":"Asha"}'),
    ('00000000-0000-0000-0000-0000000000c2', 'c2@test.local', '{"full_name":"Ravi"}'),
    ('00000000-0000-0000-0000-0000000000a1', 'a1@test.local', '{}'),
    ('00000000-0000-0000-0000-0000000000b1', 'b1@test.local', '{}');
update public.profiles set role = 'admin'   where id = '00000000-0000-0000-0000-0000000000a1';
update public.profiles set role = 'reports' where id = '00000000-0000-0000-0000-0000000000b1';

insert into public.categories (id, slug, name, is_active) values
    ('cccccccc-0000-0000-0000-000000000001', 'test-open', 'Open', true),
    ('cccccccc-0000-0000-0000-000000000002', 'test-hidden', 'Hidden', false);

insert into public.products (id, category_id, name, price_paise, is_active) values
    ('aaaaaaaa-0000-0000-0000-000000000001', 'cccccccc-0000-0000-0000-000000000001', 'Plum Cake', 45000, true),
    ('aaaaaaaa-0000-0000-0000-000000000002', 'cccccccc-0000-0000-0000-000000000001', 'Veg Puff', 2500, true),
    ('aaaaaaaa-0000-0000-0000-000000000003', 'cccccccc-0000-0000-0000-000000000001', 'Retired Bun', 1000, false),
    ('aaaaaaaa-0000-0000-0000-000000000004', 'cccccccc-0000-0000-0000-000000000002', 'Hidden Tart', 3000, true),
    ('aaaaaaaa-0000-0000-0000-000000000005', 'cccccccc-0000-0000-0000-000000000001', 'Sold Out Roll', 1500, true);

insert into public.stock_movements (product_id, movement_type, quantity_delta) values
    ('aaaaaaaa-0000-0000-0000-000000000001', 'production', 5),
    ('aaaaaaaa-0000-0000-0000-000000000002', 'production', 20),
    ('aaaaaaaa-0000-0000-0000-000000000003', 'production', 5),
    ('aaaaaaaa-0000-0000-0000-000000000004', 'production', 5);

-- ---------------------------------------------------------------- visibility
set local role anon;
select is(
    (select count(*)::int from public.products where name = 'Hidden Tart'),
    0, 'products in hidden categories are not shown to customers'
);
select throws_ok(
    $$ select public.place_order('[{"product_id":"aaaaaaaa-0000-0000-0000-000000000001","quantity":1}]') $$,
    '42501', null, 'anon cannot place orders'
);
reset role;

-- ---------------------------------------------------------------- customer happy path
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000c1"}', true);

create temporary table result as
select public.place_order(
    '[{"product_id":"aaaaaaaa-0000-0000-0000-000000000001","quantity":2},
      {"product_id":"aaaaaaaa-0000-0000-0000-000000000002","quantity":3},
      {"product_id":"aaaaaaaa-0000-0000-0000-000000000002","quantity":1}]',
    '  Pick up at 5pm  '
) as r;

select is((select (r ->> 'total_paise')::bigint from result), 100000::bigint,
    'total is calculated by the server: 2 x 450 + 4 x 25 = 1000 rupees');
select ok((select (r ->> 'order_number')::bigint from result) > 0, 'an order number is returned');

select is(
    (select status::text from public.orders where id = (select (r ->> 'order_id')::uuid from result)),
    'pending_payment', 'new orders wait for payment'
);
select is(
    (select pickup_note from public.orders where id = (select (r ->> 'order_id')::uuid from result)),
    'Pick up at 5pm', 'pickup note is trimmed'
);
select is(
    (select count(*)::int from public.order_items where order_id = (select (r ->> 'order_id')::uuid from result)),
    2, 'duplicate cart lines are merged into one order line'
);
select is(
    (select quantity from public.order_items
      where order_id = (select (r ->> 'order_id')::uuid from result)
        and product_id = 'aaaaaaaa-0000-0000-0000-000000000002'),
    4, 'merged quantity is 3 + 1'
);
select is(
    (select unit_price_paise from public.order_items
      where order_id = (select (r ->> 'order_id')::uuid from result)
        and product_id = 'aaaaaaaa-0000-0000-0000-000000000001'),
    45000::bigint, 'unit price comes from the products table'
);

-- ---------------------------------------------------------------- customer rejections
select throws_ok(
    $$ select public.place_order('[]') $$,
    'P0001', 'Your cart is empty or too large', 'empty cart is rejected'
);
select throws_ok(
    $$ select public.place_order('[{"product_id":"aaaaaaaa-0000-0000-0000-000000000001","quantity":0}]') $$,
    'P0001', 'Each item quantity must be between 1 and 100', 'zero quantity is rejected'
);
select throws_ok(
    $$ select public.place_order('[{"product_id":"aaaaaaaa-0000-0000-0000-000000000001","quantity":-3}]') $$,
    'P0001', 'Each item quantity must be between 1 and 100', 'negative quantity is rejected'
);
select throws_ok(
    $$ select public.place_order('[{"product_id":"not-a-uuid","quantity":1}]') $$,
    'P0001', 'Your cart contains an invalid item', 'malformed item is rejected'
);
select throws_ok(
    $$ select public.place_order('[{"product_id":"aaaaaaaa-0000-0000-0000-000000000001","quantity":6}]') $$,
    'P0001', 'Only 5 of Plum Cake left', 'cannot order more than is in stock'
);
select throws_ok(
    $$ select public.place_order('[{"product_id":"aaaaaaaa-0000-0000-0000-000000000005","quantity":1}]') $$,
    'P0001', 'Sold Out Roll is sold out', 'sold-out product is rejected'
);
select throws_ok(
    $$ select public.place_order('[{"product_id":"aaaaaaaa-0000-0000-0000-000000000003","quantity":1}]') $$,
    'P0001', null, 'inactive product is rejected'
);
select throws_ok(
    $$ select public.place_order('[{"product_id":"aaaaaaaa-0000-0000-0000-000000000004","quantity":1}]') $$,
    'P0001', null, 'product in a hidden category is rejected'
);
select throws_ok(
    $$ select public.place_order('[{"product_id":"aaaaaaaa-0000-0000-0000-0000000000ff","quantity":1}]') $$,
    'P0001', null, 'unknown product is rejected'
);

-- ---------------------------------------------------------------- stock is untouched
reset role;
select is(
    (select stock_quantity from public.products where id = 'aaaaaaaa-0000-0000-0000-000000000001'),
    5, 'placing an unpaid order does not change stock'
);

-- ---------------------------------------------------------------- cancelling
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000c2"}', true);
select throws_ok(
    format('select public.cancel_my_order(%L)', (select (r ->> 'order_id') from result)),
    'P0001', 'This order cannot be cancelled', 'customers cannot cancel someone else''s order'
);
select is((select count(*)::int from public.orders), 0, 'other customers cannot see the order');

select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000c1"}', true);
select lives_ok(
    format('select public.cancel_my_order(%L)', (select (r ->> 'order_id') from result)),
    'customer can cancel their own unpaid order'
);
select is(
    (select status::text from public.orders where id = (select (r ->> 'order_id')::uuid from result)),
    'cancelled', 'order is now cancelled'
);
select throws_ok(
    format('select public.cancel_my_order(%L)', (select (r ->> 'order_id') from result)),
    'P0001', 'This order cannot be cancelled', 'a cancelled order cannot be cancelled again'
);

-- ---------------------------------------------------------------- staff cannot order
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000b1"}', true);
select throws_ok(
    $$ select public.place_order('[{"product_id":"aaaaaaaa-0000-0000-0000-000000000002","quantity":1}]') $$,
    '42501', null, 'reports user cannot create orders'
);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a1"}', true);
select throws_ok(
    $$ select public.place_order('[{"product_id":"aaaaaaaa-0000-0000-0000-000000000002","quantity":1}]') $$,
    '42501', null, 'admin account cannot place customer orders'
);

-- ---------------------------------------------------------------- unpaid-order limit
reset role;
insert into public.orders (customer_id, total_paise)
select '00000000-0000-0000-0000-0000000000c2', 100 from generate_series(1, 5);
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000c2"}', true);
select throws_ok(
    $$ select public.place_order('[{"product_id":"aaaaaaaa-0000-0000-0000-000000000002","quantity":1}]') $$,
    'P0001', null, 'too many unpaid orders are refused'
);

select * from finish();
rollback;
