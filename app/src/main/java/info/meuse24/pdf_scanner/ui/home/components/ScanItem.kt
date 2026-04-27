package info.meuse24.pdf_scanner.ui.home.components

import android.graphics.BitmapFactory
import android.text.format.Formatter
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.ui.components.DocumentEditSheet
import info.meuse24.pdf_scanner.ui.components.ScanAction
import info.meuse24.pdf_scanner.ui.ocr.OcrQualityBadge
import info.meuse24.pdf_scanner.util.toQuality
import info.meuse24.pdf_scanner.util.toQualityPercent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ScanItem(
    record:           Document,
    isSelected:       Boolean,
    modifier:         Modifier = Modifier,
    inSelectionMode:  Boolean  = false,
    compact:          Boolean  = false,
    onClick:          () -> Unit,
    onCheckboxToggle: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    folderLabel: String? = null,
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
    val favoriteDescription = stringResource(
        if (record.isFavorite) R.string.cd_remove_favorite else R.string.cd_add_favorite
    )
    val actionsDescription = stringResource(R.string.cd_document_actions)
    val selectDescription = stringResource(R.string.cd_select_document, record.filename)

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
        shape     = RoundedCornerShape(if (compact) 12.dp else 16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                             else            MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border    = BorderStroke(
            width = 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        if (compact) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val thumb = thumbnail
                if (thumb != null) {
                    Image(
                        painter = BitmapPainter(thumb),
                        contentDescription = stringResource(R.string.cd_pdf_document),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = stringResource(R.string.cd_pdf_document),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        record.filename,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (record.isSearchable) {
                        Spacer(Modifier.height(2.dp))
                        SearchableBadges(record = record)
                    }
                }

                Spacer(Modifier.width(4.dp))

                ScanItemActions(
                    isFavorite = record.isFavorite,
                    showDocumentActions = record.pageCount >= 1,
                    inSelectionMode = inSelectionMode,
                    isSelected = isSelected,
                    favoriteDescription = favoriteDescription,
                    actionsDescription = actionsDescription,
                    selectDescription = selectDescription,
                    onToggleFavorite = onToggleFavorite,
                    onShowActions = { sheetVisible = true },
                    onCheckboxToggle = onCheckboxToggle
                )
            }
        } else {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Dateiname — volle Breite
            Text(
                record.filename,
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(2.dp))
            // Thumbnail · Metadaten · Badge · Aktionen
            Row(verticalAlignment = Alignment.CenterVertically) {
                val thumb = thumbnail
                if (thumb != null) {
                    Image(
                        painter            = BitmapPainter(thumb),
                        contentDescription = stringResource(R.string.cd_pdf_document),
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = stringResource(R.string.cd_pdf_document),
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(36.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    if (record.isSearchable) {
                        Spacer(Modifier.height(2.dp))
                        SearchableBadges(record = record)
                    }
                    if (folderLabel != null) {
                        Spacer(Modifier.height(3.dp))
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text(folderLabel, style = MaterialTheme.typography.labelSmall)
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

                ScanItemActions(
                    isFavorite = record.isFavorite,
                    showDocumentActions = record.pageCount >= 1,
                    inSelectionMode = inSelectionMode,
                    isSelected = isSelected,
                    favoriteDescription = favoriteDescription,
                    actionsDescription = actionsDescription,
                    selectDescription = selectDescription,
                    onToggleFavorite = onToggleFavorite,
                    onShowActions = { sheetVisible = true },
                    onCheckboxToggle = onCheckboxToggle
                )
            } // Row
        } // Column
        }
    } // Card

    // ── Action Bottom Sheet ──────────────────────────────────────────────────
    if (sheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            DocumentEditSheet(
                record   = record,
                onAction = { action ->
                    sheetVisible = false
                    onAction(action)
                }
            )
        }
    }
}

@Composable
private fun ScanItemActions(
    isFavorite: Boolean,
    showDocumentActions: Boolean,
    inSelectionMode: Boolean,
    isSelected: Boolean,
    favoriteDescription: String,
    actionsDescription: String,
    selectDescription: String,
    onToggleFavorite: () -> Unit,
    onShowActions: () -> Unit,
    onCheckboxToggle: () -> Unit
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides ScanItemActionWidth) {
        if (!inSelectionMode) {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(
                    width = ScanItemActionWidth,
                    height = ScanItemActionHeight
                )
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = favoriteDescription,
                    tint = if (isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (!inSelectionMode && showDocumentActions) {
            IconButton(
                onClick = onShowActions,
                modifier = Modifier.size(
                    width = ScanItemActionWidth,
                    height = ScanItemActionHeight
                )
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = actionsDescription,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Checkbox(
            checked = isSelected,
            onCheckedChange = { onCheckboxToggle() },
            modifier = Modifier
                .size(width = ScanItemActionWidth, height = ScanItemActionHeight)
                .semantics { contentDescription = selectDescription }
        )
    }
}

private val ScanItemActionWidth = 40.dp
private val ScanItemActionHeight = 48.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchableBadges(record: Document) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Badge(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Text(
                stringResource(R.string.searchable_badge),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false
            )
        }
        OcrQualityBadge(
            quality = record.ocrConfidence.toQuality(),
            percent = record.ocrConfidence.toQualityPercent()
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

