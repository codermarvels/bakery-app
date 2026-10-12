-- =====================================================================================
-- VSJ BAKERY — new-order alerts on the administrator's phone (push notifications)
--
-- When the administrator signs in, the app registers the phone's Firebase Cloud Messaging
-- token here. When a payment is confirmed by the server, the payments / webhook Edge Functions
-- read these tokens (service role) and send a "New order" notification with sound.
--
-- The token only lets the server send notifications TO this app on that phone; it is not a
-- login and cannot be used to read anything.
-- =====================================================================================

create table public.push_devices (
    token       text primary key check (char_length(token) between 20 and 4096),
    user_id     uuid not null references auth.users (id) on delete cascade,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create index push_devices_user_id_idx on public.push_devices (user_id);

alter table public.push_devices enable row level security;
-- No direct access from the app; only the functions below and the server.
revoke all on public.push_devices from anon, authenticated;
grant select, delete on public.push_devices to service_role;

-- Called by the app after an administrator signs in (and whenever Firebase rotates the token).
create function public.register_push_device(p_token text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    perform private.require_admin();
    if p_token is null or char_length(p_token) not between 20 and 4096 then
        raise exception 'Invalid device token' using errcode = 'P0001';
    end if;
    -- A phone belongs to whoever signed in on it last.
    insert into public.push_devices (token, user_id)
    values (p_token, auth.uid())
    on conflict (token) do update set user_id = excluded.user_id, updated_at = now();
end;
$$;

-- Called by the app before signing out, so a signed-out phone gets no more alerts.
create function public.unregister_push_device(p_token text)
returns void
language sql
security definer
set search_path = ''
as $$
    delete from public.push_devices where token = p_token and user_id = auth.uid();
$$;

revoke all on function public.register_push_device(text) from public, anon;
revoke all on function public.unregister_push_device(text) from public, anon;
grant execute on function public.register_push_device(text) to authenticated;
grant execute on function public.unregister_push_device(text) to authenticated;
