package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.backup.BACKUP_RESTORE_STAGING_DIR_NAME
import info.meuse24.pdf_scanner.testutil.TestDispatcherProvider
import info.meuse24.pdf_scanner.testutil.TestStorageProvider
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CleanStagingDirsUseCaseTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher())

    @Test
    fun `removes stale restore directories recursively`() = runTest {
        val storageProvider = TestStorageProvider(tmpFolder.root)
        val stagingRoot = storageProvider.tempDir().resolve(BACKUP_RESTORE_STAGING_DIR_NAME)
        stagingRoot.resolve("session/files").mkdirs()
        stagingRoot.resolve("session/files/document.pdf").writeText("decrypted")

        val result = CleanStagingDirsUseCase(storageProvider, dispatchers)()

        assertTrue(result)
        assertFalse(stagingRoot.exists())
    }

    @Test
    fun `succeeds when no staging directory exists`() = runTest {
        val storageProvider = TestStorageProvider(tmpFolder.root)

        val result = CleanStagingDirsUseCase(storageProvider, dispatchers)()

        assertTrue(result)
    }
}
