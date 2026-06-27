package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.backup.BACKUP_RESTORE_STAGING_DIR_NAME
import info.meuse24.pdf_scanner.domain.gateway.StorageProvider
import java.io.File
import javax.inject.Inject

class CleanStagingDirsUseCase @Inject constructor(
    private val storageProvider: StorageProvider
) {
    operator fun invoke(): Boolean {
        val stagingRoot = File(
            storageProvider.tempDir(),
            BACKUP_RESTORE_STAGING_DIR_NAME
        )
        return !stagingRoot.exists() || stagingRoot.deleteRecursively()
    }
}
