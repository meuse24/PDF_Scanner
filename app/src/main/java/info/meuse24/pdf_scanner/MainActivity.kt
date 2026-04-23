package info.meuse24.pdf_scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import info.meuse24.pdf_scanner.ui.navigation.AppNavigation
import info.meuse24.pdf_scanner.ui.settings.SettingsViewModel
import info.meuse24.pdf_scanner.ui.theme.PDF_ScannerTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settings = settingsViewModel.settings.collectAsStateWithLifecycle().value

            PDF_ScannerTheme(themeMode = settings.themeMode) {
                AppNavigation(
                    onThemeModeChange = settingsViewModel::setThemeMode
                )
            }
        }
    }
}
