package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.local.TrashDao
import info.meuse24.pdf_scanner.data.repository.TrashRepository
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
        val useCase = RestoreScansUseCase(TrashRepository(dao))

        useCase(listOf(1L))

        assertEquals(listOf(1L), dao.restoredIds)
    }

    @Test
    fun `missing file fails before restore`() = runTest {
        val dao = RestoreFakeTrashDao(records = listOf(scanRecord(1L, "missing.pdf")))
        val useCase = RestoreScansUseCase(TrashRepository(dao))

        try {
            useCase(listOf(1L))
            fail("Expected RestoreMissingFileException")
        } catch (_: RestoreMissingFileException) {
        }
        assertEquals(emptyList<Long>(), dao.restoredIds)
    }
}

private fun scanRecord(id: Long, filepath: String) = ScanRecord(
    id = id,
    filename = "scan$id",
    filepath = filepath,
    timestamp = 0L,
    pageCount = 1,
    fileSize = 1L,
    deletedAt = 1L
)

private class RestoreFakeTrashDao(
    private val records: List<ScanRecord>
) : TrashDao {
    var restoredIds: List<Long> = emptyList()

    override fun getTrashedScans(): Flow<List<ScanRecord>> = flowOf(records)
    override suspend fun getScansByIds(ids: List<Long>): List<ScanRecord> =
        records.filter { it.id in ids }
    override suspend fun softDelete(ids: List<Long>, timestamp: Long) = Unit
    override suspend fun restore(ids: List<Long>) {
        restoredIds = ids
    }
    override suspend fun findExpiredTrash(threshold: Long): List<ScanRecord> = emptyList()
}
