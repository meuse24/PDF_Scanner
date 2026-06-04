package info.meuse24.pdf_scanner.data.backup

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import info.meuse24.pdf_scanner.domain.backup.BackupKdfParameters
import info.meuse24.pdf_scanner.domain.gateway.BackupKeyDeriver
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Arrays
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Singleton
class Argon2BackupKeyDeriver @Inject constructor() : BackupKeyDeriver {
    private val argon2Kt by lazy { Argon2Kt() }

    @OptIn(ExperimentalEncodingApi::class)
    override fun deriveKey(passphrase: CharArray, parameters: BackupKdfParameters): ByteArray {
        require(parameters.algorithm == "argon2id") { "Unsupported KDF: ${parameters.algorithm}" }
        val passwordBytes = encodeUtf8(passphrase)
        return try {
            val result = argon2Kt.hash(
                mode = Argon2Mode.ARGON2_ID,
                password = passwordBytes,
                salt = Base64.decode(parameters.saltBase64),
                tCostInIterations = parameters.iterations,
                mCostInKibibyte = parameters.memoryKiB,
                parallelism = parameters.parallelism,
                hashLengthInBytes = parameters.outputBytes
            )
            result.rawHashAsByteArray()
        } finally {
            Arrays.fill(passwordBytes, 0)
        }
    }

    private fun encodeUtf8(chars: CharArray): ByteArray {
        val encoder = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val charBuffer = CharBuffer.wrap(chars)
        val byteBuffer = ByteBuffer.allocate((chars.size * encoder.maxBytesPerChar()).toInt())
        encoder.encode(charBuffer, byteBuffer, true)
        encoder.flush(byteBuffer)
        byteBuffer.flip()
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        return bytes
    }
}
