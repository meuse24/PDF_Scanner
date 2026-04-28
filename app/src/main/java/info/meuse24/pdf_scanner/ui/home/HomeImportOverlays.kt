package info.meuse24.pdf_scanner.ui.home

import android.content.ClipData
import android.content.Intent
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.ui.components.LocalAppSnackbarHostState
import info.meuse24.pdf_scanner.ui.home.components.HomeAddDocumentSheet
import info.meuse24.pdf_scanner.ui.home.components.HomeOcrResultSheet
import info.meuse24.pdf_scanner.ui.home.components.HomeSaveImportDialog
import kotlinx.coroutines.launch

@Composable
internal fun HomeImportOverlays(
    showAddSheet: Boolean,
    onShowAddSheetChange: (Boolean) -> Unit,
    showSaveDialog: Boolean,
    onShowSaveDialogChange: (Boolean) -> Unit,
    pendingImport: PendingImport?,
    onPendingImportChange: (PendingImport?) -> Unit,
    filenameInput: String,
    onFilenameInputChange: (String) -> Unit,
    makeSearchable: Boolean,
    onMakeSearchableChange: (Boolean) -> Unit,
    selectedLang: String,
    onSelectedLangChange: (String) -> Unit,
    langMenuExpanded: Boolean,
    onLangMenuExpandedChange: (Boolean) -> Unit,
    settings: info.meuse24.pdf_scanner.domain.model.AppSettings,
    ocrLanguages: List<Pair<String, String>>,
    ocrText: String?,
    clipboard: Clipboard,
    haptic: HapticFeedback,
    resources: Resources,
    onLaunchScanner: () -> Unit,
    onLaunchImportPdf: () -> Unit,
    onLaunchImportImages: () -> Unit,
    onClearOcrText: () -> Unit,
    viewModel: HomeViewModel
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = LocalAppSnackbarHostState.current
    val actionShareTextLabel = stringResource(R.string.action_share_text)
    val ocrCopiedMessage = stringResource(R.string.ocr_copied)
    val context = androidx.compose.ui.platform.LocalContext.current

    if (showAddSheet) {
        HomeAddDocumentSheet(
            onDismiss = { onShowAddSheetChange(false) },
            onScanClick = {
                onShowAddSheetChange(false)
                onLaunchScanner()
            },
            onImportClick = {
                onShowAddSheetChange(false)
                onLaunchImportPdf()
            },
            onImagesToPdfClick = {
                onShowAddSheetChange(false)
                onLaunchImportImages()
            }
        )
    }

    if (ocrText != null) {
        HomeOcrResultSheet(
            text = ocrText,
            onDismiss = onClearOcrText,
            onShare = {
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, ocrText)
                        },
                        actionShareTextLabel
                    )
                )
            },
            onCopy = {
                coroutineScope.launch {
                    clipboard.setClipEntry(ClipData.newPlainText("", ocrText).toClipEntry())
                    snackbarHostState?.showSnackbar(ocrCopiedMessage)
                }
            }
        )
    }

    if (showSaveDialog) {
        val currentImport = pendingImport
        HomeSaveImportDialog(
            pendingImport = currentImport,
            filenameInput = filenameInput,
            makeSearchable = makeSearchable,
            selectedLanguage = selectedLang,
            languageMenuExpanded = langMenuExpanded,
            ocrLanguages = ocrLanguages,
            onFilenameChange = onFilenameInputChange,
            onMakeSearchableChange = onMakeSearchableChange,
            onLanguageMenuExpandedChange = onLangMenuExpandedChange,
            onLanguageSelected = {
                onSelectedLangChange(it)
                onLangMenuExpandedChange(false)
            },
            onConfirm = {
                when (val import = currentImport) {
                    is PendingImport.Scan -> {
                        val pdf = import.result.pdf
                        if (filenameInput.isNotBlank() && pdf != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.saveScan(
                                pdf.uri,
                                pdf.pageCount,
                                filenameInput.trim(),
                                import.result.pages?.firstOrNull()?.imageUri,
                                makeSearchable,
                                selectedLang
                            )
                            onShowSaveDialogChange(false)
                            onPendingImportChange(null)
                            onMakeSearchableChange(settings.defaultMakeSearchable)
                            onSelectedLangChange(settings.defaultOcrLanguage)
                        }
                    }

                    is PendingImport.File -> {
                        if (filenameInput.isNotBlank()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.importFile(import.uri, filenameInput.trim())
                            onShowSaveDialogChange(false)
                            onPendingImportChange(null)
                            onMakeSearchableChange(settings.defaultMakeSearchable)
                            onSelectedLangChange(settings.defaultOcrLanguage)
                        }
                    }

                    null -> Unit
                }
            },
            onDismiss = {
                onShowSaveDialogChange(false)
                onPendingImportChange(null)
                onMakeSearchableChange(settings.defaultMakeSearchable)
                onSelectedLangChange(settings.defaultOcrLanguage)
            }
        )
    }
}
