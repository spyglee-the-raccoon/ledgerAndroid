package work.spyglee.ladger.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {
    private val lock = AppLock()

    private fun unlock() {
        lock.onAuthenticationStarted(AuthPurpose.Unlock)
        lock.onAuthenticationFinished(success = true)
    }

    @Test
    fun startsLockedAndPromptsOnFirstForegroundWhenOn() {
        assertTrue(lock.isLocked.value)
        assertTrue(lock.shouldPromptOnForeground(appLockOn = true))
        assertFalse(lock.shouldPromptOnForeground(appLockOn = false))
    }

    @Test
    fun successfulAuthenticationUnlocks() {
        unlock()
        assertFalse(lock.isLocked.value)
        assertFalse(lock.shouldPromptOnForeground(appLockOn = true))
    }

    @Test
    fun goingToTheBackgroundLocksOnlyWhenAppLockIsOn() {
        unlock()
        lock.onBackground(appLockOn = false)
        assertFalse(lock.isLocked.value)
        lock.onBackground(appLockOn = true)
        assertTrue(lock.isLocked.value)
        assertTrue(lock.shouldPromptOnForeground(appLockOn = true))
    }

    @Test
    fun cancellingThePromptDoesNotPromptAgainUntilTheNextReturn() {
        lock.onAuthenticationStarted(AuthPurpose.Unlock)
        assertFalse(lock.shouldPromptOnForeground(appLockOn = true))
        lock.onAuthenticationFinished(success = false)
        assertTrue(lock.isLocked.value)
        assertFalse(lock.shouldPromptOnForeground(appLockOn = true))

        lock.onBackground(appLockOn = true)
        assertTrue(lock.shouldPromptOnForeground(appLockOn = true))
    }

    @Test
    fun theCredentialScreenSendingTheAppToTheBackgroundDoesNotRelock() {
        unlock()
        lock.onAuthenticationStarted(AuthPurpose.SetAppLock(on = false))
        lock.onBackground(appLockOn = true)
        assertFalse(lock.isLocked.value)
        assertEquals(AuthPurpose.SetAppLock(on = false), lock.onAuthenticationFinished(success = true))
        assertFalse(lock.isAuthenticating)
    }

    @Test
    fun turningAppLockOnUnlocksSoTheAppDoesNotLockStraightAway() {
        lock.onAuthenticationStarted(AuthPurpose.SetAppLock(on = true))
        lock.onAuthenticationFinished(success = true)
        assertFalse(lock.shouldPromptOnForeground(appLockOn = true))
    }
}
