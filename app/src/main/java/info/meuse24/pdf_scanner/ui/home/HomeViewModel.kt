package info.meuse24.pdf_scanner.ui.home

import android.content.ContentValues
import android.content.Context
import android.util.Log
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
import info.meuse24.pdf_scanner.util.SearchablePdfBuilder
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
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ScanRepository,
    private val fileUtil: FileUtil,
    private val searchablePdfBuilder: SearchablePdfBuilder,
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

    /** (aktuelleSeite, gesamtSeiten) während makeSearchable; null sonst */
    private val _ocrProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val ocrProgress: StateFlow<Pair<Int, Int>?> = _ocrProgress.asStateFlow()

    fun saveScan(
        pdfUri: Uri,
        pageCount: Int,
        filename: String,
        thumbnailUri: Uri? = null,
        makeSearchable: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val savedFile = fileUtil.savePdfFromUri(pdfUri, filename)
                val baseName  = savedFile.nameWithoutExtension
                val thumbnailPath = thumbnailUri?.let {
                    fileUtil.saveThumbnailFromUri(it, baseName)?.absolutePath
                }

                var isSearchable = false
                if (makeSearchable) {
                    try {
                        val lang = Locale.getDefault().language
                        _ocrLoading.value = true
                        searchablePdfBuilder.makeSearchable(savedFile, lang) { cur, tot ->
                            _ocrProgress.value = cur to tot
                        }
                        isSearchable = true
                    } catch (e: Throwable) {
                        Log.e("SearchablePDF", "makeSearchable (saveScan) failed", e)
                        _error.value = context.getString(R.string.searchable_failed)
                    } finally {
                        _ocrLoading.value  = false
                        _ocrProgress.value = null
                    }
                }

                repository.saveScan(
                    ScanRecord(
                        filename      = baseName,
                        filepath      = savedFile.absolutePath,
                        timestamp     = System.currentTimeMillis(),
                        pageCount     = pageCount,
                        fileSize      = savedFile.length(),
                        thumbnailPath = thumbnailPath,
                        isSearchable  = isSearchable
                    )
                )
            } catch (e: Exception) {
                _error.value = e.message ?: context.getString(R.string.error_save_failed)
            }
        }
    }

    fun deleteScan(record: ScanRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            val file    = File(record.filepath)
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
                val file    = File(record.filepath)
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
                val displayName    = "${record.filename}.pdf"
                val contentValues  = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver   = context.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val itemUri    = resolver.insert(collection, contentValues)
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

    /** Macht bestehende Scans durchsuchbar (Bulk-Aktion).
     *  Bereits durchsuchbare Records werden übersprungen (Idempotenz). */
    fun makeSearchableScans(records: List<ScanRecord>) {
        if (_ocrLoading.value) return
        val pending = records.filter { !it.isSearchable }
        if (pending.isEmpty()) return
        _ocrLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lang = Locale.getDefault().language
                pending.forEach { record ->
                    val pdfFile = File(record.filepath)
                    if (!pdfFile.exists()) return@forEach
                    searchablePdfBuilder.makeSearchable(pdfFile, lang) { cur, tot ->
                        _ocrProgress.value = cur to tot
                    }
                    // fileSize aktualisieren: PDF wächst durch Textlayer + eingebettete Fonts
                    repository.markSearchable(record.id, pdfFile.length())
                }
                val firstName = pending.firstOrNull()?.filename ?: ""
                _success.value = if (pending.size == 1) {
                    context.getString(R.string.searchable_success, firstName)
                } else {
                    context.getString(R.string.searchable_success_multi, pending.size)
                }
            } catch (e: Throwable) {
                Log.e("SearchablePDF", "makeSearchableScans failed", e)
                _error.value = context.getString(R.string.searchable_failed)
            } finally {
                _ocrLoading.value  = false
                _ocrProgress.value = null
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

    fun extractTexts(records: List<ScanRecord>) {
        if (_ocrLoading.value) return
        val withImages = records.filter { it.thumbnailPath != null }
        if (withImages.isEmpty()) {
            _error.value = context.getString(R.string.ocr_no_image)
            return
        }
        _ocrLoading.value = true
        viewModelScope.launch {
            try {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val results    = StringBuilder()
                for (record in withImages) {
                    val image = withContext(Dispatchers.IO) {
                        InputImage.fromFilePath(context, Uri.fromFile(File(record.thumbnailPath!!)))
                    }
                    val text = suspendCancellableCoroutine<String> { cont ->
                        recognizer.process(image)
                            .addOnSuccessListener { result -> cont.resume(result.text) }
                            .addOnFailureListener { e -> cont.resumeWithException(e) }
                        cont.invokeOnCancellation { recognizer.close() }
                    }
                    if (text.isNotBlank()) {
                        if (results.isNotEmpty()) results.append("\n\n")
                        if (withImages.size > 1) {
                            results.append(context.getString(R.string.ocr_bulk_separator, record.filename))
                            results.append("\n")
                        }
                        results.append(text)
                    }
                }
                recognizer.close()
                if (results.isBlank()) {
                    _error.value = context.getString(R.string.ocr_no_text_found)
                } else {
                    _ocrText.value = results.toString()
                }
            } catch (e: Exception) {
                _error.value = context.getString(R.string.ocr_failed)
            } finally {
                _ocrLoading.value = false
            }
        }
    }

    fun clearOcrText() { _ocrText.value = null }
    fun reportError(message: String) { _error.value = message }
    fun clearError() { _error.value = null }
    fun clearSuccess() { _success.value = null }
}
