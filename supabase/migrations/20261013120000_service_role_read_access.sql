-- =====================================================================================
-- Give the server-side role read access to the app's tables.
--
-- Newer Supabase projects no longer grant table privileges automatically. Our Edge
-- Functions use the service role to look up orders and payments (for example to check
-- that an order belongs to the signed-in customer), so it needs SELECT.
--
-- Read-only on purpose: every change made by the server still goes through the checked
-- database functions (prepare_payment, confirm_payment, …), never direct table writes.
-- The app's own roles (anon, authenticated) are unaffected.
-- =====================================================================================

grant select on all tables in schema public to service_role;

-- Tables added by future migrations get the same read access.
alter default privileges in schema public grant select on tables to service_role;
