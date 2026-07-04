package info.meuse24.pdf_scanner.testutil

import info.meuse24.pdf_scanner.domain.gateway.DispatcherProvider
import info.meuse24.pdf_scanner.domain.gateway.ResourceProvider
import info.meuse24.pdf_scanner.domain.gateway.StringResource
import info.meuse24.pdf_scanner.domain.gateway.StorageProvider
import info.meuse24.pdf_scanner.R
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File

class FakeResourceProvider(
    var strings: Map<Int, String> = emptyMap(),
    var plurals: Map<Int, String> = emptyMap(),
    private val fallback: String = "stub"
) : ResourceProvider {
    override fun getString(resource: StringResource): String = strings[resource.toAndroidResId()] ?: fallback

    override fun getString(resId: Int): String = strings[resId] ?: fallback

    override fun getString(resId: Int, vararg args: Any): String {
        val template = strings[resId] ?: fallback
        return String.format(template, *args)
    }

    override fun getQuantityString(resId: Int, quantity: Int, vararg args: Any): String {
        val template = plurals[resId] ?: strings[resId] ?: fallback
        return String.format(template, *args)
    }
}

private fun StringResource.toAndroidResId(): Int = when (this) {
    StringResource.ErrorPdfInvalid -> R.string.error_pdf_invalid
    StringResource.WorkflowNothingSelected -> R.string.workflow_nothing_selected
    StringResource.MergeNotEnoughScans -> R.string.merge_not_enough_scans
    StringResource.SearchableNothingToDo -> R.string.searchable_nothing_to_do
    StringResource.SearchableUnsupportedScript -> R.string.searchable_unsupported_script
    StringResource.SplitNoPoints -> R.string.split_no_points
    StringResource.PageSelectionInvalid -> R.string.page_selection_invalid
    StringResource.ReorderInvalidOrder -> R.string.reorder_invalid_order
    StringResource.WatermarkInvalid -> R.string.watermark_invalid
    StringResource.SignatureRequired -> R.string.signature_required
    StringResource.SignatureScaleInvalid -> R.string.signature_scale_invalid
    StringResource.CompressPdfSearchableUnsupported -> R.string.compress_pdf_searchable_unsupported
    StringResource.ProtectedPdfUnsupported -> R.string.protected_pdf_unsupported
    StringResource.PasswordRequired -> R.string.password_required
    StringResource.PasswordWrong -> R.string.password_wrong
    StringResource.ProtectPdfAlreadyProtected -> R.string.protect_pdf_already_protected
    StringResource.UnlockPdfNotProtected -> R.string.unlock_pdf_not_protected
    StringResource.DeletePagesAllError -> R.string.delete_pages_all_error
    StringResource.WorkflowMissingFiles -> R.string.workflow_missing_files
    StringResource.WorkflowStorageFailed -> R.string.workflow_storage_failed
    StringResource.MergeError -> R.string.merge_error
    StringResource.SplitError -> R.string.split_error
    StringResource.ReorderError -> R.string.reorder_error
    StringResource.RotateError -> R.string.rotate_error
    StringResource.DeletePagesError -> R.string.delete_pages_error
    StringResource.ExtractPagesError -> R.string.extract_pages_error
    StringResource.DuplicatePagesError -> R.string.duplicate_pages_error
    StringResource.PageNumbersError -> R.string.page_numbers_error
    StringResource.WatermarkError -> R.string.watermark_error
    StringResource.CompressPdfError -> R.string.compress_pdf_error
    StringResource.ProtectPdfError -> R.string.protect_pdf_error
    StringResource.UnlockPdfError -> R.string.unlock_pdf_error
    StringResource.SignatureError -> R.string.signature_error
    StringResource.RemoveTextLayerNotSearchable -> R.string.remove_text_layer_not_searchable
    StringResource.RemoveTextLayerError -> R.string.remove_text_layer_error
    StringResource.RemovePasswordRequiresInput -> R.string.remove_password_requires_input
    StringResource.RemovePasswordError -> R.string.remove_password_error
    StringResource.RestrictUsageError -> R.string.restrict_usage_error
    StringResource.RedactNoAreas -> R.string.redact_no_areas
    StringResource.RedactError -> R.string.redact_error
    StringResource.HighlightNoStrokes -> R.string.highlight_no_strokes
    StringResource.HighlightError -> R.string.highlight_error
    StringResource.AnnotateNoItems -> R.string.annotate_no_items
    StringResource.AnnotateError -> R.string.annotate_error
    StringResource.GrayscaleError -> R.string.grayscale_error
    StringResource.MetadataError -> R.string.metadata_error
    StringResource.AppendTargetEncrypted -> R.string.append_target_encrypted
    StringResource.AppendError -> R.string.append_error
    StringResource.FormNotFillable -> R.string.form_fill_not_fillable
    StringResource.FormXfaUnsupported -> R.string.form_fill_xfa_unsupported
    StringResource.FormSignedLocked -> R.string.form_fill_signed_locked
    StringResource.FormRequiredMissing -> R.string.form_fill_required_missing
    StringResource.FormFillError -> R.string.form_fill_error
    StringResource.OcrModelDownloadFailed -> R.string.ocr_model_download_failed
    StringResource.SearchableFailed -> R.string.searchable_failed
}

class TestDispatcherProvider(
    dispatcher: CoroutineDispatcher
) : DispatcherProvider {
    override val main: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
}

class TestStorageProvider(
    private val rootDir: File
) : StorageProvider {
    override fun scansDir(): File = File(rootDir, "scans").apply { mkdirs() }

    override fun tempDir(): File = File(rootDir, "temp").apply { mkdirs() }
}
