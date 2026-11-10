# Implementing decisions D1–D14

How Android carries out the decisions in [platform-decisions.md](platform-decisions.md) (made on 10 November 2026),
and what they ask of iOS. Android work only happens in this repo; the iOS tasks at the end are for the iOS developer.
Kotlin paths are relative to `app/src/main/java/work/spyglee/ladger/`.

## Order

One pull request per feature, as D14 asks of iOS too. Sizes as in part 1 of the decisions doc.

| # | Pull request | Decisions | Size | Needed for the first release |
|---|---|---|---|---|
| 1 | App ID `work.spyglee.ledger` | D6 | S | yes |
| 2 | Backup and phone-to-phone transfer | D2 | S | yes |
| 3 | App lock: no re-lock after our own pages; notifications on the lock screen | D5 | S | yes |
| 4 | Exact alarms | D10 | S | no |
| 5 | Per-app language | D8 | S | no |
| 6 | Widgets | D4, D5 | L | no |
| 7 | Pro subscription | D1 | L | yes |
| 8 | Finish the `fa64623` sync: version 1.0 | D7 | S | yes |
| 9 | Multi-page scans | D9 | M | no |
| 10 | Cyrillic OCR (prototype first) | D9 | M–L | no |
| – | CSV spec, a document only, any time | D12 | S | no |

- **The app ID comes first.** Every later test on a phone then runs under the final ID, and Google Play needs the
  final ID before subscription products can be created (PR 7).
- **Version 1.0 comes last.** It belongs to iOS `fa64623`, which also brings the medium chart widget (PR 6), the
  StoreKit test setup and the privacy manifest (both PR 7).

---

## 1. App ID `work.spyglee.ledger` (D6)
**Changes**
- `app/build.gradle.kts`: `applicationId = "work.spyglee.ledger"`.
- Keep `namespace` and the Kotlin packages as `work.spyglee.ladger`. Users never see them, and renaming them
  would touch every file for nothing.
- `res/xml/shortcuts.xml`: `targetPackage` becomes the new ID. `targetClass` stays as it is, because class names
  follow the namespace. The action strings in `QuickAction.kt` can stay as they are.
- Commands and comments: `README.md` (adb commands, the instrumentation runner `work.spyglee.ledger.test`) and
  the `run-as` comment in `scan/ReceiptScanner.kt`.
- `docs/platform-decisions.md`, the app identity table in part 2.

**Before installing:** the new ID installs as a separate app. The sample data on the phone needs a CSV export from
the old app and an import into the new one; this loses receipt photos. Then uninstall the old app.

**Check:** the quick actions open the right screens; the instrumented tests run on the emulator.

## 2. Backup and phone-to-phone transfer (D2)
**Done**, branch `backup-transfer`.

**What was built**
- Manifest: `allowBackup="true"`. Rules in `res/xml/data_extraction_rules.xml` (Android 12+),
  `res/xml-v28/backup_rules.xml` (Android 9–11) and `res/xml/backup_rules.xml` (Android 8).
- Only the app's own data is included:
  - `ladger.db` with its `-wal`, `-shm` and `-journal` files. Most recent changes live in the WAL file.
  - `files/receipts` and `files/datastore`. The merchant learning is in the database.
  - Files that libraries keep in the same folders stay behind: ML Kit's logging database and its preferences.
- **Google backup only when it is encrypted end to end** with the screen lock.
  - Android 12+: `disableIfNoEncryptionCapabilities`.
  - Android 9–11: `requireFlags`, with each path listed once for encrypted backup and once for
    phone-to-phone transfer. Android checks each `<include>` on its own (`BackupAgent`, Android 11 source).
  - Android 8 can't do either, so it has no backup at all; moving from it stays CSV only. **Not discussed
    in D2; say if Android 8 should back up anyway.**
- Phone-to-phone transfer works without encryption, since it goes directly to the new phone.
- After a restore, `LadgerApp.onCreate` sets the alarms again on the first start (from the code). The
  notification permission doesn't move with the data.

**Tested on the emulator (Android 14)** with Android's local test transport:
- Encrypted backup, then uninstall and reinstall: the database, settings and a receipt file came back byte for
  byte. The libraries' files didn't. The app opened with the transaction, and the default categories weren't
  added a second time.
- Unencrypted backup of the same data: refused. Encrypted before and after: accepted.
- Simulated phone-to-phone transfer, unencrypted: everything came back byte for byte.
- Google's own transfer transport backs up fine but can't restore on the same device. A real transfer needs two
  phones.
- The Android 8–11 rules couldn't be tried; there is no emulator image for them here.

**Still to check:** whether Google's Data safety guidance counts Android backup as "collected" (RELEASE.md).

**Accepted risk:** above 25 MB Android skips the Google backup completely. If we want to tell the user, a custom
`BackupAgent` gets `onQuotaExceeded`. That isn't decided, so it isn't part of this PR.

## 3. App lock (D5, Android part)
**Done**, branch `app-lock-d5`.

**No re-lock after our own pages**
- `lock/AppLock.kt`: `expectReturn()` is called right before the app opens one of its own outside pages:
  - the CSV import picker and the export folder picker;
  - notification settings (Settings);
  - app details when the camera permission is refused (transaction editor).
- The next trip to the background then doesn't lock, and coming back within 5 minutes stays unlocked.
  - After 5 minutes it locks, since Android can't tell whether the user went somewhere else from there.
  - The screen turning off during the trip locks it right away, so a phone that was put down never comes
    back to an unlocked app.
  - If the app doesn't go to the background within 10 seconds, the expectation lapses.
- `MainActivity.onStart` calls `onForeground`, before the unlock prompt in `onResume`.
- If Android ends the app's process while the user is in the picker, the app starts locked, as any new
  process does.
- PR 4 (the exact-alarm page) and PR 7 (Manage subscription) must call `expectReturn()` too.

**Notifications off the lock screen while the app lock is on**
- The planned-transaction and budget notifications show amounts. With the lock on, they get
  `VISIBILITY_SECRET`: nothing on the lock screen, and the full notification once the phone is unlocked.
  With the lock off, they keep the default (`VISIBILITY_PRIVATE`).
- A public version without the amounts wasn't enough. Android only shows it when the user has hidden
  sensitive content on the lock screen, and that isn't the default.
- The notifiers read the lock setting when they post (`showRecorded` and `BudgetAlerts.showReached` suspend
  now), so a notification from an alarm while the app was closed follows it too.
- Turning the lock on posts the planned-transaction and budget notifications that are already showing again,
  without sound, so they leave the lock screen too.
- The daily reminder has no amounts and is unchanged.

**Tested**
- Unit tests: 7 new `AppLockTest` cases (own page within and after the limit, one trip only, a lapsed
  expectation, coming back without leaving, the screen turning off).
- On the emulator:
  - The new `LockScreenNotificationTest` checks both notifications with the lock on and off, and that
    turning the lock on moves notifications already showing off the lock screen.
  - By hand with the lock on: the import picker stops the app, and coming back doesn't ask to unlock. Going
    home and coming back does ask, and so does turning the screen off while the picker is open.

The app switcher stays as it is (decided).

## 4. Exact alarms (D10)
**Done**, branch `exact-alarms`.

**What was built**
- Manifest: `SCHEDULE_EXACT_ALARM`, not `USE_EXACT_ALARM`, which Play keeps for alarm-clock and calendar apps.
- `reminder/ExactAlarms.kt`: the planned-transaction alarm and the daily reminder use
  `setExactAndAllowWhileIdle` when exact alarms are allowed, and `setWindow` as before otherwise.
  - "Allowed" means always below Android 12; Android 12 and 13 grant it on install; Android 14+ asks.
- **Asking, once:** right after the notification permission, when a plan is saved or the reminder is turned on.
  - "Allow" and "Not now" both go ahead with the save straight away; "Allow" then opens the system page.
  - The answer is remembered (`didAskExactAlarms`), so the app doesn't ask again.
  - The system page counts as one of the app's own pages for the app lock (D5).
- **Settings** shows "Allow exact timing" for as long as exact alarms are off.
- When the user allows them, Android tells the app (`ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`),
  and `ReminderRescheduleReceiver` sets both alarms again, exact.
  - When the user takes the permission back, Android stops the app. The alarms become inexact again on its
    next start.
- `AppContainer` can take its own settings store, so UI tests no longer share the app's settings.
- Five new Android-only strings, added to translations-to-review.md for the iOS developer to send (D14).

**Tested**
- On the Android 14 emulator, where exact alarms are off for a new install:
  - Settings showed the row. Turning the reminder on asked; "Allow" opened the system page.
  - After allowing there, the row went away, and the reminder alarm was set again as exact (`window=0`,
    `exactAllowReason=permission`).
  - After taking it back, Android stopped the app. On the next start the alarm was inexact again (10-minute
    window), and the row was back.
- `PlannedTransactionsFlowTest` answers the question with "Not now" and checks that the plan is saved anyway.
- 169 unit tests and 36 instrumented tests pass.
- Not checked: timing while a real phone sits idle for a long time. That's for the phone session.

**Before release:** check the Play Console rules for exact-alarm permissions (help article 12253906).

## 5. Per-app language (D8)
**Changes**
- `app/build.gradle.kts`: `androidResources { generateLocaleConfig = true }`, and
  `res/resources.properties` with `unqualifiedResLocale=en`. Android 13+ then lists the four languages under
  the app in system settings.
- The activity isn't an AppCompat activity, and nothing is needed below Android 13 (the decision is 13+).

**Check:** texts made outside an activity follow the app language: notifications from alarms, the widgets (PR 6)
and money and date formatting.

**Translations:** send the Android-only strings in `docs/translations-to-review.md` to the iOS developer, who
sends strings to translators (D14).

## 6. Widgets (D4, D5)
**Changes**
- Dependencies: Jetpack Glance (`glance-appwidget`, `glance-material3`).
- New package `widget/` with three widgets, each with an `appwidget-provider` XML and a preview:
  - month chart: 2×2, plus 4×2 with a legend, as in iOS `fa64623`;
  - budget: 4×2;
  - last three transactions: 4×2.
- `domain/WidgetSnapshot.kt`, ported from iOS's `WidgetSnapshotWriter`:
  - the month's slices, like the main screen's donut;
  - the month's balance;
  - the budget shown: the general limit when it's set and on, else the first active category budget by name;
  - the three latest transactions.
- Port iOS's widget snapshot test with it.
- The donut is drawn into a small bitmap with the colours of `ui/main/DonutChart.kt`, since Glance can't draw arcs.
- **Refresh after every change:** watch the transaction, budget, category and account tables through Room's
  invalidation tracker, with a short debounce. Also refresh at the start of each month, and when the app lock
  setting or the language changes.
- Tapping a widget opens the app, and the app lock applies as usual.
- **With the lock on (D5): no amounts and no notes.**
  - **Open point, to agree with iOS so both look the same (#16).** Proposal: the chart keeps its slices but
    drops the balance; the budget keeps its bar and name; transactions show their category and date only.
- Strings: the widget names and descriptions already come from the iOS catalog.

**Tests:** unit tests for the snapshot, with the lock on and off; Glance's testing library for the layouts.
Check on the emulator: each size, dark mode and every language.

## 7. Pro subscription (D1)
**Play Console (needs PR 1)**
- Upload a build to internal testing.
- Create two subscriptions with the iOS IDs `work.spyglee.ledger.pro.monthly` (€2.99) and `.yearly` (€29.99). Each
  gets one base plan and a 7-day free trial offer. Other countries' prices follow iOS where possible.
- Add license testers.

**Network permissions**
- Manifest: remove the two `tools:node="remove"` lines for `INTERNET` and `ACCESS_NETWORK_STATE`.
- **Important:** with `INTERNET` back, ML Kit's usage logging (datatransport) could start sending. "The only
  network use is billing" needs that logging switched off. Remove its datatransport services and receivers in the
  manifest, or exclude its upload module, and check that nothing breaks.
- Then check that the app itself sends nothing after scanning and reading receipts with Gemini Nano: Android
  Studio's network inspector, or the app's traffic in `adb shell dumpsys netstats`.

**Code**
- `billing/SubscriptionManager.kt`, like the iOS one:
  - Play Billing (`billing-ktx`); query purchases at start and when the app comes back.
  - Pro means an active purchase of either product. Acknowledge new purchases, or Play refunds them after three
    days.
  - Remember the last answer in `data/SettingsRepository.kt`, so the statistics don't look locked while
    checking (decided).
- Paywall screen, like iOS's `PaywallView`:
  - the chart icon, `paywall_title` and `paywall_subtitle`;
  - both products with Play's price and trial text;
  - Restore purchases;
  - Terms and Privacy links (`spyglee.work/ledger/terms/`, `/privacy/`);
  - a close button.
- Settings, as in iOS's `statisticLink`: without Pro, the four statistics rows show a lock and open the paywall.
  The routes in `ui/LadgerNavHost.kt` are guarded too (`stats/*`, `stats/price_history/*`).
- With Pro, Settings shows "Manage subscription", which opens the subscription page on Google Play. That's one of
  our own pages for PR 3.

**Strings:** the paywall texts come from the iOS catalog. The Android-only ones (Manage subscription, trial
wording) go to the iOS developer for translators.

**Docs and listing**
- README and `docs/RELEASE.md`: the network permission, used only for billing.
- Data safety answers: the equivalent of iOS's privacy manifest. Check how Play Billing purchases are declared.
- Store listing: "the only network use is Google Play billing".
- Ask whoever runs spyglee.work to make the terms and privacy pages cover Android and Google Play.

**Tests**
- Unit tests for the Pro logic, through a small interface around `BillingClient`.
- On the emulator, which needs a Google Play image and a license tester account: buy, trial, cancel and
  restore.
- Not on the phone with the sample data (no instrumented tests there).

## 8. Finish the `fa64623` sync (D7)
- `app/build.gradle.kts`: `versionName = "1.0"`, and `versionCode` goes up.
- `docs/RELEASE.md`: replace "currently 0.1" with the rule: Android takes the iOS version of the commit it's
  synced to.
- Run `tools/sync-ios-strings.ps1`: `fa64623` removed 150 lines of strings.
- README: synced to iOS `fa64623`. Update the status table in the decisions doc.
- The rest of `fa64623` is covered by PR 6 (medium chart widget) and PR 7 (StoreKit test setup and privacy
  manifest). The login removal needs nothing on Android.

## 9. Multi-page scans (D9)
- `scan/ReceiptCameraScreen.kt`: after "Use photo", offer "Add page" or "Done". Show the pages taken as
  thumbnails, and let the user remove one.
- `ui/transactions/TransactionEditorViewModel.kt`: `_scannedPhotos` is already a list, and
  `ReceiptRepository.attach` already stores pages in order. Read each page and join the text in page order
  before parsing, as for a PDF. The page limit is the same as for PDFs.
- Tests: joining the pages' text (unit). The camera isn't covered by tests; check it by hand on the phone.

## 10. Cyrillic OCR (D9)
- **Prototype first**, on real Ukrainian receipts. We need samples; ask the iOS developer or Ukrainian users.
- **Candidate:** Tesseract through Tesseract4Android (Apache 2.0), with the small `ukr` model from
  `tessdata_fast`.
  - Measure the app size per ABI. The App Bundle already gives each phone only its own ABI.
  - Measure the time per page.
  - Check that its native libraries support 16 KB memory pages, which Play requires.
- **When it runs**, to choose after the prototype: always, as a second pass; only when the app language is
  Ukrainian; or when ML Kit's text looks like misread Cyrillic. The second pass costs time on every scan.
- Tesseract's lines with their positions go into `domain/receipt/ReceiptTextLayout.kt` like ML Kit's blocks.
  `ReceiptRulesParser` already knows the Ukrainian words, from iOS.
- Docs: the "Text recognition" row in part 2, and the README's privacy note (Tesseract runs on the phone too).

## CSV spec (D12)
- Android drafts `docs/csv-format.md` from `domain/csv/CsvFormat.kt` and the importer tests. It covers the 11
  files, headers, number and date formats, and how rows are matched on import. The iOS developer reviews it and
  copies it to the iOS repo.
- Add a test that every icon name in `ios-demo-data` maps to an Android icon. Until iOS changes `generate.py`,
  `desktopcomputer` is the known exception.

## Nothing to build
- **D3:** nothing; look at AppFunctions again when it's stable. The Siri strings stay generated but unused.
- **D11:** nothing until iOS fixes #8, #12, #18 and #19. Android then ports each fix in that sync and replaces
  the tests and comments that pin today's behaviour. For example, `data/BudgetRepository.kt` says the list switch
  "keeps this month's alert date, as on iOS".
- **D13:** no change.
- **D14:** each iOS commit gets the few lines on behaviour changes; shared-format changes are flagged before
  they ship.

---

## For the iOS developer
From the decisions; Android doesn't change the iOS repo.

- **D2:** don't turn on iCloud sync before we've agreed whether Android needs sync too.
- **D5:**
  - With the lock on, widgets show no amounts or notes (#16). Agree with Android on what's left (see PR 6).
  - Siri questions follow the same rule as widgets (#17).
  - Still open: whether Siri logging checks the lock.
- **D6:** rename the URL scheme from `ladger://` to `ledger://`, together with the widget links and the share
  extension.
- **D10:** record due planned transactions in a background refresh task (#10).
- **D11:** fix #8 (names ignore case), #12 (no double recording after a schedule change), #18 (the list switch
  re-arms the alert) and #19 (restore takes the backup's limits). Android follows in the same sync.
- **D12:** review `docs/csv-format.md` and copy it. Keep `DemoData` up to date. `generate.py` should only use icon
  names from `Icons.swift`.
- **D14:**
  - Smaller commits, each with a few lines on behaviour changes.
  - Flag changes to CSV, string keys or phrases.
  - Send new strings to translators, including Android's own list in translations-to-review.md.
- **D1 (website):** the terms and privacy pages cover Android and Google Play.
