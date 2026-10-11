-- Tests for server-verified payments (Stage 4). Run with: supabase test db
begin;
create extension if not exists pgtap with schema extensions;
select plan(31);

-- ---------------------------------------------------------------- fixtures (as owner)
insert into auth.users (id, email, raw_user_meta_data) values
    ('00000000-0000-0000-0000-0000000000c1', 'c1@test.local', '{}'),
    ('00000000-0000-0000-0000-0000000000c2', 'c2@test.local', '{}'),
    ('00000000-0000-0000-0000-0000000000a1', 'a1@test.local', '{}');
update public.profiles set role = 'admin' where id = '00000000-0000-0000-0000-0000000000a1';

insert into public.categories (id, slug, name) values
    ('cccccccc-0000-0000-0000-000000000001', 'pay-test', 'Pay test');
insert into public.products (id, category_id, name, price_paise) values
    ('aaaaaaaa-0000-0000-0000-000000000001', 'cccccccc-0000-0000-0000-000000000001', 'Plum Cake', 45000),
    ('aaaaaaaa-0000-0000-0000-000000000002', 'cccccccc-0000-0000-0000-000000000001', 'Veg Puff', 2500);
insert into public.stock_movements (product_id, movement_type, quantity_delta) values
    ('aaaaaaaa-0000-0000-0000-000000000001', 'production', 3),
    ('aaaaaaaa-0000-0000-0000-000000000002', 'production', 10);

-- Customer c1 places an order: 2 cakes + 4 puffs = 1,000 rupees.
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000c1"}', true);
create temporary table o1 as
select (public.place_order(
    '[{"product_id":"aaaaaaaa-0000-0000-0000-000000000001","quantity":2},
      {"product_id":"aaaaaaaa-0000-0000-0000-000000000002","quantity":4}]') ->> 'order_id')::uuid as id;
grant select on o1 to public;

-- ---------------------------------------------------------------- the app cannot reach these
select throws_ok(
    format('select public.prepare_payment(%L, auth.uid())', (select id from o1)),
    '42501', null, 'customers cannot call prepare_payment'
);
select throws_ok(
    format($$select public.record_payment_attempt(%L, 'razorpay', 'order_fake', 100000)$$, (select id from o1)),
    '42501', null, 'customers cannot record payment attempts'
);
select throws_ok(
    $$ select public.confirm_payment('order_fake', 'pay_fake', 100000) $$,
    '42501', null, 'customers cannot confirm payments'
);
select throws_ok(
    $$ select public.complete_refund(gen_random_uuid(), 'rfnd_fake', true) $$,
    '42501', null, 'customers cannot complete refunds'
);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000a1"}', true);
select throws_ok(
    $$ select public.confirm_payment('order_fake', 'pay_fake', 100000) $$,
    '42501', null, 'not even the admin can confirm a payment from the app'
);
reset role;

-- ---------------------------------------------------------------- prepare + attempt (service role)
set local role service_role;
select throws_ok(
    format($$select public.prepare_payment(%L, '00000000-0000-0000-0000-0000000000c2')$$, (select id from o1)),
    'P0001', 'Order not found', 'cannot pay for another customer''s order'
);
select is(
    (select (public.prepare_payment((select id from o1), '00000000-0000-0000-0000-0000000000c1') ->> 'amount_paise')::bigint),
    100000::bigint, 'amount to pay comes from the order total on the server'
);
select throws_ok(
    format($$select public.record_payment_attempt(%L, 'razorpay', 'order_A', 1)$$, (select id from o1)),
    'P0001', 'Payment amount does not match the order total', 'a payment for the wrong amount is refused'
);
select lives_ok(
    format($$select public.record_payment_attempt(%L, 'razorpay', 'order_A', 100000)$$, (select id from o1)),
    'payment attempt is recorded'
);
select is(
    (select public.prepare_payment((select id from o1), '00000000-0000-0000-0000-0000000000c1') ->> 'reusable_provider_order_id'),
    'order_A', 'an unpaid attempt for the same amount is reused'
);

-- ---------------------------------------------------------------- confirm
select throws_ok(
    $$ select public.confirm_payment('order_A', 'pay_A', 99999) $$,
    'P0001', null, 'a captured amount that differs from the order is rejected'
);
select is(
    (select status::text from public.orders where id = (select id from o1)),
    'pending_payment', 'order stays unpaid after a mismatched amount'
);
select is(
    (select public.confirm_payment('order_A', 'pay_A', 100000) ->> 'result'),
    'paid', 'verified payment marks the order paid'
);
select is((select status::text from public.orders where id = (select id from o1)), 'paid', 'order status is paid');
select isnt((select paid_at from public.orders where id = (select id from o1)), null, 'paid time is recorded');
select is(
    (select status::text from public.payments where provider_order_id = 'order_A'),
    'captured', 'payment is captured'
);
select isnt(
    (select verified_at from public.payments where provider_order_id = 'order_A'),
    null, 'payment has a verification time'
);
select is(
    (select stock_quantity from public.products where id = 'aaaaaaaa-0000-0000-0000-000000000001'),
    1, 'cake stock reduced from 3 to 1'
);
select is(
    (select stock_quantity from public.products where id = 'aaaaaaaa-0000-0000-0000-000000000002'),
    6, 'puff stock reduced from 10 to 6'
);
select is(
    (select count(*)::int from public.stock_movements where order_id = (select id from o1) and movement_type = 'sale'),
    2, 'one sale entry per item in the stock ledger'
);
select is(
    (select public.confirm_payment('order_A', 'pay_A', 100000) ->> 'result'),
    'already_confirmed', 'confirming the same payment again is harmless'
);
select is(
    (select stock_quantity from public.products where id = 'aaaaaaaa-0000-0000-0000-000000000001'),
    1, 'stock is not reduced twice'
);
reset role;

-- ---------------------------------------------------------------- cancelled before payment completed
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000c1"}', true);
create temporary table o2 as
select (public.place_order('[{"product_id":"aaaaaaaa-0000-0000-0000-000000000002","quantity":1}]') ->> 'order_id')::uuid as id;
grant select on o2 to public;
reset role;
set local role service_role;
select public.record_payment_attempt((select id from o2), 'razorpay', 'order_B', 2500);
reset role;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000c1"}', true);
select public.cancel_my_order((select id from o2));
reset role;
set local role service_role;
select is(
    (select public.confirm_payment('order_B', 'pay_B', 2500) ->> 'result'),
    'refund_required', 'payment for a cancelled order triggers a refund'
);
select is(
    (select status::text from public.refunds where order_id = (select id from o2)),
    'pending', 'refund is recorded as pending'
);
select is(
    (select stock_quantity from public.products where id = 'aaaaaaaa-0000-0000-0000-000000000002'),
    6, 'no stock taken for a refunded payment'
);
select lives_ok(
    $$ select public.complete_refund((select id from public.refunds limit 1), 'rfnd_B', true) $$,
    'refund result is recorded'
);
select is((select status::text from public.refunds limit 1), 'processed', 'refund is processed');
reset role;

-- ---------------------------------------------------------------- sold out by the time payment completes
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000c1"}', true);
create temporary table o3 as
select (public.place_order('[{"product_id":"aaaaaaaa-0000-0000-0000-000000000001","quantity":1}]') ->> 'order_id')::uuid as id;
grant select on o3 to public;
reset role;
set local role service_role;
select public.record_payment_attempt((select id from o3), 'razorpay', 'order_C', 45000);
reset role;
-- Meanwhile the last cake is thrown away.
insert into public.stock_movements (product_id, movement_type, quantity_delta, reason)
values ('aaaaaaaa-0000-0000-0000-000000000001', 'wastage', -1, 'Dropped');
set local role service_role;
select is(
    (select public.confirm_payment('order_C', 'pay_C', 45000) ->> 'result'),
    'refund_required', 'payment for an item that sold out triggers a refund'
);
select is((select status::text from public.orders where id = (select id from o3)), 'cancelled',
    'the unfulfillable order is cancelled');

-- ---------------------------------------------------------------- failed attempts
insert into public.payments (order_id, provider, provider_order_id, amount_paise)
values ((select id from o3), 'razorpay', 'order_D', 45000);
select public.mark_payment_failed('order_D');
select is((select status::text from public.payments where provider_order_id = 'order_D'), 'failed',
    'a failed attempt is marked failed');
reset role;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000c2"}', true);
select is((select count(*)::int from public.payments), 0, 'customers cannot see other customers'' payments');

select * from finish();
rollback;
