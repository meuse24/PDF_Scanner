package info.meuse24.pdf_scanner.ui.home

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.util.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ScanRepository,
    private val fileUtil: FileUtil,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val scans: StateFlow<List<ScanRecord>> = repository.getAllScans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _success = MutableStateFlow<String?>(null)
    val success: StateFlow<String?> = _success.asStateFlow()

    private val _ocrText = MutableStateFlow<String?>(null)
    val ocrText: StateFlow<String?> = _ocrText.asStateFlow()

    private val _ocrLoading = MutableStateFlow(false)
    val ocrLoading: StateFlow<Boolean> = _ocrLoading.asStateFlow()

    fun saveScan(pdfUri: Uri, pageCount: Int, filename: String, thumbnailUri: Uri? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val savedFile = fileUtil.savePdfFromUri(pdfUri, filename)
                val baseName = savedFile.nameWithoutExtension
                val thumbnailPath = thumbnailUri?.let {
                    fileUtil.saveThumbnailFromUri(it, baseName)?.absolutePath
                }
                repository.saveScan(
                    ScanRecord(
                        filename      = baseName,
                        filepath      = savedFile.absolutePath,
                        timestamp     = System.currentTimeMillis(),
                        pageCount     = pageCount,
                        fileSize      = savedFile.length(),
                        thumbnailPath = thumbnailPath
                    )
                )
            } catch (e: Exception) {
                _error.value = e.message ?: context.getString(R.string.error_save_failed)
            }
        }
    }

    fun deleteScan(record: ScanRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(record.filepath)
            val deleted = !file.exists() || file.delete()
            if (deleted) {
                record.thumbnailPath?.let { path ->
                    val thumbFile = File(path)
                    if (thumbFile.exists()) thumbFile.delete()
                }
                repository.deleteScan(record)
            } else {
                _error.value = context.getString(R.string.error_delete_failed)
            }
        }
    }

    fun deleteScans(records: List<ScanRecord>) {
        viewModelScope.launch(Dispatchers.IO) {
            var anyError = false
            for (record in records) {
                val file = File(record.filepath)
                val deleted = !file.exists() || file.delete()
                if (deleted) {
                    record.thumbnailPath?.let { path ->
                        val thumbFile = File(path)
                        if (thumbFile.exists()) thumbFile.delete()
                    }
                    repository.deleteScan(record)
                } else {
                    anyError = true
                }
            }
            if (anyError) _error.value = context.getString(R.string.error_delete_failed)
        }
    }

    fun exportScan(record: ScanRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sourceFile = File(record.filepath)
                if (!sourceFile.exists()) {
                    _error.value = context.getString(R.string.error_export_failed)
                    return@launch
                }
                val displayName = "${record.filename}.pdf"
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val itemUri = resolver.insert(collection, contentValues)
                    ?: run {
                        _error.value = context.getString(R.string.error_export_failed)
                        return@launch
                    }
                try {
                    resolver.openOutputStream(itemUri)?.use { output ->
                        sourceFile.inputStream().use { input -> input.copyTo(output) }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(itemUri, contentValues, null, null)
                    _success.value = context.getString(R.string.export_success, displayName)
                } catch (e: Exception) {
                    resolver.delete(itemUri, null, null)
                    _error.value = context.getString(R.string.error_export_failed)
                }
            } catch (e: Exception) {
                _error.value = context.getString(R.string.error_export_failed)
            }
        }
    }

    fun extractText(record: ScanRecord) {
        if (_ocrLoading.value) return
        val imagePath = record.thumbnailPath
        if (imagePath == null) {
            _error.value = context.getString(R.string.ocr_no_image)
            return
        }
        _ocrLoading.value = true
        viewModelScope.launch {
            try {
                val image = withContext(Dispatchers.IO) {
                    InputImage.fromFilePath(context, Uri.fromFile(File(imagePath)))
                }
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val text = suspendCancellableCoroutine { cont ->
                    recognizer.process(image)
                        .addOnSuccessListener { result -> cont.resume(result.text) }
                        .addOnFailureListener { e -> cont.resumeWithException(e) }
                    cont.invokeOnCancellation { recognizer.close() }
                }
                recognizer.close()
                if (text.isBlank()) {
                    _error.value = context.getString(R.string.ocr_no_text_found)
                } else {
                    _ocrText.value = text
                }
            } catch (e: Exception) {
                _error.value = context.getString(R.string.ocr_failed)
            } finally {
                _ocrLoading.value = false
            }
        }
    }

    fun clearOcrText() {
        _ocrText.value = null
    }

    fun reportError(message: String) {
        _error.value = message
    }

    fun clearError() {
        _error.value = null
    }

    fun clearSuccess() {
        _success.value = null
    }
}
