package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.ProfileWithCookie
import com.example.ui.components.BiometricLockOverlay
import com.example.ui.components.BulkCreateProfilesDialog
import com.example.ui.components.CookieImportExportDialog
import com.example.ui.components.CreateOrEditProfileDialog
import com.example.ui.components.FolderManagerDialog
import com.example.ui.components.MoveProfilesToFolderDialog
import com.example.ui.components.ProfileVaultAndHistoryDialog
import com.example.ui.navigation.WorkspaceNavigationScaffold
import com.example.ui.navigation.WorkspaceRoute
import com.example.ui.screens.BrowserWorkspaceScreen
import com.example.ui.screens.ProfilesHubScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.state.AppDestination
import com.example.ui.state.FolderFilter
import com.example.ui.state.MainViewModel
import com.example.ui.theme.SessionDeckTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SessionDeckApp()
        }
    }
}

@Composable
fun SessionDeckApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: WorkspaceRoute.PROFILES

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val allProfiles by viewModel.allProfiles.collectAsStateWithLifecycle()
    val filteredProfiles by viewModel.filteredProfiles.collectAsStateWithLifecycle()
    val selectedFolderFilter by viewModel.selectedFolderFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val selectedProfileIds by viewModel.selectedProfileIds.collectAsStateWithLifecycle()
    val openTabs by viewModel.openTabs.collectAsStateWithLifecycle()
    val activeTabProfileId by viewModel.activeTabProfileId.collectAsStateWithLifecycle()
    val isTabGridOpen by viewModel.isTabGridOpen.collectAsStateWithLifecycle()
    val inspectedHistory by viewModel.inspectedProfileHistory.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    // Dialog states
    var showCreateEditProfile by remember { mutableStateOf<Pair<Boolean, ProfileWithCookie?>>(false to null) }
    var showBulkCreate by remember { mutableStateOf(false) }
    var showFolderManager by remember { mutableStateOf(false) }
    var showMoveSelectedDialog by remember { mutableStateOf(false) }
    var cookieDialogProfileId by remember { mutableStateOf<Long?>(null) }
    var vaultDialogProfileId by remember { mutableStateOf<Long?>(null) }

    val navigateToRoute: (String) -> Unit = { route ->
        if (route == WorkspaceRoute.BROWSER && openTabs.isEmpty()) {
            viewModel.showMessage("Open a profile card to launch a browser tab")
        } else {
            val vmDest = when (route) {
                WorkspaceRoute.BROWSER -> AppDestination.BROWSER
                WorkspaceRoute.SETTINGS -> AppDestination.SETTINGS
                else -> AppDestination.HUB
            }
            viewModel.navigateTo(vmDest)
            if (currentRoute != route) {
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    // Sync ViewModel-initiated destination transitions (e.g., opening a profile card or closing last tab)
    LaunchedEffect(destination) {
        val targetRoute = when (destination) {
            AppDestination.HUB -> WorkspaceRoute.PROFILES
            AppDestination.BROWSER -> WorkspaceRoute.BROWSER
            AppDestination.SETTINGS -> WorkspaceRoute.SETTINGS
        }
        if (currentRoute != targetRoute) {
            navController.navigate(targetRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Keep ViewModel destination aligned with system back stack pops
    LaunchedEffect(currentRoute) {
        val mapped = when (currentRoute) {
            WorkspaceRoute.BROWSER -> AppDestination.BROWSER
            WorkspaceRoute.SETTINGS -> AppDestination.SETTINGS
            else -> AppDestination.HUB
        }
        if (destination != mapped) {
            viewModel.navigateTo(mapped)
        }
    }

    // SAF Backup Export & Import launchers
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = viewModel.generateBackupJson()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                    true
                }.getOrDefault(false)
            }
            viewModel.showMessage(
                if (ok) "Exported SessionDeck backup JSON" else "Failed to write backup file"
            )
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (json.isNullOrBlank()) {
                viewModel.showMessage("Could not read selected backup file")
            } else {
                viewModel.restoreFromBackupJson(json)
            }
        }
    }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessage()
    }

    SessionDeckTheme(themeMode = settings.themeMode) {
        if (isLocked) {
            BiometricLockOverlay(onUnlocked = viewModel::unlockApp)
        } else {
            val activeTab = remember(openTabs, activeTabProfileId) {
                openTabs.firstOrNull { it.profileId == activeTabProfileId } ?: openTabs.lastOrNull()
            }
            val activeProfile = remember(allProfiles, activeTab) {
                activeTab?.let { tab -> allProfiles.firstOrNull { it.profile.id == tab.profileId } }
            }

            WorkspaceNavigationScaffold(
                currentRoute = currentRoute,
                folders = folders,
                allProfiles = allProfiles,
                selectedFolderFilter = selectedFolderFilter,
                openTabs = openTabs,
                activeTabProfileId = activeTabProfileId,
                onNavigateRoute = navigateToRoute,
                onSelectFolder = viewModel::setFolderFilter,
                onManageFolders = { showFolderManager = true },
                onSwitchSession = viewModel::switchToTab,
                onCloseSession = viewModel::closeTab,
                onCloseAllSessions = viewModel::closeAllTabs,
            ) { showDrawerButton, onOpenDrawer ->
                val showCompactBottomBar = showDrawerButton && currentRoute != WorkspaceRoute.BROWSER

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets.navigationBars,
                    bottomBar = {
                        if (showCompactBottomBar) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentRoute == WorkspaceRoute.PROFILES,
                                    onClick = { navigateToRoute(WorkspaceRoute.PROFILES) },
                                    icon = { Icon(Icons.Filled.AccountTree, contentDescription = null) },
                                    label = { Text("Profiles") },
                                    modifier = Modifier.testTag("nav_profiles"),
                                )
                                NavigationBarItem(
                                    selected = false,
                                    onClick = { navigateToRoute(WorkspaceRoute.BROWSER) },
                                    icon = {
                                        BadgedBox(
                                            badge = {
                                                if (openTabs.isNotEmpty()) {
                                                    Badge { Text("${openTabs.size}") }
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Filled.Tab, contentDescription = null)
                                        }
                                    },
                                    label = { Text("Tabs") },
                                    modifier = Modifier.testTag("nav_tabs"),
                                )
                                NavigationBarItem(
                                    selected = currentRoute == WorkspaceRoute.SETTINGS,
                                    onClick = { navigateToRoute(WorkspaceRoute.SETTINGS) },
                                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                    label = { Text("Settings") },
                                    modifier = Modifier.testTag("nav_settings"),
                                )
                            }
                        }
                    },
                ) { outerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(outerPadding),
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = WorkspaceRoute.PROFILES,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            composable(WorkspaceRoute.PROFILES) {
                                ProfilesHubScreen(
                                    profiles = filteredProfiles,
                                    allProfilesCount = allProfiles.size,
                                    folders = folders,
                                    selectedFolderFilter = selectedFolderFilter,
                                    searchQuery = searchQuery,
                                    sortMode = sortMode,
                                    selectedProfileIds = selectedProfileIds,
                                    openTabs = openTabs,
                                    showDrawerButton = showDrawerButton,
                                    onOpenDrawer = onOpenDrawer,
                                    onSearchQueryChange = viewModel::setSearchQuery,
                                    onSelectFolderFilter = viewModel::setFolderFilter,
                                    onSortModeChange = viewModel::setSortMode,
                                    onOpenProfile = { viewModel.openProfileInTab(it) },
                                    onOpenCookieImport = { cookieDialogProfileId = it.profile.id },
                                    onOpenVaultAndHistory = {
                                        viewModel.setInspectedHistoryProfile(it.profile.id)
                                        vaultDialogProfileId = it.profile.id
                                    },
                                    onToggleSelectProfile = viewModel::toggleProfileSelection,
                                    onSelectAll = viewModel::selectAllFiltered,
                                    onClearSelection = viewModel::clearSelection,
                                    onOpenSelectedTabs = viewModel::openSelectedProfiles,
                                    onMoveSelectedToFolder = { showMoveSelectedDialog = true },
                                    onDeleteSelected = {
                                        viewModel.deleteProfiles(selectedProfileIds.toList())
                                    },
                                    onCreateNewProfile = { showCreateEditProfile = true to null },
                                    onOpenBulkCreate = { showBulkCreate = true },
                                    onOpenFolderManager = { showFolderManager = true },
                                    onOpenActiveBrowser = { navigateToRoute(WorkspaceRoute.BROWSER) },
                                )
                            }

                            composable(WorkspaceRoute.BROWSER) {
                                BrowserWorkspaceScreen(
                                    openTabs = openTabs,
                                    activeTab = activeTab,
                                    activeProfile = activeProfile,
                                    isTabGridOpen = isTabGridOpen,
                                    showDrawerButton = showDrawerButton,
                                    onOpenDrawer = onOpenDrawer,
                                    obtainWebView = viewModel::obtainWebViewForTab,
                                    onSelectTab = viewModel::switchToTab,
                                    onCloseTab = viewModel::closeTab,
                                    onCloseAllTabs = viewModel::closeAllTabs,
                                    onToggleTabGrid = viewModel::setTabGridOpen,
                                    onNavigateUrl = viewModel::navigateActiveTab,
                                    onGoBack = viewModel::goBackActiveTab,
                                    onGoForward = viewModel::goForwardActiveTab,
                                    onReload = viewModel::reloadActiveTab,
                                    onOpenCookieImport = { cookieDialogProfileId = it.profile.id },
                                    onOpenVaultAndHistory = {
                                        viewModel.setInspectedHistoryProfile(it.profile.id)
                                        vaultDialogProfileId = it.profile.id
                                    },
                                    onAutoFillLogin = viewModel::autoFillCredentials,
                                    onGoToProfilesHub = { navigateToRoute(WorkspaceRoute.PROFILES) },
                                )
                            }

                            composable(WorkspaceRoute.SETTINGS) {
                                SettingsScreen(
                                    settings = settings,
                                    isMultiProfileSupported = viewModel.isMultiProfileSupported,
                                    showDrawerButton = showDrawerButton,
                                    onOpenDrawer = onOpenDrawer,
                                    onThemeModeChange = viewModel::setThemeMode,
                                    onBiometricToggle = viewModel::setBiometricLockEnabled,
                                    onLockNow = viewModel::lockAppIfEnabled,
                                    onUserAgentSave = viewModel::setDefaultUserAgent,
                                    onDesktopWidthChange = viewModel::setDesktopViewportWidth,
                                    onEnforceDesktopFbChange = viewModel::setEnforceDesktopFacebookUrl,
                                    onExportBackup = {
                                        exportBackupLauncher.launch("sessiondeck_backup.json")
                                    },
                                    onImportBackup = {
                                        importBackupLauncher.launch(arrayOf("application/json", "text/plain"))
                                    },
                                    onBackToHub = { navigateToRoute(WorkspaceRoute.PROFILES) },
                                )
                            }
                        }
                    }
                }
            }

            // Dialogs & Sheets
            val defaultFolderForCreate = when (selectedFolderFilter) {
                FolderFilter.ALL, FolderFilter.UNGROUPED -> null
                else -> selectedFolderFilter
            }

            if (showCreateEditProfile.first) {
                CreateOrEditProfileDialog(
                    existing = showCreateEditProfile.second,
                    folders = folders,
                    defaultFolderId = defaultFolderForCreate,
                    onDismiss = { showCreateEditProfile = false to null },
                    onCreate = viewModel::createProfile,
                    onUpdate = viewModel::updateProfile,
                )
            }

            if (showBulkCreate) {
                BulkCreateProfilesDialog(
                    folders = folders,
                    defaultFolderId = defaultFolderForCreate,
                    onDismiss = { showBulkCreate = false },
                    onBulkNumbered = viewModel::bulkCreateNumbered,
                    onBulkPaste = viewModel::bulkImportLines,
                )
            }

            if (showFolderManager) {
                FolderManagerDialog(
                    folders = folders,
                    onDismiss = { showFolderManager = false },
                    onCreateFolder = viewModel::createFolder,
                    onUpdateFolder = viewModel::updateFolder,
                    onDeleteFolder = viewModel::deleteFolder,
                )
            }

            if (showMoveSelectedDialog && selectedProfileIds.isNotEmpty()) {
                MoveProfilesToFolderDialog(
                    count = selectedProfileIds.size,
                    folders = folders,
                    onDismiss = { showMoveSelectedDialog = false },
                    onMove = { targetFolderId ->
                        viewModel.moveProfilesToFolder(selectedProfileIds.toList(), targetFolderId)
                    },
                )
            }

            val cookieTarget = cookieDialogProfileId?.let { id ->
                allProfiles.firstOrNull { it.profile.id == id }
            }
            if (cookieTarget != null) {
                CookieImportExportDialog(
                    profile = cookieTarget,
                    onDismiss = { cookieDialogProfileId = null },
                    onImportAndApply = { rawInput, openAfter ->
                        viewModel.importCookiesForProfile(cookieTarget.profile.id, rawInput, openAfter)
                    },
                    onLoadLiveCookies = { viewModel.exportCookiesForProfile(cookieTarget.profile.id) },
                    onCopyFeedback = viewModel::showMessage,
                )
            }

            val vaultTarget = vaultDialogProfileId?.let { id ->
                allProfiles.firstOrNull { it.profile.id == id }
            }
            if (vaultTarget != null) {
                ProfileVaultAndHistoryDialog(
                    profile = vaultTarget,
                    history = inspectedHistory,
                    isActiveTabOpen = openTabs.any { it.profileId == vaultTarget.profile.id },
                    onDismiss = {
                        vaultDialogProfileId = null
                        viewModel.setInspectedHistoryProfile(null)
                    },
                    onAutoFillLogin = { viewModel.autoFillCredentials(vaultTarget.profile.id) },
                    onOpenUrl = { url -> viewModel.navigateProfileToUrl(vaultTarget.profile.id, url) },
                    onClearHistory = { viewModel.clearProfileHistory(vaultTarget.profile.id) },
                    onOpenCookieImport = { cookieDialogProfileId = vaultTarget.profile.id },
                    onEditProfile = { showCreateEditProfile = true to vaultTarget },
                    onCopyFeedback = viewModel::showMessage,
                )
            }
        }
    }
}
