package info.meuse24.pdf_scanner.ui.info

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PrivacyTip
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import info.meuse24.pdf_scanner.BuildConfig
import info.meuse24.pdf_scanner.R

@Composable
fun InfoScreen() {
    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PdfHeroAnimation() }
        item {
            SectionCard(Icons.Default.Gavel, stringResource(R.string.info_section_copyright)) {
                Text(stringResource(R.string.info_copyright),
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    stringResource(
                        R.string.info_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(stringResource(R.string.info_license_title) + "\n")
                        }
                        append(stringResource(R.string.info_license_body))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SectionCard(Icons.Default.Code, stringResource(R.string.info_section_techstack)) {
                InfoRow(stringResource(R.string.info_label_language),     "Kotlin 2.2.10")
                InfoRow(stringResource(R.string.info_label_ui),           "Jetpack Compose + Material Design 3")
                InfoRow(stringResource(R.string.info_label_architecture), "MVVM + Clean Architecture")
                InfoRow(stringResource(R.string.info_label_async),        "Kotlin Coroutines & Flow")
                InfoRow(stringResource(R.string.info_label_platform),      "Android 10+ (API 29+)")
                InfoRow(stringResource(R.string.info_label_ocr_languages), stringResource(R.string.info_ocr_languages_value))
            }
        }
        item {
            SectionCard(Icons.Default.AutoAwesome, stringResource(R.string.info_section_features)) {
                Text(
                    stringResource(R.string.info_features_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.info_feature_qr_scan),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.info_feature_shortcuts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.info_feature_folders),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.info_feature_app_lock),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.info_feature_businesscard),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.info_feature_print_share),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.info_feature_docx),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.info_feature_translate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SectionCard(Icons.AutoMirrored.Filled.LibraryBooks, stringResource(R.string.info_section_libraries)) {
                InfoRow("Compose BOM",                      "2026.03.00")
                InfoRow("Navigation Compose",               "2.9.7")
                InfoRow("Hilt (Dagger)",                    "2.59.2")
                InfoRow("Hilt Navigation Compose",          "1.3.0")
                InfoRow("Jetpack Room",                     "2.8.4")
                InfoRow("AndroidX Biometric",               "1.4.0-alpha04")
                InfoRow("Kotlinx Serialization",            "1.8.1")
                InfoRow("Google ML Kit Document Scanner",   "16.0.0")
                InfoRow("Google ML Kit Barcode Scanning",   "17.3.0")
                InfoRow("Google ML Kit Text Recognition",   "16.0.1")
                InfoRow("Google ML Kit Translate",          "17.0.3")
                InfoRow("PdfBox Android",                   "2.0.27.0")
                InfoRow("Reorderable",                      "2.4.3")
                InfoRow("Google Tink",                      "1.19.0")
                InfoRow("Argon2kt",                         "1.6.0")
                InfoRow("KSP",                              "2.3.2")
                InfoRow("Android Gradle Plugin",            "9.2.1")
            }
        }
        item {
            SectionCard(Icons.Default.Link, stringResource(R.string.info_section_source)) {
                RepositoryLink(
                    label = stringResource(R.string.info_repository_label),
                    url   = "https://github.com/meuse24/PDF_Scanner"
                )
            }
        }
        item {
            SectionCard(Icons.Default.PrivacyTip, stringResource(R.string.info_section_privacy)) {
                Text(
                    stringResource(R.string.info_privacy_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SectionCard(Icons.Default.AutoAwesome, stringResource(R.string.info_section_credits)) {
                Text(stringResource(R.string.info_credits_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.info_credits_tools),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.info_credits_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RepositoryLink(label: String, url: String) {
    val uriHandler = LocalUriHandler.current
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        Text(
            text     = url,
            style    = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
            color    = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { uriHandler.openUri(url) }
        )
    }
}
