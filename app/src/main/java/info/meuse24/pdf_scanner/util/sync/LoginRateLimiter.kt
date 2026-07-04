package info.meuse24.pdf_scanner.util.sync

import java.util.concurrent.ConcurrentHashMap

sealed interface LoginAttemptResult {
    data object Allowed : LoginAttemptResult
    data class LockedOut(val retryAfterSeconds: Long) : LoginAttemptResult
    data object HardStopped : LoginAttemptResult
}

/**
 * Guards the PIN login endpoint against brute-forcing. The 4-digit PIN only has
 * 10,000 combinations, so per-client exponential backoff alone is not enough –
 * a hard stop across all clients bounds the total number of guesses during a
 * single server run (see plan1.md, "Sicherheitskonzept").
 */
class LoginRateLimiter(
    private val failuresBeforeLockout: Int = 5,
    private val baseLockoutSeconds: Long = 30L,
    private val hardStopAfterTotalFailures: Int = 20
) {
    private data class ClientState(var failures: Int = 0, var lockedUntilMillis: Long = 0L)

    private val clients = ConcurrentHashMap<String, ClientState>()
    private var totalFailures = 0
    private var hardStopped = false

    @Synchronized
    fun checkAllowed(clientId: String, nowMillis: Long): LoginAttemptResult {
        if (hardStopped) return LoginAttemptResult.HardStopped
        val state = clients[clientId] ?: return LoginAttemptResult.Allowed
        val remainingMillis = state.lockedUntilMillis - nowMillis
        return if (remainingMillis > 0) {
            LoginAttemptResult.LockedOut(millisToSecondsRoundedUp(remainingMillis))
        } else {
            LoginAttemptResult.Allowed
        }
    }

    @Synchronized
    fun recordFailure(clientId: String, nowMillis: Long): LoginAttemptResult {
        if (hardStopped) return LoginAttemptResult.HardStopped
        val state = clients.getOrPut(clientId) { ClientState() }
        state.failures += 1
        totalFailures += 1
        if (totalFailures >= hardStopAfterTotalFailures) {
            hardStopped = true
            return LoginAttemptResult.HardStopped
        }
        if (state.failures >= failuresBeforeLockout) {
            val extraSteps = (state.failures - failuresBeforeLockout).coerceAtMost(6)
            val lockoutSeconds = baseLockoutSeconds shl extraSteps
            state.lockedUntilMillis = nowMillis + lockoutSeconds * 1000
            return LoginAttemptResult.LockedOut(lockoutSeconds)
        }
        return LoginAttemptResult.Allowed
    }

    @Synchronized
    fun recordSuccess(clientId: String) {
        clients.remove(clientId)
    }

    @Synchronized
    fun isHardStopped(): Boolean = hardStopped

    private fun millisToSecondsRoundedUp(millis: Long): Long = (millis + 999) / 1000
}
