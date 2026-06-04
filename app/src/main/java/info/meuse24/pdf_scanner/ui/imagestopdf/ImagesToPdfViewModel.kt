package info.meuse24.pdf_scanner.ui.imagestopdf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.meuse24.pdf_scanner.domain.model.PdfPageSetup
import info.meuse24.pdf_scanner.domain.repository.AppSettingsRepository
import info.meuse24.pdf_scanner.domain.usecase.CreatePdfFromImagesUseCase
import info.meuse24.pdf_scanner.domain.usecase.ImagePdfOptions
import info.meuse24.pdf_scanner.domain.gateway.DispatcherProvider
import info.meuse24.pdf_scanner.domain.gateway.StorageProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImagesToPdfViewModel @Inject constructor(
    private val createPdfFromImagesUseCase: CreatePdfFromImagesUseCase,
    private val storageProvider: StorageProvider,
    private val dispatcherProvider: DispatcherProvider,
    private val settingsRepository: AppSettingsRepository
) : ViewModel() {

    private val _editLoading = MutableStateFlow(false)
    val editLoading: StateFlow<Boolean> = _editLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _success = MutableStateFlow(false)
    val success: StateFlow<Boolean> = _success.asStateFlow()

    /** Anzahl der Bilder, die nicht gelesen werden konnten (0 = kein Hinweis nötig). */
    private val _skippedCount = MutableStateFlow(0)
    val skippedCount: StateFlow<Int> = _skippedCount.asStateFlow()

    private val _pageSetup = MutableStateFlow(settingsRepository.settings.value.defaultImagePdfPageSetup)
    val pageSetup: StateFlow<PdfPageSetup> = _pageSetup.asStateFlow()

    fun updatePageSetup(setup: PdfPageSetup) {
        _pageSetup.value = setup
        settingsRepository.updateDefaultImagePdfPageSetup(setup)
    }

    fun createPdf(imageUris: List<Uri>, filename: String, options: ImagePdfOptions) {
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val result = createPdfFromImagesUseCase(
                    imageUris, filename, options, storageProvider.scansDir()
                )
                _skippedCount.value = result.skippedCount
                _success.value = true
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _editLoading.value = false
            }
        }
    }

    fun clearError() { _error.value = null }
}
