package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.FolderEntity
import com.example.data.local.ProfileWithCookie
import com.example.ui.state.FolderFilter
import com.example.ui.state.OpenTabInfo
import com.example.ui.state.ProfileSortMode
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.FolderColors
import com.example.ui.theme.MonoCodeStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesHubScreen(
    profiles: List<ProfileWithCookie>,
    allProfilesCount: Int,
    folders: List<FolderEntity>,
    selectedFolderFilter: Long,
    searchQuery: String,
    sortMode: ProfileSortMode,
    selectedProfileIds: Set<Long>,
    openTabs: List<OpenTabInfo>,
    showDrawerButton: Boolean,
    onOpenDrawer: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectFolderFilter: (Long) -> Unit,
    onSortModeChange: (ProfileSortMode) -> Unit,
    onOpenProfile: (ProfileWithCookie) -> Unit,
    onOpenCookieImport: (ProfileWithCookie) -> Unit,
    onOpenVaultAndHistory: (ProfileWithCookie) -> Unit,
    onToggleSelectProfile: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onOpenSelectedTabs: () -> Unit,
    onMoveSelectedToFolder: () -> Unit,
    onDeleteSelected: () -> Unit,
    onCreateNewProfile: () -> Unit,
    onOpenBulkCreate: () -> Unit,
    onOpenFolderManager: () -> Unit,
    onOpenActiveBrowser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val inSelectionMode = selectedProfileIds.isNotEmpty()
    val folderMap = remember(folders) { folders.associateBy { it.id } }
    val openTabIds = remember(openTabs) { openTabs.map { it.profileId }.toSet() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (inSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedProfileIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = onSelectAll) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
                        }
                        IconButton(
                            onClick = onOpenSelectedTabs,
                            modifier = Modifier.testTag("batch_open_tabs_button"),
                        ) {
                            Icon(Icons.Filled.OpenInBrowser, contentDescription = "Open selected tabs")
                        }
                        IconButton(onClick = onMoveSelectedToFolder) {
                            Icon(Icons.Filled.DriveFileMove, contentDescription = "Move to folder")
                        }
                        IconButton(onClick = onDeleteSelected) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete selected",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        if (showDrawerButton) {
                            IconButton(
                                onClick = onOpenDrawer,
                                modifier = Modifier.testTag("hub_open_drawer_button"),
                            ) {
                                Icon(Icons.Filled.Menu, contentDescription = "Open workspace sidebar")
                            }
                        }
                    },
                    title = {
                        Column {
                            Text("SessionDeck", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "$allProfilesCount profiles • ${openTabs.size} active tabs",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        if (openTabs.isNotEmpty()) {
                            FilledTonalButton(
                                onClick = onOpenActiveBrowser,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .testTag("resume_tabs_button"),
                            ) {
                                Icon(Icons.Filled.Tab, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${openTabs.size}")
                            }
                        }
                        IconButton(
                            onClick = onOpenBulkCreate,
                            modifier = Modifier.testTag("bulk_create_button"),
                        ) {
                            Icon(Icons.Filled.DynamicFeed, contentDescription = "Bulk create profiles")
                        }
                        IconButton(
                            onClick = onOpenFolderManager,
                            modifier = Modifier.testTag("manage_folders_button"),
                        ) {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = "Manage folders")
                        }
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }) {
                                Icon(Icons.Filled.Sort, contentDescription = "Sort profiles")
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false },
                            ) {
                                ProfileSortMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text("Sort: ${mode.label}") },
                                        onClick = {
                                            onSortModeChange(mode)
                                            sortMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!inSelectionMode) {
                FloatingActionButton(
                    onClick = onCreateNewProfile,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("new_profile_fab"),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "New profile")
                }
            }
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val columns = if (maxWidth >= 680.dp) 2 else 1

            Column(modifier = Modifier.fillMaxSize()) {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search profile name, c_user ID, email, or notes...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("profile_search_input"),
                )

                // Folder Filter Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = selectedFolderFilter == FolderFilter.ALL,
                        onClick = { onSelectFolderFilter(FolderFilter.ALL) },
                        label = { Text("All ($allProfilesCount)") },
                    )
                    FilterChip(
                        selected = selectedFolderFilter == FolderFilter.UNGROUPED,
                        onClick = { onSelectFolderFilter(FolderFilter.UNGROUPED) },
                        label = { Text("Ungrouped") },
                    )
                    folders.forEach { folder ->
                        val color = FolderColors[folder.colorIndex.mod(FolderColors.size)]
                        FilterChip(
                            selected = selectedFolderFilter == folder.id,
                            onClick = { onSelectFolderFilter(folder.id) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(color),
                                )
                            },
                            label = { Text(folder.name) },
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = onOpenFolderManager,
                        leadingIcon = {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Folder") },
                    )
                }

                // Profile Grid / List
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (allProfilesCount == 0) {
                        item(span = { GridItemSpan(columns) }) {
                            EmptyWorkspaceCard(
                                onCreateProfile = onCreateNewProfile,
                                onBulkCreate = onOpenBulkCreate,
                            )
                        }
                    } else if (profiles.isEmpty()) {
                        item(span = { GridItemSpan(columns) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No profiles match your filter or search.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        items(profiles, key = { it.profile.id }) { entry ->
                            val folder = entry.profile.folderId?.let { folderMap[it] }
                            ProfileItemCard(
                                entry = entry,
                                folder = folder,
                                isSelected = entry.profile.id in selectedProfileIds,
                                isTabOpen = entry.profile.id in openTabIds,
                                inSelectionMode = inSelectionMode,
                                onClick = {
                                    if (inSelectionMode) {
                                        onToggleSelectProfile(entry.profile.id)
                                    } else {
                                        onOpenProfile(entry)
                                    }
                                },
                                onLongClick = { onToggleSelectProfile(entry.profile.id) },
                                onImportCookies = { onOpenCookieImport(entry) },
                                onOpenVault = { onOpenVaultAndHistory(entry) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileItemCard(
    entry: ProfileWithCookie,
    folder: FolderEntity?,
    isSelected: Boolean,
    isTabOpen: Boolean,
    inSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onImportCookies: () -> Unit,
    onOpenVault: () -> Unit,
) {
    val profile = entry.profile
    val folderColor = folder?.let { FolderColors[it.colorIndex.mod(FolderColors.size)] }
        ?: MaterialTheme.colorScheme.primary
    val cookieCount = entry.cookieSnapshot?.cookieCount ?: 0

    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected || isTabOpen) 1.5.dp else 1.dp,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isTabOpen -> EmeraldAccent
                    else -> MaterialTheme.colorScheme.outline
                },
                shape = MaterialTheme.shapes.large,
            )
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .testTag("profile_card_${profile.id}"),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(folderColor.copy(alpha = 0.18f))
                        .border(1.5.dp, folderColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(
                            text = profile.displayName.take(2).uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = folderColor,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (profile.isPinned) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.PushPin,
                                contentDescription = "Pinned",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (profile.fbUserId.isNotBlank()) {
                            Text(
                                text = "ID: ${profile.fbUserId}",
                                style = MonoCodeStyle,
                                color = EmeraldAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else if (profile.emailOrPhone.isNotBlank()) {
                            Text(
                                text = profile.emailOrPhone,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Text(
                                text = "Desktop Win Chrome • Isolated",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (isTabOpen) {
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(EmeraldAccent.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "OPEN",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldAccent,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata & Quick Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (folder != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(folderColor.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            Icons.Filled.Folder,
                            contentDescription = null,
                            tint = folderColor,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = folderColor,
                            maxLines = 1,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(
                            if (cookieCount > 0) EmeraldAccent.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (cookieCount > 0) "$cookieCount cookies" else "No cookies",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (cookieCount > 0) EmeraldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                AnimatedVisibility(visible = !inSelectionMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onImportCookies,
                            modifier = Modifier.testTag("card_import_cookies_${profile.id}"),
                        ) {
                            Icon(
                                Icons.Filled.Cookie,
                                contentDescription = "Import cookies",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(
                            onClick = onOpenVault,
                            modifier = Modifier.testTag("card_vault_${profile.id}"),
                        ) {
                            Icon(
                                Icons.Filled.Key,
                                contentDescription = "Vault & history",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledTonalButton(
                            onClick = onClick,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("card_open_tab_${profile.id}"),
                        ) {
                            Text(if (isTabOpen) "Focus" else "Open")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyWorkspaceCard(
    onCreateProfile: () -> Unit,
    onBulkCreate: () -> Unit,
) {
    ElevatedCard(
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraLarge),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_workspace_hero_1795075158937),
                    contentDescription = "SessionDeck isolated browser folders illustration",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                )
                            )
                        ),
                )
            }
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Isolate 100+ Facebook Sessions",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Every profile runs in an isolated Windows Chrome desktop container with its own cookies, passwords, and browsing history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onCreateProfile,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("empty_create_profile_button"),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Profile")
                    }
                    OutlinedButton(
                        onClick = onBulkCreate,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("empty_bulk_create_button"),
                    ) {
                        Icon(Icons.Filled.DynamicFeed, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bulk Create")
                    }
                }
            }
        }
    }
}
