package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.util.OcrModelInstaller
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
import info.meuse24.pdf_scanner.util.QrCodeScanner
import org.mockito.Mockito.mock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ScanQrCodesUseCaseTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `verschluesselte PDFs werden nicht gescannt`() = runTest {
        val scanner = FakeQrCodeScanner()
        val useCase = ScanQrCodesUseCase(scanner)
        val pdfFile = tmpFolder.newFile("encrypted.pdf")
        val record = record(pdfFile, isEncrypted = true)

        val result = useCase(record)

        assertTrue(result.isEmpty())
        assertEquals(0, scanner.invocationCount)
    }

    @Test
    fun `fehlende Datei wird als leeres Ergebnis behandelt`() = runTest {
        val scanner = FakeQrCodeScanner()
        val useCase = ScanQrCodesUseCase(scanner)
        val record = record(File(tmpFolder.root, "missing.pdf"), isEncrypted = false)

        val result = useCase(record)

        assertTrue(result.isEmpty())
        assertEquals(0, scanner.invocationCount)
    }

    @Test
    fun `gibt Scanner-Ergebnisse fuer vorhandene Datei zurueck`() = runTest {
        val pdfFile = tmpFolder.newFile("scan.pdf")
        val expected = listOf(
            QrCodeResult(
                rawValue = "https://example.com",
                valueType = 0,
                displayUrl = "https://example.com",
                wifiSsid = null,
                wifiPassword = null,
                pageNumber = 2
            )
        )
        val scanner = FakeQrCodeScanner(results = expected)
        val useCase = ScanQrCodesUseCase(scanner)

        val result = useCase(record(pdfFile, isEncrypted = false))

        assertEquals(expected, result)
        assertEquals(1, scanner.invocationCount)
        assertEquals(pdfFile.absolutePath, scanner.lastFile?.absolutePath)
    }

    @Test
    fun `leitet Progress-Callbacks weiter`() = runTest {
        val pdfFile = tmpFolder.newFile("scan.pdf")
        val scanner = FakeQrCodeScanner(
            onScan = { _, onProgress ->
                onProgress(1, 3)
                onProgress(2, 3)
            }
        )
        val useCase = ScanQrCodesUseCase(scanner)
        val progressEvents = mutableListOf<Pair<Int, Int>>()

        useCase(record(pdfFile, isEncrypted = false)) { page, total ->
            progressEvents += page to total
        }

        assertFalse(progressEvents.isEmpty())
        assertEquals(listOf(1 to 3, 2 to 3), progressEvents)
    }

    private fun record(file: File, isEncrypted: Boolean) = Document(
        id = 1L,
        filename = file.nameWithoutExtension,
        filepath = file.absolutePath,
        timestamp = 0L,
        pageCount = 3,
        fileSize = 0L,
        isEncrypted = isEncrypted
    )
}

private class FakeQrCodeScanner(
    private val results: List<QrCodeResult> = emptyList(),
    private val onScan: suspend (File, (Int, Int) -> Unit) -> Unit = { _, _ -> }
) : QrCodeScanner(mock(OcrModelInstaller::class.java)) {
    var invocationCount: Int = 0
    var lastFile: File? = null

    override suspend fun scan(
        pdfFile: File,
        onProgress: (page: Int, total: Int) -> Unit,
        onStatus: (OcrPipelineStatus) -> Unit
    ): List<QrCodeResult> {
        invocationCount += 1
        lastFile = pdfFile
        onScan(pdfFile, onProgress)
        return results
    }
}

