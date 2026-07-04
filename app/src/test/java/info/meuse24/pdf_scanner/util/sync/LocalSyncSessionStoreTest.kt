package info.meuse24.pdf_scanner.util.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSyncSessionStoreTest {

    @Test
    fun `created session is valid immediately`() {
        val store = LocalSyncSessionStore(sessionTimeoutMillis = 1000)
        val id = store.createSession(nowMillis = 0)

        assertTrue(store.touch(id, nowMillis = 0))
    }

    @Test
    fun `unknown session id is rejected`() {
        val store = LocalSyncSessionStore(sessionTimeoutMillis = 1000)
        store.createSession(nowMillis = 0)

        assertFalse(store.touch("does-not-exist", nowMillis = 0))
    }

    @Test
    fun `session expires after the inactivity timeout`() {
        val store = LocalSyncSessionStore(sessionTimeoutMillis = 1000)
        val id = store.createSession(nowMillis = 0)

        assertFalse(store.touch(id, nowMillis = 1001))
        assertFalse(store.touch(id, nowMillis = 1001))
    }

    @Test
    fun `touching resets the inactivity window`() {
        val store = LocalSyncSessionStore(sessionTimeoutMillis = 1000)
        val id = store.createSession(nowMillis = 0)

        assertTrue(store.touch(id, nowMillis = 900))
        assertTrue(store.touch(id, nowMillis = 1800))
    }

    @Test
    fun `invalidateAll clears every session`() {
        val store = LocalSyncSessionStore(sessionTimeoutMillis = 1000)
        val id = store.createSession(nowMillis = 0)
        store.invalidateAll()

        assertFalse(store.touch(id, nowMillis = 0))
    }

    @Test
    fun `session ids are random and unique`() {
        val store = LocalSyncSessionStore(sessionTimeoutMillis = 1000)
        val first = store.createSession(nowMillis = 0)
        val second = store.createSession(nowMillis = 0)

        assertNotEquals(first, second)
    }
}
