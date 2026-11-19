package com.example.domain

import com.example.data.local.CookieSnapshotEntity
import com.example.data.local.FolderEntity
import com.example.data.local.HistoryEntity
import com.example.data.local.ProfileEntity
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@JsonClass(generateAdapter = true)
data class BackupFolderDto(
    val id: Long,
    val name: String,
    val colorIndex: Int,
    val sortOrder: Int,
    val createdAt: Long,
)

@JsonClass(generateAdapter = true)
data class BackupProfileDto(
    val profileKey: String,
    val displayName: String,
    val folderLocalId: Long? = null,
    val fbUserId: String = "",
    val emailOrPhone: String = "",
    val savedPassword: String = "",
    val notes: String = "",
    val lastUrl: String = "https://www.facebook.com/",
    val lastTitle: String = "Facebook",
    val isPinned: Boolean = false,
    val statusTag: String = ProfileEntity.STATUS_READY,
    val customUserAgent: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = 0L,
    val rawCookieString: String = "",
    val cookieCount: Int = 0,
)

@JsonClass(generateAdapter = true)
data class BackupHistoryDto(
    val profileKey: String,
    val title: String,
    val url: String,
    val visitedAt: Long,
)

@JsonClass(generateAdapter = true)
data class SessionDeckBackupDto(
    val schemaVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val folders: List<BackupFolderDto> = emptyList(),
    val profiles: List<BackupProfileDto> = emptyList(),
    val history: List<BackupHistoryDto> = emptyList(),
)

object BackupSerializer {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(SessionDeckBackupDto::class.java).indent("  ")

    fun encode(
        folders: List<FolderEntity>,
        profiles: List<ProfileEntity>,
        snapshots: List<CookieSnapshotEntity>,
        history: List<HistoryEntity>,
    ): String {
        val snapshotByProfile = snapshots.associateBy { it.profileId }
        val profileKeyById = profiles.associate { it.id to it.profileKey }

        val backup = SessionDeckBackupDto(
            schemaVersion = 1,
            exportedAt = System.currentTimeMillis(),
            folders = folders.map {
                BackupFolderDto(
                    id = it.id,
                    name = it.name,
                    colorIndex = it.colorIndex,
                    sortOrder = it.sortOrder,
                    createdAt = it.createdAt,
                )
            },
            profiles = profiles.map { profile ->
                val snap = snapshotByProfile[profile.id]
                BackupProfileDto(
                    profileKey = profile.profileKey,
                    displayName = profile.displayName,
                    folderLocalId = profile.folderId,
                    fbUserId = profile.fbUserId,
                    emailOrPhone = profile.emailOrPhone,
                    savedPassword = profile.savedPassword,
                    notes = profile.notes,
                    lastUrl = profile.lastUrl,
                    lastTitle = profile.lastTitle,
                    isPinned = profile.isPinned,
                    statusTag = profile.statusTag,
                    customUserAgent = profile.customUserAgent,
                    createdAt = profile.createdAt,
                    lastOpenedAt = profile.lastOpenedAt,
                    rawCookieString = snap?.rawCookieString.orEmpty(),
                    cookieCount = snap?.cookieCount ?: 0,
                )
            },
            history = history.mapNotNull { h ->
                val key = profileKeyById[h.profileId] ?: return@mapNotNull null
                BackupHistoryDto(
                    profileKey = key,
                    title = h.title,
                    url = h.url,
                    visitedAt = h.visitedAt,
                )
            },
        )
        return adapter.toJson(backup)
    }

    fun decode(json: String): SessionDeckBackupDto? {
        return runCatching { adapter.fromJson(json) }.getOrNull()
    }
}
