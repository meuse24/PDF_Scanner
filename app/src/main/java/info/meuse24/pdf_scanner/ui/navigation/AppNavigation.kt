package info.meuse24.pdf_scanner.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.Folder
import info.meuse24.pdf_scanner.domain.model.LocalSyncState
import info.meuse24.pdf_scanner.ui.components.LocalAppSnackbarHostState
import info.meuse24.pdf_scanner.ui.entry.AppEntryAction
import info.meuse24.pdf_scanner.ui.home.ArchiveFilter
import info.meuse24.pdf_scanner.ui.sync.LocalSyncStatusViewModel
import info.meuse24.pdf_scanner.domain.model.ThemeMode
import info.meuse24.pdf_scanner.util.AppLockManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    m24AnimationEnabled: Boolean,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    heightSizeClass: WindowHeightSizeClass = WindowHeightSizeClass.Medium,
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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val canNavigateBack = navController.previousBackStackEntry != null
    val isHomeRoute = currentRoute == Screen.Ablage.route || currentRoute == null
    val showNavigationRail = widthSizeClass != WindowWidthSizeClass.Compact
    val isLandscapeCompact = showNavigationRail && heightSizeClass == WindowHeightSizeClass.Compact
    val contentMaxWidth = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> null
        WindowWidthSizeClass.Medium -> 840.dp
        else -> 1040.dp
    }

    var addActionTrigger by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    val snackbarBottomPadding = if (currentRoute?.startsWith(Screen.Viewer.route.substringBefore("{")) == true) 88.dp else 0.dp

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

    fun navigateToTopLevel(screen: Screen) {
        navController.navigate(screen.route) {
            if (screen == Screen.Ablage) {
                popUpTo(Screen.Ablage.route) { inclusive = true }
            } else {
                popUpTo(Screen.Ablage.route)
                launchSingleTop = true
            }
        }
        closeDrawer()
    }

    val drawerContent: @Composable () -> Unit = {
        AppDrawerContent(
                m24AnimationEnabled = m24AnimationEnabled,
                currentRoute = currentRoute,
                folders = folders,
                archiveFilter = archiveFilter,
                onNavigateToTopLevel = { screen -> navigateToTopLevel(screen) },
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

    val appContent: @Composable () -> Unit = {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
            ),
            topBar = {
                if (!(isLandscapeCompact && isSelectionMode && isHomeRoute)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .height(48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                            AppBarTitle(
                                currentRoute = currentRoute,
                                isHomeRoute = isHomeRoute,
                                m24AnimationEnabled = m24AnimationEnabled
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (!showNavigationRail && isHomeRoute && !isSelectionMode) {
                    FloatingActionButton(
                        onClick = { addActionTrigger = true },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Default.Add, stringResource(R.string.cd_new_scan))
                    }
                }
            },
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = snackbarBottomPadding)
                )
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
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    val contentModifier = if (contentMaxWidth == null) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxHeight()
                            .widthIn(max = contentMaxWidth)
                            .fillMaxWidth()
                    }
                    Box(modifier = contentModifier) {
                        CompositionLocalProvider(LocalAppSnackbarHostState provides snackbarHostState) {
                            AppNavigationHost(
                                navController = navController,
                                innerPadding = innerPadding,
                                addActionTrigger = addActionTrigger,
                                onAddActionTriggered = { addActionTrigger = false },
                                onSelectionModeChange = { isSelectionMode = it },
                                isLandscapeCompact = isLandscapeCompact,
                                onThemeModeChange = onThemeModeChange,
                                pendingAppEntryAction = pendingAppEntryAction,
                                onConsumeAppEntryAction = onConsumeAppEntryAction,
                                appLockManager = appLockManager
                            )
                        }
                    }
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !showNavigationRail && drawerGesturesEnabled,
        drawerContent = drawerContent
    ) {
        if (showNavigationRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                AppNavigationRail(
                    currentRoute = currentRoute,
                    showAddAction = isHomeRoute && !isSelectionMode,
                    onAddAction = { addActionTrigger = true },
                    onNavigateToTopLevel = { screen -> navigateToTopLevel(screen) }
                )
                appContent()
            }
        } else {
            appContent()
        }
    }
}

@Composable
private fun AppNavigationRail(
    currentRoute: String?,
    showAddAction: Boolean,
    onAddAction: () -> Unit,
    onNavigateToTopLevel: (Screen) -> Unit
) {
    NavigationRail(
        header = if (showAddAction) {
            {
                FloatingActionButton(
                    onClick = onAddAction,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.Add, stringResource(R.string.cd_new_scan))
                }
            }
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RailItem(
                icon = Icons.Default.FolderOpen,
                label = stringResource(R.string.folder_all_documents),
                selected = currentRoute == Screen.Ablage.route || currentRoute == null,
                onClick = { onNavigateToTopLevel(Screen.Ablage) }
            )
            RailItem(
                icon = Icons.Default.Delete,
                label = stringResource(R.string.nav_trash),
                selected = currentRoute == Screen.Trash.route,
                onClick = { onNavigateToTopLevel(Screen.Trash) }
            )
            val syncStatusViewModel: LocalSyncStatusViewModel = hiltViewModel()
            val syncState by syncStatusViewModel.state.collectAsStateWithLifecycle()
            RailItem(
                icon = Icons.Default.Wifi,
                label = stringResource(R.string.local_sync_drawer_label),
                selected = currentRoute == Screen.LocalSync.route,
                showActiveBadge = syncState is LocalSyncState.Running,
                onClick = { onNavigateToTopLevel(Screen.LocalSync) }
            )
            RailItem(
                icon = Icons.Default.Settings,
                label = stringResource(R.string.nav_settings),
                selected = currentRoute == Screen.Settings.route,
                onClick = { onNavigateToTopLevel(Screen.Settings) }
            )
            RailItem(
                icon = Icons.AutoMirrored.Filled.Help,
                label = stringResource(R.string.nav_help),
                selected = currentRoute == Screen.Help.route,
                onClick = { onNavigateToTopLevel(Screen.Help) }
            )
        }
    }
}

@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    showActiveBadge: Boolean = false
) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = {
            if (showActiveBadge) {
                BadgedBox(badge = { Badge() }) {
                    Icon(icon, contentDescription = label)
                }
            } else {
                Icon(icon, contentDescription = label)
            }
        },
        label = { androidx.compose.material3.Text(label) }
    )
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
