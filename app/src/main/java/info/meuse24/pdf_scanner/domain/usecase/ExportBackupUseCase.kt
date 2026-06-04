package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.backup.BackupArchiveException
import info.meuse24.pdf_scanner.domain.backup.BackupExportOptions
import info.meuse24.pdf_scanner.domain.backup.BackupFailure
import info.meuse24.pdf_scanner.domain.backup.BackupManifest
import info.meuse24.pdf_scanner.domain.backup.BackupProgress
import info.meuse24.pdf_scanner.domain.backup.BackupResult
import info.meuse24.pdf_scanner.domain.gateway.BackupArchiveCodec
import info.meuse24.pdf_scanner.domain.gateway.BackupDataSource
import info.meuse24.pdf_scanner.domain.gateway.BackupLocationFactory
import info.meuse24.pdf_scanner.domain.gateway.BackupManifestReader
import info.meuse24.pdf_scanner.domain.gateway.BackupPayloadWriter
import info.meuse24.pdf_scanner.domain.gateway.DispatcherProvider
import java.util.Arrays
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Orchestrates an encrypted backup export: loads the Room snapshot, writes the ZIP payload into the
 * Tink streaming-AEAD container at a user-picked location, then probe-reads the result to confirm
 * it decrypts. On any failure or cancellation the partial target file is compensated (deleted).
 *
 * The [passphrase] is zeroed before returning regardless of outcome.
 */
class ExportBackupUseCase @Inject constructor(
    private val dataSource: BackupDataSource,
    private val payloadWriter: BackupPayloadWriter,
    private val codec: BackupArchiveCodec,
    private val manifestReader: BackupManifestReader,
    private val locationFactory: BackupLocationFactory,
    private val dispatchers: DispatcherProvider
) {
    suspend operator fun invoke(
        locationToken: String,
        passphrase: CharArray,
        options: BackupExportOptions,
        onProgress: (BackupProgress) -> Unit = {}
    ): BackupResult = withContext(dispatchers.io) {
        val target = locationFactory.exportTarget(locationToken)
        try {
            val exportData = dataSource.loadExportData(options)
            onProgress(BackupProgress.Preparing(exportData.documents.size))

            target.openOutputStream().use { output ->
                codec.writeBackup(output, passphrase, onProgress) { payload ->
                    payloadWriter.writePayload(exportData, payload)
                }
            }

            ensureActive()
            val manifest = probeReadManifest(target, passphrase)
            onProgress(BackupProgress.Finished)
            BackupResult.Success(manifest = manifest)
        } catch (cancellation: CancellationException) {
            runCatching { target.delete() }
            throw cancellation
        } catch (exception: Exception) {
            val removed = runCatching { target.delete() }.getOrDefault(false)
            BackupResult.Failure(
                reason = exception.toBackupFailure(),
                cause = exception,
                leftoverFileMayRemain = !removed
            )
        } finally {
            Arrays.fill(passphrase, '\u0000')
        }
    }

    /**
     * Sanity probe: reopen the freshly written backup, decrypt, and read only `manifest.json`. This
     * authenticates the header, the wrapped keyset and the leading payload segment(s) — enough to
     * confirm the file is decryptable. It intentionally does not drain to EOF (see [BackupArchiveCodec]).
     */
    private fun probeReadManifest(
        target: info.meuse24.pdf_scanner.domain.gateway.BackupExportTarget,
        passphrase: CharArray
    ): BackupManifest {
        var manifest: BackupManifest? = null
        target.openInputStream().use { raw ->
            codec.readBackup(raw, passphrase) { payload, _ ->
                manifest = manifestReader.readManifest(payload)
            }
        }
        return manifest
            ?: throw BackupArchiveException(BackupFailure.CorruptArchive, "Backup probe returned no manifest")
    }

    private fun Exception.toBackupFailure(): BackupFailure =
        (this as? BackupArchiveException)?.failure ?: BackupFailure.Io(message)
}
