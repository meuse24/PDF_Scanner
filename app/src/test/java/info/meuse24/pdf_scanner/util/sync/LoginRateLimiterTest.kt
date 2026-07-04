package info.meuse24.pdf_scanner.util.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginRateLimiterTest {

    @Test
    fun `allows attempts below the failure threshold`() {
        val limiter = LoginRateLimiter()
        repeat(4) {
            val result = limiter.recordFailure("1.2.3.4", nowMillis = 0)
            assertTrue(result is LoginAttemptResult.Allowed)
        }
        assertTrue(limiter.checkAllowed("1.2.3.4", nowMillis = 0) is LoginAttemptResult.Allowed)
    }

    @Test
    fun `locks out client after threshold with exponential backoff`() {
        val limiter = LoginRateLimiter(failuresBeforeLockout = 5, baseLockoutSeconds = 30, hardStopAfterTotalFailures = 1000)
        repeat(4) { limiter.recordFailure("1.2.3.4", nowMillis = 0) }

        val fifth = limiter.recordFailure("1.2.3.4", nowMillis = 0)
        assertEquals(LoginAttemptResult.LockedOut(30), fifth)

        val sixth = limiter.recordFailure("1.2.3.4", nowMillis = 30_000)
        assertEquals(LoginAttemptResult.LockedOut(60), sixth)

        val seventh = limiter.recordFailure("1.2.3.4", nowMillis = 90_000)
        assertEquals(LoginAttemptResult.LockedOut(120), seventh)
    }

    @Test
    fun `checkAllowed reports remaining lockout and clears after it elapses`() {
        val limiter = LoginRateLimiter(failuresBeforeLockout = 5, baseLockoutSeconds = 30, hardStopAfterTotalFailures = 1000)
        repeat(5) { limiter.recordFailure("1.2.3.4", nowMillis = 0) }

        assertEquals(LoginAttemptResult.LockedOut(30), limiter.checkAllowed("1.2.3.4", nowMillis = 0))
        assertEquals(LoginAttemptResult.LockedOut(1), limiter.checkAllowed("1.2.3.4", nowMillis = 29_500))
        assertTrue(limiter.checkAllowed("1.2.3.4", nowMillis = 30_000) is LoginAttemptResult.Allowed)
    }

    @Test
    fun `different clients are tracked independently`() {
        val limiter = LoginRateLimiter(failuresBeforeLockout = 2, baseLockoutSeconds = 30, hardStopAfterTotalFailures = 1000)
        limiter.recordFailure("1.1.1.1", nowMillis = 0)
        limiter.recordFailure("1.1.1.1", nowMillis = 0)

        assertTrue(limiter.checkAllowed("1.1.1.1", nowMillis = 0) is LoginAttemptResult.LockedOut)
        assertTrue(limiter.checkAllowed("2.2.2.2", nowMillis = 0) is LoginAttemptResult.Allowed)
    }

    @Test
    fun `success resets the client's failure count`() {
        val limiter = LoginRateLimiter(failuresBeforeLockout = 3, baseLockoutSeconds = 30, hardStopAfterTotalFailures = 1000)
        limiter.recordFailure("1.1.1.1", nowMillis = 0)
        limiter.recordFailure("1.1.1.1", nowMillis = 0)
        limiter.recordSuccess("1.1.1.1")
        limiter.recordFailure("1.1.1.1", nowMillis = 0)

        assertTrue(limiter.checkAllowed("1.1.1.1", nowMillis = 0) is LoginAttemptResult.Allowed)
    }

    @Test
    fun `hard-stops the whole limiter once total failures across all clients hit the cap`() {
        val limiter = LoginRateLimiter(failuresBeforeLockout = 100, baseLockoutSeconds = 30, hardStopAfterTotalFailures = 3)
        limiter.recordFailure("1.1.1.1", nowMillis = 0)
        limiter.recordFailure("2.2.2.2", nowMillis = 0)
        val third = limiter.recordFailure("3.3.3.3", nowMillis = 0)

        assertEquals(LoginAttemptResult.HardStopped, third)
        assertTrue(limiter.isHardStopped())
        assertEquals(LoginAttemptResult.HardStopped, limiter.checkAllowed("4.4.4.4", nowMillis = 0))
    }
}
