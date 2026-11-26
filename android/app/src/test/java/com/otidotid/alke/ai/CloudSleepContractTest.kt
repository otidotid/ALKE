package com.otidotid.alke.ai

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
class CloudSleepContractTest {
    private val start=1_800_000_000L
    private val bin=listOf<Number?>(60,0.02,45,1.0,1.0,0.9)
    private val snapshot=CloudSleepProtocol.Snapshot(start,List(12){bin},420,10800L,emptyList())
    private fun response(type:String="NAP",a:Long=start+600,b:Long=start+1200,confidence:String="HIGH",
                         snap:CloudSleepProtocol.Snapshot=snapshot):String =
        JSONObject().put("sleepDetection",JSONObject()
            .put("schemaVersion",CloudSleepProtocol.SCHEMA).put("requestId",snap.body().getString("requestId"))
            .put("startTs",snap.start).put("endTs",snap.end).put("model","openai/gpt-oss-20b")
            .put("status","COMPLETE").put("episodes",JSONArray().put(JSONObject()
                .put("type",type).put("startTs",a).put("endTs",b)
                .put("confidence",confidence).put("source","cloud-ai")
                .put("id",CloudActivityProtocol.sha256("${CloudSleepProtocol.SCHEMA}:$a:$b:$type"))))).toString()
    @Test fun acceptsOnlySupportedCompletedEpisodes() {
        for(type in CloudSleepProtocol.types) {
            assertEquals(type,CloudSleepProtocol.parse(response(type),snapshot,
                snapshot.body().getString("requestId"))!!.episodes.single().type)
        }
        assertNull(CloudSleepProtocol.parse(response(a=start),snapshot,snapshot.body().getString("requestId")))
        assertNull(CloudSleepProtocol.parse(response(confidence="MODERATE"),snapshot,snapshot.body().getString("requestId")))
    }
    @Test fun rejectsMissingEvidenceOrExclusion() {
        val excluded=snapshot.copy(excluded=listOf(start+600 to start+900))
        assertNull(CloudSleepProtocol.parse(response(snap=excluded),excluded,excluded.body().getString("requestId")))
        val missing=snapshot.copy(bins=snapshot.bins.mapIndexed{i,b->if(i==2)listOf(null,null,null,0,0,0) else b})
        assertNull(CloudSleepProtocol.parse(response(snap=missing),missing,missing.body().getString("requestId")))
    }
    @Test fun noConsentNeverContactsCloud()=runBlocking {
        assertEquals(403,CloudSleepClient(base="https://example.invalid").detect(snapshot,"x".repeat(32),false).code)
    }
}
