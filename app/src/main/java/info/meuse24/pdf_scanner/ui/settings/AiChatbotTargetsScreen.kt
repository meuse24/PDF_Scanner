package info.meuse24.pdf_scanner.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.AiChatbotTarget

@Composable
fun AiChatbotTargetsScreen(
    targets: List<AiChatbotTarget>,
    onTargetsChange: (List<AiChatbotTarget>) -> Unit
) {
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editorVisible by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(targets) { index, target ->
            ListItem(
                modifier = Modifier.clickable {
                    editingIndex = index
                    editorVisible = true
                },
                headlineContent = { Text(target.name) },
                supportingContent = {
                    Text(
                        text = target.url,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingContent = {
                    Row {
                        IconButton(
                            onClick = {
                                editingIndex = index
                                editorVisible = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.action_edit)
                            )
                        }
                        IconButton(
                            enabled = targets.size > 1,
                            onClick = {
                                onTargetsChange(targets.filterIndexed { itemIndex, _ -> itemIndex != index })
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
            HorizontalDivider()
        }
        item {
            FilledTonalButton(
                onClick = {
                    editingIndex = null
                    editorVisible = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_add))
            }
        }
    }

    if (editorVisible) {
        AiChatbotTargetDialog(
            target = editingIndex?.let(targets::getOrNull),
            onSave = { target ->
                onTargetsChange(
                    editingIndex?.let { index ->
                        targets.mapIndexed { itemIndex, existing ->
                            if (itemIndex == index) target else existing
                        }
                    } ?: targets + target
                )
                editorVisible = false
            },
            onDismiss = { editorVisible = false }
        )
    }
}
