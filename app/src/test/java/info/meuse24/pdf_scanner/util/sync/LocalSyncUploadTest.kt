package info.meuse24.pdf_scanner.util.sync

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalSyncUploadTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `copies a valid pdf payload byte for byte`() = runTest {
        val payload = "%PDF-1.7\n%%EOF".toByteArray()
        val destination = temporaryFolder.newFile("upload.pdf")

        ByteReadChannel(payload).copyToPdfFile(destination, maxBytes = 1024)

        assertArrayEquals(payload, destination.readBytes())
    }

    @Test
    fun `rejects payloads without the pdf magic header`() = runTest {
        val destination = temporaryFolder.newFile("upload.pdf")

        assertThrows(UploadNotAPdfException::class.java) {
            kotlinx.coroutines.runBlocking {
                ByteReadChannel("not a pdf at all".toByteArray()).copyToPdfFile(destination, maxBytes = 1024)
            }
        }
    }

    @Test
    fun `rejects payloads shorter than the magic header`() = runTest {
        val destination = temporaryFolder.newFile("upload.pdf")

        assertThrows(UploadNotAPdfException::class.java) {
            kotlinx.coroutines.runBlocking {
                ByteReadChannel("%PD".toByteArray()).copyToPdfFile(destination, maxBytes = 1024)
            }
        }
    }

    @Test
    fun `rejects payloads larger than the configured limit`() = runTest {
        val payload = "%PDF-1.7".toByteArray() + ByteArray(100) { 'a'.code.toByte() }
        val destination = temporaryFolder.newFile("upload.pdf")

        assertThrows(UploadTooLargeException::class.java) {
            kotlinx.coroutines.runBlocking {
                ByteReadChannel(payload).copyToPdfFile(destination, maxBytes = 20)
            }
        }
    }
}
