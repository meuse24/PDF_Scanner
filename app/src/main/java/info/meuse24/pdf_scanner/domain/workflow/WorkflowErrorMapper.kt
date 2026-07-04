package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.gateway.ResourceProvider
import info.meuse24.pdf_scanner.domain.gateway.StringResource
import info.meuse24.pdf_scanner.domain.model.OcrModelInstallException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkflowErrorMapper @Inject constructor(
    private val resourceProvider: ResourceProvider
) {
    fun map(error: ScanWorkflowError): String = when (error) {
        ScanWorkflowError.NothingSelected -> resourceProvider.getString(StringResource.WorkflowNothingSelected)
        ScanWorkflowError.NotEnoughScans -> resourceProvider.getString(StringResource.MergeNotEnoughScans)
        ScanWorkflowError.NoEligibleScans -> resourceProvider.getString(StringResource.SearchableNothingToDo)
        ScanWorkflowError.SearchableUnsupportedForScript -> resourceProvider.getString(StringResource.SearchableUnsupportedScript)
        ScanWorkflowError.InvalidSplitSelection -> resourceProvider.getString(StringResource.SplitNoPoints)
        ScanWorkflowError.InvalidPageSelection -> resourceProvider.getString(StringResource.PageSelectionInvalid)
        ScanWorkflowError.InvalidPageOrder -> resourceProvider.getString(StringResource.ReorderInvalidOrder)
        ScanWorkflowError.InvalidWatermarkText -> resourceProvider.getString(StringResource.WatermarkInvalid)
        ScanWorkflowError.SignatureRequired -> resourceProvider.getString(StringResource.SignatureRequired)
        ScanWorkflowError.InvalidSignatureScale -> resourceProvider.getString(StringResource.SignatureScaleInvalid)
        ScanWorkflowError.CompressionUnsupportedForSearchablePdf -> resourceProvider.getString(StringResource.CompressPdfSearchableUnsupported)
        ScanWorkflowError.ProtectedPdfUnsupported -> resourceProvider.getString(StringResource.ProtectedPdfUnsupported)
        ScanWorkflowError.PasswordRequired -> resourceProvider.getString(StringResource.PasswordRequired)
        ScanWorkflowError.WrongPassword -> resourceProvider.getString(StringResource.PasswordWrong)
        ScanWorkflowError.AlreadyProtected -> resourceProvider.getString(StringResource.ProtectPdfAlreadyProtected)
        ScanWorkflowError.NotProtected -> resourceProvider.getString(StringResource.UnlockPdfNotProtected)
        ScanWorkflowError.CannotDeleteAllPages -> resourceProvider.getString(StringResource.DeletePagesAllError)
        is ScanWorkflowError.MissingFiles -> resourceProvider.getString(StringResource.WorkflowMissingFiles)
        is ScanWorkflowError.StorageWriteFailed -> resourceProvider.getString(StringResource.WorkflowStorageFailed)
        is ScanWorkflowError.OcrFailed -> mapOcrFailed(error)
        is ScanWorkflowError.MergeFailed -> resourceProvider.getString(StringResource.MergeError)
        is ScanWorkflowError.SplitFailed -> resourceProvider.getString(StringResource.SplitError)
        is ScanWorkflowError.ReorderFailed -> resourceProvider.getString(StringResource.ReorderError)
        is ScanWorkflowError.RotateFailed -> resourceProvider.getString(StringResource.RotateError)
        is ScanWorkflowError.DeletePagesFailed -> resourceProvider.getString(StringResource.DeletePagesError)
        is ScanWorkflowError.ExtractPagesFailed -> resourceProvider.getString(StringResource.ExtractPagesError)
        is ScanWorkflowError.DuplicatePagesFailed -> resourceProvider.getString(StringResource.DuplicatePagesError)
        is ScanWorkflowError.PageNumbersFailed -> resourceProvider.getString(StringResource.PageNumbersError)
        is ScanWorkflowError.TextWatermarkFailed -> resourceProvider.getString(StringResource.WatermarkError)
        is ScanWorkflowError.CompressionFailed -> resourceProvider.getString(StringResource.CompressPdfError)
        is ScanWorkflowError.ProtectFailed -> resourceProvider.getString(StringResource.ProtectPdfError)
        is ScanWorkflowError.UnlockFailed -> resourceProvider.getString(StringResource.UnlockPdfError)
        is ScanWorkflowError.SignatureFailed -> resourceProvider.getString(StringResource.SignatureError)
        ScanWorkflowError.NotSearchable -> resourceProvider.getString(StringResource.RemoveTextLayerNotSearchable)
        is ScanWorkflowError.RemoveTextLayerFailed -> resourceProvider.getString(StringResource.RemoveTextLayerError)
        ScanWorkflowError.PasswordRequiredToRemove -> resourceProvider.getString(StringResource.RemovePasswordRequiresInput)
        is ScanWorkflowError.RemovePasswordFailed -> resourceProvider.getString(StringResource.RemovePasswordError)
        is ScanWorkflowError.UsageRestrictionFailed -> resourceProvider.getString(StringResource.RestrictUsageError)
        ScanWorkflowError.NoRedactionAreas -> resourceProvider.getString(StringResource.RedactNoAreas)
        is ScanWorkflowError.RedactionFailed -> resourceProvider.getString(StringResource.RedactError)
        ScanWorkflowError.NoHighlightStrokes -> resourceProvider.getString(StringResource.HighlightNoStrokes)
        is ScanWorkflowError.HighlightFailed -> resourceProvider.getString(StringResource.HighlightError)
        ScanWorkflowError.NoAnnotations -> resourceProvider.getString(StringResource.AnnotateNoItems)
        is ScanWorkflowError.AnnotateFailed -> resourceProvider.getString(StringResource.AnnotateError)
        is ScanWorkflowError.GrayscaleFailed -> resourceProvider.getString(StringResource.GrayscaleError)
        is ScanWorkflowError.PdfMetadataFailed -> resourceProvider.getString(StringResource.MetadataError)
        ScanWorkflowError.AppendTargetEncrypted -> resourceProvider.getString(StringResource.AppendTargetEncrypted)
        ScanWorkflowError.AppendSourceEncrypted -> resourceProvider.getString(StringResource.AppendTargetEncrypted)
        is ScanWorkflowError.AppendFailed -> resourceProvider.getString(StringResource.AppendError)
        ScanWorkflowError.FormNotFillable -> resourceProvider.getString(StringResource.FormNotFillable)
        ScanWorkflowError.FormXfaUnsupported -> resourceProvider.getString(StringResource.FormXfaUnsupported)
        ScanWorkflowError.FormSignedLocked -> resourceProvider.getString(StringResource.FormSignedLocked)
        is ScanWorkflowError.RequiredFormFieldsMissing ->
            resourceProvider.getString(StringResource.FormRequiredMissing)
        is ScanWorkflowError.FormFillFailed -> resourceProvider.getString(StringResource.FormFillError)
    }

    private fun mapOcrFailed(error: ScanWorkflowError.OcrFailed): String {
        return if (error.cause is OcrModelInstallException) {
            resourceProvider.getString(StringResource.OcrModelDownloadFailed)
        } else {
            resourceProvider.getString(StringResource.SearchableFailed)
        }
    }
}
