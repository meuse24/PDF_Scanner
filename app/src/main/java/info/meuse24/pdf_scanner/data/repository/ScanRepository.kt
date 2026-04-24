package info.meuse24.pdf_scanner.data.repository

import info.meuse24.pdf_scanner.data.local.ScanDao
import info.meuse24.pdf_scanner.data.mapper.toDomain
import info.meuse24.pdf_scanner.data.mapper.toEntity
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.util.toOcrPageTextJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRepository @Inject constructor(private val dao: ScanDao) : DocumentRepository {
    override fun getAllScans(): Flow<List<Document>> = dao.getAllScans().map { records ->
        records.map { it.toDomain() }
    }

    override fun getScansInFolder(folderId: Long): Flow<List<Document>> = dao.getScansInFolder(folderId).map { records ->
        records.map { it.toDomain() }
    }

    override fun getFavoriteScans(): Flow<List<Document>> = dao.getFavoriteScans().map { records ->
        records.map { it.toDomain() }
    }

    override fun searchScansFlow(query: String): Flow<List<Document>> = dao.searchScansFlow(query).map { records ->
        records.map { it.toDomain() }
    }

    override suspend fun saveScan(record: Document): Long = dao.insert(record.toEntity())

    override suspend fun saveScans(records: List<Document>) = dao.insertAll(records.map { it.toEntity() })

    override suspend fun deleteScan(record: Document) = dao.delete(record.toEntity())

    override suspend fun markSearchable(id: Long, fileSize: Long) = dao.markSearchable(id, fileSize)

    override suspend fun markSearchableWithContent(
        id: Long,
        fileSize: Long,
        text: String?,
        tags: String?,
        confidence: Float?,
        language: String?,
        pageTexts: List<String>
    ) = dao.markSearchableWithContent(id, fileSize, text, tags, confidence, language, pageTexts.toOcrPageTextJson())

    override suspend fun updateExtractedTextAndOcrStats(
        id: Long,
        text: String?,
        confidence: Float?,
        language: String?,
        pageTexts: List<String>
    ) = dao.updateExtractedTextAndOcrStats(id, text, confidence, language, pageTexts.toOcrPageTextJson())

    override suspend fun updateFileSize(id: Long, fileSize: Long) = dao.updateFileSize(id, fileSize)

    override suspend fun updatePageMetrics(id: Long, pageCount: Int, fileSize: Long) =
        dao.updatePageMetrics(id, pageCount, fileSize)

    override suspend fun invalidateAfterAppend(id: Long, fileSize: Long, pageCount: Int) =
        dao.invalidateAfterAppend(id, fileSize, pageCount)

    override suspend fun updateFilenameAndPath(id: Long, filename: String, filepath: String, thumbnailPath: String?) =
        dao.updateFilenameAndPath(id, filename, filepath, thumbnailPath)

    override suspend fun moveDocumentsToFolder(ids: List<Long>, folderId: Long?) = dao.moveScans(ids, folderId)

    override suspend fun setFavorite(ids: List<Long>, favorite: Boolean) = dao.setFavorite(ids, favorite)
}
