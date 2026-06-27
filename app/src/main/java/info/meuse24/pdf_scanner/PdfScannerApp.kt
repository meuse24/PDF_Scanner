package info.meuse24.pdf_scanner

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.HiltAndroidApp
import info.meuse24.pdf_scanner.domain.usecase.CleanStagingDirsUseCase
import info.meuse24.pdf_scanner.domain.usecase.PurgeTrashUseCase
import info.meuse24.pdf_scanner.util.AppLockManager
import info.meuse24.pdf_scanner.domain.gateway.DispatcherProvider
import info.meuse24.pdf_scanner.util.PlayReviewPromptManager
import info.meuse24.pdf_scanner.util.TrashConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PdfScannerApp : Application() {
    @Inject lateinit var purgeTrashUseCase: PurgeTrashUseCase
    @Inject lateinit var cleanStagingDirsUseCase: CleanStagingDirsUseCase
    @Inject lateinit var dispatcherProvider: DispatcherProvider
    @Inject lateinit var appLockManager: AppLockManager
    @Inject lateinit var playReviewPromptManager: PlayReviewPromptManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLockManager)
        playReviewPromptManager.recordAppLaunch()
        appScope.launch(dispatcherProvider.io) {
            cleanStagingDirsUseCase()
            purgeTrashUseCase.purgeExpired(TrashConstants.RETENTION_MILLIS)
        }
    }
}
