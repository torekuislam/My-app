package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAllOnce(): List<FolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteById(folderId: Long)
}

@Dao
interface ProfileDao {
    @Transaction
    @Query("SELECT * FROM profiles ORDER BY isPinned DESC, lastOpenedAt DESC, createdAt DESC")
    fun observeAllWithCookies(): Flow<List<ProfileWithCookie>>

    @Query("SELECT * FROM profiles ORDER BY id ASC")
    suspend fun getAllOnce(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :profileId LIMIT 1")
    suspend fun getById(profileId: Long): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE profileKey = :profileKey LIMIT 1")
    suspend fun getByKey(profileKey: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<ProfileEntity>): List<Long>

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("UPDATE profiles SET folderId = :folderId WHERE id IN (:profileIds)")
    suspend fun moveProfilesToFolder(profileIds: List<Long>, folderId: Long?)

    @Query("DELETE FROM profiles WHERE id IN (:profileIds)")
    suspend fun deleteByIds(profileIds: List<Long>)

    @Query("UPDATE profiles SET lastUrl = :url, lastTitle = :title, lastOpenedAt = :openedAt WHERE id = :profileId")
    suspend fun updateLastNavigation(profileId: Long, url: String, title: String, openedAt: Long)

    @Query("UPDATE profiles SET lastOpenedAt = :openedAt WHERE id = :profileId")
    suspend fun touchLastOpened(profileId: Long, openedAt: Long)

    @Query("UPDATE profiles SET fbUserId = :fbUserId, statusTag = :statusTag WHERE id = :profileId")
    suspend fun updateFacebookIdentity(profileId: Long, fbUserId: String, statusTag: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM profile_history WHERE profileId = :profileId ORDER BY visitedAt DESC LIMIT 200")
    fun observeForProfile(profileId: Long): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM profile_history ORDER BY visitedAt DESC")
    suspend fun getAllOnce(): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<HistoryEntity>)

    @Query("DELETE FROM profile_history WHERE profileId = :profileId")
    suspend fun clearForProfile(profileId: Long)
}

@Dao
interface CookieSnapshotDao {
    @Query("SELECT * FROM cookie_snapshots WHERE profileId = :profileId LIMIT 1")
    suspend fun getForProfile(profileId: Long): CookieSnapshotEntity?

    @Query("SELECT * FROM cookie_snapshots")
    suspend fun getAllOnce(): List<CookieSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: CookieSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(snapshots: List<CookieSnapshotEntity>)

    @Query("DELETE FROM cookie_snapshots WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)
}
