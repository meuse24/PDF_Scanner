package info.meuse24.pdf_scanner.data.repository

import info.meuse24.pdf_scanner.data.local.ScanDao
import info.meuse24.pdf_scanner.data.local.ScanRecord
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRepository @Inject constructor(private val dao: ScanDao) {
    fun getAllScans(): Flow<List<ScanRecord>> = dao.getAllScans()
    fun searchScansFlow(query: String): Flow<List<ScanRecord>> = dao.searchScansFlow(query)
    suspend fun saveScan(record: ScanRecord): Long = dao.insert(record)
    suspend fun saveScans(records: List<ScanRecord>) = dao.insertAll(records)
    suspend fun deleteScan(record: ScanRecord) = dao.delete(record)
    suspend fun markSearchable(id: Long, fileSize: Long) = dao.markSearchable(id, fileSize)
    suspend fun markSearchableWithContent(
        id: Long,
        fileSize: Long,
        text: String?,
        tags: String?,
        confidence: Float?,
        language: String?,
        pageTextJson: String?
    ) = dao.markSearchableWithContent(id, fileSize, text, tags, confidence, language, pageTextJson)
    suspend fun updateExtractedTextAndOcrStats(
        id: Long,
        text: String?,
        confidence: Float?,
        language: String?,
        pageTextJson: String?
    ) = dao.updateExtractedTextAndOcrStats(id, text, confidence, language, pageTextJson)
    suspend fun updateFileSize(id: Long, fileSize: Long) = dao.updateFileSize(id, fileSize)
    suspend fun updatePageMetrics(id: Long, pageCount: Int, fileSize: Long) =
        dao.updatePageMetrics(id, pageCount, fileSize)
    suspend fun invalidateAfterAppend(id: Long, fileSize: Long, pageCount: Int) =
        dao.invalidateAfterAppend(id, fileSize, pageCount)
    suspend fun updateFilenameAndPath(id: Long, filename: String, filepath: String, thumbnailPath: String?) =
        dao.updateFilenameAndPath(id, filename, filepath, thumbnailPath)
}
