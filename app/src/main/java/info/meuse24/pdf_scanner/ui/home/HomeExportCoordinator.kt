package info.meuse24.pdf_scanner.ui.home

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.usecase.CheckPrintPageSizeWarningUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExportAsJpgUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExportDocxUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExportOcrTextUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExportScanUseCase
import info.meuse24.pdf_scanner.domain.usecase.JpgExportResult
import info.meuse24.pdf_scanner.ui.print.PrintRequestCoordinator
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class HomeExportCoordinator @Inject constructor(
    private val exportScanUseCase: ExportScanUseCase,
    private val exportAsJpgUseCase: ExportAsJpgUseCase,
    private val exportDocxUseCase: ExportDocxUseCase,
    private val exportOcrTextUseCase: ExportOcrTextUseCase,
    checkPrintPageSizeWarningUseCase: CheckPrintPageSizeWarningUseCase
) {
    private val printCoordinator = PrintRequestCoordinator(checkPrintPageSizeWarningUseCase)

    val pendingPrintDocument = printCoordinator.pendingPrintDocument
    val printRequests = printCoordinator.printRequests

    suspend fun exportPdf(record: Document): String = exportScanUseCase(record)

    suspend fun exportAsJpg(record: Document): JpgExportResult = exportAsJpgUseCase(record)

    suspend fun exportDocx(records: List<Document>): String = exportDocxUseCase(records)

    suspend fun exportOcrText(records: List<Document>): String = exportOcrTextUseCase(records)

    fun requestPrint(scope: CoroutineScope, record: Document) {
        printCoordinator.requestPrint(scope, record)
    }

    fun confirmPrintWarning(scope: CoroutineScope) {
        printCoordinator.confirmPrintWarning(scope)
    }

    fun dismissPrintWarning() {
        printCoordinator.dismissPrintWarning()
    }
}
