package info.meuse24.pdf_scanner.domain.workflow

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkflowErrorMapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun map(error: ScanWorkflowError): String = when (error) {
        ScanWorkflowError.NothingSelected -> context.getString(R.string.workflow_nothing_selected)
        ScanWorkflowError.NotEnoughScans -> context.getString(R.string.merge_not_enough_scans)
        ScanWorkflowError.NoEligibleScans -> context.getString(R.string.searchable_nothing_to_do)
        ScanWorkflowError.InvalidSplitSelection -> context.getString(R.string.split_no_points)
        ScanWorkflowError.InvalidPageSelection -> context.getString(R.string.page_selection_invalid)
        ScanWorkflowError.InvalidPageOrder -> context.getString(R.string.reorder_invalid_order)
        ScanWorkflowError.InvalidWatermarkText -> context.getString(R.string.watermark_invalid)
        ScanWorkflowError.SignatureRequired -> context.getString(R.string.signature_required)
        ScanWorkflowError.InvalidSignatureScale -> context.getString(R.string.signature_scale_invalid)
        ScanWorkflowError.CompressionUnsupportedForSearchablePdf -> context.getString(R.string.compress_pdf_searchable_unsupported)
        ScanWorkflowError.ProtectedPdfUnsupported -> context.getString(R.string.protected_pdf_unsupported)
        ScanWorkflowError.PasswordRequired -> context.getString(R.string.password_required)
        ScanWorkflowError.WrongPassword -> context.getString(R.string.password_wrong)
        ScanWorkflowError.AlreadyProtected -> context.getString(R.string.protect_pdf_already_protected)
        ScanWorkflowError.NotProtected -> context.getString(R.string.unlock_pdf_not_protected)
        ScanWorkflowError.CannotDeleteAllPages -> context.getString(R.string.delete_pages_all_error)
        is ScanWorkflowError.MissingFiles -> context.getString(R.string.workflow_missing_files)
        is ScanWorkflowError.StorageWriteFailed -> context.getString(R.string.workflow_storage_failed)
        is ScanWorkflowError.OcrFailed -> context.getString(R.string.searchable_failed)
        is ScanWorkflowError.MergeFailed -> context.getString(R.string.merge_error)
        is ScanWorkflowError.SplitFailed -> context.getString(R.string.split_error)
        is ScanWorkflowError.ReorderFailed -> context.getString(R.string.reorder_error)
        is ScanWorkflowError.RotateFailed -> context.getString(R.string.rotate_error)
        is ScanWorkflowError.DeletePagesFailed -> context.getString(R.string.delete_pages_error)
        is ScanWorkflowError.ExtractPagesFailed -> context.getString(R.string.extract_pages_error)
        is ScanWorkflowError.DuplicatePagesFailed -> context.getString(R.string.duplicate_pages_error)
        is ScanWorkflowError.PageNumbersFailed -> context.getString(R.string.page_numbers_error)
        is ScanWorkflowError.TextWatermarkFailed -> context.getString(R.string.watermark_error)
        is ScanWorkflowError.CompressionFailed -> context.getString(R.string.compress_pdf_error)
        is ScanWorkflowError.ProtectFailed -> context.getString(R.string.protect_pdf_error)
        is ScanWorkflowError.UnlockFailed -> context.getString(R.string.unlock_pdf_error)
        is ScanWorkflowError.SignatureFailed -> context.getString(R.string.signature_error)
        ScanWorkflowError.NotSearchable -> context.getString(R.string.remove_text_layer_not_searchable)
        is ScanWorkflowError.RemoveTextLayerFailed -> context.getString(R.string.remove_text_layer_error)
        ScanWorkflowError.PasswordRequiredToRemove -> context.getString(R.string.remove_password_requires_input)
        is ScanWorkflowError.RemovePasswordFailed -> context.getString(R.string.remove_password_error)
        is ScanWorkflowError.UsageRestrictionFailed -> context.getString(R.string.restrict_usage_error)
        ScanWorkflowError.NoHighlightStrokes -> context.getString(R.string.highlight_no_strokes)
        is ScanWorkflowError.HighlightFailed -> context.getString(R.string.highlight_error)
        ScanWorkflowError.NoAnnotations -> context.getString(R.string.annotate_no_items)
        is ScanWorkflowError.AnnotateFailed -> context.getString(R.string.annotate_error)
        is ScanWorkflowError.GrayscaleFailed -> context.getString(R.string.grayscale_error)
        is ScanWorkflowError.PdfMetadataFailed -> context.getString(R.string.metadata_error)
    }
}
