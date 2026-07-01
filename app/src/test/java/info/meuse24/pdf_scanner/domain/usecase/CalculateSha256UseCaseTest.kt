package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.testutil.TestDispatcherProvider
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CalculateSha256UseCaseTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val useCase = CalculateSha256UseCase(TestDispatcherProvider(testDispatcher))

    @Test
    fun `calculates known sha256 test vector without requiring a pdf`() = runTest(testDispatcher) {
        val file = temporaryFolder.newFile("plain-text.bin").apply {
            writeBytes("abc".encodeToByteArray())
        }

        val result = useCase(documentFor(file))

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            result
        )
        assertTrue(result.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `calculates sha256 for empty file`() = runTest(testDispatcher) {
        val file = temporaryFolder.newFile("empty.pdf")

        val result = useCase(documentFor(file))

        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            result
        )
    }

    @Test
    fun `streams binary file larger than one buffer`() = runTest(testDispatcher) {
        val bytes = ByteArray(150_000) { index -> (index * 31).toByte() }
        val file = temporaryFolder.newFile("large.pdf").apply { writeBytes(bytes) }
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

        val result = useCase(documentFor(file))

        assertEquals(expected, result)
    }

    @Test
    fun `throws file not found for missing path`() = runTest(testDispatcher) {
        val missingFile = File(temporaryFolder.root, "missing.pdf")

        try {
            useCase(documentFor(missingFile))
            throw AssertionError("Expected FileNotFoundException")
        } catch (_: FileNotFoundException) {
            // Expected.
        }
    }

    private fun documentFor(file: File) = Document(
        filename = file.nameWithoutExtension,
        filepath = file.absolutePath,
        timestamp = 0L,
        pageCount = 1,
        fileSize = file.length()
    )
}
