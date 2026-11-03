package dev.olympus.desk

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.olympus.desk.data.*
import kotlinx.coroutines.launch
import org.json.JSONObject

private val TreePaper: Color @Composable get() = DeskColors.text
private val TreeMuted: Color @Composable get() = DeskColors.muted
private val TreeBlue: Color @Composable get() = DeskColors.blue
private val TreeGreen: Color @Composable get() = DeskColors.green
private val TreeWarm: Color @Composable get() = DeskColors.apricot
private data class ResourceNode(val id:String,val kind:String,val name:String,val namespace:String,val health:String,val sync:String,val parents:List<String>)
private fun shortKind(kind:String)=when(kind){"Application"->"APP";"Deployment"->"DEP";"ReplicaSet"->"RS";"StatefulSet"->"STS";"DaemonSet"->"DS";"Service"->"SVC";"PersistentVolumeClaim"->"PVC";"HTTPRoute"->"HTTP";"ConfigMap"->"CM";"Pod"->"POD";"ClusterRole"->"CR";"ClusterRoleBinding"->"CRB";"ServiceAccount"->"SA";"Secret"->"SEC";else->kind.take(4).uppercase()}

@Composable internal fun ResourceMiniMap(kinds:List<String>,pods:Int){
 val counts=kinds.groupingBy{it}.eachCount().toMutableMap();if(pods>0)counts["Pod"]=pods
 val priority=listOf("Deployment","StatefulSet","DaemonSet","Pod","Service","PersistentVolumeClaim","HTTPRoute","Job","CronJob")
 val grouped=counts.entries.sortedWith(compareBy({priority.indexOf(it.key).let{p->if(p<0)99 else p}},{it.key})).take(3)
 if(grouped.isEmpty()){Text("Resource map unavailable",color=TreeMuted,fontSize=11.sp);return}
 Row(Modifier.fillMaxWidth().padding(top=2.dp).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){
  Text("APP",color=TreeBlue,fontSize=9.sp,fontWeight=FontWeight.SemiBold)
  Text("→",color=TreeMuted,fontSize=14.sp)
  grouped.forEach{(kind,count)->Column(Modifier.clip(RoundedCornerShape(9.dp)).background(DeskColors.raised).padding(horizontal=6.dp,vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(shortKind(kind),color=TreePaper,fontSize=9.sp,fontWeight=FontWeight.SemiBold);Text("$count",color=TreeMuted,fontSize=10.sp)}}
 }
}

@Composable internal fun ApplicationScreen(name:String,open:(String)->Unit){
 val context=LocalContext.current;val scope=rememberCoroutineScope()
 val app=LiveRepository.services.find{it.name==name}
 LaunchedEffect(name){Pairing.read(context)?.let{LiveRepository.loadApp(it,name)}}
 if(app==null){Text("Application unavailable",color=TreeMuted);return}
 Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
  AppIcon(app.icon,name)
  Column(Modifier.weight(1f)){Text(app.health,color=if(app.health=="Healthy")TreeGreen else TreeWarm,fontWeight=FontWeight.SemiBold);Text("${app.sync} · ${app.namespace} · ${app.ready}/${app.pods} pods",color=TreeMuted,fontSize=11.sp)}
  if(app.url.startsWith("https://"))TextButton(onClick={context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(app.url)))}){Text("Open ↗",color=TreeBlue)}
 }
 if (app.lastSync.isNotBlank()) Text("Last sync · ${localTime(app.lastSync)}", color = TreeMuted, fontSize = 12.sp)
 DashboardActions(argoApplication = name)
 var tab by rememberSaveable(name){mutableStateOf("Resources")}
 CompactTabs(listOf("Resources","Usage","Events"),tab,{tab=it}){scope.launch{Pairing.read(context)?.let{LiveRepository.loadApp(it,name)}}}

 when(tab){
  "Resources"->{val details=LiveRepository.appDetails;if(details==null){Text(if(LiveRepository.appError)"Resource map unavailable"else"Loading resources…",color=TreeMuted,modifier=Modifier.padding(18.dp))}else{if(details.optBoolean("partial"))Text("Some resource data is unavailable",color=TreeWarm,fontSize=11.sp);ResourceTree(details,open)}}
  "Usage"->{DetailTable("Workload usage",listOf("Metric","Value"),listOf(240,250),listOf(listOf("CPU",if(app.cpu==null)"Unavailable"else"${(app.cpu*1000).toInt()}m"),listOf("Memory",if(app.memory==null)"Unavailable"else"${app.memory.toInt()} MiB"),listOf("Ready pods","${app.ready}/${app.pods}")));Text("CPU",color=TreeMuted,modifier=Modifier.padding(top=20.dp));Spark(LiveRepository.history["app:$name:cpu"]?:emptyList(),TreeBlue);Text("Memory",color=TreeMuted,modifier=Modifier.padding(top=20.dp));Spark(LiveRepository.history["app:$name:memory"]?:emptyList(),DeskColors.lilac)}
  "Events"->{val events=LiveRepository.events.filter{it.source.contains(name,true)||it.detail.contains(name,true)};if(events.isEmpty())Text("No matching events",color=TreeMuted,modifier=Modifier.padding(18.dp));events.forEach{event->EventRow(event){open("event:${event.id}")}}}
 }
}

@Composable private fun ResourceTree(details:JSONObject,open:(String)->Unit){
 val values=details.optJSONArray("nodes")?:return
 val nodes=(0 until values.length()).map{val item=values.getJSONObject(it);val parents=item.optJSONArray("parents");ResourceNode(item.getString("id"),item.getString("kind"),item.getString("name"),item.optString("namespace"),item.optString("health"),item.optString("sync"),(0 until (parents?.length()?:0)).map{parents!!.getString(it)})}
 val wide=wideWindow()
 var presentation by rememberSaveable(wide){mutableStateOf(if(wide)"Fit"else"List")}
 CompactTabs(listOf("Fit","Map","List"),presentation,{presentation=it})
 if(presentation=="List"){
  nodes.sortedWith(compareBy({it.kind},{it.name})).forEach{node->Column(Modifier.fillMaxWidth().clickable{if(node.kind=="Pod")open("pod:${node.namespace}/${node.name}@")}.padding(vertical=8.dp)){
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(node.kind,color=TreeBlue,fontSize=12.sp);Text(node.health.ifBlank{node.sync},color=if(node.health=="Healthy")TreeGreen else TreeMuted,fontSize=12.sp)}
   Text(node.name,color=TreePaper,fontSize=14.sp)
   if(node.namespace.isNotBlank())Text(node.namespace,color=TreeMuted,fontSize=11.sp)
   HorizontalDivider(color=DeskColors.raised,modifier=Modifier.padding(top=10.dp))
  }}
  return
 }
 val known=nodes.map{it.id}.toSet();val levels=mutableMapOf<String,Int>();nodes.filter{it.parents.none{p->p in known}}.forEach{levels[it.id]=0}
 repeat(nodes.size.coerceAtMost(20)){nodes.forEach{node->if(node.id !in levels&&node.parents.any{it in levels})levels[node.id]=(node.parents.mapNotNull{levels[it]}.maxOrNull()?:0)+1}}
 val layers=nodes.groupBy{levels[it.id]?:0}.toSortedMap().mapValues{(_,list)->list.sortedWith(compareBy({it.kind},{it.name}))}
 val maxRows=layers.values.maxOfOrNull{it.size}?:1
 val points=mutableMapOf<String,Pair<Int,Int>>();layers.forEach{(level,list)->list.forEachIndexed{index,node->points[node.id]=level*174 to (index*(if(presentation=="Fit")38 else 66)+if(level==0)(maxRows-1)*(if(presentation=="Fit")19 else 33) else 0)}}
 var selected by remember{mutableStateOf<ResourceNode?>(null)}
 val width=((layers.keys.maxOrNull()?:0)+1)*174+16;val nodeHeight=if(presentation=="Fit")34 else 50
 val height=maxRows*(if(presentation=="Fit")38 else 66)+16
 @Composable fun graph(){Box(Modifier.requiredSize(width.dp,height.dp).padding(12.dp)){
    Canvas(Modifier.matchParentSize()){
     nodes.forEach{node->val end=points[node.id]?:return@forEach;node.parents.forEach{parent->val start=points[parent]?:return@forEach;val a=Offset((start.first+146).dp.toPx(),(start.second+nodeHeight/2).dp.toPx());val b=Offset(end.first.dp.toPx(),(end.second+nodeHeight/2).dp.toPx());val path=Path().apply{moveTo(a.x,a.y);cubicTo(a.x+18.dp.toPx(),a.y,b.x-18.dp.toPx(),b.y,b.x,b.y)};drawPath(path,Color(0xFF66758D),style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))}}
    }
    nodes.forEach{node->val point=points[node.id]!!;Column(Modifier.offset(point.first.dp,point.second.dp).size(146.dp,nodeHeight.dp).clip(RoundedCornerShape(12.dp)).background(DeskColors.raised).border(1.dp,if(node.health=="Healthy")TreeGreen.copy(alpha=.5f)else Color(0xFF52607A),RoundedCornerShape(12.dp)).clickable{selected=node}.padding(if(presentation=="Fit")5.dp else 9.dp)){
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(shortKind(node.kind),color=TreeBlue,fontSize=9.sp,lineHeight=10.sp,fontWeight=FontWeight.Bold);Text(node.health.ifBlank{node.sync},color=if(node.health=="Healthy")TreeGreen else TreeMuted,fontSize=9.sp,lineHeight=10.sp,maxLines=1)}
     Text(node.name,color=TreePaper,fontSize=10.sp,lineHeight=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=if(presentation=="Fit")2.dp else 5.dp))
    }}
   }}
 if(presentation=="Fit"){
  androidx.compose.ui.layout.Layout(content={graph()},modifier=Modifier.fillMaxWidth().height(if(wide)190.dp else 300.dp).clip(RoundedCornerShape(16.dp)).background(DeskColors.surface)) { children,constraints->
   val rawWidth=width.dp.roundToPx();val rawHeight=height.dp.roundToPx()
   val item=children.single().measure(androidx.compose.ui.unit.Constraints.fixed(rawWidth,rawHeight))
   val scale=minOf(constraints.maxWidth.toFloat()/rawWidth,constraints.maxHeight.toFloat()/rawHeight,1f)
   layout(constraints.maxWidth,constraints.maxHeight){item.placeWithLayer(0,0){scaleX=scale;scaleY=scale;transformOrigin=androidx.compose.ui.graphics.TransformOrigin(0f,0f)}}
  }
 }else Box(Modifier.fillMaxWidth().height(if(wide)210.dp else 330.dp).clip(RoundedCornerShape(16.dp)).background(DeskColors.surface).horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())){graph()}
 selected?.let{node->DeskModal(node.name,{selected=null}){
  StatusPill(node.health.ifBlank{node.sync}.ifBlank{"Status not reported"},if(node.health=="Healthy")TreeGreen else TreeMuted)
  Text(listOf(node.kind,node.namespace).filter{it.isNotBlank()}.joinToString(" · "),color=TreeMuted,fontSize=12.sp)
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){if(node.kind=="Pod")DeskAction("Inspect container & logs",true){selected=null;open("pod:${node.namespace}/${node.name}@")};DeskAction("Close"){selected=null}}
 }}
}

@Composable
internal fun DashboardActions(argoApplication: String? = null) {
 val context = LocalContext.current
 val argo = LiveRepository.dashboardLinks["argo"]
 val grafana = LiveRepository.dashboardLinks["grafana"]
 if (argo == null && grafana == null) return
 fun open(url: String) {
  runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
 }
 Row(
  Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
  horizontalArrangement = Arrangement.spacedBy(8.dp)
 ) {
  argo?.let { address ->
   DeskAction("Open Argo CD") {
    open(if (argoApplication == null) address else android.net.Uri.parse(address).buildUpon()
     .appendPath("applications").appendPath("argocd").appendPath(argoApplication).build().toString())
   }
  }
  grafana?.let { address -> DeskAction("Open Grafana") { open(address) } }
 }
}
