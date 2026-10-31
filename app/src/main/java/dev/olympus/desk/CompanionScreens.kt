package dev.olympus.desk

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.olympus.desk.data.*
import org.json.JSONObject

@Composable private fun CompanionPanel(title:String,content:@Composable ColumnScope.()->Unit){
 Column(Modifier.fillMaxWidth().padding(vertical=6.dp).clip(RoundedCornerShape(20.dp)).background(DeskColors.surface).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Text(title,color=DeskColors.text,fontSize=18.sp)
  content()
 }
}

/** A read-only investigation into the actual pod returned by the node API. */
@Composable internal fun WorkloadScreen(route:String,open:(String)->Unit){
 val context=LocalContext.current
 val node=route.substringAfter('@',"")
 val namespace=route.substringBefore('@').substringBefore('/')
 val name=route.substringBefore('@').substringAfter('/')
 LaunchedEffect(node){Pairing.read(context)?.let{LiveRepository.loadNode(it,node)}}
 val details=LiveRepository.nodeDetails
 CompanionPanel(name){
  Text("$namespace · $node",color=DeskColors.muted,fontSize=13.sp)
  val list=details?.optJSONArray("workloads")
  val pod=(0 until (list?.length()?:0)).map{list!!.getJSONObject(it)}.find{it.optString("namespace")==namespace&&it.optString("name")==name}
  when{
   details==null->Text(if(LiveRepository.nodeError)"Workload readings unavailable"else"Loading workload…",color=DeskColors.muted)
   pod==null->Text("Pod is no longer present on this node. The incident remains in history.",color=DeskColors.apricot)
   else->{
    Text("${pod.optString("phase")} · ${pod.optInt("ready")}/${pod.optInt("containers")} containers ready",color=DeskColors.green,fontSize=15.sp)
    Text("Restarts · ${pod.optInt("restarts")}",color=DeskColors.text)
    Text("Created · ${localTime(pod.optString("createdAt"))}",color=DeskColors.muted,fontSize=12.sp)
    val requests=pod.optJSONObject("requests")?:JSONObject()
    val limits=pod.optJSONObject("limits")?:JSONObject()
    DetailTable("Resource allocation",listOf("Resource","Requests","Limits"),listOf(140,150,150),listOf(
     listOf("CPU",String.format("%.0fm",requests.optDouble("cpu",0.0)*1000),if(limits.optDouble("cpu",0.0)==0.0)"Not set"else String.format("%.0fm",limits.optDouble("cpu",0.0)*1000)),
     listOf("Memory",String.format("%.0f MiB",requests.optDouble("memory",0.0)/1048576),if(limits.optDouble("memory",0.0)==0.0)"Not set"else String.format("%.0f MiB",limits.optDouble("memory",0.0)/1048576))
    ))
   }
  }
  DeskAction("Open node · $node"){open("node:$node")}
 }
 val related=(LiveRepository.active+LiveRepository.events).distinctBy{it.id}.filter{it.namespace==namespace&&it.pod==name}
 CompanionPanel("Related incidents & activity"){
  if(related.isEmpty())Text("No related records",color=DeskColors.muted)
  related.take(12).forEach{event->EventRow(event){open("event:${event.id}")}}
 }
}

@Composable internal fun MonitorDetailScreen(id:String,open:(String)->Unit){
 val monitor=LiveRepository.monitors.find{it.id==id}
 if(monitor==null){Text("Monitor unavailable",color=DeskColors.muted);return}
 CompanionPanel(monitor.name){
  Text(monitor.status,color=if(monitor.status=="Up")DeskColors.green else DeskColors.apricot,fontSize=24.sp)
  Text(monitor.responseMs?.let{"${it.toInt()} ms response time"}?:"Response time unavailable",color=DeskColors.text,fontSize=17.sp)
  Text("${monitor.type} · Uptime Kuma",color=DeskColors.muted,fontSize=12.sp)
  Text(if(LiveRepository.state.startsWith("Live"))"Current reading"else"Last known reading · ${LiveRepository.state}",color=DeskColors.muted,fontSize=12.sp)
  val history=LiveRepository.history["monitor:$id"].orEmpty()
  if(history.size>1){Text("Response time · recent session samples (ms)",color=DeskColors.muted,fontSize=12.sp);Spark(history,DeskColors.lilac)}
 }
 val related=(LiveRepository.active+LiveRepository.events).distinctBy{it.id}.filter{it.source.contains("Kuma",true)&&(it.title.contains(monitor.name,true)||it.detail.contains(monitor.name,true))}
 CompanionPanel("Related notifications"){
  if(related.isEmpty())Text("No matching notifications received",color=DeskColors.muted,fontSize=13.sp)
  related.take(12).forEach{event->EventRow(event){open("event:${event.id}")}}
 }
}

@Composable internal fun IntegrationDetailScreen(name:String,open:(String)->Unit){
 val integration=LiveRepository.integrations.find{it.name==name}
 val app=LiveRepository.services.find{it.name==name}
 CompanionPanel(name){
  Text(if(integration?.error?.isNotBlank()==true)"Some fields unavailable"else if(integration==null)"Integration unavailable"else"Connected integration",color=if(integration?.error?.isNotBlank()==true)DeskColors.apricot else DeskColors.green,fontSize=14.sp)
  integration?.fields?.entries?.forEach{(label,value)->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,color=DeskColors.muted);Text(value?.toInt()?.toString()?:"Unavailable",color=DeskColors.text)}}
  if(integration?.time?.isNotBlank()==true)Text("Collected · ${localTime(integration.time)}",color=DeskColors.muted,fontSize=12.sp)
  if(app!=null)DeskAction("Inspect application resources"){open("app:${app.name}")}
 }
 val related=(LiveRepository.active+LiveRepository.events).distinctBy{it.id}.filter{it.source.replace(" ","").equals(name.replace("-",""),true)||it.source.equals(name,true)}
 CompanionPanel("App notifications"){
  if(related.isEmpty())Text("No notifications received from this app",color=DeskColors.muted,fontSize=13.sp)
  related.take(12).forEach{event->EventRow(event){open("event:${event.id}")}}
 }
}
