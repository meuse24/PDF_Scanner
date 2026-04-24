package info.meuse24.pdf_scanner.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.ui.annotate.AnnotateScreen
import info.meuse24.pdf_scanner.ui.append.AppendScreen
import info.meuse24.pdf_scanner.ui.businesscard.BusinessCardScreen
import info.meuse24.pdf_scanner.ui.entry.AppEntryAction
import info.meuse24.pdf_scanner.ui.documentaction.CompressPdfScreen
import info.meuse24.pdf_scanner.ui.documentaction.ConvertToGrayscaleScreen
import info.meuse24.pdf_scanner.ui.documentaction.PdfMetadataScreen
import info.meuse24.pdf_scanner.ui.documentaction.ProtectPdfScreen
import info.meuse24.pdf_scanner.ui.documentaction.RemovePasswordScreen
import info.meuse24.pdf_scanner.ui.documentaction.RemoveTextLayerScreen
import info.meuse24.pdf_scanner.ui.documentaction.RestrictUsageScreen
import info.meuse24.pdf_scanner.ui.documentaction.UnlockPdfScreen
import info.meuse24.pdf_scanner.ui.folders.FolderManagementScreen
import info.meuse24.pdf_scanner.ui.help.HelpScreen
import info.meuse24.pdf_scanner.ui.home.HomeScreen
import info.meuse24.pdf_scanner.ui.home.HomeViewModel
import info.meuse24.pdf_scanner.ui.home.HomeNavigationCallbacks
import info.meuse24.pdf_scanner.ui.imagestopdf.ImagesToPdfScreen
import info.meuse24.pdf_scanner.ui.info.InfoScreen
import info.meuse24.pdf_scanner.ui.ocr.OcrReviewScreen
import info.meuse24.pdf_scanner.ui.overlay.PageNumbersScreen
import info.meuse24.pdf_scanner.ui.overlay.TextWatermarkScreen
import info.meuse24.pdf_scanner.ui.pageedit.DeletePagesScreen
import info.meuse24.pdf_scanner.ui.pageedit.DuplicatePagesScreen
import info.meuse24.pdf_scanner.ui.pageedit.ExtractPagesScreen
import info.meuse24.pdf_scanner.ui.pageedit.RotatePagesScreen
import info.meuse24.pdf_scanner.ui.privacy.PrivacyScreen
import info.meuse24.pdf_scanner.ui.qrscan.QrScanScreen
import info.meuse24.pdf_scanner.ui.redact.RedactScreen
import info.meuse24.pdf_scanner.ui.reorder.ReorderScreen
import info.meuse24.pdf_scanner.ui.settings.SettingsScreen
import info.meuse24.pdf_scanner.ui.settings.SettingsViewModel
import info.meuse24.pdf_scanner.ui.signature.SignatureScreen
import info.meuse24.pdf_scanner.ui.split.SplitScreen
import info.meuse24.pdf_scanner.ui.theme.ThemeMode
import info.meuse24.pdf_scanner.ui.trash.TrashScreen
import info.meuse24.pdf_scanner.ui.viewer.PdfViewerScreen
import info.meuse24.pdf_scanner.util.AppLockAuthResult
import info.meuse24.pdf_scanner.util.AppLockAvailability
import info.meuse24.pdf_scanner.util.AppLockManager
import kotlinx.coroutines.launch

@Composable
internal fun AppNavigationHost(
    navController: NavHostController,
    innerPadding: PaddingValues,
    addActionTrigger: Boolean,
    onAddActionTriggered: () -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    pendingAppEntryAction: AppEntryAction?,
    onConsumeAppEntryAction: (AppEntryAction) -> Unit,
    appLockManager: AppLockManager
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Ablage.route,
        modifier = Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
    ) {
        homeNavGraph(
            navController = navController,
            addActionTrigger = addActionTrigger,
            onAddActionTriggered = onAddActionTriggered,
            onSelectionModeChange = onSelectionModeChange,
            pendingAppEntryAction = pendingAppEntryAction,
            onConsumeAppEntryAction = onConsumeAppEntryAction
        )
        infoNavGraph(
            navController = navController,
            onThemeModeChange = onThemeModeChange,
            appLockManager = appLockManager
        )
        viewerNavGraph(navController = navController)
        editNavGraph(navController = navController)
        appendNavGraph(navController = navController)
        imagesNavGraph(navController = navController)
    }
}

private fun NavGraphBuilder.homeNavGraph(
    navController: NavHostController,
    addActionTrigger: Boolean,
    onAddActionTriggered: () -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    pendingAppEntryAction: AppEntryAction?,
    onConsumeAppEntryAction: (AppEntryAction) -> Unit
) {
    composable(Screen.Ablage.route) {
        HomeScreen(
            addActionTrigger = addActionTrigger,
            onAddActionTriggered = onAddActionTriggered,
            onSelectionModeChange = onSelectionModeChange,
            pendingAppEntryAction = pendingAppEntryAction,
            onConsumeAppEntryAction = onConsumeAppEntryAction,
            navigation = HomeNavigationCallbacks(
                onSplit = { scanId -> navController.navigate(Screen.Split.createRoute(scanId)) },
                onReorder = { scanId -> navController.navigate(Screen.Reorder.createRoute(scanId)) },
                onRotate = { scanId -> navController.navigate(Screen.RotatePages.createRoute(scanId)) },
                onDeletePages = { scanId -> navController.navigate(Screen.DeletePages.createRoute(scanId)) },
                onExtractPages = { scanId -> navController.navigate(Screen.ExtractPages.createRoute(scanId)) },
                onAppendPages = { scanId -> navController.navigate(Screen.AppendPages.createRoute(scanId)) },
                onDuplicatePages = { scanId -> navController.navigate(Screen.DuplicatePages.createRoute(scanId)) },
                onPageNumbers = { scanId -> navController.navigate(Screen.PageNumbers.createRoute(scanId)) },
                onTextWatermark = { scanId -> navController.navigate(Screen.TextWatermark.createRoute(scanId)) },
                onCompressPdf = { scanId -> navController.navigate(Screen.CompressPdf.createRoute(scanId)) },
                onProtectPdf = { scanId -> navController.navigate(Screen.ProtectPdf.createRoute(scanId)) },
                onUnlockPdf = { scanId -> navController.navigate(Screen.UnlockPdf.createRoute(scanId)) },
                onSignature = { scanId -> navController.navigate(Screen.Signature.createRoute(scanId)) },
                onRemoveTextLayer = { scanId -> navController.navigate(Screen.RemoveTextLayer.createRoute(scanId)) },
                onRemovePassword = { scanId -> navController.navigate(Screen.RemovePassword.createRoute(scanId)) },
                onRestrictUsage = { scanId -> navController.navigate(Screen.RestrictUsage.createRoute(scanId)) },
                onAnnotate = { scanId -> navController.navigate(Screen.Annotate.createRoute(scanId)) },
                onRedact = { scanId -> navController.navigate(Screen.Redact.createRoute(scanId)) },
                onGrayscale = { scanId -> navController.navigate(Screen.Grayscale.createRoute(scanId)) },
                onPdfMetadata = { scanId -> navController.navigate(Screen.PdfMetadata.createRoute(scanId)) },
                onQrScan = { scanId -> navController.navigate(Screen.QrScan.createRoute(scanId)) },
                onBusinessCard = { scanId -> navController.navigate(Screen.BusinessCard.createRoute(scanId)) },
                onOcrReview = { scanId -> navController.navigate(Screen.OcrReview.createRoute(scanId)) },
                onViewer = { scanId -> navController.navigate(Screen.Viewer.createRoute(scanId)) },
                onImagesToPdf = { navController.navigate(Screen.ImagesToPdf.route) }
            )
        )
    }
}

private fun NavGraphBuilder.infoNavGraph(
    navController: NavHostController,
    onThemeModeChange: (ThemeMode) -> Unit,
    appLockManager: AppLockManager
) {
    composable(Screen.Help.route) { HelpScreen() }
    composable(Screen.Trash.route) { TrashScreen() }
    composable(Screen.FolderManagement.route) {
        FolderManagementScreen(onNavigateBack = { navController.navigateUp() })
    }
    composable(Screen.Settings.route) {
        val settingsViewModel: SettingsViewModel = hiltViewModel()
        val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
        val errorMessage by settingsViewModel.error.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val appLockUnavailableMessage = stringResource(R.string.app_lock_unavailable)
        val appLockAuthFailedMessage = stringResource(R.string.app_lock_auth_failed)
        val appLockNotEnrolledMessage = stringResource(R.string.app_lock_not_enrolled)
        SettingsScreen(
            settings = settings,
            onThemeModeChange = onThemeModeChange,
            onM24AnimationEnabledChange = settingsViewModel::setM24AnimationEnabled,
            onDefaultMakeSearchableChange = settingsViewModel::setDefaultMakeSearchable,
            onDefaultOcrLanguageChange = settingsViewModel::setDefaultOcrLanguage,
            onDefaultSortOrderChange = settingsViewModel::setDefaultSortOrder,
            onTrashUndoSnackbarSecondsChange = settingsViewModel::setTrashUndoSnackbarSeconds,
            onAppLockEnabledChange = { enabled ->
                if (!enabled) {
                    settingsViewModel.setAppLockEnabled(false)
                    return@SettingsScreen
                }

                when (appLockManager.getAvailability()) {
                    AppLockAvailability.AVAILABLE -> {
                        val activity = context as? androidx.fragment.app.FragmentActivity
                        if (activity == null) {
                            settingsViewModel.reportError(appLockUnavailableMessage)
                            return@SettingsScreen
                        }
                        scope.launch {
                            when (appLockManager.authenticate(activity)) {
                                AppLockAuthResult.Success -> settingsViewModel.setAppLockEnabled(true)
                                AppLockAuthResult.Cancelled -> settingsViewModel.reportError(appLockAuthFailedMessage)
                                AppLockAuthResult.Unavailable -> settingsViewModel.reportError(appLockUnavailableMessage)
                            }
                        }
                    }

                    AppLockAvailability.NONE_ENROLLED -> {
                        settingsViewModel.reportError(appLockNotEnrolledMessage)
                    }

                    AppLockAvailability.UNAVAILABLE -> {
                        settingsViewModel.reportError(appLockUnavailableMessage)
                    }
                }
            },
            onAppLockTimeoutSecondsChange = settingsViewModel::setAppLockTimeoutSeconds,
            transientError = errorMessage,
            onTransientErrorConsumed = settingsViewModel::clearError
        )
    }
    composable(Screen.Info.route) { InfoScreen() }
    composable(Screen.Privacy.route) { PrivacyScreen() }
}

private fun NavGraphBuilder.viewerNavGraph(
    navController: NavHostController
) {
    composable(
        route = Screen.OcrReview.route,
        arguments = listOf(navArgument("scanId") { type = NavType.LongType })
    ) {
        OcrReviewScreen()
    }
    composable(
        route = Screen.Viewer.route,
        arguments = listOf(navArgument("scanId") { type = NavType.LongType })
    ) {
        PdfViewerScreen(
            onNavigateBack = { navController.navigateUp() },
            onNavigateToSplit = { scanId -> navController.navigate(Screen.Split.createRoute(scanId)) },
            onNavigateToReorder = { scanId -> navController.navigate(Screen.Reorder.createRoute(scanId)) },
            onNavigateToRotate = { scanId -> navController.navigate(Screen.RotatePages.createRoute(scanId)) },
            onNavigateToDeletePages = { scanId -> navController.navigate(Screen.DeletePages.createRoute(scanId)) },
            onNavigateToExtractPages = { scanId -> navController.navigate(Screen.ExtractPages.createRoute(scanId)) },
            onNavigateToAppendPages = { scanId -> navController.navigate(Screen.AppendPages.createRoute(scanId)) },
            onNavigateToDuplicatePages = { scanId -> navController.navigate(Screen.DuplicatePages.createRoute(scanId)) },
            onNavigateToPageNumbers = { scanId -> navController.navigate(Screen.PageNumbers.createRoute(scanId)) },
            onNavigateToTextWatermark = { scanId -> navController.navigate(Screen.TextWatermark.createRoute(scanId)) },
            onNavigateToCompressPdf = { scanId -> navController.navigate(Screen.CompressPdf.createRoute(scanId)) },
            onNavigateToProtectPdf = { scanId -> navController.navigate(Screen.ProtectPdf.createRoute(scanId)) },
            onNavigateToUnlockPdf = { scanId -> navController.navigate(Screen.UnlockPdf.createRoute(scanId)) },
            onNavigateToSignature = { scanId -> navController.navigate(Screen.Signature.createRoute(scanId)) },
            onNavigateToRemoveTextLayer = { scanId -> navController.navigate(Screen.RemoveTextLayer.createRoute(scanId)) },
            onNavigateToRemovePassword = { scanId -> navController.navigate(Screen.RemovePassword.createRoute(scanId)) },
            onNavigateToRestrictUsage = { scanId -> navController.navigate(Screen.RestrictUsage.createRoute(scanId)) },
            onNavigateToAnnotate = { scanId -> navController.navigate(Screen.Annotate.createRoute(scanId)) },
            onNavigateToRedact = { scanId -> navController.navigate(Screen.Redact.createRoute(scanId)) },
            onNavigateToGrayscale = { scanId -> navController.navigate(Screen.Grayscale.createRoute(scanId)) },
            onNavigateToPdfMetadata = { scanId -> navController.navigate(Screen.PdfMetadata.createRoute(scanId)) },
            onNavigateToQrScan = { scanId -> navController.navigate(Screen.QrScan.createRoute(scanId)) },
            onNavigateToBusinessCard = { scanId -> navController.navigate(Screen.BusinessCard.createRoute(scanId)) }
        )
    }
}

private fun NavGraphBuilder.editNavGraph(
    navController: NavHostController
) {
    composable(Screen.Split.route, listOf(navArgument("scanId") { type = NavType.LongType })) {
        SplitScreen(onNavigateBack = { navController.navigateUp() })
    }
    composable(Screen.Reorder.route, listOf(navArgument("scanId") { type = NavType.LongType })) {
        ReorderScreen(onNavigateBack = { navController.navigateUp() })
    }
    composable(Screen.RotatePages.route, listOf(navArgument("scanId") { type = NavType.LongType })) {
        RotatePagesScreen(onNavigateBack = { navController.navigateUp() })
    }
    composable(Screen.DeletePages.route, listOf(navArgument("scanId") { type = NavType.LongType })) {
        DeletePagesScreen(onNavigateBack = { navController.navigateUp() })
    }
    composable(Screen.ExtractPages.route, listOf(navArgument("scanId") { type = NavType.LongType })) {
        ExtractPagesScreen(onNavigateBack = { navController.navigateUp() })
    }
    composable(Screen.DuplicatePages.route, listOf(navArgument("scanId") { type = NavType.LongType })) {
        DuplicatePagesScreen(onNavigateBack = { navController.navigateUp() })
    }
    composable(Screen.PageNumbers.route, listOf(navArgument("scanId") { type = NavType.LongType })) {
        PageNumbersScreen(onNavigateBack = { navController.navigateUp() })
    }
    composable(Screen.TextWatermark.route, listOf(navArgument("scanId") { type = NavType.LongType })) {
        TextWatermarkScreen(onNavigateBack = { navController.navigateUp() })
    }
    composable(Screen.CompressPdf.route, listOf(navArgument("scanId") { type = NavType.LongType })) {
        CompressPdfScreen(onNavigateBack = { navController.navigateUp() })
    }
    composable(Screen.ProtectPdf.route, listOf(navArgument("scanId") { type = NavType.LongType })) {
        ProtectPdfScreen(onNavigateBack = { navController.navigateUp() })
    }
    composable(Screen.UnlockPdf.route, listOf(navArgument("scanId") { type = NavType.LongType })) {
        UnlockPdfScreen(onNavigateBack = { navController.navigateUp() })
    }
    composable(Screen.Signature.route, listOf(navArgument("scanId") { type = NavType.LongType })) {
        SignatureScreen(onNavigateBack = { navController.navigateUp() })
    }
    composable(Screen.RemoveTextLayer.route, listOf(navArgument("scanId") { type = NavType.LongType })) {
        RemoveTextLayerScreen(onNavigateBack = { navController.navigateUp() })
    }
    composable(Screen.RemovePassword.route, listOf(navArgument("scanId") { type = NavType.LongType })) {
        RemovePasswordScreen(onNavigateBack = { navController.navigateUp() })
    }
    composable(Screen.RestrictUsage.route, listOf(navArgument("scanId") { type = NavType.LongType })) {
        RestrictUsageScreen(onNavigateBack = { navController.navigateUp() })
    }
    composable(Screen.Annotate.route, listOf(navArgument("scanId") { type = NavType.LongType })) {
        AnnotateScreen(onNavigateBack = { navController.navigateUp() })
    }
    composable(Screen.Redact.route, listOf(navArgument("scanId") { type = NavType.LongType })) {
        RedactScreen(onNavigateBack = { navController.navigateUp() })
    }
    composable(Screen.Grayscale.route, listOf(navArgument("scanId") { type = NavType.LongType })) {
        ConvertToGrayscaleScreen(onNavigateBack = { navController.navigateUp() })
    }
    composable(Screen.PdfMetadata.route, listOf(navArgument("scanId") { type = NavType.LongType })) {
        PdfMetadataScreen(onNavigateBack = { navController.navigateUp() })
    }
    composable(Screen.QrScan.route, listOf(navArgument("scanId") { type = NavType.LongType })) {
        QrScanScreen(onNavigateBack = { navController.navigateUp() })
    }
    composable(Screen.BusinessCard.route, listOf(navArgument("scanId") { type = NavType.LongType })) {
        BusinessCardScreen(onNavigateBack = { navController.navigateUp() })
    }
}

private fun NavGraphBuilder.appendNavGraph(
    navController: NavHostController
) {
    composable(Screen.AppendPages.route, listOf(navArgument("scanId") { type = NavType.LongType })) {
        AppendScreen(
            onAppendComplete = { scanId ->
                navController.popBackStack()
                navController.navigate(Screen.Viewer.createRoute(scanId)) {
                    launchSingleTop = true
                }
            }
        )
    }
}

private fun NavGraphBuilder.imagesNavGraph(
    navController: NavHostController
) {
    composable(Screen.ImagesToPdf.route) { backStackEntry ->
        val homeEntry = remember(backStackEntry) {
            navController.getBackStackEntry(Screen.Ablage.route)
        }
        val homeVm: HomeViewModel = hiltViewModel(homeEntry)
        val homeUiState by homeVm.archiveUiState.collectAsStateWithLifecycle()
        ImagesToPdfScreen(
            imageUris = homeUiState.pendingImageUris,
            onNavigateBack = {
                homeVm.clearPendingImageUris()
                navController.popBackStack()
            }
        )
    }
}
