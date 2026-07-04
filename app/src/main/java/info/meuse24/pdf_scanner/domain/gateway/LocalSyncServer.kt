package info.meuse24.pdf_scanner.domain.gateway

import info.meuse24.pdf_scanner.domain.model.LocalSyncSession
import info.meuse24.pdf_scanner.domain.model.LocalSyncState
import kotlinx.coroutines.flow.StateFlow

interface LocalSyncServer {
    val state: StateFlow<LocalSyncState>

    suspend fun start(): LocalSyncSession

    suspend fun stop()

    /** Milliseconds since the last HTTP request, or null if the server isn't running. */
    fun millisSinceLastActivity(): Long?
}
