package info.meuse24.pdf_scanner.ui.ocr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.util.OcrQuality

@Composable
fun OcrQualityBadge(
    quality: OcrQuality,
    percent: Int?,
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor) = when (quality) {
        OcrQuality.HIGH -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        OcrQuality.MEDIUM -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        OcrQuality.LOW -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        OcrQuality.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when (quality) {
        OcrQuality.HIGH, OcrQuality.MEDIUM -> Icons.Default.CheckCircle
        OcrQuality.LOW -> Icons.Default.WarningAmber
        OcrQuality.UNKNOWN -> Icons.Default.FindInPage
    }
    val label = percent?.let { stringResource(R.string.ocr_quality_badge_percent, it) }
        ?: stringResource(R.string.ocr_quality_badge_unknown)

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false
            )
        }
    }
}
