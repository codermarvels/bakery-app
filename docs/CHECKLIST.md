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
- ⬜ Password reset by email (planned with the confirmation landing page)

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

## Stage 4 — Payments (test mode) 🟡 (code done — follow `docs/PAYMENTS_SETUP.md`)
- ✅ Razorpay chosen; Key ID and Key Secret kept in Supabase Edge Function secrets (never in the app or Git)
- ✅ Server creates the Razorpay order for the order total stored in the database
- ✅ App opens Razorpay Checkout (UPI, cards, wallets) from **Pay** on unpaid orders and after checkout
- ✅ Server verifies the checkout signature AND asks Razorpay that the money was captured
- ✅ Only then: payment captured + order paid + stock reduced, in one database transaction (safe to repeat)
- ✅ Webhook (signature-checked) confirms payments even if the app closes mid-payment
- ✅ Cancelled / already-paid / sold-out-at-payment orders are refunded automatically
- ✅ Payment functions callable only by the server (service role); 31 new database tests (110 total)
- ✅ Edge Function unit tests (signatures, capture, refunds) in CI; one-click deploy workflow
- ⬜ **You:** run `20261013090000_payments.sql`, add Razorpay test keys as Supabase secrets, deploy functions, add webhook
- ⬜ **You:** test payment with `success@razorpay` on the emulator

## Stage 5 — Administrator ⬜
- ⬜ Add / edit products, categories, prices, photos
- ⬜ Record fresh production (stock in)
- ⬜ Stock adjustment with mandatory reason
- ⬜ Wastage recording
- ⬜ View orders, mark ready / collected
- ⬜ Admin-initiated refunds (automatic refunds for unfulfillable paid orders are done in Stage 4)

## Stage 6 — Reports (read-only role) ⬜
- ⬜ Daily, monthly, custom date-range summaries
- ⬜ Paid order count, gross sales, refunds, net sales, item-wise quantities
- ⬜ PDF export and share
- ⬜ Database-enforced read-only access for the reports role

## Stage 7 — Release ⬜
- ⬜ Instrumented UI tests, security review
- ⬜ Release signing (keystore kept outside Git)
- ⬜ Switch gateway to live mode
- ⬜ Play Store listing
