package dev.olympus.desk

import android.app.*
import android.content.*
import android.os.IBinder
import dev.olympus.desk.data.*
import kotlinx.coroutines.*
import java.time.LocalTime

/** User-enabled local alert watch, not a cloud push transport. */
class AlertWatchService:Service(){
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private var watcher:Job?=null
 private val prefs get()=getSharedPreferences("home-v3",0)
 private val incidentMutes get() = getSharedPreferences("incident-mutes", MODE_PRIVATE)
 private val manager get()=getSystemService(NotificationManager::class.java)
 private fun content(id:String="")=PendingIntent.getActivity(this,id.hashCode(),Intent(this,MainActivity::class.java).putExtra("eventId",id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 private fun ongoing(text:String):Notification{
  val stop=PendingIntent.getService(this,1,Intent(this,AlertWatchService::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE)
  return Notification.Builder(this,"watch").setSmallIcon(R.drawable.ic_stat_olympus).setContentTitle("Olympus live alert watch").setContentText(text).setContentIntent(content()).setOngoing(true).setOnlyAlertOnce(true).addAction(Notification.Action.Builder(null,"Stop",stop).build()).build()
 }
 override fun onCreate() {
  super.onCreate()
  ensureOlympusNotificationChannels(this)
 }
 private fun channel(event: LabEvent) = when {
  event.state == "Resolved" || event.state == "Recovery" -> "resolved"
  event.state == "Firing" && event.kind.equals("critical", ignoreCase = true) -> "critical"
  event.state == "Firing" -> "warning"
  else -> "notices"
 }
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
  if (intent?.action == "silence") {
   val id = intent.data?.lastPathSegment
   if (!id.isNullOrBlank()) {
    incidentMutes.edit().putLong(id, System.currentTimeMillis() + 3_600_000L).apply()
    manager.cancel(id.hashCode())
   }
   if (watcher?.isActive != true) stopSelf(startId)
   return START_NOT_STICKY
  }
  if(intent?.action=="stop"||!prefs.getBoolean("backgroundAlerts",false)){prefs.edit().putBoolean("backgroundAlerts",false).apply();stopSelf();return START_NOT_STICKY}
  startForeground(41,ongoing("Connecting to your cluster"))
  if(watcher?.isActive==true)return START_NOT_STICKY
  watcher=scope.launch{
   var seeded=false
   val seen=mutableMapOf<String,String>()
   var retry=1000L
   while(isActive&&prefs.getBoolean("backgroundAlerts",false)){
    val pair=Pairing.read(this@AlertWatchService)
    if(pair==null){manager.notify(41,ongoing("Pairing required"));delay(5000);continue}
    LiveRepository.reconnecting()
    try{
     LiveRepository.stream(pair).collect{frame->
      LiveRepository.applyFrame(frame);OlympusStatusWidget.publish(this@AlertWatchService);retry=1000
      val events=(LiveRepository.active+LiveRepository.events).distinctBy{it.id}
      val sources=prefs.getStringSet("notificationSources",null)
      val quiet=prefs.getBoolean("quietHours",false)&&quietHour(LocalTime.now().hour,prefs.getInt("quietStart",22),prefs.getInt("quietEnd",7))
      val now = System.currentTimeMillis()
      val mutes = incidentMutes.all
      val expired = mutes.filterValues { (it as? Long ?: 0L) <= now }.keys
      if (expired.isNotEmpty()) {
       val edit = incidentMutes.edit()
       expired.forEach { edit.remove(it) }
       edit.apply()
      }
      if(seeded){
       val eligible=alertCandidates(events,seen,apps=true,recoveries=prefs.getBoolean("recoveryNotifications",false)).filter { event ->
        val channel = channel(event)
        val enabled = when (channel) {
         "critical" -> prefs.getBoolean("criticalNotifications", true)
         "warning" -> prefs.getBoolean("warningNotifications", true)
         "notices" -> prefs.getBoolean("appNotifications", true)
         else -> true
        }
        enabled && (sources == null || event.source in sources) && shouldNotifyNow(quiet, incidentIsSilenced(event.id, mutes, now), channel == "critical")
       }
       eligible.take(10).forEach{event->
        val detail=listOf(event.node,event.namespace,event.pod).filter{it.isNotBlank()}.joinToString(" · ")
        val silence = PendingIntent.getService(this@AlertWatchService, 2,
                  Intent(this@AlertWatchService, AlertWatchService::class.java).setAction("silence")
          .setData(android.net.Uri.Builder().scheme("olympus").authority("incident").appendPath(event.id).build()),
         PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(this@AlertWatchService,channel(event))
         .addAction(Notification.Action.Builder(null,"Open",content(event.id)).build())
         .addAction(Notification.Action.Builder(null,"Silence 1 h",silence).build()).setSmallIcon(R.drawable.ic_stat_olympus).setContentTitle(incidentTitle(event)).setContentText("${event.source} · ${event.state}"+if(detail.isBlank())""else" · $detail").setContentIntent(content(event.id)).setAutoCancel(true).setOnlyAlertOnce(true).setGroup("olympus-incidents").build()
        manager.notify(event.id.hashCode(),notification)
       }
       if(eligible.size>10 && !quiet && prefs.getBoolean("appNotifications",true))manager.notify(42,Notification.Builder(this@AlertWatchService,"notices").setSmallIcon(R.drawable.ic_stat_olympus).setContentTitle("${eligible.size} cluster updates").setContentText("${eligible.size-10} additional updates · open Inbox").setContentIntent(content("-inbox-")).setAutoCancel(true).setGroup("olympus-incidents").setGroupSummary(true).build())
      }
      events.forEach{seen[it.id]=it.state+"|"+it.kind.lowercase()};if(seen.size>2500){val keep=events.map{it.id}.toSet();seen.keys.retainAll(keep)};seeded=true
      manager.notify(41,ongoing(if(LiveRepository.state.contains("Partial"))"Connected · some sources unavailable"else"Connected · ${LiveRepository.active.count{it.state=="Firing"}} firing"))
      withContext(Dispatchers.IO) { LiveRepository.cacheFrame(this@AlertWatchService, frame) }
     }
    }catch(e:CancellationException){throw e}catch(_:Exception){ /* Reconnect below. */ }
    LiveRepository.unavailable()
    OlympusStatusWidget.publish(this@AlertWatchService)
    manager.notify(41,ongoing("Disconnected · retrying"));LiveRepository.awaitRetry(retry);retry=(retry*2).coerceAtMost(30000)
   }
  }
  return START_NOT_STICKY
 }
 override fun onDestroy(){scope.cancel();super.onDestroy()}
 override fun onBind(intent:Intent?):IBinder?=null
}
internal fun alertCandidates(events:List<LabEvent>,seen:Map<String,String>,apps:Boolean,recoveries:Boolean)=events.filter{event->
 event.id.isNotBlank()&&event.id!=""&&seen[event.id]!=event.state&&seen[event.id]!=event.state+"|"+event.kind.lowercase()&&event.source!="Test"&&event.kind!="Test"&&when(event.state){"Firing"->!isInformational(event.kind);"Resolved"->recoveries&&seen[event.id]?.substringBefore('|')=="Firing";"Notification","Activity"->apps;"Recovery"->apps&&recoveries;"Observed"->apps&&event.source !in listOf("Kubernetes","Prometheus","Argo CD")&&!isInformational(event.kind);else->false}
}

internal fun quietHour(hour:Int,start:Int,end:Int)=if(start==end)false else if(start<end)hour in start until end else hour>=start||hour<end

internal fun ensureOlympusNotificationChannels(context: Context) {
 val manager = context.getSystemService(NotificationManager::class.java)
 manager.createNotificationChannels(listOf(
  NotificationChannel("watch", "Live alert connection", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
  NotificationChannel("critical", "Critical incidents", NotificationManager.IMPORTANCE_HIGH).apply {
   lockscreenVisibility = Notification.VISIBILITY_PRIVATE
  },
  NotificationChannel("warning", "Warnings", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(true) },
  NotificationChannel("notices", "Notices", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
  NotificationChannel("resolved", "Resolved incidents", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
 ))
}

internal fun shouldNotifyNow(quiet: Boolean, silenced: Boolean, critical: Boolean): Boolean =
 !silenced && (!quiet || critical)

internal fun incidentIsSilenced(id: String, mutes: Map<String, *>, now: Long): Boolean =
 (mutes[id] as? Long ?: 0L) > now
