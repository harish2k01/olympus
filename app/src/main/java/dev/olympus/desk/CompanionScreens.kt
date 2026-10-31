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
 Column(Modifier.fillMaxWidth().padding(vertical=6.dp).clip(RoundedCornerShape(16.dp)).background(DeskColors.surface).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Text(title,color=DeskColors.text,fontSize=18.sp)
  content()
 }
}

/** On-demand bounded inspection; pod environment and secrets are never requested. */
@Composable internal fun WorkloadScreen(route:String,open:(String)->Unit){
 val context=LocalContext.current
 val namespace=route.substringBefore('@').substringBefore('/')
 val name=route.substringBefore('@').substringAfter('/')
 var data by remember(route){mutableStateOf<JSONObject?>(null)}
 var failed by remember(route){mutableStateOf(false)}
 var refresh by remember{mutableIntStateOf(0)}
 var section by androidx.compose.runtime.saveable.rememberSaveable(route){mutableStateOf("Containers")}
 var container by androidx.compose.runtime.saveable.rememberSaveable(route){mutableStateOf("")}
 var previous by androidx.compose.runtime.saveable.rememberSaveable(route){mutableStateOf(false)}
 var logRefresh by remember{mutableIntStateOf(0)}
 var log by remember(route){mutableStateOf<String?>(null)}
 var logError by remember{mutableStateOf(false)}
 var collected by remember{mutableStateOf("")}
 fun encoded(v:String)=java.net.URLEncoder.encode(v,"UTF-8")
 val identity="namespace=${encoded(namespace)}&pod=${encoded(name)}"
 LaunchedEffect(route,refresh){data=null;failed=false;try{val pair=Pairing.read(context)?:error("Not paired");data=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){JSONObject(LiveRepository.request(pair,"/v1/workload?$identity"))};if(container.isBlank())container=data?.optJSONArray("containers")?.optJSONObject(0)?.optString("Name").orEmpty()}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){failed=true}}
 val node=data?.optString("node").orEmpty().ifBlank{route.substringAfter('@',"")}
 Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
  StatusPill(data?.optString("phase")?:if(failed)"Unavailable"else"Loading",if(data?.optString("phase")=="Running")DeskColors.green else DeskColors.apricot)
  Text(namespace,color=DeskColors.muted,fontSize=12.sp,modifier=Modifier.weight(1f))
  if(node.isNotBlank())DeskAction(node){open("node:$node")}
 }
 DeskSegments(listOf("Containers","Logs","Events"),section,{section=it}){refresh++}
 val containers=listOf("containers","initContainers").flatMap{key->val list=data?.optJSONArray(key);(0 until (list?.length()?:0)).map{list!!.getJSONObject(it)}}
 when(section){
  "Containers"->{
   if(failed){DeskEmpty("Workload unavailable","The pod may have moved or terminated.");DeskAction("Retry"){refresh++}}
   containers.forEach{item->
    val statuses=listOf("statuses","initStatuses").flatMap{key->val list=data?.optJSONArray(key);(0 until (list?.length()?:0)).map{list!!.getJSONObject(it)}}
    val status=statuses.find{it.optString("Name")==item.optString("Name")}
    CompanionPanel(item.optString("Name")){
     Text(item.optString("Image"),color=DeskColors.muted,fontSize=12.sp)
     Text("${if(status?.optBoolean("Ready")==true)"Ready"else"Not ready"} · ${status?.optInt("RestartCount")?:0} restarts",color=DeskColors.text,fontSize=13.sp)
     status?.optJSONObject("State")?.keys()?.asSequence()?.forEach{key->val state=status.optJSONObject("State")?.optJSONObject(key);Text(key.replaceFirstChar{it.uppercase()}+state?.optString("reason").orEmpty().let{if(it.isBlank())""else" · $it"},color=DeskColors.muted,fontSize=12.sp)}
     DeskAction("View logs"){container=item.optString("Name");section="Logs"}
    }
   }
  }
  "Logs"->{
   if(containers.isNotEmpty())DeskSegments(containers.map{it.optString("Name")},container,{container=it})
   DeskSegments(listOf("Current","Previous"),if(previous)"Previous"else"Current",{previous=it=="Previous"}){logRefresh++}
   LaunchedEffect(route,container,previous,logRefresh){log=null;logError=false;if(container.isNotBlank())try{val pair=Pairing.read(context)?:error("Not paired");val result=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){JSONObject(LiveRepository.request(pair,"/v1/workload/logs?$identity&container=${encoded(container)}&previous=$previous"))};log=result.optString("text");collected=result.optString("collectedAt")}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){logError=true}}
   when{logError->{DeskEmpty("Logs unavailable",if(previous)"No previous container logs, or the pod has terminated."else"The container may not have started, or access is unavailable.");DeskAction("Retry"){logRefresh++}};log==null->Text("Loading logs…",color=DeskColors.muted);log!!.isBlank()->DeskEmpty("No log lines in the last hour");else->{Text("Up to 200 lines · ${localTime(collected)}",color=DeskColors.muted,fontSize=11.sp);androidx.compose.foundation.text.selection.SelectionContainer{Text(log!!,color=DeskColors.text,fontSize=11.sp,lineHeight=16.sp,fontFamily=androidx.compose.ui.text.font.FontFamily.Monospace,modifier=Modifier.fillMaxWidth().background(DeskColors.surface,RoundedCornerShape(12.dp)).padding(12.dp))}}}
  }
  else->{val related=(LiveRepository.active+LiveRepository.events).distinctBy{it.id}.filter{it.namespace==namespace&&it.pod==name};if(related.isEmpty())DeskEmpty("No related activity");related.forEach{event->EventRow(event){open("event:${event.id}")}}}
 }
}

@Composable internal fun MonitorDetailScreen(id:String,open:(String)->Unit){
 val monitor=LiveRepository.monitors.find{it.id==id}
 if(monitor==null){Text("Monitor unavailable",color=DeskColors.muted);return}
 Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
  StatusPill(monitor.status,if(monitor.status=="Up")DeskColors.green else DeskColors.apricot)
  Text(monitor.responseMs?.let{"${it.toInt()} ms"}?:"Latency unavailable",color=DeskColors.text,fontSize=18.sp)
  Text("${monitor.type.uppercase()} · Uptime Kuma",color=DeskColors.muted,fontSize=11.sp,modifier=Modifier.weight(1f))
  if(!LiveRepository.state.startsWith("Live"))Text("Last known",color=DeskColors.apricot,fontSize=11.sp)
 }
 MetricHistoryPanel("kind=monitor&id="+java.net.URLEncoder.encode(id,"UTF-8"))
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
