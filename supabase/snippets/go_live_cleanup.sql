-- =====================================================================================
-- VSJ BAKERY — go-live cleanup (run ONCE, in Supabase → SQL Editor)
--
-- Removes every TEST sale so real reports start from zero:
--   all orders, payments, refunds, stock movements and stock counts;
--   all stock set to 0 (record real production afterwards);
--   the sample products marked [TEST]; order numbers restart at #1.
-- Keeps: categories, real products with their prices and photos, and all accounts.
--
-- WHEN: after switching Razorpay to LIVE keys, BEFORE the first real customer order.
-- Running it later would delete REAL sales records. It cannot be undone.
-- =====================================================================================
begin;

delete from public.refunds;
delete from public.payments;
delete from public.stock_movements;
delete from public.stock_counts;
delete from public.orders;            -- order items go with them
update public.products set stock_quantity = 0 where stock_quantity <> 0;
delete from public.products where description like '[TEST]%';
alter table public.orders alter column order_number restart with 1;

commit;

-- Check: everything should be 0 except products.
select (select count(*) from public.orders)          as orders,
       (select count(*) from public.payments)        as payments,
       (select count(*) from public.refunds)         as refunds,
       (select count(*) from public.stock_movements) as stock_movements,
       (select count(*) from public.products)        as products;
