package info.meuse24.pdf_scanner.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.meuse24.pdf_scanner.R

@Composable
internal fun AppBarTitle(
    currentRoute: String?,
    isHomeRoute: Boolean,
    m24AnimationEnabled: Boolean
) {
    val appName = stringResource(R.string.app_name)
    val splitIndex = appName.indexOf(' ')
    val prefix = if (splitIndex > 0) appName.substring(0, splitIndex) else appName
    val suffix = if (splitIndex > 0) appName.substring(splitIndex).trimStart() else ""
    val routeTitle = when {
        isHomeRoute -> suffix
        currentRoute == Screen.Help.route -> stringResource(R.string.nav_help)
        currentRoute == Screen.Trash.route -> stringResource(R.string.nav_trash)
        currentRoute == Screen.FolderManagement.route -> stringResource(R.string.folder_management_title)
        currentRoute == Screen.Settings.route -> stringResource(R.string.nav_settings)
        currentRoute == Screen.AiChatbotTargets.route -> stringResource(R.string.settings_ai_chatbots)
        currentRoute == Screen.Info.route -> stringResource(R.string.nav_info)
        currentRoute == Screen.Privacy.route -> stringResource(R.string.nav_privacy)
        currentRoute == Screen.LocalSync.route -> stringResource(R.string.local_sync_title)
        currentRoute?.startsWith("ocr-review/") == true -> stringResource(R.string.ocr_review_title)
        currentRoute?.startsWith("viewer/") == true -> stringResource(R.string.pdf_viewer_screen_title)
        currentRoute?.startsWith("split/") == true -> stringResource(R.string.split_screen_title)
        currentRoute?.startsWith("reorder/") == true -> stringResource(R.string.reorder_screen_title)
        currentRoute?.startsWith("rotate-pages/") == true -> stringResource(R.string.rotate_screen_title)
        currentRoute?.startsWith("delete-pages/") == true -> stringResource(R.string.delete_pages_screen_title)
        currentRoute?.startsWith("extract-pages/") == true -> stringResource(R.string.extract_pages_screen_title)
        currentRoute?.startsWith("append-pages/") == true -> stringResource(R.string.append_pages_screen_title)
        currentRoute?.startsWith("duplicate-pages/") == true -> stringResource(R.string.duplicate_pages_screen_title)
        currentRoute?.startsWith("page-numbers/") == true -> stringResource(R.string.page_numbers_screen_title)
        currentRoute?.startsWith("text-watermark/") == true -> stringResource(R.string.watermark_screen_title)
        currentRoute?.startsWith("compress-pdf/") == true -> stringResource(R.string.compress_pdf_screen_title)
        currentRoute?.startsWith("protect-pdf/") == true -> stringResource(R.string.protect_pdf_screen_title)
        currentRoute?.startsWith("unlock-pdf/") == true -> stringResource(R.string.unlock_pdf_screen_title)
        currentRoute?.startsWith("signature/") == true -> stringResource(R.string.signature_screen_title)
        currentRoute?.startsWith("remove-text-layer/") == true -> stringResource(R.string.remove_text_layer_screen_title)
        currentRoute?.startsWith("remove-password/") == true -> stringResource(R.string.remove_password_screen_title)
        currentRoute?.startsWith("restrict-usage/") == true -> stringResource(R.string.restrict_usage_screen_title)
        currentRoute?.startsWith("annotate/") == true -> stringResource(R.string.annotate_screen_title)
        currentRoute?.startsWith("redact/") == true -> stringResource(R.string.redact_screen_title)
        currentRoute?.startsWith("grayscale/") == true -> stringResource(R.string.grayscale_screen_title)
        currentRoute?.startsWith("pdf-metadata/") == true -> stringResource(R.string.metadata_screen_title)
        currentRoute?.startsWith("qr-scan/") == true -> stringResource(R.string.qr_scan_title)
        currentRoute?.startsWith("business-card/") == true -> stringResource(R.string.business_card_title)
        currentRoute?.startsWith("table-export/") == true -> stringResource(R.string.table_export_screen_title)
        currentRoute == Screen.ImagesToPdf.route -> stringResource(R.string.images_to_pdf_title)
        else -> suffix.ifBlank { appName }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedM24Badge(
            text = prefix,
            animationKey = currentRoute ?: Screen.Ablage.route,
            enabled = m24AnimationEnabled
        )
        if (routeTitle.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = routeTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun AnimatedM24Badge(
    text: String,
    animationKey: String,
    enabled: Boolean = true
) {
    val rotation = remember { Animatable(0f) }
    var replayCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(animationKey, replayCount, enabled) {
        rotation.snapTo(0f)
        if (enabled) {
            rotation.animateTo(
                targetValue = 360f,
                animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
            )
        }
    }

    val badgeModifier = if (enabled) {
        Modifier.clickable { replayCount++ }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .graphicsLayer { rotationZ = rotation.value }
            .then(badgeModifier)
            .background(
                color = MaterialTheme.colorScheme.tertiary,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(letterSpacing = (-0.2).sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiary,
            maxLines = 1
        )
    }
}
