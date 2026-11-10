# iOS issues found while porting Ladger to Android

Found while porting the iOS app (commit `2b36197`) to Android, which is now feature-complete. Most
important first. Android avoids or fixes most of these; where it behaves the same "to match", the issue
says so. File references are to the iOS repo.

Checked again at iOS `6f46e7b`: issues 2–6 are fixed there; 1, 7 and 8 and the typo in 9 are still open.
Checked again at iOS `f909b82`: no change to those; 10–13 are new, from planned transactions.
Checked again at iOS `46702e5`: 13 is fixed and the app icon no longer says "Ladger" (9); 1, 7, 8, 10–12 and the
typo in 9 are still open; 14 and 15 are new, from budgets and receipt items.
Checked again at iOS `fa64623`: 11 is fixed and 15 mostly (receipt photos still aren't exported); 1, 7, 8, 10, 12, 14
and the typo in 9 are still open; 16–19 are new, from widgets, Siri, budget alerts and the new CSV files.

## 1. Dark mode: the lock screen probably lets balances show through
`views/LockView.swift`, `components/BackgroundView.swift`, `ContentView.swift`

`LockView` is an `.overlay` on `MainView`, and its only background is `BackgroundView`, whose colours
are at 0.7 opacity in dark mode. Unless something else covers it, about 30 % of the screen underneath
(total balance, donut chart) shows through the lock screen. Android's version of the same layering
leaked in an automated test until an opaque layer was added under the gradient.
**Fix:** put an opaque background (e.g. `Color(.systemBackground)`) under `BackgroundView` in `LockView`.

## 2. Receipt totals are skipped when the line contains "Hrvatska"
**Fixed in iOS `6f46e7b`** (`containsWord`).
`models/ReceiptParser.swift`, `findTotal`

Keywords and VAT exclusions are matched as substrings, and `"vat"` is an exclusion, so a total line
such as `UKUPNO KONZUM HRVATSKA 15,89` is thrown away (H-r-**vat**-ska). The same goes for any word
containing "ptu", "pdv" etc.
**Fix:** match keywords as whole words (Android: not glued to letters on either side) and VAT terms
as word starts ("porez" still matches "poreza").

## 3. The unlock prompt can come back after every cancel
**Fixed in iOS `6f46e7b`** (`shouldPromptOnActive`).
`ContentView.swift`

`unlock()` runs whenever the scene becomes `.active` while locked. Dismissing Face ID/the passcode
sheet makes the scene inactive and then active again, so the prompt reappears straight away.
**Fix:** prompt once per return from the background, and otherwise only when "Unlock" is tapped.

## 4. Share extension unavailable on iOS 26
**Fixed in iOS `6f46e7b`.**
`budget tracker.xcodeproj`: the share extension's deployment target is **iOS 27.0** while the app's is
**26.5**, so "Share to Ladger" doesn't appear on iOS 26 devices.
**Fix:** set the extension's deployment target to 26.5.

## 5. Share extension version doesn't match the app
**Fixed in iOS `6f46e7b`** (both 0.1.0).
The extension's `MARKETING_VERSION` is **1.0**, the app's **0.1**. App Store Connect requires an
extension's version to match its containing app (warning ITMS-90473, rejection in some cases).
**Fix:** use the same version for both targets.

## 6. The scanned receipt's date is never used
**Fixed in iOS `6f46e7b`**, which also reads the receipt's time and shows the date picker when adding; Android follows.
`views/AddTransactionView.swift`, `parseText`

`ReceiptParser` finds the date but `parseText` doesn't set `draft.date`, and add mode has no date
picker, so a scanned receipt is always dated "now". (Android currently does the same, to match. If
the date should be used, both apps can change together.)

## 7. Collections won't sync once iCloud is turned on
`models/TransactionCollection.swift`

CloudKit needs every property to be optional or have a default, and every relationship to have an
inverse. `TransactionCollection` has non-optional properties without defaults and `transactions` has
no inverse, so enabling `cloudKitDatabase: .private(...)` (as the comment in `budget_trackerApp.swift`
plans) will fail to load the store.

## 8. Duplicate-name checks are case-sensitive
Categories, sources and collections: "Food" and "food" can both be created, while the CSV importer
matches names ignoring case, so an import may attach transactions to either one. Since `8a76b5d` Siri matches
category and account names exactly too (`intents/LedgerEntities.swift`). Android does the same, to match.

## 9. Text
- `unlock_reason` (English): "Unlock app to see the **blance** and manipulate transactions" — typo.
- `reminder_title` was "Budget Tracker" while the app is called Ladger. Since `6f46e7b` it is "Ledger", matching the
  iOS app's new display name; Android followed and is now called Ledger too.
- ~~The app icon still had "Ladger" written under the logo.~~ **Fixed in iOS `46702e5`** (`Icon-3.png`, a script L);
  Android's icons are regenerated from it.

## 10. "Planned transaction performed" arrives before anything is recorded
`models/PlannedTransactionNotifier.swift`, `components/RecurranceEngine.swift`, `ContentView.swift`

Each plan's next due date gets a notification saying "Netflix: €12.99 has been recorded", but the
transaction is only created by `RecurrenceEngine.generateDue` when the app next becomes active. Until
then the balances leave it out, however long the app stays closed.
**Fix:** record in the background (e.g. a `BGAppRefreshTask`), or word the notification as "due" rather
than "recorded". Android sets an alarm for the due time that records the transaction and then notifies.

## 11. Exported sources and planned transactions can't be imported
**Fixed in iOS `8a76b5d`**: the importer recognizes every exported file by its header; Android follows.
`models/CSVExporter.swift`, `models/CSVImporter.swift`

The export now also writes `accounts.csv` (`sources.csv` before `46702e5`) and `planned_transactions.csv`, but the importer only reads
transaction files and rejects the whole import ("unknown format") if either is selected. Selecting every
file in an export folder, the obvious way to restore a backup, imports nothing. Android does the same, to match.
**Fix:** skip these files when importing, or import them (sources with initial value, icon and colour; plans).

## 12. Changing a plan's start date can record an occurrence twice
`views/AddPlannedTransactionView.swift`, `models/PlannedTransaction.swift`

Editing keeps `occurrencesGenerated`, and occurrences are counted from the start date. If a monthly plan
that has recorded 1 October is moved to start on 1 September, occurrence 1 is 1 October again and is
recorded a second time, while 1 September never is. Moving the start later skips the new start date.
Changing the frequency or interval shifts the next date the same way. Android does the same, to match.
**Fix:** when the schedule changes, count only the new occurrences up to the last recorded one.

## 13. `no_categories` has no text
**Fixed in iOS `46702e5`**; Android's own copy is removed.
`Localizable.xcstrings`: the key was added without any localization, so an empty categories list showed
"no_categories".

## 14. The daily reminder's budget hint is out of date
`models/DailyReminder.swift`, `models/BudgetNotifier.swift`

The hint ("Over half of your general limit is used…") is baked into the repeating notification when it is
scheduled, and only rescheduled when the app checks the budgets (saving a transaction or budget, opening
the app). An expense recorded by a planned transaction while the app stays closed, or a new month, isn't
reflected: on 1 November the reminder can still say the limit is reached.
**Fix:** schedule the reminder without the hint and refresh it daily (e.g. a `BGAppRefreshTask`), or drop the
hint when the scheduled text is from an earlier month. Android works the text out when the reminder goes off.

## 15. Budgets and receipts aren't in the CSV export
**Mostly fixed in iOS `8a76b5d`**: budgets, categories, collections and receipt items are exported and imported;
Android follows. Receipt photos (and learned merchant categories) still aren't exported.
`models/CSVExporter.swift`

The export writes transactions, accounts and planned transactions, but not budgets, receipt items or
photos. Restoring a backup on a new phone loses every limit and the whole price history. Android does
the same, to match.
**Fix:** export `budgets.csv` and `receipt_items.csv` (and import them, see 11).

## 16. Widgets show money while the app is locked
`LedgerWidgets/*.swift`, `models/WidgetSnapshotWriter.swift`

With "lock the app" on, the home screen widgets still show the month's balance, the budget and the last three
transactions with their notes. Nothing checks the lock setting and nothing is marked `privacySensitive()`, so
the data also shows on a locked iPhone. Android has no widgets yet; what they should show is decision D5 in
[platform-decisions.md](platform-decisions.md).
**Fix:** when the lock is on, write a snapshot without amounts (or mark the views `privacySensitive()`).

## 17. Siri questions refuse whenever the lock setting is on; logging ignores it
`intents/LedgerEntities.swift` (`ensureAppUnlocked`), `intents/LogTransactionIntent.swift`

`ensureAppUnlocked` throws when the "lock the app" *setting* is on, not when the app is locked, so with the
setting on every question ("how much budget is left") is refused, even right after unlocking the app. Logging a
transaction doesn't check the lock at all. Android has no Siri equivalent (decision D3).
**Fix:** decide what the lock covers (D5), then check the same thing in every intent.

## 18. Switching a budget off and on doesn't re-arm its alert
`views/BudgetingView.swift` (`BudgetCardView`), `models/Budget.swift` (`BudgetDraft.apply`)

Since `8a76b5d` a budget alerts once a month, and saving it in the editor with a changed limit, category or
on/off state clears `lastAlertDate` so it can alert again. The switch on the budget card binds `isActive`
directly and skips that, so switching a budget off and on in the list doesn't re-arm it while doing the same
in the editor does. Android does the same, to match.
**Fix:** clear `lastAlertDate` in the card's toggle too, or in neither place.

## 19. Restoring a backup loses the general limit
`models/CSVImporter.swift` (`insertBudget`), `views/BudgetingView.swift`

The budgets screen creates an empty, inactive general limit the first time it opens. The importer skips any
budget that already exists, so importing a `budgets.csv` with a general limit (`;500;true`) into an app whose
budgets screen was ever opened keeps the empty one. Category budgets only have the same problem when one
for that category exists already. Android does the same, to match.
**Fix:** let an imported budget update an existing one that has no limit, or always take the imported values.

## Optional: receipt OCR that reads O as zero
On a monospace receipt font, Android's OCR read "UKUPNO" as "UKUPN0". The Android parser now treats
a 0 touching letters as "o" when looking for keywords (amounts are untouched). Apple's recognizer
may not need it, but it's a cheap safeguard.
