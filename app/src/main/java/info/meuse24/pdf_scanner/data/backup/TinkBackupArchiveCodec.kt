package info.meuse24.pdf_scanner.data.backup

import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import com.google.crypto.tink.streamingaead.PredefinedStreamingAeadParameters
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import info.meuse24.pdf_scanner.domain.backup.BACKUP_ASSOCIATED_DATA_VERSION
import info.meuse24.pdf_scanner.domain.backup.BACKUP_APP_ID
import info.meuse24.pdf_scanner.domain.backup.BACKUP_FORMAT_VERSION
import info.meuse24.pdf_scanner.domain.backup.BackupArchiveException
import info.meuse24.pdf_scanner.domain.backup.BackupArchiveHeader
import info.meuse24.pdf_scanner.domain.backup.BackupFailure
import info.meuse24.pdf_scanner.domain.backup.BackupKdfParameters
import info.meuse24.pdf_scanner.domain.backup.BackupKeyWrapInfo
import info.meuse24.pdf_scanner.domain.backup.BackupProgress
import info.meuse24.pdf_scanner.domain.gateway.BackupArchiveCodec
import info.meuse24.pdf_scanner.domain.gateway.BackupKeyDeriver
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class TinkBackupArchiveCodec @Inject constructor(
    private val keyDeriver: BackupKeyDeriver
) : BackupArchiveCodec {
    private val secureRandom = SecureRandom()
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    init {
        StreamingAeadConfig.register()
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun writeBackup(
        outputStream: OutputStream,
        passphrase: CharArray,
        progressCallback: (BackupProgress) -> Unit,
        payloadWriter: (OutputStream) -> Unit
    ): BackupArchiveHeader {
        val salt = secureRandomBytes(SALT_BYTES)
        val nonce = secureRandomBytes(GCM_NONCE_BYTES)
        val kdfParameters = BackupKdfParameters(saltBase64 = Base64.encode(salt))
        val kek = keyDeriver.deriveKey(passphrase, kdfParameters)
        return try {
            val keysetHandle = KeysetHandle.generateNew(
                PredefinedStreamingAeadParameters.AES256_GCM_HKDF_1MB
            )
            val serializedKeyset = serializeKeyset(keysetHandle)
            val encryptedKeyset = aesGcmEncrypt(
                key = kek,
                nonce = nonce,
                plaintext = serializedKeyset
            )
            Arrays.fill(serializedKeyset, 0)

            val header = BackupArchiveHeader(
                createdAtEpochMillis = System.currentTimeMillis(),
                kdf = kdfParameters,
                keyWrap = BackupKeyWrapInfo(
                    nonceBase64 = Base64.encode(nonce),
                    encryptedStreamingKeysetBase64 = Base64.encode(encryptedKeyset)
                )
            )
            val headerBytes = json.encodeToString(header).encodeToByteArray()
            writeContainerPrefix(outputStream, headerBytes)
            val streamingAead = keysetHandle.getPrimitive(
                RegistryConfiguration.get(),
                StreamingAead::class.java
            )
            streamingAead.newEncryptingStream(outputStream, associatedData(headerBytes)).use { encryptingStream ->
                payloadWriter(encryptingStream)
            }
            progressCallback(BackupProgress.Finished)
            header
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e.toBackupArchiveException(BackupFailure.CorruptArchive, "Could not write encrypted backup")
        } finally {
            Arrays.fill(kek, 0)
            Arrays.fill(salt, 0)
            Arrays.fill(nonce, 0)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun readBackup(
        inputStream: InputStream,
        passphrase: CharArray,
        progressCallback: (BackupProgress) -> Unit,
        payloadReader: (InputStream, BackupArchiveHeader) -> Unit
    ): BackupArchiveHeader {
        val headerBytes = readContainerHeader(inputStream)
        val header = decodeAndValidateHeader(headerBytes)
        val kek = keyDeriver.deriveKey(passphrase, header.kdf)
        return try {
            val serializedKeyset = aesGcmDecrypt(
                key = kek,
                nonce = Base64.decode(header.keyWrap.nonceBase64),
                ciphertext = Base64.decode(header.keyWrap.encryptedStreamingKeysetBase64)
            )
            val keysetHandle = TinkJsonProtoKeysetFormat.parseKeyset(
                serializedKeyset.decodeToString(),
                InsecureSecretKeyAccess.get()
            )
            Arrays.fill(serializedKeyset, 0)

            val streamingAead = keysetHandle.getPrimitive(
                RegistryConfiguration.get(),
                StreamingAead::class.java
            )
            streamingAead.newDecryptingStream(inputStream, associatedData(headerBytes)).use { decryptingStream ->
                payloadReader(decryptingStream, header)
            }
            progressCallback(BackupProgress.Finished)
            header
        } catch (e: BackupArchiveException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: AEADBadTagException) {
            throw BackupArchiveException(BackupFailure.WrongPassphrase, "Wrong backup passphrase", e)
        } catch (e: GeneralSecurityException) {
            throw BackupArchiveException(BackupFailure.CorruptArchive, "Could not decrypt backup keyset", e)
        } catch (e: Exception) {
            throw e.toBackupArchiveException(BackupFailure.CorruptArchive, "Could not read encrypted backup")
        } finally {
            Arrays.fill(kek, 0)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeAndValidateHeader(headerBytes: ByteArray): BackupArchiveHeader {
        val header = try {
            json.decodeFromString<BackupArchiveHeader>(headerBytes.decodeToString())
        } catch (e: SerializationException) {
            throw BackupArchiveException(BackupFailure.CorruptArchive, "Backup header is not valid JSON", e)
        }
        when {
            header.appId != BACKUP_APP_ID -> throw BackupArchiveException(
                BackupFailure.UnsupportedFormat,
                "Backup belongs to another app: ${header.appId}"
            )
            header.formatVersion > BACKUP_FORMAT_VERSION -> throw BackupArchiveException(
                BackupFailure.UnsupportedFutureVersion,
                "Backup format ${header.formatVersion} needs a newer app"
            )
            header.formatVersion < BACKUP_FORMAT_VERSION -> throw BackupArchiveException(
                BackupFailure.UnsupportedFormat,
                "Unsupported backup format ${header.formatVersion}"
            )
            header.kdf.algorithm != "argon2id" -> throw BackupArchiveException(
                BackupFailure.UnsupportedFormat,
                "Unsupported KDF ${header.kdf.algorithm}"
            )
            header.keyWrap.algorithm != "AES-256-GCM" -> throw BackupArchiveException(
                BackupFailure.UnsupportedFormat,
                "Unsupported key wrap ${header.keyWrap.algorithm}"
            )
            header.payload.algorithm != "TINK_STREAMING_AEAD" -> throw BackupArchiveException(
                BackupFailure.UnsupportedFormat,
                "Unsupported payload algorithm ${header.payload.algorithm}"
            )
            header.payload.keyTemplate != "AES256_GCM_HKDF_1MB" -> throw BackupArchiveException(
                BackupFailure.UnsupportedFormat,
                "Unsupported payload key template ${header.payload.keyTemplate}"
            )
            header.payload.associatedDataVersion != BACKUP_ASSOCIATED_DATA_VERSION -> throw BackupArchiveException(
                BackupFailure.UnsupportedFormat,
                "Unsupported associated data version ${header.payload.associatedDataVersion}"
            )
        }
        validateKdfParameters(header.kdf)
        validateKeyWrap(header.keyWrap)
        return header
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun validateKdfParameters(parameters: BackupKdfParameters) {
        val salt = decodeBase64(parameters.saltBase64, "KDF salt")
        when {
            salt.size != SALT_BYTES -> throw BackupArchiveException(
                BackupFailure.CorruptArchive,
                "Invalid KDF salt length"
            )
            parameters.memoryKiB !in MIN_MEMORY_KIB..MAX_MEMORY_KIB -> throw BackupArchiveException(
                BackupFailure.CorruptArchive,
                "Invalid KDF memory cost"
            )
            parameters.iterations !in MIN_ITERATIONS..MAX_ITERATIONS -> throw BackupArchiveException(
                BackupFailure.CorruptArchive,
                "Invalid KDF iteration count"
            )
            parameters.parallelism !in MIN_PARALLELISM..MAX_PARALLELISM -> throw BackupArchiveException(
                BackupFailure.CorruptArchive,
                "Invalid KDF parallelism"
            )
            parameters.outputBytes != KEK_BYTES -> throw BackupArchiveException(
                BackupFailure.CorruptArchive,
                "Invalid KDF output size"
            )
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun validateKeyWrap(keyWrap: BackupKeyWrapInfo) {
        val nonce = decodeBase64(keyWrap.nonceBase64, "key wrap nonce")
        val encryptedKeyset = decodeBase64(keyWrap.encryptedStreamingKeysetBase64, "wrapped keyset")
        when {
            nonce.size != GCM_NONCE_BYTES -> throw BackupArchiveException(
                BackupFailure.CorruptArchive,
                "Invalid key wrap nonce length"
            )
            encryptedKeyset.isEmpty() -> throw BackupArchiveException(
                BackupFailure.CorruptArchive,
                "Wrapped keyset is empty"
            )
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBase64(value: String, fieldName: String): ByteArray =
        try {
            Base64.decode(value)
        } catch (e: IllegalArgumentException) {
            throw BackupArchiveException(BackupFailure.CorruptArchive, "Invalid Base64 in $fieldName", e)
        }

    private fun writeContainerPrefix(outputStream: OutputStream, headerBytes: ByteArray) {
        if (headerBytes.size > MAX_HEADER_BYTES) {
            throw BackupArchiveException(BackupFailure.CorruptArchive, "Backup header is too large")
        }
        DataOutputStream(outputStream).apply {
            write(MAGIC_BYTES)
            writeInt(headerBytes.size)
            write(headerBytes)
            flush()
        }
    }

    private fun readContainerHeader(inputStream: InputStream): ByteArray {
        val dataInput = DataInputStream(inputStream)
        val magic = ByteArray(MAGIC_BYTES.size)
        dataInput.readFully(magic)
        if (!magic.contentEquals(MAGIC_BYTES)) {
            throw BackupArchiveException(BackupFailure.UnsupportedFormat, "Backup magic bytes do not match")
        }
        val headerLength = dataInput.readInt()
        if (headerLength <= 0 || headerLength > MAX_HEADER_BYTES) {
            throw BackupArchiveException(BackupFailure.CorruptArchive, "Invalid backup header length")
        }
        return ByteArray(headerLength).also { dataInput.readFully(it) }
    }

    private fun associatedData(headerBytes: ByteArray): ByteArray {
        val headerHash = MessageDigest.getInstance("SHA-256").digest(headerBytes)
        return MAGIC_BYTES + headerHash
    }

    private fun serializeKeyset(keysetHandle: KeysetHandle): ByteArray =
        TinkJsonProtoKeysetFormat.serializeKeyset(
            keysetHandle,
            InsecureSecretKeyAccess.get()
        ).encodeToByteArray()

    private fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray =
        aesGcmCipher(Cipher.ENCRYPT_MODE, key, nonce).doFinal(plaintext)

    private fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray): ByteArray =
        aesGcmCipher(Cipher.DECRYPT_MODE, key, nonce).doFinal(ciphertext)

    private fun aesGcmCipher(mode: Int, key: ByteArray, nonce: ByteArray): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        return cipher
    }

    private fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

    private fun Exception.toBackupArchiveException(failure: BackupFailure, message: String): BackupArchiveException =
        if (this is BackupArchiveException) this else BackupArchiveException(failure, message, this)

    companion object {
        private const val MAGIC = "M24PDFBACKUP"
        private val MAGIC_BYTES = MAGIC.encodeToByteArray()
        private const val MAX_HEADER_BYTES = 64 * 1024
        private const val SALT_BYTES = 16
        private const val GCM_NONCE_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val KEK_BYTES = 32
        private const val MIN_MEMORY_KIB = 16 * 1024
        private const val MAX_MEMORY_KIB = 1024 * 1024
        private const val MIN_ITERATIONS = 1
        private const val MAX_ITERATIONS = 10
        private const val MIN_PARALLELISM = 1
        private const val MAX_PARALLELISM = 4
    }
}
