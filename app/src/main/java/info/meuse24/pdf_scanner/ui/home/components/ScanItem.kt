package info.meuse24.pdf_scanner.ui.home.components

import android.graphics.BitmapFactory
import android.text.format.Formatter
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BrandingWatermark
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.NoEncryption
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.ScanRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Aktionen aus dem Kontextmenü eines Scan-Eintrags. */
sealed interface ScanAction {
    data object Split           : ScanAction
    data object Reorder         : ScanAction
    data object Rotate          : ScanAction
    data object DeletePages     : ScanAction
    data object ExtractPages    : ScanAction
    data object DuplicatePages  : ScanAction
    data object PageNumbers     : ScanAction
    data object TextWatermark   : ScanAction
    data object CompressPdf     : ScanAction
    data object ProtectPdf      : ScanAction
    data object UnlockPdf       : ScanAction
    data object Signature       : ScanAction
    data object RemoveTextLayer  : ScanAction
    data object RemovePassword   : ScanAction
    data object RestrictUsage    : ScanAction
    data object ExportAsJpg      : ScanAction
    data object Annotate         : ScanAction
    data object Rename           : ScanAction
    data object Grayscale        : ScanAction
    data object PdfMetadata      : ScanAction
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScanItem(
    record:           ScanRecord,
    isSelected:       Boolean,
    inSelectionMode:  Boolean  = false,
    modifier:         Modifier = Modifier,
    onClick:          () -> Unit,
    onCheckboxToggle: () -> Unit,
    onAction:         (ScanAction) -> Unit = {}
) {
    val context = LocalContext.current
    var sheetVisible by remember { mutableStateOf(false) }

    val dateStr = remember(record.timestamp) {
        DateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.SHORT,
            Locale.getDefault()
        ).format(Date(record.timestamp))
    }
    val sizeStr = remember(record.fileSize, context) {
        Formatter.formatShortFileSize(context, record.fileSize.coerceAtLeast(0L))
    }
    val subtitle = stringResource(R.string.scan_item_subtitle, dateStr, record.pageCount, sizeStr)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue   = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label         = "cardScale"
    )

    val thumbnail by produceState<ImageBitmap?>(initialValue = null, key1 = record.thumbnailPath) {
        value = withContext(Dispatchers.IO) {
            val path = record.thumbnailPath ?: return@withContext null
            val file = File(path)
            if (!file.exists()) return@withContext null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            var sample = 1
            while (opts.outWidth / (sample * 2) >= 160 && opts.outHeight / (sample * 2) >= 160) {
                sample *= 2
            }
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )?.asImageBitmap()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(cardScale)
            .clickable(
                interactionSource = interactionSource,
                indication        = LocalIndication.current,
                onClick           = onClick
            ),
        shape     = RoundedCornerShape(28.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                             else            MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            // Dateiname — volle Breite
            Text(
                record.filename,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            // Thumbnail · Metadaten · Badge · Aktionen
            Row(verticalAlignment = Alignment.CenterVertically) {
                val thumb = thumbnail
                if (thumb != null) {
                    Image(
                        painter            = BitmapPainter(thumb),
                        contentDescription = stringResource(R.string.cd_pdf_document),
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = stringResource(R.string.cd_pdf_document),
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(40.dp)
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    if (record.isSearchable) {
                        Spacer(Modifier.height(2.dp))
                        Badge(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor   = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Text(
                                stringResource(R.string.searchable_badge),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    val tagList = record.tags
                        ?.split(",")
                        ?.filter { it.isNotBlank() }
                        ?.take(3)
                    if (!tagList.isNullOrEmpty()) {
                        Spacer(Modifier.height(3.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            tagList.forEach { tagKey ->
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor   = MaterialTheme.colorScheme.onTertiaryContainer
                                ) {
                                    Text(tagLabel(tagKey), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                if (!inSelectionMode && record.pageCount >= 1) {
                    IconButton(onClick = { sheetVisible = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                }

                Checkbox(
                    checked         = isSelected,
                    onCheckedChange = { onCheckboxToggle() }
                )
            } // Row
        } // Column
    } // Card

    // ── Action Bottom Sheet ──────────────────────────────────────────────────
    if (sheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            PdfActionSheet(
                record   = record,
                onAction = { action ->
                    sheetVisible = false
                    onAction(action)
                }
            )
        }
    }
}

// ── Bottom Sheet Inhalt ──────────────────────────────────────────────────────

@Composable
private fun PdfActionSheet(
    record:   ScanRecord,
    onAction: (ScanAction) -> Unit
) {
    val context      = LocalContext.current
    val notEncrypted = !record.isEncrypted
    val multiPage    = record.pageCount >= 2
    val sizeStr      = remember(record.fileSize, context) {
        Formatter.formatShortFileSize(context, record.fileSize.coerceAtLeast(0L))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        // Dokument-Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text       = record.filename,
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text  = stringResource(R.string.sheet_header_meta, record.pageCount, sizeStr),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        HorizontalDivider()

        // ── BEARBEITEN ───────────────────────────────────────────────────────
        SheetSection(R.string.sheet_section_edit)
        SheetItem(Icons.Default.DriveFileRenameOutline, R.string.action_rename, true)      { onAction(ScanAction.Rename) }
        SheetItem(Icons.Default.Info,                   R.string.action_pdf_metadata, true) { onAction(ScanAction.PdfMetadata) }
        SheetItem(Icons.Default.BorderColor,            R.string.action_annotate_pdf, notEncrypted) { onAction(ScanAction.Annotate) }
        SheetItem(Icons.Default.Draw,        R.string.action_sign_pdf,      notEncrypted) { onAction(ScanAction.Signature) }

        // ── SEITEN ───────────────────────────────────────────────────────────
        SheetSection(R.string.sheet_section_pages)
        SheetItem(Icons.AutoMirrored.Filled.RotateRight, R.string.action_rotate,          notEncrypted)              { onAction(ScanAction.Rotate) }
        SheetItem(Icons.Default.SwapVert,                R.string.action_reorder,         notEncrypted && multiPage) { onAction(ScanAction.Reorder) }
        SheetItem(Icons.Default.ContentCut,              R.string.action_split,           notEncrypted && multiPage) { onAction(ScanAction.Split) }
        SheetItem(Icons.Default.Delete,                  R.string.action_delete_pages,    notEncrypted && multiPage) { onAction(ScanAction.DeletePages) }
        SheetItem(Icons.Default.PictureAsPdf,            R.string.action_extract_pages,   notEncrypted && multiPage) { onAction(ScanAction.ExtractPages) }
        SheetItem(Icons.Default.ContentCopy,             R.string.action_duplicate_pages, true)                      { onAction(ScanAction.DuplicatePages) }

        // ── AUSGABE ──────────────────────────────────────────────────────────
        SheetSection(R.string.sheet_section_output)
        SheetItem(Icons.Default.FormatListNumbered,              R.string.action_page_numbers,  notEncrypted)                         { onAction(ScanAction.PageNumbers) }
        SheetItem(Icons.AutoMirrored.Filled.BrandingWatermark,   R.string.action_text_watermark, notEncrypted)                        { onAction(ScanAction.TextWatermark) }
        SheetItem(Icons.Default.Image,                           R.string.action_export_as_jpg, notEncrypted)                         { onAction(ScanAction.ExportAsJpg) }
        SheetItem(Icons.Default.InvertColors,                    R.string.action_grayscale_pdf, notEncrypted)                         { onAction(ScanAction.Grayscale) }
        SheetItem(Icons.Default.Compress,                        R.string.action_compress_pdf,  notEncrypted && !record.isSearchable) { onAction(ScanAction.CompressPdf) }

        // ── SCHUTZ ───────────────────────────────────────────────────────────
        SheetSection(R.string.sheet_section_security)
        SheetItem(Icons.Default.Lock,               R.string.action_protect_pdf,     notEncrypted)         { onAction(ScanAction.ProtectPdf) }
        SheetItem(Icons.Default.AdminPanelSettings, R.string.action_restrict_usage,  notEncrypted)         { onAction(ScanAction.RestrictUsage) }
        SheetItem(Icons.Default.LockOpen,           R.string.action_unlock_pdf,      record.isEncrypted)   { onAction(ScanAction.UnlockPdf) }
        SheetItem(Icons.Default.NoEncryption,       R.string.action_remove_password, record.isEncrypted)   { onAction(ScanAction.RemovePassword) }

        // ── OCR — nur sichtbar wenn durchsuchbar und nicht verschlüsselt ─────
        if (record.isSearchable && notEncrypted) {
            SheetSection(R.string.sheet_section_ocr)
            SheetItem(Icons.Default.FindInPage, R.string.action_remove_text_layer, true) { onAction(ScanAction.RemoveTextLayer) }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SheetSection(titleRes: Int) {
    Text(
        text     = stringResource(titleRes).uppercase(Locale.getDefault()),
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SheetItem(
    icon:    ImageVector,
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
            imageVector        = icon,
            contentDescription = null,
            tint               = contentColor,
            modifier           = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text  = stringResource(textRes),
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor
        )
    }
}

@Composable
private fun tagLabel(tagKey: String): String = when (tagKey) {
    "invoice"     -> stringResource(R.string.tag_invoice)
    "contract"    -> stringResource(R.string.tag_contract)
    "insurance"   -> stringResource(R.string.tag_insurance)
    "certificate" -> stringResource(R.string.tag_certificate)
    "bank"        -> stringResource(R.string.tag_bank)
    "delivery"    -> stringResource(R.string.tag_delivery)
    else          -> tagKey
}
