package info.meuse24.pdf_scanner.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.Folder
import info.meuse24.pdf_scanner.ui.entry.AppEntryAction
import info.meuse24.pdf_scanner.ui.home.ArchiveFilter
import info.meuse24.pdf_scanner.ui.theme.ThemeMode
import info.meuse24.pdf_scanner.util.AppLockManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    m24AnimationEnabled: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    pendingAppEntryAction: AppEntryAction? = null,
    onConsumeAppEntryAction: (AppEntryAction) -> Unit = {},
    appLockManager: AppLockManager,
    folders: List<Folder>,
    archiveFilter: ArchiveFilter,
    onShowAllDocuments: () -> Unit,
    onShowFavorites: () -> Unit,
    onShowFolder: (Long) -> Unit
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val canNavigateBack = navController.previousBackStackEntry != null
    val isHomeRoute = currentRoute == Screen.Ablage.route || currentRoute == null

    var addActionTrigger by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }

    val drawerGesturesEnabled = currentRoute in setOf(
        null,
        Screen.Ablage.route,
        Screen.Trash.route,
        Screen.Settings.route,
        Screen.Help.route,
        Screen.Info.route,
        Screen.Privacy.route
    )

    fun closeDrawer() = scope.launch { drawerState.close() }
    fun openDrawer() = scope.launch { drawerState.open() }

    LaunchedEffect(pendingAppEntryAction, currentRoute) {
        when (pendingAppEntryAction) {
            AppEntryAction.OpenTrash -> {
                if (currentRoute != Screen.Trash.route) {
                    navController.navigate(Screen.Trash.route) {
                        popUpTo(Screen.Ablage.route)
                        launchSingleTop = true
                    }
                }
                onConsumeAppEntryAction(pendingAppEntryAction)
            }

            AppEntryAction.ImportImages,
            AppEntryAction.ScanNew,
            is AppEntryAction.ShareImages,
            is AppEntryAction.SharePdf -> {
                // HomeScreen consumes these actions after it opens the corresponding
                // dialog/launcher flow. AppNavigation only makes sure the archive is visible.
                if (shouldNavigatePendingHomeActionToArchive(pendingAppEntryAction, currentRoute)) {
                    navController.navigate(Screen.Ablage.route) {
                        popUpTo(Screen.Ablage.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            null -> Unit
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerGesturesEnabled,
        drawerContent = {
            AppDrawerContent(
                m24AnimationEnabled = m24AnimationEnabled,
                currentRoute = currentRoute,
                folders = folders,
                archiveFilter = archiveFilter,
                onNavigateToTopLevel = { screen ->
                    navController.navigate(screen.route) {
                        if (screen == Screen.Ablage) {
                            popUpTo(Screen.Ablage.route) { inclusive = true }
                        } else {
                            popUpTo(Screen.Ablage.route)
                            launchSingleTop = true
                        }
                    }
                    closeDrawer()
                },
                onShowAllDocuments = {
                    onShowAllDocuments()
                    navController.navigate(Screen.Ablage.route) {
                        popUpTo(Screen.Ablage.route) { inclusive = true }
                        launchSingleTop = true
                    }
                    closeDrawer()
                },
                onShowFavorites = {
                    onShowFavorites()
                    navController.navigate(Screen.Ablage.route) {
                        popUpTo(Screen.Ablage.route) { inclusive = true }
                        launchSingleTop = true
                    }
                    closeDrawer()
                },
                onShowFolder = { folderId ->
                    onShowFolder(folderId)
                    navController.navigate(Screen.Ablage.route) {
                        popUpTo(Screen.Ablage.route) { inclusive = true }
                        launchSingleTop = true
                    }
                    closeDrawer()
                },
                onManageFolders = {
                    navController.navigate(Screen.FolderManagement.route) {
                        launchSingleTop = true
                    }
                    closeDrawer()
                }
            )
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
            ),
            topBar = {
                TopAppBar(
                    title = {
                        AppBarTitle(
                            currentRoute = currentRoute,
                            isHomeRoute = isHomeRoute,
                            m24AnimationEnabled = m24AnimationEnabled
                        )
                    },
                    navigationIcon = {
                        if (canNavigateBack) {
                            IconButton(onClick = { navController.navigateUp() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    stringResource(R.string.cd_navigate_back)
                                )
                            }
                        } else {
                            IconButton(onClick = { openDrawer() }) {
                                Icon(Icons.Default.Menu, stringResource(R.string.cd_open_menu))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            floatingActionButton = {
                if ((currentRoute == Screen.Ablage.route || currentRoute == null) && !isSelectionMode) {
                    FloatingActionButton(
                        onClick = { addActionTrigger = true },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Default.Add, stringResource(R.string.cd_new_scan))
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
                                0.4f to MaterialTheme.colorScheme.surface.copy(alpha = 0.0f),
                                1.0f to MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.10f)
                            )
                        )
                    )
            ) {
                AppNavigationHost(
                    navController = navController,
                    innerPadding = innerPadding,
                    addActionTrigger = addActionTrigger,
                    onAddActionTriggered = { addActionTrigger = false },
                    onSelectionModeChange = { isSelectionMode = it },
                    onThemeModeChange = onThemeModeChange,
                    pendingAppEntryAction = pendingAppEntryAction,
                    onConsumeAppEntryAction = onConsumeAppEntryAction,
                    appLockManager = appLockManager
                )
            }
        }
    }
}

internal fun shouldNavigatePendingHomeActionToArchive(
    pendingAppEntryAction: AppEntryAction?,
    currentRoute: String?
): Boolean = when (pendingAppEntryAction) {
    AppEntryAction.ImportImages,
    AppEntryAction.ScanNew,
    is AppEntryAction.ShareImages,
    is AppEntryAction.SharePdf -> {
        // On cold start the NavHost has not published its start destination yet.
        // Navigating again here can recreate Home before the share/import UI is shown.
        currentRoute != null && currentRoute != Screen.Ablage.route
    }
    AppEntryAction.OpenTrash,
    null -> false
}
