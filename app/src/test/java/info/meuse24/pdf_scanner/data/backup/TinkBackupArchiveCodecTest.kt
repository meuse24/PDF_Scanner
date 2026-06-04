package info.meuse24.pdf_scanner.data.backup

import info.meuse24.pdf_scanner.domain.backup.BackupArchiveException
import info.meuse24.pdf_scanner.domain.backup.BackupFailure
import info.meuse24.pdf_scanner.domain.backup.BackupKdfParameters
import info.meuse24.pdf_scanner.domain.gateway.BackupKeyDeriver
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class TinkBackupArchiveCodecTest {
    private val codec = TinkBackupArchiveCodec(DeterministicTestKeyDeriver())
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `round trip keeps streamed payload bytes intact`() {
        val payload = ByteArray(128 * 1024) { index -> (index % 251).toByte() }
        val output = ByteArrayOutputStream()

        val header = codec.writeBackup(
            outputStream = output,
            passphrase = "correct horse battery staple".toCharArray()
        ) { stream ->
            stream.write(payload)
        }

        assertEquals(1, header.formatVersion)
        assertTrue(output.toByteArray().startsWith("M24PDFBACKUP".encodeToByteArray()))
        assertFalse(output.toByteArray().containsSlice(payload.copyOfRange(0, 64)))

        var restored = ByteArray(0)
        codec.readBackup(
            inputStream = ByteArrayInputStream(output.toByteArray()),
            passphrase = "correct horse battery staple".toCharArray()
        ) { stream, _ ->
            restored = stream.readBytes()
        }

        assertArrayEquals(payload, restored)
    }

    @Test
    fun `wrong passphrase fails with typed failure`() {
        val backup = ByteArrayOutputStream()
        codec.writeBackup(backup, "right".toCharArray()) { stream ->
            stream.write("payload".encodeToByteArray())
        }

        val error = assertFailsWithBackupArchiveException {
            codec.readBackup(ByteArrayInputStream(backup.toByteArray()), "wrong".toCharArray()) { stream, _ ->
                stream.readBytes()
            }
        }

        assertEquals(BackupFailure.WrongPassphrase, error.failure)
    }

    @Test
    fun `truncated archive fails before returning success`() {
        val backup = ByteArrayOutputStream()
        codec.writeBackup(backup, "secret".toCharArray()) { stream ->
            stream.write(ByteArray(8192) { 7 })
        }
        val truncated = backup.toByteArray().copyOf(backup.size() - 8)

        val error = assertFailsWithBackupArchiveException {
            codec.readBackup(ByteArrayInputStream(truncated), "secret".toCharArray()) { stream, _ ->
                stream.readBytes()
            }
        }

        assertEquals(BackupFailure.CorruptArchive, error.failure)
    }

    @Test
    fun `invalid magic bytes fail as unsupported format`() {
        val error = assertFailsWithBackupArchiveException {
            codec.readBackup(ByteArrayInputStream("not-a-backup".encodeToByteArray()), "secret".toCharArray()) { _, _ -> }
        }

        assertEquals(BackupFailure.UnsupportedFormat, error.failure)
    }

    @Test
    fun `manipulated header fails payload authentication`() {
        val backup = ByteArrayOutputStream()
        codec.writeBackup(backup, "secret".toCharArray()) { stream ->
            stream.write("payload".encodeToByteArray())
        }
        val manipulated = backup.toByteArray().replaceHeader { header ->
            header.with("createdAtEpochMillis", JsonPrimitive(123456789L))
        }

        val error = assertFailsWithBackupArchiveException {
            codec.readBackup(ByteArrayInputStream(manipulated), "secret".toCharArray()) { stream, _ ->
                stream.readBytes()
            }
        }

        assertEquals(BackupFailure.CorruptArchive, error.failure)
    }

    @Test
    fun `unsafe kdf parameters fail before key derivation`() {
        val countingDeriver = CountingBackupKeyDeriver()
        val backup = ByteArrayOutputStream()
        TinkBackupArchiveCodec(countingDeriver).writeBackup(backup, "secret".toCharArray()) { stream ->
            stream.write("payload".encodeToByteArray())
        }
        assertEquals(1, countingDeriver.invocations)
        countingDeriver.invocations = 0
        val manipulated = backup.toByteArray().replaceHeader { header ->
            header.withNestedObject("kdf", "memoryKiB", JsonPrimitive(4 * 1024 * 1024))
        }

        val error = assertFailsWithBackupArchiveException {
            TinkBackupArchiveCodec(countingDeriver).readBackup(
                ByteArrayInputStream(manipulated),
                "secret".toCharArray()
            ) { stream, _ ->
                stream.readBytes()
            }
        }

        assertEquals(BackupFailure.CorruptArchive, error.failure)
        assertEquals(0, countingDeriver.invocations)
    }

    @Test
    fun `manipulated payload fails authentication`() {
        val backup = ByteArrayOutputStream()
        codec.writeBackup(backup, "secret".toCharArray()) { stream ->
            stream.write(ByteArray(64 * 1024) { index -> index.toByte() })
        }
        val manipulated = backup.toByteArray().also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last() + 1).toByte()
        }

        val error = assertFailsWithBackupArchiveException {
            codec.readBackup(ByteArrayInputStream(manipulated), "secret".toCharArray()) { stream, _ ->
                stream.readBytes()
            }
        }

        assertEquals(BackupFailure.CorruptArchive, error.failure)
    }

    @Test
    fun `multi megabyte payload round trip stays stream compatible`() {
        val payloadSize = 8 * 1024 * 1024
        val backup = ByteArrayOutputStream()
        codec.writeBackup(backup, "secret".toCharArray()) { stream ->
            writePattern(stream, payloadSize)
        }
        val expectedDigest = patternDigest(payloadSize)

        var restoredDigest = ByteArray(0)
        codec.readBackup(ByteArrayInputStream(backup.toByteArray()), "secret".toCharArray()) { stream, _ ->
            restoredDigest = stream.sha256Digest()
        }

        assertArrayEquals(expectedDigest, restoredDigest)
    }

    private fun assertFailsWithBackupArchiveException(block: () -> Unit): BackupArchiveException {
        return try {
            block()
            throw AssertionError("Expected BackupArchiveException")
        } catch (e: BackupArchiveException) {
            e
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && copyOf(prefix.size).contentEquals(prefix)

    private fun ByteArray.containsSlice(slice: ByteArray): Boolean {
        if (slice.isEmpty() || slice.size > size) return false
        return indices.any { index ->
            index + slice.size <= size && slice.indices.all { sliceIndex -> this[index + sliceIndex] == slice[sliceIndex] }
        }
    }

    private fun ByteArray.replaceHeader(transform: (JsonObject) -> JsonObject): ByteArray {
        val magic = "M24PDFBACKUP".encodeToByteArray()
        val headerLengthOffset = magic.size
        val headerLength = ByteBuffer.wrap(this, headerLengthOffset, Int.SIZE_BYTES).int
        val headerStart = headerLengthOffset + Int.SIZE_BYTES
        val headerEnd = headerStart + headerLength
        val header = json.parseToJsonElement(copyOfRange(headerStart, headerEnd).decodeToString()) as JsonObject
        val replacementHeader = json.encodeToString(JsonObject.serializer(), transform(header)).encodeToByteArray()
        return ByteArrayOutputStream().use { output ->
            output.write(magic)
            output.write(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(replacementHeader.size).array())
            output.write(replacementHeader)
            output.write(copyOfRange(headerEnd, size))
            output.toByteArray()
        }
    }

    private fun JsonObject.with(key: String, value: JsonPrimitive): JsonObject =
        buildJsonObject {
            this@with.forEach { (entryKey, entryValue) ->
                put(entryKey, if (entryKey == key) value else entryValue)
            }
        }

    private fun JsonObject.withNestedObject(parentKey: String, key: String, value: JsonPrimitive): JsonObject =
        buildJsonObject {
            this@withNestedObject.forEach { (entryKey, entryValue) ->
                put(
                    entryKey,
                    if (entryKey == parentKey && entryValue is JsonObject) entryValue.with(key, value) else entryValue
                )
            }
        }

    private fun writePattern(stream: java.io.OutputStream, size: Int) {
        val buffer = ByteArray(64 * 1024)
        var written = 0
        while (written < size) {
            val chunkSize = minOf(buffer.size, size - written)
            for (index in 0 until chunkSize) {
                buffer[index] = ((written + index) % 251).toByte()
            }
            stream.write(buffer, 0, chunkSize)
            written += chunkSize
        }
    }

    private fun patternDigest(size: Int): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var written = 0
        while (written < size) {
            val chunkSize = minOf(buffer.size, size - written)
            for (index in 0 until chunkSize) {
                buffer[index] = ((written + index) % 251).toByte()
            }
            digest.update(buffer, 0, chunkSize)
            written += chunkSize
        }
        return digest.digest()
    }

    private fun InputStream.sha256Digest(): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
        return digest.digest()
    }
}

private class DeterministicTestKeyDeriver : BackupKeyDeriver {
    override fun deriveKey(passphrase: CharArray, parameters: BackupKdfParameters): ByteArray {
        val seed = passphrase.concatToString().encodeToByteArray() +
            parameters.saltBase64.encodeToByteArray() +
            parameters.iterations.toString().encodeToByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(seed)
        return ByteArray(parameters.outputBytes) { index -> digest[index % digest.size] }
    }
}

private class CountingBackupKeyDeriver : BackupKeyDeriver {
    var invocations: Int = 0

    override fun deriveKey(passphrase: CharArray, parameters: BackupKdfParameters): ByteArray {
        invocations += 1
        return DeterministicTestKeyDeriver().deriveKey(passphrase, parameters)
    }
}
