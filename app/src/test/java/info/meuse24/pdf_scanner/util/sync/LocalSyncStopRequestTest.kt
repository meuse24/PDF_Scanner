package info.meuse24.pdf_scanner.util.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSyncStopRequestTest {

    @Test
    fun `a user-triggered stop applies to whatever run is active`() {
        assertTrue(shouldApplyStopRequest(USER_STOP_RUN_ID, currentRunId = 12345L))
    }

    @Test
    fun `an alarm scheduled for the current run applies`() {
        assertTrue(shouldApplyStopRequest(requestedRunId = 12345L, currentRunId = 12345L))
    }

    /** A pending alarm from an earlier run must not tear down a freshly started one. */
    @Test
    fun `a stale alarm from a previous run is ignored`() {
        assertFalse(shouldApplyStopRequest(requestedRunId = 12345L, currentRunId = 67890L))
    }

    @Test
    fun `an alarm is ignored when no run is active`() {
        assertFalse(shouldApplyStopRequest(requestedRunId = 12345L, currentRunId = USER_STOP_RUN_ID))
    }
}
