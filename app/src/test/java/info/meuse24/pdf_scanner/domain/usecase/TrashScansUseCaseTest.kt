package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.local.TrashDao
import info.meuse24.pdf_scanner.data.repository.TrashRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TrashScansUseCaseTest {
    @Test
    fun `soft deletes all record ids`() = runTest {
        val dao = FakeTrashDao()
        val useCase = TrashScansUseCase(TrashRepository(dao))

        val ids = useCase(
            listOf(
                scanRecord(id = 1L),
                scanRecord(id = 2L)
            )
        )

        assertEquals(listOf(1L, 2L), ids)
        assertEquals(listOf(1L, 2L), dao.softDeletedIds)
        assertTrue(dao.softDeleteTimestamp > 0L)
    }

    @Test
    fun `empty list is ignored`() = runTest {
        val dao = FakeTrashDao()
        val useCase = TrashScansUseCase(TrashRepository(dao))

        val ids = useCase(emptyList())

        assertTrue(ids.isEmpty())
        assertTrue(dao.softDeletedIds.isEmpty())
    }

    @Test
    fun `invalid id fails fast`() = runTest {
        val dao = FakeTrashDao()
        val useCase = TrashScansUseCase(TrashRepository(dao))

        try {
            useCase(listOf(scanRecord(id = 0L)))
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            assertTrue(dao.softDeletedIds.isEmpty())
        }
    }
}

private fun scanRecord(id: Long) = ScanRecord(
    id = id,
    filename = "scan$id",
    filepath = "scan$id.pdf",
    timestamp = 0L,
    pageCount = 1,
    fileSize = 1L
)

private class FakeTrashDao : TrashDao {
    var softDeletedIds: List<Long> = emptyList()
    var softDeleteTimestamp: Long = 0L

    override fun getTrashedScans(): Flow<List<ScanRecord>> = flowOf(emptyList())
    override suspend fun getScansByIds(ids: List<Long>): List<ScanRecord> = emptyList()
    override suspend fun softDelete(ids: List<Long>, timestamp: Long) {
        softDeletedIds = ids
        softDeleteTimestamp = timestamp
    }
    override suspend fun restore(ids: List<Long>) = Unit
    override suspend fun findExpiredTrash(threshold: Long): List<ScanRecord> = emptyList()
}
