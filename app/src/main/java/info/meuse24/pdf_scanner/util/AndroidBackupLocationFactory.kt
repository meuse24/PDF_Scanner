package info.meuse24.pdf_scanner.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.domain.gateway.BackupExportTarget
import info.meuse24.pdf_scanner.domain.gateway.BackupImportSource
import info.meuse24.pdf_scanner.domain.gateway.BackupLocationFactory
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves Storage Access Framework content-URI tokens into framework-free backup handles so the
 * export/restore use cases never touch [Uri] or [ContentResolver] directly.
 */
@Singleton
class AndroidBackupLocationFactory @Inject constructor(
    @param:ApplicationContext private val context: Context
) : BackupLocationFactory {
    private val resolver: ContentResolver get() = context.contentResolver

    override fun exportTarget(locationToken: String): BackupExportTarget =
        SafBackupExportTarget(resolver, Uri.parse(locationToken))

    override fun importSource(locationToken: String): BackupImportSource =
        SafBackupImportSource(resolver, Uri.parse(locationToken))
}

private class SafBackupExportTarget(
    private val resolver: ContentResolver,
    private val uri: Uri
) : BackupExportTarget {
    override val displayName: String? get() = resolver.queryDisplayName(uri)

    override fun openOutputStream(): OutputStream =
        resolver.openOutputStream(uri, "wt")
            ?: throw IOException("Could not open backup target for writing")

    override fun openInputStream(): InputStream =
        resolver.openInputStream(uri)
            ?: throw IOException("Could not reopen backup target for reading")

    override fun delete(): Boolean =
        try {
            if (DocumentsContract.deleteDocument(resolver, uri)) {
                true
            } else {
                resolver.delete(uri, null, null) > 0
            }
        } catch (_: Exception) {
            runCatching { resolver.delete(uri, null, null) > 0 }.getOrDefault(false)
        }
}

private class SafBackupImportSource(
    private val resolver: ContentResolver,
    private val uri: Uri
) : BackupImportSource {
    override val displayName: String? get() = resolver.queryDisplayName(uri)

    override fun openInputStream(): InputStream =
        resolver.openInputStream(uri)
            ?: throw IOException("Could not open backup file for reading")
}

private fun ContentResolver.queryDisplayName(uri: Uri): String? =
    runCatching {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }
    }.getOrNull()
