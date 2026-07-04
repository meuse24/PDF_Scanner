package info.meuse24.pdf_scanner.util.sync

import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

const val LOCAL_SYNC_SESSION_COOKIE_NAME = "m24_sync_session"
const val LOCAL_SYNC_SESSION_TIMEOUT_MILLIS = 15 * 60 * 1000L

/**
 * In-memory session store for the PC-Sync PIN login. A successful PIN entry
 * creates a random session id (returned to the browser as an HttpOnly cookie);
 * every authenticated request must present a still-valid id via [touch].
 */
class LocalSyncSessionStore(
    private val sessionTimeoutMillis: Long = LOCAL_SYNC_SESSION_TIMEOUT_MILLIS,
    private val random: SecureRandom = SecureRandom()
) {
    private val sessions = ConcurrentHashMap<String, Long>()

    fun createSession(nowMillis: Long): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        val id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        sessions[id] = nowMillis
        return id
    }

    fun touch(sessionId: String, nowMillis: Long): Boolean {
        val lastAccess = sessions[sessionId] ?: return false
        if (nowMillis - lastAccess > sessionTimeoutMillis) {
            sessions.remove(sessionId)
            return false
        }
        sessions[sessionId] = nowMillis
        return true
    }

    fun invalidateAll() {
        sessions.clear()
    }
}
