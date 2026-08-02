package info.meuse24.pdf_scanner.domain.model

/**
 * Consistent snapshot of a running PC-Sync server's activity.
 *
 * Read as one value rather than through separate getters so idle time, runtime and
 * the in-flight transfer count always describe the same instant — three independently
 * read atomics would not be a consistent snapshot.
 *
 * @param idleMillis time since the last authorized request completed
 * @param runtimeMillis time since the server started
 * @param activeRequests authorized uploads/downloads currently in flight
 */
data class LocalSyncActivity(
    val idleMillis: Long,
    val runtimeMillis: Long,
    val activeRequests: Int
)
