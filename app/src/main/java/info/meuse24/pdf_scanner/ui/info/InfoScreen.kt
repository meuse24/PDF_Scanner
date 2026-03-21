package info.meuse24.pdf_scanner.ui.info

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun InfoScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("MEUSE24 PDF Scanner", style = MaterialTheme.typography.headlineMedium)
        Text("Version 1.0", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(24.dp))

        // Copyright
        SectionHeader("Copyright & Lizenz")
        Text(
            "© 2026 Günther Meusburger",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("MIT License\n") }
                append(
                    "Permission is hereby granted, free of charge, to any person obtaining a copy " +
                    "of this software and associated documentation files (the \"Software\"), to deal " +
                    "in the Software without restriction, including without limitation the rights to " +
                    "use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies " +
                    "of the Software, and to permit persons to whom the Software is furnished to do so."
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        // Tech Stack
        SectionHeader("Tech Stack")
        InfoRow("Sprache",        "Kotlin 2.2.10")
        InfoRow("UI-Framework",   "Jetpack Compose + Material Design 3")
        InfoRow("Architektur",    "MVVM + Clean Architecture")
        InfoRow("Asynchronität",  "Kotlin Coroutines & Flow")
        InfoRow("Plattform",      "Android 10+ (API 29+)")

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        // Libraries
        SectionHeader("Bibliotheken")
        InfoRow("Google ML Kit Document Scanner", "16.0.0")
        InfoRow("Jetpack Room",                  "2.8.4")
        InfoRow("Hilt (Dagger)",                 "2.59.2")
        InfoRow("Navigation Compose",            "2.9.7")
        InfoRow("Hilt Navigation Compose",       "1.3.0")
        InfoRow("KSP",                           "2.2.10-2.0.2")
        InfoRow("Android Gradle Plugin",         "9.1.0")

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        // Credits
        SectionHeader("Credits")
        Text(
            "Diese App wurde vollständig mit Unterstützung von",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Claude Code (claude.ai/code)",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "by Anthropic",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Planung, Architektur, Implementierung, Icon-Design und Dokumentation wurden im Dialog mit dem KI-Assistenten Claude Code erarbeitet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
