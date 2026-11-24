package com.otidotid.alke.ai

import android.app.Application
import androidx.room.Room
import com.otidotid.alke.data.WhoopDatabase
import com.otidotid.alke.data.WhoopRepository
import com.otidotid.alke.data.WorkoutRow
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],manifest=Config.NONE,application=Application::class)
class CloudActivityReconciliationTest {
    private lateinit var db: WhoopDatabase
    private lateinit var repo: WhoopRepository
    private val device = "my-whoop"
    private val start = 1_700_000_100L
    private val p = CloudActivityProtocol
    @Before fun setup() {
        db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),WhoopDatabase::class.java)
            .allowMainThreadQueries().build()
        repo=WhoopRepository(db)
    }
    @After fun close(){db.close()}
    private fun e(a:Long,b:Long,t:String,workout:Boolean) =
        p.Event(p.sha256("${p.SCHEMA}:$a:$b:$t"),t,a,b,workout)
    @Test fun delayedHealthConnectSupersedesOnlyCloudWorkout() = runBlocking {
        val cloud=e(start,start+600,"RUNNING",true)
        assertEquals(1,repo.saveCloudActivityEvents(device,listOf(cloud)))
        assertEquals(0,repo.saveCloudActivityEvents(device,listOf(cloud)))
        val manual=WorkoutRow(device,start+3600,start+4200,"Cycling","manual")
        db.whoopDao().upsertWorkouts(listOf(manual))
        val zepp=WorkoutRow("health-connect",start-30,start+630,"Running","health-connect")
        assertEquals(listOf(zepp),repo.saveCandidateWorkoutsNoOverlap(listOf(zepp)))
        assertEquals(emptyList<WorkoutRow>(),repo.detectedWorkoutsUnion(device,start-100,start+1000))
        assertEquals(listOf(zepp),repo.workouts("health-connect",start-100,start+1000))
        val collision=WorkoutRow("health-connect",manual.startTs,manual.endTs,"Cycling","health-connect")
        assertTrue(repo.saveCandidateWorkoutsNoOverlap(listOf(collision)).isEmpty())
        assertEquals(listOf(manual),repo.workouts(device,start+3500,start+4300))
    }
    @Test fun deliberateManualWorkoutSupersedesPredictionWithoutTouchingRealHistory()=runBlocking{
        val cloud=e(start,start+600,"RUNNING",true)
        assertEquals(1,repo.saveCloudActivityEvents(device,listOf(cloud)))
        val manual=WorkoutRow(device,start-30,start+630,"Cycling","manual")
        assertEquals(listOf(manual),repo.saveCandidateWorkoutsNoOverlap(listOf(manual)))
        assertTrue(repo.detectedWorkoutsUnion(device,start-100,start+1000).isEmpty())
        assertEquals(listOf(manual),repo.workouts(device,start-100,start+1000))
        val other=e(start+1800,start+2400,"RUNNING",true)
        assertEquals(1,repo.saveCloudActivityEvents(device,listOf(other)))
        val retro=WorkoutRow(device,start+1770,start+2430,"Strength training","manual")
        repo.saveManualWorkout(retro)
        assertTrue(repo.detectedWorkoutsUnion(device,start+1600,start+2600).isEmpty())
        assertEquals(listOf(retro),repo.workouts(device,start+1600,start+2600))
    }
    @Test fun dailyMovementNeverCreatesTrainingWorkoutAndLaterImportHidesIt()=runBlocking{
        val walking=e(start,start+180,"WALKING",false)
        assertEquals(1,repo.saveCloudActivityEvents(device,listOf(walking)))
        assertEquals(0,repo.saveCloudActivityEvents(device,listOf(walking)))
        assertEquals("Walking",repo.cloudDailyActivityRows(start-10,start+200).single().sport)
        assertTrue(db.whoopDao().workoutsAllSources(start-10,start+200,100).isEmpty())
        val zepp=WorkoutRow("health-connect",start-10,start+200,"Walking","health-connect")
        assertEquals(listOf(zepp),repo.saveCandidateWorkoutsNoOverlap(listOf(zepp)))
        assertTrue(repo.cloudDailyActivityRows(start-10,start+200).isEmpty())
    }
    @Test fun contiguousBatchesBecomeOneWorkoutAndDismissedMovementStaysHidden()=runBlocking{
        val a=e(start,start+600,"RUNNING",true)
        val b=e(start+600,start+1200,"RUNNING",true)
        assertEquals(1,repo.saveCloudActivityEvents(device,listOf(a)))
        assertEquals(1,repo.saveCloudActivityEvents(device,listOf(b)))
        val merged=repo.detectedWorkoutsUnion(device,start-1,start+1300).single()
        assertEquals(start+1200,merged.endTs)
        val daily=e(start+1800,start+1980,"WALKING",false)
        repo.saveCloudActivityEvents(device,listOf(daily))
        val row=repo.cloudDailyActivityRows(start+1700,start+2000).single()
        repo.dismissCloudDailyActivity(row)
        assertTrue(repo.cloudDailyActivityRows(start+1700,start+2000).isEmpty())
        assertEquals(0,repo.saveCloudActivityEvents(device,listOf(daily)))
    }
}
