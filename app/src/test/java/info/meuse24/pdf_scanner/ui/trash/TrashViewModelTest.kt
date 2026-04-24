package info.meuse24.pdf_scanner.ui.trash

import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.ScanDao
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.data.local.TrashDao
import info.meuse24.pdf_scanner.data.mapper.toDomain
import info.meuse24.pdf_scanner.data.mapper.toEntity
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.data.repository.TrashRepository
import info.meuse24.pdf_scanner.domain.model.Folder
import info.meuse24.pdf_scanner.domain.repository.FolderRepository
import info.meuse24.pdf_scanner.domain.usecase.DeleteScansUseCase
import info.meuse24.pdf_scanner.domain.usecase.PurgeTrashUseCase
import info.meuse24.pdf_scanner.domain.usecase.RestoreScansUseCase
import info.meuse24.pdf_scanner.testutil.FakeResourceProvider
import info.meuse24.pdf_scanner.testutil.TestDispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModelTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    private lateinit var store: TrashStore
    private lateinit var trashDao: TestTrashDao
    private lateinit var scanDao: TestScanDao
    private lateinit var viewModel: TrashViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `trashedScans exposes repository flow`() = runTest(dispatcher) {
        val record = trashedRecord(id = 1L, filename = "loaded")
        buildViewModel(listOf(record))
        val collector = backgroundScope.launch { viewModel.trashedScans.collect() }
        advanceUntilIdle()

        assertEquals(listOf(record), viewModel.trashedScans.value)
        assertFalse(viewModel.loading.value)
        collector.cancel()
    }

    @Test
    fun `restore removes record and reports success`() = runTest(dispatcher) {
        val record = trashedRecord(id = 2L, filename = "restore")
        buildViewModel(listOf(record))
        val collector = backgroundScope.launch { viewModel.trashedScans.collect() }

        viewModel.restore(listOf(record))
        advanceUntilIdle()

        assertEquals(emptyList<Document>(), viewModel.trashedScans.value)
        assertEquals("1 restored", viewModel.success.value)
        assertNull(viewModel.error.value)
        assertFalse(viewModel.loading.value)
        collector.cancel()
    }

    @Test
    fun `purge deletes file and reports success`() = runTest(dispatcher) {
        val record = trashedRecord(id = 3L, filename = "purge")
        buildViewModel(listOf(record))
        val collector = backgroundScope.launch { viewModel.trashedScans.collect() }
        val file = File(record.filepath)

        viewModel.purge(listOf(record))
        advanceUntilIdle()

        assertEquals(emptyList<Document>(), viewModel.trashedScans.value)
        assertEquals(listOf(3L), scanDao.deletedIds)
        assertFalse(file.exists())
        assertEquals("1 permanently deleted", viewModel.success.value)
        assertNull(viewModel.error.value)
        assertFalse(viewModel.loading.value)
        collector.cancel()
    }

    @Test
    fun `clearError resets error state`() = runTest(dispatcher) {
        val missing = Document(
            id = 4L,
            filename = "missing",
            filepath = File(tmpFolder.root, "missing.pdf").absolutePath,
            timestamp = 0L,
            pageCount = 1,
            fileSize = 0L,
            deletedAt = System.currentTimeMillis()
        )
        buildViewModel(listOf(missing))
        val collector = backgroundScope.launch { viewModel.trashedScans.collect() }

        viewModel.restore(listOf(missing))
        advanceUntilIdle()
        assertEquals("Restore missing file", viewModel.error.value)

        viewModel.clearError()

        assertNull(viewModel.error.value)
        collector.cancel()
    }

    private fun buildViewModel(records: List<Document>) {
        store = TrashStore(records)
        trashDao = TestTrashDao(store)
        scanDao = TestScanDao(store)
        val resourceProvider = FakeResourceProvider(
            strings = mapOf(
                R.string.error_restore_missing_file to "Restore missing file",
                R.string.error_restore_failed to "Restore failed",
                R.string.error_delete_failed to "Delete failed"
            ),
            plurals = mapOf(
                R.plurals.trash_restored to "%d restored",
                R.plurals.trash_purged to "%d permanently deleted"
            )
        )
        viewModel = TrashViewModel(
            trashRepository = TrashRepository(trashDao),
            restoreScansUseCase = RestoreScansUseCase(
                TrashRepository(trashDao),
                ScanRepository(scanDao),
                TrashTestFolderRepository()
            ),
            purgeTrashUseCase = PurgeTrashUseCase(
                repository = TrashRepository(trashDao),
                deleteScansUseCase = DeleteScansUseCase(ScanRepository(scanDao))
            ),
            resourceProvider = resourceProvider,
            dispatcherProvider = TestDispatcherProvider(dispatcher)
        )
    }

    private fun trashedRecord(id: Long, filename: String): Document {
        val file = tmpFolder.newFile("$filename.pdf")
        file.writeText("pdf")
        return Document(
            id = id,
            filename = filename,
            filepath = file.absolutePath,
            timestamp = 0L,
            pageCount = 1,
            fileSize = file.length(),
            deletedAt = System.currentTimeMillis()
        )
    }
}

private class TrashStore(initialRecords: List<Document>) {
    val records = MutableStateFlow(initialRecords)
}

private class TestTrashDao(
    private val store: TrashStore
) : TrashDao {
    override fun getTrashedScans(): Flow<List<ScanRecord>> =
        store.records.map { records ->
            records
                .filter { it.deletedAt != null }
                .sortedByDescending { it.deletedAt }
                .map { it.toEntity() }
        }

    override suspend fun getScansByIds(ids: List<Long>): List<ScanRecord> =
        store.records.value.filter { it.id in ids }.map { it.toEntity() }

    override suspend fun softDelete(ids: List<Long>, timestamp: Long) {
        store.records.value = store.records.value.map { record ->
            if (record.id in ids) record.copy(deletedAt = timestamp) else record
        }
    }

    override suspend fun restore(ids: List<Long>) {
        store.records.value = store.records.value.mapNotNull { record ->
            if (record.id in ids) record.copy(deletedAt = null) else record
        }
    }

    override suspend fun findExpiredTrash(threshold: Long): List<ScanRecord> =
        store.records.value.filter { (it.deletedAt ?: Long.MAX_VALUE) < threshold }.map { it.toEntity() }
}

private class TestScanDao(
    private val store: TrashStore
) : ScanDao {
    val deletedIds = mutableListOf<Long>()

    override fun getAllScans(): Flow<List<ScanRecord>> = store.records.map { records -> records.map { it.toEntity() } }

    override fun searchScansFlow(query: String): Flow<List<ScanRecord>> = store.records.map { records -> records.map { it.toEntity() } }

    override suspend fun insert(record: ScanRecord): Long = record.id

    override suspend fun insertAll(records: List<ScanRecord>) = Unit

    override suspend fun delete(record: ScanRecord) {
        deletedIds += record.id
        store.records.value = store.records.value.filterNot { it.id == record.id }
    }

    override suspend fun markSearchableWithContent(
        id: Long,
        fileSize: Long,
        text: String?,
        tags: String?,
        confidence: Float?,
        language: String?,
        pageTextJson: String?
    ) = Unit

    override suspend fun updateExtractedTextAndOcrStats(
        id: Long,
        text: String?,
        confidence: Float?,
        language: String?,
        pageTextJson: String?
    ) = Unit

    override suspend fun markSearchable(id: Long, fileSize: Long) = Unit

    override suspend fun updateFileSize(id: Long, fileSize: Long) = Unit

    override suspend fun updatePageMetrics(id: Long, pageCount: Int, fileSize: Long) = Unit

    override suspend fun invalidateAfterAppend(id: Long, fileSize: Long, pageCount: Int) = Unit

    override suspend fun updateFilenameAndPath(
        id: Long,
        filename: String,
        filepath: String,
        thumbnailPath: String?
    ) = Unit

    override fun getScansInFolder(folderId: Long): Flow<List<ScanRecord>> = store.records.map { emptyList() }

    override fun getFavoriteScans(): Flow<List<ScanRecord>> = store.records.map { emptyList() }

    override suspend fun moveScans(ids: List<Long>, folderId: Long?) = Unit

    override suspend fun setFavorite(ids: List<Long>, favorite: Boolean) = Unit
}

private class TrashTestFolderRepository : FolderRepository {
    override fun observeFolders(): Flow<List<Folder>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun createFolder(folder: Folder): Long = 0L
    override suspend fun renameFolder(id: Long, name: String) = Unit
    override suspend fun deleteFolder(id: Long) = Unit
    override suspend fun folderExists(id: Long): Boolean = false
}

