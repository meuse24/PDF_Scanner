package info.meuse24.pdf_scanner.domain.workflow

sealed interface ScanWorkflowError {
    val cause: Throwable? get() = null

    data object NothingSelected : ScanWorkflowError
    data object NotEnoughScans : ScanWorkflowError
    data object NoEligibleScans : ScanWorkflowError
    data class MissingFiles(val filenames: List<String>) : ScanWorkflowError
    data class StorageWriteFailed(override val cause: Throwable) : ScanWorkflowError
    data class OcrFailed(override val cause: Throwable) : ScanWorkflowError
    data class MergeFailed(override val cause: Throwable) : ScanWorkflowError
}
