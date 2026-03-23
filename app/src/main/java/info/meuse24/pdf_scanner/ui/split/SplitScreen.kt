package info.meuse24.pdf_scanner.ui.split

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.ui.home.HomeViewModel

@Composable
fun SplitScreen(
    onNavigateBack: () -> Unit,
    viewModel:      SplitViewModel  = hiltViewModel(),
    homeViewModel:  HomeViewModel   = hiltViewModel()
) {
    val record      by viewModel.record.collectAsState()
    val pages       by viewModel.pages.collectAsState()
    val splitPoints by viewModel.splitPoints.collectAsState()
    val loading     by viewModel.loading.collectAsState()

    val preview = viewModel.computePreview()
    val partsCount = splitPoints.count { pt ->
        val pageCount = record?.pageCount ?: 0
        pt in 1 until pageCount
    } + 1

    Column(modifier = Modifier.fillMaxSize()) {

        // Preview text
        if (preview.isNotEmpty()) {
            Text(
                text     = stringResource(R.string.split_preview, partsCount, preview),
                style    = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (loading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier       = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                itemsIndexed(pages) { index, page ->
                    // Page thumbnail card
                    val pageCount = pages.size
                    Card(
                        modifier  = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                        shape     = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier          = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Thumbnail
                            val bmp = page.bitmap
                            if (bmp != null) {
                                Image(
                                    painter            = BitmapPainter(bmp.asImageBitmap()),
                                    contentDescription = null,
                                    contentScale       = ContentScale.Crop,
                                    modifier           = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!page.isLoaded) CircularProgressIndicator(Modifier.size(24.dp))
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Text(
                                text       = stringResource(R.string.split_page_label, index + 1),
                                style      = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Split divider between pages (not after last page)
                    if (index < pageCount - 1) {
                        val isSplitPoint = splitPoints.contains(index)
                        SplitDivider(
                            active  = isSplitPoint,
                            onClick = { viewModel.toggleSplitPoint(index) }
                        )
                    }
                }
            }
        }

        // Confirm button
        Button(
            onClick  = {
                val rec = record ?: return@Button
                val pts = viewModel.getSplitPoints()
                if (pts.isEmpty()) return@Button
                homeViewModel.splitPdf(rec, pts)
                onNavigateBack()
            },
            enabled  = splitPoints.isNotEmpty() && record != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.split_confirm))
        }
    }
}

@Composable
private fun SplitDivider(
    active:  Boolean,
    onClick: () -> Unit
) {
    val color = if (active) MaterialTheme.colorScheme.primary
                else        MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier  = Modifier.weight(1f),
            color     = color,
            thickness = if (active) 2.dp else 1.dp
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier         = Modifier
                .size(32.dp)
                .background(
                    color  = if (active) MaterialTheme.colorScheme.primary
                             else        MaterialTheme.colorScheme.surface,
                    shape  = CircleShape
                )
                .border(
                    width = 1.dp,
                    color = color,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.Default.ContentCut,
                contentDescription = if (active)
                    stringResource(R.string.cd_split_point_active)
                else
                    stringResource(R.string.cd_split_point_inactive),
                tint     = if (active) MaterialTheme.colorScheme.onPrimary
                           else        MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(
            modifier  = Modifier.weight(1f),
            color     = color,
            thickness = if (active) 2.dp else 1.dp
        )
    }
}
