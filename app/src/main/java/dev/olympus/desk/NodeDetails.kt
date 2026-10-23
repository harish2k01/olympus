package dev.olympus.desk

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.olympus.desk.data.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DetailPaper=Color(0xFFF5F3EF)
private val DetailMuted=Color(0xFFBFC4D1)
private val DetailCard=Color(0xFF242832)
internal fun localTime(value:String):String=try{DateTimeFormatter.ofPattern("dd MMM · HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.parse(value))}catch(_:Exception){"—"}
private fun amount(value:Double,resource:String):String=when(resource){"cpu"->String.format(java.util.Locale.US,"%.0fm",value*1000);"memory","ephemeral-storage"->if(value>=1073741824)String.format(java.util.Locale.US,"%.2f GiB",value/1073741824)else String.format(java.util.Locale.US,"%.0f MiB",value/1048576);else->String.format(java.util.Locale.US,"%.0f",value)}
private fun percentage(row:JSONObject,key:String)=if(row.isNull(key))""else" (${row.optDouble(key).toInt()}%)"
private fun JSONArray?.objects()=(0 until (this?.length()?:0)).map{this!!.getJSONObject(it)}

@Composable internal fun DetailTable(title:String,headers:List<String>,widths:List<Int>,rows:List<List<String>>){
 Text(title,color=DetailPaper,fontSize=17.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=22.dp,bottom=10.dp))
 if(rows.isEmpty()){Text("None",color=DetailMuted,fontSize=12.sp);return}
 Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp,Color(0xFF3A4150),RoundedCornerShape(16.dp)).horizontalScroll(rememberScrollState())){
  Row(Modifier.background(Color(0xFF30384A)).padding(horizontal=8.dp)){
   headers.forEachIndexed{i,label->Text(label,color=DetailMuted,fontSize=11.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.width(widths[i].dp).padding(horizontal=10.dp,vertical=13.dp))}
  }
  rows.forEachIndexed{index,row->
   Row(Modifier.background(if(index%2==0)DetailCard else Color(0xFF202630)).padding(horizontal=8.dp)){
    row.forEachIndexed{i,value->Text(value.ifBlank{"—"},color=when{title=="Conditions"&&i==1->if((row[0]=="Ready"&&value=="True")||(row[0]!="Ready"&&value=="False"))Color(0xFFB4DFBE)else Color(0xFFEDB48F);value in listOf("Ready","Healthy")->Color(0xFFB4DFBE);value in listOf("Warning","Not ready","Critical")->Color(0xFFEDB48F);else->DetailPaper},fontSize=12.sp,lineHeight=18.sp,modifier=Modifier.width(widths[i].dp).padding(horizontal=10.dp,vertical=13.dp))}
   }
   HorizontalDivider(color=Color(0xFF353C49),thickness=.5.dp)
  }
 }
}

@Composable internal fun NodeDetailScreen(name:String){
 val context=LocalContext.current
 val scope=rememberCoroutineScope()
 LaunchedEffect(name){Pairing.read(context)?.let{LiveRepository.loadNode(it,name)}}
 val metric=LiveRepository.nodes.find{it.name==name}
 if(metric!=null){
  Usage("CPU · ${metric.cores} cores",if(metric.available)metric.cpuPercent else null,Color(0xFF879BFF))
  Usage("Memory · ${amount(metric.memoryUsedGiB*1073741824.0,"memory")} / ${amount(metric.memoryTotalGiB*1073741824.0,"memory")}",if(metric.available)metric.memoryUsedGiB/metric.memoryTotalGiB*100 else null,Color(0xFFCDC5FF))
 }
 var section by rememberSaveable(name){mutableStateOf("Overview")}
 Row(Modifier.fillMaxWidth().padding(top=14.dp).horizontalScroll(rememberScrollState())){listOf("Overview","Workloads","Metrics","Events").forEach{label->FilterChip(selected=section==label,onClick={section=label},label={Text(label)},modifier=Modifier.padding(end=8.dp))};TextButton(onClick={scope.launch{Pairing.read(context)?.let{LiveRepository.loadNode(it,name)}}}){Text("Refresh",color=Color(0xFF879BFF))}}
 val details=LiveRepository.nodeDetails
 if(details==null){Text(if(LiveRepository.nodeError)"Node details unavailable"else"Loading…",color=DetailMuted,modifier=Modifier.padding(20.dp));return}
 val node=details.getJSONObject("node");val status=node.getJSONObject("Status");val spec=node.getJSONObject("Spec")
 when(section){
  "Overview"->{
   DetailTable("Conditions",listOf("Type","Status","Last heartbeat","Last transition","Reason","Message"),listOf(170,75,165,165,230,330),status.optJSONArray("Conditions").objects().map{c->listOf(c.optString("type"),c.optString("status"),localTime(c.optString("lastHeartbeatTime")),localTime(c.optString("lastTransitionTime")),c.optString("reason"),c.optString("message"))})
   val capacity=status.optJSONObject("Capacity")?:JSONObject();val allocatable=status.optJSONObject("Allocatable")?:JSONObject()
   DetailTable("Capacity & allocatable",listOf("Resource","Capacity","Allocatable"),listOf(185,210,210),capacity.keys().asSequence().sorted().map{key->listOf(key,capacity.optString(key),allocatable.optString(key))}.toList())
   DetailTable("Allocated resources",listOf("Resource","Requests","Limits"),listOf(185,210,210),details.optJSONArray("allocations").objects().sortedBy{it.optString("resource")}.filter{it.optString("resource")!="pods"}.map{row->val key=row.optString("resource");listOf(key,amount(row.optDouble("requests"),key)+percentage(row,"requestPercent"),amount(row.optDouble("limits"),key)+percentage(row,"limitPercent"))})
   DetailTable("Addresses",listOf("Type","Address"),listOf(180,350),status.optJSONArray("Addresses").objects().map{listOf(it.optString("type"),it.optString("address"))})
   val system=status.optJSONObject("NodeInfo")?:JSONObject();val names=linkedMapOf("osImage" to "OS image","kernelVersion" to "Kernel","operatingSystem" to "Operating system","architecture" to "Architecture","containerRuntimeVersion" to "Container runtime","kubeletVersion" to "Kubelet","kubeProxyVersion" to "Kube proxy")
   DetailTable("System",listOf("Field","Value"),listOf(210,420),names.map{(key,label)->listOf(label,system.optString(key))}+listOf(listOf("Pod CIDR",spec.optString("PodCIDR")),listOf("Scheduling",if(spec.optBoolean("Unschedulable"))"Cordoned"else"Enabled")))
   var technical by rememberSaveable(name){mutableStateOf(false)}
   TextButton(onClick={technical=!technical},modifier=Modifier.padding(top=12.dp)){Text(if(technical)"Hide technical details ▴"else"Technical details ▾",color=Color(0xFF879BFF))}
   if(technical){val labels=node.getJSONObject("Metadata").optJSONObject("Labels")?:JSONObject();DetailTable("Labels",listOf("Key","Value"),listOf(370,350),labels.keys().asSequence().sorted().map{listOf(it,labels.optString(it))}.toList());DetailTable("Taints",listOf("Key","Value","Effect"),listOf(290,200,200),spec.optJSONArray("Taints").objects().map{listOf(it.optString("key"),it.optString("value"),it.optString("effect"))});DetailTable("Identifiers",listOf("Field","Value"),listOf(180,450),listOf("machineID","systemUUID","bootID").map{listOf(it,system.optString(it))})}
  }
  "Workloads"->{
   DetailTable("Non-terminated pods",listOf("Namespace","Pod","Ready","CPU requests","CPU limits","Memory requests","Memory limits","Restarts","Age"),listOf(125,330,70,125,125,145,145,85,90),details.optJSONArray("workloads").objects().sortedWith(compareBy({it.optString("namespace")},{it.optString("name")})).map{p->val req=p.optJSONObject("requests")?:JSONObject();val lim=p.optJSONObject("limits")?:JSONObject();val age=try{val hours=java.time.Duration.between(Instant.parse(p.getString("createdAt")),Instant.now()).toHours();if(hours>=24)"${hours/24}d ${hours%24}h"else"${hours}h"}catch(_:Exception){"—"};listOf(p.optString("namespace"),p.optString("name"),"${p.optInt("ready")}/${p.optInt("containers")}",amount(req.optDouble("cpu",0.0),"cpu"),amount(lim.optDouble("cpu",0.0),"cpu"),amount(req.optDouble("memory",0.0),"memory"),amount(lim.optDouble("memory",0.0),"memory"),p.optInt("restarts").toString(),age)})
  }
  "Metrics"->{
   val values=details.optJSONArray("cpuHistory")?.optJSONObject(0)?.optJSONArray("Values")
   Text("CPU · 15 minutes",color=DetailMuted,fontSize=12.sp,modifier=Modifier.padding(top=18.dp,bottom=10.dp));Spark(values?.objectsFromPairs()?:emptyList(),Color(0xFF879BFF))
   val stats=details.optJSONObject("stats")?:JSONObject();fun value(key:String)=if(stats.isNull(key))"Unavailable"else String.format(java.util.Locale.US,"%.2f",stats.optDouble(key))
   DetailTable("Storage & network",listOf("Metric","Value"),listOf(260,240),listOf(listOf("Filesystem used",value("diskUsedPercent")+" %"),listOf("Network receive",value("receiveMiBs")+" MiB/s"),listOf("Network transmit",value("transmitMiBs")+" MiB/s")))
  }
  "Events"->DetailTable("Node events",listOf("Type","Reason","Last seen","Count","Message"),listOf(90,210,165,70,500),details.optJSONArray("events").objects().map{e->listOf(e.optString("Type"),e.optString("Reason"),localTime(e.optString("LastTimestamp").ifEmpty{e.optString("EventTime")}),e.optInt("Count").toString(),e.optString("Message"))})
 }
}
private fun JSONArray.objectsFromPairs()=(0 until length()).mapNotNull{optJSONArray(it)?.optString(1)?.toFloatOrNull()}
