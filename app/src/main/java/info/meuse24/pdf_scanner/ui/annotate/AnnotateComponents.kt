package info.meuse24.pdf_scanner.ui.annotate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import info.meuse24.pdf_scanner.R

internal data class AnnotateToolSpec(
    val icon: ImageVector,
    val labelRes: Int
)

internal fun AnnotateTool.spec(): AnnotateToolSpec = when (this) {
    AnnotateTool.MARK -> AnnotateToolSpec(Icons.Default.Edit, R.string.annotate_tool_mark)
    AnnotateTool.TEXT -> AnnotateToolSpec(Icons.Default.Create, R.string.annotate_tool_text)
    AnnotateTool.RECT_FILLED -> AnnotateToolSpec(Icons.Default.CropSquare, R.string.annotate_tool_rect_filled)
    AnnotateTool.RECT_FRAME -> AnnotateToolSpec(Icons.Default.CropSquare, R.string.annotate_tool_rect_frame)
    AnnotateTool.OVAL_FILLED -> AnnotateToolSpec(Icons.Default.RadioButtonUnchecked, R.string.annotate_tool_oval_filled)
    AnnotateTool.OVAL_FRAME -> AnnotateToolSpec(Icons.Default.RadioButtonUnchecked, R.string.annotate_tool_oval_frame)
    AnnotateTool.ZOOM -> AnnotateToolSpec(Icons.Default.ZoomIn, R.string.annotate_tool_zoom)
}

@Composable
internal fun AnnotationToolDropdown(
    selectedTool: AnnotateTool,
    onToolSelected: (AnnotateTool) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedSpec = selectedTool.spec()

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(selectedSpec.icon, null)
            Text(
                text = stringResource(selectedSpec.labelRes),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                maxLines = 1
            )
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AnnotateTool.entries.filterNot { it == AnnotateTool.ZOOM }.forEach { tool ->
                val spec = tool.spec()
                DropdownMenuItem(
                    text = { Text(stringResource(spec.labelRes)) },
                    leadingIcon = { Icon(spec.icon, null) },
                    onClick = {
                        expanded = false
                        onToolSelected(tool)
                    }
                )
            }
        }
    }
}

@Composable
internal fun AnnotationToolbarIconButton(
    icon: ImageVector,
    contentDescriptionRes: Int,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(10.dp),
        modifier = Modifier.size(44.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(contentDescriptionRes),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
internal fun AnnotationAttributeBar(
    selectedColor: Int,
    selectedWidthFraction: Float,
    widthLabelRes: Int,
    onColorSelected: (Int) -> Unit,
    onWidthSelected: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.annotate_attribute_color),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            annotationPalette.forEach { color ->
                val isSelected = color == selectedColor
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 28.dp else 24.dp)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape
                        )
                        .padding(3.dp)
                        .background(Color(color), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.75f), CircleShape)
                        .clickable { onColorSelected(color) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(widthLabelRes),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            markerWidthOptions.forEach { option ->
                val isSelected = selectedWidthFraction == option.fraction
                val optionLabel = stringResource(option.labelRes)
                val lineColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                FilterChip(
                    selected = isSelected,
                    onClick = { onWidthSelected(option.fraction) },
                    label = {
                        Canvas(
                            modifier = Modifier
                                .size(width = 34.dp, height = 18.dp)
                                .semantics { contentDescription = optionLabel }
                        ) {
                            val strokeWidth = when (option.fraction) {
                                markerWidthOptions[0].fraction -> 3f
                                markerWidthOptions[1].fraction -> 6f
                                else -> 10f
                            }
                            drawLine(
                                color = lineColor,
                                start = center.copy(x = 4.dp.toPx()),
                                end = center.copy(x = size.width - 4.dp.toPx()),
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
internal fun AnnotationActionIconBar(
    hasPageAnnotations: Boolean,
    hasAnyAnnotations: Boolean,
    selectionLabelRes: Int? = null,
    showEditText: Boolean = false,
    onUndo: () -> Unit,
    onClearPage: () -> Unit,
    onResetAll: () -> Unit,
    onEditText: () -> Unit = {},
    onDelete: () -> Unit = {},
    onClearSelection: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        selectionLabelRes?.let {
            Text(
                text = stringResource(R.string.annotate_selected_prefix, stringResource(it)),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnnotationToolbarIconButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    contentDescriptionRes = R.string.annotate_action_undo,
                    enabled = hasPageAnnotations,
                    onClick = onUndo
                )
                AnnotationToolbarIconButton(
                    icon = Icons.AutoMirrored.Filled.Article,
                    contentDescriptionRes = R.string.annotate_action_clear_page,
                    enabled = hasPageAnnotations,
                    onClick = onClearPage
                )
                AnnotationToolbarIconButton(
                    icon = Icons.Default.ClearAll,
                    contentDescriptionRes = R.string.annotate_action_clear_all,
                    enabled = hasAnyAnnotations,
                    onClick = onResetAll
                )
                if (showEditText) {
                    AnnotationToolbarIconButton(
                        icon = Icons.Default.Edit,
                        contentDescriptionRes = R.string.annotate_selected_edit_text,
                        onClick = onEditText
                    )
                }
                if (selectionLabelRes != null) {
                    AnnotationToolbarIconButton(
                        icon = Icons.Default.Delete,
                        contentDescriptionRes = R.string.annotate_selected_delete,
                        onClick = onDelete
                    )
                    AnnotationToolbarIconButton(
                        icon = Icons.Default.Close,
                        contentDescriptionRes = R.string.annotate_selected_clear,
                        onClick = onClearSelection
                    )
                }
            }
        }
    }
}
