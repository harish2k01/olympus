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

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun NodeDetailScreen(name:String,open:(String)->Unit={}){
 val context=LocalContext.current
 val scope=rememberCoroutineScope()
 LaunchedEffect(name){Pairing.read(context)?.let{LiveRepository.loadNode(it,name)}}
 val metric=LiveRepository.nodes.find{it.name==name}
 var section by rememberSaveable(name){mutableStateOf("Overview")}
 @Composable fun sectionTabs() {
  CompactTabs(listOf("Overview","Workloads","Metrics","Events"),section,{section=it}){
   scope.launch{Pairing.read(context)?.let{LiveRepository.loadNode(it,name)}}
  }
 }
 if (section != "Overview") sectionTabs()

 val details=LiveRepository.nodeDetails
 if (details == null) {
  Text(if (LiveRepository.nodeError) "Node details unavailable" else "Loading…",
   color = DetailMuted, modifier = Modifier.padding(20.dp))
  if (LiveRepository.nodeError) DeskAction("Retry") {
   scope.launch { Pairing.read(context)?.let { LiveRepository.loadNode(it, name) } }
  }
  return
 }
 val node=details.getJSONObject("node");val status=node.getJSONObject("Status");val spec=node.getJSONObject("Spec")
 when(section){
  "Overview"->{
   val conditions = status.optJSONArray("Conditions").objects()
   val readiness = conditions.firstOrNull { it.optString("type") == "Ready" }?.optString("status")
   val pressure = conditions.filter { it.optString("type").endsWith("Pressure") && it.optString("status") == "True" }
   val workloads = details.optJSONArray("workloads").objects()
   val problemPods = workloads.filter { it.optInt("restarts") > 0 || it.optInt("ready") < it.optInt("containers") }
    .sortedWith(compareBy({ it.optInt("ready") >= it.optInt("containers") }, { -it.optInt("restarts") }))
   val memoryUsage = metric?.takeIf { it.available && it.memoryTotalGiB > 0 }
    ?.let { it.memoryUsedGiB / it.memoryTotalGiB * 100 }
   val live = LiveRepository.state.startsWith("Live")
   val verdict = when {
    !live || readiness == null || readiness == "Unknown" -> Verdict.Unknown
    readiness == "False" || pressure.isNotEmpty() || (memoryUsage ?: 0f) >= 75f -> Verdict.Attention
    else -> Verdict.Calm
   }
   val summary = when {
    verdict == Verdict.Unknown -> "Readiness unconfirmed"
    readiness == "False" -> "Node is not ready"
    pressure.isNotEmpty() -> "${pressure.first().optString("type")} active, still Ready"
    verdict == Verdict.Attention -> "High memory use, still Ready"
    else -> "Node is Ready"
   }
   @Composable fun nodeSummary() {
    val warning = verdict == Verdict.Attention
    Column(
     Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(24.dp))
      .background(if (warning) DeskColors.attentionSurface else DeskColors.surface).padding(14.dp),
     verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
     Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
      VerdictMark(verdict)
      Text(when (verdict) {
       Verdict.Attention -> "Needs a look"
       Verdict.Calm -> "Ready"
       else -> "Status unknown"
      }, color = if (warning) DeskColors.onAttention else DetailMuted, fontSize = 12.sp)
     }
     Text(summary, color = if (warning) DeskColors.onAttention else DetailPaper,
      fontSize = 27.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
     val observations = listOfNotNull(
      memoryUsage?.let { "${if (live) "" else "Last reported "}${clusterPercentage(it)} of memory in use." },
      problemPods.size.takeIf { it > 0 }?.let { "$it pods with restarts or unready containers." }
     )
     if (observations.isNotEmpty()) Text(observations.joinToString(" "), color = DetailMuted, fontSize = 12.sp)
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
       VerdictMark(Verdict.Attention)
       Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
        Text(podName, color = DetailPaper, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        val restarts = pod.optInt("restarts")
        val status = if (restarts > 0) "$restarts recorded ${if (restarts == 1) "restart" else "restarts"}"
         else "${pod.optInt("ready")}/${pod.optInt("containers")} containers ready"
        Text(status, color = DetailMuted, fontSize = 12.sp)
       }
       Text("›", color = DetailPaper, fontSize = 20.sp)
      }
     }
    }
   }
   @Composable fun nodeResources() {
    CompanionPanel("Resources") {
     val allocations = details.optJSONArray("allocations").objects()
     for (resource in listOf("cpu", "memory")) {
      val label = if (resource == "cpu") "CPU" else "Memory"
      val allocation = allocations.firstOrNull { it.optString("resource") == resource }
      val used = metric?.takeIf { it.available }?.let {
       if (resource == "cpu") it.cpuPercent
       else memoryUsage
      }
      val values = listOf(
       "used" to used,
       "requested" to allocation?.optDouble("requestPercent")?.takeIf { it.isFinite() }?.toFloat(),
       "limits" to allocation?.optDouble("limitPercent")?.takeIf { it.isFinite() }?.toFloat()
      )
      for ((kind, value) in values) {
       Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$label $kind", Modifier.weight(1f), color = DetailMuted, fontSize = 12.sp)
        ClusterResourceBar(value, Modifier.weight(1.5f))
        Text(clusterPercentage(value), Modifier.padding(start = 12.dp).width(42.dp),
         color = DetailPaper, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End)
       }
      }
     }
     Text("Usage is measured against node capacity. Requests and limits use allocatable resources.", color = DetailMuted, fontSize = 12.sp)
    }
   }
   @Composable fun nodeConditions() {
    CompanionPanel("Conditions") {
     FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      conditions.forEach { condition ->
       val type = condition.optString("type")
       val state = condition.optString("status")
       val label = when (state) {
        "True" -> type
        "False" -> "$type off"
        else -> "$type unknown"
       }
       val healthy = (type == "Ready" && state == "True") || (type.endsWith("Pressure") && state == "False")
       Row(Modifier.clip(RoundedCornerShape(16.dp)).background(DeskColors.raised).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        VerdictMark(if (!live || state == "Unknown") Verdict.Unknown else if (healthy) Verdict.Calm else Verdict.Attention, 10.dp)
        Text(label, color = DetailPaper, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
       }
      }
     }
    }
   }
   val addresses = status.optJSONArray("Addresses").objects()
   val system = status.optJSONObject("NodeInfo") ?: JSONObject()
   val taints = spec.optJSONArray("Taints").objects()
   @Composable fun nodeFacts() {
    CompanionPanel("Details") {
     val detailsLine = listOf(
      addresses.firstOrNull { it.optString("type") == "InternalIP" }?.optString("address").orEmpty(),
      system.optString("kubeletVersion").takeIf { it.isNotBlank() }?.let { "Kubernetes $it" }.orEmpty(),
      system.optString("osImage")
     ).filter { it.isNotBlank() }.joinToString(" · ")
     if (detailsLine.isNotBlank()) Text(detailsLine, color = DetailPaper, fontSize = 13.sp)
     FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      metric?.role?.takeIf { it.isNotBlank() }?.let { StatusPill("role=$it", DeskColors.blue) }
      StatusPill(if (taints.isEmpty()) "No taints" else "${taints.size} taints", DeskColors.blue)
     }
    }
   }
   if (wideWindow()) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
     Column(Modifier.weight(1f)) {
      nodeSummary()
      nodeConditions()
      nodeFacts()
     }
     Column(Modifier.weight(1.15f)) {
      nodeResources()
      problemWorkloads()
     }
    }
   } else {
    nodeSummary()
    nodeResources()
    nodeConditions()
    problemWorkloads()
    nodeFacts()
   }
   sectionTabs()
   DeskDisclosure("Advanced node details") {
    DeskDisclosure("Condition timestamps & messages"){
    DetailTable("Conditions",listOf("Type","Status","Last heartbeat","Last transition","Reason","Message"),listOf(170,75,165,165,230,330),status.optJSONArray("Conditions").objects().map{c->listOf(c.optString("type"),c.optString("status"),localTime(c.optString("lastHeartbeatTime")),localTime(c.optString("lastTransitionTime")),c.optString("reason"),c.optString("message"))})
    }
    val capacity=status.optJSONObject("Capacity")?:JSONObject();val allocatable=status.optJSONObject("Allocatable")?:JSONObject()
    DetailTable("Capacity & allocatable",listOf("Resource","Capacity","Allocatable"),listOf(185,210,210),capacity.keys().asSequence().sorted().map{key->listOf(key,capacity.optString(key),allocatable.optString(key))}.toList())
    Text("Requests and limits use allocatable resources. Limits can exceed 100%.", color = DetailMuted, fontSize = 12.sp)
    DetailTable("Allocated resources",listOf("Resource","Requests","Limits"),listOf(185,210,210),details.optJSONArray("allocations").objects().sortedBy{it.optString("resource")}.filter{it.optString("resource")!="pods"}.map{row->val key=row.optString("resource");listOf(key,amount(row.optDouble("requests"),key)+percentage(row,"requestPercent"),amount(row.optDouble("limits"),key)+percentage(row,"limitPercent"))})
    DetailTable("Addresses",listOf("Type","Address"),listOf(180,350),status.optJSONArray("Addresses").objects().map{listOf(it.optString("type"),it.optString("address"))})
    val names=linkedMapOf("osImage" to "OS image","kernelVersion" to "Kernel","operatingSystem" to "Operating system","architecture" to "Architecture","containerRuntimeVersion" to "Container runtime","kubeletVersion" to "Kubelet","kubeProxyVersion" to "Kube proxy")
    DetailTable("System",listOf("Field","Value"),listOf(210,420),names.map{(key,label)->listOf(label,system.optString(key))}+listOf(listOf("Pod CIDR",spec.optString("PodCIDR")),listOf("Scheduling",if(spec.optBoolean("Unschedulable"))"Cordoned"else"Enabled")))
   DeskDisclosure("Labels, taints & identifiers"){val labels=node.getJSONObject("Metadata").optJSONObject("Labels")?:JSONObject();DetailTable("Labels",listOf("Key","Value"),listOf(370,350),labels.keys().asSequence().sorted().map{listOf(it,labels.optString(it))}.toList());DetailTable("Taints",listOf("Key","Value","Effect"),listOf(290,200,200),spec.optJSONArray("Taints").objects().map{listOf(it.optString("key"),it.optString("value"),it.optString("effect"))});DetailTable("Identifiers",listOf("Field","Value"),listOf(180,450),listOf("machineID","systemUUID","bootID").map{listOf(it,system.optString(it))})}
   }
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
