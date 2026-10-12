-- Tests for administrator refunds of app orders (Stage 7). Run with: supabase test db
begin;
create extension if not exists pgtap with schema extensions;
select plan(18);

-- ---------------------------------------------------------------- fixtures (as owner)
insert into auth.users (id, email, raw_user_meta_data) values
    ('00000000-0000-0000-0000-0000000000e1', 'e1@test.local', '{}'),
    ('00000000-0000-0000-0000-0000000000ea', 'ea@test.local', '{}');
update public.profiles set role = 'admin' where id = '00000000-0000-0000-0000-0000000000ea';

insert into public.categories (id, slug, name) values
    ('cccccccc-0000-0000-0000-0000000000e1', 'refund-test', 'Refund test');
insert into public.products (id, category_id, name, price_paise) values
    ('aaaaaaaa-0000-0000-0000-0000000000e1', 'cccccccc-0000-0000-0000-0000000000e1', 'Fruit Cake', 50000),
    ('aaaaaaaa-0000-0000-0000-0000000000e2', 'cccccccc-0000-0000-0000-0000000000e1', 'Bun', 2000);
insert into public.stock_movements (product_id, movement_type, quantity_delta) values
    ('aaaaaaaa-0000-0000-0000-0000000000e1', 'production', 3),
    ('aaaaaaaa-0000-0000-0000-0000000000e2', 'production', 10);

-- The customer orders one cake, pays; then 5 buns, pays.
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000e1"}', true);
create temporary table r1 as
select (public.place_order('[{"product_id":"aaaaaaaa-0000-0000-0000-0000000000e1","quantity":1}]') ->> 'order_id')::uuid as id;
grant select on r1 to public;
reset role;
set local role service_role;
select public.record_payment_attempt((select id from r1), 'razorpay', 'order_r1', 50000);
select public.confirm_payment('order_r1', 'pay_r1', 50000);
reset role;

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000e1"}', true);
create temporary table r2 as
select (public.place_order('[{"product_id":"aaaaaaaa-0000-0000-0000-0000000000e2","quantity":5}]') ->> 'order_id')::uuid as id;
grant select on r2 to public;
reset role;
set local role service_role;
select public.record_payment_attempt((select id from r2), 'razorpay', 'order_r2', 10000);
select public.confirm_payment('order_r2', 'pay_r2', 10000);
reset role;

-- A third order is left unpaid (placed after the others were paid, so it is not merged into them).
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000e1"}', true);
create temporary table r3 as
select (public.place_order('[{"product_id":"aaaaaaaa-0000-0000-0000-0000000000e2","quantity":1}]') ->> 'order_id')::uuid as id;
grant select on r3 to public;
reset role;
update public.orders set status = 'collected', collected_at = now() where id = (select id from r2);

select is((select stock_quantity from public.products where id = 'aaaaaaaa-0000-0000-0000-0000000000e1'), 2,
          'setup: the paid cake left stock');

-- ---------------------------------------------------------------- who may refund
select ok(
    not has_function_privilege('authenticated', 'public.start_app_refund(uuid, uuid, text, boolean)', 'execute'),
    'the app cannot start refunds directly (only the server function can)'
);

set local role service_role;
select throws_ok(
    format($$ select public.start_app_refund(%L, '00000000-0000-0000-0000-0000000000e1', 'Please', true) $$, (select id from r1)),
    '42501', 'Only the administrator can refund orders', 'a customer cannot refund, even through the server'
);
select throws_ok(
    format($$ select public.start_app_refund(%L, '00000000-0000-0000-0000-0000000000ea', '  ', true) $$, (select id from r1)),
    'P0001', 'A reason is required for a refund', 'a reason is required'
);
select throws_like(
    format($$ select public.start_app_refund(%L, '00000000-0000-0000-0000-0000000000ea', 'Test', true) $$, (select id from r3)),
    'Order #% has no confirmed online payment to refund', 'unpaid orders cannot be refunded'
);
select throws_ok(
    $$ select public.start_app_refund(gen_random_uuid(), '00000000-0000-0000-0000-0000000000ea', 'Test', true) $$,
    'P0001', 'Order not found', 'unknown orders are reported'
);

-- ---------------------------------------------------------------- refund a paid order, items back on the shelf
create temporary table s1 as
select public.start_app_refund((select id from r1), '00000000-0000-0000-0000-0000000000ea', 'Customer cancelled', true) as r;
grant select on s1 to public;
select is((select (r ->> 'amount_paise')::bigint from s1), 50000::bigint, 'the full amount is refunded');
select is((select r ->> 'provider_payment_id' from s1), 'pay_r1', 'the Razorpay payment to refund is returned');
select is((select (r ->> 'retry')::boolean from s1), false, 'first attempt is not a retry');
select is((select status::text from public.orders where id = (select id from r1)), 'cancelled', 'the order is cancelled');
select is((select status::text from public.refunds where id = (select (r ->> 'refund_id')::uuid from s1)), 'pending',
          'the refund waits for Razorpay');
select is((select stock_quantity from public.products where id = 'aaaaaaaa-0000-0000-0000-0000000000e1'), 3,
          'the cake is back in stock');

-- Calling again before Razorpay answered: the same refund is sent again, nothing new is created.
select is(
    (select (public.start_app_refund((select id from r1), '00000000-0000-0000-0000-0000000000ea', 'Again', true) ->> 'refund_id')::uuid),
    (select (r ->> 'refund_id')::uuid from s1),
    'retrying reuses the same refund'
);
select is((select stock_quantity from public.products where id = 'aaaaaaaa-0000-0000-0000-0000000000e1'), 3,
          'retrying does not restock twice');

select public.complete_refund((select (r ->> 'refund_id')::uuid from s1), 'rfnd_r1', true);
select throws_like(
    format($$ select public.start_app_refund(%L, '00000000-0000-0000-0000-0000000000ea', 'Again', true) $$, (select id from r1)),
    'Order #% has already been refunded', 'a processed refund cannot be repeated'
);

-- ---------------------------------------------------------------- a collected order: never restocked
select lives_ok(
    format($$ select public.start_app_refund(%L, '00000000-0000-0000-0000-0000000000ea', 'Stale buns', true) $$, (select id from r2)),
    'a collected order can be refunded (e.g. a quality complaint)'
);
select is((select stock_quantity from public.products where id = 'aaaaaaaa-0000-0000-0000-0000000000e2'), 5,
          'items already handed over are not put back in stock');
reset role;

select is((select count(*) from public.refunds where order_id in ((select id from r1), (select id from r2))), 2::bigint,
          'one refund per payment');

select * from finish();
rollback;
