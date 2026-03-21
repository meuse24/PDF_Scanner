package info.meuse24.pdf_scanner.ui.navigation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.ui.help.HelpScreen
import info.meuse24.pdf_scanner.ui.home.HomeScreen
import info.meuse24.pdf_scanner.ui.info.InfoScreen
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

    var scanTrigger by remember { mutableStateOf(false) }

    fun closeDrawer() = scope.launch { drawerState.close() }
    fun openDrawer()  = scope.launch { drawerState.open() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.app_name),
                    style     = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier  = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    color     = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))

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

                DrawerItem(
                    icon     = Icons.Default.PhotoCamera,
                    label    = stringResource(R.string.nav_start_scanner),
                    selected = false
                ) {
                    if (currentRoute != Screen.Ablage.route) {
                        navController.navigate(Screen.Ablage.route) {
                            popUpTo(Screen.Ablage.route) { inclusive = true }
                        }
                    }
                    scanTrigger = true
                    closeDrawer()
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))

                DrawerItem(
                    icon     = Icons.AutoMirrored.Filled.Help,
                    label    = stringResource(R.string.nav_help),
                    selected = currentRoute == Screen.Help.route
                ) {
                    // launchSingleTop prevents stacking identical destinations (#7)
                    navController.navigate(Screen.Help.route) { launchSingleTop = true }
                    closeDrawer()
                }

                DrawerItem(
                    icon     = Icons.Default.Info,
                    label    = stringResource(R.string.nav_info),
                    selected = currentRoute == Screen.Info.route
                ) {
                    navController.navigate(Screen.Info.route) { launchSingleTop = true }
                    closeDrawer()
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (currentRoute) {
                                Screen.Help.route -> stringResource(R.string.nav_help)
                                Screen.Info.route -> stringResource(R.string.nav_info)
                                else              -> stringResource(R.string.app_name)
                            }
                        )
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
                        containerColor        = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor     = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            floatingActionButton = {
                if (currentRoute == Screen.Ablage.route || currentRoute == null) {
                    FloatingActionButton(onClick = { scanTrigger = true }) {
                        Icon(Icons.Default.Add, stringResource(R.string.cd_new_scan))
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController    = navController,
                startDestination = Screen.Ablage.route,
                modifier         = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Ablage.route) {
                    HomeScreen(
                        scanTrigger     = scanTrigger,
                        onScanTriggered = { scanTrigger = false }
                    )
                }
                composable(Screen.Help.route) { HelpScreen() }
                composable(Screen.Info.route) { InfoScreen() }
            }
        }
    }
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
