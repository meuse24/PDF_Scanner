package info.meuse24.pdf_scanner.domain.repository

import info.meuse24.pdf_scanner.domain.model.Document
import kotlinx.coroutines.flow.Flow

interface TrashDocumentRepository {
    fun getTrashedScans(): Flow<List<Document>>
    suspend fun getScansByIds(ids: List<Long>): List<Document>
    suspend fun softDelete(ids: List<Long>, timestamp: Long)
    suspend fun restore(ids: List<Long>)
    suspend fun findExpiredTrash(threshold: Long): List<Document>
}
