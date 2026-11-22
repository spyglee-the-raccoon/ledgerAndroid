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
- Data stays with the user: transactions in the app's database, settings in DataStore. Since decision D2,
  Android's backup is on (`res/xml/data_extraction_rules.xml`, `backup_rules.xml`):
  - The database, receipt photos and settings go into the user's Google backup. That only happens when the
    phone can encrypt the backup end to end with its screen lock (Android 9+; Android 8 has no backup).
  - They also move to a new phone with Android's phone-to-phone transfer.
  - The data only goes to the user's own Google account or phone, never to us, so we answer that it isn't
    collected. **Check before submitting** that Play's Data safety help agrees about Android backup.
- Receipt photos and PDFs are read on the device (ML Kit's bundled text recognizer, and Gemini Nano in
  Android's AICore service where available). Since iOS `46702e5` a photo taken with "Scan your receipt" is
  kept with its transaction (a 1600 px JPEG in the app's private `files/receipts`, backed up with the
  database), with the products read from it; nothing is sent to us, so it still isn't "collected".
  Since iOS `1748a53` a shared image or PDF is kept the same way.
- Ledger Pro (decision D1) is bought through Google Play. The app only learns from the Play Store app whether
  the user has an active subscription, keeps that on the phone, and sends nothing anywhere: the app still has no
  INTERNET permission. **Check before submitting** how Play's Data safety help treats purchase history read
  through Play Billing; nothing about it leaves the phone, so we expect "not collected".

## 5. Permissions (for review questions)
| Permission | Why |
|---|---|
| `CAMERA` | Photographing receipts; requested when "Scan your receipt" is tapped. |
| `POST_NOTIFICATIONS` | The optional daily reminder at 20:30, "planned transaction performed" and "budget limit reached"; requested when the reminder is turned on or a plan or budget is saved. |
| `RECEIVE_BOOT_COMPLETED` | Scheduling the reminder again after a restart. |
| `SCHEDULE_EXACT_ALARM` | Recording planned transactions and showing the daily reminder at the time the user set (decision D10). From Android 14 the user allows it in system settings; without it the alarms stay inexact. Not `USE_EXACT_ALARM`, which Play keeps for alarm-clock and calendar apps. **Before release:** check the Play Console rules for exact-alarm permissions (Play Console help, answer 12253906). |
| `USE_BIOMETRIC` | The optional app lock. |
| `com.android.vending.BILLING` | Ledger Pro, bought through Google Play (decision D1); added by Play Billing. |
| `BIND_SERVICE` (AICore) | Talking to Gemini Nano on the device, if the phone has it. |

## 6. Store listing assets
- Icon: `app/src/main/ic_launcher-playstore.png` (512 × 512), generated with the launcher icons by
  `tools/make-icons.py` from the iOS icon.
- Screenshots and feature graphic: still to make.

## 7. Ledger Pro (Google Play Billing)
The subscriptions and their prices live in Play Console (`work.spyglee.ledger.pro.monthly` and `.yearly`, the iOS
product IDs). Play Console only offers subscriptions after a build that contains Play Billing has been uploaded.

- **Licence key.** Play Console → Monetize with Play → Monetization setup → Licensing has the app's public key.
  Put it in `gradle.properties` as `ledger.playLicenseKey=MIIB...`. It isn't secret. With it, the app accepts
  only purchases signed by Google Play. Without it, purchases are taken as Play reports them, so set it before
  the first release.
- **Debug builds** sell Pro through a stand-in store (`FakeStore`), with the same products and trial. Buying
  succeeds at once and "Manage subscription" cancels. That's for trying the paywall without Play.
- **Testing with Google Play.** Add the tester's Google account under Play Console → Settings → License testing.
  Install a debug build made with `./gradlew installDebug -Pledger.playBilling=true` on a phone signed in with
  that account. License testers can use sideloaded debug builds, and their purchases are free. Test renewals are
  fast: a trial lasts 3 minutes, a month 5 minutes and a year 30, and a subscription ends after six renewals.
- **Before each release**, check the APK's permissions (`aapt dump permissions`). `INTERNET` and
  `ACCESS_NETWORK_STATE` must stay absent. Play Billing's usage logging asks for them, and the manifest removes
  them as for ML Kit.
