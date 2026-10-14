# Ledger for Android

The Android version of Ledger, a local-only budgeting app; the iOS app (SwiftUI, in the
`spyglee/budgetTracker` repository) is the reference for features and behaviour. Android is up to date
with iOS commit `6f46e7b`. Kotlin, Jetpack Compose, Room, CameraX and ML Kit; minSdk 26.

Nothing leaves the device: the app has no INTERNET permission, receipts are read on the device, and
backups are turned off. CSV files are compatible with the iOS app in both directions.

## Build and test
Open in Android Studio, or:

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest            # JVM tests: domain logic, parsers, charts maths
./gradlew connectedDebugAndroidTest    # on a device or emulator: database, UI flows, real OCR
```

Releasing, signing and the Play Data safety answers: [docs/RELEASE.md](docs/RELEASE.md).

## Layout
- `data/` Room entities and DAOs, repositories, CSV file access
- `domain/` plain Kotlin business rules ported from iOS (balances, statistics, CSV, receipt parsing)
- `ui/` Compose screens; `scan/` camera, OCR and Gemini Nano; `lock/` app lock; `reminder/` daily reminder

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
