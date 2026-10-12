# VSJ BAKERY — Feature checklist

Legend: ✅ done · 🟡 partly done · ⬜ pending

## Stage 1 — Foundation ✅
- ✅ Android project (Kotlin, Jetpack Compose, Material 3), single `app` module
- ✅ Gradle wrapper 8.14.3, version catalog (`gradle/libs.versions.toml`), AGP 8.9.1, Kotlin 2.1.20
- ✅ Brand theme: cream / chocolate brown / gold, light + dark, serif headings
- ✅ Adaptive launcher icon (gold cupcake on brown)
- ✅ Type-safe navigation with bottom bar (Home, Cart, Account)
- ✅ Home screen: hero banner, pickup-only info, 6 category cards, menu status card
- ✅ Category, Cart (empty state), Account (customer / admin / reports), Sign-in screens (sign-in disabled until Stage 2)
- ✅ Money formatter in paise with Indian digit grouping (+ unit tests)
- ✅ GitHub Actions CI: unit tests, lint, debug APK build and download

## Stage 2 — Backend & sign-in ✅
- ✅ Migrations: profiles & roles, categories, products, stock ledger, orders, order items, payments, refunds
- ✅ Row Level Security on every table + minimal table/column privileges
- ✅ Roles: new accounts are always customers; admin/reports granted only from the dashboard
- ✅ Stock ledger: admin-only `record_production`, `adjust_stock` (reason required), `record_wastage` (reason required); stock can't go below zero or be edited directly
- ✅ Orders, payments, refunds: no app-side writes at all (server-only, Stage 3/4)
- ✅ Storage bucket `product-images` (public view, admin-only upload, 5 MB, JPEG/PNG/WebP)
- ✅ Six starter categories seeded
- ✅ 41 automated security tests (pgTAP) + CI job on a real Supabase database
- ✅ App: email/password sign-in, customer sign-up, sign-out, session kept between launches
- ✅ App: role read from the server; staff entries refuse the wrong role; admin & reports dashboards (tools listed, switched on in Stages 5–6)
- ✅ Supabase URL + publishable key committed in `gradle.properties` (public by design); overridable; CI blocks secret keys
- ✅ Migrations applied to the live Supabase project
- ✅ Admin and reports accounts created; customer, admin and reports sign-in confirmed on the emulator
- ✅ Password reset with an emailed one-time code (Stage 7)

## Stage 3 — Catalogue & cart ✅
- ✅ Products by category from Supabase: photo (or category icon), name, price, availability ("In stock" / "Only 3 left" / "Sold out")
- ✅ Home: live categories with item counts, "Fresh today" row, pull-to-refresh
- ✅ Cart: add, change quantity (capped at stock), remove, total, cart badge on the bottom bar
- ✅ Cart flags items that sold out or dropped below the cart quantity, with one-tap fix
- ✅ Place pickup order: server-side `place_order` recalculates prices and total, checks stock; app sends ids + quantities only
- ✅ Orders start as "Awaiting payment"; stock is not touched until payment is verified (Stage 4)
- ✅ My orders (customer): list with items, total, status; cancel unpaid orders
- ✅ One unpaid order per customer: new checkouts are added to it (stock re-checked, current prices); orders with a payment in progress are never changed
- ✅ Staff accounts cannot place orders
- ✅ 38 ordering tests + 41 security tests (79 database tests) + cart/order unit tests
- ✅ `20261012090000_place_order.sql` applied; ordering confirmed on the emulator
- ✅ Merge-into-unpaid-order applied and confirmed on the emulator
- ✅ Test products added
- ⬜ Cart is kept in memory only (cleared if Android closes the app) — persistence can be added later

## Stage 4 — Payments (test mode) ✅
- ✅ Razorpay chosen; Key ID and Key Secret kept in Supabase Edge Function secrets (never in the app or Git)
- ✅ Server creates the Razorpay order for the order total stored in the database
- ✅ App opens Razorpay Checkout (UPI, cards, wallets) from **Pay** on unpaid orders and after checkout
- ✅ Server verifies the checkout signature AND asks Razorpay that the money was captured
- ✅ Only then: payment captured + order paid + stock reduced, in one database transaction (safe to repeat)
- ✅ Webhook (signature-checked) confirms payments even if the app closes mid-payment
- ✅ Cancelled / already-paid / sold-out-at-payment orders are refunded automatically
- ✅ Payment functions callable only by the server (service role); 31 new database tests (110 total)
- ✅ Edge Function unit tests (signatures, capture, refunds) in CI; one-click deploy workflow
- ✅ Safety net: before paying and after a phone-side error, the server asks Razorpay whether the order was already paid (never charges twice)
- ✅ Server role has read-only table access (newer Supabase projects don't grant it automatically)
- ✅ Migrations applied, keys stored as Supabase secrets, functions deployed
- ✅ Test payment confirmed on the emulator: payment captured, order paid, stock reduced by the server
- ⬜ Confirm the Razorpay webhook deliveries succeed (Razorpay → Webhooks → delivery list)
- ⬜ UPI option: enable in Razorpay → Payment methods, and test on a real phone with a UPI app

## Stage 5 — Administrator ✅
- ✅ Admin dashboard with four tabs, each with an ⓘ "what to enter" button
- ✅ **Counter** tab: walk-in sales (items, quantities, Cash / UPI / Card, note); stock reduced immediately; today's totals by payment method; undo with reason (restocks, records refund)
- ✅ **Orders** tab: app orders by status (to prepare / ready / collected / unpaid / cancelled); mark ready, mark collected
- ✅ **Stock** tab: + made (production), wastage (reason required), adjust (reason required), per-product history
- ✅ End-of-day count: counted quantity becomes stock; unrecorded differences shown with their value and stored for reports
- ✅ **Products** tab: add/edit products (category, price in ₹, description, show in app) with photo upload (auto-resized)
- ✅ Categories: add, rename, reorder, hide
- ✅ All admin actions are administrator-only in the database; reports user can read but not change (42 new tests, 155 total)
- ✅ Admin guide: `docs/ADMIN_GUIDE.md`
- ✅ Migration applied and every tab tested on the emulator

## Stage 6 — Reports (read-only role) ✅
- ✅ `sales_report(from, to)` database function: staff only, read-only, Indian-time days, max one year
- ✅ Today, Yesterday, This month, Last month and Custom (date-range picker)
- ✅ Paid orders (app / counter), gross sales, refunds, net sales, money by payment method,
  item-wise quantities, daily breakdown, production, wastage and end-of-day count differences
- ✅ Export PDF (A4, multi-page) and share through any app (WhatsApp, Gmail, Drive…)
- ✅ Reports account and administrator can both open it; everyone else is refused by the database
- ✅ 29 new database tests (184 in total) and app unit tests for the report parser
- ✅ Migration applied; reports and PDF export confirmed on the emulator

## Stage 7 — Release & go-live 🟡 (code done — your steps are in `docs/GO_LIVE.md`)
- ✅ **Delete my account** (Google Play requirement): removes sign-in, name and phone; cancels unpaid
  orders; keeps past sales anonymously; refused while a paid order awaits pickup; staff excluded
- ✅ **Forgot password** with an emailed one-time code
- ✅ **Admin refunds** of paid app orders through Razorpay, recorded in the database (reports and
  stock stay right), optional restock, safe retry that never refunds twice
- ✅ Policy website (`site/`): terms, cancellation & refund, pickup (no delivery), privacy, contact,
  account deletion; published by GitHub Pages only when the bakery details are filled in
- ✅ Account tab links to the policies
- ✅ Release build: R8 shrinking, signing from GitHub secrets / `local.properties` (never in Git),
  signed `.aab` + `.apk` + `mapping.txt` built by CI; version 1.0.0
- ✅ Go-live cleanup script for test data (`supabase/snippets/go_live_cleanup.sql`)
- ✅ 35 new database tests (219 in total), 4 new payment-function tests (16 in total), app unit tests
- ✅ Security review of the new server paths (role checks in database and function, no secrets in app)
- ⬜ **You:** steps 1–7 of `docs/GO_LIVE.md` (migrations, SMTP + reset email, policy details,
  Razorpay KYC & live keys, cleanup, upload key, Play Console)
- ✅ Customers see refunds in *My orders* (amount, date, reason)
- ✅ **New-order alerts** with sound on the admin phone (Firebase push), sent only after the server
  verifies a payment; tap opens Admin → Orders (`docs/ORDER_ALERTS.md`)
- ⬜ **You:** Firebase setup in `docs/ORDER_ALERTS.md`
- ⬜ Later (optional): instrumented UI tests
