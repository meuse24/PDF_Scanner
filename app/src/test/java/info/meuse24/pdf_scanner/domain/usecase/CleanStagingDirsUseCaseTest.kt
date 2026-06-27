package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.backup.BACKUP_RESTORE_STAGING_DIR_NAME
import info.meuse24.pdf_scanner.testutil.TestStorageProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CleanStagingDirsUseCaseTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `removes stale restore directories recursively`() {
        val storageProvider = TestStorageProvider(tmpFolder.root)
        val stagingRoot = storageProvider.tempDir().resolve(BACKUP_RESTORE_STAGING_DIR_NAME)
        stagingRoot.resolve("session/files").mkdirs()
        stagingRoot.resolve("session/files/document.pdf").writeText("decrypted")

        val result = CleanStagingDirsUseCase(storageProvider)()

        assertTrue(result)
        assertFalse(stagingRoot.exists())
    }

    @Test
    fun `succeeds when no staging directory exists`() {
        val storageProvider = TestStorageProvider(tmpFolder.root)

        val result = CleanStagingDirsUseCase(storageProvider)()

        assertTrue(result)
    }
}
