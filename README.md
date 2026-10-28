# Ledger for Android

The Android version of Ledger, a local-only budgeting app; the iOS app (SwiftUI, in the
`spyglee-the-raccoon/ledgerIos` repository) is the reference for features and behaviour. Android is up to date
with iOS commit `46702e5`. Kotlin, Jetpack Compose, Room, CameraX and ML Kit; minSdk 26.

Nothing leaves the device: the app has no INTERNET permission, receipts are read on the device, and
backups are turned off. CSV files are compatible with the iOS app in both directions. Like iOS, the export
also writes `accounts.csv` and `planned_transactions.csv`, which neither app can import; budgets and receipts
aren't exported, as on iOS. Scanned receipt pages are kept as JPEGs in the app's `files/receipts` folder.

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
adb shell pm grant work.spyglee.ladger android.permission.POST_NOTIFICATIONS
adb shell am instrument -w work.spyglee.ladger.test/androidx.test.runner.AndroidJUnitRunner
```

Releasing, signing and the Play Data safety answers: [docs/RELEASE.md](docs/RELEASE.md).

## Layout
- `data/` Room entities and DAOs, repositories, CSV file access
- `domain/` plain Kotlin business rules ported from iOS (balances, statistics, CSV, receipt parsing)
- `ui/` Compose screens; `scan/` camera, OCR and Gemini Nano; `lock/` app lock; `reminder/` daily reminder,
  budget alerts and the alarm that records planned transactions when they fall due

## Tools
- `tools/sync-ios-strings.ps1`: regenerates `strings.xml` (en/hr/uk) from the iOS string catalog.
- `tools/make-icons.py`: regenerates the launcher and Play icons from the iOS app icon (needs Pillow).
- `tools/make-ka-ching.py`: regenerates the sound played after adding a transaction.

## Docs
- [docs/ios-issues.md](docs/ios-issues.md): problems found in the iOS app during the port.
- [docs/translations-to-review.md](docs/translations-to-review.md): Android-only strings to check with native speakers.

## Debugging receipts
In debug builds the recognized receipt text is logged (`adb logcat -s TransactionEditor`) and the last
photo is kept: `adb exec-out run-as work.spyglee.ladger cat cache/last-receipt.jpg > last-receipt.jpg`.
`adb shell am start -S -n work.spyglee.ladger/.MainActivity --ez show_reminder true` shows the reminder.
