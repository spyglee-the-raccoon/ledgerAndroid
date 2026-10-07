package work.spyglee.ladger.lock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Why the user is asked to authenticate. */
sealed interface AuthPurpose {
    /** Unlock the app after it was locked. */
    data object Unlock : AuthPurpose

    /** Turn app lock on or off in Settings; as on iOS, both directions need the device owner. */
    data class SetAppLock(val on: Boolean) : AuthPurpose
}

/**
 * Whether the app is locked, following iOS's ContentView: with app lock on, the app locks when it
 * goes to the background and asks to unlock when it comes back. Android asks once per return
 * rather than on every activation, so cancelling the prompt leaves the lock screen up instead of
 * asking again straight away.
 *
 * Lives as long as the process, so a new process always starts locked. The locked state only
 * matters while app lock is on.
 */
class AppLock {
    private val locked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = locked.asStateFlow()

    /** The authentication in progress, if any. */
    var pending: AuthPurpose? = null
        private set

    val isAuthenticating: Boolean get() = pending != null

    private var promptedSinceForeground = false

    /**
     * The app went to the background. Entering a PIN in the system's credential screen also sends
     * the app to the background, so nothing changes while authenticating.
     */
    fun onBackground(appLockOn: Boolean) {
        if (isAuthenticating) return
        promptedSinceForeground = false
        if (appLockOn) locked.value = true
    }

    /** Whether to show the unlock prompt by itself now: once each time the app returns while locked. */
    fun shouldPromptOnForeground(appLockOn: Boolean): Boolean =
        appLockOn && locked.value && !isAuthenticating && !promptedSinceForeground

    fun onAuthenticationStarted(purpose: AuthPurpose) {
        pending = purpose
        promptedSinceForeground = true
    }

    /** Returns what the finished authentication was for. Any successful authentication unlocks the app. */
    fun onAuthenticationFinished(success: Boolean): AuthPurpose? {
        val purpose = pending
        pending = null
        if (success) locked.value = false
        return purpose
    }

    /** The device has no screen lock any more, so there is nothing to check the user against. */
    fun unlockWithoutCredential() {
        locked.value = false
    }
}
