package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.backup.BackupArchiveException
import info.meuse24.pdf_scanner.domain.backup.BackupFailure
import info.meuse24.pdf_scanner.domain.backup.BackupManifest
import info.meuse24.pdf_scanner.domain.backup.BackupProgress
import info.meuse24.pdf_scanner.domain.backup.BackupRestorePrepareResult
import info.meuse24.pdf_scanner.domain.backup.BackupRestorePreview
import info.meuse24.pdf_scanner.domain.backup.BackupRestoreResult
import info.meuse24.pdf_scanner.domain.backup.BackupStagingResult
import info.meuse24.pdf_scanner.domain.backup.BACKUP_RESTORE_STAGING_DIR_NAME
import info.meuse24.pdf_scanner.domain.gateway.BackupArchiveCodec
import info.meuse24.pdf_scanner.domain.gateway.BackupLocationFactory
import info.meuse24.pdf_scanner.domain.gateway.BackupPayloadStagingReader
import info.meuse24.pdf_scanner.domain.gateway.BackupRestoreWriter
import info.meuse24.pdf_scanner.domain.gateway.DispatcherProvider
import info.meuse24.pdf_scanner.domain.gateway.StorageProvider
import java.io.File
import java.util.Arrays
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * Orchestrates restore in two phases so the user can review a summary before any change is made:
 *
 * 1. [prepare] decrypts the archive and validates it into an isolated staging directory, returning
 *    a [BackupRestorePreview] plus the staging session (kept on disk).
 * 2. [confirm] merges the staged archive into the live database via [BackupRestoreWriter] (which
 *    compensates files and clears the staging directory), or [cancel] discards the staging session.
 *
 * The [passphrase] is zeroed before [prepare] returns regardless of outcome.
 */
class RestoreBackupUseCase @Inject constructor(
    private val locationFactory: BackupLocationFactory,
    private val codec: BackupArchiveCodec,
    private val stagingReader: BackupPayloadStagingReader,
    private val restoreWriter: BackupRestoreWriter,
    private val storageProvider: StorageProvider,
    private val dispatchers: DispatcherProvider
) {
    suspend fun prepare(
        locationToken: String,
        passphrase: CharArray,
        onProgress: (BackupProgress) -> Unit = {}
    ): BackupRestorePrepareResult = withContext(dispatchers.io) {
        val stagingDir = newStagingDir()
        try {
            onProgress(BackupProgress.Reading(current = 0, total = null))
            val source = locationFactory.importSource(locationToken)
            var staging: BackupStagingResult? = null
            source.openInputStream().use { raw ->
                codec.readBackup(raw, passphrase) { payload, _ ->
                    staging = stagingReader.readToStaging(payload, stagingDir)
                }
            }
            val session = staging
                ?: throw BackupArchiveException(BackupFailure.CorruptArchive, "Backup payload was empty")
            onProgress(BackupProgress.Finished)
            BackupRestorePrepareResult.Ready(
                preview = session.manifest.toPreview(),
                session = session
            )
        } catch (cancellation: CancellationException) {
            stagingDir.deleteRecursively()
            throw cancellation
        } catch (exception: Exception) {
            stagingDir.deleteRecursively()
            BackupRestorePrepareResult.Failure(
                reason = exception.toBackupFailure(),
                cause = exception
            )
        } finally {
            Arrays.fill(passphrase, ' ')
        }
    }

    suspend fun confirm(session: BackupStagingResult): BackupRestoreResult = withContext(dispatchers.io) {
        try {
            BackupRestoreResult.Success(restoreWriter.restoreFromStaging(session))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            BackupRestoreResult.Failure(
                reason = exception.toBackupFailure(),
                cause = exception
            )
        }
    }

    /** Discards a prepared staging session the user chose not to restore. */
    suspend fun cancel(session: BackupStagingResult) = withContext(dispatchers.io) {
        session.stagingDir.deleteRecursively()
    }

    private fun newStagingDir(): File =
        File(
            File(storageProvider.tempDir(), BACKUP_RESTORE_STAGING_DIR_NAME),
            UUID.randomUUID().toString()
        )

    private fun BackupManifest.toPreview(): BackupRestorePreview {
        val totalBytes = documents.sumOf { document ->
            document.pdf.sizeBytes +
                (document.thumbnail?.sizeBytes ?: 0L) +
                (document.ocr?.sizeBytes ?: 0L)
        }
        return BackupRestorePreview(
            documentCount = documents.size,
            folderCount = folders.size,
            totalBytes = totalBytes,
            createdAtEpochMillis = exportedAtEpochMillis,
            appVersionName = appVersionName,
            sourceDatabaseVersion = sourceDatabaseVersion,
            includesTrash = options.includeTrash
        )
    }

    private fun Exception.toBackupFailure(): BackupFailure =
        (this as? BackupArchiveException)?.failure ?: BackupFailure.Io(message)
}
