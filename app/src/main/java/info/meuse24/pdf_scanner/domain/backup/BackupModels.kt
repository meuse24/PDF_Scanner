package info.meuse24.pdf_scanner.domain.backup

import java.io.File
import kotlinx.serialization.Serializable

const val BACKUP_APP_ID = "info.meuse24.pdf_scanner"
const val BACKUP_FORMAT_VERSION = 1
const val BACKUP_MANIFEST_VERSION = 1
const val BACKUP_ASSOCIATED_DATA_VERSION = 1
const val BACKUP_MIME_TYPE = "application/vnd.info.meuse24.pdf-scanner.backup"
const val BACKUP_FILE_EXTENSION = "m24backup"

@Serializable
data class BackupKdfParameters(
    val algorithm: String = "argon2id",
    val saltBase64: String,
    val memoryKiB: Int = 65536,
    val iterations: Int = 3,
    val parallelism: Int = 1,
    val outputBytes: Int = 32
)

@Serializable
data class BackupKeyWrapInfo(
    val algorithm: String = "AES-256-GCM",
    val nonceBase64: String,
    val encryptedStreamingKeysetBase64: String
)

@Serializable
data class BackupPayloadInfo(
    val algorithm: String = "TINK_STREAMING_AEAD",
    val keyTemplate: String = "AES256_GCM_HKDF_1MB",
    val associatedDataVersion: Int = BACKUP_ASSOCIATED_DATA_VERSION
)

@Serializable
data class BackupArchiveHeader(
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val appId: String = BACKUP_APP_ID,
    val createdAtEpochMillis: Long,
    val kdf: BackupKdfParameters,
    val keyWrap: BackupKeyWrapInfo,
    val payload: BackupPayloadInfo = BackupPayloadInfo()
)

@Serializable
data class BackupExportOptions(
    val includeTrash: Boolean = false,
    val includeOcrText: Boolean = true,
    val includeThumbnails: Boolean = true
)

@Serializable
data class BackupImportOptions(
    val mergeOnly: Boolean = true
)

@Serializable
data class BackupManifest(
    val manifestVersion: Int = BACKUP_MANIFEST_VERSION,
    val sourceDatabaseVersion: Int,
    val exportedAtEpochMillis: Long,
    val appVersionName: String? = null,
    val appVersionCode: Long? = null,
    val options: BackupExportOptions = BackupExportOptions(),
    val folders: List<BackupFolderEntry> = emptyList(),
    val documents: List<BackupDocumentEntry> = emptyList()
)

@Serializable
data class BackupFolderEntry(
    val backupFolderId: String,
    val name: String,
    val colorArgb: Int? = null,
    val createdAt: Long
)

@Serializable
data class BackupDocumentEntry(
    val backupDocumentId: String,
    val filename: String,
    val timestamp: Long,
    val pageCount: Int,
    val fileSize: Long,
    val isSearchable: Boolean,
    val isEncrypted: Boolean,
    val tags: String? = null,
    val folderBackupId: String? = null,
    val isFavorite: Boolean = false,
    val deletedAt: Long? = null,
    val pdf: BackupFileEntry,
    val thumbnail: BackupFileEntry? = null,
    val ocr: BackupFileEntry? = null
)

@Serializable
data class BackupFileEntry(
    val path: String,
    val sizeBytes: Long,
    val sha256Base64: String
)

@Serializable
data class BackupOcrEntry(
    val backupDocumentId: String,
    val extractedText: String? = null,
    val ocrPageTextJson: String? = null,
    val ocrConfidence: Float? = null,
    val ocrLanguage: String? = null
)

data class BackupExportData(
    val sourceDatabaseVersion: Int,
    val exportedAtEpochMillis: Long,
    val appVersionName: String? = null,
    val appVersionCode: Long? = null,
    val options: BackupExportOptions,
    val folders: List<BackupExportFolder>,
    val documents: List<BackupExportDocument>
)

data class BackupExportFolder(
    val localId: Long,
    val backupFolderId: String,
    val name: String,
    val colorArgb: Int? = null,
    val createdAt: Long
)

data class BackupExportDocument(
    val localId: Long,
    val backupDocumentId: String,
    val filename: String,
    val timestamp: Long,
    val pageCount: Int,
    val fileSize: Long,
    val isSearchable: Boolean,
    val isEncrypted: Boolean,
    val tags: String? = null,
    val folderBackupId: String? = null,
    val isFavorite: Boolean = false,
    val deletedAt: Long? = null,
    val extractedText: String? = null,
    val ocrConfidence: Float? = null,
    val ocrLanguage: String? = null,
    val ocrPageTextJson: String? = null,
    val pdfFile: File,
    val thumbnailFile: File? = null
) {
    val hasOcrPayload: Boolean
        get() = extractedText != null ||
            ocrPageTextJson != null ||
            ocrConfidence != null ||
            ocrLanguage != null
}

data class BackupStagingResult(
    val manifest: BackupManifest,
    val stagingDir: File,
    val files: Map<String, File>
)

data class BackupRestoreSummary(
    val documentsRestored: Int,
    val foldersCreated: Int,
    val foldersReused: Int,
    val thumbnailsCopied: Int,
    val thumbnailsRegenerated: Int
)

sealed interface BackupProgress {
    data class Preparing(val documents: Int) : BackupProgress
    data class Writing(val current: Int, val total: Int) : BackupProgress
    data class Reading(val current: Int, val total: Int?) : BackupProgress
    data object Finished : BackupProgress
}

sealed interface BackupResult {
    data class Success(val header: BackupArchiveHeader? = null, val manifest: BackupManifest? = null) : BackupResult
    data class Failure(
        val reason: BackupFailure,
        val cause: Throwable? = null,
        /** True when a partial backup file could not be removed and may linger at the target. */
        val leftoverFileMayRemain: Boolean = false
    ) : BackupResult
}

/** Summary shown to the user before a restore is finalized (computed from a staged archive). */
data class BackupRestorePreview(
    val documentCount: Int,
    val folderCount: Int,
    val totalBytes: Long,
    val createdAtEpochMillis: Long,
    val appVersionName: String? = null,
    val sourceDatabaseVersion: Int,
    val includesTrash: Boolean,
    /** Merge-only restore is additive and may create duplicates of already present documents. */
    val mergeAdditive: Boolean = true
)

/** Result of the first restore phase: decrypt + stage + validate, keeping the staging session. */
sealed interface BackupRestorePrepareResult {
    data class Ready(
        val preview: BackupRestorePreview,
        val session: BackupStagingResult
    ) : BackupRestorePrepareResult

    data class Failure(val reason: BackupFailure, val cause: Throwable? = null) : BackupRestorePrepareResult
}

/** Result of the second restore phase: merge the staged archive into the live database. */
sealed interface BackupRestoreResult {
    data class Success(val summary: BackupRestoreSummary) : BackupRestoreResult
    data class Failure(val reason: BackupFailure, val cause: Throwable? = null) : BackupRestoreResult
}

sealed interface BackupFailure {
    data object WrongPassphrase : BackupFailure
    data object CorruptArchive : BackupFailure
    data object UnsupportedFormat : BackupFailure
    data object UnsupportedFutureVersion : BackupFailure
    data object InvalidManifest : BackupFailure
    data object MissingDocumentFile : BackupFailure
    data object Cancelled : BackupFailure
    data class InsufficientStorage(
        val requiredBytes: Long? = null,
        val availableBytes: Long? = null
    ) : BackupFailure
    data class Io(val message: String? = null) : BackupFailure
}
