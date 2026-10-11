# Payments setup (Stage 4 — Razorpay test mode)

Test mode uses pretend money: nothing is charged, nothing is paid out. Do these steps once.
They take about 20 minutes.

> **Key rule:** the Razorpay **Key Secret** and the **webhook secret** go only into Supabase's
> *Edge Function secrets* (step 3). Never paste them into chat, the app, GitHub or a file.

## How a payment works

1. In the app the customer taps **Pay**. The app asks our server (`payments` function) to start.
2. The server reads the order total **from the database** and creates a Razorpay order for exactly
   that amount.
3. Razorpay Checkout opens on the phone. The customer pays by UPI, card and so on.
4. The phone receives Razorpay's response and passes it to the server. **This is not trusted on its
   own.** The server:
   - checks Razorpay's signature using the Key Secret, which only the server has, and
   - asks Razorpay's own servers whether the money was really captured, and for how much.
5. Only then does the database mark the order **paid** and reduce the stock, in one step.
6. Razorpay also calls our `razorpay-webhook` directly. If the phone closes mid-payment, the order is
   still confirmed.
7. If an item sold out or the order was cancelled while the customer was paying, the order is **not**
   marked paid and the money is **refunded automatically**.

---

## 1. Database

Go to **Supabase → SQL Editor → New query**. Paste
`supabase/migrations/20261013090000_payments.sql` and click **Run**.

## 2. Razorpay test keys

1. Sign in at <https://dashboard.razorpay.com>. Switch the toggle at the top to **Test Mode**.
2. Go to **Account & Settings → API Keys → Generate Test Key**.
3. You get a **Key ID** (`rzp_test_…`) and a **Key Secret**. The secret is shown only once, so copy
   it straight into step 3. If you lose it, generate a new key.

## 3. Put the secrets into Supabase (server only)

Go to **Supabase → Edge Functions → Secrets** (also under **Project Settings → Edge Functions**) and
add these three:

| Name | Value |
|---|---|
| `RAZORPAY_KEY_ID` | your `rzp_test_…` Key ID |
| `RAZORPAY_KEY_SECRET` | your Key Secret |
| `RAZORPAY_WEBHOOK_SECRET` | a long random password **you make up**, for example 32 letters and numbers. You'll type the same value into Razorpay in step 5. |

`SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` are provided to functions automatically, so don't add
them.

## 4. Deploy the two server functions

The code is in `supabase/functions/` (`payments`, `razorpay-webhook` and shared code in `_shared`).

**Option A: from GitHub (no software needed).** This works once the Stage 4 pull request is merged
into `main`.

1. In Supabase, click your avatar → **Account preferences → Access Tokens → Generate new token**.
   Name it `github-deploy` and copy it.
2. On GitHub, go to the repository → **Settings → Secrets and variables → Actions**. On the
   **Secrets** tab, click **New repository secret**. Name it `SUPABASE_ACCESS_TOKEN` and paste the
   token as the value.
3. Go to **Actions → Deploy Edge Functions → Run workflow**.

**Option B: from a computer with Node.js installed**, in the project folder:

```bash
npx supabase login
npx supabase functions deploy payments --project-ref ckepiiynmdspwqqxlhzw
npx supabase functions deploy razorpay-webhook --project-ref ckepiiynmdspwqqxlhzw
```

Afterwards, **Supabase → Edge Functions** lists `payments` and `razorpay-webhook`. Each function
checks callers itself, so **"Verify JWT" must be OFF** for both. The deploy turns it off
automatically using `supabase/config.toml`. If you ever edit them in the dashboard, keep it off.

## 5. Razorpay webhook

Go to **Razorpay Dashboard (Test Mode) → Account & Settings → Webhooks → Add New Webhook**:

- **Webhook URL:** `https://ckepiiynmdspwqqxlhzw.supabase.co/functions/v1/razorpay-webhook`
- **Secret:** the same value as `RAZORPAY_WEBHOOK_SECRET` from step 3
- **Active events:** `payment.captured`, `payment.failed`, `order.paid`

## 6. Try a test payment

1. Install the new APK and sign in as a customer. Place an order, then tap **Pay**.
2. In Razorpay Checkout, choose **UPI** and enter **`success@razorpay`**, which is Razorpay's
   test UPI ID for a successful payment. To try a failure, use `failure@razorpay`. Test cards are
   listed at <https://razorpay.com/docs/payments/payments/test-card-details/>.
3. You should see **"Payment successful"**, and **My orders** shows the order as **Paid**.
4. Check the server side:
   - **Supabase → Table Editor → `payments`:** `captured` with a `verified_at` time.
   - **`orders`:** `paid`.
   - **`stock_movements`:** a `sale` row for each item.
   - **`products`:** stock reduced.
   - **Razorpay Dashboard → Transactions → Payments:** the payment is listed.

## Going live later (not now)

- Complete Razorpay's KYC and add the bakery's **bank account for settlements**. This can be the
  SBI account behind `vsj87@oksbi`. Customers can still pay with Google Pay or any UPI app;
  Razorpay pays the money into that bank account.
- Generate **Live** keys and replace the three secrets in step 3 with live values. Add the webhook
  again in Live Mode.
- Nothing in the app needs to change: the Key ID always comes from the server.
