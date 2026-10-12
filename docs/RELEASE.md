# Release build and Google Play

The release build is what customers install from Google Play. It is shrunk and optimised (R8)
and signed with **your upload key**. GitHub builds it automatically. You only create the key
once and give it to GitHub as secrets.

> **The upload key is like the master key to your app on Play.** Keep the `.jks` file and its
> passwords somewhere safe (a password manager plus an offline backup). Never commit them, never
> paste them in chat. If you lose it, Google can reset it, but that takes days.

## 1. Create the upload key (once, about 5 minutes)

You need Java (it comes with Android Studio). In a terminal on **your own computer**:

```bash
keytool -genkeypair -v -keystore vsj-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias vsj-upload
```

It asks for a password (use a long one) and your name/organisation. It creates `vsj-upload.jks`.

## 2. Give it to GitHub as secrets

1. Turn the file into text:
   - macOS / Linux: `base64 -i vsj-upload.jks | pbcopy` (macOS) or `base64 -w0 vsj-upload.jks` (Linux)
   - Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("vsj-upload.jks")) | Set-Clipboard`
2. GitHub → repository → **Settings → Secrets and variables → Actions → Secrets → New repository
   secret**, four times:

   | Name | Value |
   |------|-------|
   | `RELEASE_KEYSTORE_BASE64` | the long text from step 1 |
   | `RELEASE_STORE_PASSWORD` | the keystore password |
   | `RELEASE_KEY_ALIAS` | `vsj-upload` |
   | `RELEASE_KEY_PASSWORD` | the key password (the same as the keystore password if you pressed Enter) |

GitHub secrets are encrypted and never shown in logs. The build deletes the decoded key file
when it finishes.

## 3. Get the release files

GitHub → **Actions** → latest **Android build** → artifacts:

- `vsj-bakery-release-signed-for-play`: contains `app-release.aab` (upload this to Play),
  `app-release.apk` (to install directly) and `mapping.txt` (Play uses it to read crash reports).
- `vsj-bakery-release-TEST-ONLY-debug-signed`: produced while the secrets are missing. It's fine
  for checking the release build on the emulator, but Play will refuse it.

**Before each Play upload**, raise `versionCode` (and usually `versionName`) in
`app/build.gradle.kts`. Play refuses a version code it has seen before.

### Building on your own computer instead (optional)

Put this in `local.properties` (that file is never committed):

```
release.storeFile=/full/path/to/vsj-upload.jks
release.storePassword=…
release.keyAlias=vsj-upload
release.keyPassword=…
```

Then run `./gradlew bundleRelease`. The bundle is in `app/build/outputs/bundle/release/`.

## 4. Test the release build first

Install the release APK on the emulator (uninstall the debug app first) and check:
sign-in, placing an order, a test payment, admin tabs, reports and PDF export. The release build
is optimised differently from the debug build, so a quick run-through catches problems early.

## 5. Google Play Console (once)

1. Create a developer account at <https://play.google.com/console> (one-time USD 25 fee and
   identity verification). If you register as an **individual** with a new account, Google
   requires a **closed test with at least 12 testers for 14 days** before you can publish.
   An **organisation** account (needs a D-U-N-S number) skips this.
2. **Create app**: name *VSJ BAKERY*, app (not game), free.
3. **Play App Signing**: accept (recommended). Google keeps the final signing key and you upload
   with your upload key.
4. **App content** (left menu):
   - **Privacy policy:** `https://codermarvels.github.io/bakery-app/privacy.html`
   - **App access:** "All or some functionality is restricted". Give a test customer login
     (and say that staff screens are for bakery employees only).
   - **Ads:** No ads.
   - **Content rating:** fill in the questionnaire (food ordering, no user-generated content).
   - **Target audience:** 18+.
   - **Data safety:** see the answers below.
   - **Account deletion:** in-app *Account → Delete my account*. Web link:
     `https://codermarvels.github.io/bakery-app/delete-account.html`
   - **Financial features:** none. Payments are for physical goods (food), handled by Razorpay.
     Google Play billing is **not** required for physical goods.
5. **Store listing:** short description, full description, app icon (512×512), feature graphic
   (1024×500) and at least 2 phone screenshots (take them from the emulator).
6. **Testing → Internal testing → Create release**, upload `app-release.aab`, add your own email
   as a tester and install it from the Play link. Then a closed test (if required), then
   **Production**.

### Data safety answers (what this app actually does)

| Question | Answer |
|----------|--------|
| Collects or shares data? | Collects: yes. Shares: no (Supabase and Razorpay process data on our behalf, which counts as "service providers", not sharing). |
| Encrypted in transit? | Yes (HTTPS only) |
| Users can request deletion? | Yes (in the app and by email) |
| Personal info | **Name**, **Email address**, **Phone number** (optional): collected, required for the app to work (name/email) / optional (phone); purpose: *App functionality*, *Account management* |
| Financial info | **Purchase history**: collected; purpose: *App functionality*. Card/UPI details are handled by Razorpay, not this app. |
| Location, contacts, photos, device IDs, analytics | Not collected |

### Suggested store text

**Short description (80 characters):**
Order fresh cakes, pastries & breads from VSJ BAKERY. Pay online, pick up in store.

**Full description:**
> Order from VSJ BAKERY in a few taps: cakes, pastries, breads, cookies, snacks and beverages,
> freshly made every day.
>
> • Browse the full menu with prices and live availability
> • Pay securely with UPI, cards, net banking or wallets (powered by Razorpay)
> • Your order is confirmed the moment your payment is verified
> • See when your order is ready, then pick it up at the shop, no waiting
>
> Pickup only: we do not deliver. Shop address and hours are in the app and on our website.
