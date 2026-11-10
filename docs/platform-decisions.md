# Ledger on iOS and Android: status, differences and decisions

For the iOS and Android developers. iOS is the main app and Android follows it feature by feature, but some things
can't work the same way on Android, and some already work differently. This document lists:

1. how far Android is behind iOS,
2. how the two apps already behave differently, and why,
3. the decisions we need to make together before porting the rest.

Compared on 10 November 2026: iOS `main` at `fa64623` and Android at branch `sync-ios-46702e5`. Updated the same
day for branch `sync-ios-8a76b5d-part1` ("part 1" below), which ports everything from `8a76b5d` that needs no
decision. File references without a repo name are to the iOS repo when they are Swift files, and to this repo when
they are Kotlin files.

Each decision in part 3 has the options, an **Android proposal** (a starting point, not a conclusion) and the
**Decision** the iOS and Android developers made together on 10 November 2026. How Android implements them:
[decisions-plan.md](decisions-plan.md).

---

## 1. How far Android is behind

| iOS commit | Date | What it added | Android |
|---|---|---|---|
| up to `f909b82` | until 21 Oct | everything up to planned transactions and list search | **done**, on `main` |
| `46702e5` | 28 Oct | budgets, receipt items and photos, price comparison, Sources renamed to Accounts | **ported**, branch `sync-ios-46702e5`; tested on the emulator and a phone; pull request not opened yet |
| `8a76b5d` | 30 Oct | "Bells and whistles": widgets, Siri, quick actions, Pro subscription, CSV for all data, editing receipt items, Polish | **part 1 done**, branch `sync-ios-8a76b5d-part1`: everything that needs no decision |
| `fa64623` | 3 Nov | cleanup: StoreKit test setup, privacy manifest, login stubs removed, version 1.0, medium chart widget | **not started**: everything in it waits for a decision, except the login removal, which Android doesn't need |

Those two commits change 74 files (+8,300 lines). About 1,600 lines are demo CSV data and 2,600 are the string
catalog, which leaves about 4,000 lines of code, 1,400 of them tests. After part 1, what's left are the features
that wait for a decision: widgets, Siri, the Pro subscription and version 1.0.

### What the two commits add, and where Android is

| Feature (iOS) | Size | Decision | Android |
|---|---|---|---|
| **Widgets**: month chart (small + medium), budget (medium), 3 latest transactions (medium) | L | D4, D5 | waiting |
| **Siri and Shortcuts**: log a transaction; ask what's left of the budget, the month's spending, an account balance | L | D3 | waiting |
| **Pro subscription**: the four statistics screens need Pro (€2.99 a month or €29.99 a year, 1-week free trial) | L | D1 | waiting |
| **CSV for all data**: categories, budgets, collections and receipt items are now exported; the importer works out the file type from its header and also imports accounts and planned transactions | L | (D12) | **done** in part 1 |
| **Editing receipt items**: tap an item to fix it, add or remove items; total = quantity × unit price unless changed | M | none | **done** in part 1 |
| **Budget alert at most once a month**: it no longer comes back when spending drops under the limit and rises again; editing a budget re-arms it | S | none | **done** in part 1 |
| **Quick actions** on the app icon: Add transaction, Scan | S | none | **done** in part 1 |
| **Polish** translation | M | (D8) | **done** in part 1; per-app language done since D8 |
| **New app icon** | S | none | **done** in part 1 (`tools/make-icons.py`) |
| **Version 1.0** | S | D7 | waiting |
| **Privacy manifest** ("no data collected"), which on Android means the Play Data safety form | S | D1 | waiting |
| **Demo data**: `DemoData/generate.py` writes a year of data as CSV files | S | (D12) | **done** in part 1, as Android test data |
| **New iOS tests** (budgets, CSV, model logic, recurrence) | M | none | **done** in part 1, ported to Android tests |
| Login and sign-up screens removed | none | none | nothing to do; Android never had them |

A decision in brackets is related, but the port didn't have to wait for it.

### Known iOS issues, checked again at `fa64623`
[ios-issues.md](ios-issues.md) lists problems found during the port. At `fa64623`:

- **Fixed:** #11 (export files the importer rejected) and #15 (budgets and receipt items missing from the export; receipt
  photos are still not exported).
- **Still open:**
  - #1: the dark-mode lock screen lets balances show through.
  - #7: collections can't sync once iCloud is turned on.
  - #8: duplicate-name checks are case-sensitive.
  - #9: the "blance" typo.
  - #10: the "recorded" notification arrives before the transaction is recorded.
  - #12: changing a plan's start date can record an occurrence twice.
  - #14: the daily reminder's budget hint goes stale.
- **New in `8a76b5d`:**
  - Widgets show balances and transactions on the home screen even when the app lock is on.
  - With the lock *setting* on, every Siri question ("how much budget is left") is refused, even while the app is
    unlocked. Logging a transaction through Siri ignores the lock altogether. See `intents/LedgerEntities.swift`,
    `ensureAppUnlocked`.
  - The on/off toggle in the budget list doesn't re-arm the budget alert; editing the budget does.
  - Restoring a backup loses the general limit when the budgets screen was ever opened, because that screen
    creates an empty one, which the import then keeps.

These are #16–#19 in ios-issues.md.

---

## 2. How the apps already behave differently

Android at `46702e5` has the same features as iOS at `46702e5`, and part 1 adds the decision-free features of
`8a76b5d`. The differences below come from the platform, or are places where Android had to choose. Most users
won't notice them, but anyone comparing the two apps will.

### Scanning and reading receipts

| | iOS | Android |
|---|---|---|
| Camera | System document scanner (VisionKit): finds the edges, straightens the page, several pages per scan | Own camera screen (CameraX): **one photo per scan**, cropped to what the preview showed. Zoom buttons, tap to focus, torch, Retake / Use photo. |
| Text recognition | Apple Vision, which also reads Cyrillic | ML Kit, built into the app, **Latin letters only**. Ukrainian words such as "СУМА" aren't read; amounts and dates are. |
| AI reading (merchant, total, category, items) | Apple Intelligence (Foundation Models); the output is forced into a schema | Gemini Nano through Android's AICore, on few phones so far. Plain-text answers parsed leniently, with time limits. |
| Without on-device AI | Rules parser: total, date, time, merchant; no items | Same |
| Sharing a photo or PDF into the app | Share extension reads it, then opens the app with the text | The app itself receives the share and reads it |

- Android avoided Google's ready-made document scanner because it runs in Play services. With its own camera,
  photos never leave the app.
- ML Kit returns a label and its right-aligned amount as separate blocks, so Android puts them back into rows
  (`ReceiptTextLayout.kt`).
- Android's parser has two extras:
  - It reads a 0 touching letters as "O" ("UKUPN0").
  - It knows a few more tax words.
- On both, a shared file is only read and isn't stored; a scanned photo is kept with its transaction.
- Editing receipt items (part 1) works the same, with one small difference: iOS starts a new item at a total of 0,
  so it can be saved with just a name. Android leaves the total empty until a unit price or total is typed, and
  Save waits for it.

### App lock and privacy

| | iOS | Android |
|---|---|---|
| Unlock with | Face ID / Touch ID or the passcode | Fingerprint or face (any strength) or the screen lock PIN/pattern |
| Locks when | The app goes to the background | The app is no longer visible, except while the user is in a file picker or settings page the app opened (D5). Coming back from one of those after more than 5 minutes, or after the screen turned off, locks anyway. |
| Notifications on the lock screen | The system default shows previews only after Face ID | With the lock on, the planned-transaction and budget notifications (which show amounts) stay off the lock screen and show once the phone is unlocked (D5). |
| App switcher | Always blurred, lock on or off | Hidden only while the lock is on. This also blocks screenshots and screen recording. |
| No screen lock on the device | The lock toggle doesn't change | "Set up a screen lock…" message |

### Notifications and things that happen in the background

| | iOS | Android |
|---|---|---|
| Planned transactions | Recorded when the app is next opened. Notifications are scheduled in advance, so "has been recorded" can arrive days before the transaction exists. | An alarm at the due time **records the transaction in the background**, then notifies. Also caught up when the app opens. |
| Daily reminder budget hint | Fixed when the reminder is scheduled, can be out of date | Worked out when the reminder goes off |
| Timing | Exact | Exact once the user allows exact alarms (D10). Android 14+ asks once, when a plan is saved or the reminder turned on; without the permission, alarms stay inexact and may arrive a little late while the phone is idle. |
| After restart, update, clock or time-zone change | Handled by the system | The app sets its alarms again |
| While the app is open | Reminder and planned notifications are hidden; the budget alert shows as a banner | All are shown. A budget alert replaces the previous one for that budget. |
| Asking for permission | After saving a budget or plan | Before saving, then saves |

Android has notification channels, so users can turn the three kinds (daily reminder, planned transactions,
budgets) on and off separately in system settings.

### Data, backup and moving to a new phone

| | iOS | Android |
|---|---|---|
| Database | SwiftData. iCloud sync is prepared but off (`LedgerStore.swift`). | Room, version 3, with tested migrations |
| While the phone is locked | Data can't be read (`NSFileProtectionComplete`) | Readable after the first unlock since boot, which is what lets alarms record planned transactions |
| Receipt photos | Inside the database | JPEG files in the app's private folder |
| Backup | **Included** in iCloud/computer backups and iPhone-to-iPhone transfer | **Included** since D2: in Google backup, only when encrypted end to end with the screen lock (Android 9+), and in phone-to-phone transfer. Google backup stops above 25 MB. Android 8: none. |
| Moving to a new phone | Automatic | Automatic with Android's phone-to-phone transfer or a Google backup, photos included. CSV still works but loses photos and learned merchant categories. |
| Network | None (purchases go through the App Store) | **No INTERNET permission at all**, so the app itself sends nothing. Android's backup and transfer (D2) are the only way data leaves the phone. |

### CSV
- The format is identical, and files go both ways. Since part 1, Android reads and writes all 11 files like iOS
  `8a76b5d`. A test imports a copy of iOS's `DemoData` (about 1,200 rows) and exports the same lines back.
- **Exporting:** iOS shows a save dialog. On Android you pick a folder, and files with the same name are
  overwritten.
- **Dates:** Android reads them strictly (`d.M.yyyy`); iOS is more lenient.
- **Import:** all or nothing on both.

### Look and navigation
- **Material 3, not an iOS look-alike.**
  - The iOS blue/indigo and category colours are kept.
  - Material You wallpaper colours are off.
  - SF Symbol names are kept as icon keys in the data and shown as Material icons.
- **Full screens instead of sheets:** what is a sheet on iOS (Add, lists, editors) is a full screen with a
  back arrow on Android. The receipt item editor (part 1) is a small dialog instead of a half-height sheet.
- **App icon shortcuts:** iOS adds its quick actions when the app starts; Android lists them in the app's
  manifest, so they work before the app is first opened. On both, they wait until the app is unlocked.
- **Swipe and long press:**
  - Swiping a row acts as soon as the swipe completes, with no button to tap: swipe left deletes, swipe right
    pauses or resumes (budgets, planned transactions).
  - Long press opens a small menu, not a preview.
- **Charts:** drawn by hand on Android. Tap a month to select it, tap it again to clear the selection.
- **Save sound:** the "ka-ching" is a sound file bundled with the Android app. iOS loads a system sound by its
  file path (`AddTransactionView.swift`), which isn't public API.

### Languages
- iOS: English, Croatian, Ukrainian and (since `8a76b5d`) **Polish**. Users can pick a language per app.
- Android: the same four (Polish since part 1), generated from the iOS catalog by `tools/sync-ios-strings.ps1`.
  - Since D8, users can pick a language for Ledger alone on Android 13+, in system settings, as on iOS.
    Before Android 13, Ledger follows the phone's language.
  - Amounts keep the phone's region, and so its currency, when Ledger has a language without a region,
    as iOS does. Android 14's picker usually asks for a region too, and then that region's currency is used.
  - 26 Android-only strings were translated without a native speaker, in all three languages besides English
    ([translations-to-review.md](translations-to-review.md)).
  - The Siri texts in the catalog are generated too but unused, since Android has no Siri (D3).

### App identity

| | iOS | Android |
|---|---|---|
| ID | `work.spyglee.ledger` | The same since D6 (was `work.spyglee.ladger`). The Kotlin code keeps its `work.spyglee.ladger` package. |
| Version | 1.0 (since `fa64623`) | 0.1 |
| Runs on | iOS 26.5, iPhone and iPad | Android 8.0 and later, phones and tablets |
| URL scheme | `ladger://` (also with an a) | none; Android uses intents |
| Icon | Three bars forming an L on black (since `8a76b5d`) | The same artwork (part 1), drawn a little smaller so the round and squircle masks of Android launchers don't cut off the bars; also a one-colour version for themed icons |

### Tests
- **iOS:** 7 test files (receipt parser, budgets, CSV, Siri logging, model logic, recurrence, widget snapshot).
- **Android:** 162 unit tests and 31 on-device tests with part 1 (130 and 30 before).
  - Covered: the business rules, database, migrations, real OCR, main screens, and the iOS test cases and demo
    data from `8a76b5d`.
  - Not covered: live Gemini Nano, alarms, fingerprint, camera, sharing, quick actions with the app lock on.

### Where Android copies iOS on purpose, and where it doesn't
- **Copied, so both apps match:**
  - #8: case-sensitive names.
  - #12: changing a plan's start date.
  - #18 and #19 (part 1): the budget list switch doesn't re-arm the alert, and restoring a backup keeps the empty
    general limit.
  - #11 and #15, the export gaps, were copied too. iOS fixed them in `8a76b5d`, and Android followed in part 1.
- **Not copied:**
  - #1: Android's lock screen is opaque.
  - #10 and #14: background recording, and the hint is worked out when the reminder goes off.

---

## 3. Decisions

| # | Question | Android proposal | Decision | Blocks |
|---|---|---|---|---|
| D1 | Pro subscription on Android, and the "no internet" promise | Google Play Billing; network permission used only for billing | As proposed | paywall port, first release |
| D2 | Backup and moving to a new phone; iCloud sync later? | Allow phone-to-phone transfer, no cloud backup; decide sync before iOS turns it on | Transfer **and** Google cloud backup, photos included | first release |
| D3 | What replaces Siri and Shortcuts | Nothing for now; the quick actions (done in part 1) | As proposed | Siri port |
| D4 | Widgets | Port all three | As proposed | widget port |
| D5 | What the app lock covers | Widgets and lock-screen notifications hide amounts when the lock is on | As proposed; app switcher unchanged | widgets, D3 |
| D6 | Android app ID spelled "ladger" | Change to `work.spyglee.ledger` before the first upload | As proposed | first release |
| D7 | Version numbers | Same marketing version on both | As proposed | first release |
| D8 | Per-app language (Polish is done in part 1) | Turn it on | As proposed | none |
| D9 | Receipt camera and Cyrillic | Keep our camera, add multi-page; accept the Cyrillic gap | Proposal **plus** Cyrillic OCR | none |
| D10 | Background work for planned transactions | Keep Android's way; iOS words the notification "due" or records in the background | Android: exact alarms. iOS: records in the background | none |
| D11 | Fix shared bugs together or keep matching | Agree on the right behaviour; both apps fix together | As proposed | none |
| D12 | CSV as the shared contract | A short shared spec; keep `DemoData` as the shared test files (Android uses a copy since part 1) | As proposed | none |
| D13 | Android-native look or iOS look-alike | Stay Android-native | As proposed | none |
| D14 | How we sync from now on | One Android PR per iOS commit; a few lines on behaviour changes per iOS commit | As proposed; iOS sends strings to translators | none |

### D1. Pro subscription on Android
**Context.**
- **What iOS does:**
  - Statistics (income vs. expenses, savings, top categories, price comparison) need "Ledger Pro":
    `work.spyglee.ledger.pro.monthly` at €2.99 and `.yearly` at €29.99, both with a 1-week free trial
    (`Ledger.storekit`). Everything else is free.
  - The check happens on the phone; there is no server and no account.
- **What it means on Android:** Google Play Billing.
  - The Play Billing library adds the INTERNET and ACCESS_NETWORK_STATE permissions to the app (reported for
    version 6 and later; to confirm with a test build).
  - Android currently removes those permissions on purpose. README.md, RELEASE.md and the Play Data safety answers
    say the app has no internet access.

**Options.**
1. **Play Billing, and accept the network permissions.** Update the README, RELEASE.md and the Data safety
   answers. The app still sends no user data anywhere.
2. **Play Billing, but keep stripping the permissions.** Purchases go through the Play Store app, so this may work,
   but Google doesn't support it, so we'd have to test it on many phones and it could break with any library update.
3. **No subscription on Android:** free for now, or a one-time price on Play.

**Also to agree, whichever option we pick:**
- Same product IDs, prices, trial and gated screens as iOS?
- Someone who pays on iPhone doesn't get Pro on Android, and the other way round, because there are no accounts.
  Is that acceptable?
- Android needs its own paywall screen (iOS uses Apple's ready-made one) and a "Manage subscription" link to Google Play.
- The terms and privacy pages (`spyglee.work/ledger/terms`, `/privacy`) must also cover Android and Google Play.
- iOS forgets the subscription on every launch, so the statistics look locked until the check finishes. Android
  can remember the last answer.

**Android proposal:** option 1, same products and prices. Say clearly in the store listing that the
only network use is Google Play billing.

**Decision:** option 1, as proposed.
- Google Play Billing. The app keeps the network permissions, and its only network use is Google Play billing; the
  store listing says so.
- Same product IDs, prices, 1-week trial and locked statistics screens as iOS.
- A subscription bought on one platform doesn't unlock the other. Accepted.
- Android remembers the last Pro status, so the statistics don't look locked while the check runs.

### D2. Backup, moving to a new phone, and iCloud sync later
**Context.**
- **iOS:** the data is part of the phone's iCloud/computer backup and moves to a new iPhone without the user
  doing anything.
- **Android:** backup is off, so the only way to a new phone is a CSV export, which loses receipt photos and the
  merchant → category learning.
- **iCloud sync later:** `LedgerStore.swift` is prepared for iCloud sync. If iOS turns it on, Android has nothing
  similar without user accounts and a server, or Google Drive. Either one needs internet access and a login.

**Options for Android backup.**
1. Keep it as it is: CSV only.
2. **Allow phone-to-phone transfer only.** When setting up a new phone, Android copies the app's data over a cable or
   directly over Wi-Fi; nothing goes to Google's cloud.
3. Also allow Google backup. It is end-to-end encrypted with the screen lock, and closest to iCloud backup, but the
   data does leave the phone.

**Android proposal:** option 2. Before iOS turns on iCloud sync, agree whether Android needs sync too. If it does,
that's a backend project, not a port.

**Decision:** option 3: phone-to-phone transfer **and** Google cloud backup.
- Both include everything, receipt photos too. Android skips an app's cloud backup altogether once it is over
  25 MB, which is roughly 100 or more scanned receipts; we accept that.
- iCloud sync: no work now. iOS doesn't turn it on before we agree whether Android needs sync too. If it does,
  that's a backend project.

### D3. What replaces Siri and Shortcuts on Android
**Context.**
- **What iOS has:** seven Shortcuts phrases ("Log an expense in Ledger", "Ledger budget", "Ledger spending this
  month", "Ledger total balance", …). They answer by voice with a small card, without opening the app.
- **Wallet automation:** logging can be triggered by a Shortcuts automation when you pay with Apple Pay, with the
  amount and merchant filled in (`LogTransactionIntent.swift`).
- **What Android lacks:** an equivalent of spoken answers without opening the app, of the result cards, and of
  the Wallet trigger.

**Options.**
1. **None for now.** Only the quick actions (Add transaction, Scan), which work the same on Android and are done
   in part 1.
2. Google Assistant "App Actions". Assistant is being replaced by Gemini, so it's not worth building for.
3. **AppFunctions**: lets Gemini call into the app. It needs Android 16 or later and is still an early library.
   Also, the answer then goes through Google's assistant, which sits awkwardly with "nothing leaves the device".
4. Public intents, so automation apps (Tasker etc.) can log a transaction. Small, but few users would use it.
5. Read bank and Google Wallet notifications to log payments automatically. **Not recommended:** it means reading
   every notification, and Play restricts it.

**Android proposal:** option 1. Look at AppFunctions again when it's stable.

**Decision:** option 1, as proposed: nothing besides the quick actions for now. Look at AppFunctions again when
it's stable.

### D4. Widgets
**Context.**
- **iOS:** three widgets.
  - Month spending donut with this month's balance: small, and medium with a legend.
  - Budget card: medium.
  - Last 3 transactions: medium.
  - They refresh when the app is left, and reset the month's numbers at the start of a month.
- **Android:** would use Jetpack Glance.
  - Android can refresh after every change, because the widget can read the database directly.
  - Glance can't draw a donut, so the chart has to be drawn as a picture.
  - Sizes map to 2×2 (small) and 4×2 (medium) cells.

**Options.** All three widgets; only the month chart first; none yet.

**Android proposal:** all three, refreshed after every change. What they show while the app lock is on is D5.

**Decision:** as proposed: all three widgets, refreshed after every change.

### D5. What the app lock covers
**Context.** Both apps have an optional lock. The new features show money outside the app, and today the platforms
already disagree on the details:

| Where | iOS today | Android today |
|---|---|---|
| Widgets | Show amounts and notes while the lock is on | none yet |
| Siri questions | All refused while the lock setting is on, even when unlocked | none |
| Siri logging | Works regardless of the lock | none |
| App icon quick actions | Wait until the app is unlocked | The same (part 1) |
| Notifications on the lock screen (amounts) | The system default shows previews only after Face ID | The system default shows the full text |
| App switcher | Always blurred | Hidden only with the lock on, and screenshots are blocked |
| Leaving for a file picker or settings | No lock (pickers are inside the app) | Locks, asks again on return |

**Options to agree on (one rule for both apps):**
- **Widgets while the lock is on:** show everything / hide amounts / show only "Locked".
- **Notifications while the lock is on:** full text / no amounts on the lock screen.
- **App switcher:** hide only with the lock on (Android now) / always hide.
- **Android only:** blocking screenshots with the lock on is a side effect. Android 13+ can hide the app switcher
  preview without blocking screenshots.
- **Android only:** don't re-lock while the user is in a file picker or settings page that the app itself opened.

**Android proposal:**
- With the lock on, widgets and lock-screen notifications show no amounts.
- Siri/Android questions behave like widgets.
- Android stops re-locking for its own pickers.

**Decision:** as proposed.
- With the lock on, widgets show no amounts or notes, and notifications show no amounts on the lock screen (the
  full text once the phone is unlocked).
- Siri questions follow the same rule as widgets.
- Android stops re-locking when the user comes back from a file picker or settings page the app opened.
- The app switcher stays as it is: iOS always blurs it; Android hides it only with the lock on, which also blocks
  screenshots.
- Not discussed: whether logging a transaction through Siri checks the lock (#17).

### D6. Android app ID spelled "ladger"
**Context.**
- The Android package is `work.spyglee.ladger`.
- iOS is `work.spyglee.ledger`, but its URL scheme is also `ladger://`.
- Once the app is uploaded to Google Play, its ID can never change; a new ID is a new app with new reviews and
  downloads. Android isn't on Play yet (RELEASE.md still lists the store assets as to do).

**Options.**
1. Keep `ladger`.
2. **Change to `work.spyglee.ledger`** now. Data on our own test phones has to be moved with a CSV export, which
   loses receipt photos, or with an adb backup.

**Android proposal:** option 2, before the first upload. iOS could rename the URL scheme to `ledger://` at the same
time (widgets and the share extension use it).

**Decision:** option 2, as proposed: Android becomes `work.spyglee.ledger` before the first upload, and iOS
renames its URL scheme to `ledger://` at the same time.

### D7. Version numbers
**Context.** iOS went to 1.0 in `fa64623`. Android is 0.1 (RELEASE.md says Android follows the iOS marketing
version). The build numbers are separate on each store anyway.

**Android proposal:** the same marketing version on both, and an Android release takes the version of the iOS
commit it's synced to.

**Decision:** as proposed: the same marketing version on both. An Android release takes the version of the iOS
commit it's synced to; build numbers stay separate.

### D8. Choosing a language per app
**Context.**
- iOS added Polish in `8a76b5d`. Android has it since part 1: `tools/sync-ios-strings.ps1` now also writes
  `values-pl`, and the 21 Android-only strings were translated, with nobody to check them yet.
- On Android 13+ the app can also offer its own language in system settings (`localeConfig`), as iOS does.
  Android doesn't do this yet.

**Android proposal:** turn on the per-app language. We need a native speaker for Polish (and the Croatian and
Ukrainian list in translations-to-review.md).

**Decision:** as proposed: turn on the per-app language (Android 13+), and find native speakers for Polish and
for the Croatian and Ukrainian strings in translations-to-review.md.

### D9. Receipt camera and Cyrillic
**Context.** See part 2: one page per scan on Android, and Ukrainian words aren't read.

**Options.**
- Keep our own camera and add multi-page scans, so photos stay in the app.
- Use Google's ML Kit document scanner instead. It is closest to iOS (edges, straightening, several pages), but
  it's a Play services component.
- For Cyrillic: accept the gap, since amounts and dates are still read, or add another OCR engine, which makes the
  app bigger.

**Android proposal:** keep our camera and add multi-page. Accept the Cyrillic gap unless Ukrainian users complain.

**Decision:** keep our camera and add multi-page scans, as proposed, **and** add an OCR engine that reads
Cyrillic, although it makes the app bigger.

### D10. Planned transactions and reminders in the background
**Context.**
- **Android:** planned transactions are recorded at their due time, even with the app closed.
- **iOS:** they wait until the app is opened, but the notification already says "has been recorded" (issue #10).
  Balances, widgets and the budget hint are wrong in between.

**Options for iOS:**
- Record in a background refresh task.
- Or change the wording to "is due".

**Option for Android:** use exact alarms. That needs a special permission and a Play declaration, so we suggest not.

**Android proposal:** Android keeps its way and stays inexact, and iOS picks one of the two options, so both apps
tell the user the same thing.

**Decision:**
- **Android** switches to exact alarms. It asks for the exact-alarm permission and stays inexact if the user
  refuses.
- **iOS** records planned transactions in a background refresh task, so "has been recorded" is true (fixes #10).

### D11. Fix shared bugs together, or keep matching
**Context.** Android copies some iOS bugs so both apps behave the same:
- #8: "Food" and "food" are different names.
- #12: changing a plan's start date can record an occurrence twice.
- The budget list toggle doesn't re-arm the alert (#18).
- Restoring a backup keeps the empty general limit instead of the backup's (#19).

**Android proposal:** agree on the correct behaviour for each one now. iOS fixes first, Android follows in the same
sync, and neither app fixes a shared behaviour alone.

**Decision:** as proposed: iOS fixes first, Android follows in the same sync, and neither app fixes a shared
behaviour alone. The correct behaviour:
- #8: names are compared ignoring case.
- #12: changing a plan's schedule never records an occurrence twice.
- #18: switching a budget off and on in the list re-arms its alert, as the editor does.
- #19: restoring a backup takes the backup's budget limits.

### D12. CSV as the shared contract
**Context.**
- CSV is the only way to move data between the apps and, on Android, to a new phone (D2).
- `8a76b5d` adds five file types and a new importer. Android ported both in part 1, so an iOS export imports on
  Android again (before, Android refused it with "unknown format").
- Android's tests import a copy of iOS's `DemoData/` (in `app/src/test/resources/ios-demo-data`), check that every
  line is read, and check that exporting gives the same lines back.

**To agree on:**
- **A short written spec**, e.g. `docs/csv-format.md` in both repos. It would cover the 11 files, their headers,
  number and date formats, and how rows are matched to transactions on import.
- Keep `DemoData/` as the **shared test files** both apps must import identically. When iOS changes the format,
  it updates `DemoData`, and Android copies it again.
- **Icons are stored as SF Symbol names.** Android maps them to its own icons, so a new icon on iOS needs a mapping
  on Android. Names outside iOS's icon list (`models/Icons.swift`) work on iOS but show as a placeholder on Android.
  For example, the demo data's "Home Office" collection uses `desktopcomputer`, which only `generate.py` uses.
  Proposal: CSV files only use names from `Icons.swift`.
- Receipt photos and merchant learning are not exported. Should a full backup (e.g. a zip with the photos) be a
  feature on both?

**Android proposal:** yes to the spec and the shared test files; the full backup can wait.

**Decision:** as proposed.
- A short spec, `docs/csv-format.md`, in both repos.
- `DemoData/` stays the shared test files: iOS updates it when the format changes, Android copies it.
- CSV files only use icon names from `Icons.swift`.
- The full backup (photos and merchant learning) waits.

### D13. Android-native look, or an iOS look-alike
**Context.** Android follows Material 3 conventions:
- Full screens instead of sheets.
- Swipe acts immediately.
- The system back button.
- The iOS colours are kept.

Users of each platform expect their own conventions, but screenshots of the two apps won't look the same.

**Options.** Stay native; or copy the iOS look more closely; also whether to use the wallpaper colours (Material You).

**Android proposal:** stay native, keep the iOS colours, no wallpaper colours.

**Decision:** as proposed: stay Android-native, keep the iOS colours, no wallpaper colours.

### D14. How we sync from now on
**Context.**
- One Android pull request per iOS commit works well for small commits.
- `8a76b5d` mixes eight features in one commit, which makes it hard to port and review in steps. Android had to
  split it: part 1 has the features that need no decision; the rest follows once the decisions are made.

**Android proposal:**
- **Smaller iOS commits, one feature each where possible**, with a few lines on any behaviour change (e.g. "budget
  alerts once a month now").
- **Changes to shared formats** (CSV, string keys, phrases) are flagged, so Android can check them before they ship.
- **Who sends strings to translators:** to agree.

**Decision:** as proposed: smaller iOS commits, one feature each where possible, each with a few lines on any
behaviour change, and changes to shared formats are flagged before they ship. The iOS developer sends new strings
to translators, for both apps.

### Not decisions, but worth knowing (iOS repo)
- **A real app database is committed** in `new data backup/` (`default.store`, `-shm`, `-wal`, since `6f46e7b`). If it holds
  personal financial data, remove it from the repo and its history.
- **The sound after saving** is loaded from a private system path, which App Review may flag.
- **The share extension** opens the app by walking the responder chain. Apple doesn't support that, so it could
  stop working in a future iOS.
- **Android housekeeping, done in part 1:** comments in `ReceiptRulesParser.kt` and its test said iOS skips totals on
  lines with "Hrvatska", which iOS fixed in `6f46e7b`; and ios-issues.md said Android avoids every listed issue,
  though it copies some to match.

---

## 4. No decision needed: done in part 1
On branch `sync-ios-8a76b5d-part1`:
- CSV for all data
- Editing receipt items
- Budget alerts once a month
- Quick actions (Add transaction, Scan)
- Polish
- The new app icon
- The new tests from `ledgerTests/` as Android tests
- The demo data as test input
