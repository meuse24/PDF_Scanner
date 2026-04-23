package info.meuse24.pdf_scanner.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BrandingWatermark
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
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
import info.meuse24.pdf_scanner.data.local.ScanRecord
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
    data object Print : ScanAction
}

@Composable
fun DocumentEditSheet(
    record: ScanRecord,
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = record.filename,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.sheet_header_meta, record.pageCount, sizeStr),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        HorizontalDivider()

        SheetSection(R.string.sheet_section_document)
        if (showRenameAction) {
            SheetItem(Icons.Default.DriveFileRenameOutline, R.string.action_rename, true) {
                onAction(ScanAction.Rename)
            }
        }
        SheetItem(Icons.Default.Info, R.string.action_pdf_metadata, true) {
            onAction(ScanAction.PdfMetadata)
        }

        SheetSection(R.string.sheet_section_pages)
        SheetItem(Icons.Default.SwapVert, R.string.action_reorder, notEncrypted && multiPage) {
            onAction(ScanAction.Reorder)
        }
        SheetItem(Icons.AutoMirrored.Filled.RotateRight, R.string.action_rotate, notEncrypted) {
            onAction(ScanAction.Rotate)
        }
        SheetItem(Icons.Default.ContentCut, R.string.action_split, notEncrypted && multiPage) {
            onAction(ScanAction.Split)
        }
        SheetItem(Icons.Default.PictureAsPdf, R.string.action_extract_pages, notEncrypted && multiPage) {
            onAction(ScanAction.ExtractPages)
        }
        SheetItem(Icons.Default.PostAdd, R.string.action_append_pages, notEncrypted) {
            onAction(ScanAction.AppendPages)
        }
        SheetItem(Icons.Default.ContentCopy, R.string.action_duplicate_pages, true) {
            onAction(ScanAction.DuplicatePages)
        }
        SheetItem(Icons.Default.Delete, R.string.action_delete_pages, notEncrypted && multiPage) {
            onAction(ScanAction.DeletePages)
        }

        SheetSection(R.string.sheet_section_edit)
        SheetItem(Icons.Default.BorderColor, R.string.action_annotate_pdf, notEncrypted) {
            onAction(ScanAction.Annotate)
        }
        SheetItem(Icons.Default.Draw, R.string.action_sign_pdf, notEncrypted) {
            onAction(ScanAction.Signature)
        }
        SheetItem(Icons.Default.FormatListNumbered, R.string.action_page_numbers, notEncrypted) {
            onAction(ScanAction.PageNumbers)
        }
        SheetItem(Icons.AutoMirrored.Filled.BrandingWatermark, R.string.action_text_watermark, notEncrypted) {
            onAction(ScanAction.TextWatermark)
        }
        SheetItem(Icons.Default.Block, R.string.action_redact_pdf, notEncrypted) {
            onAction(ScanAction.Redact)
        }

        SheetSection(R.string.sheet_section_analyse)
        SheetItem(Icons.Default.QrCodeScanner, R.string.action_scan_qr_codes, notEncrypted) {
            onAction(ScanAction.ScanQrCodes)
        }
        if (record.isSearchable && notEncrypted) {
            SheetItem(Icons.Default.FindInPage, R.string.action_remove_text_layer, true) {
                onAction(ScanAction.RemoveTextLayer)
            }
        }

        SheetSection(R.string.sheet_section_export)
        if (showPrintAction) {
            SheetItem(Icons.Default.Print, R.string.action_print_pdf, notEncrypted) {
                onAction(ScanAction.Print)
            }
        }
        if (showExportAsJpgAction) {
            SheetItem(Icons.Default.Image, R.string.action_export_as_jpg, notEncrypted) {
                onAction(ScanAction.ExportAsJpg)
            }
        }
        SheetItem(Icons.Default.InvertColors, R.string.action_grayscale_pdf, notEncrypted) {
            onAction(ScanAction.Grayscale)
        }
        SheetItem(Icons.Default.Compress, R.string.action_compress_pdf, notEncrypted && !record.isSearchable) {
            onAction(ScanAction.CompressPdf)
        }

        SheetSection(R.string.sheet_section_security)
        SheetItem(Icons.Default.Lock, R.string.action_protect_pdf, notEncrypted) {
            onAction(ScanAction.ProtectPdf)
        }
        SheetItem(Icons.Default.AdminPanelSettings, R.string.action_restrict_usage, notEncrypted) {
            onAction(ScanAction.RestrictUsage)
        }
        SheetItem(Icons.Default.LockOpen, R.string.action_unlock_pdf, record.isEncrypted) {
            onAction(ScanAction.UnlockPdf)
        }
        SheetItem(Icons.Default.NoEncryption, R.string.action_remove_password, record.isEncrypted) {
            onAction(ScanAction.RemovePassword)
        }

        Spacer(Modifier.height(8.dp))
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
