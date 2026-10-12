# Go-live checklist (Stage 7)

Work through this list from top to bottom. Each step says **where** to do it. Nothing here needs
code changes. Secrets go only into the Supabase or GitHub secret stores, never into files or chat.

---

## 1. Database and server functions (15 minutes)

1. **Supabase → SQL Editor → New query.** Paste and **Run** each file, one at a time:
   1. `supabase/migrations/20261016090000_account_deletion.sql` adds *Delete my account*.
   2. `supabase/migrations/20261016100000_app_refunds.sql` adds administrator refunds.
2. **GitHub → Actions → Deploy Edge Functions → Run workflow.** The `payments` function has the
   new *refund* action, so it must be deployed again.
3. Test with your **test** keys still in place:
   - Place and pay a test order as a customer. Then as admin, open **Orders**, tap **Refund**,
     give a reason, and confirm. The order shows *Refunded*, and Razorpay (test mode) →
     Transactions → Refunds lists it.
   - Create a throwaway customer and use **Account → Delete my account**. The account disappears
     from Authentication → Users.

## 2. Emails: sign-up confirmation and password reset (20 minutes)

Supabase's built-in email sender is for testing only. It sends just a few emails per hour, and
**only to your own team's addresses**. Real customers would never receive their confirmation or
reset emails, so you need your own email sender (SMTP).

1. Pick an email service. **Brevo** (free 300/day) and **Resend** (free 3,000/month) are easy.
   Zoho Mail or Google Workspace also work if the bakery has a domain. Create an account and verify
   the sender address (e.g. `orders@yourdomain` or the bakery's Gmail via the provider's
   instructions). The service shows **SMTP host, port, username and password**.
2. **Supabase → Authentication → Emails → SMTP Settings → Enable custom SMTP.** Enter those
   values, sender name `VSJ BAKERY`, then save. The SMTP password stays in Supabase.
3. **Authentication → Emails → Templates → Reset Password.** Replace the body with this so the
   email contains the code the app asks for:

   ```html
   <h2>Reset your VSJ BAKERY password</h2>
   <p>Your code is: <strong style="font-size:22px">{{ .Token }}</strong></p>
   <p>Enter it in the app (Account → Sign in → Forgot password?). It expires soon.</p>
   <p>If you did not ask for this, ignore this email; your password stays the same.</p>
   ```
4. **Authentication → Sign In / Providers → Email:** keep **Confirm email ON** for real customers.
5. **Authentication → Rate limits:** the email limit can now be raised (e.g. 60 per hour).
6. Test: **Forgot password?** on the sign-in screen, using your own customer account.

## 3. Policy website (15 minutes)

Razorpay and Google Play both require public policy pages. They are ready in `site/`. You only
add the bakery's details.

1. GitHub → open `site/details.properties` → ✏️ **Edit**. Fill in every line: legal name,
   address with PIN, phone, email, hours, city, grievance officer, date. Use **exactly** the same
   name, address, phone and email you give Razorpay. **Commit changes** (to `main`).
2. GitHub → **Settings → Pages → Build and deployment → Source: GitHub Actions** (once).
3. GitHub → **Actions → Policy website** should finish green. The site is at
   **https://codermarvels.github.io/bakery-app/**. Read every page once: the refund and pickup rules
   are sensible defaults, so change any wording that doesn't match how the bakery works (edit
   the files in `site/pages/`).
4. The links on the app's **Account** tab open these pages.

*This is not legal advice. If you have an accountant or lawyer, ask them to read the terms and
refund policy.*

## 4. Razorpay live mode (KYC takes 1–5 working days)

1. **Razorpay Dashboard → Activate account** (top banner). Enter:
   - Business type (e.g. *Proprietorship* or *Individual*), PAN, and the bank account that
     receives the money.
   - GSTIN if you have one (optional for small businesses).
   - **Website / app link:** `https://codermarvels.github.io/bakery-app/`. The pages Razorpay
     checks are there: Terms, Privacy, Cancellation & Refund, Shipping (our pickup page) and
     Contact Us.
   - Business category: *Food & Beverage → Bakery / Restaurant*.
2. Wait for the approval email. Meanwhile keep everything in test mode.
3. When approved: **Razorpay → switch to Live Mode → Account & Settings → API Keys → Generate
   Key**. Copy the Key ID (`rzp_live_…`) and Key Secret.
4. **Supabase → Edge Functions → Secrets:** replace `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET`
   with the **live** values. (The app reads the Key ID from the server, so no new app build is
   needed.)
5. **Razorpay (Live Mode) → Webhooks → Add New Webhook:** the same URL as in test mode
   (`https://ckepiiynmdspwqqxlhzw.supabase.co/functions/v1/razorpay-webhook`), events
   `payment.captured`, `payment.failed`, `order.paid`, and a **new** long random secret. Put that
   secret into Supabase as `RAZORPAY_WEBHOOK_SECRET` (replacing the test one).
6. **Razorpay → Payment methods:** make sure **UPI** is enabled (most customers will pay this way).
7. **Real-money check:** order something cheap (e.g. ₹10) with your own UPI and pay. Check that it
   shows *Paid*. Then refund it from **Admin → Orders → Refund** and check that the money comes back.

## 5. Clear the test data (2 minutes, once)

Right after step 4.7, before real customers order:

1. **Supabase → SQL Editor → New query.** Paste and **Run** `supabase/snippets/go_live_cleanup.sql`.
   It removes every test order, payment, refund and stock entry, deletes the `[TEST]` sample
   products, sets all stock to 0 and restarts order numbers at #1. Products, prices, photos and
   accounts are kept.
2. As admin, record the real stock with **Stock → Production** for each product.
3. Optional: delete test customer accounts in **Authentication → Users**, and the test
   products' photos in **Storage → product-images**.

## 6. Supabase plan, backups and security

- **Free plan limits:** a free project is **paused after 7 days without activity** and has **no
  automatic backups**. Once real money flows through the app, upgrading to **Pro** (about
  USD 25/month) is strongly recommended: daily backups, no pausing, and **leaked-password
  protection** (Authentication → Policies). Supabase → Organization → Billing.
- **Staff passwords:** use long, unique passwords for the admin and reports accounts and don't
  share them. To remove someone's access, set their role back to `customer` (`docs/SUPABASE_SETUP.md`
  step 3).
- **Access tokens:** if the Supabase access token, a Razorpay secret or the upload key ever leaks,
  revoke it at the source (Supabase account tokens, Razorpay API keys, Google Play upload-key reset)
  and replace the GitHub/Supabase secret.

## 7. Release build and Google Play

Follow `docs/RELEASE.md`: create the upload key, add 4 GitHub secrets, test the release APK,
then publish through the Play Console (privacy policy, data safety and account-deletion answers
are written out there).

---

## Everyday procedures after launch

**A customer asks by email to delete their account.** Check the email comes from the account's
address. Then **SQL Editor**:

```sql
select private.delete_customer_account(
  (select id from auth.users where email = 'customer@example.com')
);
```

It refuses (and says why) if they have a paid order waiting for pickup. Reply to confirm.

**A customer cancels a paid order / quality complaint:** Admin → Orders → **Refund**. Always
refund here, not only in the Razorpay dashboard, so reports and stock stay right. If it says the
refund failed (e.g. Razorpay was down), tap **Retry refund** later. It never refunds twice.

**Walk-in sale undone:** Counter tab → Undo (as before).

**Monthly:** Reports → *Last month* → Export PDF → share with your accountant.
