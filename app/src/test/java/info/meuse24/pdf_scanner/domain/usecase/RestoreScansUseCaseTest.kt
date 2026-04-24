package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.mapper.toEntity
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.data.local.TrashDao
import info.meuse24.pdf_scanner.domain.model.Folder
import info.meuse24.pdf_scanner.data.repository.TrashRepository
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule

class RestoreScansUseCaseTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `restores ids when files exist`() = runTest {
        val pdf = tmpFolder.newFile("scan.pdf")
        val dao = RestoreFakeTrashDao(records = listOf(scanRecord(1L, pdf.absolutePath)))
        val documentRepository = RestoreFakeDocumentRepository()
        val useCase = RestoreScansUseCase(
            TrashRepository(dao),
            documentRepository,
            RestoreFakeFolderRepository(emptySet())
        )

        useCase(listOf(1L))

        assertEquals(listOf(1L), dao.restoredIds)
        assertEquals(emptyList<Long>(), documentRepository.movedIds)
    }

    @Test
    fun `missing file fails before restore`() = runTest {
        val dao = RestoreFakeTrashDao(records = listOf(scanRecord(1L, "missing.pdf")))
        val useCase = RestoreScansUseCase(
            TrashRepository(dao),
            RestoreFakeDocumentRepository(),
            RestoreFakeFolderRepository(emptySet())
        )

        try {
            useCase(listOf(1L))
            fail("Expected RestoreMissingFileException")
        } catch (_: RestoreMissingFileException) {
        }
        assertEquals(emptyList<Long>(), dao.restoredIds)
    }

    @Test
    fun `restoring scan from deleted folder moves it to root`() = runTest {
        val pdf = tmpFolder.newFile("foldered.pdf")
        val dao = RestoreFakeTrashDao(
            records = listOf(scanRecord(1L, pdf.absolutePath).copy(folderId = 44L))
        )
        val documentRepository = RestoreFakeDocumentRepository()
        val useCase = RestoreScansUseCase(
            TrashRepository(dao),
            documentRepository,
            RestoreFakeFolderRepository(emptySet())
        )

        useCase(listOf(1L))

        assertEquals(listOf(1L), dao.restoredIds)
        assertEquals(listOf(1L), documentRepository.movedIds)
        assertEquals(null, documentRepository.targetFolderId)
    }
}

private fun scanRecord(id: Long, filepath: String) = Document(
    id = id,
    filename = "scan$id",
    filepath = filepath,
    timestamp = 0L,
    pageCount = 1,
    fileSize = 1L,
    deletedAt = 1L
)

private class RestoreFakeTrashDao(
    private val records: List<Document>
) : TrashDao {
    var restoredIds: List<Long> = emptyList()

    override fun getTrashedScans(): Flow<List<ScanRecord>> = flowOf(records.map { it.toEntity() })
    override suspend fun getScansByIds(ids: List<Long>): List<ScanRecord> =
        records.filter { it.id in ids }.map { it.toEntity() }
    override suspend fun softDelete(ids: List<Long>, timestamp: Long) = Unit
    override suspend fun restore(ids: List<Long>) {
        restoredIds = ids
    }
    override suspend fun findExpiredTrash(threshold: Long): List<ScanRecord> = emptyList()
}

private class RestoreFakeDocumentRepository : DocumentRepository {
    var movedIds: List<Long> = emptyList()
    var targetFolderId: Long? = null

    override fun getAllScans(): Flow<List<Document>> = flowOf(emptyList())
    override fun getScansInFolder(folderId: Long): Flow<List<Document>> = flowOf(emptyList())
    override fun getFavoriteScans(): Flow<List<Document>> = flowOf(emptyList())
    override fun searchScansFlow(query: String): Flow<List<Document>> = flowOf(emptyList())
    override suspend fun saveScan(record: Document): Long = 0L
    override suspend fun saveScans(records: List<Document>) = Unit
    override suspend fun deleteScan(record: Document) = Unit
    override suspend fun markSearchable(id: Long, fileSize: Long) = Unit
    override suspend fun markSearchableWithContent(
        id: Long,
        fileSize: Long,
        text: String?,
        tags: String?,
        confidence: Float?,
        language: String?,
        pageTexts: List<String>
    ) = Unit
    override suspend fun updateExtractedTextAndOcrStats(
        id: Long,
        text: String?,
        confidence: Float?,
        language: String?,
        pageTexts: List<String>
    ) = Unit
    override suspend fun updateFileSize(id: Long, fileSize: Long) = Unit
    override suspend fun updatePageMetrics(id: Long, pageCount: Int, fileSize: Long) = Unit
    override suspend fun invalidateAfterAppend(id: Long, fileSize: Long, pageCount: Int) = Unit
    override suspend fun updateFilenameAndPath(id: Long, filename: String, filepath: String, thumbnailPath: String?) = Unit
    override suspend fun moveDocumentsToFolder(ids: List<Long>, folderId: Long?) {
        movedIds = ids
        targetFolderId = folderId
    }
    override suspend fun setFavorite(ids: List<Long>, favorite: Boolean) = Unit
}

private class RestoreFakeFolderRepository(
    private val existingFolderIds: Set<Long>
) : FolderRepository {
    override fun observeFolders(): Flow<List<Folder>> = flowOf(emptyList())
    override suspend fun createFolder(folder: Folder): Long = 0L
    override suspend fun renameFolder(id: Long, name: String) = Unit
    override suspend fun deleteFolder(id: Long) = Unit
    override suspend fun folderExists(id: Long): Boolean = id in existingFolderIds
}


