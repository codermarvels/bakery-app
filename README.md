# VSJ BAKERY

An Android app for ordering, sales and accounting at VSJ BAKERY: customers order bakery items for
pickup, the administrator manages products and stock, and a read-only reporting user views sales reports.

- **App:** Kotlin, Jetpack Compose, Material 3
- **Backend:** Supabase — PostgreSQL, Auth, Storage (Edge Functions from Stage 4).
  Setup steps: [`docs/SUPABASE_SETUP.md`](docs/SUPABASE_SETUP.md)
- **Payments:** Razorpay with server-side verification, test mode first.
  Setup steps: [`docs/PAYMENTS_SETUP.md`](docs/PAYMENTS_SETUP.md)

Administrators: see [`docs/ADMIN_GUIDE.md`](docs/ADMIN_GUIDE.md).
See [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md) for the stages and
[`docs/CHECKLIST.md`](docs/CHECKLIST.md) for progress.

## Project layout

```
app/src/main/java/com/vsjbakery/app/
├── MainActivity.kt          # entry point: sets the theme and the app UI
├── VsjBakeryApplication.kt  # creates AppContainer (shared objects) at app start
├── AppContainer.kt          # builds the Supabase client and repositories once
├── domain/model/            # plain Kotlin rules & data (Money, AppRole, validation) — no Android code
├── data/                    # talks to Supabase (auth, database)
└── ui/
    ├── VsjBakeryApp.kt      # app shell: bottom navigation bar + screens
    ├── navigation/          # typed routes and the navigation graph
    ├── theme/               # colours, fonts, shapes
    ├── components/          # reusable UI pieces
    ├── auth/                # AuthViewModel: sign-in state shared by all screens
    └── home/ category/ cart/ orders/ payments/ admin/ account/ staff/   # screens
supabase/
├── functions/               # server code: payments + Razorpay webhook (holds secret keys)
├── migrations/              # database tables, security rules, storage bucket, seed data
├── tests/database/          # automated security tests (pgTAP)
└── config.toml              # Supabase CLI settings
gradle/libs.versions.toml    # every library version in one place
.github/workflows/            # CI: Android tests/lint/APK, database security tests
```

## Run it on your computer

1. Install the latest stable [Android Studio](https://developer.android.com/studio).
2. **File → Open** and choose this folder. Android Studio downloads Gradle, the Android SDK (API 35)
   and all libraries automatically on first sync.
3. Add your Supabase project URL and publishable key to `local.properties`
   (see [`docs/SUPABASE_SETUP.md`](docs/SUPABASE_SETUP.md), step 4).
4. Pick an emulator or a phone with USB debugging enabled and press **Run ▶**.

Command line equivalent (needs JDK 17+ and the Android SDK):

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # builds app/build/outputs/apk/debug/app-debug.apk
```

### Download a ready-made test APK

Every push runs the **Android build** workflow in the GitHub **Actions** tab. Open the latest run and
download the `vsj-bakery-debug-apk` artifact, unzip it and install the APK on an Android phone
(allow "install unknown apps" when asked).

## Secrets

Never commit keys or passwords. `local.properties`, keystores and `.env` files are git-ignored.
Server-side secrets (payment gateway secret key, Supabase service-role key) will live only in
Supabase project secrets — never in the app.
