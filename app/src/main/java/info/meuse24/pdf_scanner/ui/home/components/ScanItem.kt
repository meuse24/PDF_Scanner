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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BrandingWatermark
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NoEncryption
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
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
    data object Highlight        : ScanAction
}

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
    var menuExpanded        by remember { mutableStateOf(false) }
    var subMenuPages        by remember { mutableStateOf(false) }
    var subMenuSecurity     by remember { mutableStateOf(false) }
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
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }

                    // ── Hauptmenü ────────────────────────────────────────────
                    DropdownMenu(
                        expanded         = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        val notEncrypted = !record.isEncrypted
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.action_rotate)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.RotateRight, null) },
                            onClick     = { menuExpanded = false; onAction(ScanAction.Rotate) },
                            enabled     = notEncrypted
                        )
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.action_page_numbers)) },
                            leadingIcon = { Icon(Icons.Default.FormatListNumbered, null) },
                            onClick     = { menuExpanded = false; onAction(ScanAction.PageNumbers) },
                            enabled     = notEncrypted
                        )
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.action_text_watermark)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.BrandingWatermark, null) },
                            onClick     = { menuExpanded = false; onAction(ScanAction.TextWatermark) },
                            enabled     = notEncrypted
                        )
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.action_sign_pdf)) },
                            leadingIcon = { Icon(Icons.Default.Draw, null) },
                            onClick     = { menuExpanded = false; onAction(ScanAction.Signature) },
                            enabled     = notEncrypted
                        )
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.action_highlight_pdf)) },
                            leadingIcon = { Icon(Icons.Default.BorderColor, null) },
                            onClick     = { menuExpanded = false; onAction(ScanAction.Highlight) },
                            enabled     = notEncrypted
                        )
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.action_compress_pdf)) },
                            leadingIcon = { Icon(Icons.Default.Compress, null) },
                            onClick     = { menuExpanded = false; onAction(ScanAction.CompressPdf) },
                            enabled     = notEncrypted && !record.isSearchable
                        )
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.action_export_as_jpg)) },
                            leadingIcon = { Icon(Icons.Default.Image, null) },
                            onClick     = { menuExpanded = false; onAction(ScanAction.ExportAsJpg) },
                            enabled     = notEncrypted
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text         = { Text(stringResource(R.string.action_submenu_page_structure)) },
                            leadingIcon  = { Icon(Icons.Default.Layers, null) },
                            trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                            onClick      = { menuExpanded = false; subMenuPages = true },
                            enabled      = notEncrypted
                        )
                        DropdownMenuItem(
                            text         = { Text(stringResource(R.string.action_submenu_security)) },
                            leadingIcon  = { Icon(Icons.Default.Security, null) },
                            trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                            onClick      = { menuExpanded = false; subMenuSecurity = true }
                        )
                    }

                    // ── Submenu: Seitenstruktur ───────────────────────────────
                    DropdownMenu(
                        expanded         = subMenuPages,
                        onDismissRequest = { subMenuPages = false }
                    ) {
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.action_split)) },
                            leadingIcon = { Icon(Icons.Default.ContentCut, null) },
                            onClick     = { subMenuPages = false; onAction(ScanAction.Split) },
                            enabled     = record.pageCount >= 2
                        )
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.action_reorder)) },
                            leadingIcon = { Icon(Icons.Default.SwapVert, null) },
                            onClick     = { subMenuPages = false; onAction(ScanAction.Reorder) },
                            enabled     = record.pageCount >= 2
                        )
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.action_delete_pages)) },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick     = { subMenuPages = false; onAction(ScanAction.DeletePages) },
                            enabled     = record.pageCount >= 2
                        )
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.action_extract_pages)) },
                            leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) },
                            onClick     = { subMenuPages = false; onAction(ScanAction.ExtractPages) },
                            enabled     = record.pageCount >= 2
                        )
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.action_duplicate_pages)) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                            onClick     = { subMenuPages = false; onAction(ScanAction.DuplicatePages) }
                        )
                    }

                    // ── Submenu: Schutz & Passwort ────────────────────────────
                    DropdownMenu(
                        expanded         = subMenuSecurity,
                        onDismissRequest = { subMenuSecurity = false }
                    ) {
                        val notEncrypted = !record.isEncrypted
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.action_protect_pdf)) },
                            leadingIcon = { Icon(Icons.Default.Lock, null) },
                            onClick     = { subMenuSecurity = false; onAction(ScanAction.ProtectPdf) },
                            enabled     = notEncrypted
                        )
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.action_restrict_usage)) },
                            leadingIcon = { Icon(Icons.Default.AdminPanelSettings, null) },
                            onClick     = { subMenuSecurity = false; onAction(ScanAction.RestrictUsage) },
                            enabled     = notEncrypted
                        )
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.action_unlock_pdf)) },
                            leadingIcon = { Icon(Icons.Default.LockOpen, null) },
                            onClick     = { subMenuSecurity = false; onAction(ScanAction.UnlockPdf) },
                            enabled     = record.isEncrypted
                        )
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.action_remove_password)) },
                            leadingIcon = { Icon(Icons.Default.NoEncryption, null) },
                            onClick     = { subMenuSecurity = false; onAction(ScanAction.RemovePassword) },
                            enabled     = record.isEncrypted
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.action_remove_text_layer)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ManageSearch, null) },
                            onClick     = { subMenuSecurity = false; onAction(ScanAction.RemoveTextLayer) },
                            enabled     = record.isSearchable && notEncrypted
                        )
                    }
                }
            }

            Checkbox(
                checked         = isSelected,
                onCheckedChange = { onCheckboxToggle() }
            )
            } // Row
        } // Column
    } // Card
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
