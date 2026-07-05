package info.meuse24.pdf_scanner.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Eine Datei → `ACTION_SEND`, mehrere Dateien → `ACTION_SEND_MULTIPLE` (analog [buildPdfShareIntent]). */
fun buildCsvShareIntent(context: Context, files: List<File>, mimeType: String): Intent? {
    val uris = ArrayList(
        files.map { file ->
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    )
    if (uris.isEmpty()) return null
    return if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uris[0])
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
