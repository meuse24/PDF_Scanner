package info.meuse24.pdf_scanner.ui.help

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import info.meuse24.pdf_scanner.R

@Composable
fun HelpScreen() {
    val lazyListState = rememberLazyListState()
    LazyColumn(
        state             = lazyListState,
        flingBehavior     = rememberSnapFlingBehavior(lazyListState),
        contentPadding    = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard(Icons.Default.PhotoCamera, stringResource(R.string.help_section_scan)) {
                HelpStep(Icons.Default.Add,         "1.", stringResource(R.string.help_step1))
                HelpStep(Icons.Default.PhotoCamera, "2.", stringResource(R.string.help_step2))
                HelpStep(Icons.Default.Save,        "3.", stringResource(R.string.help_step3))
            }
        }
        item {
            SectionCard(Icons.Default.FolderOpen, stringResource(R.string.help_section_archive)) {
                HelpStep(Icons.Default.PictureAsPdf, "·", stringResource(R.string.help_item_open))
                HelpStep(Icons.Default.CheckBox,     "·", stringResource(R.string.help_item_share))
                HelpStep(Icons.Default.DoneAll,      "·", stringResource(R.string.help_item_delete))
            }
        }
        item {
            SectionCard(Icons.Default.Menu, stringResource(R.string.help_section_navigation)) {
                HelpStep(Icons.Default.FolderOpen, "·", stringResource(R.string.help_nav_text))
            }
        }
        item {
            SectionCard(Icons.Default.TipsAndUpdates, stringResource(R.string.help_section_notes)) {
                Text("• ${stringResource(R.string.help_note1)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("• ${stringResource(R.string.help_note2)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("• ${stringResource(R.string.help_note3)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier         = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null,
                        tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun HelpStep(
    icon:     ImageVector,
    step:     String,
    text:     String,
    iconTint: Color = Color.Unspecified
) {
    val tint = if (iconTint == Color.Unspecified) MaterialTheme.colorScheme.primary else iconTint
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 6.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
        Spacer(Modifier.width(10.dp))
        Text("$step  $text", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
