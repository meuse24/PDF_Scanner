package info.meuse24.pdf_scanner

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import info.meuse24.pdf_scanner.domain.usecase.PurgeTrashUseCase
import info.meuse24.pdf_scanner.util.DispatcherProvider
import info.meuse24.pdf_scanner.util.TrashConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PdfScannerApp : Application() {
    @Inject lateinit var purgeTrashUseCase: PurgeTrashUseCase
    @Inject lateinit var dispatcherProvider: DispatcherProvider

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        appScope.launch(dispatcherProvider.io) {
            purgeTrashUseCase.purgeExpired(TrashConstants.RETENTION_MILLIS)
        }
    }
}
