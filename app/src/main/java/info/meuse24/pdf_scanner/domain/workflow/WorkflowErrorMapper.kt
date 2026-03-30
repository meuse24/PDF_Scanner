package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.util.OcrModelInstallException
import info.meuse24.pdf_scanner.util.ResourceProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkflowErrorMapper @Inject constructor(
    private val resourceProvider: ResourceProvider
) {
    fun map(error: ScanWorkflowError): String = when (error) {
        ScanWorkflowError.NothingSelected -> resourceProvider.getString(R.string.workflow_nothing_selected)
        ScanWorkflowError.NotEnoughScans -> resourceProvider.getString(R.string.merge_not_enough_scans)
        ScanWorkflowError.NoEligibleScans -> resourceProvider.getString(R.string.searchable_nothing_to_do)
        ScanWorkflowError.SearchableUnsupportedForScript -> resourceProvider.getString(R.string.searchable_unsupported_script)
        ScanWorkflowError.InvalidSplitSelection -> resourceProvider.getString(R.string.split_no_points)
        ScanWorkflowError.InvalidPageSelection -> resourceProvider.getString(R.string.page_selection_invalid)
        ScanWorkflowError.InvalidPageOrder -> resourceProvider.getString(R.string.reorder_invalid_order)
        ScanWorkflowError.InvalidWatermarkText -> resourceProvider.getString(R.string.watermark_invalid)
        ScanWorkflowError.SignatureRequired -> resourceProvider.getString(R.string.signature_required)
        ScanWorkflowError.InvalidSignatureScale -> resourceProvider.getString(R.string.signature_scale_invalid)
        ScanWorkflowError.CompressionUnsupportedForSearchablePdf -> resourceProvider.getString(R.string.compress_pdf_searchable_unsupported)
        ScanWorkflowError.ProtectedPdfUnsupported -> resourceProvider.getString(R.string.protected_pdf_unsupported)
        ScanWorkflowError.PasswordRequired -> resourceProvider.getString(R.string.password_required)
        ScanWorkflowError.WrongPassword -> resourceProvider.getString(R.string.password_wrong)
        ScanWorkflowError.AlreadyProtected -> resourceProvider.getString(R.string.protect_pdf_already_protected)
        ScanWorkflowError.NotProtected -> resourceProvider.getString(R.string.unlock_pdf_not_protected)
        ScanWorkflowError.CannotDeleteAllPages -> resourceProvider.getString(R.string.delete_pages_all_error)
        is ScanWorkflowError.MissingFiles -> resourceProvider.getString(R.string.workflow_missing_files)
        is ScanWorkflowError.StorageWriteFailed -> resourceProvider.getString(R.string.workflow_storage_failed)
        is ScanWorkflowError.OcrFailed -> mapOcrFailed(error)
        is ScanWorkflowError.MergeFailed -> resourceProvider.getString(R.string.merge_error)
        is ScanWorkflowError.SplitFailed -> resourceProvider.getString(R.string.split_error)
        is ScanWorkflowError.ReorderFailed -> resourceProvider.getString(R.string.reorder_error)
        is ScanWorkflowError.RotateFailed -> resourceProvider.getString(R.string.rotate_error)
        is ScanWorkflowError.DeletePagesFailed -> resourceProvider.getString(R.string.delete_pages_error)
        is ScanWorkflowError.ExtractPagesFailed -> resourceProvider.getString(R.string.extract_pages_error)
        is ScanWorkflowError.DuplicatePagesFailed -> resourceProvider.getString(R.string.duplicate_pages_error)
        is ScanWorkflowError.PageNumbersFailed -> resourceProvider.getString(R.string.page_numbers_error)
        is ScanWorkflowError.TextWatermarkFailed -> resourceProvider.getString(R.string.watermark_error)
        is ScanWorkflowError.CompressionFailed -> resourceProvider.getString(R.string.compress_pdf_error)
        is ScanWorkflowError.ProtectFailed -> resourceProvider.getString(R.string.protect_pdf_error)
        is ScanWorkflowError.UnlockFailed -> resourceProvider.getString(R.string.unlock_pdf_error)
        is ScanWorkflowError.SignatureFailed -> resourceProvider.getString(R.string.signature_error)
        ScanWorkflowError.NotSearchable -> resourceProvider.getString(R.string.remove_text_layer_not_searchable)
        is ScanWorkflowError.RemoveTextLayerFailed -> resourceProvider.getString(R.string.remove_text_layer_error)
        ScanWorkflowError.PasswordRequiredToRemove -> resourceProvider.getString(R.string.remove_password_requires_input)
        is ScanWorkflowError.RemovePasswordFailed -> resourceProvider.getString(R.string.remove_password_error)
        is ScanWorkflowError.UsageRestrictionFailed -> resourceProvider.getString(R.string.restrict_usage_error)
        ScanWorkflowError.NoRedactionAreas -> resourceProvider.getString(R.string.redact_no_areas)
        is ScanWorkflowError.RedactionFailed -> resourceProvider.getString(R.string.redact_error)
        ScanWorkflowError.NoHighlightStrokes -> resourceProvider.getString(R.string.highlight_no_strokes)
        is ScanWorkflowError.HighlightFailed -> resourceProvider.getString(R.string.highlight_error)
        ScanWorkflowError.NoAnnotations -> resourceProvider.getString(R.string.annotate_no_items)
        is ScanWorkflowError.AnnotateFailed -> resourceProvider.getString(R.string.annotate_error)
        is ScanWorkflowError.GrayscaleFailed -> resourceProvider.getString(R.string.grayscale_error)
        is ScanWorkflowError.PdfMetadataFailed -> resourceProvider.getString(R.string.metadata_error)
    }

    private fun mapOcrFailed(error: ScanWorkflowError.OcrFailed): String {
        return if (error.cause is OcrModelInstallException) {
            resourceProvider.getString(R.string.ocr_model_download_failed)
        } else {
            resourceProvider.getString(R.string.searchable_failed)
        }
    }
}
