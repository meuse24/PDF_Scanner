package info.meuse24.pdf_scanner.util

import android.content.Context
import android.print.PrintManager
import java.io.File

object PdfPrintHelper {
    fun print(
        context: Context,
        pdf: File,
        jobName: String,
        pageCount: Int
    ) {
        context.getSystemService(PrintManager::class.java)?.print(
            jobName,
            PdfPrintAdapter(
                file = pdf,
                jobName = jobName,
                pageCount = pageCount
            ),
            null
        )
    }
}
