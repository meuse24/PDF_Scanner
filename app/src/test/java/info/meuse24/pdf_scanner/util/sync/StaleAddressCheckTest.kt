package info.meuse24.pdf_scanner.util.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression for the network-address race: the interface scan is slow enough that a user can
 * stop and restart PC Sync while it runs, after which its verdict belongs to a dead run.
 */
class StaleAddressCheckTest {

    private val checkedRun = BoundRun(generation = 7L, ipAddress = "192.168.0.81")

    @Test
    fun `a check for the still-active run is applied`() {
        assertFalse(isStaleAddressCheck(checkedRun, currentRun = checkedRun))
    }

    @Test
    fun `a check outlived by a restart on a different address is discarded`() {
        val restarted = BoundRun(generation = 8L, ipAddress = "192.168.0.99")

        assertTrue(isStaleAddressCheck(checkedRun, currentRun = restarted))
    }

    /** Same address, new run: the restart is legitimate and must survive. */
    @Test
    fun `a check outlived by a restart on the same address is discarded`() {
        val restarted = BoundRun(generation = 8L, ipAddress = checkedRun.ipAddress)

        assertTrue(isStaleAddressCheck(checkedRun, currentRun = restarted))
    }

    @Test
    fun `a check that outlived a plain stop is discarded`() {
        assertTrue(isStaleAddressCheck(checkedRun, currentRun = null))
    }
}
