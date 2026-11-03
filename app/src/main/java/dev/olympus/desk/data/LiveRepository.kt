package dev.olympus.desk.data

import android.content.Context
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.compose.runtime.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

/** Local pairing token is encrypted with a non-exportable Android Keystore key. */
object Pairing {
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey("olympus-pair",null) as? SecretKey)?.let{return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder("olympus-pair",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()
    }
    fun save(context:Context,url:String,token:String){
        require(url.startsWith("https://")) {"HTTPS required"}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,key())}
        val encoded=Base64.encodeToString(cipher.iv+cipher.doFinal(token.toByteArray()),Base64.NO_WRAP)
        context.getSharedPreferences("pairing",0).edit().putString("url",url.trimEnd('/')).putString("token",encoded).apply()
    }
    fun read(context:Context):Pair<String,String>?=try{
        val prefs=context.getSharedPreferences("pairing",0);val encrypted=prefs.getString("token",null)
        if(encrypted==null)null else {val bytes=Base64.decode(encrypted,Base64.NO_WRAP);val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,key(),javax.crypto.spec.GCMParameterSpec(128,bytes.copyOfRange(0,12)))};Pair(prefs.getString("url","")!!,String(cipher.doFinal(bytes.copyOfRange(12,bytes.size))))}
    }catch(_:Exception){null}
}

data class ServiceState(val name:String,val namespace:String,val health:String,val sync:String,val cpu:Float?=null,val memory:Float?=null,val pods:Int=0,val ready:Int=0,val icon:String="",val url:String="",val resources:List<String> = emptyList(),val lastSync:String="")
data class MonitorState(val id:String,val name:String,val type:String,val status:String,val responseMs:Float?,val uptime24h:Float?=null)
data class QueueEntry(val title: String, val status: String, val progress: Double?)
data class ServiceHealth(val source: String, val type: String, val message: String, val wikiUrl: String)
data class ServiceWidgetState(val name:String,val kind:String,val fields:Map<String,Float?>,val time:String,val error:String,val health:List<ServiceHealth>?=null,val queue:List<QueueEntry>?=null)
data class HistoryPolicy(val days: Int, val maxRecords: Int, val persistent: Boolean)
data class SourceDelivery(val name:String,val count:Int,val lastReceived:String)

data class AttentionItem(val title:String,val target:String,val severity:String)
object LiveRepository {
    var deliveries by mutableStateOf(emptyList<SourceDelivery>());private set
    var deliveryError by mutableStateOf(false);private set
    private var deliveryTime=0L
    suspend fun loadDeliveries(pair:Pair<String,String>,force:Boolean=false){if(!force&&System.currentTimeMillis()-deliveryTime<30000)return;try{val page=withContext(Dispatchers.IO){JSONObject(request(pair,"/v1/events/sources"))};val list=page.getJSONArray("sources");deliveries=(0 until list.length()).map{val row=list.getJSONObject(it);SourceDelivery(row.getString("name"),row.getInt("count"),row.optString("lastReceived"))};deliveryError=false;deliveryTime=System.currentTimeMillis()}catch(e:CancellationException){throw e}catch(_:Exception){deliveryError=true}}

    var active by mutableStateOf(emptyList<LabEvent>());private set
    var integrations by mutableStateOf(emptyList<ServiceWidgetState>());private set
    var signals by mutableStateOf(mapOf<String,Boolean>());private set
    fun event(e:JSONObject)=LabEvent(e.getString("source"),e.getString("title"),e.optString("detail"),e.optString("kind"),e.getString("id"),e.optString("time"),e.optString("receivedAt"),e.optString("state","Observed"),e.optString("resolvedAt"),e.optString("lastSeen"),e.optString("evidence"),e.optString("resource"),e.optString("namespace"),e.optString("pod"),e.optString("container"),e.optString("node"),e.optString("nodeSource"),e.optJSONObject("labels")?.let{j->j.keys().asSequence().associateWith{j.optString(it)}}?:emptyMap(),e.optString("cpuLimit"),if(e.isNull("throttledPeriodsPercent"))null else e.optDouble("throttledPeriodsPercent").toFloat(),e.optString("nextStep"))
    private var legacyIssues by mutableStateOf(emptyList<AttentionItem>())
    val issues:List<AttentionItem> get()=mergeAttention(legacyIssues,active)
    var recentActivity by mutableStateOf<Map<String,Int>?>(null);private set
    var recentActivityWithTests by mutableStateOf<Map<String,Int>?>(null);private set
    var history by mutableStateOf(mapOf<String,List<Float>>());private set
    var nodeDetails by mutableStateOf<JSONObject?>(null);private set
    var nodeError by mutableStateOf(false);private set
    var appDetails by mutableStateOf<JSONObject?>(null);private set
    var appError by mutableStateOf(false);private set
    suspend fun loadApp(pair:Pair<String,String>,name:String){appDetails=null;appError=false;try{appDetails=withContext(Dispatchers.IO){JSONObject(request(pair,"/v1/apps/"+java.net.URLEncoder.encode(name,"UTF-8")))}}catch(e:CancellationException){throw e}catch(_:Exception){appError=true}}
    fun record(key:String,value:Float?){if(value!=null)history=history+(key to ((history[key]?:emptyList())+value).takeLast(60))}
    suspend fun loadNode(pair:Pair<String,String>,name:String){nodeDetails=null;nodeError=false;try{nodeDetails=withContext(Dispatchers.IO){JSONObject(request(pair,"/v1/nodes/"+java.net.URLEncoder.encode(name,"UTF-8")))}}catch(e:CancellationException){throw e}catch(_:Exception){nodeError=true}}
    fun stream(pair:Pair<String,String>)=callbackFlow<String>{
        val connection=URL(pair.first+"/v1/stream").openConnection() as HttpURLConnection
        val reader=launch(Dispatchers.IO){try{connection.instanceFollowRedirects=false;connection.connectTimeout=7000;connection.readTimeout=25000;connection.setRequestProperty("Authorization","Bearer "+pair.second);connection.setRequestProperty("Accept","text/event-stream");require(connection.responseCode==200);connection.inputStream.bufferedReader().use{input->while(isActive){val line=input.readLine()?:break;if(line.startsWith("data: ")){if(!trySend(line.removePrefix("data: ")).isSuccess)break}}};close()}catch(e:Exception){close(e)}finally{connection.disconnect()}}
        awaitClose{connection.disconnect();reader.cancel()}
    }
    var nodes by mutableStateOf(emptyList<NodeMetric>());private set
    var events by mutableStateOf(emptyList<LabEvent>());private set
    var services by mutableStateOf(emptyList<ServiceState>());private set
    var state by mutableStateOf("Not paired");private set
    var time by mutableStateOf("");private set
    var dashboardLinks by mutableStateOf(emptyMap<String, String>());private set
    var historyPolicy by mutableStateOf<HistoryPolicy?>(null);private set
    var observedAt by mutableStateOf(emptyMap<String, String>());private set
    var lastSuccess by mutableLongStateOf(0);private set
    var download by mutableStateOf<Float?>(null);private set
    var backupVolumes by mutableStateOf<Int?>(null);private set
    var backupRecorded by mutableStateOf<Int?>(null);private set
    var backupLast by mutableStateOf("");private set
    private val cacheLock = Any()
    private const val maxCachedBytes = 3 * 1024 * 1024

    internal fun cachedFrame(context: Context): String? = synchronized(cacheLock) {
        runCatching {
            val file = android.util.AtomicFile(java.io.File(context.filesDir, "last-frame.json"))
            file.openRead().use { input ->
                if (input.channel.size() <= maxCachedBytes) input.bufferedReader().readText() else null
            }
        }.getOrNull()
    }

    internal fun cacheFrame(context: Context, frame: String) = synchronized(cacheLock) {
        val bytes = frame.encodeToByteArray()
        if (bytes.size > maxCachedBytes) return@synchronized
        val file = android.util.AtomicFile(java.io.File(context.filesDir, "last-frame.json"))
        val stream = runCatching { file.startWrite() }.getOrNull() ?: return@synchronized
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (_: Exception) {
            file.failWrite(stream)
        }
    }

    internal fun clearCachedFrame(context: Context) = synchronized(cacheLock) {
        android.util.AtomicFile(java.io.File(context.filesDir, "last-frame.json")).delete()
    }

    fun restore(frame:String){applyFrame(frame);lastSuccess=try{java.time.Instant.parse(time).toEpochMilli()}catch(_:Exception){0};state="Cached"}
    var backup by mutableStateOf<Boolean?>(null);private set
    var monitors by mutableStateOf(emptyList<MonitorState>());private set
    var monitorsUp by mutableStateOf<Int?>(null);private set
    var monitorsTotal by mutableStateOf<Int?>(null);private set
    val cpuPercent:Float? get()=if(nodes.isEmpty()||nodes.any{!it.available})null else DemoRepository.cpuPercent(nodes)
    val memoryPercent:Float? get()=if(nodes.isEmpty()||nodes.any{!it.available})null else DemoRepository.memoryPercent(nodes)
    val totalCores:Int get()=nodes.sumOf{it.cores}
    val totalMemory:Float? get()=if(nodes.isEmpty()||nodes.any{!it.available})null else nodes.sumOf{it.memoryTotalGiB.toDouble()}.toFloat()
    val usedMemory:Float? get()=if(nodes.isEmpty()||nodes.any{!it.available})null else nodes.sumOf{it.memoryUsedGiB.toDouble()}.toFloat()
    fun request(pair:Pair<String,String>,path:String):String {
        val connection=URL(pair.first+path).openConnection() as HttpURLConnection
        try{connection.instanceFollowRedirects=false;connection.connectTimeout=7000;connection.readTimeout=12000;connection.setRequestProperty("Authorization","Bearer "+pair.second);require(connection.responseCode==200){"Backend unavailable"};return connection.inputStream.bufferedReader().use{it.readText()}}finally{connection.disconnect()}
    }
    internal fun requestIcon(pair: Pair<String, String>, name: String): ByteArray {
        val encoded = java.net.URLEncoder.encode(name, "UTF-8")
        val connection = URL(pair.first + "/v1/icons?name=" + encoded).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 4000
            connection.readTimeout = 7000
            connection.setRequestProperty("Authorization", "Bearer " + pair.second)
            require(connection.responseCode == 200 && connection.contentType == "image/png")
            return connection.inputStream.use { input ->
                val limit = 512 * 1024
                val bytes = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val size = input.read(buffer)
                    if (size < 0) break
                    require(bytes.size() + size <= limit)
                    bytes.write(buffer, 0, size)
                }
                bytes.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    fun fetch(pair:Pair<String,String>):Pair<String,String> = request(pair,"/v1/snapshot") to request(pair,"/v1/events")
    fun apply(payload:Pair<String,String>){
        val root=JSONObject(payload.first);val list=root.getJSONArray("nodes")
        require(list.length()>0){"Cluster data unavailable"}
        val newSample=root.getString("time")!=time
        nodes=(0 until list.length()).map{val n=list.getJSONObject(it);NodeMetric(n.getString("name"),n.getString("role"),n.getInt("cores"),n.optDouble("cpuPercent",0.0).toFloat(),n.optDouble("memoryUsedGiB",0.0).toFloat(),n.optDouble("memoryTotalGiB",0.0).toFloat(),!n.isNull("cpuPercent")&&!n.isNull("memoryUsedGiB")&&!n.isNull("memoryTotalGiB"),n.optBoolean("ready"))}
        val apps=root.getJSONArray("apps");services=(0 until apps.length()).map{val a=apps.getJSONObject(it);ServiceState(a.getString("name"),a.getString("namespace"),a.getString("health"),a.getString("sync"),if(a.isNull("cpuCores"))null else a.getDouble("cpuCores").toFloat(),if(a.isNull("memoryMiB"))null else a.getDouble("memoryMiB").toFloat(),a.optInt("pods"),a.optInt("ready"),a.optString("icon"),a.optString("url"),(a.optJSONArray("resources")?:org.json.JSONArray()).let{rs->(0 until rs.length()).map{rs.getJSONObject(it).optString("kind")}},a.optString("lastSync"))}
        fun counts(key:String)=root.optJSONObject(key)?.let{j->j.keys().asSequence().associateWith{j.optInt(it)}}
        recentActivity=counts("recentActivity");recentActivityWithTests=counts("recentActivityWithTests")
        val ongoing=root.optJSONArray("active")?:org.json.JSONArray();active=(0 until ongoing.length()).map{event(ongoing.getJSONObject(it))}
        val sources=root.optJSONObject("signals")?:JSONObject();signals=sources.keys().asSequence().filter{!it.startsWith("resource:")}.associateWith{sources.optBoolean(it)}
        val integrationsJSON=root.optJSONArray("integrations")?:org.json.JSONArray();integrations=(0 until integrationsJSON.length()).map{val i=integrationsJSON.getJSONObject(it);val f=i.optJSONObject("fields")?:JSONObject();ServiceWidgetState(i.getString("name"),i.getString("kind"),f.keys().asSequence().associateWith{key->if(f.isNull(key))null else f.optDouble(key).toFloat()},i.optString("time"),i.optString("error"),i.optJSONArray("health")?.let { health -> (0 until health.length()).mapNotNull { index -> health.optJSONObject(index)?.let { item -> ServiceHealth(item.optString("source"),item.optString("type"),item.optString("message"),item.optString("wikiUrl")) } } },i.optJSONArray("queue")?.let { queue -> (0 until queue.length()).mapNotNull { index -> queue.optJSONObject(index)?.let { item -> QueueEntry(item.optString("title"), item.optString("status"), item.optDouble("progress").takeIf { it in 0.0..100.0 }) } } })}
        val alerts=root.optJSONArray("issues")?:org.json.JSONArray();legacyIssues=(0 until alerts.length()).map{val item=alerts.getJSONObject(it);AttentionItem(item.getString("title"),item.getString("target"),item.getString("severity"))}
        backupVolumes=if(root.isNull("backupVolumes"))null else root.optInt("backupVolumes");backupRecorded=if(root.isNull("backupRecorded"))null else root.optInt("backupRecorded");backupLast=root.optString("backupLast")
        download=if(root.isNull("downloadMiB"))null else root.getDouble("downloadMiB").toFloat();backup=if(root.isNull("backupAvailable"))null else root.getBoolean("backupAvailable")
        val checks=root.optJSONArray("monitors")?:org.json.JSONArray();monitors=(0 until checks.length()).map{val m=checks.getJSONObject(it);MonitorState(m.optString("id"),m.optString("name"),m.optString("type"),m.optString("status"),if(m.isNull("responseMs"))null else m.optDouble("responseMs").toFloat(),m.optDouble("uptime24h").toFloat().takeIf { it in 0f..1f })}
        monitorsUp=if(root.isNull("monitorsUp"))null else root.getInt("monitorsUp");monitorsTotal=if(root.isNull("monitorsTotal"))null else root.getInt("monitorsTotal")
        val rows=org.json.JSONArray(payload.second);events=(0 until rows.length()).map{val e=rows.getJSONObject(it);event(e)}
        observedAt = root.optJSONObject("observedAt")?.let { obj -> obj.keys().asSequence().associateWith { obj.optString(it) } }.orEmpty()
        dashboardLinks = root.optJSONObject("links")?.let { obj -> obj.keys().asSequence().associateWith { obj.optString(it) }.filterValues { it.startsWith("https://") } }.orEmpty()
        historyPolicy = root.optJSONObject("historyPolicy")?.let { policy ->
            HistoryPolicy(policy.optInt("days"), policy.optInt("maxRecords"), policy.optBoolean("persistent"))
                .takeIf { it.days > 0 && it.maxRecords > 0 }
        }
        time=root.getString("time");lastSuccess=System.currentTimeMillis();state=if(root.getJSONArray("errors").length()>0)"Live · Partial data" else "Live"
        if(newSample){monitors.forEach{record("monitor:${it.id}",it.responseMs)};record("cpu",cpuPercent);record("memory",memoryPercent);nodes.forEach{if(it.available){record("node:${it.name}",it.cpuPercent);if(it.memoryTotalGiB>0)record("node-memory:${it.name}",it.memoryUsedGiB/it.memoryTotalGiB*100)}};services.forEach{record("app:${it.name}:cpu",it.cpu);record("app:${it.name}:memory",it.memory)}}
    }
    fun applyFrame(frame:String){val root=JSONObject(frame);apply(root.getJSONObject("snapshot").toString() to root.getJSONArray("events").toString())}
    fun checkFreshness(){if(state.startsWith("Live")&&lastSuccess>0&&System.currentTimeMillis()-lastSuccess>45000)state="Stale"}
    private val retryRequests = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    fun retryNow() { retryRequests.trySend(Unit) }
    suspend fun awaitRetry(delayMillis: Long) {
        kotlinx.coroutines.withTimeoutOrNull(delayMillis) { retryRequests.receive() }
    }
    fun reconnecting() {
        if (!state.startsWith("Live")) state = if (lastSuccess == 0L) "Connecting…" else "Reconnecting…"
    }

    fun unavailable() {
        state = if (lastSuccess == 0L) "Offline" else "Offline · cached"
    }
}
