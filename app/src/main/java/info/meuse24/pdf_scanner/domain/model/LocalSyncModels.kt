package info.meuse24.pdf_scanner.domain.model

data class LocalSyncSession(
    val ipAddress: String,
    val port: Int,
    val pin: String,
    val startedAt: Long
) {
    val url: String get() = "http://$ipAddress:$port"
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
