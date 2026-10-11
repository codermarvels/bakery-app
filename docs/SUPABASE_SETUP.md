# Supabase setup (Stage 2)

Follow these steps once. They take about 15 minutes. Everything happens in the Supabase dashboard
at <https://supabase.com/dashboard> → your project.

> **Never share or commit** the `service_role` / `sb_secret_…` key or the database password.
> The app only uses the **publishable** key (`sb_publishable_…`), which is safe to ship inside the app
> because the database's Row Level Security decides what it can do.

---

## 1. Create the database tables and security rules

1. Open **SQL Editor** (left sidebar) → **New query**.
2. Open each file below from this repository, copy **all** of its text, paste it into the editor and
   click **Run**. Do them **in this order**, one at a time, and wait for "Success" each time:
   1. `supabase/migrations/20261011120000_initial_schema.sql`
   2. `supabase/migrations/20261011120100_storage_product_images.sql`
   3. `supabase/migrations/20261011120200_seed_categories.sql`
3. Check the result:
   - **Table Editor** shows `profiles`, `categories`, `products`, `stock_movements`, `orders`,
     `order_items`, `payments`, `refunds`. `categories` has 6 rows.
   - **Storage** shows a public bucket called `product-images`.

If a file fails halfway, tell me the exact error message. Don't run it twice: the second run
fails with "already exists".

*Alternative for developers:* install the [Supabase CLI](https://supabase.com/docs/guides/cli), then
run `supabase link --project-ref <your-project-ref>` and `supabase db push` from the repository folder.
The project ref is the `xxxx` part of `https://xxxx.supabase.co`.

## 2. Email sign-in settings

**Authentication → Sign In / Providers → Email**
- Keep **Email** enabled.
- Set **Minimum password length** to **8**. The app asks for at least 8.
- **Confirm email**:
  - **On (recommended for real customers):** after signing up, a customer must click the link in
    the email before they can sign in. The link confirms the account even if the page it opens
    afterwards looks blank. A proper landing page comes later.
  - **Off (handy while testing):** customers can sign in immediately after signing up.

## 3. Create the staff accounts

Staff can't sign themselves up as admin; that's on purpose. Create them like this:

1. **Authentication → Users → Add user → Create new user.** Enter the email and a strong password,
   tick **Auto Confirm User**, then click **Create user**. Do this once for the administrator and once
   for the reports user.
2. **SQL Editor → New query**, replace the two emails, then **Run**:

   ```sql
   update public.profiles set role = 'admin'
   where id = (select id from auth.users where email = 'owner@example.com');

   update public.profiles set role = 'reports'
   where id = (select id from auth.users where email = 'accounts@example.com');

   -- Check: should list both people with their roles
   select u.email, p.role from public.profiles p join auth.users u on u.id = p.id
   where p.role <> 'customer';
   ```

Roles can only be changed here in the dashboard, never from the app. To remove someone's staff access,
set their role back to `'customer'`.

## 4. The app's connection settings (already done)

The project URL and the **publishable** key are stored in `gradle.properties`
(`vsj.supabase.url`, `vsj.supabase.publishableKey`), so every build connects automatically.
That is safe: the publishable key is designed to ship inside apps, and Row Level Security
limits what it can do.

- To point a build at a different project, put `supabase.url=` and `supabase.publishableKey=`
  in `local.properties`, or set the GitHub repository variables `SUPABASE_URL` /
  `SUPABASE_PUBLISHABLE_KEY`. Either one overrides the defaults.
- If you ever rotate the publishable key in Supabase, update `gradle.properties`.
- The CI build fails if a secret key (`sb_secret_…` / `service_role`) is ever committed.

## 5. Try it

1. **Uninstall the old app first**, then install the new APK (GitHub → **Actions** → latest
   **Android build** → `vsj-bakery-debug-apk`). Each GitHub build is signed differently, so
   Android won't install one over another.
2. **Account → Customer → Create a customer account**, then sign in.
3. Sign out, then **Account → Administrator** and sign in with the admin account. You land on
   the admin dashboard.
4. Try the reports account through **Administrator**. It's refused with
   "This account does not have administrator access". Through **Reports** it works.

## 6. Stage 3 — ordering (do this once)

1. **SQL Editor → New query:** paste and **Run**
   `supabase/migrations/20261012090000_place_order.sql`. This adds the secure ordering function.
2. **Optional test products:** paste and run `supabase/snippets/test_products.sql`. It adds seven
   sample items (marked `[TEST]` in their description) with opening stock, so the shop has
   something to show. To hide them later, run:
   `update public.products set is_active = false where description like '[TEST]%';`
3. **Optional photos:** **Storage → product-images → Upload file**, for example `cakes/black-forest.jpg`.
   Then in **Table Editor → products**, set that product's `image_path` to the same path
   (`cakes/black-forest.jpg`). Products without a photo show their category icon. The admin
   screens in Stage 5 will do this from the app.

How ordering works:
- The app sends only product IDs and quantities. The server looks up the current prices, checks
  each item is on sale and in stock, and works out the total itself.
- New orders are **awaiting payment**. Stock is **not** reduced yet. It's reduced only when a
  payment is verified by the server (Stage 4).
- Customers can cancel their own unpaid orders. One customer can have at most 5 unpaid orders a day.
- Admin and reports accounts can't place orders, so staff accounts stay clean.

## How the security works (plain language)

- **Row Level Security (RLS)** is a filter the database applies to every request. Even if someone
  copied the publishable key out of the app, the database would still only show them what their role
  allows.
- **Customers** see active products and categories, plus only their own profile and orders.
- **Reports users** can read orders, payments, refunds, products and stock history. They have **no
  write permission on anything**, which the database enforces.
- **Administrators** can manage categories and products, and change stock only through the
  `record_production`, `adjust_stock` (reason required) and `record_wastage` (reason required)
  functions. Each change is stored permanently in `stock_movements`.
- **Nobody** can mark an order as paid, record a payment or create a refund from the app. Only
  server code with secret keys can do that (Stage 4).
- Stock can never go below zero.

These rules are checked by 68 automated tests in `supabase/tests/database/` (run with `supabase test db`).
