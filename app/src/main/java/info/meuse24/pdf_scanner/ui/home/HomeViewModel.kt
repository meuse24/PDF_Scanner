package info.meuse24.pdf_scanner.ui.home

import android.content.ContentValues
import android.content.Context
import android.util.Log
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.util.FileUtil
import info.meuse24.pdf_scanner.util.OcrManager
import info.meuse24.pdf_scanner.util.PdfEditor
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
    private val ocrManager: OcrManager,
    private val pdfEditor: PdfEditor,
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

    /** true während merge/split/reorder Operationen */
    private val _editLoading = MutableStateFlow(false)
    val editLoading: StateFlow<Boolean> = _editLoading.asStateFlow()

    /** (aktuellerSchritt, gesamtSchritte) während Edit-Bulk-Ops; null sonst */
    private val _editProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val editProgress: StateFlow<Pair<Int, Int>?> = _editProgress.asStateFlow()

    fun saveScan(
        pdfUri: Uri,
        pageCount: Int,
        filename: String,
        thumbnailUri: Uri? = null,
        makeSearchable: Boolean = false,
        languageCode: String = Locale.getDefault().language
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
                        val lang = languageCode
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
    fun makeSearchableScans(records: List<ScanRecord>, languageCode: String) {
        if (_ocrLoading.value) return
        val pending = records.filter { !it.isSearchable }
        if (pending.isEmpty()) return
        _ocrLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lang = languageCode
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

    fun extractText(record: ScanRecord, languageCode: String = Locale.getDefault().language) {
        if (_ocrLoading.value) return
        val pdfFile = File(record.filepath)
        if (!pdfFile.exists() && record.thumbnailPath == null) {
            _error.value = context.getString(R.string.ocr_no_image)
            return
        }
        _ocrLoading.value = true
        viewModelScope.launch {
            try {
                val recognizer = ocrManager.getRecognizer(languageCode)
                val result = StringBuilder()
                try {
                    if (pdfFile.exists()) {
                        withContext(Dispatchers.IO) {
                            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                                PdfRenderer(pfd).use { renderer ->
                                    repeat(renderer.pageCount) { i ->
                                        renderer.openPage(i).use { page ->
                                            val bmp = Bitmap.createBitmap(
                                                page.width.coerceAtLeast(1),
                                                page.height.coerceAtLeast(1),
                                                Bitmap.Config.ARGB_8888
                                            )
                                            bmp.eraseColor(Color.WHITE)
                                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                            val text = try {
                                                suspendCancellableCoroutine<String> { cont ->
                                                    recognizer.process(InputImage.fromBitmap(bmp, 0))
                                                        .addOnSuccessListener { cont.resume(it.text) }
                                                        .addOnFailureListener { e -> cont.resumeWithException(e) }
                                                    cont.invokeOnCancellation { recognizer.close() }
                                                }
                                            } finally {
                                                bmp.recycle()
                                            }
                                            if (text.isNotBlank()) {
                                                if (result.isNotEmpty()) result.append("\n\n")
                                                result.append(text)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Fallback: thumbnailPath (erste Seite)
                        val image = withContext(Dispatchers.IO) {
                            InputImage.fromFilePath(context, Uri.fromFile(File(record.thumbnailPath!!)))
                        }
                        val text = suspendCancellableCoroutine<String> { cont ->
                            recognizer.process(image)
                                .addOnSuccessListener { cont.resume(it.text) }
                                .addOnFailureListener { e -> cont.resumeWithException(e) }
                            cont.invokeOnCancellation { recognizer.close() }
                        }
                        if (text.isNotBlank()) result.append(text)
                    }
                } finally {
                    recognizer.close()
                }
                if (result.isBlank()) {
                    _error.value = context.getString(R.string.ocr_no_text_found)
                } else {
                    _ocrText.value = result.toString()
                }
            } catch (e: Exception) {
                _error.value = context.getString(R.string.ocr_failed)
            } finally {
                _ocrLoading.value = false
            }
        }
    }

    fun extractTexts(records: List<ScanRecord>, languageCode: String = Locale.getDefault().language) {
        if (_ocrLoading.value) return
        val validRecords = records.filter { File(it.filepath).exists() || it.thumbnailPath != null }
        if (validRecords.isEmpty()) {
            _error.value = context.getString(R.string.ocr_no_image)
            return
        }
        _ocrLoading.value = true
        viewModelScope.launch {
            try {
                val recognizer = ocrManager.getRecognizer(languageCode)
                val results    = StringBuilder()
                try {
                    for (record in validRecords) {
                        val pdfFile   = File(record.filepath)
                        val pageTexts = StringBuilder()
                        if (pdfFile.exists()) {
                            withContext(Dispatchers.IO) {
                                ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                                    PdfRenderer(pfd).use { renderer ->
                                        repeat(renderer.pageCount) { i ->
                                            renderer.openPage(i).use { page ->
                                                val bmp = Bitmap.createBitmap(
                                                    page.width.coerceAtLeast(1),
                                                    page.height.coerceAtLeast(1),
                                                    Bitmap.Config.ARGB_8888
                                                )
                                                bmp.eraseColor(Color.WHITE)
                                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                                val text = try {
                                                    suspendCancellableCoroutine<String> { cont ->
                                                        recognizer.process(InputImage.fromBitmap(bmp, 0))
                                                            .addOnSuccessListener { cont.resume(it.text) }
                                                            .addOnFailureListener { e -> cont.resumeWithException(e) }
                                                        cont.invokeOnCancellation { recognizer.close() }
                                                    }
                                                } finally {
                                                    bmp.recycle()
                                                }
                                                if (text.isNotBlank()) {
                                                    if (pageTexts.isNotEmpty()) pageTexts.append("\n\n")
                                                    pageTexts.append(text)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (record.thumbnailPath != null) {
                            val image = withContext(Dispatchers.IO) {
                                InputImage.fromFilePath(context, Uri.fromFile(File(record.thumbnailPath)))
                            }
                            val text = suspendCancellableCoroutine<String> { cont ->
                                recognizer.process(image)
                                    .addOnSuccessListener { cont.resume(it.text) }
                                    .addOnFailureListener { e -> cont.resumeWithException(e) }
                                cont.invokeOnCancellation { recognizer.close() }
                            }
                            if (text.isNotBlank()) pageTexts.append(text)
                        }
                        if (pageTexts.isNotBlank()) {
                            if (results.isNotEmpty()) results.append("\n\n")
                            if (validRecords.size > 1) {
                                results.append(context.getString(R.string.ocr_bulk_separator, record.filename))
                                results.append("\n")
                            }
                            results.append(pageTexts)
                        }
                    }
                } finally {
                    recognizer.close()
                }
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

    // ── PDF-Bearbeitungsfunktionen ────────────────────────────────────────────

    /**
     * Führt die ausgewählten PDFs zu einer neuen Datei zusammen.
     * Reihenfolge entspricht der Übergabeliste.
     */
    fun mergePdfs(records: List<ScanRecord>, outputFilename: String) {
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val scansDir = java.io.File(context.filesDir, "scans").apply { mkdirs() }
                val baseName = info.meuse24.pdf_scanner.util.resolveUniqueFilename(scansDir, outputFilename)
                val destFile = java.io.File(scansDir, "$baseName.pdf")
                pdfEditor.mergePdfs(records.map { java.io.File(it.filepath) }, destFile)
                val thumbFile = java.io.File(scansDir, "$baseName.jpg")
                pdfEditor.generateThumbnail(destFile, thumbFile)
                repository.saveScan(
                    ScanRecord(
                        filename      = baseName,
                        filepath      = destFile.absolutePath,
                        timestamp     = System.currentTimeMillis(),
                        pageCount     = records.sumOf { it.pageCount },
                        fileSize      = destFile.length(),
                        thumbnailPath = thumbFile.takeIf { it.exists() }?.absolutePath
                    )
                )
                _success.value = context.getString(R.string.merge_success, baseName)
            } catch (e: Exception) {
                Log.e("PdfEditor", "mergePdfs failed", e)
                _error.value = context.getString(R.string.merge_error)
            } finally {
                _editLoading.value = false
            }
        }
    }

    /**
     * Teilt ein PDF an den angegebenen Seitenindizes auf.
     * Neue ScanRecords werden für jede erzeugte Datei angelegt.
     */
    fun splitPdf(record: ScanRecord, splitAtPages: List<Int>) {
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val scansDir = java.io.File(context.filesDir, "scans").apply { mkdirs() }
                val parts = pdfEditor.splitPdf(java.io.File(record.filepath), scansDir, splitAtPages)
                val newRecords = parts.map { partFile ->
                    val baseName = partFile.nameWithoutExtension
                    val thumbFile = java.io.File(scansDir, "$baseName.jpg")
                    pdfEditor.generateThumbnail(partFile, thumbFile)
                    ScanRecord(
                        filename      = baseName,
                        filepath      = partFile.absolutePath,
                        timestamp     = System.currentTimeMillis(),
                        pageCount     = 0,  // PdfRenderer-Seitenanzahl erst beim Öffnen bekannt
                        fileSize      = partFile.length(),
                        thumbnailPath = thumbFile.takeIf { it.exists() }?.absolutePath
                    )
                }
                repository.saveScans(newRecords)
                _success.value = context.getString(R.string.split_success, parts.size)
            } catch (e: Exception) {
                Log.e("PdfEditor", "splitPdf failed", e)
                _error.value = context.getString(R.string.split_error)
            } finally {
                _editLoading.value = false
            }
        }
    }

    /**
     * Ordnet Seiten eines PDFs neu an.
     * [saveAsCopy] = false: Original wird atomar überschrieben.
     * [saveAsCopy] = true: neue Datei mit Suffix „_Sortiert" angelegt.
     * isSearchable bleibt erhalten (PdfBox konserviert Text-Layer).
     */
    fun reorderPages(record: ScanRecord, newOrder: List<Int>, saveAsCopy: Boolean) {
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resultFile = pdfEditor.reorderPages(
                    java.io.File(record.filepath), newOrder, saveAsCopy
                )
                val scansDir = java.io.File(context.filesDir, "scans")
                val thumbFile = java.io.File(scansDir, "${resultFile.nameWithoutExtension}.jpg")
                pdfEditor.generateThumbnail(resultFile, thumbFile)
                if (saveAsCopy) {
                    repository.saveScan(
                        ScanRecord(
                            filename      = resultFile.nameWithoutExtension,
                            filepath      = resultFile.absolutePath,
                            timestamp     = System.currentTimeMillis(),
                            pageCount     = record.pageCount,
                            fileSize      = resultFile.length(),
                            thumbnailPath = thumbFile.takeIf { it.exists() }?.absolutePath,
                            isSearchable  = record.isSearchable
                        )
                    )
                } else {
                    repository.updateFileSize(record.id, resultFile.length())
                }
                _success.value = context.getString(R.string.reorder_success)
            } catch (e: Exception) {
                Log.e("PdfEditor", "reorderPages failed", e)
                _error.value = context.getString(R.string.reorder_error)
            } finally {
                _editLoading.value = false
            }
        }
    }

    fun clearOcrText() { _ocrText.value = null }
    fun reportError(message: String) { _error.value = message }
    fun clearError() { _error.value = null }
    fun clearSuccess() { _success.value = null }
}
