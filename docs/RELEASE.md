# Releasing Ledger for Android

## 1. Signing key (once)
Google Play signs the app with its own key (Play App Signing); you sign uploads with an **upload key**.
If the upload key is lost it can be reset through Play Console support, but keep it and its passwords
somewhere safe (a password manager) and never commit them.

```sh
keytool -genkeypair -v -keystore ladger-upload.jks -alias ladger-upload -keyalg RSA -keysize 4096 -validity 10000
```

Put the `.jks` file outside the repository or in the project root (`*.jks` is git-ignored), then create
`keystore.properties` in the project root (also git-ignored):

```properties
storeFile=ladger-upload.jks
storePassword=...
keyAlias=ladger-upload
keyPassword=...
```

Without `keystore.properties`, release builds are signed with the debug key: fine for testing on your
own devices, rejected by Google Play.

## 2. Version
`app/build.gradle.kts`: `versionName` follows the iOS app's marketing version (currently 0.1),
`versionCode` must go up by one for every upload.

## 3. Build and test
```sh
./gradlew testDebugUnitTest connectedDebugAndroidTest   # with a device or emulator connected
./gradlew bundleRelease                                 # app/build/outputs/bundle/release/app-release.aab
./gradlew assembleRelease                               # an installable APK for a last check on a phone
```
Always try the **release** APK on a phone before uploading: R8 shrinking only happens there. In
particular scan or share a receipt, because ML Kit needs the keep rules in `app/proguard-rules.pro`
(without them every scan fails in release builds only).

Upload the `.aab`: Play delivers only the files a phone needs, about 16 MB for a typical phone (the
universal APK is about 47 MB because it carries the OCR libraries for all four processor types).

## 4. Play Console: Data safety
The app is local-only and has **no INTERNET permission** (libraries that request it have it removed in
`AndroidManifest.xml`; check with `aapt dump permissions` on the APK before each release). So:

- **Does your app collect or share any of the required user data types?** No.
- Data stays on the device: transactions in the app's database, settings in DataStore. Android backup
  is turned off (`allowBackup="false"`, data extraction rules exclude everything). This includes
  phone-to-phone transfer, so moving to a new phone means Settings → Export to CSV, then Import.
- Receipt photos and PDFs are read on the device (ML Kit's bundled text recognizer, and Gemini Nano in
  Android's AICore service where available) and are not stored.

## 5. Permissions (for review questions)
| Permission | Why |
|---|---|
| `CAMERA` | Photographing receipts; requested when "Scan your receipt" is tapped. |
| `POST_NOTIFICATIONS` | The optional daily reminder at 20:30; requested when it's turned on. |
| `RECEIVE_BOOT_COMPLETED` | Scheduling the reminder again after a restart. |
| `USE_BIOMETRIC` | The optional app lock. |
| `BIND_SERVICE` (AICore) | Talking to Gemini Nano on the device, if the phone has it. |

## 6. Store listing assets
- Icon: `app/src/main/ic_launcher-playstore.png` (512 × 512), generated with the launcher icons by
  `tools/make-icons.py` from the iOS icon.
- Screenshots and feature graphic: still to make.
