-- Security tests for roles, Row Level Security and stock rules.
-- Run with the Supabase CLI:  supabase test db
-- Everything happens inside a transaction that is rolled back, so no data is left behind.
begin;
create extension if not exists pgtap with schema extensions;
select plan(41);

-- ---------------------------------------------------------------- test fixtures (as owner)
-- The customer tries to sign up as admin through metadata; the trigger must ignore it.
insert into auth.users (id, email, raw_user_meta_data) values
    ('00000000-0000-0000-0000-00000000000c', 'customer@test.local', '{"full_name":"Asha","role":"admin"}'),
    ('00000000-0000-0000-0000-0000000000c2', 'other@test.local',    '{"full_name":"Ravi"}'),
    ('00000000-0000-0000-0000-00000000000a', 'admin@test.local',    '{"full_name":"Owner"}'),
    ('00000000-0000-0000-0000-00000000000b', 'reports@test.local',  '{"full_name":"Accountant"}');

select is(
    (select role::text from public.profiles where id = '00000000-0000-0000-0000-00000000000c'),
    'customer',
    'new sign-ups are always customers, even if they ask to be admin'
);
select is(
    (select full_name from public.profiles where id = '00000000-0000-0000-0000-00000000000c'),
    'Asha',
    'full name is copied from sign-up data'
);

-- Roles can only be granted by the database owner (the Supabase dashboard).
update public.profiles set role = 'admin'   where id = '00000000-0000-0000-0000-00000000000a';
update public.profiles set role = 'reports' where id = '00000000-0000-0000-0000-00000000000b';

insert into public.products (id, category_id, name, price_paise, is_active)
select '11111111-1111-1111-1111-111111111111', id, 'Black Forest Cake', 45000, true
from public.categories where slug = 'cakes';
insert into public.products (id, category_id, name, price_paise, is_active)
select '22222222-2222-2222-2222-222222222222', id, 'Hidden Test Bun', 2000, false
from public.categories where slug = 'breads';

insert into public.orders (id, customer_id, total_paise) values
    ('33333333-3333-3333-3333-333333333331', '00000000-0000-0000-0000-00000000000c', 45000),
    ('33333333-3333-3333-3333-333333333332', '00000000-0000-0000-0000-0000000000c2', 45000);

-- ---------------------------------------------------------------- anonymous visitor
set local role anon;
select set_config('request.jwt.claims', '', true);

select is((select count(*)::int from public.categories), 6, 'anon sees the 6 starter categories');
select is((select count(*)::int from public.products), 1, 'anon sees only active products');
select throws_ok(
    $$ select count(*) from public.orders $$,
    '42501', null, 'anon cannot read orders at all'
);
select throws_ok(
    $$ select public.record_production('11111111-1111-1111-1111-111111111111', 5) $$,
    '42501', null, 'anon cannot call stock functions'
);
select throws_ok(
    $$ insert into public.categories (slug, name) values ('x', 'X') $$,
    '42501', null, 'anon cannot create categories'
);

-- ---------------------------------------------------------------- customer
reset role;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000c"}', true);

select is((select count(*)::int from public.profiles), 1, 'customer sees only their own profile');
select lives_ok(
    $$ update public.profiles set full_name = 'Asha K' where id = auth.uid() $$,
    'customer can edit their own name'
);
select throws_ok(
    $$ update public.profiles set role = 'admin' where id = auth.uid() $$,
    '42501', null, 'customer cannot change their own role'
);
select is((select count(*)::int from public.products), 1, 'customer sees only active products');
select throws_ok(
    $$ insert into public.categories (slug, name) values ('x', 'X') $$,
    '42501', null, 'customer cannot create categories'
);
update public.products set price_paise = 1 where id = '11111111-1111-1111-1111-111111111111';
select is((select count(*)::int from public.orders), 1, 'customer sees only their own order');
select throws_ok(
    $$ insert into public.orders (customer_id, total_paise) values (auth.uid(), 1) $$,
    '42501', null, 'customer cannot create orders directly (server creates them)'
);
select throws_ok(
    $$ update public.orders set status = 'paid' $$,
    '42501', null, 'customer cannot mark an order as paid'
);
select throws_ok(
    $$ insert into public.payments (order_id, provider, amount_paise, status, verified_at)
       values ('33333333-3333-3333-3333-333333333331', 'x', 45000, 'captured', now()) $$,
    '42501', null, 'customer cannot record a payment'
);
select throws_ok(
    $$ select public.record_production('11111111-1111-1111-1111-111111111111', 5) $$,
    '42501', null, 'customer cannot record production'
);
select throws_ok(
    $$ insert into storage.objects (bucket_id, name) values ('product-images', 'evil.jpg') $$,
    '42501', null, 'customer cannot upload product photos'
);

reset role;
select is(
    (select price_paise from public.products where id = '11111111-1111-1111-1111-111111111111'),
    45000::bigint,
    'customer price update had no effect'
);

-- ---------------------------------------------------------------- reports (read-only)
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b"}', true);

select is((select count(*)::int from public.orders), 2, 'reports user can read all orders');
select is((select count(*)::int from public.products), 2, 'reports user can read all products');
select is((select count(*)::int from public.profiles), 1, 'reports user cannot read other users');
select throws_ok(
    $$ insert into public.products (category_id, name, price_paise)
       select id, 'X', 100 from public.categories limit 1 $$,
    '42501', null, 'reports user cannot add products'
);
update public.products set price_paise = 1 where id = '11111111-1111-1111-1111-111111111111';
update public.categories set name = 'Hacked' where slug = 'cakes';
select throws_ok(
    $$ update public.orders set status = 'cancelled' $$,
    '42501', null, 'reports user cannot change orders'
);
select throws_ok(
    $$ update public.profiles set role = 'admin' where id = auth.uid() $$,
    '42501', null, 'reports user cannot change permissions'
);
select throws_ok(
    $$ select public.adjust_stock('11111111-1111-1111-1111-111111111111', 5, 'test') $$,
    '42501', null, 'reports user cannot adjust stock'
);
select throws_ok(
    $$ insert into public.refunds (order_id, payment_id, amount_paise, reason)
       values ('33333333-3333-3333-3333-333333333331', gen_random_uuid(), 100, 'x') $$,
    '42501', null, 'reports user cannot create refunds'
);

reset role;
select is(
    (select price_paise from public.products where id = '11111111-1111-1111-1111-111111111111'),
    45000::bigint,
    'reports price update had no effect'
);
select is(
    (select name from public.categories where slug = 'cakes'),
    'Cakes',
    'reports category update had no effect'
);

-- ---------------------------------------------------------------- administrator
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a"}', true);

select lives_ok(
    $$ insert into public.categories (slug, name) values ('seasonal', 'Seasonal') $$,
    'admin can create categories'
);
select lives_ok(
    $$ update public.products set price_paise = 48000 where id = '11111111-1111-1111-1111-111111111111' $$,
    'admin can change prices'
);
select lives_ok(
    $$ select public.record_production('11111111-1111-1111-1111-111111111111', 10, 'Morning batch') $$,
    'admin can record production'
);
select lives_ok(
    $$ select public.record_wastage('11111111-1111-1111-1111-111111111111', 2, 'Dropped tray') $$,
    'admin can record wastage'
);
select lives_ok(
    $$ select public.adjust_stock('11111111-1111-1111-1111-111111111111', -1, 'Count correction') $$,
    'admin can adjust stock with a reason'
);
select is(
    (select stock_quantity from public.products where id = '11111111-1111-1111-1111-111111111111'),
    7,
    'stock = 10 produced - 2 wasted - 1 adjusted'
);
select throws_ok(
    $$ select public.adjust_stock('11111111-1111-1111-1111-111111111111', 3, '  ') $$,
    '22023', null, 'stock adjustment without a reason is rejected'
);
select throws_ok(
    $$ select public.record_wastage('11111111-1111-1111-1111-111111111111', 50, 'Too many') $$,
    '23514', null, 'stock can never go below zero'
);
select throws_ok(
    $$ update public.products set stock_quantity = 999 where id = '11111111-1111-1111-1111-111111111111' $$,
    '42501', null, 'admin cannot overwrite stock without a ledger entry'
);
select throws_ok(
    $$ insert into public.stock_movements (product_id, movement_type, quantity_delta)
       values ('11111111-1111-1111-1111-111111111111', 'production', 100) $$,
    '42501', null, 'nobody writes the stock ledger directly from the app'
);
select throws_ok(
    $$ update public.orders set status = 'paid' $$,
    '42501', null, 'even admin cannot mark orders paid from the app'
);
select lives_ok(
    $$ insert into storage.objects (bucket_id, name) values ('product-images', 'cakes/black-forest.jpg') $$,
    'admin can upload product photos'
);

select * from finish();
rollback;
