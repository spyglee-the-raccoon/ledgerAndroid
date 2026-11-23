import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// The app's public licence key from Play Console (Monetize with Play → Monetization setup), to check that
// purchases were signed by Google Play. Not a secret: it goes in gradle.properties (docs/RELEASE.md).
val playLicenseKey: String = providers.gradleProperty("ledger.playLicenseKey").getOrElse("")

// Release builds accept only signed purchases, so without the key nobody could unlock Pro: they don't build.
// The one exception is the first upload, which switches billing on in Play Console before the key is at hand:
// `-Pledger.allowMissingLicenseKey=true`.
if (playLicenseKey.isBlank() && !providers.gradleProperty("ledger.allowMissingLicenseKey").getOrElse("false").toBoolean()) {
    tasks.configureEach {
        if (name == "preReleaseBuild") {
            doFirst {
                throw GradleException(
                    "Set ledger.playLicenseKey in gradle.properties (docs/RELEASE.md), or build the first upload " +
                        "with -Pledger.allowMissingLicenseKey=true."
                )
            }
        }
    }
}

android {
    // The code keeps its "ladger" package; only the app ID, which users and Google Play see, matches iOS (D6).
    namespace = "work.spyglee.ladger"
    compileSdk = 37

    defaultConfig {
        // Can never change once the app is on Google Play.
        applicationId = "work.spyglee.ledger"
        minSdk = 26
        targetSdk = 36
        // The iOS marketing version of the commit Android is synced to (decision D7); the build number is Android's own.
        versionCode = 2
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "PLAY_LICENSE_KEY", "\"$playLicenseKey\"")
    }

    // Release signing comes from keystore.properties in the project root (not in git), with
    // storeFile, storePassword, keyAlias and keyPassword. Without it, release builds are signed with
    // the debug key so they can still be installed and tested locally; Play rejects those.
    val keystoreFile = rootProject.file("keystore.properties")
    val releaseSigning = if (keystoreFile.exists()) {
        val keystore = Properties().apply { keystoreFile.inputStream().use(::load) }
        signingConfigs.create("release") {
            storeFile = rootProject.file(keystore.getProperty("storeFile"))
            storePassword = keystore.getProperty("storePassword")
            keyAlias = keystore.getProperty("keyAlias")
            keyPassword = keystore.getProperty("keyPassword")
        }
    } else {
        signingConfigs.getByName("debug")
    }

    buildTypes {
        debug {
            // Debug builds sell Ledger Pro through a stand-in store until the app exists in Play Console;
            // `-Pledger.playBilling=true` uses Google Play instead (needs a license tester account).
            val playBilling = providers.gradleProperty("ledger.playBilling").getOrElse("false").toBoolean()
            buildConfigField("boolean", "PLAY_BILLING", "$playBilling")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = releaseSigning
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        // Lists the app's languages (values-hr, -uk, -pl, and English from res/resources.properties) so
        // Android 13+ lets users pick one for this app only in system settings, as iOS does (decision D8).
        generateLocaleConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // EXIF rotation of shared receipt images.
    implementation(libs.androidx.exifinterface)
    // Google Play Billing for Ledger Pro (decision D1).
    implementation(libs.billing.ktx)
    // Bundled model: OCR runs in the app's process, with nothing downloaded or sent anywhere.
    implementation(libs.mlkit.text.recognition)
    // Gemini Nano through the AICore system service; used only when already on the device.
    implementation(libs.mlkit.genai.prompt)
    // Home screen widgets (decision D4).
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
