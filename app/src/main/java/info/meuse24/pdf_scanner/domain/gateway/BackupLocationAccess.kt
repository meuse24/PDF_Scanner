package info.meuse24.pdf_scanner.domain.gateway

import info.meuse24.pdf_scanner.domain.backup.BackupManifest
import java.io.InputStream
import java.io.OutputStream

/**
 * Target for writing an encrypted backup, e.g. a user-picked Storage Access Framework document.
 * Framework-free so the export use case stays in the domain layer.
 */
interface BackupExportTarget {
    val displayName: String?

    fun openOutputStream(): OutputStream

    /** Re-opens the freshly written backup for the post-write probe read. */
    fun openInputStream(): InputStream

    /** Compensating delete of a partial or failed backup file. Returns true when removed. */
    fun delete(): Boolean
}

/** Source for reading an encrypted backup to restore from. */
interface BackupImportSource {
    val displayName: String?

    fun openInputStream(): InputStream
}

/** Resolves opaque location tokens (e.g. SAF content URIs) into framework-free handles. */
interface BackupLocationFactory {
    fun exportTarget(locationToken: String): BackupExportTarget

    fun importSource(locationToken: String): BackupImportSource
}

/**
 * Reads only the manifest entry from a decrypted backup payload stream, without staging the whole
 * archive to disk. Used by the export probe to confirm a backup is decryptable end-to-end.
 */
interface BackupManifestReader {
    fun readManifest(payload: InputStream): BackupManifest
}
