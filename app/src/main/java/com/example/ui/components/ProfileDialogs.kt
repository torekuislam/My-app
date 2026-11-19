package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.FolderEntity
import com.example.data.local.HistoryEntity
import com.example.data.local.ProfileEntity
import com.example.data.local.ProfileWithCookie
import com.example.domain.CookieParser
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.FolderColors
import com.example.ui.theme.MonoCodeStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CookieImportExportDialog(
    profile: ProfileWithCookie,
    onDismiss: () -> Unit,
    onImportAndApply: (rawCookieInput: String, openTabAfter: Boolean) -> Unit,
    onLoadLiveCookies: suspend () -> String,
    onCopyFeedback: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var cookieInput by remember {
        mutableStateOf(profile.cookieSnapshot?.rawCookieString.orEmpty())
    }
    var exportedCookies by remember { mutableStateOf("") }
    var openAfterImport by remember { mutableStateOf(true) }

    val livePreview = remember(cookieInput) { CookieParser.parse(cookieInput) }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            exportedCookies = onLoadLiveCookies()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Cookie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cookies • ${profile.profile.displayName}",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Import String") },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Export Live") },
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (livePreview.cookies.isEmpty()) {
                                "Paste datr=...; sb=...; c_user=...; xs=... or JSON"
                            } else {
                                "${livePreview.cookies.size} cookies (${livePreview.sourceFormat})" +
                                    (livePreview.fbUserId?.let { " • ID: $it" } ?: "")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (livePreview.cookies.isNotEmpty()) {
                                EmeraldAccent
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                val pasted = clipboard.getText()?.text.orEmpty()
                                if (pasted.isNotBlank()) cookieInput = pasted
                            },
                            modifier = Modifier.testTag("paste_cookie_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentPaste,
                                contentDescription = "Paste cookie string",
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste")
                        }
                    }

                    OutlinedTextField(
                        value = cookieInput,
                        onValueChange = { cookieInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 220.dp)
                            .testTag("cookie_string_input"),
                        textStyle = MonoCodeStyle,
                        placeholder = {
                            Text(
                                "datr=...; sb=...; c_user=...; xs=...; wd=1280x2351",
                                style = MonoCodeStyle,
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Open / reload browser after import",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Switch(
                            checked = openAfterImport,
                            onCheckedChange = { openAfterImport = it },
                        )
                    }
                } else {
                    Text(
                        text = "Live cookies stored in this profile's isolated jar:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = exportedCookies.ifBlank { "No cookies recorded yet for this profile." },
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 220.dp),
                        textStyle = MonoCodeStyle,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (exportedCookies.isNotBlank()) {
                                clipboard.setText(AnnotatedString(exportedCookies))
                                onCopyFeedback("Copied live cookies to clipboard")
                            }
                        },
                        enabled = exportedCookies.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy cookies",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy Cookie String")
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 0) {
                Button(
                    onClick = {
                        onImportAndApply(cookieInput, openAfterImport)
                        onDismiss()
                    },
                    enabled = livePreview.cookies.isNotEmpty(),
                    modifier = Modifier.testTag("import_cookies_apply_button"),
                ) {
                    Text("Import & Apply")
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = {
            if (selectedTab == 0) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun CreateOrEditProfileDialog(
    existing: ProfileWithCookie?,
    folders: List<FolderEntity>,
    defaultFolderId: Long?,
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        folderId: Long?,
        email: String,
        password: String,
        notes: String,
        fbUserId: String,
        initialUrl: String,
        rawCookies: String,
        pinned: Boolean,
    ) -> Unit,
    onUpdate: (ProfileEntity) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.profile?.displayName ?: "") }
    var folderId by remember { mutableStateOf(existing?.profile?.folderId ?: defaultFolderId) }
    var email by remember { mutableStateOf(existing?.profile?.emailOrPhone ?: "") }
    var password by remember { mutableStateOf(existing?.profile?.savedPassword ?: "") }
    var showPassword by remember { mutableStateOf(false) }
    var fbUserId by remember { mutableStateOf(existing?.profile?.fbUserId ?: "") }
    var notes by remember { mutableStateOf(existing?.profile?.notes ?: "") }
    var initialUrl by remember { mutableStateOf(existing?.profile?.lastUrl ?: "https://www.facebook.com/") }
    var rawCookies by remember { mutableStateOf("") }
    var isPinned by remember { mutableStateOf(existing?.profile?.isPinned ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) "New Browser Profile" else "Edit Profile",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input"),
                )

                Text("Folder Group", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = folderId == null,
                        onClick = { folderId = null },
                        label = { Text("Ungrouped") },
                    )
                    folders.forEach { folder ->
                        FilterChip(
                            selected = folderId == folder.id,
                            onClick = { folderId = folder.id },
                            label = { Text(folder.name) },
                        )
                    }
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Facebook Email / Phone / Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Saved Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "Toggle password visibility",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = fbUserId,
                    onValueChange = { fbUserId = it },
                    label = { Text("Facebook User ID (c_user, optional)") },
                    singleLine = true,
                    textStyle = MonoCodeStyle,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = initialUrl,
                    onValueChange = { initialUrl = it },
                    label = { Text("Start / Last URL") },
                    singleLine = true,
                    textStyle = MonoCodeStyle,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("2FA Secret / Notes") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (existing == null) {
                    OutlinedTextField(
                        value = rawCookies,
                        onValueChange = { rawCookies = it },
                        label = { Text("Initial Cookies (optional)") },
                        placeholder = { Text("datr=...; sb=...; c_user=...; xs=...", style = MonoCodeStyle) },
                        maxLines = 4,
                        textStyle = MonoCodeStyle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Pin profile to top", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isPinned, onCheckedChange = { isPinned = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (existing == null) {
                        onCreate(name, folderId, email, password, notes, fbUserId, initialUrl, rawCookies, isPinned)
                    } else {
                        onUpdate(
                            existing.profile.copy(
                                displayName = name.trim().ifBlank { existing.profile.displayName },
                                folderId = folderId,
                                emailOrPhone = email.trim(),
                                savedPassword = password,
                                fbUserId = fbUserId.trim(),
                                lastUrl = initialUrl.trim().ifBlank { "https://www.facebook.com/" },
                                notes = notes.trim(),
                                isPinned = isPinned,
                            )
                        )
                    }
                    onDismiss()
                },
                modifier = Modifier.testTag("save_profile_button"),
            ) {
                Text(if (existing == null) "Create Profile" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun BulkCreateProfilesDialog(
    folders: List<FolderEntity>,
    defaultFolderId: Long?,
    onDismiss: () -> Unit,
    onBulkNumbered: (prefix: String, startNumber: Int, count: Int, folderId: Long?, defaultUrl: String) -> Unit,
    onBulkPaste: (rawLines: String, prefix: String, folderId: Long?) -> Unit,
) {
    var modeTab by remember { mutableIntStateOf(0) }
    var prefix by remember { mutableStateOf("FB Account") }
    var startNumText by remember { mutableStateOf("1") }
    var countText by remember { mutableStateOf("10") }
    var defaultUrl by remember { mutableStateOf("https://www.facebook.com/") }
    var pasteLines by remember { mutableStateOf("") }
    var folderId by remember { mutableStateOf(defaultFolderId) }

    val lineCount = remember(pasteLines) {
        pasteLines.lines().count { it.trim().isNotEmpty() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Bulk Create Profiles", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TabRow(selectedTabIndex = modeTab) {
                    Tab(selected = modeTab == 0, onClick = { modeTab = 0 }, text = { Text("Batch Range") })
                    Tab(selected = modeTab == 1, onClick = { modeTab = 1 }, text = { Text("Bulk Paste") })
                }

                Text("Target Folder", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = folderId == null,
                        onClick = { folderId = null },
                        label = { Text("Ungrouped") },
                    )
                    folders.forEach { folder ->
                        FilterChip(
                            selected = folderId == folder.id,
                            onClick = { folderId = folder.id },
                            label = { Text(folder.name) },
                        )
                    }
                }

                if (modeTab == 0) {
                    OutlinedTextField(
                        value = prefix,
                        onValueChange = { prefix = it },
                        label = { Text("Name Prefix") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startNumText,
                            onValueChange = { startNumText = it.filter(Char::isDigit) },
                            label = { Text("Start #") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = countText,
                            onValueChange = { countText = it.filter(Char::isDigit) },
                            label = { Text("Count (max 200)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 25, 50, 100).forEach { preset ->
                            FilterChip(
                                selected = countText.toIntOrNull() == preset,
                                onClick = { countText = preset.toString() },
                                label = { Text("$preset") },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = defaultUrl,
                        onValueChange = { defaultUrl = it },
                        label = { Text("Default URL") },
                        singleLine = true,
                        textStyle = MonoCodeStyle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = "One account per line:\nName | Email | Password | CookieString\nor paste raw cookie strings (one per line).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = prefix,
                        onValueChange = { prefix = it },
                        label = { Text("Fallback Name Prefix") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = pasteLines,
                        onValueChange = { pasteLines = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 220.dp)
                            .testTag("bulk_paste_input"),
                        textStyle = MonoCodeStyle,
                        placeholder = {
                            Text("Acc 1 | mail@fb.com | pass | datr=...; c_user=...; xs=...", style = MonoCodeStyle)
                        },
                    )
                    Text(
                        text = "$lineCount account line(s) ready",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldAccent,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (modeTab == 0) {
                        val start = startNumText.toIntOrNull() ?: 1
                        val count = (countText.toIntOrNull() ?: 10).coerceIn(1, 200)
                        onBulkNumbered(prefix, start, count, folderId, defaultUrl)
                    } else {
                        onBulkPaste(pasteLines, prefix, folderId)
                    }
                    onDismiss()
                },
                enabled = modeTab == 0 || lineCount > 0,
                modifier = Modifier.testTag("bulk_create_confirm_button"),
            ) {
                Text(if (modeTab == 0) "Create Batch" else "Import $lineCount")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun FolderManagerDialog(
    folders: List<FolderEntity>,
    onDismiss: () -> Unit,
    onCreateFolder: (name: String, colorIndex: Int) -> Unit,
    onUpdateFolder: (FolderEntity) -> Unit,
    onDeleteFolder: (Long) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableIntStateOf(0) }
    var editingFolder by remember { mutableStateOf<FolderEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Folders", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(if (editingFolder == null) "New Folder Name" else "Rename Folder") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("folder_name_input"),
                    )
                    IconButton(
                        onClick = {
                            val target = editingFolder
                            if (target != null && newName.isNotBlank()) {
                                onUpdateFolder(target.copy(name = newName.trim(), colorIndex = selectedColor))
                                editingFolder = null
                                newName = ""
                            } else if (newName.isNotBlank()) {
                                onCreateFolder(newName.trim(), selectedColor)
                                newName = ""
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("save_folder_button"),
                    ) {
                        Icon(
                            imageVector = if (editingFolder == null) Icons.Filled.Add else Icons.Filled.Check,
                            contentDescription = "Save folder",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FolderColors.forEachIndexed { idx, color ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (selectedColor == idx) {
                                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else Modifier
                                )
                                .clickable { selectedColor = idx },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                if (folders.isEmpty()) {
                    Text(
                        text = "No folders yet. Create folders to group your 100+ Facebook accounts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                        items(folders, key = { it.id }) { folder ->
                            val color = FolderColors[folder.colorIndex.mod(FolderColors.size)]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = {
                                        editingFolder = folder
                                        newName = folder.name
                                        selectedColor = folder.colorIndex
                                    },
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Rename folder", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onDeleteFolder(folder.id) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete folder",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
fun MoveProfilesToFolderDialog(
    count: Int,
    folders: List<FolderEntity>,
    onDismiss: () -> Unit,
    onMove: (Long?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move $count Profile(s) to Folder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = {
                        onMove(null)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Ungrouped")
                }
                folders.forEach { folder ->
                    val color = FolderColors[folder.colorIndex.mod(FolderColors.size)]
                    OutlinedButton(
                        onClick = {
                            onMove(folder.id)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(folder.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun ProfileVaultAndHistoryDialog(
    profile: ProfileWithCookie,
    history: List<HistoryEntity>,
    isActiveTabOpen: Boolean,
    onDismiss: () -> Unit,
    onAutoFillLogin: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onClearHistory: () -> Unit,
    onOpenCookieImport: () -> Unit,
    onEditProfile: () -> Unit,
    onCopyFeedback: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var tabIndex by remember { mutableIntStateOf(0) }
    var showPassword by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = profile.profile.displayName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = tabIndex) {
                    Tab(
                        selected = tabIndex == 0,
                        onClick = { tabIndex = 0 },
                        icon = { Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        text = { Text("Vault") },
                    )
                    Tab(
                        selected = tabIndex == 1,
                        onClick = { tabIndex = 1 },
                        icon = { Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        text = { Text("History (${history.size})") },
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (tabIndex == 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        VaultRow(
                            label = "Facebook ID (c_user)",
                            value = profile.profile.fbUserId.ifBlank { "Not detected yet" },
                            canCopy = profile.profile.fbUserId.isNotBlank(),
                            onCopy = {
                                clipboard.setText(AnnotatedString(profile.profile.fbUserId))
                                onCopyFeedback("Copied Facebook ID")
                            },
                        )
                        VaultRow(
                            label = "Email / Phone",
                            value = profile.profile.emailOrPhone.ifBlank { "Not saved" },
                            canCopy = profile.profile.emailOrPhone.isNotBlank(),
                            onCopy = {
                                clipboard.setText(AnnotatedString(profile.profile.emailOrPhone))
                                onCopyFeedback("Copied email/phone")
                            },
                        )
                        VaultRow(
                            label = "Password",
                            value = when {
                                profile.profile.savedPassword.isBlank() -> "Not saved"
                                showPassword -> profile.profile.savedPassword
                                else -> "••••••••••••"
                            },
                            canCopy = profile.profile.savedPassword.isNotBlank(),
                            trailingExtra = {
                                if (profile.profile.savedPassword.isNotBlank()) {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(
                                            if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = "Reveal password",
                                        )
                                    }
                                }
                            },
                            onCopy = {
                                clipboard.setText(AnnotatedString(profile.profile.savedPassword))
                                onCopyFeedback("Copied password")
                            },
                        )
                        if (profile.profile.notes.isNotBlank()) {
                            VaultRow(
                                label = "Notes / 2FA",
                                value = profile.profile.notes,
                                canCopy = true,
                                onCopy = {
                                    clipboard.setText(AnnotatedString(profile.profile.notes))
                                    onCopyFeedback("Copied notes")
                                },
                            )
                        }

                        if (isActiveTabOpen &&
                            (profile.profile.emailOrPhone.isNotBlank() || profile.profile.savedPassword.isNotBlank())
                        ) {
                            Button(
                                onClick = {
                                    onAutoFillLogin()
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Auto-Fill Into Active Page")
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onOpenCookieImport()
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.Cookie, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cookies")
                            }
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onEditProfile()
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Edit")
                            }
                        }
                    }
                } else {
                    if (history.isEmpty()) {
                        Text(
                            text = "No isolated browsing history recorded for this profile yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = onClearHistory,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear History")
                            }
                        }
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                            items(history, key = { it.id }) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable {
                                            onOpenUrl(item.url)
                                            onDismiss()
                                        }
                                        .padding(vertical = 8.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${dateFormat.format(Date(item.visitedAt))} • ${item.url}",
                                            style = MonoCodeStyle,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Icon(
                                        Icons.Filled.OpenInBrowser,
                                        contentDescription = "Open URL",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun VaultRow(
    label: String,
    value: String,
    canCopy: Boolean,
    trailingExtra: (@Composable () -> Unit)? = null,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailingExtra?.invoke()
        if (canCopy) {
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy $label",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
