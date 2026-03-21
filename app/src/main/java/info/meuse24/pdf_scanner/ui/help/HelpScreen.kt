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
import androidx.compose.ui.unit.dp

@Composable
fun HelpScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Anleitung", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        SectionTitle("Dokument scannen")
        HelpStep(
            icon = { Icon(Icons.Default.Add, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) },
            step = "1.",
            text = "Tippe auf den + Button (unten rechts) oder wähle im Hamburger-Menu -> Scanner starten."
        )
        HelpStep(
            icon = { Icon(Icons.Default.PhotoCamera, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) },
            step = "2.",
            text = "Der Google Document Scanner öffnet sich. Fotografiere eine oder mehrere Seiten — Ränder und Perspektive werden automatisch korrigiert."
        )
        HelpStep(
            icon = { Icon(Icons.Default.Save, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) },
            step = "3.",
            text = "Nach dem Scan öffnet sich ein Dialog. Gib einen Dateinamen ein und tippe Speichern. Das PDF wird lokal auf deinem Geraet gespeichert."
        )

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        SectionTitle("Ablage verwalten")
        HelpStep(
            icon = { Icon(Icons.Default.PictureAsPdf, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) },
            step = "·",
            text = "Tippe auf einen Eintrag in der Liste, um das PDF in einem externen Viewer zu öffnen."
        )
        HelpStep(
            icon = { Icon(Icons.Default.Share, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) },
            step = "·",
            text = "Tippe auf das Teilen-Icon, um das PDF per E-Mail, Messenger oder anderen Apps zu versenden."
        )
        HelpStep(
            icon = { Icon(Icons.Default.Delete, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.error) },
            step = "·",
            text = "Tippe auf das Löschen-Icon, um ein Dokument dauerhaft zu entfernen."
        )

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        SectionTitle("Navigation")
        HelpStep(
            icon = { Icon(Icons.Default.FolderOpen, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) },
            step = "·",
            text = "Ueber das Hamburger-Menu (oben links) erreichst du die Ablage, den Scanner, die Hilfe und den Info-Screen."
        )

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        SectionTitle("Hinweise")
        Text(
            "• Die App benötigt keinen direkten Kamera-Zugriff — der ML Kit Document Scanner läuft als eigener System-Prozess.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "• Alle PDFs werden lokal im App-Verzeichnis gespeichert und verlassen das Gerät nicht.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "• Für Mehrseitige Dokumente einfach mehrere Seiten hintereinander fotografieren — der Scanner verbindet sie automatisch zu einem PDF.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun HelpStep(
    icon: @Composable () -> Unit,
    step: String,
    text: String
) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 6.dp)) {
        icon()
        Spacer(Modifier.width(10.dp))
        Text(
            text = "$step  $text",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
