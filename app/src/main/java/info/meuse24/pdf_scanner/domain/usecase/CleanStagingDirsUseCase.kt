package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.backup.BACKUP_RESTORE_STAGING_DIR_NAME
import info.meuse24.pdf_scanner.domain.gateway.DispatcherProvider
import info.meuse24.pdf_scanner.domain.gateway.StorageProvider
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.withContext

class CleanStagingDirsUseCase @Inject constructor(
    private val storageProvider: StorageProvider,
    private val dispatchers: DispatcherProvider
) {
    suspend operator fun invoke(): Boolean = withContext(dispatchers.io) {
        val stagingRoot = File(storageProvider.tempDir(), BACKUP_RESTORE_STAGING_DIR_NAME)
        !stagingRoot.exists() || stagingRoot.deleteRecursively()
    }
}
