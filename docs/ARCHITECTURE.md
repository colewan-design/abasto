# Architecture

## Shape

```
 Android (Kotlin/Compose)        Web (Nuxt 4)
            \                        /
             \   HTTPS + Bearer     /
              \   (Sanctum PAT)    /
               +------------------+
               |   Laravel API    |
               |  /api/v1/*       |
               +------------------+
                 |     |       |
            MariaDB  Queue   Storage
                     (OCR,   (receipt
                   reminders) images)
```

Clients hold a Sanctum personal access token. Every row in every domain table is scoped
by `user_id`; a global `BelongsToUser` trait plus a `scopeOwned` query scope enforce it,
and demo users carry `is_demo = true` so sample data never mixes with personal records.

## Money

All amounts are stored as **integer minor units** (`bigint`, e.g. centavos) in columns
named `*_minor`, never floats. `App\Support\Money` wraps conversion, formatting, and
allocation (used when splitting a receipt across categories so the split always sums to
the receipt total). The API serializes both the raw minor value and a formatted string.

## Double-entry-ish ledger

A transaction is one of `income`, `expense`, or `transfer`.

- `income` / `expense` affect one account and roll up into reports and budgets.
- A `transfer` is stored as **two rows** sharing a `transfer_group` UUID — an outflow on
  the source account and an inflow on the destination. Reports exclude
  `type = transfer` so moving money never inflates income or expenses.
- Savings contributions are transfers into a goal-linked account, or expense rows flagged
  with `savings_goal_id` and excluded from spending reports by the same rule.
- Account balances are maintained incrementally by `AccountBalanceService` on every write
  and can be rebuilt from the ledger with `php artisan accounts:recalculate`.

## Receipt pipeline

```
POST /api/v1/receipts (multipart image)
  -> store image on the `receipts` disk, compute perceptual + byte hash
  -> dispatch ProcessReceiptJob
       -> ReceiptOcr driver (anthropic | google_vision | tesseract | heuristic)
       -> ReceiptParser: merchant, date, subtotal, tax, total, line items
       -> CategorySuggester: merchant rules -> keyword map -> group fallback
       -> DuplicateDetector: content hash, then merchant+date+total fuzzy match
  -> receipt.status = processed, field-level confidence recorded
GET  /api/v1/receipts/{id}          -> review payload with `needs_review` flags
POST /api/v1/receipts/{id}/confirm  -> creates the transaction (+ splits), attaches image
```

Drivers implement `App\Contracts\ReceiptOcrDriver`. The driver is chosen by
`RECEIPT_OCR_DRIVER`; `heuristic` needs no credentials and keeps dev/demo working.

Corrections made at confirm time are fed back into `merchant_category_rules`
(`CategorySuggester::learn()`), which is what makes suggestions improve over time.

## Feature map

| Plan feature | Code |
| --- | --- |
| Income / expense tracking | `TransactionController`, `TransactionService` |
| Transfers without double counting | `TransferController`, `transfer_group` pairing |
| Receipt scanning | `ReceiptController`, `ProcessReceiptJob`, `Ocr\*Driver` |
| Category suggestions + learning | `CategorySuggester`, `merchant_category_rules` |
| Categories, groups, tags | `CategoryController`, `CategoryGroupController`, `TagController` |
| Category budgets + alerts | `BudgetController`, `BudgetService`, `BudgetThresholdNotification` |
| Bills, due dates, reminders | `BillController`, `SendBillRemindersCommand` |
| Savings goals | `SavingsGoalController`, `SavingsGoalService` |
| Cash flow + reports | `ReportController`, `ReportService` |
| Accounts and wallets | `AccountController`, `AccountBalanceService` |
| Guided setup | `OnboardingController`, `DefaultCategoryCatalog` |
| Google sign-in | `GoogleAuthController`, `GoogleTokenVerifier` |
| Guest demo | `GuestAuthController`, `DemoDataSeeder`, `DemoController@reset` |
| Search and filters | `TransactionIndexRequest`, `TransactionQuery` |
| Recurring transactions | `RecurringRuleController`, `RunRecurringRulesCommand` |
| Export / backup | `ExportController` (CSV + PDF), `BackupController` |
| Biometric / PIN unlock | mobile `AppLockManager`, `BiometricGate` |

## Scheduled work

`routes/console.php` registers:

- `recurring:run` — daily 00:10, materializes due recurring transactions.
- `bills:remind` — daily 08:00, due-date reminders honoring `reminder_days_before`.
- `budgets:check` — daily 20:00, threshold + overspend notifications.
- `demo:prune` — hourly, deletes guest demo users idle for more than 7 days.

## Clients

**Web** (`web/`) — Nuxt 4 in SPA-ish mode with a typed API layer (`app/api/*`), Pinia
stores per domain, and route middleware that redirects to onboarding until
`onboarding_completed_at` is set.

**Android** (`mobile/`) — single-activity Compose app with Hilt, Retrofit and
kotlinx.serialization against the same contract.

```
com.budgettracker
  core/          money (minor units), Outcome/ApiException, date helpers
  data/
    model/       the API contract as serializable types
    remote/      BudgetApi (every endpoint), AuthInterceptor, request bodies
    session/     token + cached user in DataStore
    repository/  one per domain, plus LedgerEvents
  ui/            theme, shared components, charts, navigation, app lock
  feature/       a package per screen: view model + composables
```

- **Amounts** stay integer minor units end to end; `core/money/Money.kt` is the only
  thing that turns one into a string, and the only thing that parses one back.
- **Failures** never throw into a screen. Repositories return `Outcome.Ok` or
  `Outcome.Err(ApiException)`, which carries Laravel's own message and the per-field
  messages behind a 422 — so a validation error lands under the input that caused it.
- **One write, many screens.** `LedgerEvents` is signalled by the repositories that
  write; the dashboard, activity list and budget reload themselves rather than going
  stale until a manual refresh.
- **The session** (token plus a cached user record) lives in DataStore, so the app opens
  straight into the dashboard with the right name, currency and tabs before the first
  response arrives. A 401 on any request clears it and the graph falls back to the
  welcome screen.
- **Receipt capture** goes through the system camera (via a `FileProvider` into the app's
  own cache) or the photo picker, is rotated by its EXIF data and downscaled, then read
  by ML Kit on-device — the recognised text is uploaded with the image so the server can
  skip a paid OCR call. Extraction runs inline so the review screen opens on a draft
  rather than polling a queued job, and nothing reaches the ledger until a person
  confirms it.
- **Google sign-in** uses Credential Manager; a build with no `GOOGLE_WEB_CLIENT_ID`
  hides the button and leaves the guest demo and password sign-in intact.
- **The app lock** is `AppLockManager` plus `BiometricGate`, which locks on
  `ON_STOP` and composes nothing behind the prompt.

Not built yet on Android: offline write-behind (the `SyncWorker`/Room cache), push
registration through `POST /devices`, and the CSV/PDF export screens — the endpoints for
all three already exist on the API.
