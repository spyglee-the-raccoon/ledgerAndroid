# iOS issues found while porting Ladger to Android

Found while porting the iOS app (commit `2b36197`) to Android, which is now feature-complete. Most
important first. Android avoids or fixes each of these; file references are to the iOS repo.

Checked again at iOS `6f46e7b`: issues 2–6 are fixed there; 1, 7 and 8 and the typo in 9 are still open.

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
matches names ignoring case, so an import may attach transactions to either one.

## 9. Text
- `unlock_reason` (English): "Unlock app to see the **blance** and manipulate transactions" — typo.
- `reminder_title` was "Budget Tracker" while the app is called Ladger. Since `6f46e7b` it is "Ledger", matching the
  iOS app's new display name; Android followed and is now called Ledger too.
- The app icon (`AppIcon.appiconset/Ladger Logo.png`) still has "Ladger" written under the logo, while the app is
  now called Ledger. Android's launcher and Play icons are generated from it, so they say "Ladger" too until the
  iOS icon changes (then rerun `tools/make-icons.py`).

## Optional: receipt OCR that reads O as zero
On a monospace receipt font, Android's OCR read "UKUPNO" as "UKUPN0". The Android parser now treats
a 0 touching letters as "o" when looking for keywords (amounts are untouched). Apple's recognizer
may not need it, but it's a cheap safeguard.
