package com.example.ui.screens

import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.local.ProfileWithCookie
import com.example.ui.state.OpenTabInfo
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.FolderColors
import com.example.ui.theme.MonoCodeStyle

@Composable
fun BrowserWorkspaceScreen(
    openTabs: List<OpenTabInfo>,
    activeTab: OpenTabInfo?,
    activeProfile: ProfileWithCookie?,
    isTabGridOpen: Boolean,
    showDrawerButton: Boolean,
    onOpenDrawer: () -> Unit,
    obtainWebView: (OpenTabInfo) -> WebView,
    onSelectTab: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
    onCloseAllTabs: () -> Unit,
    onToggleTabGrid: (Boolean) -> Unit,
    onNavigateUrl: (String) -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onReload: () -> Unit,
    onOpenCookieImport: (ProfileWithCookie) -> Unit,
    onOpenVaultAndHistory: (ProfileWithCookie) -> Unit,
    onAutoFillLogin: (Long) -> Unit,
    onGoToProfilesHub: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var urlInput by remember(activeTab?.profileId, activeTab?.currentUrl) {
        mutableStateOf(activeTab?.currentUrl ?: "https://www.facebook.com/")
    }

    LaunchedEffect(activeTab?.currentUrl) {
        if (activeTab != null) {
            urlInput = activeTab.currentUrl
        }
    }

    BackHandler {
        when {
            isTabGridOpen -> onToggleTabGrid(false)
            activeTab?.canGoBack == true -> onGoBack()
            else -> onGoToProfilesHub()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Chrome-Style Horizontal Tab Strip + Square Tab Switcher Counter
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showDrawerButton) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.testTag("browser_open_drawer_button"),
                        ) {
                            Icon(
                                Icons.Filled.Menu,
                                contentDescription = "Open folders & sessions sidebar",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    IconButton(
                        onClick = onGoToProfilesHub,
                        modifier = Modifier.testTag("browser_home_button"),
                    ) {
                        Icon(
                            Icons.Filled.Home,
                            contentDescription = "Profiles Hub",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        items(openTabs, key = { it.profileId }) { tab ->
                            val isActive = tab.profileId == activeTab?.profileId
                            val tabColor = tab.folderColorIndex?.let {
                                FolderColors[it.mod(FolderColors.size)]
                            } ?: MaterialTheme.colorScheme.primary

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .background(
                                        if (isActive) MaterialTheme.colorScheme.surfaceContainerHigh
                                        else MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                    .border(
                                        width = if (isActive) 1.5.dp else 1.dp,
                                        color = if (isActive) tabColor else MaterialTheme.colorScheme.outlineVariant,
                                        shape = MaterialTheme.shapes.small,
                                    )
                                    .clickable { onSelectTab(tab.profileId) }
                                    .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                                    .testTag("tab_pill_${tab.profileId}"),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(tabColor),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tab.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(96.dp),
                                )
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable { onCloseTab(tab.profileId) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Close ${tab.displayName} tab",
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Chrome-style [ N ] square tab grid button
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onToggleTabGrid(!isTabGridOpen) }
                            .padding(8.dp)
                            .testTag("tab_grid_switcher_button"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .border(2.dp, MaterialTheme.colorScheme.onSurface, MaterialTheme.shapes.extraSmall),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${openTabs.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    IconButton(
                        onClick = onGoToProfilesHub,
                        modifier = Modifier.testTag("add_tab_from_hub_button"),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Open another profile tab")
                    }
                }
            }

            // 2. Desktop Chrome Omnibox & Action Bar
            if (activeTab != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 1.dp,
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = onGoBack,
                                enabled = activeTab.canGoBack,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                )
                            }
                            IconButton(
                                onClick = onGoForward,
                                enabled = activeTab.canGoForward,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Forward",
                                )
                            }
                            IconButton(onClick = onReload) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Reload page")
                            }

                            // Omnibox
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(MaterialTheme.shapes.large)
                                    .background(MaterialTheme.colorScheme.background)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
                                    .padding(horizontal = 10.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = "HTTPS",
                                    tint = EmeraldAccent,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                BasicTextField(
                                    value = urlInput,
                                    onValueChange = { urlInput = it },
                                    singleLine = true,
                                    textStyle = MonoCodeStyle.copy(color = MaterialTheme.colorScheme.onBackground),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                    keyboardActions = KeyboardActions(
                                        onGo = {
                                            focusManager.clearFocus()
                                            onNavigateUrl(urlInput)
                                        }
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("browser_url_input"),
                                )
                                Icon(
                                    Icons.Filled.DesktopWindows,
                                    contentDescription = "Windows Chrome Desktop Mode Active",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp),
                                )
                            }

                            if (activeProfile != null) {
                                IconButton(
                                    onClick = { onOpenCookieImport(activeProfile) },
                                    modifier = Modifier.testTag("browser_import_cookies_button"),
                                ) {
                                    Icon(
                                        Icons.Filled.Cookie,
                                        contentDescription = "Import cookies",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (activeProfile.profile.emailOrPhone.isNotBlank() ||
                                    activeProfile.profile.savedPassword.isNotBlank()
                                ) {
                                    IconButton(
                                        onClick = { onAutoFillLogin(activeTab.profileId) },
                                        modifier = Modifier.testTag("browser_autofill_button"),
                                    ) {
                                        Icon(
                                            Icons.Filled.AutoFixHigh,
                                            contentDescription = "Auto-fill saved login",
                                            tint = EmeraldAccent,
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onOpenVaultAndHistory(activeProfile) },
                                    modifier = Modifier.testTag("browser_vault_history_button"),
                                ) {
                                    Icon(
                                        Icons.Filled.Key,
                                        contentDescription = "Profile vault & history",
                                    )
                                }
                            }
                        }

                        if (activeTab.isLoading) {
                            LinearProgressIndicator(
                                progress = { (activeTab.progress / 100f).coerceIn(0.05f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.5.dp),
                            )
                        }
                    }
                }

                // 3. Isolated WebView Host
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    key(activeTab.profileKey) {
                        val webView = remember(activeTab.profileKey) {
                            obtainWebView(activeTab)
                        }
                        AndroidView(
                            factory = {
                                (webView.parent as? ViewGroup)?.removeView(webView)
                                webView
                            },
                            update = { view ->
                                if (view.parent == null) {
                                    // Attached cleanly
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            } else {
                // Fallback if no tabs open
                Box(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No open browser tabs", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onGoToProfilesHub) {
                            Text("Browse Profiles")
                        }
                    }
                }
            }
        }

        // 4. Chrome-Style 2-Column Tab Overview Grid Switcher
        AnimatedVisibility(
            visible = isTabGridOpen,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Open Sessions (${openTabs.size})",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onCloseAllTabs) {
                                Text("Close All", color = MaterialTheme.colorScheme.error)
                            }
                            OutlinedButton(onClick = onGoToProfilesHub) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Profile")
                            }
                            IconButton(onClick = { onToggleTabGrid(false) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close tab switcher")
                            }
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 164.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(openTabs, key = { it.profileId }) { tab ->
                            val isActive = tab.profileId == activeTab?.profileId
                            val accent = tab.folderColorIndex?.let {
                                FolderColors[it.mod(FolderColors.size)]
                            } ?: MaterialTheme.colorScheme.primary

                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(152.dp)
                                    .border(
                                        width = if (isActive) 2.dp else 1.dp,
                                        color = if (isActive) accent else MaterialTheme.colorScheme.outline,
                                        shape = MaterialTheme.shapes.large,
                                    )
                                    .clip(MaterialTheme.shapes.large)
                                    .clickable { onSelectTab(tab.profileId) }
                                    .testTag("tab_grid_card_${tab.profileId}"),
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(accent.copy(alpha = 0.16f))
                                            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(accent),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = tab.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        IconButton(
                                            onClick = { onCloseTab(tab.profileId) },
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "Close tab",
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = tab.pageTitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Column {
                                            if (tab.fbUserId.isNotBlank()) {
                                                Text(
                                                    text = "c_user: ${tab.fbUserId}",
                                                    style = MonoCodeStyle,
                                                    color = EmeraldAccent,
                                                    maxLines = 1,
                                                )
                                            }
                                            Text(
                                                text = tab.currentUrl,
                                                style = MonoCodeStyle,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
