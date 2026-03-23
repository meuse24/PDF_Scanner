package info.meuse24.pdf_scanner.domain.workflow

sealed interface WorkflowResult<out T> {
    data class Success<T>(val value: T) : WorkflowResult<T>
    data class Failure(val error: ScanWorkflowError) : WorkflowResult<Nothing>
}
