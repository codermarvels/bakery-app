-- Tests for the sales report (Stage 6). Run with: supabase test db
begin;
create extension if not exists pgtap with schema extensions;
select plan(29);

-- ---------------------------------------------------------------- fixtures (as owner)
insert into auth.users (id, email, raw_user_meta_data) values
    ('00000000-0000-0000-0000-0000000000c1', 'c1@test.local', '{}'),
    ('00000000-0000-0000-0000-0000000000a1', 'a1@test.local', '{}'),
    ('00000000-0000-0000-0000-0000000000b1', 'b1@test.local', '{}');
update public.profiles set role = 'admin'   where id = '00000000-0000-0000-0000-0000000000a1';
update public.profiles set role = 'reports' where id = '00000000-0000-0000-0000-0000000000b1';

insert into public.categories (id, slug, name) values ('cccccccc-0000-0000-0000-000000000001', 'rep', 'Rep');
insert into public.products (id, category_id, name, price_paise) values
    ('aaaaaaaa-0000-0000-0000-000000000001', 'cccccccc-0000-0000-0000-000000000001', 'Veg Puff', 2500),
    ('aaaaaaaa-0000-0000-0000-000000000002', 'cccccccc-0000-0000-0000-000000000001', 'Plum Cake', 45000);

-- The report day is 10 Oct 2026 in Indian time (IST = UTC+5:30).
-- A: app order, 2 cakes, paid online.
-- B: counter sale, 4 puffs, cash.
-- C: counter sale, 2 puffs, UPI — then undone (refund processed).
-- D: app order paid online but refunded automatically (item sold out), so cancelled.
-- E: counter sale, 1 puff, card, at 00:15 IST on 10 Oct (still 9 Oct in UTC).
-- F: counter sale, 1 puff, cash, at 00:15 IST on 11 Oct (still 10 Oct in UTC) → next day.
insert into public.orders (id, customer_id, channel, status, total_paise, paid_at) values
    ('dddddddd-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-0000000000c1', 'app', 'paid', 90000, '2026-10-10 10:00+05:30'),
    ('dddddddd-0000-0000-0000-00000000000b', null, 'counter', 'collected', 10000, '2026-10-10 18:00+05:30'),
    ('dddddddd-0000-0000-0000-00000000000c', null, 'counter', 'cancelled', 5000, '2026-10-10 12:00+05:30'),
    ('dddddddd-0000-0000-0000-00000000000d', '00000000-0000-0000-0000-0000000000c1', 'app', 'cancelled', 2500, null),
    ('dddddddd-0000-0000-0000-00000000000e', null, 'counter', 'collected', 2500, '2026-10-10 00:15+05:30'),
    ('dddddddd-0000-0000-0000-00000000000f', null, 'counter', 'collected', 2500, '2026-10-11 00:15+05:30');

insert into public.order_items (order_id, product_id, product_name, unit_price_paise, quantity, line_total_paise) values
    ('dddddddd-0000-0000-0000-00000000000a', 'aaaaaaaa-0000-0000-0000-000000000002', 'Plum Cake', 45000, 2, 90000),
    ('dddddddd-0000-0000-0000-00000000000b', 'aaaaaaaa-0000-0000-0000-000000000001', 'Veg Puff', 2500, 4, 10000),
    ('dddddddd-0000-0000-0000-00000000000c', 'aaaaaaaa-0000-0000-0000-000000000001', 'Veg Puff', 2500, 2, 5000),
    ('dddddddd-0000-0000-0000-00000000000d', 'aaaaaaaa-0000-0000-0000-000000000001', 'Veg Puff', 2500, 1, 2500),
    ('dddddddd-0000-0000-0000-00000000000e', 'aaaaaaaa-0000-0000-0000-000000000001', 'Veg Puff', 2500, 1, 2500),
    ('dddddddd-0000-0000-0000-00000000000f', 'aaaaaaaa-0000-0000-0000-000000000001', 'Veg Puff', 2500, 1, 2500);

insert into public.payments (id, order_id, provider, amount_paise, status, verified_at) values
    ('eeeeeeee-0000-0000-0000-00000000000a', 'dddddddd-0000-0000-0000-00000000000a', 'razorpay', 90000, 'captured', '2026-10-10 10:00+05:30'),
    ('eeeeeeee-0000-0000-0000-00000000000b', 'dddddddd-0000-0000-0000-00000000000b', 'cash', 10000, 'captured', '2026-10-10 18:00+05:30'),
    ('eeeeeeee-0000-0000-0000-00000000000c', 'dddddddd-0000-0000-0000-00000000000c', 'upi', 5000, 'captured', '2026-10-10 12:00+05:30'),
    ('eeeeeeee-0000-0000-0000-00000000000d', 'dddddddd-0000-0000-0000-00000000000d', 'razorpay', 2500, 'captured', '2026-10-10 15:00+05:30'),
    ('eeeeeeee-0000-0000-0000-00000000000e', 'dddddddd-0000-0000-0000-00000000000e', 'card', 2500, 'captured', '2026-10-10 00:15+05:30'),
    ('eeeeeeee-0000-0000-0000-00000000000f', 'dddddddd-0000-0000-0000-00000000000f', 'cash', 2500, 'captured', '2026-10-11 00:15+05:30');

insert into public.refunds (order_id, payment_id, amount_paise, reason, status, processed_at) values
    ('dddddddd-0000-0000-0000-00000000000c', 'eeeeeeee-0000-0000-0000-00000000000c', 5000, 'Entered twice', 'processed', '2026-10-10 12:30+05:30'),
    ('dddddddd-0000-0000-0000-00000000000d', 'eeeeeeee-0000-0000-0000-00000000000d', 2500, 'Sold out', 'processed', '2026-10-10 15:01+05:30');

-- Stock activity on 10 Oct: 20 puffs and 5 cakes made, 3 puffs wasted, count found 2 cakes missing.
insert into public.stock_movements (product_id, movement_type, quantity_delta, reason, created_at) values
    ('aaaaaaaa-0000-0000-0000-000000000001', 'production', 20, 'Morning', '2026-10-10 07:00+05:30'),
    ('aaaaaaaa-0000-0000-0000-000000000002', 'production', 5, 'Morning', '2026-10-10 07:00+05:30'),
    ('aaaaaaaa-0000-0000-0000-000000000001', 'wastage', -3, 'Burnt', '2026-10-10 09:00+05:30');
insert into public.stock_counts (id, note, created_at) values
    ('ffffffff-0000-0000-0000-000000000001', 'Closing', '2026-10-10 21:00+05:30');
insert into public.stock_movements (product_id, movement_type, quantity_delta, reason, stock_count_id, created_at) values
    ('aaaaaaaa-0000-0000-0000-000000000002', 'adjustment', -2, 'End-of-day count: unrecorded difference',
     'ffffffff-0000-0000-0000-000000000001', '2026-10-10 21:00+05:30');

-- ---------------------------------------------------------------- who can see reports
set local role anon;
select throws_ok($$ select public.sales_report('2026-10-10', '2026-10-10') $$, '42501', null,
    'signed-out visitors cannot run reports');
reset role;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000c1"}', true);
select throws_ok($$ select public.sales_report('2026-10-10', '2026-10-10') $$, '42501', null,
    'customers cannot run reports');
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a1"}', true);
select lives_ok($$ select public.sales_report('2026-10-10', '2026-10-10') $$, 'admin can run reports');

-- ---------------------------------------------------------------- the numbers (reports user)
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000b1"}', true);
create temporary table rep as select public.sales_report('2026-10-10', '2026-10-10') as r;
grant select on rep to public;

select is((select (r ->> 'paid_orders')::int from rep), 5, 'paid orders: A, B, C, D, E (F is the next day)');
select is((select (r ->> 'app_orders')::int from rep), 2, 'app orders: A and D');
select is((select (r ->> 'counter_sales')::int from rep), 3, 'counter sales: B, C, E');
select is((select (r ->> 'gross_paise')::bigint from rep), 110000::bigint,
    'gross sales = 900 + 100 + 50 + 25 + 25 = 1,100 rupees');
select is((select (r ->> 'app_gross_paise')::bigint from rep), 92500::bigint, 'online (app) gross = 925 rupees');
select is((select (r ->> 'counter_gross_paise')::bigint from rep), 17500::bigint, 'counter gross = 175 rupees');
select is((select (r ->> 'refund_count')::int from rep), 2, 'two refunds');
select is((select (r ->> 'refunds_paise')::bigint from rep), 7500::bigint, 'refunds = 50 + 25 = 75 rupees');
select is((select (r ->> 'net_paise')::bigint from rep), 102500::bigint, 'net sales = 1,100 − 75 = 1,025 rupees');

select is(
    (select (m ->> 'amount_paise')::bigint from rep, jsonb_array_elements(r -> 'by_method') m where m ->> 'method' = 'razorpay'),
    92500::bigint, 'online payments: 925 rupees'
);
select is(
    (select (m ->> 'amount_paise')::bigint from rep, jsonb_array_elements(r -> 'by_method') m where m ->> 'method' = 'cash'),
    10000::bigint, 'cash: 100 rupees (the 11 Oct sale is excluded)'
);
select is(
    (select (m ->> 'amount_paise')::bigint from rep, jsonb_array_elements(r -> 'by_method') m where m ->> 'method' = 'upi'),
    5000::bigint, 'UPI: 50 rupees received (refund shown separately)'
);
select is(
    (select (m ->> 'amount_paise')::bigint from rep, jsonb_array_elements(r -> 'by_method') m where m ->> 'method' = 'card'),
    2500::bigint, 'card: the 00:15 IST sale counts on 10 Oct'
);

select is(
    (select (i ->> 'quantity')::int from rep, jsonb_array_elements(r -> 'items') i where i ->> 'name' = 'Veg Puff'),
    5, 'puffs sold = 4 (B) + 1 (E); undone and refunded orders excluded'
);
select is(
    (select (i ->> 'quantity')::int from rep, jsonb_array_elements(r -> 'items') i where i ->> 'name' = 'Plum Cake'),
    2, 'cakes sold = 2'
);
select is(
    (select (i ->> 'amount_paise')::bigint from rep, jsonb_array_elements(r -> 'items') i where i ->> 'name' = 'Veg Puff'),
    12500::bigint, 'puff sales amount = 125 rupees'
);

select is(
    (select (w ->> 'quantity')::int from rep, jsonb_array_elements(r -> 'wastage') w where w ->> 'name' = 'Veg Puff'),
    3, 'wastage quantity = 3 puffs'
);
select is(
    (select (w ->> 'value_paise')::bigint from rep, jsonb_array_elements(r -> 'wastage') w where w ->> 'name' = 'Veg Puff'),
    7500::bigint, 'wastage value = 75 rupees'
);
select is(
    (select (c ->> 'quantity')::int from rep, jsonb_array_elements(r -> 'count_differences') c where c ->> 'name' = 'Plum Cake'),
    -2, 'unrecorded difference = 2 cakes missing'
);
select is(
    (select (c ->> 'value_paise')::bigint from rep, jsonb_array_elements(r -> 'count_differences') c where c ->> 'name' = 'Plum Cake'),
    -90000::bigint, 'unrecorded difference value = −900 rupees'
);
select is(
    (select (p ->> 'quantity')::int from rep, jsonb_array_elements(r -> 'production') p where p ->> 'name' = 'Veg Puff'),
    20, 'production = 20 puffs'
);

-- ---------------------------------------------------------------- ranges
create temporary table rep3 as select public.sales_report('2026-10-09', '2026-10-11') as r;
grant select on rep3 to public;
select is((select jsonb_array_length(r -> 'daily') from rep3), 3, 'one daily line per day in the range');
select is(
    (select (d ->> 'gross_paise')::bigint from rep3, jsonb_array_elements(r -> 'daily') d where d ->> 'day' = '2026-10-11'),
    2500::bigint, 'the 00:15 IST sale on 11 Oct is on the 11th'
);
select is((select (r ->> 'gross_paise')::bigint from rep3), 112500::bigint, 'three-day gross includes the 11 Oct sale');
select throws_ok($$ select public.sales_report('2026-10-11', '2026-10-10') $$, 'P0001', null,
    'from date after to date is rejected');
select throws_ok($$ select public.sales_report('2025-01-01', '2026-10-10') $$, 'P0001', null,
    'ranges longer than a year are rejected');

select * from finish();
rollback;
