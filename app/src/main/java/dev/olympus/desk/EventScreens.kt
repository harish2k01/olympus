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

private val EventPaper=Color(0xFFF5F3EF)
private val EventMuted=Color(0xFFBFC4D1)
private val EventLilac=Color(0xFFCDC5FF)
private val EventWarm=Color(0xFFEDB48F)
private fun sourceApp(source:String)=LiveRepository.services.find{it.name.replace("-","").equals(source.replace(" ","").replace("-",""),true)}
private fun eventIcon(event:LabEvent)=sourceApp(event.source)?.icon?.ifBlank{null}?:when(event.source){"Kubernetes"->"https://raw.githubusercontent.com/selfhst/icons/main/png/kubernetes.png";"Prometheus"->"https://raw.githubusercontent.com/selfhst/icons/main/png/prometheus.png";else->""}
private fun groupingKey(e:LabEvent)=listOf(e.source,e.title,e.detail,e.kind)

@Composable internal fun NotificationInbox(initialSource:String="All",open:(LabEvent)->Unit){
 var source by rememberSaveable(initialSource){mutableStateOf(initialSource)}

 Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())){(listOf("All")+LiveRepository.events.map{it.source}.distinct().sorted()).forEach{label->FilterChip(selected=source==label,onClick={source=label},label={Text(label)},modifier=Modifier.padding(end=7.dp))}}
 val events=LiveRepository.events.filter{source=="All"||it.source==source}
 if(events.isEmpty())Text("No events",color=EventMuted,modifier=Modifier.padding(20.dp))
 events.groupBy(::groupingKey).values.sortedByDescending{it.first().time}.forEach{group->NotificationRow(group.first(),group.size){open(group.first())}}
}
@Composable private fun NotificationRow(event:LabEvent,count:Int,click:()->Unit){
 Column(Modifier.fillMaxWidth().padding(top=10.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF242832)).clickable(onClick=click).padding(16.dp)){
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
   AppIcon(eventIcon(event),event.source)
   Column(Modifier.weight(1f)){Text(event.source,color=EventLilac,fontSize=11.sp);Text(localTime(event.received.ifBlank{event.time}),color=EventMuted,fontSize=10.sp)}
   Text(event.kind+(if(count>1)" · ×$count"else""),color=EventWarm,fontSize=11.sp)
  }
  Text(event.title,color=EventPaper,fontSize=15.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=10.dp))
  val message=try{val data=JSONObject(event.detail);data.optString("message").ifBlank{data.optString("msg")}.ifBlank{data.optString("description")}}catch(_:Exception){event.detail}
  if(message.isNotBlank())Text(message,color=EventMuted,fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=5.dp))
 }
}

@Composable internal fun NotificationDetail(event:LabEvent){
 val context=LocalContext.current
 Row(Modifier.padding(top=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){AppIcon(eventIcon(event),event.source);Text(event.source,color=EventLilac,fontSize=12.sp);Text(event.kind,color=EventWarm,fontSize=12.sp)}
 Text(event.title,color=EventPaper,fontSize=23.sp,lineHeight=29.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(vertical=8.dp))
 val data=try{JSONObject(event.detail)}catch(_:Exception){null}
 val rows=mutableListOf<List<String>>()
 if(event.received.isNotEmpty()){rows.add(listOf("Received",localTime(event.received)));if(event.time!=event.received)rows.add(listOf("Event time",localTime(event.time)))}else rows.add(listOf("Event time",localTime(event.time)))
 val app=sourceApp(event.source)
 if(app!=null)rows.add(listOf("Namespace",app.namespace))
 if(data!=null){data.keys().forEach{key->val value=data.opt(key);if(value is JSONObject){value.keys().forEach{child->rows.add(listOf("${key.replaceFirstChar{it.uppercase()}} · $child",value.optString(child)))}}else if(key!="eventType")rows.add(listOf(key.replaceFirstChar{it.uppercase()},data.optString(key)))}}
 DetailTable("Details",listOf("Field","Value"),listOf(230,560),rows)
 if(data==null&&event.detail.isNotBlank())Text(event.detail,color=EventPaper,fontSize=13.sp,lineHeight=21.sp,modifier=Modifier.padding(vertical=16.dp))
 if(app?.url?.startsWith("https://")==true)TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(app.url)))}){Text("Open ${app.name} ↗",color=EventLilac)}
 val related=LiveRepository.events.filter{it.id!=event.id&&it.source==event.source}.take(8)
 if(related.isNotEmpty())DetailTable("Related events",listOf("Time","Event","Severity"),listOf(170,430,110),related.map{listOf(localTime(it.received.ifBlank{it.time}),it.title,it.kind)})
 var technical by remember{mutableStateOf(false)}
 TextButton(onClick={technical=!technical}){Text(if(technical)"Hide technical details ▴"else"Technical details ▾",color=EventLilac)}
 if(technical)DetailTable("Reference",listOf("Field","Value"),listOf(180,560),listOf(listOf("Event ID",event.id)))
}
