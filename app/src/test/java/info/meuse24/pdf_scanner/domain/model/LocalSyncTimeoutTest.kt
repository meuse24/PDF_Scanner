package info.meuse24.pdf_scanner.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSyncTimeoutTest {

    @Test
    fun `default is twenty minutes`() {
        assertEquals(20, LocalSyncTimeout.DEFAULT_MINUTES)
    }

    @Test
    fun `selectable values are exactly the five offered options`() {
        assertEquals(listOf(5, 10, 20, 30, 60), LocalSyncTimeout.SELECTABLE_MINUTES)
    }

    @Test
    fun `normalize accepts every selectable value unchanged`() {
        LocalSyncTimeout.SELECTABLE_MINUTES.forEach { minutes ->
            assertEquals(minutes, LocalSyncTimeout.normalize(minutes))
        }
    }

    /**
     * The whole point of the closed set: no stored value may translate into an unbounded
     * or arbitrary timeout, whatever put it there.
     */
    @Test
    fun `normalize falls back to the default for any other value`() {
        listOf(0, -1, 1, 17, 45, 61, Int.MAX_VALUE, Int.MIN_VALUE).forEach { minutes ->
            assertEquals(LocalSyncTimeout.DEFAULT_MINUTES, LocalSyncTimeout.normalize(minutes))
        }
    }

    @Test
    fun `the runtime ceiling exceeds the longest selectable timeout`() {
        assertTrue(
            LocalSyncTimeout.MAX_SESSION_RUNTIME_MINUTES > LocalSyncTimeout.SELECTABLE_MINUTES.max()
        )
    }

    @Test
    fun `minutes convert to milliseconds`() {
        assertEquals(1_200_000L, LocalSyncTimeout.minutesToMillis(20))
        assertEquals(7_200_000L, LocalSyncTimeout.minutesToMillis(LocalSyncTimeout.MAX_SESSION_RUNTIME_MINUTES))
    }
}
