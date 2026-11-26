package com.otidotid.alke.ai

import com.otidotid.alke.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Cloud sleep transport contract. No classifier, stage inference or scoring on the phone.
 * A separate consent must be granted by the caller before constructing/sending a snapshot. */
internal object CloudSleepProtocol {
    const val SCHEMA = "sleep-detection-v1"
    const val BIN = 300L
    const val MAX_BINS = 288
    val types = setOf("MAIN_SLEEP","DAY_SLEEP","NAP","UNCLASSIFIED_SLEEP")
    data class Episode(val id:String,val type:String,val start:Long,val end:Long)
    data class Snapshot(val start:Long,val bins:List<List<Number?>>,val offsetMinutes:Int,
        val habitualMidsleepSec:Long?,val excluded:List<Pair<Long,Long>>) {
        val end get()=start+bins.size*BIN
        fun body()=JSONObject().apply {
            put("schemaVersion",SCHEMA)
            put("sleepConsent",true)
            put("startTs",start)
            put("offsetMinutes",offsetMinutes)
            put("habitualMidsleepSec",habitualMidsleepSec ?: JSONObject.NULL)
            put("bins",JSONArray(bins.map { JSONArray(it.map { v -> v ?: JSONObject.NULL }) }))
            put("excludedIntervals",JSONArray(excluded.map { JSONArray(listOf(it.first,it.second)) }))
            put("requestId",CloudActivityProtocol.sha256(toString()))
        }
    }
    data class Response(val status:String,val episodes:List<Episode>)
    fun usable(b:List<Number?>):Boolean = b.size==6 && b[0]!=null && b[1]!=null &&
        (b[3]?.toDouble() ?: 0.0)>=0.7 && (b[4]?.toDouble() ?: 0.0)>=0.7
    fun parse(raw:String,expected:Snapshot,requestId:String):Response?=runCatching {
        val obj=JSONObject(raw).getJSONObject("sleepDetection")
        require(obj.getString("schemaVersion")==SCHEMA && obj.getString("requestId")==requestId)
        require(obj.getLong("startTs")==expected.start && obj.getLong("endTs")==expected.end)
        require(obj.getString("model") in setOf("openai/gpt-oss-20b","openai/gpt-oss-120b"))
        val status=obj.getString("status")
        require(status in setOf("COMPLETE","INSUFFICIENT_DATA"))
        val arr=obj.getJSONArray("episodes")
        require(arr.length()<=12 && (status=="COMPLETE" || arr.length()==0))
        fun usableIndex(i:Int):Boolean {
            if(i !in expected.bins.indices || !usable(expected.bins[i]))return false
            val start=expected.start+i*BIN
            return expected.excluded.none { CloudActivityProtocol.overlap(start,start+BIN,it.first,it.second) }
        }
        val episodes=(0 until arr.length()).map { i ->
            val v=arr.getJSONObject(i)
            val type=v.getString("type")
            val a=v.getLong("startTs");val b=v.getLong("endTs")
            require(type in types && v.getString("confidence")=="HIGH" && v.getString("source")=="cloud-ai")
            require(a>expected.start && b<expected.end && b>a && a%BIN==0L && b%BIN==0L)
            val first=((a-expected.start)/BIN).toInt()
            val end=((b-expected.start)/BIN).toInt()
            require((first-1..end).all(::usableIndex))
            val id=CloudActivityProtocol.sha256("$SCHEMA:$a:$b:$type")
            require(v.getString("id")==id)
            Episode(id,type,a,b)
        }.sortedBy { it.start }
        require(episodes.zipWithNext().none { (a,b)->a.end>b.start })
        Response(status,episodes)
    }.getOrNull()
}
internal class CloudSleepClient(
    private val base:String=BuildConfig.CLOUD_COACH_BASE_URL,
    private val http:OkHttpClient=OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS)
        .readTimeout(40,TimeUnit.SECONDS).writeTimeout(8,TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build(),
) {
    data class Outcome(val response:CloudSleepProtocol.Response?=null,val code:Int=0)
    suspend fun detect(snapshot:CloudSleepProtocol.Snapshot,token:String,consent:Boolean):Outcome=withContext(Dispatchers.IO) {
        if(!consent)return@withContext Outcome(code=403)
        val url=(base.trimEnd('/')+"/v1/sleep/detect").toHttpUrlOrNull()
        if(url==null||!url.isHttps||url.username.isNotEmpty()||url.password.isNotEmpty()||
            url.query!=null||url.fragment!=null||token.length<32)return@withContext Outcome(code=400)
        try {
            val body=snapshot.body()
            val req=Request.Builder().url(url).header("Authorization","Bearer $token")
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
            http.newCall(req).execute().use { res->
                if(!res.isSuccessful)return@use Outcome(code=res.code)
                Outcome(CloudSleepProtocol.parse(res.body?.string().orEmpty(),snapshot,body.getString("requestId")),res.code)
            }
        }catch(e:CancellationException){throw e}catch(_:Exception){Outcome(code=503)}
    }
}
