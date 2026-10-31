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
 var older by remember{mutableStateOf(emptyList<LabEvent>())};var next by remember{mutableIntStateOf(0)};var total by remember{mutableIntStateOf(-1)};var loading by remember{mutableStateOf(false)};var historyError by remember{mutableStateOf(false)}
 fun more(){scope.launch{loading=true;historyError=false;try{val pair=Pairing.read(context)?:error("Pairing required");val page=withContext(Dispatchers.IO){JSONObject(LiveRepository.request(pair,"/v1/events/history?offset=$next"))};val list=page.getJSONArray("events");older=(older+(0 until list.length()).map{val e=list.getJSONObject(it);LiveRepository.event(e)}).distinctBy{it.id};next=page.getInt("next");total=page.getInt("total")}catch(e:CancellationException){throw e}catch(_:Exception){historyError=true}finally{loading=false}}}
 var query by rememberSaveable{mutableStateOf("")};var period by rememberSaveable(initialSection){mutableStateOf(initialSection)}
 val showTests=LocalShowTests.current
 var source by rememberSaveable(initialSource){mutableStateOf(initialSource)}

 if(wideWindow())Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Box(Modifier.weight(1f)){DeskSearch(query,{query=it},"Search events")};Box(Modifier.width(180.dp)){DeskSegments(listOf("Active","History"),period,{period=it})}}
 else {DeskSearch(query,{query=it},"Search events");DeskSegments(listOf("Active","History"),period,{period=it})}
 if(recentOnly&&period=="History")Text("Last 24 hours",color=EventMuted,fontSize=11.sp)
 val all=(LiveRepository.events+older).distinctBy{it.id}
 Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){(listOf("All")+(LiveRepository.active+all).map{it.source}.distinct().sorted()).forEach{label->DeskAction(label,source==label){source=label}}}
 val activeIds=LiveRepository.active.map{it.id}.toSet()
 val events=(if(period=="Active")LiveRepository.active else if(recentOnly)all else all.filter{it.id !in activeIds}).filter{e->
  (period=="Active"||!recentOnly||try{java.time.Instant.parse(e.resolvedAt.ifBlank{e.time}).isAfter(java.time.Instant.now().minusSeconds(86400))}catch(_:Exception){false})&&(query.isBlank()||e.title.contains(query,true)||e.detail.contains(query,true)||e.source.contains(query,true))&&(source=="All"||e.source==source)&&(showTests||!(e.kind=="Test"||e.source=="Test"||e.title=="Test"))}
 if(events.isEmpty())Text("No notifications in this filter",color=EventMuted,modifier=Modifier.padding(20.dp))
 events.groupBy(::groupingKey).values.sortedByDescending{it.first().resolvedAt.ifBlank{it.first().time}}.forEach{group->NotificationRow(group.first(),group.size){open(group.first())}}
 if(period=="History"&&(total<0||next<total||historyError))DeskAction(if(loading)"Loading…"else if(historyError)"Retry history"else"Load history",enabled=!loading){more()}
 Text("History · 30 days / 2,000 records · active incidents retained",color=EventMuted,fontSize=10.sp,modifier=Modifier.padding(vertical=12.dp))
}
@Composable private fun NotificationRow(event:LabEvent,count:Int,click:()->Unit){
 Column(Modifier.fillMaxWidth().padding(top=8.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF242832)).clickable(onClick=click).padding(12.dp)){
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
   AppIcon(eventIcon(event),event.source)
   Column(Modifier.weight(1f)){Text(event.source,color=EventLilac,fontSize=11.sp);Text(localTime(event.resolvedAt.ifBlank{event.received.ifBlank{event.time}}),color=EventMuted,fontSize=10.sp)}
   Text((if(event.title=="Test")"Test"else event.state.ifBlank{event.kind}.replace("Unknown","Unconfirmed"))+(if(count>1)" · $count occurrences"else""),color=if(event.state=="Resolved")DeskColors.green else EventWarm,fontSize=11.sp)
  }
  Text(event.kind.replaceFirstChar{it.uppercase()},color=if(isInformational(event.kind))EventMuted else EventWarm,fontSize=10.sp)
  Text(incidentTitle(event),color=EventPaper,fontSize=15.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=8.dp))
  if(event.pod.isNotBlank()||event.node.isNotBlank())Text(listOfNotNull(event.node.takeIf{it.isNotBlank()}?.let{"Node $it"},event.namespace.takeIf{it.isNotBlank()},event.pod.takeIf{it.isNotBlank()}).joinToString(" · "),color=EventMuted,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=4.dp))
  val message=try{val data=JSONObject(event.detail);data.optString("message").ifBlank{data.optString("msg")}.ifBlank{data.optString("description")}}catch(_:Exception){cleanEventMessage(event.detail)}
  if(message.isNotBlank()&&event.state!="Resolved")Text(message,color=EventMuted,fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=5.dp))
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

 @Composable fun measurements(){Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(DeskColors.surface).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  val explanation=if(event.title.contains("throttl",true))"Throttling can occur without high node CPU usage: a container can reach its own CPU quota." else if(resolved)"This incident has resolved." else data?.optString("message")?.ifBlank{data.optString("description")}.orEmpty().ifBlank{cleanEventMessage(event.detail)}
  if(explanation.isNotBlank())Text(explanation,color=EventPaper,fontSize=15.sp,lineHeight=22.sp)
  Text(listOf(event.namespace,event.pod,event.container).filter{it.isNotBlank()}.joinToString(" / ").ifBlank{event.resource.ifBlank{"Resource not reported"}},color=EventMuted,fontSize=12.sp)
  event.throttledPeriodsPercent?.let{Text("${String.format(java.util.Locale.US,"%.1f%%",it)}",color=EventPaper,fontSize=34.sp);Text("Throttled scheduling periods",color=EventMuted,fontSize=12.sp)}
  if(event.cpuLimit.isNotBlank()){HorizontalDivider(color=DeskColors.raised);Text("CPU limit · ${event.cpuLimit}",color=EventPaper,fontSize=14.sp)}
  if(event.evidence.isNotBlank())Text(event.evidence,color=EventMuted,fontSize=12.sp)
 }}
 @Composable fun timeline(){Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(DeskColors.surface).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Text(if(event.state in listOf("Observed","Notification"))"Notification details"else"Incident timeline",color=EventPaper,fontSize=16.sp,fontWeight=FontWeight.Medium)
  if(event.pod.isNotBlank()&&event.node.isNotBlank())DeskAction("Inspect workload"){open("pod:${event.namespace}/${event.pod}@${event.node}")}
  if(event.node.isNotBlank())DeskAction("Open node · ${event.node}"){open("node:${event.node}")}
  if(app!=null)DeskAction("View application"){open("app:${app.name}")}
  if(app?.url?.startsWith("https://")==true)DeskAction("Open ${app.name}"){context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(app.url)))}
  Text("Event time · ${localTime(event.time)}",color=EventMuted,fontSize=12.sp)
  if(event.received.isNotBlank())Text("Received · ${localTime(event.received)}",color=EventMuted,fontSize=12.sp)
  if(event.lastSeen.isNotBlank())Text("Last confirmed · ${localTime(event.lastSeen)}",color=EventMuted,fontSize=12.sp)
  if(event.resolvedAt.isNotBlank())Text("Resolved · ${localTime(event.resolvedAt)}",color=DeskColors.green,fontSize=12.sp)
  HorizontalDivider(color=DeskColors.raised)
  if(event.namespace.isNotBlank())Text("Namespace · ${event.namespace}",color=EventPaper,fontSize=13.sp)
  Text("Node · ${event.node.ifBlank{"Not reported"}}",color=EventPaper,fontSize=13.sp)

 }}
 if(wideWindow())Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){Box(Modifier.weight(1.35f)){measurements()};Box(Modifier.weight(1f)){timeline()}}
 else Column(verticalArrangement=Arrangement.spacedBy(12.dp)){measurements();timeline()}
 DeskDisclosure("Technical details"){
  DetailTable("Reference",listOf("Field","Value"),listOf(180,560),listOf(listOf("Event ID",event.id),listOf("Resource",event.resource),listOf("Original title",event.title),listOf("Original message",cleanEventMessage(event.detail)))+event.labels.map{listOf(it.key,it.value)})
 }
 val related=LiveRepository.events.filter{it.id!=event.id&&it.source==event.source}.take(8)
 if(related.isNotEmpty())DeskDisclosure("Related activity · ${related.size}"){related.forEach{e->Text("${localTime(e.time)} · ${incidentTitle(e)}",color=EventMuted,fontSize=12.sp,modifier=Modifier.padding(vertical=8.dp))}}
}

private fun cleanEventMessage(message:String)=message.replace(Regex("(?i)Response body:\\s*$"),"").trim()
