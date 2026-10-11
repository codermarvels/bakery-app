# VSJ BAKERY — Implementation plan

The app is built in stages. Each stage ends with a working, buildable app.

| Stage | What gets built | Needs from you |
|------:|-----------------|----------------|
| **1** | Android project, Gradle setup, Material 3 cream/brown/gold theme, navigation, home / category / cart / account / sign-in screens, CI build | Nothing |
| **2** | Supabase backend: database migrations (users & roles, categories, products, stock ledger, orders, payments, refunds), Row Level Security, product-image storage, starter categories. Sign-in in the app with role-based screens (customer / admin / reports) | A Supabase project (URL + anon key) |
| **3** | Customer catalogue & cart: products from Supabase with photos, prices and availability; cart with quantities and total; pickup order creation on the server (server recalculates all prices) | — |
| **4** | Payments (test mode): payment gateway checkout, server-side verification (signature check + webhook), order marked **paid only by the server**, stock deducted in the same database transaction | Payment gateway test keys |
| **5** | Administrator tools: add/edit products, categories, prices, photos; record fresh production; stock adjustments with reason; wastage; view and update orders; refunds | — |
| **6** | Reporting role: daily / monthly / custom-range summaries (paid orders, gross sales, refunds, net sales, item-wise quantities), PDF export and sharing. Read-only, enforced by the database | — |
| **7** | Hardening & release: more tests, security review, release signing, Play Store listing, switch payments to live mode | Signing key, Play Console account, live gateway keys |

## Key design decisions

- **Money is stored in paise** (whole numbers) everywhere — app and database — to avoid rounding errors.
- **The server is the source of truth.** The app never decides prices, payment status or stock levels.
  Supabase Row Level Security and server-side functions enforce every permission, so a modified app
  still cannot do anything its role is not allowed to do.
- **Stock is a ledger.** Every change (production, sale, adjustment, wastage, refund restock) is a row
  in a stock-movement table with who/when/why; current stock is derived from it. This makes stock
  auditable and keeps reports consistent.
- **Payments are verified on the server.** A "payment success" message from the phone is never trusted
  on its own; an Edge Function verifies it with the gateway using secret keys that only live on the server.

## About the UPI ID `vsj87@oksbi`

`@oksbi` is a personal Google Pay handle. A plain UPI payment to a personal handle gives the app
**no way to verify** on the server that the money arrived, so it cannot meet the "never mark paid from a
client message" rule. The plan is therefore to use a payment gateway (Razorpay is the proposed default;
Cashfree or PhonePe PG work similarly). Customers still pay by UPI (including Google Pay), the gateway
confirms the payment to our server, and the gateway settles the money to the **bank account** you register
with it — which can be the same SBI account behind `vsj87@oksbi`. This will be confirmed with you before
Stage 4 starts.
