package info.meuse24.pdf_scanner.ui.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import info.meuse24.pdf_scanner.R

@Composable
fun PrivacyScreen() {
    val points = listOf(
        Triple(Icons.Default.PhoneAndroid, stringResource(R.string.privacy_keyword_1), stringResource(R.string.privacy_point_1)),
        Triple(Icons.Default.CloudOff,     stringResource(R.string.privacy_keyword_2), stringResource(R.string.privacy_point_2)),
        Triple(Icons.Default.Shield,       stringResource(R.string.privacy_keyword_3), stringResource(R.string.privacy_point_3)),
        Triple(Icons.Default.Lock,         stringResource(R.string.privacy_keyword_4), stringResource(R.string.privacy_point_4)),
        Triple(Icons.Default.Psychology,   stringResource(R.string.privacy_keyword_5), stringResource(R.string.privacy_point_5)),
        Triple(Icons.Default.Delete,       stringResource(R.string.privacy_keyword_6), stringResource(R.string.privacy_point_6)),
    )

    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(points) { (icon, keyword, text) ->
            PrivacyCard(icon = icon, keyword = keyword, text = text)
        }

        item {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.privacy_footer),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PrivacyCard(icon: ImageVector, keyword: String, text: String) {
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
                    Icon(
                        imageVector        = icon,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier           = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text  = keyword,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))
            Text(
                text  = text,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
