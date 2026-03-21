package info.meuse24.pdf_scanner.ui.help

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import info.meuse24.pdf_scanner.R

@Composable
fun HelpScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(stringResource(R.string.help_screen_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        SectionTitle(stringResource(R.string.help_section_scan))
        HelpStep(icon = { Icon(Icons.Default.Add, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) }, step = "1.", text = stringResource(R.string.help_step1))
        HelpStep(icon = { Icon(Icons.Default.PhotoCamera, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) }, step = "2.", text = stringResource(R.string.help_step2))
        HelpStep(icon = { Icon(Icons.Default.Save, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) }, step = "3.", text = stringResource(R.string.help_step3))

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        SectionTitle(stringResource(R.string.help_section_archive))
        HelpStep(icon = { Icon(Icons.Default.PictureAsPdf, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) }, step = "·", text = stringResource(R.string.help_item_open))
        HelpStep(icon = { Icon(Icons.Default.Share, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) }, step = "·", text = stringResource(R.string.help_item_share))
        HelpStep(icon = { Icon(Icons.Default.Delete, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.error) }, step = "·", text = stringResource(R.string.help_item_delete))

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        SectionTitle(stringResource(R.string.help_section_navigation))
        HelpStep(icon = { Icon(Icons.Default.FolderOpen, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) }, step = "·", text = stringResource(R.string.help_nav_text))

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        SectionTitle(stringResource(R.string.help_section_notes))
        Text("• ${stringResource(R.string.help_note1)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("• ${stringResource(R.string.help_note2)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("• ${stringResource(R.string.help_note3)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun HelpStep(icon: @Composable () -> Unit, step: String, text: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 6.dp)) {
        icon()
        Spacer(Modifier.width(10.dp))
        Text("$step  $text", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
