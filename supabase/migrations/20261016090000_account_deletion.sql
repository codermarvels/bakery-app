-- =====================================================================================
-- VSJ BAKERY — Stage 7: customers can delete their account (a Google Play requirement)
--
-- What deleting an account does:
--   * the sign-in (auth.users) and profile (name, phone) are removed for good;
--   * unpaid orders are cancelled (if a payment for one still arrives, it is refunded
--     automatically, as for any cancelled order);
--   * past orders stay, because they are the bakery's sales records, but they no longer
--     point at a person and their pickup notes are erased.
--
-- Refused while an order is paid but not yet collected, so nobody loses food they paid for.
-- Staff accounts cannot be deleted this way (change their role to customer first).
-- =====================================================================================

alter table public.orders add column customer_removed_at timestamptz;

-- App orders keep their customer unless the account was deleted through this function.
alter table public.orders drop constraint orders_app_orders_have_customer;
alter table public.orders add constraint orders_app_orders_have_customer
    check (channel = 'counter' or customer_id is not null or customer_removed_at is not null);

-- Deleting a user now clears the link instead of being blocked. The check above still
-- blocks deleting a customer with app orders any other way (e.g. by mistake in the dashboard).
alter table public.orders drop constraint orders_customer_id_fkey;
alter table public.orders add constraint orders_customer_id_fkey
    foreign key (customer_id) references auth.users (id) on delete set null;

-- -------------------------------------------------------------------------------------
-- private.delete_customer_account: does the work. Also used by the owner in the SQL editor
-- to handle a deletion request received by email (see docs/GO_LIVE.md).
-- -------------------------------------------------------------------------------------
create function private.delete_customer_account(p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_role public.app_role;
    v_open bigint;
begin
    select role into v_role from public.profiles where id = p_user_id for update;
    if not found then
        raise exception 'Account not found' using errcode = 'P0001';
    end if;
    if v_role <> 'customer' then
        raise exception 'Staff accounts cannot be deleted from the app' using errcode = 'P0001';
    end if;

    select order_number into v_open
      from public.orders
     where customer_id = p_user_id and status in ('paid', 'ready_for_pickup')
     order by order_number
     limit 1;
    if v_open is not null then
        raise exception 'Order #% is paid and waiting for pickup. Collect it first (or ask the bakery for a refund), then delete your account.', v_open
            using errcode = 'P0001';
    end if;

    update public.orders
       set status = 'cancelled', cancelled_at = now()
     where customer_id = p_user_id and status = 'pending_payment';

    update public.orders
       set customer_removed_at = now(), pickup_note = null
     where customer_id = p_user_id;

    -- Removes the sign-in; the profile goes with it (on delete cascade) and the
    -- orders' customer_id becomes null (on delete set null).
    delete from auth.users where id = p_user_id;
end;
$$;

revoke all on function private.delete_customer_account(uuid) from public, anon, authenticated;

-- -------------------------------------------------------------------------------------
-- public.delete_my_account: what the app calls. Only ever deletes the caller's own account.
-- -------------------------------------------------------------------------------------
create function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user uuid := auth.uid();
begin
    if v_user is null then
        raise exception 'Sign in first' using errcode = '42501';
    end if;
    perform private.delete_customer_account(v_user);
end;
$$;

revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;
