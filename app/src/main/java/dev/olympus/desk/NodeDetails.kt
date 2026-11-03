package dev.olympus.desk

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
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

private val DetailPaper: Color @Composable get() = DeskColors.text
private val DetailMuted: Color @Composable get() = DeskColors.muted
private val DetailCard: Color @Composable get() = DeskColors.surface
internal fun localTime(value:String):String=try{DateTimeFormatter.ofPattern("dd MMM · HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.parse(value))}catch(_:Exception){"—"}
private fun amount(value:Double,resource:String):String=when(resource){"cpu"->String.format(java.util.Locale.US,"%.0fm",value*1000);"memory","ephemeral-storage"->if(value>=1073741824)String.format(java.util.Locale.US,"%.2f GiB",value/1073741824)else String.format(java.util.Locale.US,"%.0f MiB",value/1048576);else->String.format(java.util.Locale.US,"%.0f",value)}
private fun percentage(row:JSONObject,key:String)=if(row.isNull(key))""else" (${row.optDouble(key).toInt()}%)"
private fun JSONArray?.objects()=(0 until (this?.length()?:0)).map{this!!.getJSONObject(it)}

@Composable internal fun DetailTable(title:String,headers:List<String>,widths:List<Int>,rows:List<List<String>>){
 Text(title,color=DetailPaper,fontSize=15.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=8.dp,bottom=6.dp))
 if(rows.isEmpty()){Text("None",color=DetailMuted,fontSize=12.sp);return}
 BoxWithConstraints(Modifier.fillMaxWidth()){
 val widthFactor=((maxWidth.value-16)/widths.sum()).coerceAtLeast(1f)
 Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp,DeskColors.raised,RoundedCornerShape(16.dp)).horizontalScroll(rememberScrollState())){
  Row(Modifier.background(DeskColors.raised).padding(horizontal=8.dp)){
   headers.forEachIndexed{i,label->Text(label,color=DetailMuted,fontSize=11.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.width((widths[i]*widthFactor).dp).padding(horizontal=8.dp,vertical=6.dp))}
  }
  rows.forEachIndexed{index,row->
   Row(Modifier.background(if(index%2==0)DetailCard else DeskColors.raised.copy(alpha = .45f)).padding(horizontal=8.dp)){
    row.forEachIndexed{i,value->Text(value.ifBlank{"—"},color=when{title=="Conditions"&&i==1->if((row[0]=="Ready"&&value=="True")||(row[0]!="Ready"&&value=="False"))DeskColors.green else DeskColors.apricot;value in listOf("Ready","Healthy")->DeskColors.green;value in listOf("Warning","Not ready","Critical")->DeskColors.apricot;else->DetailPaper},fontSize=11.sp,lineHeight=15.sp,modifier=Modifier.width((widths[i]*widthFactor).dp).padding(horizontal=8.dp,vertical=6.dp))}
   }
   HorizontalDivider(color=DeskColors.raised,thickness=.5.dp)
  }
 }
 }
}

@Composable internal fun NodeDetailScreen(name:String,open:(String)->Unit={}){
 val context=LocalContext.current
 val scope=rememberCoroutineScope()
 LaunchedEffect(name){Pairing.read(context)?.let{LiveRepository.loadNode(it,name)}}
 val metric=LiveRepository.nodes.find{it.name==name}
 if(metric!=null){
  val wide=wideWindow()
  @Composable fun cpu(){Column(Modifier.fillMaxWidth().background(DetailCard,RoundedCornerShape(14.dp)).padding(horizontal=12.dp,vertical=10.dp)){Usage("CPU · ${metric.cores} cores",if(metric.available)metric.cpuPercent else null,DeskColors.blue)}}
  @Composable fun memory(){Column(Modifier.fillMaxWidth().background(DetailCard,RoundedCornerShape(14.dp)).padding(horizontal=12.dp,vertical=10.dp)){Usage("Memory · ${amount(metric.memoryUsedGiB*1073741824.0,"memory")} / ${amount(metric.memoryTotalGiB*1073741824.0,"memory")}",if(metric.available)metric.memoryUsedGiB/metric.memoryTotalGiB*100 else null,DeskColors.lilac)}}
  if(wide)Row(Modifier.padding(top=6.dp,bottom=6.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){Box(Modifier.weight(1f)){cpu()};Box(Modifier.weight(1f)){memory()}}
  else Column(Modifier.padding(top=6.dp,bottom=6.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){cpu();memory()}
 }

 var section by rememberSaveable(name){mutableStateOf("Overview")}
 CompactTabs(listOf("Overview","Workloads","Metrics","Events"),section,{section=it}){scope.launch{Pairing.read(context)?.let{LiveRepository.loadNode(it,name)}}}

 val details=LiveRepository.nodeDetails
 if(details==null){Text(if(LiveRepository.nodeError)"Node details unavailable"else"Loading…",color=DetailMuted,modifier=Modifier.padding(20.dp));return}
 val node=details.getJSONObject("node");val status=node.getJSONObject("Status");val spec=node.getJSONObject("Spec")
 when(section){
  "Overview"->{
   val conditions = status.optJSONArray("Conditions").objects()
   val readiness = conditions.firstOrNull { it.optString("type") == "Ready" }?.optString("status")
   val pressure = conditions.filter { it.optString("type").endsWith("Pressure") && it.optString("status") == "True" }
   val workloads = details.optJSONArray("workloads").objects()
   val problemPods = workloads.filter { it.optInt("restarts") > 0 || it.optInt("ready") < it.optInt("containers") }
    .sortedWith(compareBy({ it.optInt("ready") >= it.optInt("containers") }, { -it.optInt("restarts") }))
   @Composable fun conditionSummary() {
    CompanionPanel("Node condition") {
     val summary = when {
      !LiveRepository.state.startsWith("Live") -> "Current readiness unconfirmed"
      readiness == "False" -> "Node is not ready"
      readiness != "True" -> "Readiness unconfirmed"
      pressure.isNotEmpty() -> "${pressure.first().optString("type")} active · still Ready"
      else -> "Node is Ready"
     }
     Text(summary, color = DetailPaper, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
     if (problemPods.isNotEmpty()) Text("${problemPods.size} pods with restarts or unready containers",
      color = DetailMuted, fontSize = 12.sp)
    }
   }
   @Composable fun problemWorkloads() {
    CompanionPanel("Problem pods") {
     if (problemPods.isEmpty()) Text("No unready containers or recorded restarts", color = DetailMuted, fontSize = 12.sp)
     problemPods.take(6).forEach { pod ->
      val namespace = pod.optString("namespace")
      val podName = pod.optString("name")
      Row(
       Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { open("pod:$namespace/$podName@$name") },
       horizontalArrangement = Arrangement.SpaceBetween,
       verticalAlignment = Alignment.CenterVertically
      ) {
       Text(podName, color = DetailPaper, fontSize = 12.sp, modifier = Modifier.weight(1f))
       val restarts = pod.optInt("restarts")
       Text(if (restarts == 1) "1 recorded restart" else "$restarts recorded restarts", color = DetailMuted, fontSize = 11.sp)
      }
     }
    }
   }
   if (wideWindow()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Column(Modifier.weight(1f)) { conditionSummary() }
    Column(Modifier.weight(1f)) { problemWorkloads() }
   } else {
    conditionSummary()
    problemWorkloads()
   }
   Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
    StatusPill(if(metric?.ready==true)"Ready"else"Readiness unconfirmed",if(metric?.ready==true)DeskColors.green else DeskColors.apricot)
    Text("${conditions.size} conditions",color=DetailMuted,fontSize=11.sp)
   }
   DetailTable("Conditions",listOf("Type","Status","Reason"),listOf(170,75,230),conditions.map{c->listOf(c.optString("type"),c.optString("status"),c.optString("reason"))})
   DeskDisclosure("Condition timestamps & messages"){
   DetailTable("Conditions",listOf("Type","Status","Last heartbeat","Last transition","Reason","Message"),listOf(170,75,165,165,230,330),status.optJSONArray("Conditions").objects().map{c->listOf(c.optString("type"),c.optString("status"),localTime(c.optString("lastHeartbeatTime")),localTime(c.optString("lastTransitionTime")),c.optString("reason"),c.optString("message"))})
   }
   val capacity=status.optJSONObject("Capacity")?:JSONObject();val allocatable=status.optJSONObject("Allocatable")?:JSONObject()
   DetailTable("Capacity & allocatable",listOf("Resource","Capacity","Allocatable"),listOf(185,210,210),capacity.keys().asSequence().sorted().map{key->listOf(key,capacity.optString(key),allocatable.optString(key))}.toList())
   Text("Requests and limits use allocatable resources. Limits can exceed 100%.", color = DetailMuted, fontSize = 12.sp)
   DetailTable("Allocated resources",listOf("Resource","Requests","Limits"),listOf(185,210,210),details.optJSONArray("allocations").objects().sortedBy{it.optString("resource")}.filter{it.optString("resource")!="pods"}.map{row->val key=row.optString("resource");listOf(key,amount(row.optDouble("requests"),key)+percentage(row,"requestPercent"),amount(row.optDouble("limits"),key)+percentage(row,"limitPercent"))})
   DetailTable("Addresses",listOf("Type","Address"),listOf(180,350),status.optJSONArray("Addresses").objects().map{listOf(it.optString("type"),it.optString("address"))})
   val system=status.optJSONObject("NodeInfo")?:JSONObject();val names=linkedMapOf("osImage" to "OS image","kernelVersion" to "Kernel","operatingSystem" to "Operating system","architecture" to "Architecture","containerRuntimeVersion" to "Container runtime","kubeletVersion" to "Kubelet","kubeProxyVersion" to "Kube proxy")
   DetailTable("System",listOf("Field","Value"),listOf(210,420),names.map{(key,label)->listOf(label,system.optString(key))}+listOf(listOf("Pod CIDR",spec.optString("PodCIDR")),listOf("Scheduling",if(spec.optBoolean("Unschedulable"))"Cordoned"else"Enabled")))
   DeskDisclosure("Labels, taints & identifiers"){val labels=node.getJSONObject("Metadata").optJSONObject("Labels")?:JSONObject();DetailTable("Labels",listOf("Key","Value"),listOf(370,350),labels.keys().asSequence().sorted().map{listOf(it,labels.optString(it))}.toList());DetailTable("Taints",listOf("Key","Value","Effect"),listOf(290,200,200),spec.optJSONArray("Taints").objects().map{listOf(it.optString("key"),it.optString("value"),it.optString("effect"))});DetailTable("Identifiers",listOf("Field","Value"),listOf(180,450),listOf("machineID","systemUUID","bootID").map{listOf(it,system.optString(it))})}
  }
  "Workloads"->{
   details.optJSONArray("workloads").objects().sortedBy{it.optString("name")}.forEach{pod->
    Row(Modifier.fillMaxWidth().clickable{open("pod:${pod.optString("namespace")}/${pod.optString("name")}@$name")}.padding(vertical=9.dp),horizontalArrangement=Arrangement.SpaceBetween){
     Column(Modifier.weight(1f)){Text(pod.optString("name"),color=DetailPaper,fontSize=13.sp);Text(pod.optString("namespace"),color=DetailMuted,fontSize=11.sp)}
     Text("${pod.optInt("ready")}/${pod.optInt("containers")} ready · ${pod.optInt("restarts")} restarts",color=DetailMuted,fontSize=11.sp)
    };HorizontalDivider(color=DeskColors.raised)
   }
   DeskDisclosure("Requests, limits & allocations"){

   DetailTable("Non-terminated pods",listOf("Namespace","Pod","Ready","CPU requests","CPU limits","Memory requests","Memory limits","Restarts","Age"),listOf(125,330,70,125,125,145,145,85,90),details.optJSONArray("workloads").objects().sortedWith(compareBy({it.optString("namespace")},{it.optString("name")})).map{p->val req=p.optJSONObject("requests")?:JSONObject();val lim=p.optJSONObject("limits")?:JSONObject();val age=try{val hours=java.time.Duration.between(Instant.parse(p.getString("createdAt")),Instant.now()).toHours();if(hours>=24)"${hours/24}d ${hours%24}h"else"${hours}h"}catch(_:Exception){"—"};listOf(p.optString("namespace"),p.optString("name"),"${p.optInt("ready")}/${p.optInt("containers")}",amount(req.optDouble("cpu",0.0),"cpu"),amount(lim.optDouble("cpu",0.0),"cpu"),amount(req.optDouble("memory",0.0),"memory"),amount(lim.optDouble("memory",0.0),"memory"),p.optInt("restarts").toString(),age)})
  }
   }
  "Metrics"->{
   val values=details.optJSONArray("cpuHistory")?.optJSONObject(0)?.optJSONArray("Values")
   Text("CPU · 15 minutes",color=DetailMuted,fontSize=12.sp,modifier=Modifier.padding(top=18.dp,bottom=10.dp));Spark(values?.objectsFromPairs()?:emptyList(),DeskColors.blue,true)
   val stats=details.optJSONObject("stats")?:JSONObject();fun value(key:String)=if(stats.isNull(key))"Unavailable"else String.format(java.util.Locale.US,"%.2f",stats.optDouble(key))
   DetailTable("Storage & network",listOf("Metric","Value"),listOf(260,240),listOf(listOf("Filesystem used",value("diskUsedPercent")+" %"),listOf("Network receive",value("receiveMiBs")+" MiB/s"),listOf("Network transmit",value("transmitMiBs")+" MiB/s")))
  }
  "Events"->DetailTable("Node events",listOf("Type","Reason","Last seen","Count","Message"),listOf(90,210,165,70,500),details.optJSONArray("events").objects().map{e->listOf(e.optString("Type"),e.optString("Reason"),localTime(e.optString("LastTimestamp").ifEmpty{e.optString("EventTime")}),e.optInt("Count").toString(),e.optString("Message"))})
 }
}
private fun JSONArray.objectsFromPairs()=(0 until length()).mapNotNull{optJSONArray(it)?.optString(1)?.toFloatOrNull()}
