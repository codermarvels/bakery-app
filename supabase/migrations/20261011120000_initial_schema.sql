-- =====================================================================================
-- VSJ BAKERY — initial schema, roles and Row Level Security
--
-- Security model in one paragraph:
--   * Every signed-up user gets a row in public.profiles with role 'customer'.
--     Nobody can change their own role from the app; roles are changed only from the
--     Supabase dashboard (SQL editor), which runs with full database rights.
--   * Every table has Row Level Security (RLS) switched on, and table privileges are
--     reduced to the minimum each kind of user needs. RLS policies then decide which
--     rows each role may see or change.
--   * 'admin'   — manages categories and products and records stock via functions.
--   * 'reports' — read-only: may read sales data but has no write privilege anywhere.
--   * Stock is a ledger (public.stock_movements). products.stock_quantity is updated
--     only by a trigger on that ledger and can never go below zero.
--   * Orders, payments and refunds are written only by server-side code (service role
--     or security-definer functions added in later stages), never directly by the app.
-- Money is stored in paise (1 rupee = 100 paise) as whole numbers.
-- =====================================================================================

-- Helper functions live in a schema that the public REST API does not expose.
create schema if not exists private;
revoke all on schema private from public;
grant usage on schema private to anon, authenticated, service_role;

-- -------------------------------------------------------------------------------------
-- Types
-- -------------------------------------------------------------------------------------
create type public.app_role as enum ('customer', 'admin', 'reports');

create type public.stock_movement_type as enum (
    'production',      -- freshly prepared stock (positive)
    'sale',            -- deducted when an order is paid (negative)
    'adjustment',      -- manual correction with a reason (either sign)
    'wastage',         -- spoiled / discarded stock with a reason (negative)
    'refund_restock'   -- returned to stock after a refund (positive)
);

create type public.order_status as enum (
    'pending_payment', 'paid', 'ready_for_pickup', 'collected', 'cancelled'
);

create type public.payment_status as enum ('created', 'captured', 'failed');

create type public.refund_status as enum ('pending', 'processed', 'failed');

-- -------------------------------------------------------------------------------------
-- Shared trigger: keep updated_at current
-- -------------------------------------------------------------------------------------
create function private.touch_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

-- -------------------------------------------------------------------------------------
-- Profiles and roles
-- -------------------------------------------------------------------------------------
create table public.profiles (
    id          uuid primary key references auth.users (id) on delete cascade,
    full_name   text not null default '' check (char_length(full_name) <= 100),
    phone       text check (phone is null or phone ~ '^[0-9+ -]{7,20}$'),
    role        public.app_role not null default 'customer',
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create trigger profiles_touch_updated_at
    before update on public.profiles
    for each row execute function private.touch_updated_at();

-- The signed-in user's role, or null when signed out. SECURITY DEFINER so policies can
-- call it without needing (and without recursing into) the profiles RLS policies.
create function private.user_role()
returns public.app_role
language sql
stable
security definer
set search_path = ''
as $$
    select p.role from public.profiles p where p.id = auth.uid();
$$;

create function private.is_admin()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select coalesce(private.user_role() = 'admin', false);
$$;

-- Staff = admin or reports. Used for read access to sales data.
create function private.is_staff()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select coalesce(private.user_role() in ('admin', 'reports'), false);
$$;


-- Create a profile for every new auth user. The role is ALWAYS 'customer' here, whatever
-- the sign-up request contained, so nobody can sign themselves up as admin.
create function private.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.profiles (id, full_name)
    values (new.id, left(btrim(coalesce(new.raw_user_meta_data ->> 'full_name', '')), 100));
    return new;
end;
$$;

create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function private.handle_new_user();

alter table public.profiles enable row level security;
revoke all on public.profiles from anon, authenticated;
grant select on public.profiles to authenticated;
-- Column-level grant: users may edit their name and phone, never their role.
grant update (full_name, phone) on public.profiles to authenticated;

create policy "profiles: users read their own"
    on public.profiles for select to authenticated
    using (id = (select auth.uid()));

create policy "profiles: admins read all"
    on public.profiles for select to authenticated
    using ((select private.is_admin()));

create policy "profiles: users update their own"
    on public.profiles for update to authenticated
    using (id = (select auth.uid()))
    with check (id = (select auth.uid()));

-- -------------------------------------------------------------------------------------
-- Categories
-- -------------------------------------------------------------------------------------
create table public.categories (
    id          uuid primary key default gen_random_uuid(),
    slug        text not null unique check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    name        text not null check (char_length(btrim(name)) between 1 and 60),
    tagline     text not null default '' check (char_length(tagline) <= 120),
    sort_order  integer not null default 0,
    is_active   boolean not null default true,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create trigger categories_touch_updated_at
    before update on public.categories
    for each row execute function private.touch_updated_at();

alter table public.categories enable row level security;
revoke all on public.categories from anon, authenticated;
grant select on public.categories to anon, authenticated;
grant insert, update, delete on public.categories to authenticated;

create policy "categories: everyone reads active, staff read all"
    on public.categories for select to anon, authenticated
    using (is_active or (select private.is_staff()));

create policy "categories: admins insert"
    on public.categories for insert to authenticated
    with check ((select private.is_admin()));

create policy "categories: admins update"
    on public.categories for update to authenticated
    using ((select private.is_admin()))
    with check ((select private.is_admin()));

create policy "categories: admins delete"
    on public.categories for delete to authenticated
    using ((select private.is_admin()));

-- -------------------------------------------------------------------------------------
-- Products
-- -------------------------------------------------------------------------------------
create table public.products (
    id              uuid primary key default gen_random_uuid(),
    category_id     uuid not null references public.categories (id) on delete restrict,
    name            text not null check (char_length(btrim(name)) between 1 and 100),
    description     text not null default '' check (char_length(description) <= 1000),
    -- Price per item in paise. Upper bound ₹1,00,000 guards against typing mistakes.
    price_paise     bigint not null check (price_paise between 0 and 10000000),
    -- Path of the photo inside the 'product-images' storage bucket.
    image_path      text check (image_path is null or char_length(image_path) <= 300),
    is_active       boolean not null default true,
    -- Maintained ONLY by the stock ledger trigger below.
    stock_quantity  integer not null default 0 check (stock_quantity >= 0),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);

create index products_category_id_idx on public.products (category_id);

create trigger products_touch_updated_at
    before update on public.products
    for each row execute function private.touch_updated_at();

alter table public.products enable row level security;
revoke all on public.products from anon, authenticated;
grant select on public.products to anon, authenticated;
-- stock_quantity is deliberately missing: stock changes only through the ledger.
grant insert (category_id, name, description, price_paise, image_path, is_active)
    on public.products to authenticated;
grant update (category_id, name, description, price_paise, image_path, is_active)
    on public.products to authenticated;
grant delete on public.products to authenticated;

create policy "products: everyone reads active, staff read all"
    on public.products for select to anon, authenticated
    using (is_active or (select private.is_staff()));

create policy "products: admins insert"
    on public.products for insert to authenticated
    with check ((select private.is_admin()));

create policy "products: admins update"
    on public.products for update to authenticated
    using ((select private.is_admin()))
    with check ((select private.is_admin()));

create policy "products: admins delete"
    on public.products for delete to authenticated
    using ((select private.is_admin()));

-- -------------------------------------------------------------------------------------
-- Orders and order items (rows are created by server-side code from Stage 3)
-- -------------------------------------------------------------------------------------
create table public.orders (
    id            uuid primary key default gen_random_uuid(),
    order_number  bigint generated always as identity unique,
    -- Orders are financial records, so a customer with orders cannot be hard-deleted.
    customer_id   uuid not null references auth.users (id) on delete restrict,
    status        public.order_status not null default 'pending_payment',
    total_paise   bigint not null check (total_paise >= 0),
    pickup_note   text check (pickup_note is null or char_length(pickup_note) <= 300),
    paid_at       timestamptz,
    ready_at      timestamptz,
    collected_at  timestamptz,
    cancelled_at  timestamptz,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);

create index orders_customer_id_idx on public.orders (customer_id);
create index orders_paid_at_idx on public.orders (paid_at);
create index orders_status_idx on public.orders (status);

create trigger orders_touch_updated_at
    before update on public.orders
    for each row execute function private.touch_updated_at();

create table public.order_items (
    id                 bigint generated always as identity primary key,
    order_id           uuid not null references public.orders (id) on delete cascade,
    product_id         uuid not null references public.products (id) on delete restrict,
    -- Name and price are copied at order time so later edits never change old orders.
    product_name       text not null,
    unit_price_paise   bigint not null check (unit_price_paise >= 0),
    quantity           integer not null check (quantity between 1 and 1000),
    line_total_paise   bigint not null,
    constraint order_items_line_total_matches check (line_total_paise = unit_price_paise * quantity),
    constraint order_items_one_line_per_product unique (order_id, product_id)
);

create index order_items_product_id_idx on public.order_items (product_id);

alter table public.orders enable row level security;
alter table public.order_items enable row level security;
revoke all on public.orders, public.order_items from anon, authenticated;
grant select on public.orders, public.order_items to authenticated;

create policy "orders: customers read their own, staff read all"
    on public.orders for select to authenticated
    using (customer_id = (select auth.uid()) or (select private.is_staff()));

create policy "order items: visible with their order"
    on public.order_items for select to authenticated
    using (
        (select private.is_staff())
        or exists (
            select 1 from public.orders o
            where o.id = order_items.order_id and o.customer_id = (select auth.uid())
        )
    );

-- -------------------------------------------------------------------------------------
-- Stock ledger
-- -------------------------------------------------------------------------------------
create table public.stock_movements (
    id              bigint generated always as identity primary key,
    product_id      uuid not null references public.products (id) on delete restrict,
    movement_type   public.stock_movement_type not null,
    quantity_delta  integer not null check (quantity_delta <> 0),
    reason          text check (reason is null or char_length(reason) <= 300),
    order_id        uuid references public.orders (id) on delete restrict,
    created_by      uuid references auth.users (id) on delete set null default auth.uid(),
    created_at      timestamptz not null default now(),
    constraint stock_movements_sign_matches_type check (
        (movement_type in ('production', 'refund_restock') and quantity_delta > 0)
        or (movement_type in ('sale', 'wastage') and quantity_delta < 0)
        or movement_type = 'adjustment'
    ),
    constraint stock_movements_reason_required check (
        movement_type not in ('adjustment', 'wastage')
        or (reason is not null and char_length(btrim(reason)) > 0)
    ),
    constraint stock_movements_sale_has_order check (
        movement_type not in ('sale', 'refund_restock') or order_id is not null
    )
);

create index stock_movements_product_created_idx on public.stock_movements (product_id, created_at);
create index stock_movements_order_id_idx on public.stock_movements (order_id);

-- Apply each ledger row to the product's running stock. The products check constraint
-- (stock_quantity >= 0) makes the whole insert fail if stock would go negative.
create function private.apply_stock_movement()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    update public.products
       set stock_quantity = stock_quantity + new.quantity_delta
     where id = new.product_id;
    return new;
end;
$$;

create trigger stock_movements_apply
    after insert on public.stock_movements
    for each row execute function private.apply_stock_movement();

alter table public.stock_movements enable row level security;
revoke all on public.stock_movements from anon, authenticated;
-- Read-only for staff. No one writes to the ledger directly from the app; the functions
-- below (and server-side order processing later) are the only writers. Rows are never
-- updated or deleted, so the ledger is a permanent audit trail.
grant select on public.stock_movements to authenticated;

create policy "stock movements: staff read"
    on public.stock_movements for select to authenticated
    using ((select private.is_staff()));

-- Administrator stock functions --------------------------------------------------------

create function private.require_admin()
returns void
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if not private.is_admin() then
        raise exception 'Only administrators can change stock' using errcode = '42501';
    end if;
end;
$$;

create function public.record_production(p_product_id uuid, p_quantity integer, p_note text default null)
returns bigint
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_id bigint;
begin
    perform private.require_admin();
    if p_quantity is null or p_quantity < 1 or p_quantity > 100000 then
        raise exception 'Quantity must be between 1 and 100000' using errcode = '22023';
    end if;
    insert into public.stock_movements (product_id, movement_type, quantity_delta, reason, created_by)
    values (p_product_id, 'production', p_quantity, nullif(btrim(p_note), ''), auth.uid())
    returning id into v_id;
    return v_id;
end;
$$;

create function public.adjust_stock(p_product_id uuid, p_quantity_delta integer, p_reason text)
returns bigint
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_id bigint;
begin
    perform private.require_admin();
    if p_quantity_delta is null or p_quantity_delta = 0 or abs(p_quantity_delta) > 100000 then
        raise exception 'Adjustment must be a non-zero number up to 100000' using errcode = '22023';
    end if;
    if p_reason is null or char_length(btrim(p_reason)) = 0 then
        raise exception 'A reason is required for stock adjustments' using errcode = '22023';
    end if;
    insert into public.stock_movements (product_id, movement_type, quantity_delta, reason, created_by)
    values (p_product_id, 'adjustment', p_quantity_delta, btrim(p_reason), auth.uid())
    returning id into v_id;
    return v_id;
end;
$$;

create function public.record_wastage(p_product_id uuid, p_quantity integer, p_reason text)
returns bigint
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_id bigint;
begin
    perform private.require_admin();
    if p_quantity is null or p_quantity < 1 or p_quantity > 100000 then
        raise exception 'Quantity must be between 1 and 100000' using errcode = '22023';
    end if;
    if p_reason is null or char_length(btrim(p_reason)) = 0 then
        raise exception 'A reason is required for wastage' using errcode = '22023';
    end if;
    insert into public.stock_movements (product_id, movement_type, quantity_delta, reason, created_by)
    values (p_product_id, 'wastage', -p_quantity, btrim(p_reason), auth.uid())
    returning id into v_id;
    return v_id;
end;
$$;

revoke all on function public.record_production(uuid, integer, text) from public, anon;
revoke all on function public.adjust_stock(uuid, integer, text) from public, anon;
revoke all on function public.record_wastage(uuid, integer, text) from public, anon;
grant execute on function public.record_production(uuid, integer, text) to authenticated;
grant execute on function public.adjust_stock(uuid, integer, text) to authenticated;
grant execute on function public.record_wastage(uuid, integer, text) to authenticated;

-- -------------------------------------------------------------------------------------
-- Payments and refunds (written only by server-side code with the service role)
-- -------------------------------------------------------------------------------------
create table public.payments (
    id                   uuid primary key default gen_random_uuid(),
    order_id             uuid not null references public.orders (id) on delete restrict,
    provider             text not null check (char_length(provider) <= 40),
    provider_order_id    text unique,
    provider_payment_id  text unique,
    amount_paise         bigint not null check (amount_paise > 0),
    currency             text not null default 'INR' check (currency = 'INR'),
    status               public.payment_status not null default 'created',
    verified_at          timestamptz,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),
    -- A payment can only be 'captured' once the server has verified it.
    constraint payments_captured_is_verified check (status <> 'captured' or verified_at is not null)
);

create index payments_order_id_idx on public.payments (order_id);

create trigger payments_touch_updated_at
    before update on public.payments
    for each row execute function private.touch_updated_at();

create table public.refunds (
    id                  uuid primary key default gen_random_uuid(),
    order_id            uuid not null references public.orders (id) on delete restrict,
    payment_id          uuid not null references public.payments (id) on delete restrict,
    amount_paise        bigint not null check (amount_paise > 0),
    reason              text not null check (char_length(btrim(reason)) between 1 and 300),
    status              public.refund_status not null default 'pending',
    provider_refund_id  text unique,
    created_by          uuid references auth.users (id) on delete set null,
    created_at          timestamptz not null default now(),
    processed_at        timestamptz
);

create index refunds_order_id_idx on public.refunds (order_id);
create index refunds_processed_at_idx on public.refunds (processed_at);

alter table public.payments enable row level security;
alter table public.refunds enable row level security;
revoke all on public.payments, public.refunds from anon, authenticated;
grant select on public.payments, public.refunds to authenticated;

create policy "payments: customers read their own, staff read all"
    on public.payments for select to authenticated
    using (
        (select private.is_staff())
        or exists (
            select 1 from public.orders o
            where o.id = payments.order_id and o.customer_id = (select auth.uid())
        )
    );

create policy "refunds: customers read their own, staff read all"
    on public.refunds for select to authenticated
    using (
        (select private.is_staff())
        or exists (
            select 1 from public.orders o
            where o.id = refunds.order_id and o.customer_id = (select auth.uid())
        )
    );

-- -------------------------------------------------------------------------------------
-- Lock down helper functions: only the role checks used inside RLS policies are callable.
-- -------------------------------------------------------------------------------------
revoke all on all functions in schema private from public, anon, authenticated;
grant execute on function private.user_role(), private.is_admin(), private.is_staff()
    to anon, authenticated, service_role;
