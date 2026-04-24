package info.meuse24.pdf_scanner.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BrandingWatermark
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NoEncryption
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.Document
import java.util.Locale

sealed interface ScanAction {
    data object Split : ScanAction
    data object Reorder : ScanAction
    data object Rotate : ScanAction
    data object DeletePages : ScanAction
    data object ExtractPages : ScanAction
    data object AppendPages : ScanAction
    data object DuplicatePages : ScanAction
    data object PageNumbers : ScanAction
    data object TextWatermark : ScanAction
    data object CompressPdf : ScanAction
    data object ProtectPdf : ScanAction
    data object UnlockPdf : ScanAction
    data object Signature : ScanAction
    data object RemoveTextLayer : ScanAction
    data object RemovePassword : ScanAction
    data object RestrictUsage : ScanAction
    data object ExportAsJpg : ScanAction
    data object Annotate : ScanAction
    data object Redact : ScanAction
    data object Rename : ScanAction
    data object Grayscale : ScanAction
    data object PdfMetadata : ScanAction
    data object ScanQrCodes : ScanAction
    data object ScanBusinessCard : ScanAction
    data object Print : ScanAction
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentEditSheet(
    record: Document,
    onAction: (ScanAction) -> Unit,
    showRenameAction: Boolean = true,
    showPrintAction: Boolean = true,
    showExportAsJpgAction: Boolean = true
) {
    val context = LocalContext.current
    val notEncrypted = !record.isEncrypted
    val multiPage = record.pageCount >= 2
    val sizeStr = remember(record.fileSize, context) {
        Formatter.formatShortFileSize(context, record.fileSize.coerceAtLeast(0L))
    }
    val showMetadata = true
    val showRename = showRenameAction
    val showPrint = showPrintAction && notEncrypted
    val showExportAsJpg = showExportAsJpgAction && notEncrypted
    val showReorder = notEncrypted && multiPage
    val showRotate = notEncrypted
    val showAppend = notEncrypted
    val showExtractPages = notEncrypted && multiPage
    val showDuplicatePages = true
    val showSplit = notEncrypted && multiPage
    val showDeletePages = notEncrypted && multiPage
    val showAnnotate = notEncrypted
    val showSignature = notEncrypted
    val showPageNumbers = notEncrypted
    val showTextWatermark = notEncrypted
    val showRedact = notEncrypted
    val showQrScan = notEncrypted
    val showBusinessCard = notEncrypted && record.pageCount >= 1
    val showRemoveTextLayer = record.isSearchable && notEncrypted
    val showGrayscale = notEncrypted
    val showCompress = notEncrypted && !record.isSearchable
    val showProtect = notEncrypted
    val showRestrictUsage = notEncrypted
    val showUnlock = record.isEncrypted
    val showRemovePassword = record.isEncrypted

    val showQuickSection = showRename || showMetadata || showPrint || showExportAsJpg
    val showPagesSection = showReorder || showRotate || showAppend || showExtractPages ||
        showDuplicatePages || showSplit || showDeletePages
    val showEditSection = showAnnotate || showSignature || showPageNumbers || showTextWatermark || showRedact
    val showAnalyseSection = showQrScan || showBusinessCard || showRemoveTextLayer
    val showExportSection = showGrayscale || showCompress
    val showSecuritySection = showProtect || showRestrictUsage || showUnlock || showRemovePassword

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        stickyHeader {
            SheetHeader(
                filename = record.filename,
                meta = stringResource(R.string.sheet_header_meta, record.pageCount, sizeStr)
            )
        }

        if (showQuickSection) {
            item {
                SheetSection(R.string.sheet_section_document)
            }
        }
        if (showRename) {
            item {
                SheetItem(Icons.Default.DriveFileRenameOutline, R.string.action_rename, true) {
                    onAction(ScanAction.Rename)
                }
            }
        }
        if (showMetadata) {
            item {
                SheetItem(Icons.Default.Info, R.string.action_pdf_metadata, true) {
                    onAction(ScanAction.PdfMetadata)
                }
            }
        }
        if (showPrint) {
            item {
                SheetItem(Icons.Default.Print, R.string.action_print_pdf, true) {
                    onAction(ScanAction.Print)
                }
            }
        }
        if (showExportAsJpg) {
            item {
                SheetItem(Icons.Default.Image, R.string.action_export_as_jpg, true) {
                    onAction(ScanAction.ExportAsJpg)
                }
            }
        }

        if (showPagesSection) {
            item {
                SheetSection(R.string.sheet_section_pages)
            }
        }
        if (showReorder) {
            item {
                SheetItem(Icons.Default.SwapVert, R.string.action_reorder, true) {
                    onAction(ScanAction.Reorder)
                }
            }
        }
        if (showRotate) {
            item {
                SheetItem(Icons.AutoMirrored.Filled.RotateRight, R.string.action_rotate, true) {
                    onAction(ScanAction.Rotate)
                }
            }
        }
        if (showAppend) {
            item {
                SheetItem(Icons.Default.PostAdd, R.string.action_append_pages, true) {
                    onAction(ScanAction.AppendPages)
                }
            }
        }
        if (showExtractPages) {
            item {
                SheetItem(Icons.Default.PictureAsPdf, R.string.action_extract_pages, true) {
                    onAction(ScanAction.ExtractPages)
                }
            }
        }
        if (showDuplicatePages) {
            item {
                SheetItem(Icons.Default.ContentCopy, R.string.action_duplicate_pages, true) {
                    onAction(ScanAction.DuplicatePages)
                }
            }
        }
        if (showSplit) {
            item {
                SheetItem(Icons.Default.ContentCut, R.string.action_split, true) {
                    onAction(ScanAction.Split)
                }
            }
        }
        if (showDeletePages) {
            item {
                SheetItem(Icons.Default.Delete, R.string.action_delete_pages, true) {
                    onAction(ScanAction.DeletePages)
                }
            }
        }

        if (showEditSection) {
            item {
                SheetSection(R.string.sheet_section_edit)
            }
        }
        if (showAnnotate) {
            item {
                SheetItem(Icons.Default.BorderColor, R.string.action_annotate_pdf, true) {
                    onAction(ScanAction.Annotate)
                }
            }
        }
        if (showSignature) {
            item {
                SheetItem(Icons.Default.Draw, R.string.action_sign_pdf, true) {
                    onAction(ScanAction.Signature)
                }
            }
        }
        if (showPageNumbers) {
            item {
                SheetItem(Icons.Default.FormatListNumbered, R.string.action_page_numbers, true) {
                    onAction(ScanAction.PageNumbers)
                }
            }
        }
        if (showTextWatermark) {
            item {
                SheetItem(Icons.AutoMirrored.Filled.BrandingWatermark, R.string.action_text_watermark, true) {
                    onAction(ScanAction.TextWatermark)
                }
            }
        }
        if (showRedact) {
            item {
                SheetItem(Icons.Default.Block, R.string.action_redact_pdf, true) {
                    onAction(ScanAction.Redact)
                }
            }
        }

        if (showAnalyseSection) {
            item {
                SheetSection(R.string.sheet_section_analyse)
            }
        }
        if (showQrScan) {
            item {
                SheetItem(Icons.Default.QrCodeScanner, R.string.action_scan_qr_codes, true) {
                    onAction(ScanAction.ScanQrCodes)
                }
            }
        }
        if (showBusinessCard) {
            item {
                SheetItem(Icons.Default.ContactPage, R.string.action_scan_business_card, true) {
                    onAction(ScanAction.ScanBusinessCard)
                }
            }
        }
        if (showRemoveTextLayer) {
            item {
                SheetItem(Icons.Default.FindInPage, R.string.action_remove_text_layer, true) {
                    onAction(ScanAction.RemoveTextLayer)
                }
            }
        }

        if (showExportSection) {
            item {
                SheetSection(R.string.sheet_section_export)
            }
        }
        if (showGrayscale) {
            item {
                SheetItem(Icons.Default.InvertColors, R.string.action_grayscale_pdf, true) {
                    onAction(ScanAction.Grayscale)
                }
            }
        }
        if (showCompress) {
            item {
                SheetItem(Icons.Default.Compress, R.string.action_compress_pdf, true) {
                    onAction(ScanAction.CompressPdf)
                }
            }
        }

        if (showSecuritySection) {
            item {
                SheetSection(R.string.sheet_section_security)
            }
        }
        if (showProtect) {
            item {
                SheetItem(Icons.Default.Lock, R.string.action_protect_pdf, true) {
                    onAction(ScanAction.ProtectPdf)
                }
            }
        }
        if (showRestrictUsage) {
            item {
                SheetItem(Icons.Default.AdminPanelSettings, R.string.action_restrict_usage, true) {
                    onAction(ScanAction.RestrictUsage)
                }
            }
        }
        if (showUnlock) {
            item {
                SheetItem(Icons.Default.LockOpen, R.string.action_unlock_pdf, true) {
                    onAction(ScanAction.UnlockPdf)
                }
            }
        }
        if (showRemovePassword) {
            item {
                SheetItem(Icons.Default.NoEncryption, R.string.action_remove_password, true) {
                    onAction(ScanAction.RemovePassword)
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SheetHeader(
    filename: String,
    meta: String
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = filename,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun SheetSection(titleRes: Int) {
    Text(
        text = stringResource(titleRes).uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SheetItem(
    icon: ImageVector,
    textRes: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val contentColor = MaterialTheme.colorScheme.onSurface.let {
        if (enabled) it else it.copy(alpha = 0.38f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor
        )
    }
}

