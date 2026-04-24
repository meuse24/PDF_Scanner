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

    fun fromIntent(intent: Intent?): AppEntryAction? {
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
            Intent.ACTION_SEND -> parseSingleShare(intent)
            Intent.ACTION_SEND_MULTIPLE -> parseMultipleShare(intent)
            else -> null
        }
    }

    private fun parseSingleShare(intent: Intent): AppEntryAction? {
        val uri = intent.parcelableExtraCompat(Intent.EXTRA_STREAM) ?: return null
        return when {
            intent.type == "application/pdf" -> AppEntryAction.SharePdf(uri)
            intent.type?.startsWith("image/") == true -> AppEntryAction.ShareImages(listOf(uri))
            else -> null
        }
    }

    private fun parseMultipleShare(intent: Intent): AppEntryAction? {
        if (intent.type?.startsWith("image/") != true) return null
        val uris = intent.parcelableArrayListExtraCompat(Intent.EXTRA_STREAM)
            ?.filterNotNull()
            .orEmpty()
        return if (uris.isEmpty()) null else AppEntryAction.ShareImages(uris)
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
