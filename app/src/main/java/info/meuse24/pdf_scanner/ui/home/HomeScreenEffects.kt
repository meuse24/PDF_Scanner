package info.meuse24.pdf_scanner.ui.home

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import info.meuse24.pdf_scanner.ui.entry.AppEntryAction

@Composable
internal fun HandleHomeAddActionEffect(
    addActionTrigger: Boolean,
    onAddActionTriggered: () -> Unit,
    onOpenSheet: () -> Unit
) {
    LaunchedEffect(addActionTrigger) {
        if (addActionTrigger) {
            onOpenSheet()
            onAddActionTriggered()
        }
    }
}

@Composable
internal fun HandleHomeAppEntryActionEffect(
    pendingAppEntryAction: AppEntryAction?,
    onConsume: (AppEntryAction) -> Unit,
    onLaunchScanner: () -> Unit,
    onLaunchImportImages: () -> Unit,
    onImportSharedPdf: (Uri) -> Unit,
    onImportSharedImages: (List<Uri>) -> Unit
) {
    LaunchedEffect(pendingAppEntryAction) {
        when (val action = pendingAppEntryAction) {
            AppEntryAction.ScanNew -> {
                onConsume(action)
                onLaunchScanner()
            }

            AppEntryAction.ImportImages -> {
                onConsume(action)
                onLaunchImportImages()
            }

            is AppEntryAction.SharePdf -> {
                onConsume(action)
                onImportSharedPdf(action.uri)
            }

            is AppEntryAction.ShareImages -> {
                onConsume(action)
                onImportSharedImages(action.uris)
            }

            AppEntryAction.OpenTrash,
            null -> Unit
        }
    }
}

@Composable
internal fun HandleHomeSuccessEffect(
    success: String?,
    context: Context,
    haptic: HapticFeedback,
    onConsumed: () -> Unit
) {
    LaunchedEffect(success) {
        val message = success ?: return@LaunchedEffect
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        onConsumed()
    }
}

@Composable
internal fun HandleHomeTrashEffect(
    trashMessage: String?,
    snackbarHostState: SnackbarHostState,
    undoLabel: String,
    onUndo: () -> Unit,
    onConsumed: () -> Unit
) {
    LaunchedEffect(trashMessage) {
        val message = trashMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = undoLabel
        )
        if (result == SnackbarResult.ActionPerformed) {
            onUndo()
        }
        onConsumed()
    }
}

@Composable
internal fun HandleHomeOcrReviewEffect(
    ocrReviewRequestId: Long?,
    onNavigateToOcrReview: (Long) -> Unit,
    onConsumed: () -> Unit
) {
    LaunchedEffect(ocrReviewRequestId) {
        val scanId = ocrReviewRequestId ?: return@LaunchedEffect
        onNavigateToOcrReview(scanId)
        onConsumed()
    }
}

@Composable
internal fun HandleHomeListHaptics(
    listState: LazyListState,
    haptic: HapticFeedback
) {
    LaunchedEffect(listState) {
        var initialized = false
        snapshotFlow { listState.firstVisibleItemIndex }.collect {
            if (initialized) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            initialized = true
        }
    }
}
