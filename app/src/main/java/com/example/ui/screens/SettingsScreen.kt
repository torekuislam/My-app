package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.data.preferences.AppSettings
import com.example.data.preferences.ThemeMode
import com.example.ui.components.canUseBiometricOrDeviceCredential
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.MonoCodeStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    isMultiProfileSupported: Boolean,
    showDrawerButton: Boolean,
    onOpenDrawer: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBiometricToggle: (Boolean) -> Unit,
    onLockNow: () -> Unit,
    onUserAgentSave: (String) -> Unit,
    onDesktopWidthChange: (Int) -> Unit,
    onEnforceDesktopFbChange: (Boolean) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onBackToHub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricAvailable = remember(activity) {
        activity != null && canUseBiometricOrDeviceCredential(activity)
    }
    var uaInput by remember(settings.defaultUserAgent) {
        mutableStateOf(settings.defaultUserAgent)
    }

    BackHandler { onBackToHub() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showDrawerButton) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.testTag("settings_open_drawer_button"),
                        ) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open workspace sidebar")
                        }
                    }
                },
                title = { Text("Settings & Security") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1. Appearance
                SettingsSectionCard(
                    title = "Appearance & Focus",
                    icon = Icons.Filled.Palette,
                ) {
                    Text(
                        text = "Theme Mode",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                                label = {
                                    Text(
                                        when (mode) {
                                            ThemeMode.DARK -> "Dark Focus"
                                            ThemeMode.LIGHT -> "Light"
                                            ThemeMode.SYSTEM -> "System"
                                        }
                                    )
                                },
                                modifier = Modifier.testTag("theme_chip_${mode.name.lowercase()}"),
                            )
                        }
                    }
                }

                // 2. Security
                SettingsSectionCard(
                    title = "Workspace Lock",
                    icon = Icons.Filled.Fingerprint,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Biometric / PIN App Lock", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (biometricAvailable) {
                                    "Require fingerprint, face, or device PIN on launch"
                                } else {
                                    "Configure a screen lock in Android Settings to enable"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.biometricLockEnabled,
                            enabled = biometricAvailable,
                            onCheckedChange = onBiometricToggle,
                            modifier = Modifier.testTag("biometric_lock_switch"),
                        )
                    }
                    if (settings.biometricLockEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(onClick = onLockNow) {
                            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lock SessionDeck Now")
                        }
                    }
                }

                // 3. Windows Chrome Desktop Engine
                SettingsSectionCard(
                    title = "Windows Chrome Desktop Mode",
                    icon = Icons.Filled.DesktopWindows,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (isMultiProfileSupported) EmeraldAccent.copy(alpha = 0.14f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .padding(10.dp),
                    ) {
                        Icon(
                            Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = if (isMultiProfileSupported) EmeraldAccent else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isMultiProfileSupported) {
                                "Chromium ProfileStore Multi-Profile Isolation Active"
                            } else {
                                "Cookie Snapshot Swap Isolation Active"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isMultiProfileSupported) EmeraldAccent else MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Windows Chrome Presets", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uaInput == AppSettings.CHROME_135_WIN_UA,
                            onClick = {
                                uaInput = AppSettings.CHROME_135_WIN_UA
                                onUserAgentSave(uaInput)
                            },
                            label = { Text("Win Chrome 135") },
                        )
                        FilterChip(
                            selected = uaInput == AppSettings.CHROME_134_WIN_UA,
                            onClick = {
                                uaInput = AppSettings.CHROME_134_WIN_UA
                                onUserAgentSave(uaInput)
                            },
                            label = { Text("Win Chrome 134") },
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uaInput,
                        onValueChange = { uaInput = it },
                        label = { Text("Active Windows Chrome User-Agent") },
                        textStyle = MonoCodeStyle,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onUserAgentSave(uaInput) },
                        enabled = uaInput.isNotBlank() && uaInput != settings.defaultUserAgent,
                    ) {
                        Text("Apply User-Agent")
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Desktop Viewport Width", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1024, 1280, 1440).forEach { w ->
                            FilterChip(
                                selected = settings.desktopViewportWidth == w,
                                onClick = { onDesktopWidthChange(w) },
                                label = { Text("${w}px") },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Force www.facebook.com Desktop", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Automatically rewrite m.facebook.com / mbasic links to desktop www",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.enforceDesktopFacebookUrl,
                            onCheckedChange = onEnforceDesktopFbChange,
                        )
                    }
                }

                // 4. Local JSON Backup & Restore
                SettingsSectionCard(
                    title = "Local JSON Backup & Restore",
                    icon = Icons.Filled.Backup,
                ) {
                    Text(
                        text = "Export all profiles, folders, cookies, saved credentials, and history to a local JSON file, or restore from an existing backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onExportBackup,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_backup_button"),
                        ) {
                            Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export JSON")
                        }
                        OutlinedButton(
                            onClick = onImportBackup,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_backup_button"),
                        ) {
                            Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore JSON")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
