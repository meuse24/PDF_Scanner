package info.meuse24.pdf_scanner.data.backup

import info.meuse24.pdf_scanner.domain.backup.BACKUP_MANIFEST_VERSION
import info.meuse24.pdf_scanner.domain.backup.BackupArchiveException
import info.meuse24.pdf_scanner.domain.backup.BackupFailure
import info.meuse24.pdf_scanner.domain.backup.BackupFileEntry
import info.meuse24.pdf_scanner.domain.backup.BackupManifest
import info.meuse24.pdf_scanner.domain.backup.BackupStagingResult
import info.meuse24.pdf_scanner.domain.gateway.BackupPayloadStagingReader
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Singleton
class BackupZipStagingReader @Inject constructor() : BackupPayloadStagingReader {
    internal var usableSpace: (File) -> Long = { directory -> directory.usableSpace }

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override fun readToStaging(inputStream: InputStream, stagingDir: File): BackupStagingResult {
        ensureCleanDirectory(stagingDir)
        val stagedFiles = linkedMapOf<String, StagedFile>()
        var manifest: BackupManifest? = null
        var entryCount = 0
        var totalBytes = 0L

        try {
            ZipInputStream(inputStream).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    entryCount += 1
                    if (entryCount > MAX_ENTRY_COUNT) {
                        throw invalidManifest("Backup contains too many ZIP entries")
                    }
                    val path = validateEntryPath(entry.name)
                    if (path == MANIFEST_ENTRY) {
                        if (manifest != null) throw invalidManifest("Backup contains duplicate manifest")
                        val bytes = zip.readEntryBytes(MAX_MANIFEST_BYTES)
                        totalBytes = addSize(totalBytes, bytes.size.toLong())
                        manifest = decodeManifest(bytes)
                    } else {
                        if (path in stagedFiles) throw invalidManifest("Backup contains duplicate entry $path")
                        val outputFile = stagingDir.resolve(path)
                        ensureInsideDirectory(stagingDir, outputFile)
                        outputFile.parentFile?.mkdirs()
                        val stats = zip.copyEntryToFile(outputFile)
                        totalBytes = addSize(totalBytes, stats.sizeBytes)
                        stagedFiles[path] = StagedFile(outputFile, stats)
                    }
                    if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                        throw invalidManifest("Backup payload is too large")
                    }
                    zip.closeEntry()
                }
            }

            val decodedManifest = manifest ?: throw invalidManifest("Backup manifest is missing")
            validateManifest(decodedManifest, stagedFiles)
            return BackupStagingResult(
                manifest = decodedManifest,
                stagingDir = stagingDir,
                files = stagedFiles.mapValues { it.value.file }
            )
        } catch (exception: BackupArchiveException) {
            stagingDir.deleteRecursively()
            throw exception
        } catch (exception: IOException) {
            stagingDir.deleteRecursively()
            throw BackupArchiveException(
                BackupFailure.Io(exception.message),
                "Could not stage backup ZIP payload",
                exception
            )
        } catch (exception: Exception) {
            stagingDir.deleteRecursively()
            throw BackupArchiveException(BackupFailure.CorruptArchive, "Could not read backup ZIP payload", exception)
        }
    }

    private fun validateManifest(manifest: BackupManifest, stagedFiles: Map<String, StagedFile>) {
        when {
            manifest.manifestVersion > BACKUP_MANIFEST_VERSION -> throw BackupArchiveException(
                BackupFailure.UnsupportedFutureVersion,
                "Backup manifest ${manifest.manifestVersion} needs a newer app"
            )
            manifest.manifestVersion < BACKUP_MANIFEST_VERSION -> throw invalidManifest(
                "Unsupported backup manifest ${manifest.manifestVersion}"
            )
            manifest.sourceDatabaseVersion > SOURCE_DATABASE_VERSION -> throw BackupArchiveException(
                BackupFailure.UnsupportedFutureVersion,
                "Backup database version ${manifest.sourceDatabaseVersion} needs a newer app"
            )
        }

        val expectedPaths = linkedSetOf<String>()
        manifest.documents.forEach { document ->
            expectedPaths += document.pdf.path
            validateManifestFile("PDF", document.pdf, stagedFiles)
            document.thumbnail?.let { thumbnail ->
                expectedPaths += thumbnail.path
                validateManifestFile("thumbnail", thumbnail, stagedFiles)
            }
            document.ocr?.let { ocr ->
                expectedPaths += ocr.path
                validateManifestFile("OCR", ocr, stagedFiles)
            }
        }

        val unexpectedPaths = stagedFiles.keys - expectedPaths
        if (unexpectedPaths.isNotEmpty()) {
            throw invalidManifest("Backup contains files not referenced by manifest: ${unexpectedPaths.joinToString()}")
        }
    }

    private fun validateManifestFile(label: String, entry: BackupFileEntry, stagedFiles: Map<String, StagedFile>) {
        validateEntryPath(entry.path)
        val staged = stagedFiles[entry.path] ?: throw invalidManifest("Missing $label file ${entry.path}")
        if (staged.stats.sizeBytes != entry.sizeBytes) {
            throw invalidManifest("Invalid $label size for ${entry.path}")
        }
        if (staged.stats.sha256Base64 != entry.sha256Base64) {
            throw invalidManifest("Invalid $label checksum for ${entry.path}")
        }
    }

    private fun validateEntryPath(rawPath: String): String {
        val path = rawPath.replace('\\', '/')
        val lowerPath = path.lowercase(Locale.US)
        val allowedPrefix = path == MANIFEST_ENTRY ||
            path.startsWith("documents/") ||
            path.startsWith("thumbnails/") ||
            path.startsWith("ocr/")
        if (
            path.isBlank() ||
            path != rawPath ||
            path.startsWith("/") ||
            lowerPath.matches(WINDOWS_DRIVE_REGEX) ||
            path.split('/').any { segment -> segment.isBlank() || segment == "." || segment == ".." } ||
            !allowedPrefix
        ) {
            throw invalidManifest("Unsafe ZIP entry path: $rawPath")
        }
        return path
    }

    private fun decodeManifest(bytes: ByteArray): BackupManifest =
        try {
            json.decodeFromString<BackupManifest>(bytes.decodeToString())
        } catch (exception: SerializationException) {
            throw invalidManifest("Backup manifest is not valid JSON", exception)
        }

    private fun ZipInputStream.readEntryBytes(maxBytes: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read == -1) break
            total = addSize(total, read.toLong())
            if (total > maxBytes) throw invalidManifest("ZIP entry is too large")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun ZipInputStream.copyEntryToFile(file: File): FileStats {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        file.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = read(buffer)
                if (read == -1) break
                total = addSize(total, read.toLong())
                if (total > MAX_SINGLE_FILE_BYTES) throw invalidManifest("ZIP entry is too large")
                ensureUsableSpace(file.parentFile ?: file, read.toLong())
                digest.update(buffer, 0, read)
                output.write(buffer, 0, read)
            }
        }
        return FileStats(
            sizeBytes = total,
            sha256Base64 = Base64.encode(digest.digest())
        )
    }

    private fun ensureCleanDirectory(directory: File) {
        if (directory.exists()) directory.deleteRecursively()
        if (!directory.mkdirs() && !directory.isDirectory) {
            throw BackupArchiveException(BackupFailure.Io("Could not create staging directory"), "Could not create staging directory")
        }
    }

    private fun ensureInsideDirectory(directory: File, file: File) {
        val basePath = directory.canonicalFile.toPath()
        val targetPath = file.canonicalFile.toPath()
        if (!targetPath.startsWith(basePath)) {
            throw invalidManifest("ZIP entry escapes staging directory")
        }
    }

    private fun addSize(current: Long, increment: Long): Long {
        val total = current + increment
        if (total < current) throw invalidManifest("ZIP size overflow")
        return total
    }

    private fun ensureUsableSpace(directory: File, requiredBytes: Long) {
        val availableBytes = usableSpace(directory)
        val requiredWithReserve = addSize(requiredBytes, MIN_FREE_SPACE_RESERVE_BYTES)
        if (availableBytes < requiredWithReserve) {
            throw BackupArchiveException(
                BackupFailure.InsufficientStorage(
                    requiredBytes = requiredWithReserve,
                    availableBytes = availableBytes
                ),
                "Not enough free storage to stage backup"
            )
        }
    }

    private fun invalidManifest(message: String, cause: Throwable? = null): BackupArchiveException =
        BackupArchiveException(BackupFailure.InvalidManifest, message, cause)

    private data class StagedFile(
        val file: File,
        val stats: FileStats
    )

    private data class FileStats(
        val sizeBytes: Long,
        val sha256Base64: String
    )

    private companion object {
        const val MANIFEST_ENTRY = "manifest.json"
        const val SOURCE_DATABASE_VERSION = 9
        const val MAX_ENTRY_COUNT = 20_000
        const val MAX_MANIFEST_BYTES = 16L * 1024L * 1024L
        const val MAX_SINGLE_FILE_BYTES = 512L * 1024L * 1024L
        const val MAX_UNCOMPRESSED_BYTES = 2L * 1024L * 1024L * 1024L
        const val MIN_FREE_SPACE_RESERVE_BYTES = 16L * 1024L * 1024L
        val WINDOWS_DRIVE_REGEX = Regex("^[a-z]:/.*")
    }
}
