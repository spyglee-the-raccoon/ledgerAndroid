package work.spyglee.ladger.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {
    private var now = 1_000_000L
    private val lock = AppLock { now }

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

    @Test
    fun comingBackFromAnOwnPageDoesNotRelock() {
        unlock()
        lock.expectReturn()
        now += 1_000
        lock.onBackground(appLockOn = true)
        now += 60_000
        lock.onForeground(appLockOn = true)
        assertFalse(lock.isLocked.value)
        assertFalse(lock.shouldPromptOnForeground(appLockOn = true))
    }

    @Test
    fun comingBackFromAnOwnPageAfterTheLimitLocks() {
        unlock()
        lock.expectReturn()
        lock.onBackground(appLockOn = true)
        now += AppLock.OWN_PAGE_LIMIT_MS + 1
        lock.onForeground(appLockOn = true)
        assertTrue(lock.isLocked.value)
        assertTrue(lock.shouldPromptOnForeground(appLockOn = true))
    }

    @Test
    fun anOwnPageCoversOnlyOneTrip() {
        unlock()
        lock.expectReturn()
        lock.onBackground(appLockOn = true)
        lock.onForeground(appLockOn = true)
        lock.onBackground(appLockOn = true)
        assertTrue(lock.isLocked.value)
    }

    @Test
    fun anOwnPageThatNeverSentTheAppAwayDoesNotCoverALaterTrip() {
        unlock()
        lock.expectReturn()
        now += AppLock.LEAVE_WINDOW_MS + 1
        lock.onBackground(appLockOn = true)
        assertTrue(lock.isLocked.value)
    }

    @Test
    fun comingBackWithoutLeavingClearsTheExpectedReturn() {
        unlock()
        lock.expectReturn()
        lock.onForeground(appLockOn = true)
        lock.onBackground(appLockOn = true)
        assertTrue(lock.isLocked.value)
    }

    @Test
    fun theScreenTurningOffDuringAnOwnPageTripLocks() {
        unlock()
        lock.expectReturn()
        lock.onBackground(appLockOn = true)
        lock.onScreenOff(appLockOn = true)
        now += 10_000
        lock.onForeground(appLockOn = true)
        assertTrue(lock.isLocked.value)
        assertTrue(lock.shouldPromptOnForeground(appLockOn = true))
    }

    @Test
    fun theScreenTurningOffLocksOnlyWhenAppLockIsOn() {
        unlock()
        lock.onScreenOff(appLockOn = false)
        assertFalse(lock.isLocked.value)
    }
}
