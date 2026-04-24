package info.meuse24.pdf_scanner.ui.entry

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface AppEntryAction {
    data object ScanNew : AppEntryAction
    data object ImportImages : AppEntryAction
    data object OpenTrash : AppEntryAction
    data class SharePdf(val uri: Uri) : AppEntryAction
    data class ShareImages(val uris: List<Uri>) : AppEntryAction
}

object AppEntryActionCodec {
    const val EXTRA_KEY = "info.meuse24.pdf_scanner.ACTION"
    const val VALUE_SCAN_NEW = "scan_new"
    const val VALUE_IMPORT_IMAGES = "import_images"
    const val VALUE_OPEN_TRASH = "open_trash"

    fun buildLaunchIntent(
        context: Context,
        actionValue: String
    ): Intent = Intent(context, info.meuse24.pdf_scanner.MainActivity::class.java).apply {
        putExtra(EXTRA_KEY, actionValue)
    }

    fun fromIntent(intent: Intent?): AppEntryAction? = fromIntent(context = null, intent = intent)

    fun fromIntent(context: Context?, intent: Intent?): AppEntryAction? {
        intent ?: return null

        intent.getStringExtra(EXTRA_KEY)?.let { value ->
            return when (value) {
                VALUE_SCAN_NEW -> AppEntryAction.ScanNew
                VALUE_IMPORT_IMAGES -> AppEntryAction.ImportImages
                VALUE_OPEN_TRASH -> AppEntryAction.OpenTrash
                else -> null
            }
        }

        return when (intent.action) {
            Intent.ACTION_VIEW -> parseView(context, intent)
            Intent.ACTION_SEND -> parseSingleShare(context, intent)
            Intent.ACTION_SEND_MULTIPLE -> parseMultipleShare(context, intent)
            else -> null
        }
    }

    private fun parseView(context: Context?, intent: Intent): AppEntryAction? {
        val uri = intent.data ?: intent.firstClipDataUri() ?: return null
        return when {
            intent.matchesPdf(context, uri) -> AppEntryAction.SharePdf(uri)
            intent.matchesImage(context, uri) -> AppEntryAction.ShareImages(listOf(uri))
            else -> null
        }
    }

    private fun parseSingleShare(context: Context?, intent: Intent): AppEntryAction? {
        val uri = intent.sharedUris().firstOrNull() ?: return null
        return when {
            intent.matchesPdf(context, uri) -> AppEntryAction.SharePdf(uri)
            intent.matchesImage(context, uri) -> AppEntryAction.ShareImages(listOf(uri))
            else -> null
        }
    }

    private fun parseMultipleShare(context: Context?, intent: Intent): AppEntryAction? {
        val uris = intent.sharedUris()
        if (uris.isEmpty()) return null

        // Multiple-share import intentionally supports images only.
        // Multiple PDFs are not auto-merged implicitly.
        return if (uris.all { uri -> intent.matchesImage(context, uri) }) {
            AppEntryAction.ShareImages(uris)
        } else {
            null
        }
    }
}

class AppEntryActionViewModel : ViewModel() {
    private val _pendingActions = MutableStateFlow<List<AppEntryAction>>(emptyList())
    val pendingActions: StateFlow<List<AppEntryAction>> = _pendingActions.asStateFlow()

    fun offer(action: AppEntryAction) {
        _pendingActions.update { current -> current + action }
    }

    fun consume(action: AppEntryAction) {
        _pendingActions.update { current ->
            val index = current.indexOf(action)
            if (index < 0) {
                current
            } else {
                current.toMutableList().apply { removeAt(index) }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun Intent.parcelableExtraCompat(name: String): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, Uri::class.java)
    } else {
        getParcelableExtra(name) as? Uri
    }

@Suppress("DEPRECATION")
private fun Intent.parcelableArrayListExtraCompat(name: String): ArrayList<Uri>? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(name, Uri::class.java)
    } else {
        getParcelableArrayListExtra(name)
    }

private fun Intent.sharedUris(): List<Uri> {
    val many = parcelableArrayListExtraCompat(Intent.EXTRA_STREAM)
        ?.filterNotNull()
        .orEmpty()
    if (many.isNotEmpty()) return many

    val single = parcelableExtraCompat(Intent.EXTRA_STREAM)
    if (single != null) return listOf(single)

    val clipUris = clipData
        ?.let { clip ->
            buildList {
                repeat(clip.itemCount) { index ->
                    clip.getItemAt(index).uri?.let(::add)
                }
            }
        }
        .orEmpty()
    if (clipUris.isNotEmpty()) return clipUris

    return listOfNotNull(data)
}

private fun Intent.firstClipDataUri(): Uri? = clipData
    ?.let { clip ->
        (0 until clip.itemCount)
            .asSequence()
            .mapNotNull { index -> clip.getItemAt(index).uri }
            .firstOrNull()
    }

private fun Intent.matchesPdf(context: Context?, uri: Uri): Boolean = resolvedMimeTypes(context, uri).any {
    it.equals("application/pdf", ignoreCase = true) ||
        it.equals("application/x-pdf", ignoreCase = true)
} || uri.looksLikePdf()

private fun Intent.matchesImage(context: Context?, uri: Uri): Boolean = resolvedMimeTypes(context, uri).any {
    it.startsWith("image/", ignoreCase = true)
} || uri.looksLikeImage()

private fun Intent.resolvedMimeTypes(context: Context?, uri: Uri): Set<String> = buildSet {
    type?.let(::add)
    clipData?.description?.let { description ->
        repeat(description.mimeTypeCount) { index ->
            description.getMimeType(index)?.let(::add)
        }
    }
    context?.contentResolver?.getType(uri)?.let(::add)
}

private fun Uri.looksLikePdf(): Boolean {
    val extension = lastPathSegment
        ?.substringAfterLast('.', "")
        ?.lowercase()
    return extension == "pdf"
}

private fun Uri.looksLikeImage(): Boolean {
    val extension = lastPathSegment
        ?.substringAfterLast('.', "")
        ?.lowercase()
    return extension in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif", "tif", "tiff")
}
