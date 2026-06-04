package info.meuse24.pdf_scanner.domain.gateway

import info.meuse24.pdf_scanner.domain.backup.BackupArchiveHeader
import info.meuse24.pdf_scanner.domain.backup.BackupExportData
import info.meuse24.pdf_scanner.domain.backup.BackupExportOptions
import info.meuse24.pdf_scanner.domain.backup.BackupKdfParameters
import info.meuse24.pdf_scanner.domain.backup.BackupProgress
import info.meuse24.pdf_scanner.domain.backup.BackupRestoreSummary
import info.meuse24.pdf_scanner.domain.backup.BackupStagingResult
import java.io.File
import java.io.InputStream
import java.io.OutputStream

interface BackupArchiveCodec {
    /**
     * Writes and closes the encrypted payload stream. The underlying output stream is closed by Tink.
     */
    fun writeBackup(
        outputStream: OutputStream,
        passphrase: CharArray,
        progressCallback: (BackupProgress) -> Unit = {},
        payloadWriter: (OutputStream) -> Unit
    ): BackupArchiveHeader

    /**
     * Opens and closes the decrypted payload stream. StreamingAead authenticates per segment as the
     * reader consumes the stream: reading to EOF authenticates the whole payload and detects
     * truncation (this is what the restore path does). A reader may deliberately stop early — e.g. a
     * manifest probe that only reads `manifest.json` — in which case only the consumed segments are
     * authenticated, which is sufficient for a sanity check but is not a full integrity verification.
     */
    fun readBackup(
        inputStream: InputStream,
        passphrase: CharArray,
        progressCallback: (BackupProgress) -> Unit = {},
        payloadReader: (InputStream, BackupArchiveHeader) -> Unit
    ): BackupArchiveHeader
}

interface BackupKeyDeriver {
    fun deriveKey(passphrase: CharArray, parameters: BackupKdfParameters): ByteArray
}

interface BackupDataSource {
    suspend fun loadExportData(options: BackupExportOptions): BackupExportData
}

interface BackupPayloadWriter {
    fun writePayload(exportData: BackupExportData, outputStream: OutputStream)
}

interface BackupPayloadStagingReader {
    fun readToStaging(inputStream: InputStream, stagingDir: File): BackupStagingResult
}

interface BackupRestoreWriter {
    suspend fun restoreFromStaging(staging: BackupStagingResult): BackupRestoreSummary
}
