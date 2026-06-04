package info.meuse24.pdf_scanner.data.backup

import info.meuse24.pdf_scanner.domain.backup.BackupArchiveException
import info.meuse24.pdf_scanner.domain.backup.BackupFailure
import info.meuse24.pdf_scanner.domain.backup.BackupManifest
import info.meuse24.pdf_scanner.domain.gateway.BackupManifestReader
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Reads only the `manifest.json` entry from a decrypted backup ZIP payload. Used by the export
 * probe, which reopens and decrypts the just-written backup to verify it is readable. The reader
 * deliberately stops at the manifest and does not consume the stream to EOF; this is a sanity probe
 * (header + keyset + first segment authenticate), not a full integrity verification.
 */
@Singleton
class BackupManifestZipReader @Inject constructor() : BackupManifestReader {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override fun readManifest(payload: InputStream): BackupManifest {
        val zip = ZipInputStream(payload)
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory && entry.name.replace('\\', '/') == MANIFEST_ENTRY) {
                return decode(zip.readCapped(MAX_MANIFEST_BYTES))
            }
            zip.closeEntry()
        }
        throw BackupArchiveException(BackupFailure.InvalidManifest, "Backup manifest is missing")
    }

    private fun decode(bytes: ByteArray): BackupManifest =
        try {
            json.decodeFromString<BackupManifest>(bytes.decodeToString())
        } catch (exception: SerializationException) {
            throw BackupArchiveException(BackupFailure.InvalidManifest, "Backup manifest is not valid JSON", exception)
        }

    private fun ZipInputStream.readCapped(maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) {
                throw BackupArchiveException(BackupFailure.InvalidManifest, "Backup manifest is too large")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private companion object {
        const val MANIFEST_ENTRY = "manifest.json"
        const val MAX_MANIFEST_BYTES = 16L * 1024L * 1024L
    }
}
