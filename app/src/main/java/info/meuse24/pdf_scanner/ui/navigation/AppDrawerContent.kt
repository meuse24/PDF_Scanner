package info.meuse24.pdf_scanner.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.meuse24.pdf_scanner.BuildConfig
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.Folder
import info.meuse24.pdf_scanner.domain.model.LocalSyncState
import info.meuse24.pdf_scanner.ui.home.ArchiveFilter
import info.meuse24.pdf_scanner.ui.sync.LocalSyncStatusViewModel

@Composable
internal fun AppDrawerContent(
    m24AnimationEnabled: Boolean,
    currentRoute: String?,
    folders: List<Folder>,
    archiveFilter: ArchiveFilter,
    onNavigateToTopLevel: (Screen) -> Unit,
    onShowAllDocuments: () -> Unit,
    onShowFavorites: () -> Unit,
    onShowFolder: (Long) -> Unit,
    onManageFolders: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedM24Badge(
                text = "M24",
                animationKey = "drawer_header",
                enabled = m24AnimationEnabled
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = "PDF Scanner",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.drawer_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))

        DrawerItem(
            icon = Icons.Default.FolderOpen,
            label = stringResource(R.string.folder_all_documents),
            selected = currentRoute == Screen.Ablage.route && archiveFilter == ArchiveFilter.AllDocuments,
            onClick = onShowAllDocuments
        )
        DrawerItem(
            icon = Icons.Default.Star,
            label = stringResource(R.string.folder_favorites),
            selected = currentRoute == Screen.Ablage.route && archiveFilter == ArchiveFilter.Favorites,
            onClick = onShowFavorites
        )
        folders.forEach { folder ->
            DrawerItem(
                icon = Icons.Default.Folder,
                label = folder.name,
                selected = currentRoute == Screen.Ablage.route &&
                    archiveFilter == ArchiveFilter.Folder(folder.id),
                onClick = { onShowFolder(folder.id) }
            )
        }
        DrawerItem(
            icon = Icons.Default.FolderOpen,
            label = stringResource(R.string.folder_manage),
            selected = currentRoute == Screen.FolderManagement.route,
            onClick = onManageFolders
        )
        DrawerItem(
            icon = Icons.Default.Delete,
            label = stringResource(R.string.nav_trash),
            selected = currentRoute == Screen.Trash.route,
            onClick = { onNavigateToTopLevel(Screen.Trash) }
        )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))

        val syncStatusViewModel: LocalSyncStatusViewModel = hiltViewModel()
        val syncState by syncStatusViewModel.state.collectAsStateWithLifecycle()
        DrawerItem(
            icon = Icons.Default.Wifi,
            label = stringResource(R.string.local_sync_drawer_label),
            selected = currentRoute == Screen.LocalSync.route,
            showActiveBadge = syncState is LocalSyncState.Running,
            onClick = { onNavigateToTopLevel(Screen.LocalSync) }
        )
        DrawerItem(
            icon = Icons.Default.Settings,
            label = stringResource(R.string.nav_settings),
            selected = currentRoute == Screen.Settings.route,
            onClick = { onNavigateToTopLevel(Screen.Settings) }
        )
        DrawerItem(
            icon = Icons.AutoMirrored.Filled.Help,
            label = stringResource(R.string.nav_help),
            selected = currentRoute == Screen.Help.route,
            onClick = { onNavigateToTopLevel(Screen.Help) }
        )
        DrawerItem(
            icon = Icons.Default.Info,
            label = stringResource(R.string.nav_info),
            selected = currentRoute == Screen.Info.route,
            onClick = { onNavigateToTopLevel(Screen.Info) }
        )
        DrawerItem(
            icon = Icons.Default.PrivacyTip,
            label = stringResource(R.string.nav_privacy),
            selected = currentRoute == Screen.Privacy.route,
            onClick = { onNavigateToTopLevel(Screen.Privacy) }
        )
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    showActiveBadge: Boolean = false
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = label) },
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label)
                if (showActiveBadge) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}
