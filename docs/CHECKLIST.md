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

## Stage 2 — Backend & sign-in ⬜
- ⬜ Supabase project connected (URL + anon key via `local.properties`, never committed)
- ⬜ Migrations: profiles & roles, categories, products, stock ledger, orders, order items, payments, refunds
- ⬜ Row Level Security on every table; role checks in server functions
- ⬜ Storage bucket for product photos (public read, admin-only write)
- ⬜ Starter categories seeded
- ⬜ Email/password sign-in, sign-out, session persistence
- ⬜ Role-based navigation (customer / admin / reports)

## Stage 3 — Catalogue & cart ⬜
- ⬜ Products by category with photo, name, price, availability
- ⬜ Cart: add, change quantity, remove, total
- ⬜ Place pickup order (prices recalculated on the server)
- ⬜ Customer order history and status

## Stage 4 — Payments (test mode) ⬜
- ⬜ Gateway account in test mode; secret key stored only in Supabase secrets
- ⬜ Server creates gateway order for the server-calculated amount
- ⬜ App checkout (UPI and other methods)
- ⬜ Server verifies payment signature + webhook; only then marks order paid
- ⬜ Stock deducted atomically when the order becomes paid

## Stage 5 — Administrator ⬜
- ⬜ Add / edit products, categories, prices, photos
- ⬜ Record fresh production (stock in)
- ⬜ Stock adjustment with mandatory reason
- ⬜ Wastage recording
- ⬜ View orders, mark ready / collected
- ⬜ Refunds through the gateway (server-side)

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
