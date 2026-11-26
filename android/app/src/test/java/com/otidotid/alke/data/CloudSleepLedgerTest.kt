package com.otidotid.alke.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.otidotid.alke.ai.CloudSleepProtocol
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class CloudSleepLedgerTest {
    private lateinit var db: WhoopDatabase
    private lateinit var repo: WhoopRepository
    private val device = "my-whoop"
    private val start = 1800000000L
    private val snapshot = CloudSleepProtocol.Snapshot(start, List(24) {
        listOf<Number?>(65, 0.02, null, 1, 1, 0)
    }, 420, null, emptyList())
    private val ledger get() = requireNotNull(repo.cloudSleep)

    @Before fun setup() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, WhoopDatabase::class.java).allowMainThreadQueries().build()
        repo = WhoopRepository(db, context)
    }
    @After fun close() = db.close()

    private fun episode(type: String, a: Int, b: Int) =
        CloudSleepProtocol.Episode("${type}-$a-$b", type, start + a * 300, start + b * 300)
    private suspend fun save(value: CloudSleepProtocol.Episode) =
        ledger.save(device, snapshot, CloudSleepProtocol.Response("COMPLETE", listOf(value)))

    @Test fun laterMainReplacesOnlyAnEnclosedUneditedCloudNap() = runBlocking {
        val nap = episode("NAP", 4, 6)
        val main = episode("MAIN_SLEEP", 2, 10)
        ledger.configure(device, true, start)
        assertEquals(1, save(nap))
        assertEquals(0, save(nap))
        db.whoopDao().insertSleepSession(SleepSession("$device-alke", nap.start, nap.end))
        assertEquals(1, save(main))
        val restored = WhoopRepository(db).cloudSleep!!.history(device, start, snapshot.end)
        assertEquals(listOf(main), restored.episodes)
        assertNull(db.whoopDao().sleepSession("$device-alke", nap.start))
        assertEquals(0, save(nap))
    }

    @Test fun preserveManualEditAndRespectRevocationAndDeviceIdentity() = runBlocking {
        val nap = episode("NAP", 4, 6)
        ledger.configure(device, true, start)
        assertEquals(1, save(nap))
        db.whoopDao().insertSleepSession(SleepSession("$device-alke", nap.start, nap.end, userEdited = true))
        assertEquals(0, save(episode("MAIN_SLEEP", 2, 10)))
        assertTrue(db.whoopDao().sleepSession("$device-alke", nap.start)!!.userEdited)
        ledger.configure(device, false, start + 9 * 300)
        assertEquals(0, save(episode("NAP", 10, 12)))
        assertTrue(ledger.history("other", start, snapshot.end).episodes.isEmpty())
    }
}
