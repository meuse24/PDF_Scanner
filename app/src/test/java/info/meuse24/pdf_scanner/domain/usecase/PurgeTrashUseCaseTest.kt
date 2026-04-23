package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.local.TrashDao
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.data.repository.TrashRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PurgeTrashUseCaseTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `purgeExpired deletes only records older than threshold`() = runTest {
        val expiredPdf = tmpFolder.newFile("expired.pdf")
        val activePdf = tmpFolder.newFile("active.pdf")
        val now = System.currentTimeMillis()
        val expired = scanRecord(1L, expiredPdf, now - 4_000L)
        val active = scanRecord(2L, activePdf, now - 1_000L)
        val trashDao = PurgeFakeTrashDao(records = listOf(expired, active))
        val scanDao = PurgeFakeScanDao()
        val useCase = PurgeTrashUseCase(
            repository = TrashRepository(trashDao),
            deleteScansUseCase = DeleteScansUseCase(ScanRepository(scanDao))
        )

        val deleted = useCase.purgeExpired(retentionMillis = 2_000L)

        assertEquals(1, deleted)
        assertEquals(listOf(1L), scanDao.deletedIds)
    }
}

private fun scanRecord(id: Long, file: File, deletedAt: Long) = ScanRecord(
    id = id,
    filename = file.nameWithoutExtension,
    filepath = file.absolutePath,
    timestamp = 0L,
    pageCount = 1,
    fileSize = file.length(),
    deletedAt = deletedAt
)

private class PurgeFakeTrashDao(
    private val records: List<ScanRecord>
) : TrashDao {
    override fun getTrashedScans(): Flow<List<ScanRecord>> = flowOf(records)
    override suspend fun getScansByIds(ids: List<Long>): List<ScanRecord> =
        records.filter { it.id in ids }
    override suspend fun softDelete(ids: List<Long>, timestamp: Long) = Unit
    override suspend fun restore(ids: List<Long>) = Unit
    override suspend fun findExpiredTrash(threshold: Long): List<ScanRecord> =
        records.filter { (it.deletedAt ?: Long.MAX_VALUE) < threshold }
}

private class PurgeFakeScanDao : info.meuse24.pdf_scanner.data.local.ScanDao {
    val deletedIds = mutableListOf<Long>()

    override fun getAllScans(): Flow<List<ScanRecord>> = flowOf(emptyList())
    override fun searchScansFlow(query: String): Flow<List<ScanRecord>> = flowOf(emptyList())
    override suspend fun insert(record: ScanRecord): Long = record.id
    override suspend fun insertAll(records: List<ScanRecord>) = Unit
    override suspend fun delete(record: ScanRecord) {
        deletedIds += record.id
    }
    override suspend fun markSearchableWithContent(id: Long, fileSize: Long, text: String?, tags: String?) = Unit
    override suspend fun markSearchable(id: Long, fileSize: Long) = Unit
    override suspend fun updateFileSize(id: Long, fileSize: Long) = Unit
    override suspend fun updatePageMetrics(id: Long, pageCount: Int, fileSize: Long) = Unit
    override suspend fun invalidateAfterAppend(id: Long, fileSize: Long, pageCount: Int) = Unit
    override suspend fun updateFilenameAndPath(id: Long, filename: String, filepath: String, thumbnailPath: String?) = Unit
}
