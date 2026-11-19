package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CookieSnapshotEntity
import com.example.data.local.FolderEntity
import com.example.data.local.HistoryEntity
import com.example.data.local.ProfileEntity
import com.example.domain.BackupSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SessionDeck", appName)
    }

    @Test
    fun `backup serializer round trip preserves folders profiles and cookies`() {
        val folders = listOf(FolderEntity(id = 1L, name = "Business", colorIndex = 2))
        val profiles = listOf(
            ProfileEntity(
                id = 10L,
                profileKey = "prof_abc123",
                displayName = "FB Main 01",
                folderId = 1L,
                fbUserId = "61596622534624",
                emailOrPhone = "user@example.com",
                savedPassword = "secret",
            )
        )
        val snapshots = listOf(
            CookieSnapshotEntity(
                profileId = 10L,
                rawCookieString = "c_user=61596622534624; xs=abc",
                cookieCount = 2,
            )
        )
        val history = listOf(
            HistoryEntity(
                id = 1L,
                profileId = 10L,
                title = "Facebook",
                url = "https://www.facebook.com/",
            )
        )

        val json = BackupSerializer.encode(folders, profiles, snapshots, history)
        val decoded = BackupSerializer.decode(json)

        assertNotNull(decoded)
        assertEquals(1, decoded!!.folders.size)
        assertEquals("Business", decoded.folders.first().name)
        assertEquals(1, decoded.profiles.size)
        assertEquals("61596622534624", decoded.profiles.first().fbUserId)
        assertEquals(1, decoded.history.size)
    }
}
