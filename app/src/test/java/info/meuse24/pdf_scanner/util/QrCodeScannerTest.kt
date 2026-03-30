package info.meuse24.pdf_scanner.util

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeScannerTest {

    @Test
    fun `useQrScannerResource schliesst Ressource auch bei Exception`() = runTest {
        val resource = FakeQrScannerResource()

        runCatching {
            useQrScannerResource(resource) {
                throw IllegalStateException("boom")
            }
        }

        assertTrue(resource.closed)
    }
}

private class FakeQrScannerResource : QrScannerResource {
    var closed: Boolean = false

    override fun close() {
        closed = true
    }
}
