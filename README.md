# Budget Tracker

Personal budget tracker: record income and expenses, scan receipts, manage budgets and
bills, build savings, and understand cash flow.

## Repository layout

| Path | Stack | Purpose |
| --- | --- | --- |
| [api/](api/) | Laravel 12, PHP 8.4, MariaDB | REST API, auth, OCR pipeline, reports, exports |
| [web/](web/) | Nuxt 4, Vue 3, TypeScript, Tailwind | Web client |
| [mobile/](mobile/) | Kotlin, Jetpack Compose, Hilt, Room | Android client |
| [docs/](docs/) | — | Architecture, data model, API contract |

## Quick start

### API

```bash
cd api
cp .env.example .env            # already created by the installer
php artisan key:generate
php artisan migrate --seed
php artisan serve               # http://127.0.0.1:8000
php artisan queue:work          # receipt OCR + reminders
```

### Web

```bash
cd web
npm install
npm run dev                     # http://localhost:3000
```

### Mobile

```bash
cd mobile
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # money, dates, API failure mapping, update decisions
./gradlew lintDebug
./gradlew publishRelease       # cut a release — see "Publishing a release" below
```

Machine-specific settings live in `mobile/local.properties`, which is not committed:

| Key | Purpose |
| --- | --- |
| `sdk.dir` | Android SDK location (escape the drive colon on Windows: `C\:/...`) |
| `API_BASE_URL` | Where the API is. Defaults to `http://10.0.2.2:8010/api/v1/`, which is the host machine as seen from the emulator — use your LAN address for a physical device |
| `GOOGLE_WEB_CLIENT_ID` | OAuth web client ID. Leave blank to hide the Google button and sign in with the guest demo or a password |
| `UPDATE_MANIFEST_URL` | Where the in-app updater looks for a newer build. Defaults to `http://10.0.2.2:3000/downloads/android.json` — the website, not the API |

Guest sign-in needs nothing configured, so the app is usable end to end against a
freshly seeded API.

> **If the app says "That did not come through"**, the API is being served by PHP's
> built-in server (`php artisan serve`). It sends large responses without a
> `Content-Length`, ending them by closing the connection, and the tail is lost on the
> way through the emulator's network — the dashboard response is the one big enough to
> hit it. Serve the API through Apache or nginx (XAMPP's Apache pointed at `api/public`
> is enough) and it goes away. Everything smaller than the dashboard works either way.

## Publishing a release

The app is distributed as a direct APK rather than through Play, so the website is
both the download page and the update channel. One command publishes both:

```bash
cd mobile
# bump versionCode and versionName in app/build.gradle.kts first
./gradlew publishRelease
```

It builds the release APK and writes it to `web/public/downloads/` alongside
`android.json`, which records the version, size, SHA-256, release date and the notes
from `mobile/release-notes.txt`. Two things read that manifest and nothing else:

- **The download page** (`/`) shows the version, size, notes and digest, and links to
  the APK. With no manifest present it says the build is not published yet.
- **The in-app updater** checks it on launch, and prompts when `version_code` is
  higher than the installed build. The person can download it there and then, put it
  off, or skip that version; the download is verified against the digest before the
  system installer is offered the file. There is also a "Check for updates" card in
  Settings for when a prompt was skipped.

Generating both from one build is deliberate — a hand-maintained manifest drifts, and
the failure mode is an updater offering a download whose digest no longer matches.

Raise `minimumSupportedVersionCode` in `app/build.gradle.kts` only to force an update
that cannot be dismissed, and only when an older build genuinely stopped working
against the API.

> Published APKs are build output, not source. Clear `web/public/downloads/` rather
> than committing a 45 MB binary per release.

> Release builds reject cleartext HTTP (only the debug source set allows it), so
> `UPDATE_MANIFEST_URL` and `API_BASE_URL` must both be HTTPS in a real deployment.
> A debug build installed over a release build is a *separate* app — debug carries
> the `.debug` application ID suffix — so testing the updater locally leaves two
> icons on the device.

## Release scope

**First release** — Google sign-in, guest demo, guided setup, accounts and starting
balances, manual income/expense/transfers, receipt scanning with review and duplicate
warnings, category suggestions, monthly category budgets, cash-flow summaries.

**Following release** — recurring transactions, bills and reminders, savings goals,
expanded reports, CSV/PDF export and backups.

Both releases are implemented in this repository; see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
for the feature-to-code map.
