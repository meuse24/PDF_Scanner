package info.meuse24.pdf_scanner

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import dagger.hilt.android.AndroidEntryPoint
import info.meuse24.pdf_scanner.ui.entry.AppEntryActionCodec
import info.meuse24.pdf_scanner.ui.entry.AppEntryActionViewModel
import info.meuse24.pdf_scanner.ui.home.ArchiveFilterStore
import info.meuse24.pdf_scanner.ui.lock.AppLockScreen
import info.meuse24.pdf_scanner.ui.navigation.AppNavigation
import info.meuse24.pdf_scanner.ui.settings.SettingsViewModel
import info.meuse24.pdf_scanner.ui.theme.PDF_ScannerTheme
import info.meuse24.pdf_scanner.util.AppLockAuthResult
import info.meuse24.pdf_scanner.util.AppLockManager
import info.meuse24.pdf_scanner.domain.repository.FolderRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val appEntryActionViewModel: AppEntryActionViewModel by viewModels()
    @Inject lateinit var appLockManager: AppLockManager
    @Inject lateinit var folderRepository: FolderRepository
    @Inject lateinit var archiveFilterStore: ArchiveFilterStore

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            handleIntent(intent)
        }
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settings = settingsViewModel.settings.collectAsStateWithLifecycle().value
            val isLocked = appLockManager.isLocked.collectAsStateWithLifecycle().value
            val folders = folderRepository.observeFolders().collectAsStateWithLifecycle(emptyList()).value
            val archiveFilter = archiveFilterStore.filter.collectAsStateWithLifecycle().value
            val pendingEntryAction = appEntryActionViewModel.pendingActions
                .collectAsStateWithLifecycle()
                .value
                .firstOrNull()
            val scope = rememberCoroutineScope()
            var isAuthenticating by remember { mutableStateOf(false) }
            val windowSizeClass = calculateWindowSizeClass(this)

            PDF_ScannerTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColorEnabled
            ) {
                Box {
                    AppNavigation(
                        m24AnimationEnabled = settings.m24AnimationEnabled,
                        widthSizeClass = windowSizeClass.widthSizeClass,
                        heightSizeClass = windowSizeClass.heightSizeClass,
                        onThemeModeChange = settingsViewModel::setThemeMode,
                        pendingAppEntryAction = if (settings.appLockEnabled && isLocked) null else pendingEntryAction,
                        onConsumeAppEntryAction = appEntryActionViewModel::consume,
                        appLockManager = appLockManager,
                        folders = folders,
                        archiveFilter = archiveFilter,
                        onShowAllDocuments = archiveFilterStore::showAllDocuments,
                        onShowFavorites = archiveFilterStore::showFavorites,
                        onShowFolder = archiveFilterStore::showFolder
                    )
                    if (settings.appLockEnabled && isLocked) {
                        AppLockScreen(
                            isAuthenticating = isAuthenticating,
                            onUnlockClick = {
                                scope.launch {
                                    isAuthenticating = true
                                    when (appLockManager.authenticate(this@MainActivity)) {
                                        AppLockAuthResult.Success -> Unit
                                        AppLockAuthResult.Cancelled -> settingsViewModel.reportError(
                                            getString(R.string.app_lock_auth_failed)
                                        )
                                        AppLockAuthResult.Unavailable -> settingsViewModel.reportError(
                                            getString(R.string.app_lock_unavailable)
                                        )
                                    }
                                    isAuthenticating = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        AppEntryActionCodec.fromIntent(this, intent)?.let(appEntryActionViewModel::offer)
    }
}
