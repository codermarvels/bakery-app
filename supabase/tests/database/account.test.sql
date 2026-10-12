-- Tests for account deletion (Stage 7). Run with: supabase test db
begin;
create extension if not exists pgtap with schema extensions;
select plan(17);

-- ---------------------------------------------------------------- fixtures (as owner)
insert into auth.users (id, email, raw_user_meta_data) values
    ('00000000-0000-0000-0000-0000000000d1', 'd1@test.local', '{"full_name":"Leaving"}'),
    ('00000000-0000-0000-0000-0000000000d2', 'd2@test.local', '{"full_name":"Waiting"}'),
    ('00000000-0000-0000-0000-0000000000d3', 'd3@test.local', '{"full_name":"Other"}'),
    ('00000000-0000-0000-0000-0000000000da', 'da@test.local', '{"full_name":"Owner"}');
update public.profiles set role = 'admin' where id = '00000000-0000-0000-0000-0000000000da';

-- d1: one collected order and one unpaid order. d2: a paid order not yet collected.
insert into public.orders (id, customer_id, status, total_paise, pickup_note, paid_at, collected_at) values
    ('dddddddd-0000-0000-0000-000000000001', '00000000-0000-0000-0000-0000000000d1', 'collected', 5000,
     'Write Happy Birthday Riya', now(), now()),
    ('dddddddd-0000-0000-0000-000000000002', '00000000-0000-0000-0000-0000000000d1', 'pending_payment', 3000,
     null, null, null),
    ('dddddddd-0000-0000-0000-000000000003', '00000000-0000-0000-0000-0000000000d2', 'paid', 4000,
     null, now(), null),
    ('dddddddd-0000-0000-0000-000000000004', '00000000-0000-0000-0000-0000000000d3', 'collected', 1000,
     null, now(), now());

-- ---------------------------------------------------------------- who may call it
select ok(
    not has_function_privilege('anon', 'public.delete_my_account()', 'execute'),
    'signed-out visitors cannot call delete_my_account'
);
select ok(
    not has_function_privilege('authenticated', 'private.delete_customer_account(uuid)', 'execute'),
    'the app cannot call the internal function with someone else''s id'
);

set local role authenticated;

select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000da"}', true);
select throws_ok(
    $$ select public.delete_my_account() $$,
    'P0001', 'Staff accounts cannot be deleted from the app', 'staff accounts are refused'
);

select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000d2"}', true);
select throws_like(
    $$ select public.delete_my_account() $$,
    'Order #% is paid and waiting for pickup%', 'refused while a paid order waits for pickup'
);

select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000d1"}', true);
select lives_ok($$ select public.delete_my_account() $$, 'a customer can delete their own account');

reset role;

-- ---------------------------------------------------------------- what happened
select is((select count(*) from auth.users where id = '00000000-0000-0000-0000-0000000000d1'), 0::bigint,
          'the sign-in is removed');
select is((select count(*) from public.profiles where id = '00000000-0000-0000-0000-0000000000d1'), 0::bigint,
          'the profile (name, phone) is removed');
select is((select count(*) from public.orders where id in ('dddddddd-0000-0000-0000-000000000001',
                                                           'dddddddd-0000-0000-0000-000000000002')), 2::bigint,
          'past orders are kept as sales records');
select ok((select customer_id is null and customer_removed_at is not null
             from public.orders where id = 'dddddddd-0000-0000-0000-000000000001'),
          'kept orders no longer point at the person');
select is((select pickup_note from public.orders where id = 'dddddddd-0000-0000-0000-000000000001'), null,
          'pickup notes are erased');
select is((select status::text from public.orders where id = 'dddddddd-0000-0000-0000-000000000001'), 'collected',
          'collected orders keep their status');
select is((select status::text from public.orders where id = 'dddddddd-0000-0000-0000-000000000002'), 'cancelled',
          'unpaid orders are cancelled');

select is((select count(*) from auth.users where id = '00000000-0000-0000-0000-0000000000d2'), 1::bigint,
          'the refused customer still exists');
select is((select status::text from public.orders where id = 'dddddddd-0000-0000-0000-000000000003'), 'paid',
          'and their paid order is untouched');
select is((select count(*) from auth.users where id = '00000000-0000-0000-0000-0000000000da'), 1::bigint,
          'the refused staff account still exists');

-- Deleting a customer with orders any other way is still blocked.
select throws_ok(
    $$ delete from auth.users where id = '00000000-0000-0000-0000-0000000000d3' $$,
    '23514', null, 'a customer with orders cannot be deleted directly (bypassing the function)'
);

-- The internal function reports accounts that do not exist.
select throws_ok(
    $$ select private.delete_customer_account('00000000-0000-0000-0000-0000000000ff') $$,
    'P0001', 'Account not found', 'unknown accounts are reported'
);

select * from finish();
rollback;
