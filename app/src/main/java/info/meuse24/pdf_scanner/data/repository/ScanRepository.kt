package info.meuse24.pdf_scanner.data.repository

import info.meuse24.pdf_scanner.data.local.ScanDao
import info.meuse24.pdf_scanner.data.local.ScanRecord
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRepository @Inject constructor(private val dao: ScanDao) {
    fun getAllScans(): Flow<List<ScanRecord>> = dao.getAllScans()
    suspend fun saveScan(record: ScanRecord) = dao.insert(record)
    suspend fun deleteScan(record: ScanRecord) = dao.delete(record)
}
