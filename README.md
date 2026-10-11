# VSJ BAKERY

An Android app for ordering, sales and accounting at VSJ BAKERY: customers order bakery items for
pickup, the administrator manages products and stock, and a read-only reporting user views sales reports.

- **App:** Kotlin, Jetpack Compose, Material 3
- **Backend (from Stage 2):** Supabase — PostgreSQL, Auth, Storage, Edge Functions
- **Payments (from Stage 4):** payment gateway with server-side verification, test mode first

See [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md) for the stages and
[`docs/CHECKLIST.md`](docs/CHECKLIST.md) for progress.

## Project layout

```
app/src/main/java/com/vsjbakery/app/
├── MainActivity.kt          # entry point: sets the theme and the app UI
├── domain/model/            # plain Kotlin data (Category, Money, AppRole) — no Android code
└── ui/
    ├── VsjBakeryApp.kt      # app shell: bottom navigation bar + screens
    ├── navigation/          # typed routes and the navigation graph
    ├── theme/               # colours, fonts, shapes
    ├── components/          # reusable UI pieces
    └── home/ category/ cart/ account/   # one folder per screen
gradle/libs.versions.toml    # every library version in one place
.github/workflows/android.yml  # CI: tests, lint, APK build on every push
```

## Run it on your computer

1. Install the latest stable [Android Studio](https://developer.android.com/studio).
2. **File → Open** and choose this folder. Android Studio downloads Gradle, the Android SDK (API 35)
   and all libraries automatically on first sync.
3. Pick an emulator or a phone with USB debugging enabled and press **Run ▶**.

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
