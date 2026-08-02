package info.meuse24.pdf_scanner.util.sync

/**
 * Records what keeps a PC-Sync run alive.
 *
 * Only *authorized* traffic counts. Wiring this into the routes instead of an
 * `ApplicationCallPipeline` interceptor is deliberate: an interceptor sits in front of
 * the authorization check, so stray LAN traffic (port scans, favicon requests, 404s)
 * would refresh the idle timer and could keep the server alive indefinitely.
 */
internal interface LocalSyncActivityTracker {

    /** A request from an authenticated client completed. */
    fun recordActivity()

    /** An authorized upload/download started; the run must not idle out during it. */
    fun beginTransfer()

    /** An authorized upload/download finished, successfully or not. */
    fun endTransfer()

    object NoOp : LocalSyncActivityTracker {
        override fun recordActivity() = Unit
        override fun beginTransfer() = Unit
        override fun endTransfer() = Unit
    }
}

/** Runs [block] while counted as an in-flight transfer, releasing the count on any outcome. */
internal suspend fun <T> LocalSyncActivityTracker.withTransfer(block: suspend () -> T): T {
    beginTransfer()
    return try {
        block()
    } finally {
        endTransfer()
    }
}
