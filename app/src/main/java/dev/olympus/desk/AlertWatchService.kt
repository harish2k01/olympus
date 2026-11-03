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
 private val manager get()=getSystemService(NotificationManager::class.java)
 private fun content(id:String="")=PendingIntent.getActivity(this,id.hashCode(),Intent(this,MainActivity::class.java).putExtra("eventId",id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 private fun ongoing(text:String):Notification{
  val stop=PendingIntent.getService(this,1,Intent(this,AlertWatchService::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE)
  return Notification.Builder(this,"watch").setSmallIcon(R.drawable.ic_stat_olympus).setContentTitle("Olympus live alert watch").setContentText(text).setContentIntent(content()).setOngoing(true).setOnlyAlertOnce(true).addAction(Notification.Action.Builder(null,"Stop",stop).build()).build()
 }
 override fun onCreate(){super.onCreate();manager.createNotificationChannel(NotificationChannel("watch","Live alert connection",NotificationManager.IMPORTANCE_LOW));manager.createNotificationChannel(NotificationChannel("incidents","Cluster incidents",NotificationManager.IMPORTANCE_DEFAULT))}
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
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
    try{
     LiveRepository.stream(pair).collect{frame->
      LiveRepository.applyFrame(frame);retry=1000
      val events=(LiveRepository.active+LiveRepository.events).distinctBy{it.id}
      val sources=prefs.getStringSet("notificationSources",null)
      val quiet=prefs.getBoolean("quietHours",false)&&quietHour(LocalTime.now().hour,prefs.getInt("quietStart",22),prefs.getInt("quietEnd",7))
      if(seeded&&!quiet){
       val eligible=alertCandidates(events,seen,prefs.getBoolean("appNotifications",true),prefs.getBoolean("recoveryNotifications",false)).filter{sources==null||it.source in sources}
       eligible.take(10).forEach{event->
        val detail=listOf(event.node,event.namespace,event.pod).filter{it.isNotBlank()}.joinToString(" · ")
        val notification=Notification.Builder(this@AlertWatchService,"incidents").setSmallIcon(R.drawable.ic_stat_olympus).setContentTitle(incidentTitle(event)).setContentText("${event.source} · ${event.state}"+if(detail.isBlank())""else" · $detail").setContentIntent(content(event.id)).setAutoCancel(true).setOnlyAlertOnce(true).setGroup("olympus-incidents").build()
        manager.notify(event.id.hashCode(),notification)
       }
       if(eligible.size>10)manager.notify(42,Notification.Builder(this@AlertWatchService,"incidents").setSmallIcon(R.drawable.ic_stat_olympus).setContentTitle("${eligible.size} cluster updates").setContentText("${eligible.size-10} additional updates · open Inbox").setContentIntent(content("-inbox-")).setAutoCancel(true).setGroup("olympus-incidents").setGroupSummary(true).build())
      }
      events.forEach{seen[it.id]=it.state+"|"+it.kind.lowercase()};if(seen.size>2500){val keep=events.map{it.id}.toSet();seen.keys.retainAll(keep)};seeded=true
      manager.notify(41,ongoing(if(LiveRepository.state.contains("Partial"))"Connected · some sources unavailable"else"Connected · ${LiveRepository.active.count{it.state=="Firing"}} firing"))
      withContext(Dispatchers.IO){try{val file=java.io.File(filesDir,"last-frame.tmp");file.writeText(frame);file.renameTo(java.io.File(filesDir,"last-frame.json"))}catch(_:Exception){}}
     }
    }catch(e:CancellationException){throw e}catch(_:Exception){LiveRepository.unavailable()}
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
