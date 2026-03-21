package info.meuse24.pdf_scanner.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import java.io.File
import javax.inject.Inject

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

    fun saveScan(pdfUri: Uri, pageCount: Int, filename: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val savedFile = fileUtil.savePdfFromUri(pdfUri, filename)
                repository.saveScan(
                    ScanRecord(
                        filename  = savedFile.nameWithoutExtension,
                        filepath  = savedFile.absolutePath,
                        timestamp = System.currentTimeMillis(),
                        pageCount = pageCount,
                        fileSize  = savedFile.length()
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
                repository.deleteScan(record)
            } else {
                _error.value = context.getString(R.string.error_delete_failed)
            }
        }
    }

    fun reportError(message: String) {
        _error.value = message
    }

    fun clearError() {
        _error.value = null
    }
}
