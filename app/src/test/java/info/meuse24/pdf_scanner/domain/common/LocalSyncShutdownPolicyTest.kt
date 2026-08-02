package info.meuse24.pdf_scanner.domain.common

import info.meuse24.pdf_scanner.domain.model.LocalSyncActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalSyncShutdownPolicyTest {

    private val idleTimeout = 20 * 60_000L
    private val maxRuntime = 120 * 60_000L

    private fun activity(
        idleMillis: Long = 0L,
        runtimeMillis: Long = 0L,
        activeRequests: Int = 0
    ) = LocalSyncActivity(idleMillis, runtimeMillis, activeRequests)

    private fun evaluate(
        appLocked: Boolean = false,
        activity: LocalSyncActivity? = activity()
    ) = evaluateLocalSyncShutdown(appLocked, activity, idleTimeout, maxRuntime)

    @Test
    fun `keeps running while idle time is below the timeout`() {
        assertNull(evaluate(activity = activity(idleMillis = idleTimeout - 1)))
    }

    @Test
    fun `app lock outranks every other condition`() {
        val reason = evaluate(
            appLocked = true,
            activity = activity(idleMillis = idleTimeout, runtimeMillis = maxRuntime)
        )

        assertEquals(LocalSyncShutdownReason.AppLocked, reason)
    }

    @Test
    fun `app lock shuts down even when the server already stopped`() {
        assertEquals(LocalSyncShutdownReason.AppLocked, evaluate(appLocked = true, activity = null))
    }

    /**
     * Regression for D1: a null snapshot used to break the watchdog loop, leaving the
     * foreground service alive indefinitely. It must be a shutdown reason instead.
     */
    @Test
    fun `missing snapshot means the server stopped itself and the service must follow`() {
        assertEquals(LocalSyncShutdownReason.ServerNotRunning, evaluate(activity = null))
    }

    @Test
    fun `idle timeout triggers exactly at the boundary`() {
        assertEquals(
            LocalSyncShutdownReason.IdleTimeout,
            evaluate(activity = activity(idleMillis = idleTimeout))
        )
    }

    @Test
    fun `runtime ceiling triggers exactly at the boundary`() {
        assertEquals(
            LocalSyncShutdownReason.MaxRuntimeExceeded,
            evaluate(activity = activity(runtimeMillis = maxRuntime))
        )
    }

    @Test
    fun `an in-flight transfer defers the idle timeout`() {
        assertNull(evaluate(activity = activity(idleMillis = idleTimeout, activeRequests = 1)))
    }

    @Test
    fun `an in-flight transfer does not defer the runtime ceiling`() {
        assertEquals(
            LocalSyncShutdownReason.MaxRuntimeExceeded,
            evaluate(activity = activity(runtimeMillis = maxRuntime, activeRequests = 3))
        )
    }

    @Test
    fun `runtime ceiling wins over a simultaneously expired idle timeout`() {
        assertEquals(
            LocalSyncShutdownReason.MaxRuntimeExceeded,
            evaluate(activity = activity(idleMillis = idleTimeout, runtimeMillis = maxRuntime))
        )
    }

    @Test
    fun `a shorter configured timeout shuts down earlier`() {
        val reason = evaluateLocalSyncShutdown(
            appLocked = false,
            activity = activity(idleMillis = 5 * 60_000L),
            idleTimeoutMillis = 5 * 60_000L,
            maxRuntimeMillis = maxRuntime
        )

        assertEquals(LocalSyncShutdownReason.IdleTimeout, reason)
    }
}
