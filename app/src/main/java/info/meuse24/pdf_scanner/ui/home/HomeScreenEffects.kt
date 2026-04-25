package info.meuse24.pdf_scanner.ui.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import info.meuse24.pdf_scanner.ui.components.LocalAppSnackbarHostState
import info.meuse24.pdf_scanner.ui.entry.AppEntryAction
import kotlinx.coroutines.withTimeoutOrNull

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
            // Home is the actual consumer for non-navigation app-entry actions.
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
    haptic: HapticFeedback,
    onConsumed: () -> Unit
) {
    val snackbarHostState = LocalAppSnackbarHostState.current
    LaunchedEffect(success) {
        val message = success ?: return@LaunchedEffect
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        snackbarHostState?.showSnackbar(message)
        onConsumed()
    }
}

@Composable
internal fun HandleHomeTrashEffect(
    trashMessage: String?,
    trashUndoSnackbarSeconds: Int,
    snackbarHostState: SnackbarHostState,
    undoLabel: String,
    onUndo: () -> Unit,
    onConsumed: () -> Unit
) {
    LaunchedEffect(trashMessage, trashUndoSnackbarSeconds) {
        val message = trashMessage ?: return@LaunchedEffect
        val timeoutMillis = trashUndoSnackbarSeconds.coerceAtLeast(1) * 1_000L
        var result: SnackbarResult? = null
        withTimeoutOrNull(timeoutMillis) {
            result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoLabel
            )
        } ?: snackbarHostState.currentSnackbarData?.dismiss()
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
internal fun HandleHomePlayReviewEffect(
    playReviewRequestId: Long,
    context: Context,
    onLaunchReview: (Activity, () -> Unit) -> Unit,
    onConsumed: () -> Unit
) {
    LaunchedEffect(playReviewRequestId) {
        if (playReviewRequestId == 0L) return@LaunchedEffect
        val activity = context.findActivity()
        if (activity == null) {
            onConsumed()
            return@LaunchedEffect
        }
        onLaunchReview(activity, onConsumed)
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
