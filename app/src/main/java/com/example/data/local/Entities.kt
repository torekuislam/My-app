package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorIndex: Int = 0,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "profiles",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [
        Index(value = ["folderId"]),
        Index(value = ["profileKey"], unique = true),
    ],
)
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileKey: String,
    val displayName: String,
    val folderId: Long? = null,
    val fbUserId: String = "",
    val emailOrPhone: String = "",
    val savedPassword: String = "",
    val notes: String = "",
    val lastUrl: String = "https://www.facebook.com/",
    val lastTitle: String = "Facebook",
    val isPinned: Boolean = false,
    val statusTag: String = STATUS_READY,
    val customUserAgent: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = 0L,
) {
    companion object {
        const val STATUS_READY = "READY"
        const val STATUS_LOGGED_IN = "LOGGED_IN"
        const val STATUS_NEEDS_CHECK = "NEEDS_CHECK"
    }
}

@Entity(
    tableName = "profile_history",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["profileId"]), Index(value = ["visitedAt"])],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "cookie_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class CookieSnapshotEntity(
    @PrimaryKey val profileId: Long,
    val rawCookieString: String,
    val cookieCount: Int,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class ProfileWithCookie(
    @Embedded val profile: ProfileEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "profileId",
    )
    val cookieSnapshot: CookieSnapshotEntity?,
)
