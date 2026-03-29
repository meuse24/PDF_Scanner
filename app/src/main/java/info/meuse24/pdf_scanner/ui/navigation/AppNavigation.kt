package info.meuse24.pdf_scanner.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import info.meuse24.pdf_scanner.BuildConfig
import info.meuse24.pdf_scanner.R
import androidx.navigation.NavType
import androidx.navigation.navArgument
import info.meuse24.pdf_scanner.ui.help.HelpScreen
import info.meuse24.pdf_scanner.ui.home.HomeScreen
import info.meuse24.pdf_scanner.ui.info.InfoScreen
import info.meuse24.pdf_scanner.ui.documentaction.CompressPdfScreen
import info.meuse24.pdf_scanner.ui.documentaction.ConvertToGrayscaleScreen
import info.meuse24.pdf_scanner.ui.documentaction.PdfMetadataScreen
import info.meuse24.pdf_scanner.ui.documentaction.ProtectPdfScreen
import info.meuse24.pdf_scanner.ui.documentaction.RemovePasswordScreen
import info.meuse24.pdf_scanner.ui.documentaction.RemoveTextLayerScreen
import info.meuse24.pdf_scanner.ui.documentaction.RestrictUsageScreen
import info.meuse24.pdf_scanner.ui.documentaction.UnlockPdfScreen
import info.meuse24.pdf_scanner.ui.annotate.AnnotateScreen
import info.meuse24.pdf_scanner.ui.redact.RedactScreen
import info.meuse24.pdf_scanner.ui.signature.SignatureScreen
import info.meuse24.pdf_scanner.ui.overlay.PageNumbersScreen
import info.meuse24.pdf_scanner.ui.overlay.TextWatermarkScreen
import info.meuse24.pdf_scanner.ui.pageedit.DeletePagesScreen
import info.meuse24.pdf_scanner.ui.pageedit.DuplicatePagesScreen
import info.meuse24.pdf_scanner.ui.pageedit.ExtractPagesScreen
import info.meuse24.pdf_scanner.ui.pageedit.RotatePagesScreen
import info.meuse24.pdf_scanner.ui.privacy.PrivacyScreen
import info.meuse24.pdf_scanner.ui.reorder.ReorderScreen
import info.meuse24.pdf_scanner.ui.split.SplitScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController   = rememberNavController()
    val drawerState     = rememberDrawerState(DrawerValue.Closed)
    val scope           = rememberCoroutineScope()
    val currentEntry    by navController.currentBackStackEntryAsState()
    val currentRoute    = currentEntry?.destination?.route
    val canNavigateBack = navController.previousBackStackEntry != null
    val isHomeRoute     = currentRoute == Screen.Ablage.route || currentRoute == null

    var addActionTrigger by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }

    fun closeDrawer() = scope.launch { drawerState.close() }
    fun openDrawer()  = scope.launch { drawerState.open() }

    // Drawer-Geste nur auf Top-Level-Screens erlauben
    val drawerGesturesEnabled = currentRoute == Screen.Ablage.route
        || currentRoute == Screen.Help.route
        || currentRoute == Screen.Info.route
        || currentRoute == Screen.Privacy.route
        || currentRoute == null

    ModalNavigationDrawer(
        drawerState     = drawerState,
        gesturesEnabled = drawerGesturesEnabled,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                // ── App-Header ───────────────────────────────────────────────
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter            = painterResource(R.drawable.app_icon),
                        contentDescription = null,
                        modifier           = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text       = stringResource(R.string.app_name),
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text  = stringResource(R.string.drawer_version, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))

                // ── Primäre Navigation ───────────────────────────────────────
                DrawerItem(
                    icon     = Icons.Default.FolderOpen,
                    label    = stringResource(R.string.nav_archive),
                    selected = currentRoute == Screen.Ablage.route
                ) {
                    navController.navigate(Screen.Ablage.route) {
                        popUpTo(Screen.Ablage.route) { inclusive = true }
                    }
                    closeDrawer()
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))

                // ── Sekundäre Navigation ─────────────────────────────────────
                DrawerItem(
                    icon     = Icons.AutoMirrored.Filled.Help,
                    label    = stringResource(R.string.nav_help),
                    selected = currentRoute == Screen.Help.route
                ) {
                    navController.navigate(Screen.Help.route) {
                        popUpTo(Screen.Ablage.route)
                        launchSingleTop = true
                    }
                    closeDrawer()
                }

                DrawerItem(
                    icon     = Icons.Default.Info,
                    label    = stringResource(R.string.nav_info),
                    selected = currentRoute == Screen.Info.route
                ) {
                    navController.navigate(Screen.Info.route) {
                        popUpTo(Screen.Ablage.route)
                        launchSingleTop = true
                    }
                    closeDrawer()
                }

                DrawerItem(
                    icon     = Icons.Default.PrivacyTip,
                    label    = stringResource(R.string.nav_privacy),
                    selected = currentRoute == Screen.Privacy.route
                ) {
                    navController.navigate(Screen.Privacy.route) {
                        popUpTo(Screen.Ablage.route)
                        launchSingleTop = true
                    }
                    closeDrawer()
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        AppBarTitle(currentRoute = currentRoute, isHomeRoute = isHomeRoute)
                    },
                    navigationIcon = {
                        if (canNavigateBack) {
                            IconButton(onClick = { navController.navigateUp() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_navigate_back))
                            }
                        } else {
                            IconButton(onClick = { openDrawer() }) {
                                Icon(Icons.Default.Menu, stringResource(R.string.cd_open_menu))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor             = MaterialTheme.colorScheme.surfaceContainerHigh,
                        titleContentColor          = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            floatingActionButton = {
                if ((currentRoute == Screen.Ablage.route || currentRoute == null) && !isSelectionMode) {
                    FloatingActionButton(
                        onClick = { addActionTrigger = true },
                        shape   = RoundedCornerShape(20.dp)
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
                NavHost(
                    navController    = navController,
                    startDestination = Screen.Ablage.route,
                    modifier         = Modifier.padding(innerPadding)
                ) {
                    composable(Screen.Ablage.route) {
                        HomeScreen(
                            addActionTrigger           = addActionTrigger,
                            onAddActionTriggered       = { addActionTrigger = false },
                            onSelectionModeChange      = { isSelectionMode = it },
                            onNavigateToSplit          = { scanId -> navController.navigate(Screen.Split.createRoute(scanId)) },
                            onNavigateToReorder        = { scanId -> navController.navigate(Screen.Reorder.createRoute(scanId)) },
                            onNavigateToRotate         = { scanId -> navController.navigate(Screen.RotatePages.createRoute(scanId)) },
                            onNavigateToDeletePages    = { scanId -> navController.navigate(Screen.DeletePages.createRoute(scanId)) },
                            onNavigateToExtractPages   = { scanId -> navController.navigate(Screen.ExtractPages.createRoute(scanId)) },
                            onNavigateToDuplicatePages = { scanId -> navController.navigate(Screen.DuplicatePages.createRoute(scanId)) },
                            onNavigateToPageNumbers    = { scanId -> navController.navigate(Screen.PageNumbers.createRoute(scanId)) },
                            onNavigateToTextWatermark  = { scanId -> navController.navigate(Screen.TextWatermark.createRoute(scanId)) },
                            onNavigateToCompressPdf    = { scanId -> navController.navigate(Screen.CompressPdf.createRoute(scanId)) },
                            onNavigateToProtectPdf     = { scanId -> navController.navigate(Screen.ProtectPdf.createRoute(scanId)) },
                            onNavigateToUnlockPdf      = { scanId -> navController.navigate(Screen.UnlockPdf.createRoute(scanId)) },
                            onNavigateToSignature      = { scanId -> navController.navigate(Screen.Signature.createRoute(scanId)) },
                            onNavigateToRemoveTextLayer = { scanId -> navController.navigate(Screen.RemoveTextLayer.createRoute(scanId)) },
                            onNavigateToRemovePassword  = { scanId -> navController.navigate(Screen.RemovePassword.createRoute(scanId)) },
                            onNavigateToRestrictUsage   = { scanId -> navController.navigate(Screen.RestrictUsage.createRoute(scanId)) },
                            onNavigateToAnnotate        = { scanId -> navController.navigate(Screen.Annotate.createRoute(scanId)) },
                            onNavigateToRedact          = { scanId -> navController.navigate(Screen.Redact.createRoute(scanId)) },
                            onNavigateToGrayscale       = { scanId -> navController.navigate(Screen.Grayscale.createRoute(scanId)) },
                            onNavigateToPdfMetadata     = { scanId -> navController.navigate(Screen.PdfMetadata.createRoute(scanId)) }
                        )
                    }
                    composable(Screen.Help.route)    { HelpScreen() }
                    composable(Screen.Info.route)    { InfoScreen() }
                    composable(Screen.Privacy.route) { PrivacyScreen() }
                    composable(
                        route     = Screen.Split.route,
                        arguments = listOf(navArgument("scanId") { type = NavType.LongType })
                    ) {
                        SplitScreen(onNavigateBack = { navController.navigateUp() })
                    }
                    composable(
                        route     = Screen.Reorder.route,
                        arguments = listOf(navArgument("scanId") { type = NavType.LongType })
                    ) {
                        ReorderScreen(onNavigateBack = { navController.navigateUp() })
                    }
                    composable(
                        route = Screen.RotatePages.route,
                        arguments = listOf(navArgument("scanId") { type = NavType.LongType })
                    ) {
                        RotatePagesScreen(onNavigateBack = { navController.navigateUp() })
                    }
                    composable(
                        route = Screen.DeletePages.route,
                        arguments = listOf(navArgument("scanId") { type = NavType.LongType })
                    ) {
                        DeletePagesScreen(onNavigateBack = { navController.navigateUp() })
                    }
                    composable(
                        route = Screen.ExtractPages.route,
                        arguments = listOf(navArgument("scanId") { type = NavType.LongType })
                    ) {
                        ExtractPagesScreen(onNavigateBack = { navController.navigateUp() })
                    }
                    composable(
                        route = Screen.DuplicatePages.route,
                        arguments = listOf(navArgument("scanId") { type = NavType.LongType })
                    ) {
                        DuplicatePagesScreen(onNavigateBack = { navController.navigateUp() })
                    }
                    composable(
                        route = Screen.PageNumbers.route,
                        arguments = listOf(navArgument("scanId") { type = NavType.LongType })
                    ) {
                        PageNumbersScreen(onNavigateBack = { navController.navigateUp() })
                    }
                    composable(
                        route = Screen.TextWatermark.route,
                        arguments = listOf(navArgument("scanId") { type = NavType.LongType })
                    ) {
                        TextWatermarkScreen(onNavigateBack = { navController.navigateUp() })
                    }
                    composable(
                        route = Screen.CompressPdf.route,
                        arguments = listOf(navArgument("scanId") { type = NavType.LongType })
                    ) {
                        CompressPdfScreen(onNavigateBack = { navController.navigateUp() })
                    }
                    composable(
                        route = Screen.ProtectPdf.route,
                        arguments = listOf(navArgument("scanId") { type = NavType.LongType })
                    ) {
                        ProtectPdfScreen(onNavigateBack = { navController.navigateUp() })
                    }
                    composable(
                        route = Screen.UnlockPdf.route,
                        arguments = listOf(navArgument("scanId") { type = NavType.LongType })
                    ) {
                        UnlockPdfScreen(onNavigateBack = { navController.navigateUp() })
                    }
                    composable(
                        route = Screen.Signature.route,
                        arguments = listOf(navArgument("scanId") { type = NavType.LongType })
                    ) {
                        SignatureScreen(onNavigateBack = { navController.navigateUp() })
                    }
                    composable(
                        route = Screen.RemoveTextLayer.route,
                        arguments = listOf(navArgument("scanId") { type = NavType.LongType })
                    ) {
                        RemoveTextLayerScreen(onNavigateBack = { navController.navigateUp() })
                    }
                    composable(
                        route = Screen.RemovePassword.route,
                        arguments = listOf(navArgument("scanId") { type = NavType.LongType })
                    ) {
                        RemovePasswordScreen(onNavigateBack = { navController.navigateUp() })
                    }
                    composable(
                        route = Screen.RestrictUsage.route,
                        arguments = listOf(navArgument("scanId") { type = NavType.LongType })
                    ) {
                        RestrictUsageScreen(onNavigateBack = { navController.navigateUp() })
                    }
                    composable(
                        route = Screen.Annotate.route,
                        arguments = listOf(navArgument("scanId") { type = NavType.LongType })
                    ) {
                        AnnotateScreen(onNavigateBack = { navController.navigateUp() })
                    }
                    composable(
                        route = Screen.Redact.route,
                        arguments = listOf(navArgument("scanId") { type = NavType.LongType })
                    ) {
                        RedactScreen(onNavigateBack = { navController.navigateUp() })
                    }
                    composable(
                        route = Screen.Grayscale.route,
                        arguments = listOf(navArgument("scanId") { type = NavType.LongType })
                    ) {
                        ConvertToGrayscaleScreen(onNavigateBack = { navController.navigateUp() })
                    }
                    composable(
                        route = Screen.PdfMetadata.route,
                        arguments = listOf(navArgument("scanId") { type = NavType.LongType })
                    ) {
                        PdfMetadataScreen(onNavigateBack = { navController.navigateUp() })
                    }
                }
            } // Box
        }
    }
}

@Composable
private fun AppBarTitle(
    currentRoute: String?,
    isHomeRoute: Boolean
) {
    if (isHomeRoute) {
        val appName = stringResource(R.string.app_name)
        val splitIndex = appName.indexOf(' ')
        val prefix = if (splitIndex > 0) appName.substring(0, splitIndex) else appName
        val suffix = if (splitIndex > 0) appName.substring(splitIndex) else ""
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.tertiary)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = prefix,
                    style = MaterialTheme.typography.titleMedium.copy(letterSpacing = (-0.2).sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiary,
                    maxLines = 1
                )
            }
            if (suffix.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = suffix.trimStart(),
                    style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = (-0.4).sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        return
    }

    Text(
        text = when {
            currentRoute == Screen.Help.route -> stringResource(R.string.nav_help)
            currentRoute == Screen.Info.route -> stringResource(R.string.nav_info)
            currentRoute == Screen.Privacy.route -> stringResource(R.string.nav_privacy)
            currentRoute?.startsWith("split/") == true -> stringResource(R.string.split_screen_title)
            currentRoute?.startsWith("reorder/") == true -> stringResource(R.string.reorder_screen_title)
            currentRoute?.startsWith("rotate-pages/") == true -> stringResource(R.string.rotate_screen_title)
            currentRoute?.startsWith("delete-pages/") == true -> stringResource(R.string.delete_pages_screen_title)
            currentRoute?.startsWith("extract-pages/") == true -> stringResource(R.string.extract_pages_screen_title)
            currentRoute?.startsWith("duplicate-pages/") == true -> stringResource(R.string.duplicate_pages_screen_title)
            currentRoute?.startsWith("page-numbers/") == true -> stringResource(R.string.page_numbers_screen_title)
            currentRoute?.startsWith("text-watermark/") == true -> stringResource(R.string.watermark_screen_title)
            currentRoute?.startsWith("compress-pdf/") == true -> stringResource(R.string.compress_pdf_screen_title)
            currentRoute?.startsWith("protect-pdf/") == true -> stringResource(R.string.protect_pdf_screen_title)
            currentRoute?.startsWith("unlock-pdf/") == true -> stringResource(R.string.unlock_pdf_screen_title)
            currentRoute?.startsWith("signature/") == true -> stringResource(R.string.signature_screen_title)
            currentRoute?.startsWith("remove-text-layer/") == true -> stringResource(R.string.remove_text_layer_screen_title)
            currentRoute?.startsWith("remove-password/") == true -> stringResource(R.string.remove_password_screen_title)
            currentRoute?.startsWith("restrict-usage/") == true -> stringResource(R.string.restrict_usage_screen_title)
            currentRoute?.startsWith("annotate/") == true -> stringResource(R.string.annotate_screen_title)
            currentRoute?.startsWith("redact/") == true -> stringResource(R.string.redact_screen_title)
            currentRoute?.startsWith("grayscale/") == true -> stringResource(R.string.grayscale_screen_title)
            currentRoute?.startsWith("pdf-metadata/") == true -> stringResource(R.string.metadata_screen_title)
            else -> stringResource(R.string.app_name)
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun DrawerItem(
    icon:     ImageVector,
    label:    String,
    selected: Boolean,
    onClick:  () -> Unit
) {
    NavigationDrawerItem(
        icon     = { Icon(icon, contentDescription = label) },
        label    = { Text(label) },
        selected = selected,
        onClick  = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}
