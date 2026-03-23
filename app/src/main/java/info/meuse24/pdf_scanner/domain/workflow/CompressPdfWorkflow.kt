package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.usecase.CompressPdfUseCase
import info.meuse24.pdf_scanner.domain.usecase.PdfCompressionPreset
import info.meuse24.pdf_scanner.util.PdfEditor
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class CompressPdfWorkflowResult(
    val outputFilename: String
)

class CompressPdfWorkflow @Inject constructor(
    private val compressPdfUseCase: CompressPdfUseCase,
    private val pdfEditor: PdfEditor
) {
    suspend operator fun invoke(
        record: ScanRecord,
        preset: PdfCompressionPreset,
        scansDir: File
    ): WorkflowResult<CompressPdfWorkflowResult> {
        val input = File(record.filepath)
        if (!input.exists()) {
            return WorkflowResult.Failure(ScanWorkflowError.MissingFiles(listOf(record.filename)))
        }
        if (record.isSearchable) {
            return WorkflowResult.Failure(ScanWorkflowError.CompressionUnsupportedForSearchablePdf)
        }
        if (pdfEditor.isPdfEncrypted(input)) {
            return WorkflowResult.Failure(ScanWorkflowError.ProtectedPdfUnsupported)
        }

        return try {
            WorkflowResult.Success(
                CompressPdfWorkflowResult(
                    outputFilename = compressPdfUseCase(record, preset, scansDir)
                )
            )
        } catch (e: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
        } catch (t: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.CompressionFailed(t))
        }
    }
}
