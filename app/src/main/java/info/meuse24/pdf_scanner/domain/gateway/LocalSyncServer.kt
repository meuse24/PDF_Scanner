package info.meuse24.pdf_scanner.domain.gateway

import info.meuse24.pdf_scanner.domain.model.LocalSyncActivity
import info.meuse24.pdf_scanner.domain.model.LocalSyncSession
import info.meuse24.pdf_scanner.domain.model.LocalSyncState
import kotlinx.coroutines.flow.StateFlow

interface LocalSyncServer {
    val state: StateFlow<LocalSyncState>

    suspend fun start(): LocalSyncSession

    suspend fun stop()

    /**
     * Consistent snapshot of idle time, runtime and in-flight authorized transfers,
     * or null if the server isn't running.
     */
    fun activitySnapshot(): LocalSyncActivity?
}
