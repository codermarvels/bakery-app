-- Tests for administrator tools (Stage 5). Run with: supabase test db
begin;
create extension if not exists pgtap with schema extensions;
select plan(42);

-- ---------------------------------------------------------------- fixtures (as owner)
insert into auth.users (id, email, raw_user_meta_data) values
    ('00000000-0000-0000-0000-0000000000c1', 'c1@test.local', '{"full_name":"Asha"}'),
    ('00000000-0000-0000-0000-0000000000a1', 'a1@test.local', '{"full_name":"Owner"}'),
    ('00000000-0000-0000-0000-0000000000b1', 'b1@test.local', '{}');
update public.profiles set role = 'admin'   where id = '00000000-0000-0000-0000-0000000000a1';
update public.profiles set role = 'reports' where id = '00000000-0000-0000-0000-0000000000b1';

insert into public.categories (id, slug, name) values
    ('cccccccc-0000-0000-0000-000000000001', 'admin-test', 'Admin test');
insert into public.products (id, category_id, name, price_paise) values
    ('aaaaaaaa-0000-0000-0000-000000000001', 'cccccccc-0000-0000-0000-000000000001', 'Veg Puff', 2500),
    ('aaaaaaaa-0000-0000-0000-000000000002', 'cccccccc-0000-0000-0000-000000000001', 'Milk Bread', 4500);
insert into public.stock_movements (product_id, movement_type, quantity_delta) values
    ('aaaaaaaa-0000-0000-0000-000000000001', 'production', 20),
    ('aaaaaaaa-0000-0000-0000-000000000002', 'production', 10);

-- ---------------------------------------------------------------- who may use the tools
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000c1"}', true);
select throws_ok(
    $$ select public.record_counter_sale('[{"product_id":"aaaaaaaa-0000-0000-0000-000000000001","quantity":1}]', 'cash') $$,
    '42501', null, 'customers cannot record counter sales'
);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000b1"}', true);
select throws_ok(
    $$ select public.record_counter_sale('[{"product_id":"aaaaaaaa-0000-0000-0000-000000000001","quantity":1}]', 'cash') $$,
    '42501', null, 'reports user cannot record counter sales'
);
select throws_ok(
    $$ select public.record_stock_count('[{"product_id":"aaaaaaaa-0000-0000-0000-000000000001","counted":1}]') $$,
    '42501', null, 'reports user cannot record a stock count'
);
select throws_ok(
    $$ select public.set_order_status(gen_random_uuid(), 'collected') $$,
    '42501', null, 'reports user cannot change order status'
);
select throws_ok(
    $$ select public.void_counter_sale(gen_random_uuid(), 'x') $$,
    '42501', null, 'reports user cannot undo sales'
);

-- ---------------------------------------------------------------- counter sale
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a1"}', true);
select throws_ok(
    $$ select public.record_counter_sale('[{"product_id":"aaaaaaaa-0000-0000-0000-000000000001","quantity":1}]', 'cheque') $$,
    'P0001', 'Choose how the customer paid: cash, UPI or card', 'payment method must be cash, UPI or card'
);
select throws_ok(
    $$ select public.record_counter_sale('[]', 'cash') $$,
    'P0001', 'Add at least one item', 'empty sale is rejected'
);
select throws_ok(
    $$ select public.record_counter_sale('[{"product_id":"aaaaaaaa-0000-0000-0000-000000000002","quantity":11}]', 'cash') $$,
    'P0001', null, 'cannot sell more than is in stock'
);

create temporary table sale1 as
select public.record_counter_sale(
    '[{"product_id":"aaaaaaaa-0000-0000-0000-000000000001","quantity":4},
      {"product_id":"aaaaaaaa-0000-0000-0000-000000000002","quantity":2}]',
    'upi', 'Walk-in'
) as r;
grant select on sale1 to public;

select is((select (r ->> 'total_paise')::bigint from sale1), 19000::bigint,
    'total uses current prices: 4 x 25 + 2 x 45 = 190 rupees');
reset role;
select is((select stock_quantity from public.products where id = 'aaaaaaaa-0000-0000-0000-000000000001'), 16,
    'puff stock reduced 20 -> 16');
select is((select stock_quantity from public.products where id = 'aaaaaaaa-0000-0000-0000-000000000002'), 8,
    'bread stock reduced 10 -> 8');
select is(
    (select channel || '/' || status from public.orders where id = (select (r ->> 'order_id')::uuid from sale1)),
    'counter/collected', 'counter sale is recorded as a completed counter order'
);
select is(
    (select recorded_by from public.orders where id = (select (r ->> 'order_id')::uuid from sale1)),
    '00000000-0000-0000-0000-0000000000a1'::uuid, 'the sale records who entered it'
);
select is(
    (select customer_id from public.orders where id = (select (r ->> 'order_id')::uuid from sale1)),
    null, 'counter sales have no customer account'
);
select is(
    (select provider || '/' || status from public.payments where order_id = (select (r ->> 'order_id')::uuid from sale1)),
    'upi/captured', 'payment method is stored with the sale'
);
select isnt(
    (select paid_at from public.orders where id = (select (r ->> 'order_id')::uuid from sale1)),
    null, 'counter sale is marked paid'
);
select is(
    (select count(*)::int from public.stock_movements
      where order_id = (select (r ->> 'order_id')::uuid from sale1) and movement_type = 'sale'),
    2, 'one sale entry per item in the stock ledger'
);

-- Customers never see counter sales; reports user can.
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000c1"}', true);
select is((select count(*)::int from public.orders where channel = 'counter'), 0,
    'customers cannot see counter sales');
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000b1"}', true);
select is((select count(*)::int from public.orders where channel = 'counter'), 1,
    'reports user can see counter sales');

-- ---------------------------------------------------------------- undo a counter sale
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a1"}', true);
select throws_ok(
    format($$select public.void_counter_sale(%L, '  ')$$, (select r ->> 'order_id' from sale1)),
    'P0001', 'A reason is required to undo a sale', 'undo needs a reason'
);
select lives_ok(
    format($$select public.void_counter_sale(%L, 'Entered twice')$$, (select r ->> 'order_id' from sale1)),
    'admin can undo a counter sale'
);
select throws_ok(
    format($$select public.void_counter_sale(%L, 'Again')$$, (select r ->> 'order_id' from sale1)),
    'P0001', 'This sale has already been undone', 'a sale cannot be undone twice'
);
reset role;
select is((select stock_quantity from public.products where id = 'aaaaaaaa-0000-0000-0000-000000000001'), 20,
    'undo puts the stock back');
select is(
    (select status::text || '/' || amount_paise from public.refunds
      where order_id = (select (r ->> 'order_id')::uuid from sale1)),
    'processed/19000', 'undo records a processed refund so reports net it out'
);
select is(
    (select status::text from public.orders where id = (select (r ->> 'order_id')::uuid from sale1)),
    'cancelled', 'undone sale is cancelled'
);

-- ---------------------------------------------------------------- app order progress
-- A paid app order (created directly as owner for the test).
insert into public.orders (id, customer_id, status, total_paise, paid_at) values
    ('dddddddd-0000-0000-0000-000000000001', '00000000-0000-0000-0000-0000000000c1', 'paid', 2500, now()),
    ('dddddddd-0000-0000-0000-000000000002', '00000000-0000-0000-0000-0000000000c1', 'pending_payment', 2500, null);

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a1"}', true);
select throws_ok(
    $$ select public.set_order_status('dddddddd-0000-0000-0000-000000000002', 'ready_for_pickup') $$,
    'P0001', null, 'an unpaid order cannot be marked ready'
);
select throws_ok(
    $$ select public.void_counter_sale('dddddddd-0000-0000-0000-000000000001', 'x') $$,
    'P0001', 'Counter sale not found', 'app orders cannot be undone as counter sales'
);
select lives_ok(
    $$ select public.set_order_status('dddddddd-0000-0000-0000-000000000001', 'ready_for_pickup') $$,
    'paid order can be marked ready for pickup'
);
select lives_ok(
    $$ select public.set_order_status('dddddddd-0000-0000-0000-000000000001', 'collected') $$,
    'ready order can be marked collected'
);
select throws_ok(
    $$ select public.set_order_status('dddddddd-0000-0000-0000-000000000001', 'ready_for_pickup') $$,
    'P0001', null, 'a collected order cannot go back to ready'
);
reset role;
select ok(
    (select ready_at is not null and collected_at is not null and status = 'collected'
       from public.orders where id = 'dddddddd-0000-0000-0000-000000000001'),
    'ready and collected times are recorded'
);

-- ---------------------------------------------------------------- end-of-day count
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a1"}', true);
select throws_ok(
    $$ select public.record_stock_count('[{"product_id":"aaaaaaaa-0000-0000-0000-000000000001","counted":-1}]') $$,
    'P0001', null, 'negative counts are rejected'
);
select throws_ok(
    $$ select public.record_stock_count('[]') $$,
    'P0001', 'Enter at least one counted product', 'empty count is rejected'
);
-- System expects 20 puffs and 10 bread; staff count 17 puffs and 10 bread.
create temporary table count1 as
select public.record_stock_count(
    '[{"product_id":"aaaaaaaa-0000-0000-0000-000000000001","counted":17},
      {"product_id":"aaaaaaaa-0000-0000-0000-000000000002","counted":10}]',
    'Closing count'
) as r;
grant select on count1 to public;
select is((select jsonb_array_length(r -> 'differences') from count1), 1,
    'only products that differ are reported');
select is((select (r -> 'differences' -> 0 ->> 'difference')::int from count1), -3,
    'difference is counted minus expected (17 - 20 = -3)');
select is((select (r -> 'differences' -> 0 ->> 'value_paise')::bigint from count1), -7500::bigint,
    'difference value uses the selling price (-3 x 25 rupees)');
reset role;
select is((select stock_quantity from public.products where id = 'aaaaaaaa-0000-0000-0000-000000000001'), 17,
    'the counted quantity becomes the stock');
select is((select stock_quantity from public.products where id = 'aaaaaaaa-0000-0000-0000-000000000002'), 10,
    'matching counts leave stock unchanged');
select is(
    (select count(*)::int from public.stock_movements
      where stock_count_id = (select (r ->> 'count_id')::uuid from count1)),
    1, 'the difference is stored in the stock ledger, linked to the count'
);
select is(
    (select counted_by from public.stock_counts where id = (select (r ->> 'count_id')::uuid from count1)),
    '00000000-0000-0000-0000-0000000000a1'::uuid, 'the count records who did it'
);

-- Reports user can read counts; customers cannot.
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000b1"}', true);
select is((select count(*)::int from public.stock_counts), 1, 'reports user can read stock counts');
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000c1"}', true);
select is((select count(*)::int from public.stock_counts), 0, 'customers cannot read stock counts');

select * from finish();
rollback;
