package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.FolderEntity
import com.example.data.local.ProfileWithCookie
import com.example.ui.state.FolderFilter
import com.example.ui.state.OpenTabInfo
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.FolderColors
import com.example.ui.theme.MonoCodeStyle
import kotlinx.coroutines.launch

object WorkspaceRoute {
    const val PROFILES = "profiles_hub"
    const val BROWSER = "browser_workspace"
    const val SETTINGS = "settings"
}

@Composable
fun WorkspaceNavigationScaffold(
    currentRoute: String,
    folders: List<FolderEntity>,
    allProfiles: List<ProfileWithCookie>,
    selectedFolderFilter: Long,
    openTabs: List<OpenTabInfo>,
    activeTabProfileId: Long?,
    onNavigateRoute: (String) -> Unit,
    onSelectFolder: (Long) -> Unit,
    onManageFolders: () -> Unit,
    onSwitchSession: (Long) -> Unit,
    onCloseSession: (Long) -> Unit,
    onCloseAllSessions: () -> Unit,
    content: @Composable (showDrawerButton: Boolean, onOpenDrawer: () -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isPermanentSidebar = maxWidth >= 840.dp

        if (!isPermanentSidebar) {
            BackHandler(enabled = drawerState.isOpen) {
                scope.launch { drawerState.close() }
            }
        }

        val closeDrawerIfModal: () -> Unit = {
            if (!isPermanentSidebar) {
                scope.launch { drawerState.close() }
            }
        }

        val drawerBody: @Composable () -> Unit = {
            WorkspaceSidebarContent(
                currentRoute = currentRoute,
                folders = folders,
                allProfiles = allProfiles,
                selectedFolderFilter = selectedFolderFilter,
                openTabs = openTabs,
                activeTabProfileId = activeTabProfileId,
                onNavigateRoute = { route ->
                    onNavigateRoute(route)
                    closeDrawerIfModal()
                },
                onSelectFolder = { folderId ->
                    onSelectFolder(folderId)
                    onNavigateRoute(WorkspaceRoute.PROFILES)
                    closeDrawerIfModal()
                },
                onManageFolders = {
                    onManageFolders()
                    closeDrawerIfModal()
                },
                onSwitchSession = { profileId ->
                    onSwitchSession(profileId)
                    onNavigateRoute(WorkspaceRoute.BROWSER)
                    closeDrawerIfModal()
                },
                onCloseSession = onCloseSession,
                onCloseAllSessions = onCloseAllSessions,
            )
        }

        if (isPermanentSidebar) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        modifier = Modifier.width(304.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    ) {
                        drawerBody()
                    }
                },
            ) {
                content(false) {}
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                // Disable edge swipe inside BrowserWorkspace so desktop WebView horizontal panning works cleanly
                gesturesEnabled = currentRoute != WorkspaceRoute.BROWSER || drawerState.isOpen,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(312.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    ) {
                        drawerBody()
                    }
                },
            ) {
                content(true) {
                    scope.launch { drawerState.open() }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceSidebarContent(
    currentRoute: String,
    folders: List<FolderEntity>,
    allProfiles: List<ProfileWithCookie>,
    selectedFolderFilter: Long,
    openTabs: List<OpenTabInfo>,
    activeTabProfileId: Long?,
    onNavigateRoute: (String) -> Unit,
    onSelectFolder: (Long) -> Unit,
    onManageFolders: () -> Unit,
    onSwitchSession: (Long) -> Unit,
    onCloseSession: (Long) -> Unit,
    onCloseAllSessions: () -> Unit,
) {
    val countsByFolder = remember(allProfiles) {
        allProfiles.groupingBy { it.profile.folderId }.eachCount()
    }
    val ungroupedCount = countsByFolder[null] ?: 0

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(vertical = 12.dp),
    ) {
        // Brand & Engine Status Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SessionDeck",
                    style = MaterialTheme.typography.titleLarge,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.DesktopWindows,
                        contentDescription = null,
                        tint = EmeraldAccent,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Win Chrome • ${allProfiles.size} profiles",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Section 1: Workspace Views
            item {
                SidebarSectionHeader(title = "WORKSPACE")
            }
            item {
                NavigationDrawerItem(
                    label = { Text("Profiles Hub") },
                    selected = currentRoute == WorkspaceRoute.PROFILES && selectedFolderFilter == FolderFilter.ALL,
                    onClick = {
                        onSelectFolder(FolderFilter.ALL)
                        onNavigateRoute(WorkspaceRoute.PROFILES)
                    },
                    icon = { Icon(Icons.Filled.AccountTree, contentDescription = null) },
                    badge = {
                        Text(
                            text = "${allProfiles.size}",
                            style = MonoCodeStyle,
                        )
                    },
                    modifier = Modifier
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .testTag("drawer_nav_profiles"),
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text("Browser Workspace") },
                    selected = currentRoute == WorkspaceRoute.BROWSER,
                    onClick = { onNavigateRoute(WorkspaceRoute.BROWSER) },
                    icon = { Icon(Icons.Filled.Tab, contentDescription = null) },
                    badge = {
                        if (openTabs.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(EmeraldAccent.copy(alpha = 0.18f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "${openTabs.size}",
                                    style = MonoCodeStyle,
                                    color = EmeraldAccent,
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .testTag("drawer_nav_browser"),
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text("Settings & Backup") },
                    selected = currentRoute == WorkspaceRoute.SETTINGS,
                    onClick = { onNavigateRoute(WorkspaceRoute.SETTINGS) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    modifier = Modifier
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .testTag("drawer_nav_settings"),
                )
            }

            // Section 2: Active Session Switcher
            item {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "ACTIVE SESSIONS (${openTabs.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (openTabs.size > 1) {
                        TextButton(
                            onClick = onCloseAllSessions,
                            modifier = Modifier.testTag("drawer_close_all_sessions"),
                        ) {
                            Text(
                                text = "Close All",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            if (openTabs.isEmpty()) {
                item {
                    Text(
                        text = "No sessions open. Tap any profile to launch an isolated tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(openTabs, key = { "session_${it.profileId}" }) { tab ->
                    val isFocused = currentRoute == WorkspaceRoute.BROWSER &&
                        tab.profileId == activeTabProfileId
                    val color = tab.folderColorIndex?.let {
                        FolderColors[it.mod(FolderColors.size)]
                    } ?: MaterialTheme.colorScheme.primary

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (isFocused) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .border(
                                width = if (isFocused) 1.5.dp else 1.dp,
                                color = if (isFocused) color else MaterialTheme.colorScheme.outlineVariant,
                                shape = MaterialTheme.shapes.medium,
                            )
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onSwitchSession(tab.profileId) }
                            .testTag("drawer_session_item_${tab.profileId}"),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (isFocused) EmeraldAccent else color),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tab.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = tab.fbUserId.takeIf { it.isNotBlank() }?.let { "ID: $it" }
                                        ?: tab.pageTitle,
                                    style = MonoCodeStyle,
                                    color = if (isFocused) EmeraldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = { onCloseSession(tab.profileId) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close session ${tab.displayName}",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Section 3: Profile Folders Manager
            item {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "PROFILE FOLDERS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(
                        onClick = onManageFolders,
                        modifier = Modifier.testTag("drawer_manage_folders_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CreateNewFolder,
                            contentDescription = "Create or edit folders",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            item {
                NavigationDrawerItem(
                    label = { Text("All Profiles") },
                    selected = currentRoute == WorkspaceRoute.PROFILES && selectedFolderFilter == FolderFilter.ALL,
                    onClick = { onSelectFolder(FolderFilter.ALL) },
                    icon = { Icon(Icons.Filled.Layers, contentDescription = null) },
                    badge = { Text("${allProfiles.size}", style = MonoCodeStyle) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }

            item {
                NavigationDrawerItem(
                    label = { Text("Ungrouped") },
                    selected = currentRoute == WorkspaceRoute.PROFILES && selectedFolderFilter == FolderFilter.UNGROUPED,
                    onClick = { onSelectFolder(FolderFilter.UNGROUPED) },
                    icon = { Icon(Icons.Filled.FolderOff, contentDescription = null) },
                    badge = { Text("$ungroupedCount", style = MonoCodeStyle) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }

            items(folders, key = { "folder_${it.id}" }) { folder ->
                val color = FolderColors[folder.colorIndex.mod(FolderColors.size)]
                val count = countsByFolder[folder.id] ?: 0
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = folder.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    selected = currentRoute == WorkspaceRoute.PROFILES && selectedFolderFilter == folder.id,
                    onClick = { onSelectFolder(folder.id) },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = null,
                            tint = color,
                        )
                    },
                    badge = { Text("$count", style = MonoCodeStyle) },
                    modifier = Modifier
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .testTag("drawer_folder_item_${folder.id}"),
                )
            }

            item {
                TextButton(
                    onClick = onManageFolders,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Manage / Add Folder")
                }
            }
        }
    }
}

@Composable
private fun SidebarSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
    )
}
