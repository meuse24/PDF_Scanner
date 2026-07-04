package info.meuse24.pdf_scanner.ui.formfill

import android.graphics.Bitmap
import info.meuse24.pdf_scanner.domain.model.AcroFormCapability
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.FormField
import info.meuse24.pdf_scanner.domain.model.FormFieldValue

data class FormFillUiState(
    val record: Document? = null,
    val capability: AcroFormCapability = AcroFormCapability.NONE,
    val fields: List<FormField> = emptyList(),
    val values: Map<String, FormFieldValue> = emptyMap(),
    val pageCount: Int = 0,
    val currentPageIndex: Int = 0,
    val pageBitmap: Bitmap? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val flatten: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false
) {
    val currentPageFields: List<FormField>
        get() = fields.filter { it.pageIndex == currentPageIndex }
}
