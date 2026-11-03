package dev.olympus.desk

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.olympus.desk.data.*
import org.json.JSONObject
import kotlinx.coroutines.*

private val EventPaper=Color(0xFFF5F3EF)
private val EventMuted=Color(0xFFBFC4D1)
private val EventLilac=Color(0xFFCDC5FF)
private val EventWarm=Color(0xFFEDB48F)
private fun sourceApp(source:String)=LiveRepository.services.find{it.name.replace("-","").equals(source.replace(" ","").replace("-",""),true)}
private fun eventIcon(event:LabEvent)=sourceApp(event.source)?.icon?.ifBlank{null}?:when(event.source){"Kubernetes"->"https://raw.githubusercontent.com/selfhst/icons/main/png/kubernetes.png";"Prometheus"->"https://raw.githubusercontent.com/selfhst/icons/main/png/prometheus.png";else->""}
private fun groupingKey(e:LabEvent)=listOf(e.source,e.title,e.detail,e.kind)

@Composable internal fun NotificationInbox(initialSource:String="All",initialSection:String="Active",recentOnly:Boolean=false,open:(LabEvent)->Unit){
 val context=LocalContext.current;val scope=rememberCoroutineScope()
 var source by rememberSaveable(initialSource){mutableStateOf(initialSource)}
 var historyJob by remember{mutableStateOf<Job?>(null)}
 LaunchedEffect(Unit){Pairing.read(context)?.let{LiveRepository.loadDeliveries(it)}}

 var older by remember{mutableStateOf(emptyList<LabEvent>())};var next by remember{mutableIntStateOf(0)};var total by remember{mutableIntStateOf(-1)};var loading by remember{mutableStateOf(false)};var historyError by remember{mutableStateOf(false)}
 fun more(){historyJob?.cancel();val requestSource=source;historyJob=scope.launch{loading=true;historyError=false;try{val pair=Pairing.read(context)?:error("Pairing required");val page=withContext(Dispatchers.IO){JSONObject(LiveRepository.request(pair,"/v1/events/history?offset=$next&source="+java.net.URLEncoder.encode(requestSource,"UTF-8")))};if(requestSource!=source)return@launch;val list=page.getJSONArray("events");older=(older+(0 until list.length()).map{val e=list.getJSONObject(it);LiveRepository.event(e)}).distinctBy{it.id};next=page.getInt("next");total=page.getInt("total")}catch(e:CancellationException){throw e}catch(_:Exception){historyError=true}finally{loading=false}}}
 var query by rememberSaveable{mutableStateOf("")};var period by rememberSaveable(initialSection){mutableStateOf(initialSection)}
 val showTests=LocalShowTests.current
 LaunchedEffect(source,period){historyJob?.cancel();older=emptyList();next=0;total=-1;if(period=="History")more()}


 if(wideWindow())Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Box(Modifier.weight(1f)){DeskSearch(query,{query=it},"Search events")};Box(Modifier.width(180.dp)){DeskSegments(listOf("Active","History"),period,{period=it})}}
 else {DeskSearch(query,{query=it},"Search events");DeskSegments(listOf("Active","History"),period,{period=it})}
 if(recentOnly&&period=="History")Text("Last 24 hours",color=EventMuted,fontSize=11.sp)
 val all=(LiveRepository.events+older).distinctBy{it.id}
 DeskSegments(listOf("All")+((LiveRepository.active+all).map{it.source}+LiveRepository.deliveries.map{it.name}).filter{showTests||it!="Test"}.distinct().sorted(),source,{source=it;if(it!="All"&&LiveRepository.active.none{event->event.source==it})period="History"})
 val activeIds=LiveRepository.active.map{it.id}.toSet()
 val events=(if(period=="Active")LiveRepository.active else if(recentOnly)all else all.filter{it.id !in activeIds}).filter{e->
  (period=="Active"||!recentOnly||try{java.time.Instant.parse(e.resolvedAt.ifBlank{e.time}).isAfter(java.time.Instant.now().minusSeconds(86400))}catch(_:Exception){false})&&(query.isBlank()||e.title.contains(query,true)||e.detail.contains(query,true)||e.source.contains(query,true))&&(source=="All"||e.source==source)&&(showTests||!(e.kind=="Test"||e.source=="Test"||e.title=="Test"))}
 if(events.isEmpty())DeskEmpty(if(query.isNotBlank()||source!="All")"No matching records"else if(period=="Active")"No active incidents"else"No activity received",when{query.isNotBlank()||source!="All"->"Try another source or search.";!LiveRepository.state.startsWith("Live")->"Connection unavailable · showing last known state.";LiveRepository.signals.values.any{!it}->"Some sources are unavailable. Check source delivery below.";period=="Active"->"Connected sources report no active incidents.";else->"Received notifications will appear here."})
 events.groupBy(::groupingKey).values.sortedByDescending{it.first().resolvedAt.ifBlank{it.first().time}}.forEach{group->NotificationRow(group.first(),group.size){open(group.first())}}
 if(period=="History"&&(total<0||next<total||historyError))DeskAction(if(loading)"Loading…"else if(historyError)"Retry history"else"Load history",enabled=!loading){more()}
 DeskDisclosure("Source delivery"){
  LiveRepository.signals.forEach{(name,available)->Row(Modifier.fillMaxWidth().padding(vertical=6.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(name,color=EventPaper,fontSize=12.sp);Text(if(available)"Collection available"else"Collection unavailable",color=if(available)DeskColors.green else EventWarm,fontSize=11.sp)}}
  LiveRepository.deliveries.forEach{delivery->Row(Modifier.fillMaxWidth().padding(vertical=6.dp).clickable{source=delivery.name;period="History"},horizontalArrangement=Arrangement.SpaceBetween){Text("${delivery.name} · ${delivery.count}",color=EventPaper,fontSize=12.sp);Text("Last received · ${localTime(delivery.lastReceived)}",color=EventMuted,fontSize=11.sp)}}
  if(LiveRepository.deliveryError){Text("Source history unavailable",color=EventWarm,fontSize=12.sp);DeskAction("Retry source history"){scope.launch{Pairing.read(context)?.let{LiveRepository.loadDeliveries(it,true)}}}}

  Text("Collection health does not confirm webhook delivery. Retention: 30 days / 2,000 records; active incidents retained.",color=EventMuted,fontSize=11.sp)
 }
}
@Composable private fun NotificationRow(event:LabEvent,count:Int,click:()->Unit){
 Row(Modifier.fillMaxWidth().padding(top=6.dp).clip(RoundedCornerShape(14.dp)).background(DeskColors.surface).clickable(onClick=click).padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
  AppIcon(eventIcon(event),event.source)
  Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
   Text(incidentTitle(event),color=EventPaper,fontSize=14.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
   Text(listOf(event.source,event.node.ifBlank{event.namespace},event.pod).filter{it.isNotBlank()}.joinToString(" · "),color=EventMuted,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
   if(count>1)Text("$count grouped occurrences",color=EventMuted,fontSize=10.sp)
  }
  Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(4.dp)){
   StatusPill(event.state.ifBlank{event.kind}.replace("Unknown","Unconfirmed"),if(event.state in listOf("Resolved","Recovery"))DeskColors.green else if(event.state in listOf("Activity","Notification")||isInformational(event.kind))DeskColors.muted else EventWarm)
   Text(localTime(event.resolvedAt.ifBlank{event.received.ifBlank{event.time}}),color=EventMuted,fontSize=10.sp)
  }
 }
}

@Composable internal fun NotificationDetail(event:LabEvent,open:(String)->Unit={}){
 val context=LocalContext.current
 val data=try{JSONObject(event.detail)}catch(_:Exception){null}
 val app=sourceApp(event.source)
 val resolved=event.state=="Resolved"
 val stateColor=if(resolved)DeskColors.green else EventWarm
 Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
  AppIcon(eventIcon(event),event.source)
  Text(event.source,color=EventLilac,fontSize=12.sp,modifier=Modifier.weight(1f))
  Text("${event.state.ifBlank{"Observed"}} · ${event.kind}",color=stateColor,fontSize=12.sp)
 }

 @Composable fun measurements(){Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DeskColors.surface).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
  Text(listOf(event.namespace,event.pod,event.container).filter{it.isNotBlank()}.joinToString(" / ").ifBlank{event.resource.ifBlank{incidentTitle(event)}},color=EventPaper,fontSize=13.sp)
  val message=data?.optString("message").orEmpty().ifBlank{data?.optString("description").orEmpty()}.ifBlank{data?.optString("msg").orEmpty()}
  if(message.isNotBlank()&&message!=event.title)Text(message,color=EventMuted,fontSize=12.sp)
  listOf("movie","series","episode","release","monitor","heartbeat").forEach{key->data?.optJSONObject(key)?.let{item->val summary=item.keys().asSequence().mapNotNull{field->item.opt(field)?.takeIf{it is String||it is Number||it is Boolean}?.let{"$field · $it"}}.take(6).joinToString(" · ");if(summary.isNotBlank())Text(summary,color=EventMuted,fontSize=12.sp)}}
  event.throttledPeriodsPercent?.let{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Text("${String.format(java.util.Locale.US,"%.1f%%",it)}",color=EventPaper,fontSize=26.sp);Text("Last reported throttled periods",color=EventMuted,fontSize=11.sp)}}
  if(event.cpuLimit.isNotBlank())Text("CPU limit · ${event.cpuLimit}",color=EventMuted,fontSize=12.sp)
  if(resolved)Text("Resolved · ${localTime(event.resolvedAt.ifBlank{event.lastSeen})}",color=DeskColors.green,fontSize=12.sp)
  else if(event.evidence.isNotBlank())Text(event.evidence,color=EventMuted,fontSize=12.sp)
 }}
 @Composable fun timeline(){Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DeskColors.surface).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
  Text("Timeline",color=EventPaper,fontSize=14.sp,fontWeight=FontWeight.Medium)
  listOf("Event" to event.time,"Received" to event.received,"Last confirmed" to event.lastSeen,"Resolved" to event.resolvedAt).filter{it.second.isNotBlank()}.forEach{(label,time)->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,color=EventMuted,fontSize=11.sp);Text(localTime(time),color=EventPaper,fontSize=11.sp)}}
 }}
 if(wideWindow())Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Box(Modifier.weight(1.3f)){measurements()};Box(Modifier.weight(1f)){timeline()}}
 else Column(verticalArrangement=Arrangement.spacedBy(8.dp)){measurements();timeline()}
 Row(Modifier.fillMaxWidth().padding(vertical=6.dp).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
  if(event.pod.isNotBlank()&&event.namespace.isNotBlank())DeskAction("Inspect workload",true){open("pod:${event.namespace}/${event.pod}@${event.node}")}
  if(event.node.isNotBlank())DeskAction("Node · ${event.node}"){open("node:${event.node}")}
  if(app!=null)DeskAction("Application"){open("app:${app.name}")}
  if(app?.url?.startsWith("https://")==true)DeskAction("Open source"){context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(app.url)))}
 }
 if(event.title.contains("throttl",true)&&event.namespace.isNotBlank()&&event.pod.isNotBlank()&&event.container.isNotBlank()){
  fun encoded(value:String)=java.net.URLEncoder.encode(value,"UTF-8")
  MetricHistoryPanel("kind=throttling&namespace=${encoded(event.namespace)}&pod=${encoded(event.pod)}&container=${encoded(event.container)}")
 }
 if(event.title.contains("throttl",true))DeskDisclosure("About throttling"){Text("A container can reach its CPU quota without high node CPU usage. The reported percentage counts throttled scheduling periods, not node CPU utilization.",color=EventMuted,fontSize=12.sp)}
 DeskDisclosure("Technical details"){
  DetailTable("Reference",listOf("Field","Value"),listOf(180,560),listOf(listOf("Event ID",event.id),listOf("Resource",event.resource),listOf("Original title",event.title),listOf("Original message",cleanEventMessage(event.detail)))+event.labels.map{listOf(it.key,it.value)})
 }
 val related=LiveRepository.events.filter{it.id!=event.id&&it.source==event.source}.take(8)
 if(related.isNotEmpty())DeskDisclosure("Related activity · ${related.size}"){related.forEach{e->Text("${localTime(e.time)} · ${incidentTitle(e)}",color=EventMuted,fontSize=12.sp,modifier=Modifier.padding(vertical=8.dp))}}
}

private fun cleanEventMessage(message:String)=message.replace(Regex("(?i)Response body:\\s*$"),"").trim()

@Composable internal fun EventRoute(id:String,fallback:LabEvent?,resolved:(LabEvent)->Unit={},open:(String)->Unit){
 val context=LocalContext.current
 val current=(LiveRepository.active+LiveRepository.events).find{it.id==id}?:fallback?.takeIf{it.id==id}
 var retained by remember(id){mutableStateOf<LabEvent?>(null)};var failed by remember(id){mutableStateOf(false)};var retry by remember{mutableIntStateOf(0)}
 LaunchedEffect(id,retry){if(current==null){failed=false;try{val pair=Pairing.read(context)?:error("Not paired");retained=withContext(Dispatchers.IO){LiveRepository.event(JSONObject(LiveRepository.request(pair,"/v1/events/item?id="+java.net.URLEncoder.encode(id,"UTF-8"))))}}catch(e:CancellationException){throw e}catch(_:Exception){failed=true}}}
 val event=current?:retained
 LaunchedEffect(event){event?.let(resolved)}
 if(event!=null)NotificationDetail(event,open)else if(failed){DeskEmpty("Record unavailable","It may have expired, or the backend is unreachable.");DeskAction("Retry"){retry++}}else Text("Loading incident…",color=DeskColors.muted)
}
