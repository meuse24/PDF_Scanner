package info.meuse24.pdf_scanner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.PdfMarginPreset
import info.meuse24.pdf_scanner.domain.model.PdfPageOrientation
import info.meuse24.pdf_scanner.domain.model.PdfPageSetup
import info.meuse24.pdf_scanner.domain.model.PdfPageSizePreset

@Composable
fun PdfPageSetupSection(
    setup: PdfPageSetup,
    onSetupChange: (PdfPageSetup) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.images_to_pdf_page_setup_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PageSizeRow(
            selected = setup.sizePreset,
            onSelected = { onSetupChange(setup.copy(sizePreset = it)) }
        )
        OrientationRow(
            selected = setup.orientation,
            onSelected = { onSetupChange(setup.copy(orientation = it)) }
        )
        MarginRow(
            selected = setup.marginPreset,
            onSelected = { onSetupChange(setup.copy(marginPreset = it)) }
        )
    }
}

@Composable
private fun PageSizeRow(
    selected: PdfPageSizePreset,
    onSelected: (PdfPageSizePreset) -> Unit
) {
    val options = listOf(
        PdfPageSizePreset.ISO_A5,
        PdfPageSizePreset.ISO_A4,
        PdfPageSizePreset.ISO_A3,
        PdfPageSizePreset.NA_LETTER
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = selected == option,
                onClick = { onSelected(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = {
                    Text(
                        text = when (option) {
                            PdfPageSizePreset.ISO_A5 -> stringResource(R.string.images_to_pdf_page_size_a5)
                            PdfPageSizePreset.ISO_A4 -> stringResource(R.string.images_to_pdf_page_size_a4)
                            PdfPageSizePreset.ISO_A3 -> stringResource(R.string.images_to_pdf_page_size_a3)
                            PdfPageSizePreset.NA_LETTER -> stringResource(R.string.images_to_pdf_page_size_letter)
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun OrientationRow(
    selected: PdfPageOrientation,
    onSelected: (PdfPageOrientation) -> Unit
) {
    val options = listOf(
        PdfPageOrientation.PORTRAIT,
        PdfPageOrientation.LANDSCAPE
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = selected == option,
                onClick = { onSelected(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = {
                    Text(
                        text = when (option) {
                            PdfPageOrientation.PORTRAIT -> stringResource(R.string.images_to_pdf_orientation_portrait)
                            PdfPageOrientation.LANDSCAPE -> stringResource(R.string.images_to_pdf_orientation_landscape)
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun MarginRow(
    selected: PdfMarginPreset,
    onSelected: (PdfMarginPreset) -> Unit
) {
    val options = listOf(
        PdfMarginPreset.SMALL,
        PdfMarginPreset.MEDIUM,
        PdfMarginPreset.LARGE
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = selected == option,
                onClick = { onSelected(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = {
                    Text(
                        text = when (option) {
                            PdfMarginPreset.SMALL -> stringResource(R.string.images_to_pdf_margin_small)
                            PdfMarginPreset.MEDIUM -> stringResource(R.string.images_to_pdf_margin_medium)
                            PdfMarginPreset.LARGE -> stringResource(R.string.images_to_pdf_margin_large)
                        }
                    )
                }
            )
        }
    }
}
