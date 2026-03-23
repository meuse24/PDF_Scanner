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
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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

@Composable
internal fun ScanItem(
    record:           ScanRecord,
    isSelected:       Boolean,
    inSelectionMode:  Boolean  = false,
    modifier:         Modifier = Modifier,
    onClick:          () -> Unit,
    onCheckboxToggle: () -> Unit,
    onSplit:          () -> Unit = {},
    onReorder:        () -> Unit = {},
    onRotate:         () -> Unit = {},
    onDeletePages:    () -> Unit = {},
    onExtractPages:   () -> Unit = {},
    onDuplicatePages: () -> Unit = {},
    onPageNumbers:    () -> Unit = {},
    onTextWatermark:  () -> Unit = {}
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
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
        Row(
            modifier          = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val thumb = thumbnail
            if (thumb != null) {
                Image(
                    painter            = BitmapPainter(thumb),
                    contentDescription = stringResource(R.string.cd_pdf_document),
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = stringResource(R.string.cd_pdf_document),
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(48.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    record.filename,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
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
            }

            if (!inSelectionMode && record.pageCount >= 1) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded         = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.action_rotate)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null) },
                            onClick     = { menuExpanded = false; onRotate() }
                        )
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.action_split)) },
                            leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                            onClick     = { menuExpanded = false; onSplit() },
                            enabled     = record.pageCount >= 2
                        )
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.action_reorder)) },
                            leadingIcon = { Icon(Icons.Default.SwapVert, contentDescription = null) },
                            onClick     = { menuExpanded = false; onReorder() },
                            enabled     = record.pageCount >= 2
                        )
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.action_delete_pages)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick     = { menuExpanded = false; onDeletePages() },
                            enabled     = record.pageCount >= 2
                        )
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.action_extract_pages)) },
                            leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                            onClick     = { menuExpanded = false; onExtractPages() },
                            enabled     = record.pageCount >= 2
                        )
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.action_duplicate_pages)) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick     = { menuExpanded = false; onDuplicatePages() }
                        )
                        DropdownMenuItem(
                            text    = { Text(stringResource(R.string.action_page_numbers)) },
                            onClick = { menuExpanded = false; onPageNumbers() }
                        )
                        DropdownMenuItem(
                            text    = { Text(stringResource(R.string.action_text_watermark)) },
                            onClick = { menuExpanded = false; onTextWatermark() }
                        )
                    }
                }
            }

            Checkbox(
                checked         = isSelected,
                onCheckedChange = { onCheckboxToggle() }
            )
        }
    }
}
