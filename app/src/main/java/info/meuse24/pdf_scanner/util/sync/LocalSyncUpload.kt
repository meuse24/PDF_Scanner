package info.meuse24.pdf_scanner.util.sync

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readFully
import java.io.File
import java.io.IOException

const val LOCAL_SYNC_UPLOAD_MAX_BYTES = 25L * 1024 * 1024

private val PDF_MAGIC_BYTES = "%PDF-".toByteArray(Charsets.US_ASCII)

class UploadTooLargeException : IOException("Upload exceeds the ${LOCAL_SYNC_UPLOAD_MAX_BYTES} byte limit")
class UploadNotAPdfException : IOException("Upload is not a PDF file")

/**
 * Streams [this] channel into [destination], verifying the `%PDF-` magic header and
 * enforcing [maxBytes] without ever buffering the whole upload in memory. Throws
 * [UploadNotAPdfException] / [UploadTooLargeException] and leaves a partial file behind
 * for the caller to delete on failure.
 */
suspend fun ByteReadChannel.copyToPdfFile(destination: File, maxBytes: Long = LOCAL_SYNC_UPLOAD_MAX_BYTES) {
    val header = ByteArray(PDF_MAGIC_BYTES.size)
    try {
        readFully(header)
    } catch (throwable: Throwable) {
        throw UploadNotAPdfException()
    }
    if (!header.contentEquals(PDF_MAGIC_BYTES)) {
        throw UploadNotAPdfException()
    }

    destination.outputStream().use { out ->
        out.write(header)
        var total = header.size.toLong()
        val buffer = ByteArray(8192)
        while (true) {
            val read = readAvailable(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) throw UploadTooLargeException()
            out.write(buffer, 0, read)
        }
    }
}
