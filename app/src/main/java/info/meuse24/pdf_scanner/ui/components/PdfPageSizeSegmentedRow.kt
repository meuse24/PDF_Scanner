package info.meuse24.pdf_scanner.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.PdfPageSizePreset

@Composable
fun PdfPageSizeSegmentedRow(
    selected: PdfPageSizePreset,
    onSelected: (PdfPageSizePreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        PdfPageSizePreset.ISO_A5,
        PdfPageSizePreset.ISO_A4,
        PdfPageSizePreset.ISO_A3,
        PdfPageSizePreset.NA_LETTER
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
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
