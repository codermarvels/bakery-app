-- Tests for new-order alert devices (push notifications). Run with: supabase test db
begin;
create extension if not exists pgtap with schema extensions;
select plan(9);

insert into auth.users (id, email, raw_user_meta_data) values
    ('00000000-0000-0000-0000-0000000000f1', 'f1@test.local', '{}'),
    ('00000000-0000-0000-0000-0000000000fa', 'fa@test.local', '{}');
update public.profiles set role = 'admin' where id = '00000000-0000-0000-0000-0000000000fa';

select ok(not has_table_privilege('authenticated', 'public.push_devices', 'SELECT'),
          'the app cannot read device tokens');
select ok(has_table_privilege('service_role', 'public.push_devices', 'SELECT'),
          'the server can read device tokens to send alerts');

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000f1"}', true);
select throws_ok(
    $$ select public.register_push_device('customer-token-0000000000000') $$,
    '42501', null, 'customers cannot sign up for order alerts'
);

select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000fa"}', true);
select throws_ok($$ select public.register_push_device('short') $$, 'P0001', 'Invalid device token',
                 'tokens are checked');
select lives_ok($$ select public.register_push_device('admin-phone-token-000000000001') $$,
                'the administrator registers their phone');
select lives_ok($$ select public.register_push_device('admin-phone-token-000000000001') $$,
                'registering again is harmless');

-- Another account cannot remove the admin's phone.
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000f1"}', true);
select public.unregister_push_device('admin-phone-token-000000000001');
reset role;
select is((select count(*) from public.push_devices), 1::bigint, 'only the owner can unregister a phone');

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000fa"}', true);
select public.unregister_push_device('admin-phone-token-000000000001');
reset role;
select is((select count(*) from public.push_devices), 0::bigint, 'signing out removes the phone');

-- Deleting the account removes its phones.
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000fa"}', true);
select public.register_push_device('admin-phone-token-000000000002');
reset role;
delete from auth.users where id = '00000000-0000-0000-0000-0000000000fa';
select is((select count(*) from public.push_devices), 0::bigint, 'phones go with the account');

select * from finish();
rollback;
