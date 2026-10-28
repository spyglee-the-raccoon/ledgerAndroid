package work.spyglee.ladger

import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import work.spyglee.ladger.lock.AuthPurpose
import work.spyglee.ladger.ui.Authenticator
import work.spyglee.ladger.ui.LadgerNavHost
import work.spyglee.ladger.ui.LocalAppContainer
import work.spyglee.ladger.ui.LocalAuthenticator
import work.spyglee.ladger.ui.lock.LockScreen
import work.spyglee.ladger.ui.theme.LadgerTheme

/** A [FragmentActivity] because the system authentication prompt is shown as a fragment. */
class MainActivity : FragmentActivity() {
    private val container by lazy { (application as LadgerApp).container }
    private lateinit var biometricPrompt: BiometricPrompt

    /** Last known value of the app lock setting; false until the setting has loaded. */
    private var appLockOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Created here rather than when needed, so a prompt that is open during rotation still reports back.
        biometricPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), AuthenticationCallback())
        lifecycleScope.launch {
            container.settings.isAppLockOn.collect { on ->
                appLockOn = on
                // Keeps the app out of screenshots and hides it in Recents, like the iOS blur.
                if (on) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                // On a cold start the setting loads after onResume, so the first prompt is triggered here.
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) promptToUnlockIfNeeded()
            }
        }
        handleDebugIntent(intent)
        // Only on a fresh start: after rotation the editor for this receipt is already open.
        val sharedReceipt = if (savedInstanceState == null) sharedReceiptUri(intent) else null

        setContent {
            CompositionLocalProvider(
                LocalAppContainer provides container,
                LocalAuthenticator provides Authenticator(::authenticate),
            ) {
                LadgerTheme {
                    val lockOn by container.settings.isAppLockOn.collectAsStateWithLifecycle(initialValue = null)
                    val locked by container.appLock.isLocked.collectAsStateWithLifecycle()
                    when (lockOn) {
                        // Shows nothing until the setting has loaded, so locked content never flashes up.
                        null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
                        else -> {
                            val showLock = lockOn == true && locked
                            // The screens stay composed under the lock so navigation survives locking, but are hidden from accessibility services.
                            Box(if (showLock) Modifier.clearAndSetSemantics { } else Modifier) {
                                LadgerNavHost(sharedReceipt = sharedReceipt)
                            }
                            if (showLock) LockScreen(onUnlock = { authenticate(AuthPurpose.Unlock) })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDebugIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // Records planned transactions that fell due while the alarm couldn't and checks the budgets
        // (a new month, deleted expenses), as iOS does when the app opens.
        container.applicationScope.launch {
            container.plannedTransactions.recordDue()
            container.budgets.check()
        }
    }

    override fun onResume() {
        super.onResume()
        promptToUnlockIfNeeded()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) container.appLock.onBackground(appLockOn)
    }

    private fun promptToUnlockIfNeeded() {
        if (container.appLock.shouldPromptOnForeground(appLockOn)) authenticate(AuthPurpose.Unlock)
    }

    /**
     * Asks for a fingerprint, face or the device PIN/pattern/password, like iOS's device owner
     * authentication. Returns false when the device has no screen lock at all.
     */
    private fun authenticate(purpose: AuthPurpose): Boolean {
        val lock = container.appLock
        if (lock.isAuthenticating) return true
        return when (BiometricManager.from(this).canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                lock.onAuthenticationStarted(purpose)
                biometricPrompt.authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(getString(R.string.unlock))
                        .setSubtitle(getString(R.string.unlock_reason))
                        .setAllowedAuthenticators(AUTHENTICATORS)
                        .build()
                )
                true
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                // The screen lock was removed after app lock was turned on (which itself needs the
                // old PIN). iOS would stay locked for good; here the app opens, but only once the
                // keyguard confirms there is no secure lock screen at all, since then anyone holding
                // the device can already use it. Any other failure keeps the app locked.
                val deviceSecure = getSystemService(KeyguardManager::class.java)?.isDeviceSecure ?: true
                if (purpose == AuthPurpose.Unlock && !deviceSecure) lock.unlockWithoutCredential()
                false
            }
            else -> false
        }
    }

    private inner class AuthenticationCallback : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            val purpose = container.appLock.onAuthenticationFinished(success = true)
            if (purpose is AuthPurpose.SetAppLock) {
                container.applicationScope.launch { container.settings.setAppLockOn(purpose.on) }
            }
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            container.appLock.onAuthenticationFinished(success = false)
        }
    }

    /** The image or PDF of an ACTION_SEND share; MIME types are filtered in the manifest. */
    private fun sharedReceiptUri(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
    }

    /**
     * Debug builds only: `adb shell am start -S -n work.spyglee.ladger/.MainActivity --ez show_reminder true`.
     * Without `-S` a running app is just brought to the front and the extra never arrives.
     */
    private fun handleDebugIntent(intent: Intent?) {
        if (BuildConfig.DEBUG && intent?.getBooleanExtra(EXTRA_SHOW_REMINDER, false) == true) {
            container.applicationScope.launch { container.reminder.show(container.budgets.generalUsage()) }
        }
    }

    private companion object {
        /** Biometrics or the device credential. BIOMETRIC_WEAK is the only biometric class that combines with DEVICE_CREDENTIAL on Android 8-10. */
        const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        const val EXTRA_SHOW_REMINDER = "show_reminder"
    }
}
