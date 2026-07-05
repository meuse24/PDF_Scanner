package info.meuse24.pdf_scanner.domain.model

data class LocalSyncSession(
    val ipAddress: String,
    val port: Int,
    val pin: String,
    val startedAt: Long
) {
    // Trailing slash on purpose: it gives the URL an explicit path so link parsers in chat
    // apps (WhatsApp etc.) claim the whole "http://ip:port/" as a single tappable link instead
    // of splitting off the ":port" as a separate number. The server serves "/" anyway.
    val url: String get() = "http://$ipAddress:$port/"
}

sealed interface LocalSyncState {
    data object Stopped : LocalSyncState
    data object Starting : LocalSyncState
    data class Running(val session: LocalSyncSession) : LocalSyncState
    data class Error(val reason: LocalSyncError) : LocalSyncState
}

enum class LocalSyncError {
    NO_LOCAL_NETWORK,
    PORT_UNAVAILABLE,
    HARD_STOPPED,
    UNKNOWN
}
