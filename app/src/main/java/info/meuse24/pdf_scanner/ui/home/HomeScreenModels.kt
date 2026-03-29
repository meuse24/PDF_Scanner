package info.meuse24.pdf_scanner.ui.home

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import info.meuse24.pdf_scanner.R
import java.util.Locale

internal sealed interface PendingImport {
    data class Scan(val result: GmsDocumentScanningResult) : PendingImport
    data class File(val uri: Uri, val originalName: String) : PendingImport
}

internal fun suggestedFilenameFromDisplayName(displayName: String): String {
    val trimmed = displayName.trim()
    if (trimmed.isBlank()) return ""
    return if (trimmed.lowercase(Locale.ROOT).endsWith(".pdf")) {
        trimmed.dropLast(4).trim()
    } else {
        trimmed
    }
}

internal fun queryDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (columnIndex >= 0 && cursor.moveToFirst()) cursor.getString(columnIndex) else null
    }
}

internal fun sortOrderLabel(sortOrder: SortOrder): Int = when (sortOrder) {
    SortOrder.ByDate -> R.string.sort_by_date
    SortOrder.ByName -> R.string.sort_by_name
    SortOrder.BySize -> R.string.sort_by_size
}
