package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.gateway.DispatcherProvider
import info.meuse24.pdf_scanner.domain.model.Document
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class CalculateSha256UseCase @Inject constructor(
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(document: Document): String =
        withContext(dispatcherProvider.io) {
            val file = File(document.filepath)
            if (!file.exists() || !file.isFile) {
                throw FileNotFoundException(document.filepath)
            }
            if (!file.canRead()) {
                throw FileHashUnreadableException()
            }

            val digest = MessageDigest.getInstance(SHA_256)
            val buffer = ByteArray(BUFFER_SIZE_BYTES)
            try {
                file.inputStream().use { input ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break
                        digest.update(buffer, 0, bytesRead)
                    }
                }
            } catch (exception: FileNotFoundException) {
                if (file.exists()) {
                    throw FileHashUnreadableException(exception)
                }
                throw exception
            } catch (exception: IOException) {
                throw FileHashUnreadableException(exception)
            } catch (exception: SecurityException) {
                throw FileHashUnreadableException(exception)
            }
            digest.digest().toLowerHex()
        }

    private fun ByteArray.toLowerHex(): String = buildString(size * 2) {
        for (byte in this@toLowerHex) {
            val value = byte.toInt() and 0xff
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0f])
        }
    }

    private companion object {
        const val SHA_256 = "SHA-256"
        const val BUFFER_SIZE_BYTES = 64 * 1024
        const val HEX_DIGITS = "0123456789abcdef"
    }
}

class FileHashUnreadableException(
    cause: Throwable? = null
) : IOException(cause)
