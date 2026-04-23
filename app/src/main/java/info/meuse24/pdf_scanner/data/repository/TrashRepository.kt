package info.meuse24.pdf_scanner.data.repository

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.local.TrashDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrashRepository @Inject constructor(
    private val dao: TrashDao
) {
    fun getTrashedScans(): Flow<List<ScanRecord>> = dao.getTrashedScans()
    suspend fun getScansByIds(ids: List<Long>): List<ScanRecord> = dao.getScansByIds(ids)
    suspend fun softDelete(ids: List<Long>, timestamp: Long) = dao.softDelete(ids, timestamp)
    suspend fun restore(ids: List<Long>) = dao.restore(ids)
    suspend fun findExpiredTrash(threshold: Long): List<ScanRecord> = dao.findExpiredTrash(threshold)
}
