# New-order alerts on the admin phone (push notifications)

When a customer's payment is **verified by the server**, the administrator's phone plays a sound
and shows a notification like this, even when the app is closed:

> **New paid order #42 · ₹1,250.00**
> 1× Black Forest, 4× Veg Puff · Asha

Tapping it opens **Admin → Orders**. Alerts go only to phones where an **administrator** is
signed in. Signing out stops them. Unpaid orders never trigger an alert.

It uses Google **Firebase Cloud Messaging**, which is free. Setup takes about 15 minutes.

> 🔒 Step 3 creates a **private key file**. It goes only into Supabase secrets. Never commit it,
> never send it in chat.

## 1. Create a Firebase project (3 min)

1. Open <https://console.firebase.google.com> (sign in with the bakery's Google account) →
   **Create a project** → name it `VSJ Bakery` → turn **Google Analytics off** → **Create**.

## 2. Add the Android app and copy 4 values (5 min)

1. In the project, click **Add app** → the **Android** icon.
2. **Android package name:** `com.vsjbakery.app` (exactly this). Nickname: `VSJ Bakery`. Leave
   SHA-1 empty → **Register app**.
3. **Download google-services.json**. You only need 4 values from it. Open it in any text
   editor and find:

   | In google-services.json | Goes into `gradle.properties` as |
   |---|---|
   | `"project_id": "vsj-bakery-xxxxx"` | `vsj.firebase.projectId=vsj-bakery-xxxxx` |
   | `"project_number": "123456789012"` | `vsj.firebase.senderId=123456789012` |
   | `"mobilesdk_app_id": "1:123456789012:android:abc…"` | `vsj.firebase.appId=1:123456789012:android:abc…` |
   | `"current_key": "AIza…"` | `vsj.firebase.apiKey=AIza…` |

   Click **Next** through the remaining Firebase steps, then **Continue to console**. You don't
   need to add the file to the project.
4. GitHub → `gradle.properties` → ✏️ **Edit** → fill in the four `vsj.firebase.…` lines → **Commit**.
   These four values are public app identifiers (like the Supabase publishable key) and are safe
   in GitHub. You can also send them to me and I'll add them.

## 3. Give the server permission to send (5 min)

1. Firebase → ⚙️ **Project settings** → **Service accounts** tab → **Generate new private key**
   → **Generate key**. A `.json` file downloads. **This one is secret.**
2. Open it in a text editor and copy **everything** (from the first `{` to the last `}`).
3. Supabase → **Edge Functions** → **Secrets** → **Add new secret**:
   - Name: `FIREBASE_SERVICE_ACCOUNT`
   - Value: paste the whole JSON → **Save**.
4. Delete the downloaded key file from your Downloads folder (Supabase keeps it safely).
5. Firebase → Project settings → **Cloud Messaging** tab: check that **Firebase Cloud Messaging
   API (V1)** says *Enabled* (it is by default).

## 4. Database and server (3 min)

1. Supabase → **SQL Editor** → run `supabase/migrations/20261017090000_push_devices.sql`.
2. GitHub → **Actions → Deploy Edge Functions → Run workflow** (the payment functions now send
   alerts).

## 5. Install and test

1. After your `gradle.properties` commit, wait for the new **Android build**. Install its
   `vsj-bakery-debug-apk` on the **shop phone** (uninstall the old app first).
2. Sign in as the **administrator** and open the Admin dashboard. Tap **Allow** when asked
   about notifications. If the dashboard shows *"Notifications are off"*, tap that note and
   switch them on.
3. From another phone (or the emulator), sign in as a customer, order something and pay with
   test mode. Within a few seconds the shop phone should ring and show the alert.

## Make sure it rings (important on many Indian phones)

- **Battery:** Settings → Apps → VSJ BAKERY → Battery → **Unrestricted** (or "Don't optimise").
- **Xiaomi / Redmi / POCO, Oppo, Vivo, Realme:** also turn on **Autostart** for VSJ BAKERY
  (Settings → Apps → Manage apps → VSJ BAKERY → Autostart), otherwise the phone may block alerts
  while the app is closed.
- **Sound:** Settings → Apps → VSJ BAKERY → Notifications → **New orders**. There you can choose
  a louder sound. Keep it **On** with sound.
- **Do Not Disturb / silent mode** silences it like any other notification.

## If no alert arrives

| Check | Where |
|---|---|
| The payment really shows *Paid* | Admin → Orders |
| The phone registered | Supabase → Table Editor → `push_devices` has a row (sign out and back in as admin to re-register) |
| The server tried to send | Supabase → Edge Functions → `payments` (or `razorpay-webhook`) → **Logs**: look for "New-order alert could not be sent" or "FCM send failed" with the reason |
| Secret name is exact | `FIREBASE_SERVICE_ACCOUNT`, and the value is the whole JSON |
