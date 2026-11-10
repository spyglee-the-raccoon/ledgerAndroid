package work.spyglee.ladger.lock

import android.os.SystemClock
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
 *
 * [clock] is milliseconds since boot, replaceable in tests.
 */
class AppLock(private val clock: () -> Long = SystemClock::elapsedRealtime) {
    private val locked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = locked.asStateFlow()

    /** The authentication in progress, if any. */
    var pending: AuthPurpose? = null
        private set

    val isAuthenticating: Boolean get() = pending != null

    private var promptedSinceForeground = false

    /** When [expectReturn] was called, until the app goes to the background or comes back. */
    private var ownPageOpenedAt: Long? = null

    /** When the app went to the background for one of its own pages, until it comes back. */
    private var leftForOwnPageAt: Long? = null

    /**
     * The app is about to open one of its own outside pages: a file picker, or a system settings page
     * for the app. Coming back from it doesn't ask to unlock again (decision D5); on iOS such pickers
     * are part of the app. Android can't tell whether the user went somewhere else from there, so a
     * return after more than [OWN_PAGE_LIMIT_MS] locks anyway.
     */
    fun expectReturn() {
        ownPageOpenedAt = clock()
    }

    /**
     * The app went to the background. Entering a PIN in the system's credential screen also sends
     * the app to the background, so nothing changes while authenticating.
     */
    fun onBackground(appLockOn: Boolean) {
        if (isAuthenticating) return
        promptedSinceForeground = false
        val openedAt = ownPageOpenedAt
        ownPageOpenedAt = null
        // Only a page opened just now counts, in case the page didn't send the app to the background.
        if (openedAt != null && clock() - openedAt <= LEAVE_WINDOW_MS) {
            leftForOwnPageAt = clock()
            return
        }
        if (appLockOn) locked.value = true
    }

    /** The app is visible again: after one of its own pages, it locks only if the user was away too long. */
    fun onForeground(appLockOn: Boolean) {
        ownPageOpenedAt = null
        val leftAt = leftForOwnPageAt ?: return
        leftForOwnPageAt = null
        if (appLockOn && clock() - leftAt > OWN_PAGE_LIMIT_MS) locked.value = true
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

    companion object {
        /** How long the user may stay on one of the app's own pages before coming back locks the app. */
        const val OWN_PAGE_LIMIT_MS = 5 * 60 * 1000L

        /** How soon after [expectReturn] the app must go to the background for the trip to count. */
        const val LEAVE_WINDOW_MS = 10 * 1000L
    }
}
