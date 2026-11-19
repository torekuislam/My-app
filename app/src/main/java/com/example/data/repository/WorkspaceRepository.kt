package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.CookieSnapshotEntity
import com.example.data.local.FolderEntity
import com.example.data.local.HistoryEntity
import com.example.data.local.ProfileEntity
import com.example.data.local.ProfileWithCookie
import com.example.domain.BackupSerializer
import com.example.domain.CookieParser
import kotlinx.coroutines.flow.Flow
import java.util.UUID

data class BulkPasteResult(
    val createdCount: Int,
    val cookiesImportedCount: Int,
    val importedProfileCookies: List<Pair<ProfileEntity, String>>,
)

data class BackupRestoreResult(
    val foldersRestored: Int,
    val profilesRestored: Int,
    val historyRestored: Int,
    val restoredCookies: List<Pair<String, String>>,
)

class WorkspaceRepository(private val db: AppDatabase) {
    private val folderDao = db.folderDao()
    private val profileDao = db.profileDao()
    private val historyDao = db.historyDao()
    private val cookieSnapshotDao = db.cookieSnapshotDao()

    val foldersFlow: Flow<List<FolderEntity>> = folderDao.observeAll()
    val profilesFlow: Flow<List<ProfileWithCookie>> = profileDao.observeAllWithCookies()

    fun observeHistoryForProfile(profileId: Long): Flow<List<HistoryEntity>> =
        historyDao.observeForProfile(profileId)

    suspend fun createFolder(name: String, colorIndex: Int): Long {
        val current = folderDao.getAllOnce()
        val folder = FolderEntity(
            name = name.trim(),
            colorIndex = colorIndex,
            sortOrder = (current.maxOfOrNull { it.sortOrder } ?: 0) + 1,
        )
        return folderDao.insert(folder)
    }

    suspend fun updateFolder(folder: FolderEntity) {
        folderDao.update(folder.copy(name = folder.name.trim()))
    }

    suspend fun deleteFolder(folderId: Long) {
        folderDao.deleteById(folderId)
    }

    suspend fun createProfile(
        displayName: String,
        folderId: Long?,
        emailOrPhone: String = "",
        savedPassword: String = "",
        notes: String = "",
        fbUserId: String = "",
        initialUrl: String = "https://www.facebook.com/",
        rawCookies: String = "",
        isPinned: Boolean = false,
    ): Pair<ProfileEntity, String> {
        val parsed = CookieParser.parse(rawCookies)
        val detectedId = fbUserId.trim().ifBlank { parsed.fbUserId.orEmpty() }
        val status = if (parsed.cookies.isNotEmpty() && detectedId.isNotBlank()) {
            ProfileEntity.STATUS_LOGGED_IN
        } else {
            ProfileEntity.STATUS_READY
        }
        val entity = ProfileEntity(
            profileKey = "prof_${UUID.randomUUID().toString().replace("-", "").take(16)}",
            displayName = displayName.trim().ifBlank { "FB Account" },
            folderId = folderId,
            fbUserId = detectedId,
            emailOrPhone = emailOrPhone.trim(),
            savedPassword = savedPassword,
            notes = notes.trim(),
            lastUrl = initialUrl.trim().ifBlank { "https://www.facebook.com/" },
            isPinned = isPinned,
            statusTag = status,
        )
        val insertedId = db.withTransaction {
            val id = profileDao.insert(entity)
            if (parsed.cookies.isNotEmpty()) {
                cookieSnapshotDao.upsert(
                    CookieSnapshotEntity(
                        profileId = id,
                        rawCookieString = parsed.normalizedCookieString,
                        cookieCount = parsed.cookies.size,
                    )
                )
            }
            id
        }
        val saved = entity.copy(id = insertedId)
        return saved to parsed.normalizedCookieString
    }

    suspend fun updateProfile(profile: ProfileEntity) {
        profileDao.update(profile)
    }

    suspend fun moveProfilesToFolder(profileIds: List<Long>, folderId: Long?) {
        if (profileIds.isEmpty()) return
        profileDao.moveProfilesToFolder(profileIds, folderId)
    }

    suspend fun deleteProfiles(profileIds: List<Long>): List<String> {
        if (profileIds.isEmpty()) return emptyList()
        val all = profileDao.getAllOnce().filter { it.id in profileIds }
        profileDao.deleteByIds(profileIds)
        return all.map { it.profileKey }
    }

    suspend fun bulkCreateNumbered(
        prefix: String,
        startNumber: Int,
        count: Int,
        folderId: Long?,
        defaultUrl: String,
    ): Int {
        val safeCount = count.coerceIn(1, 200)
        val safePrefix = prefix.trim().ifBlank { "FB Profile" }
        val url = defaultUrl.trim().ifBlank { "https://www.facebook.com/" }
        val now = System.currentTimeMillis()
        val entities = (0 until safeCount).map { offset ->
            val number = startNumber + offset
            ProfileEntity(
                profileKey = "prof_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                displayName = "$safePrefix $number",
                folderId = folderId,
                lastUrl = url,
                createdAt = now + offset,
            )
        }
        db.withTransaction {
            profileDao.insertAll(entities)
        }
        return entities.size
    }

    suspend fun bulkImportFromLines(
        rawLines: String,
        defaultPrefix: String,
        folderId: Long?,
    ): BulkPasteResult {
        val lines = rawLines.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return BulkPasteResult(0, 0, emptyList())

        val prefix = defaultPrefix.trim().ifBlank { "FB Account" }
        val existingCount = profileDao.getAllOnce().size
        val now = System.currentTimeMillis()

        val prepared = mutableListOf<Pair<ProfileEntity, String>>()
        lines.forEachIndexed { index, line ->
            val parts = if (line.contains("|")) line.split("|").map { it.trim() } else listOf(line)
            var name = "$prefix ${existingCount + index + 1}"
            var email = ""
            var pass = ""
            var rawCookie = ""

            when {
                parts.size >= 4 -> {
                    name = parts[0].ifBlank { name }
                    email = parts[1]
                    pass = parts[2]
                    rawCookie = parts.drop(3).joinToString("|")
                }
                parts.size == 3 -> {
                    if (parts[2].contains("=") || parts[2].startsWith("[")) {
                        email = parts[0]
                        pass = parts[1]
                        rawCookie = parts[2]
                        if (email.isNotBlank()) name = email.substringBefore("@")
                    } else {
                        name = parts[0].ifBlank { name }
                        email = parts[1]
                        pass = parts[2]
                    }
                }
                parts.size == 2 -> {
                    if (parts[1].contains("=") || parts[1].startsWith("[")) {
                        name = parts[0].ifBlank { name }
                        rawCookie = parts[1]
                    } else {
                        email = parts[0]
                        pass = parts[1]
                        if (email.isNotBlank()) name = email.substringBefore("@")
                    }
                }
                else -> {
                    if (line.contains("=") || line.startsWith("[")) {
                        rawCookie = line
                    } else {
                        name = line
                    }
                }
            }

            val parsed = CookieParser.parse(rawCookie)
            val fbId = parsed.fbUserId.orEmpty()
            if (fbId.isNotBlank() && name.startsWith(prefix)) {
                name = "$prefix ($fbId)"
            }
            val entity = ProfileEntity(
                profileKey = "prof_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                displayName = name,
                folderId = folderId,
                fbUserId = fbId,
                emailOrPhone = email,
                savedPassword = pass,
                statusTag = if (parsed.cookies.isNotEmpty() && fbId.isNotBlank()) {
                    ProfileEntity.STATUS_LOGGED_IN
                } else {
                    ProfileEntity.STATUS_READY
                },
                createdAt = now + index,
            )
            prepared += entity to parsed.normalizedCookieString
        }

        val withCookies = mutableListOf<Pair<ProfileEntity, String>>()
        db.withTransaction {
            val ids = profileDao.insertAll(prepared.map { it.first })
            val snapshots = mutableListOf<CookieSnapshotEntity>()
            ids.forEachIndexed { idx, rowId ->
                val (entity, cookieStr) = prepared[idx]
                val savedEntity = entity.copy(id = rowId)
                if (cookieStr.isNotBlank()) {
                    val count = CookieParser.parse(cookieStr).cookies.size
                    snapshots += CookieSnapshotEntity(
                        profileId = rowId,
                        rawCookieString = cookieStr,
                        cookieCount = count,
                    )
                    withCookies += savedEntity to cookieStr
                }
            }
            if (snapshots.isNotEmpty()) {
                cookieSnapshotDao.upsertAll(snapshots)
            }
        }

        return BulkPasteResult(
            createdCount = prepared.size,
            cookiesImportedCount = withCookies.size,
            importedProfileCookies = withCookies,
        )
    }

    suspend fun saveCookieSnapshot(
        profileId: Long,
        normalizedCookies: String,
        cookieCount: Int,
        fbUserId: String?,
    ) {
        db.withTransaction {
            if (normalizedCookies.isBlank()) {
                cookieSnapshotDao.deleteForProfile(profileId)
            } else {
                cookieSnapshotDao.upsert(
                    CookieSnapshotEntity(
                        profileId = profileId,
                        rawCookieString = normalizedCookies,
                        cookieCount = cookieCount,
                    )
                )
            }
            if (!fbUserId.isNullOrBlank()) {
                profileDao.updateFacebookIdentity(
                    profileId = profileId,
                    fbUserId = fbUserId,
                    statusTag = ProfileEntity.STATUS_LOGGED_IN,
                )
            }
        }
    }

    suspend fun getCookieSnapshot(profileId: Long): CookieSnapshotEntity? =
        cookieSnapshotDao.getForProfile(profileId)

    suspend fun recordNavigation(profileId: Long, url: String, title: String) {
        if (url.isBlank() || url == "about:blank") return
        val now = System.currentTimeMillis()
        val safeTitle = title.trim().ifBlank { url }
        db.withTransaction {
            profileDao.updateLastNavigation(profileId, url, safeTitle, now)
            historyDao.insert(
                HistoryEntity(
                    profileId = profileId,
                    title = safeTitle,
                    url = url,
                    visitedAt = now,
                )
            )
        }
    }

    suspend fun touchLastOpened(profileId: Long) {
        profileDao.touchLastOpened(profileId, System.currentTimeMillis())
    }

    suspend fun clearProfileHistory(profileId: Long) {
        historyDao.clearForProfile(profileId)
    }

    suspend fun exportBackupJson(): String {
        val folders = folderDao.getAllOnce()
        val profiles = profileDao.getAllOnce()
        val snapshots = cookieSnapshotDao.getAllOnce()
        val history = historyDao.getAllOnce()
        return BackupSerializer.encode(folders, profiles, snapshots, history)
    }

    suspend fun importBackupJson(json: String): BackupRestoreResult? {
        val dto = BackupSerializer.decode(json) ?: return null
        val restoredCookies = mutableListOf<Pair<String, String>>()
        var foldersCount = 0
        var profilesCount = 0
        var historyCount = 0

        db.withTransaction {
            val existingFolders = folderDao.getAllOnce().associateBy { it.name.lowercase() }
            val folderIdMap = mutableMapOf<Long, Long>()

            dto.folders.forEach { fDto ->
                val match = existingFolders[fDto.name.lowercase()]
                if (match != null) {
                    folderIdMap[fDto.id] = match.id
                } else {
                    val newId = folderDao.insert(
                        FolderEntity(
                            name = fDto.name,
                            colorIndex = fDto.colorIndex,
                            sortOrder = fDto.sortOrder,
                            createdAt = fDto.createdAt,
                        )
                    )
                    folderIdMap[fDto.id] = newId
                    foldersCount++
                }
            }

            val profileIdByKey = mutableMapOf<String, Long>()
            dto.profiles.forEach { pDto ->
                val existing = profileDao.getByKey(pDto.profileKey)
                val mappedFolderId = pDto.folderLocalId?.let { folderIdMap[it] }
                val profileId = if (existing != null) {
                    profileDao.update(
                        existing.copy(
                            displayName = pDto.displayName,
                            folderId = mappedFolderId ?: existing.folderId,
                            fbUserId = pDto.fbUserId.ifBlank { existing.fbUserId },
                            emailOrPhone = pDto.emailOrPhone.ifBlank { existing.emailOrPhone },
                            savedPassword = pDto.savedPassword.ifBlank { existing.savedPassword },
                            notes = pDto.notes.ifBlank { existing.notes },
                            lastUrl = pDto.lastUrl,
                            lastTitle = pDto.lastTitle,
                            isPinned = pDto.isPinned,
                            statusTag = pDto.statusTag,
                            customUserAgent = pDto.customUserAgent,
                        )
                    )
                    existing.id
                } else {
                    profilesCount++
                    profileDao.insert(
                        ProfileEntity(
                            profileKey = pDto.profileKey,
                            displayName = pDto.displayName,
                            folderId = mappedFolderId,
                            fbUserId = pDto.fbUserId,
                            emailOrPhone = pDto.emailOrPhone,
                            savedPassword = pDto.savedPassword,
                            notes = pDto.notes,
                            lastUrl = pDto.lastUrl,
                            lastTitle = pDto.lastTitle,
                            isPinned = pDto.isPinned,
                            statusTag = pDto.statusTag,
                            customUserAgent = pDto.customUserAgent,
                            createdAt = pDto.createdAt,
                            lastOpenedAt = pDto.lastOpenedAt,
                        )
                    )
                }
                profileIdByKey[pDto.profileKey] = profileId

                if (pDto.rawCookieString.isNotBlank()) {
                    val parsed = CookieParser.parse(pDto.rawCookieString)
                    if (parsed.cookies.isNotEmpty()) {
                        cookieSnapshotDao.upsert(
                            CookieSnapshotEntity(
                                profileId = profileId,
                                rawCookieString = parsed.normalizedCookieString,
                                cookieCount = parsed.cookies.size,
                            )
                        )
                        restoredCookies += pDto.profileKey to parsed.normalizedCookieString
                    }
                }
            }

            val historyEntities = dto.history.mapNotNull { h ->
                val pId = profileIdByKey[h.profileKey] ?: return@mapNotNull null
                HistoryEntity(
                    profileId = pId,
                    title = h.title,
                    url = h.url,
                    visitedAt = h.visitedAt,
                )
            }
            if (historyEntities.isNotEmpty()) {
                historyDao.insertAll(historyEntities)
                historyCount = historyEntities.size
            }
        }

        return BackupRestoreResult(
            foldersRestored = foldersCount,
            profilesRestored = profilesCount,
            historyRestored = historyCount,
            restoredCookies = restoredCookies,
        )
    }
}
