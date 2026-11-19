package com.example.ui.state

import android.app.Application
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.browser.BrowserTabRuntimeState
import com.example.browser.WebViewPoolManager
import com.example.data.local.AppDatabase
import com.example.data.local.FolderEntity
import com.example.data.local.HistoryEntity
import com.example.data.local.ProfileEntity
import com.example.data.local.ProfileWithCookie
import com.example.data.preferences.AppSettings
import com.example.data.preferences.SettingsRepository
import com.example.data.preferences.ThemeMode
import com.example.data.repository.WorkspaceRepository
import com.example.domain.CookieParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppDestination {
    HUB,
    BROWSER,
    SETTINGS,
}

enum class ProfileSortMode(val label: String) {
    RECENT("Recent"),
    NAME("A–Z"),
    PINNED("Pinned"),
}

object FolderFilter {
    const val ALL = -1L
    const val UNGROUPED = -2L
}

data class OpenTabInfo(
    val profileId: Long,
    val profileKey: String,
    val displayName: String,
    val folderColorIndex: Int?,
    val fbUserId: String,
    val currentUrl: String,
    val pageTitle: String,
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = WorkspaceRepository(db)
    private val settingsRepository = SettingsRepository(application)

    private val _destination = MutableStateFlow(AppDestination.HUB)
    val destination: StateFlow<AppDestination> = _destination.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFolderFilter = MutableStateFlow(FolderFilter.ALL)
    val selectedFolderFilter: StateFlow<Long> = _selectedFolderFilter.asStateFlow()

    private val _sortMode = MutableStateFlow(ProfileSortMode.RECENT)
    val sortMode: StateFlow<ProfileSortMode> = _sortMode.asStateFlow()

    private val _selectedProfileIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedProfileIds: StateFlow<Set<Long>> = _selectedProfileIds.asStateFlow()

    private val _openTabs = MutableStateFlow<List<OpenTabInfo>>(emptyList())
    val openTabs: StateFlow<List<OpenTabInfo>> = _openTabs.asStateFlow()

    private val _activeTabProfileId = MutableStateFlow<Long?>(null)
    val activeTabProfileId: StateFlow<Long?> = _activeTabProfileId.asStateFlow()

    private val _isTabGridOpen = MutableStateFlow(false)
    val isTabGridOpen: StateFlow<Boolean> = _isTabGridOpen.asStateFlow()

    private val _inspectedHistoryProfileId = MutableStateFlow<Long?>(null)

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val folders: StateFlow<List<FolderEntity>> = repository.foldersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allProfiles: StateFlow<List<ProfileWithCookie>> = repository.profilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredProfiles: StateFlow<List<ProfileWithCookie>> = combine(
        allProfiles,
        _selectedFolderFilter,
        _searchQuery,
        _sortMode,
    ) { items, folderFilter, query, sort ->
        val q = query.trim().lowercase()
        val filtered = items.filter { entry ->
            val p = entry.profile
            val matchesFolder = when (folderFilter) {
                FolderFilter.ALL -> true
                FolderFilter.UNGROUPED -> p.folderId == null
                else -> p.folderId == folderFilter
            }
            val matchesQuery = if (q.isEmpty()) true else {
                p.displayName.lowercase().contains(q) ||
                    p.fbUserId.lowercase().contains(q) ||
                    p.emailOrPhone.lowercase().contains(q) ||
                    p.notes.lowercase().contains(q)
            }
            matchesFolder && matchesQuery
        }
        when (sort) {
            ProfileSortMode.RECENT -> filtered.sortedWith(
                compareByDescending<ProfileWithCookie> { it.profile.isPinned }
                    .thenByDescending { it.profile.lastOpenedAt }
                    .thenByDescending { it.profile.createdAt }
            )
            ProfileSortMode.NAME -> filtered.sortedWith(
                compareByDescending<ProfileWithCookie> { it.profile.isPinned }
                    .thenBy { it.profile.displayName.lowercase() }
            )
            ProfileSortMode.PINNED -> filtered.sortedWith(
                compareByDescending<ProfileWithCookie> { it.profile.isPinned }
                    .thenByDescending { it.profile.createdAt }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val inspectedProfileHistory: StateFlow<List<HistoryEntity>> = _inspectedHistoryProfileId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.observeHistoryForProfile(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val webViewPool = WebViewPoolManager(
        context = application.applicationContext,
        onTabStateChanged = { runtime -> handleTabRuntimeUpdate(runtime) },
        onPageCommitted = { profileId, url, title ->
            viewModelScope.launch {
                repository.recordNavigation(profileId, url, title)
            }
        },
        onCookiesObserved = { profileId, _, rawCookies ->
            viewModelScope.launch {
                val parsed = CookieParser.parse(rawCookies)
                if (parsed.cookies.isNotEmpty()) {
                    repository.saveCookieSnapshot(
                        profileId = profileId,
                        normalizedCookies = parsed.normalizedCookieString,
                        cookieCount = parsed.cookies.size,
                        fbUserId = parsed.fbUserId,
                    )
                }
            }
        },
    )

    val isMultiProfileSupported: Boolean get() = webViewPool.isMultiProfileSupported

    init {
        viewModelScope.launch {
            val initialSettings = settingsRepository.settingsFlow.first()
            if (initialSettings.biometricLockEnabled) {
                _isLocked.value = true
            }
        }
    }

    fun unlockApp() {
        _isLocked.value = false
    }

    fun lockAppIfEnabled() {
        if (settings.value.biometricLockEnabled) {
            _isLocked.value = true
        }
    }

    fun navigateTo(dest: AppDestination) {
        _destination.value = dest
        if (dest != AppDestination.BROWSER) {
            _isTabGridOpen.value = false
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFolderFilter(filterId: Long) {
        _selectedFolderFilter.value = filterId
    }

    fun setSortMode(mode: ProfileSortMode) {
        _sortMode.value = mode
    }

    fun toggleProfileSelection(profileId: Long) {
        _selectedProfileIds.update { current ->
            if (profileId in current) current - profileId else current + profileId
        }
    }

    fun selectAllFiltered() {
        _selectedProfileIds.value = filteredProfiles.value.map { it.profile.id }.toSet()
    }

    fun clearSelection() {
        _selectedProfileIds.value = emptySet()
    }

    fun openProfileInTab(profileWithCookie: ProfileWithCookie, switchImmediately: Boolean = true) {
        val profile = profileWithCookie.profile
        val folderColor = folders.value.firstOrNull { it.id == profile.folderId }?.colorIndex
        viewModelScope.launch {
            repository.touchLastOpened(profile.id)
            val snapshot = profileWithCookie.cookieSnapshot?.rawCookieString.orEmpty()
            if (snapshot.isNotBlank() && webViewPool.isMultiProfileSupported) {
                webViewPool.importCookiesIntoProfile(
                    profileKey = profile.profileKey,
                    rawCookieInput = snapshot,
                    reloadIfOpen = false,
                )
            }
        }

        _openTabs.update { tabs ->
            if (tabs.any { it.profileId == profile.id }) {
                tabs
            } else {
                tabs + OpenTabInfo(
                    profileId = profile.id,
                    profileKey = profile.profileKey,
                    displayName = profile.displayName,
                    folderColorIndex = folderColor,
                    fbUserId = profile.fbUserId,
                    currentUrl = profile.lastUrl.ifBlank { "https://www.facebook.com/" },
                    pageTitle = profile.lastTitle.ifBlank { profile.displayName },
                )
            }
        }
        if (switchImmediately) {
            _activeTabProfileId.value = profile.id
            _isTabGridOpen.value = false
            _destination.value = AppDestination.BROWSER
        }
    }

    fun openSelectedProfiles() {
        val selected = _selectedProfileIds.value
        if (selected.isEmpty()) return
        val targets = allProfiles.value.filter { it.profile.id in selected }
        targets.forEachIndexed { index, item ->
            openProfileInTab(item, switchImmediately = index == 0)
        }
        clearSelection()
        if (targets.size > 1) {
            showMessage("Opened ${targets.size} isolated browser tabs")
        }
    }

    fun switchToTab(profileId: Long) {
        _activeTabProfileId.value = profileId
        _isTabGridOpen.value = false
        _destination.value = AppDestination.BROWSER
    }

    fun setTabGridOpen(open: Boolean) {
        _isTabGridOpen.value = open
    }

    fun closeTab(profileId: Long) {
        val tab = _openTabs.value.firstOrNull { it.profileId == profileId } ?: return
        webViewPool.closeTab(tab.profileKey)
        val remaining = _openTabs.value.filterNot { it.profileId == profileId }
        _openTabs.value = remaining
        if (_activeTabProfileId.value == profileId) {
            _activeTabProfileId.value = remaining.lastOrNull()?.profileId
        }
        if (remaining.isEmpty()) {
            _isTabGridOpen.value = false
            _destination.value = AppDestination.HUB
        }
    }

    fun closeAllTabs() {
        _openTabs.value.forEach { webViewPool.closeTab(it.profileKey) }
        _openTabs.value = emptyList()
        _activeTabProfileId.value = null
        _isTabGridOpen.value = false
        _destination.value = AppDestination.HUB
    }

    fun obtainWebViewForTab(tab: OpenTabInfo): WebView {
        val entry = allProfiles.value.firstOrNull { it.profile.id == tab.profileId }
        return webViewPool.getOrCreateWebView(
            profileId = tab.profileId,
            profileKey = tab.profileKey,
            initialUrl = tab.currentUrl,
            customUserAgent = entry?.profile?.customUserAgent,
            settings = settings.value,
            fallbackCookies = entry?.cookieSnapshot?.rawCookieString,
        )
    }

    fun navigateActiveTab(urlOrQuery: String) {
        val active = currentActiveTab() ?: return
        webViewPool.navigate(
            profileKey = active.profileKey,
            rawInput = urlOrQuery,
            enforceDesktopFb = settings.value.enforceDesktopFacebookUrl,
        )
    }

    fun navigateProfileToUrl(profileId: Long, url: String) {
        val entry = allProfiles.value.firstOrNull { it.profile.id == profileId } ?: return
        openProfileInTab(entry, switchImmediately = true)
        webViewPool.navigate(
            profileKey = entry.profile.profileKey,
            rawInput = url,
            enforceDesktopFb = settings.value.enforceDesktopFacebookUrl,
        )
    }

    fun goBackActiveTab() {
        currentActiveTab()?.let { webViewPool.goBack(it.profileKey) }
    }

    fun goForwardActiveTab() {
        currentActiveTab()?.let { webViewPool.goForward(it.profileKey) }
    }

    fun reloadActiveTab() {
        currentActiveTab()?.let { webViewPool.reload(it.profileKey) }
    }

    fun autoFillCredentials(profileId: Long) {
        val profile = allProfiles.value.firstOrNull { it.profile.id == profileId }?.profile ?: return
        if (profile.emailOrPhone.isBlank() && profile.savedPassword.isBlank()) {
            showMessage("No saved email or password for this profile")
            return
        }
        webViewPool.autoFillCredentials(
            profileKey = profile.profileKey,
            email = profile.emailOrPhone,
            password = profile.savedPassword,
        )
        showMessage("Filled saved login credentials")
    }

    fun importCookiesForProfile(profileId: Long, rawInput: String, openAfterImport: Boolean = false) {
        val entry = allProfiles.value.firstOrNull { it.profile.id == profileId } ?: return
        viewModelScope.launch {
            val result = webViewPool.importCookiesIntoProfile(
                profileKey = entry.profile.profileKey,
                rawCookieInput = rawInput,
                reloadIfOpen = true,
            )
            if (result.cookies.isEmpty()) {
                showMessage("No valid cookies detected. Paste key=value; pairs or JSON.")
                return@launch
            }
            repository.saveCookieSnapshot(
                profileId = profileId,
                normalizedCookies = result.normalizedCookieString,
                cookieCount = result.cookies.size,
                fbUserId = result.fbUserId,
            )
            val idNote = result.fbUserId?.let { " • ID $it" }.orEmpty()
            showMessage("Imported ${result.cookies.size} cookies (${result.sourceFormat})$idNote")
            if (openAfterImport) {
                val refreshed = allProfiles.value.firstOrNull { it.profile.id == profileId } ?: entry
                openProfileInTab(refreshed, switchImmediately = true)
            }
        }
    }

    suspend fun exportCookiesForProfile(profileId: Long): String {
        val entry = allProfiles.value.firstOrNull { it.profile.id == profileId } ?: return ""
        return webViewPool.exportLiveCookies(
            profileKey = entry.profile.profileKey,
            fallbackSnapshot = entry.cookieSnapshot?.rawCookieString.orEmpty(),
        )
    }

    fun createProfile(
        displayName: String,
        folderId: Long?,
        emailOrPhone: String,
        savedPassword: String,
        notes: String,
        fbUserId: String,
        initialUrl: String,
        rawCookies: String,
        isPinned: Boolean,
    ) {
        viewModelScope.launch {
            val (created, normalizedCookies) = repository.createProfile(
                displayName = displayName,
                folderId = folderId,
                emailOrPhone = emailOrPhone,
                savedPassword = savedPassword,
                notes = notes,
                fbUserId = fbUserId,
                initialUrl = initialUrl,
                rawCookies = rawCookies,
                isPinned = isPinned,
            )
            if (normalizedCookies.isNotBlank()) {
                webViewPool.importCookiesIntoProfile(
                    profileKey = created.profileKey,
                    rawCookieInput = normalizedCookies,
                    reloadIfOpen = false,
                )
            }
            showMessage("Created profile \"${created.displayName}\"")
        }
    }

    fun updateProfile(profile: ProfileEntity) {
        viewModelScope.launch {
            repository.updateProfile(profile)
            val folderColor = folders.value.firstOrNull { it.id == profile.folderId }?.colorIndex
            _openTabs.update { tabs ->
                tabs.map { tab ->
                    if (tab.profileId == profile.id) {
                        tab.copy(
                            displayName = profile.displayName,
                            folderColorIndex = folderColor,
                            fbUserId = profile.fbUserId,
                        )
                    } else tab
                }
            }
            showMessage("Saved changes to \"${profile.displayName}\"")
        }
    }

    fun bulkCreateNumbered(
        prefix: String,
        startNumber: Int,
        count: Int,
        folderId: Long?,
        defaultUrl: String,
    ) {
        viewModelScope.launch {
            val created = repository.bulkCreateNumbered(
                prefix = prefix,
                startNumber = startNumber,
                count = count,
                folderId = folderId,
                defaultUrl = defaultUrl,
            )
            showMessage("Bulk created $created isolated profiles")
        }
    }

    fun bulkImportLines(rawLines: String, prefix: String, folderId: Long?) {
        viewModelScope.launch {
            val result = repository.bulkImportFromLines(
                rawLines = rawLines,
                defaultPrefix = prefix,
                folderId = folderId,
            )
            result.importedProfileCookies.forEach { (profile, cookieStr) ->
                webViewPool.importCookiesIntoProfile(
                    profileKey = profile.profileKey,
                    rawCookieInput = cookieStr,
                    reloadIfOpen = false,
                )
            }
            showMessage(
                "Created ${result.createdCount} profiles (${result.cookiesImportedCount} with cookies)"
            )
        }
    }

    fun moveProfilesToFolder(profileIds: List<Long>, folderId: Long?) {
        viewModelScope.launch {
            repository.moveProfilesToFolder(profileIds, folderId)
            val folderColor = folders.value.firstOrNull { it.id == folderId }?.colorIndex
            _openTabs.update { tabs ->
                tabs.map { t ->
                    if (t.profileId in profileIds) t.copy(folderColorIndex = folderColor) else t
                }
            }
            clearSelection()
            showMessage("Moved ${profileIds.size} profile(s)")
        }
    }

    fun deleteProfiles(profileIds: List<Long>) {
        viewModelScope.launch {
            profileIds.forEach { id ->
                if (_openTabs.value.any { it.profileId == id }) {
                    closeTab(id)
                }
            }
            val keys = repository.deleteProfiles(profileIds)
            keys.forEach { webViewPool.destroyProfileStorage(it) }
            clearSelection()
            showMessage("Deleted ${keys.size} profile(s)")
        }
    }

    fun createFolder(name: String, colorIndex: Int) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createFolder(name, colorIndex)
            showMessage("Created folder \"$name\"")
        }
    }

    fun updateFolder(folder: FolderEntity) {
        viewModelScope.launch {
            repository.updateFolder(folder)
            _openTabs.update { tabs ->
                val map = allProfiles.value.associateBy { it.profile.id }
                tabs.map { tab ->
                    val pFolderId = map[tab.profileId]?.profile?.folderId
                    if (pFolderId == folder.id) tab.copy(folderColorIndex = folder.colorIndex) else tab
                }
            }
            showMessage("Updated folder \"${folder.name}\"")
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            repository.deleteFolder(folderId)
            if (_selectedFolderFilter.value == folderId) {
                _selectedFolderFilter.value = FolderFilter.ALL
            }
            showMessage("Folder deleted (profiles moved to Ungrouped)")
        }
    }

    fun setInspectedHistoryProfile(profileId: Long?) {
        _inspectedHistoryProfileId.value = profileId
    }

    fun clearProfileHistory(profileId: Long) {
        viewModelScope.launch {
            repository.clearProfileHistory(profileId)
            showMessage("Cleared browsing history for profile")
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBiometricLockEnabled(enabled) }
    }

    fun setDefaultUserAgent(ua: String) {
        viewModelScope.launch {
            settingsRepository.setDefaultUserAgent(ua)
            val updated = settings.value.copy(defaultUserAgent = ua)
            webViewPool.refreshSettingsAcrossOpenTabs(updated)
            showMessage("Updated Windows Chrome User-Agent")
        }
    }

    fun setDesktopViewportWidth(width: Int) {
        viewModelScope.launch {
            settingsRepository.setDesktopViewportWidth(width)
            val updated = settings.value.copy(desktopViewportWidth = width)
            webViewPool.refreshSettingsAcrossOpenTabs(updated)
        }
    }

    fun setEnforceDesktopFacebookUrl(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setEnforceDesktopFacebookUrl(enabled)
        }
    }

    suspend fun generateBackupJson(): String = repository.exportBackupJson()

    fun restoreFromBackupJson(json: String) {
        viewModelScope.launch {
            val result = repository.importBackupJson(json)
            if (result == null) {
                showMessage("Invalid backup JSON file")
                return@launch
            }
            result.restoredCookies.forEach { (profileKey, cookieStr) ->
                webViewPool.importCookiesIntoProfile(
                    profileKey = profileKey,
                    rawCookieInput = cookieStr,
                    reloadIfOpen = false,
                )
            }
            showMessage(
                "Restored ${result.profilesRestored} profiles, ${result.foldersRestored} folders, ${result.historyRestored} history items"
            )
        }
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun clearMessage() {
        _snackbarMessage.value = null
    }

    private fun currentActiveTab(): OpenTabInfo? {
        val activeId = _activeTabProfileId.value ?: return null
        return _openTabs.value.firstOrNull { it.profileId == activeId }
    }

    private fun handleTabRuntimeUpdate(runtime: BrowserTabRuntimeState) {
        _openTabs.update { tabs ->
            tabs.map { tab ->
                if (tab.profileId == runtime.profileId) {
                    tab.copy(
                        currentUrl = runtime.currentUrl,
                        pageTitle = runtime.pageTitle,
                        progress = runtime.progress,
                        isLoading = runtime.isLoading,
                        canGoBack = runtime.canGoBack,
                        canGoForward = runtime.canGoForward,
                    )
                } else {
                    tab
                }
            }
        }
    }

    override fun onCleared() {
        webViewPool.destroyAll()
        super.onCleared()
    }
}
