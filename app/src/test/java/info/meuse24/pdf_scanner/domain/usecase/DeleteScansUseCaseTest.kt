package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.data.local.ScanDao
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit-Tests für DeleteScansUseCase.
 * Nutzt FakeScanDao (in-memory) statt einer echten Room-Datenbank.
 */
class DeleteScansUseCaseTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun makeUseCase(): Pair<DeleteScansUseCase, FakeScanDao> {
        val dao        = FakeScanDao()
        val repository = ScanRepository(dao)
        return DeleteScansUseCase(repository) to dao
    }

    private fun record(id: Long, filepath: String, thumbnailPath: String? = null) = ScanRecord(
        id            = id,
        filename      = "test_$id",
        filepath      = filepath,
        timestamp     = 0L,
        pageCount     = 1,
        fileSize      = 0L,
        thumbnailPath = thumbnailPath
    )

    @Test
    fun `löscht existierende Datei und ruft deleteScan auf`() = runTest {
        val (useCase, dao) = makeUseCase()
        val pdfFile = tmpFolder.newFile("scan.pdf")
        val rec = record(1L, pdfFile.absolutePath)

        val result = useCase(listOf(rec))

        assertTrue(result)
        assertFalse(pdfFile.exists())
        assertEquals(listOf(rec), dao.deleted)
    }

    @Test
    fun `löscht auch Thumbnail wenn vorhanden`() = runTest {
        val (useCase, _) = makeUseCase()
        val pdfFile   = tmpFolder.newFile("scan.pdf")
        val thumbFile = tmpFolder.newFile("scan.jpg")
        val rec = record(1L, pdfFile.absolutePath, thumbFile.absolutePath)

        useCase(listOf(rec))

        assertFalse(pdfFile.exists())
        assertFalse(thumbFile.exists())
    }

    @Test
    fun `fehlende Datei wird als erfolgreich gelöscht gewertet`() = runTest {
        val (useCase, dao) = makeUseCase()
        val rec = record(1L, "/nicht/vorhanden/scan.pdf")

        val result = useCase(listOf(rec))

        // Datei existiert nicht → gilt als gelöscht
        assertTrue(result)
        assertEquals(1, dao.deleted.size)
    }

    @Test
    fun `mehrere Records werden alle gelöscht`() = runTest {
        val (useCase, dao) = makeUseCase()
        val file1 = tmpFolder.newFile("scan1.pdf")
        val file2 = tmpFolder.newFile("scan2.pdf")
        val rec1 = record(1L, file1.absolutePath)
        val rec2 = record(2L, file2.absolutePath)

        val result = useCase(listOf(rec1, rec2))

        assertTrue(result)
        assertFalse(file1.exists())
        assertFalse(file2.exists())
        assertEquals(2, dao.deleted.size)
    }

    @Test
    fun `gibt false zurück wenn eine Datei nicht gelöscht werden kann`() = runTest {
        val (useCase, dao) = makeUseCase()
        // Verzeichnis statt Datei → kann nicht gelöscht werden wenn nicht leer
        val lockedDir = tmpFolder.newFolder("locked")
        tmpFolder.newFile("locked/child.pdf") // nicht leer → deleteOnExit schlägt fehl
        val rec = record(1L, lockedDir.absolutePath)

        // Auf Windows kann ein Verzeichnis mit Inhalt nicht über File.delete() gelöscht werden
        // Das simuliert einen Fehlschlag der Löschoperation
        val result = useCase(listOf(rec))

        assertFalse(result)
        assertTrue(dao.deleted.isEmpty())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fake DAO – rein im Speicher, ohne Room-Infrastruktur
// ─────────────────────────────────────────────────────────────────────────────

class FakeScanDao : ScanDao {
    val inserted  = mutableListOf<ScanRecord>()
    val deleted   = mutableListOf<ScanRecord>()
    val searchableUpdates = mutableListOf<Pair<Long, Long>>() // id to fileSize
    val searchableWithContentUpdates = mutableListOf<Triple<Long, Long, String?>>() // id, fileSize, text
    val fileSizeUpdates   = mutableListOf<Pair<Long, Long>>()
    val pageMetricUpdates = mutableListOf<Triple<Long, Int, Long>>()

    override fun getAllScans(): Flow<List<ScanRecord>> = flowOf(emptyList())
    override fun searchScansFlow(query: String): Flow<List<ScanRecord>> = flowOf(emptyList())
    override suspend fun insert(record: ScanRecord): Long {
        inserted.add(record)
        return inserted.size.toLong()
    }
    override suspend fun insertAll(records: List<ScanRecord>)    { inserted.addAll(records) }
    override suspend fun delete(record: ScanRecord)              { deleted.add(record) }
    override suspend fun markSearchable(id: Long, fileSize: Long) { searchableUpdates.add(id to fileSize) }
    override suspend fun markSearchableWithContent(id: Long, fileSize: Long, text: String?, tags: String?) {
        searchableWithContentUpdates.add(Triple(id, fileSize, text))
    }
    override suspend fun updateFileSize(id: Long, fileSize: Long) { fileSizeUpdates.add(id to fileSize) }
    override suspend fun updatePageMetrics(id: Long, pageCount: Int, fileSize: Long) {
        pageMetricUpdates.add(Triple(id, pageCount, fileSize))
    }
    override suspend fun updateFilenameAndPath(id: Long, filename: String, filepath: String, thumbnailPath: String?) {}
}
