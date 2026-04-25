package info.meuse24.pdf_scanner.domain.repository

import info.meuse24.pdf_scanner.domain.model.Document
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface DocumentRepository {
    fun getAllScans(): Flow<List<Document>>
    fun getScansInFolder(folderId: Long): Flow<List<Document>>
    fun getFavoriteScans(): Flow<List<Document>>
    fun searchScansFlow(query: String): Flow<List<Document>>
    fun getAllScansForList(): Flow<List<Document>> = getAllScans()
    fun getScansInFolderForList(folderId: Long): Flow<List<Document>> = getScansInFolder(folderId)
    fun getFavoriteScansForList(): Flow<List<Document>> = getFavoriteScans()
    fun searchScansForList(query: String): Flow<List<Document>> = searchScansFlow(query)
    fun searchScansInFolderForList(query: String, folderId: Long): Flow<List<Document>> =
        searchScansForList(query).map { documents -> documents.filter { it.folderId == folderId } }

    fun searchFavoriteScansForList(query: String): Flow<List<Document>> =
        searchScansForList(query).map { documents -> documents.filter { it.isFavorite } }

    suspend fun saveScan(record: Document): Long
    suspend fun saveScans(records: List<Document>)
    suspend fun deleteScan(record: Document)
    suspend fun markSearchable(id: Long, fileSize: Long)
    suspend fun markSearchableWithContent(
        id: Long,
        fileSize: Long,
        text: String?,
        tags: String?,
        confidence: Float?,
        language: String?,
        pageTexts: List<String>
    )

    suspend fun updateExtractedTextAndOcrStats(
        id: Long,
        text: String?,
        confidence: Float?,
        language: String?,
        pageTexts: List<String>
    )

    suspend fun updateFileSize(id: Long, fileSize: Long)
    suspend fun updatePageMetrics(id: Long, pageCount: Int, fileSize: Long)
    suspend fun invalidateAfterAppend(id: Long, fileSize: Long, pageCount: Int)
    suspend fun updateFilenameAndPath(id: Long, filename: String, filepath: String, thumbnailPath: String?)
    suspend fun moveDocumentsToFolder(ids: List<Long>, folderId: Long?)
    suspend fun setFavorite(ids: List<Long>, favorite: Boolean)
}
