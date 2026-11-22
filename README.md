# Ledger for Android

The Android version of Ledger, a local-only budgeting app; the iOS app (SwiftUI, in the
`spyglee-the-raccoon/ledgerIos` repository) is the reference for features and behaviour. Android is up to date
with iOS commit `46702e5`, plus the parts of `8a76b5d` that need no decision: CSV for all data, editing receipt
items, budget alerts once a month, the quick actions, Polish and the new icon; and, following the decisions in
[docs/decisions-plan.md](docs/decisions-plan.md), the three home screen widgets. The Pro subscription follows the
same plan; Siri has no Android equivalent for now (decision D3). Kotlin, Jetpack Compose, Room, CameraX and ML Kit; minSdk 26.

The app has no INTERNET permission and reads receipts on the device. Android's own backup is on (decision
D2): the database, receipt photos and settings go into the user's Google backup when the phone can encrypt it
end to end, and move to a new phone with Android's phone-to-phone transfer. CSV files are compatible with the iOS app in both directions: as on iOS, the export
writes transactions, accounts, planned transactions, categories, budgets, collections and receipt items, and
the import recognizes each file by its header. Receipt photos aren't exported, as on iOS. Scanned receipt pages
are kept as JPEGs in the app's `files/receipts` folder.

## Build and test
Open in Android Studio, or:

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest            # JVM tests: domain logic, parsers, charts maths
./gradlew connectedDebugAndroidTest    # on a device or emulator: database, UI flows, real OCR
```

With more than one device attached, pick one with `ANDROID_SERIAL` (e.g. `emulator-5554`):
`connectedDebugAndroidTest` installs on every device and **uninstalls the app afterwards, deleting its data**.
The planned transaction and budgeting flow tests need notifications allowed, which that uninstall resets. To
keep the app's data, install and run the tests directly:

```sh
./gradlew installDebug installDebugAndroidTest
adb shell pm grant work.spyglee.ledger android.permission.POST_NOTIFICATIONS
adb shell am instrument -w work.spyglee.ledger.test/androidx.test.runner.AndroidJUnitRunner
```

Releasing, signing and the Play Data safety answers: [docs/RELEASE.md](docs/RELEASE.md).

## Layout
- `data/` Room entities and DAOs, repositories, CSV file access
- `domain/` plain Kotlin business rules ported from iOS (balances, statistics, CSV, receipt parsing)
- `ui/` Compose screens; `scan/` camera, OCR and Gemini Nano; `lock/` app lock; `reminder/` daily reminder,
  budget alerts and the alarm that records planned transactions when they fall due

## Tools
- `tools/sync-ios-strings.ps1`: regenerates `strings.xml` (en/hr/uk/pl) from the iOS string catalog.
- `tools/make-icons.py`: regenerates the launcher and Play icons from the iOS app icon (needs Pillow).
- `tools/make-ka-ching.py`: regenerates the sound played after adding a transaction.

## Docs
- [docs/platform-decisions.md](docs/platform-decisions.md): how far Android is behind iOS, how the apps differ, and
  the decisions made together.
- [docs/decisions-plan.md](docs/decisions-plan.md): how Android implements those decisions, in order.
- [docs/ios-issues.md](docs/ios-issues.md): problems found in the iOS app during the port.
- [docs/translations-to-review.md](docs/translations-to-review.md): Android-only strings to check with native speakers.

## Debugging receipts
In debug builds the recognized receipt text is logged (`adb logcat -s TransactionEditor`) and the last
photo is kept: `adb exec-out run-as work.spyglee.ledger cat cache/last-receipt.jpg > last-receipt.jpg`.
`adb shell am start -S -n work.spyglee.ledger/work.spyglee.ladger.MainActivity --ez show_reminder true` shows the
reminder.
