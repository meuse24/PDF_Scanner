package info.meuse24.pdf_scanner.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import info.meuse24.pdf_scanner.domain.model.Document
import java.io.File

fun pdfContentUri(context: Context, record: Document): Uri? {
    val file = File(record.filepath)
    if (!file.exists()) return null
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}

fun buildPdfViewIntent(context: Context, record: Document): Intent? {
    val uri = pdfContentUri(context, record) ?: return null
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

fun buildPdfShareIntent(context: Context, records: List<Document>): Intent? {
    val uris = ArrayList(records.mapNotNull { pdfContentUri(context, it) })
    if (uris.isEmpty()) return null
    return if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uris[0])
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/pdf"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

fun openPdfExternally(context: Context, record: Document): Boolean {
    val intent = buildPdfViewIntent(context, record) ?: return false
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

