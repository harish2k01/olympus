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

@Composable internal fun NotificationInbox(initialSource:String="All",open:(LabEvent)->Unit){
 val context=LocalContext.current;val scope=rememberCoroutineScope()
 var older by remember{mutableStateOf(emptyList<LabEvent>())};var next by remember{mutableIntStateOf(0)};var total by remember{mutableIntStateOf(-1)};var loading by remember{mutableStateOf(false)};var historyError by remember{mutableStateOf(false)}
 fun more(){scope.launch{loading=true;historyError=false;try{val pair=Pairing.read(context)?:error("Pairing required");val page=withContext(Dispatchers.IO){JSONObject(LiveRepository.request(pair,"/v1/events/history?offset=$next"))};val list=page.getJSONArray("events");older=(older+(0 until list.length()).map{val e=list.getJSONObject(it);LabEvent(e.getString("source"),e.getString("title"),e.getString("detail"),e.getString("kind"),e.getString("id"),e.optString("time"),e.optString("receivedAt"))}).distinctBy{it.id};next=page.getInt("next");total=page.getInt("total")}catch(e:CancellationException){throw e}catch(_:Exception){historyError=true}finally{loading=false}}}
 var query by rememberSaveable{mutableStateOf("")};var period by rememberSaveable{mutableStateOf("30 days")}
 val showTests=LocalShowTests.current
 var source by rememberSaveable(initialSource){mutableStateOf(initialSource)}

 OutlinedTextField(value=query,onValueChange={query=it},label={Text("Search notifications")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(top=6.dp))
 CompactTabs(listOf("Today","7 days","30 days"),period,{period=it})
 Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())){(listOf("All")+LiveRepository.events.filter{showTests||!(it.kind=="Test"||it.source=="Test"||it.title=="Test")}.map{it.source}.distinct().sorted()).forEach{label->FilterChip(selected=source==label,onClick={source=label},label={Text(label)},modifier=Modifier.padding(end=7.dp))}}
 val cutoff=java.time.Instant.now().minusSeconds(when(period){"Today"->java.time.Duration.between(java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant(),java.time.Instant.now()).seconds;"7 days"->604800L;else->2592000L})
 val events=(LiveRepository.events+older).distinctBy{it.id}.filter{e->(query.isBlank()||e.title.contains(query,true)||e.detail.contains(query,true)||e.source.contains(query,true))&&try{java.time.Instant.parse(e.time).isAfter(cutoff)}catch(_:Exception){true}}.filter{(source=="All"||it.source==source)&&(showTests||!(it.kind=="Test"||it.source=="Test"||it.title=="Test"))}
 if(events.isEmpty())Text("No notifications in this filter",color=EventMuted,modifier=Modifier.padding(20.dp))
 events.groupBy(::groupingKey).values.sortedByDescending{it.first().time}.forEach{group->NotificationRow(group.first(),group.size){open(group.first())}}
 if(total<0||next<total||historyError)TextButton(enabled=!loading,onClick={more()}){Text(if(loading)"Loading…"else if(historyError)"Retry history"else"Load history")}
 Text("Retained for 30 days · up to 2,000 records",color=EventMuted,fontSize=10.sp,modifier=Modifier.padding(vertical=12.dp))
}
@Composable private fun NotificationRow(event:LabEvent,count:Int,click:()->Unit){
 Column(Modifier.fillMaxWidth().padding(top=10.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF242832)).clickable(onClick=click).padding(16.dp)){
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
   AppIcon(eventIcon(event),event.source)
   Column(Modifier.weight(1f)){Text(event.source,color=EventLilac,fontSize=11.sp);Text(localTime(event.received.ifBlank{event.time}),color=EventMuted,fontSize=10.sp)}
   Text((if(event.title=="Test")"Test"else event.kind)+(if(count>1)" · ×$count"else""),color=EventWarm,fontSize=11.sp)
  }
  Text(event.title,color=EventPaper,fontSize=15.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=10.dp))
  val message=try{val data=JSONObject(event.detail);data.optString("message").ifBlank{data.optString("msg")}.ifBlank{data.optString("description")}}catch(_:Exception){event.detail}
  if(message.isNotBlank())Text(message,color=EventMuted,fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=5.dp))
 }
}

@Composable internal fun NotificationDetail(event:LabEvent){
 val context=LocalContext.current
 Row(Modifier.padding(top=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){AppIcon(eventIcon(event),event.source);Text(event.source,color=EventLilac,fontSize=12.sp);Text(if(event.title=="Test")"Test"else event.kind,color=EventWarm,fontSize=12.sp)}
 Text(event.title,color=EventPaper,fontSize=23.sp,lineHeight=29.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(vertical=8.dp))
 val data=try{JSONObject(event.detail)}catch(_:Exception){null}
 val rows=mutableListOf<List<String>>()
 if(event.received.isNotEmpty()){rows.add(listOf("Received",localTime(event.received)));if(event.time!=event.received)rows.add(listOf("Event time",localTime(event.time)))}else rows.add(listOf("Event time",localTime(event.time)))
 val app=sourceApp(event.source)
 if(app!=null)rows.add(listOf("Namespace",app.namespace))
 if(data!=null){data.keys().forEach{key->val value=data.opt(key);if(value is JSONObject){value.keys().forEach{child->if(value.optString(child).isNotBlank())rows.add(listOf("${key.replaceFirstChar{it.uppercase()}} · $child",value.optString(child)))}}else if(key!="eventType"&&data.optString(key).isNotBlank())rows.add(listOf(key.replaceFirstChar{it.uppercase()},data.optString(key)))}}
 DetailTable("Details",listOf("Field","Value"),listOf(230,560),rows)
 if(data==null&&event.detail.isNotBlank())Text(cleanEventMessage(event.detail),color=EventPaper,fontSize=13.sp,lineHeight=21.sp,modifier=Modifier.padding(vertical=16.dp))
 if(app?.url?.startsWith("https://")==true)TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(app.url)))}){Text("Open ${app.name} ↗",color=EventLilac)}
 val related=LiveRepository.events.filter{it.id!=event.id&&it.source==event.source}.take(8)
 if(related.isNotEmpty())DetailTable("Related events",listOf("Time","Event","Severity"),listOf(170,430,110),related.map{listOf(localTime(it.received.ifBlank{it.time}),it.title,it.kind)})
 var technical by remember{mutableStateOf(false)}
 TextButton(onClick={technical=!technical}){Text(if(technical)"Hide technical details ▴"else"Technical details ▾",color=EventLilac)}
 if(technical)DetailTable("Reference",listOf("Field","Value"),listOf(180,560),listOf(listOf("Event ID",event.id),listOf("Original message",event.detail)))
}

private fun cleanEventMessage(message:String)=message.replace(Regex("(?i)Response body:\\s*$"),"").trim()
