package info.meuse24.pdf_scanner.domain.workflow

sealed interface ScanWorkflowError {
    val cause: Throwable? get() = null

    data object NothingSelected : ScanWorkflowError
    data object NotEnoughScans : ScanWorkflowError
    data object NoEligibleScans : ScanWorkflowError
    data object SearchableUnsupportedForScript : ScanWorkflowError
    data object InvalidSplitSelection : ScanWorkflowError
    data object InvalidPageSelection : ScanWorkflowError
    data object InvalidPageOrder : ScanWorkflowError
    data object InvalidWatermarkText : ScanWorkflowError
    data object SignatureRequired : ScanWorkflowError
    data object InvalidSignatureScale : ScanWorkflowError
    data object CompressionUnsupportedForSearchablePdf : ScanWorkflowError
    data object ProtectedPdfUnsupported : ScanWorkflowError
    data object PasswordRequired : ScanWorkflowError
    data object WrongPassword : ScanWorkflowError
    data object AlreadyProtected : ScanWorkflowError
    data object NotProtected : ScanWorkflowError
    data object CannotDeleteAllPages : ScanWorkflowError
    data object NotSearchable : ScanWorkflowError
    data object PasswordRequiredToRemove : ScanWorkflowError
    data class MissingFiles(val filenames: List<String>) : ScanWorkflowError
    data class StorageWriteFailed(override val cause: Throwable) : ScanWorkflowError
    data class OcrFailed(override val cause: Throwable) : ScanWorkflowError
    data class MergeFailed(override val cause: Throwable) : ScanWorkflowError
    data class SplitFailed(override val cause: Throwable) : ScanWorkflowError
    data class ReorderFailed(override val cause: Throwable) : ScanWorkflowError
    data class RotateFailed(override val cause: Throwable) : ScanWorkflowError
    data class DeletePagesFailed(override val cause: Throwable) : ScanWorkflowError
    data class ExtractPagesFailed(override val cause: Throwable) : ScanWorkflowError
    data class DuplicatePagesFailed(override val cause: Throwable) : ScanWorkflowError
    data class PageNumbersFailed(override val cause: Throwable) : ScanWorkflowError
    data class TextWatermarkFailed(override val cause: Throwable) : ScanWorkflowError
    data class CompressionFailed(override val cause: Throwable) : ScanWorkflowError
    data class ProtectFailed(override val cause: Throwable) : ScanWorkflowError
    data class UnlockFailed(override val cause: Throwable) : ScanWorkflowError
    data class SignatureFailed(override val cause: Throwable) : ScanWorkflowError
    data class RemoveTextLayerFailed(override val cause: Throwable) : ScanWorkflowError
    data class RemovePasswordFailed(override val cause: Throwable) : ScanWorkflowError
    data class UsageRestrictionFailed(override val cause: Throwable) : ScanWorkflowError
    data object NoRedactionAreas : ScanWorkflowError
    data class RedactionFailed(override val cause: Throwable) : ScanWorkflowError
    data object NoHighlightStrokes : ScanWorkflowError
    data class HighlightFailed(override val cause: Throwable) : ScanWorkflowError
    data object NoAnnotations : ScanWorkflowError
    data class AnnotateFailed(override val cause: Throwable) : ScanWorkflowError
    data class GrayscaleFailed(override val cause: Throwable) : ScanWorkflowError
    data class PdfMetadataFailed(override val cause: Throwable) : ScanWorkflowError
    data object AppendTargetEncrypted : ScanWorkflowError
    data class AppendFailed(override val cause: Throwable) : ScanWorkflowError
}
