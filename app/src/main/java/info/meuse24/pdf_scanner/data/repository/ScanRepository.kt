package info.meuse24.pdf_scanner.data.repository

import info.meuse24.pdf_scanner.data.local.ScanDao
import info.meuse24.pdf_scanner.data.local.ScanListDao
import info.meuse24.pdf_scanner.data.local.ScanListItem
import info.meuse24.pdf_scanner.data.mapper.toDomain
import info.meuse24.pdf_scanner.data.mapper.toEntity
import info.meuse24.pdf_scanner.data.mapper.toListItem
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.util.toOcrPageTextJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRepository @Inject constructor(
    private val dao: ScanDao,
    private val scanListDao: ScanListDao
) : DocumentRepository {
    constructor(dao: ScanDao) : this(dao, ScanRecordBackedScanListDao(dao))

    override fun getAllScans(): Flow<List<Document>> = dao.getAllScans().map { records ->
        records.map { it.toDomain() }
    }

    override fun getScansInFolder(folderId: Long): Flow<List<Document>> = dao.getScansInFolder(folderId).map { records ->
        records.map { it.toDomain() }
    }

    override fun getFavoriteScans(): Flow<List<Document>> = dao.getFavoriteScans().map { records ->
        records.map { it.toDomain() }
    }

    override fun getScansWithTagForList(tagKey: String): Flow<List<Document>> =
        scanListDao.getScanListItemsWithTag(tagKey).map { records ->
            records.map { it.toDomain() }
        }

    override fun searchScansFlow(query: String): Flow<List<Document>> = dao.searchScansFlow(query).map { records ->
        records.map { it.toDomain() }
    }

    override fun getAllScansForList(): Flow<List<Document>> = scanListDao.getAllScanListItems().map { records ->
        records.map { it.toDomain() }
    }

    override fun getScansInFolderForList(folderId: Long): Flow<List<Document>> =
        scanListDao.getScanListItemsInFolder(folderId).map { records ->
            records.map { it.toDomain() }
        }

    override fun getFavoriteScansForList(): Flow<List<Document>> =
        scanListDao.getFavoriteScanListItems().map { records ->
            records.map { it.toDomain() }
        }

    override fun searchScansForList(query: String): Flow<List<Document>> =
        scanListDao.searchScanListItems(query).map { records ->
            records.map { it.toDomain() }
        }

    override fun searchScansInFolderForList(query: String, folderId: Long): Flow<List<Document>> =
        scanListDao.searchScanListItemsInFolder(query, folderId).map { records ->
            records.map { it.toDomain() }
        }

    override fun searchFavoriteScansForList(query: String): Flow<List<Document>> =
        scanListDao.searchFavoriteScanListItems(query).map { records ->
            records.map { it.toDomain() }
        }

    override fun searchScansWithTagForList(query: String, tagKey: String): Flow<List<Document>> =
        searchScansForList(query).map { documents ->
            documents.filter { document -> document.tags.orEmpty().split(",").any { it.trim() == tagKey } }
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

    override suspend fun getAllSearchableWithoutTags(): List<Document> =
        dao.getAllSearchableWithoutTags().map { it.toDomain() }

    override suspend fun updateTags(id: Long, tags: String) = dao.updateTags(id, tags)
}

private class ScanRecordBackedScanListDao(
    private val dao: ScanDao
) : ScanListDao {
    override fun getAllScanListItems(): Flow<List<ScanListItem>> =
        dao.getAllScans().map { records -> records.map { it.toListItem() } }

    override fun getScanListItemsInFolder(folderId: Long): Flow<List<ScanListItem>> =
        dao.getScansInFolder(folderId).map { records -> records.map { it.toListItem() } }

    override fun getFavoriteScanListItems(): Flow<List<ScanListItem>> =
        dao.getFavoriteScans().map { records -> records.map { it.toListItem() } }

    override fun getScanListItemsWithTag(tagKey: String): Flow<List<ScanListItem>> =
        dao.getScansWithTag(tagKey).map { records -> records.map { it.toListItem() } }

    override fun searchScanListItems(query: String): Flow<List<ScanListItem>> =
        dao.searchScansFlow(query).map { records -> records.map { it.toListItem() } }

    override fun searchScanListItemsInFolder(query: String, folderId: Long): Flow<List<ScanListItem>> =
        dao.searchScansFlow(query).map { records ->
            records.filter { it.folderId == folderId }.map { it.toListItem() }
        }

    override fun searchFavoriteScanListItems(query: String): Flow<List<ScanListItem>> =
        dao.searchScansFlow(query).map { records ->
            records.filter { it.isFavorite }.map { it.toListItem() }
        }
}
