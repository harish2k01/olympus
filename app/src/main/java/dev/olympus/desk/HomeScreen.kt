package dev.olympus.desk

import androidx.compose.foundation.selection.selectable

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.olympus.desk.data.*
import kotlinx.coroutines.*
import org.json.JSONObject

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val Ink: Color @Composable get() = DeskColors.background
private val Card: Color @Composable get() = DeskColors.surface
private val Cobalt: Color @Composable get() = DeskColors.blue
private val Apricot: Color @Composable get() = DeskColors.apricot
private val Lilac: Color @Composable get() = DeskColors.lilac
private val Mint: Color @Composable get() = DeskColors.green
private val Muted: Color @Composable get() = DeskColors.muted
private val Paper: Color @Composable get() = DeskColors.text
private fun Float?.fmt()=if(this==null)"—" else String.format(java.util.Locale.US,"%.1f",this)
private fun stamp(value:String)=try{DateTimeFormatter.ofPattern("dd MMM · HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.parse(value))}catch(_:Exception){"Time unavailable"}

@Composable internal fun HomeApp(notificationId:String="",notificationNonce:Long=0){
 val context=LocalContext.current
 val wide=wideWindow()
 val compactHeader=!wide&&androidx.compose.ui.platform.LocalDensity.current.fontScale>1.15f
 val prefs=remember{context.getSharedPreferences("home-v3",0)}
 var tab by rememberSaveable{mutableStateOf("Home")}
 var servicesSection by rememberSaveable{mutableStateOf("Monitors")}
 var selectedEvent by remember{mutableStateOf<LabEvent?>(null)}
 var detail by rememberSaveable{mutableStateOf("")}
 LaunchedEffect(notificationId,notificationNonce){if(notificationId.isNotBlank()){tab="Attention";detail=if(notificationId=="-inbox-")""else"event:$notificationId"}}
 var detailHistory by rememberSaveable{mutableStateOf(arrayListOf<String>())}
 fun backDetail(){if(detailHistory.isNotEmpty()){detail=detailHistory.last();detailHistory=ArrayList(detailHistory.dropLast(1))}else detail=""}
 var menu by remember{mutableStateOf(false)}
 var settings by rememberSaveable{mutableStateOf(Pairing.read(context) == null)}
 var message by remember{mutableStateOf("")}
 var reducedMotion by remember{mutableStateOf(prefs.getBoolean("reducedMotion",false))}
 var keepAwake by remember{mutableStateOf(prefs.getBoolean("keepAwake",true))}
 var showTests by remember{mutableStateOf(prefs.getBoolean("showTests",false))}
 DisposableEffect(keepAwake){val window=(context as? Activity)?.window;if(keepAwake)window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)else window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);onDispose{}}
 BackHandler(detail.isNotEmpty()){backDetail()}
 CompositionLocalProvider(LocalReducedMotion provides reducedMotion,LocalShowTests provides showTests){
 var lastInteraction by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
 val dockAutoHide = wide && !settings && detail.isEmpty()
 var dockVisible by remember { mutableStateOf(true) }
 var touchInProgress by remember { mutableStateOf(false) }
 LaunchedEffect(dockAutoHide, lastInteraction, touchInProgress) {
  dockVisible = true
  if (dockAutoHide && !touchInProgress) {
   delay(4_000L)
   dockVisible = false
  }
 }
 if (tab == "Home" && detail.isEmpty() && !settings) {
  GlanceScreen(
   wide = wide,
   nightEnabled = prefs.getBoolean("nightMode", true),
   openIncident = { incident -> tab = "Attention"; selectedEvent = incident; detail = "event:${incident.id}"; detailHistory = arrayListOf() },
   openAttention = { tab = "Attention"; detail = ""; detailHistory = arrayListOf() }
  )
  return@CompositionLocalProvider
 }
 Surface(color=Ink,modifier=Modifier.fillMaxSize().pointerInput(wide) {
  if (wide) awaitPointerEventScope {
   while (true) {
    val event = awaitPointerEvent(PointerEventPass.Initial)
    val pressed = event.changes.any { it.pressed }
    if (pressed || touchInProgress) lastInteraction = android.os.SystemClock.elapsedRealtime()
    touchInProgress = pressed
   }
  }
 }) {
 Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)){
 val destinations = listOf("Home" to "NavHome", "Cluster" to "NavCluster", "Services" to "NavServices", "Attention" to "NavInbox")
 val navigate: (String) -> Unit = {
  tab=it
  detail=""
  detailHistory=arrayListOf()
 }
 Column(Modifier.weight(1f).fillMaxHeight()){
 Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp)) {
  if (wide && tab == "Attention" && (detail.isEmpty() || detail.startsWith("event:"))) {
   Spacer(Modifier.height(12.dp))
  } else if (detail.isEmpty()) {
   if (wide && tab == "Home") {
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
     LiveStatus()
     Box(Modifier.weight(1f).padding(start = 8.dp)) { HomeSourceStatus() }
     ReferenceSettings { settings = true }
    }
   } else {
    Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
     Text(if (tab == "Home") "Olympus" else tab, Modifier.weight(1f), color = Paper, fontSize = 22.sp, fontWeight = FontWeight.Bold)
     ReferenceSettings { settings = true }
    }
    if (tab == "Home") Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
     LiveStatus()
     Box(Modifier.weight(1f).padding(start = 8.dp)) { HomeSourceStatus() }
    }
   }
  } else
  Row(Modifier.fillMaxWidth().height(44.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
   if(detail.isNotEmpty())TextButton(onClick={backDetail()},modifier=Modifier.size(40.dp),contentPadding=PaddingValues(0.dp)){LabGlyph("Back",Paper,23.dp)}else Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF4A63EA)),contentAlignment=Alignment.Center){LabGlyph("Brand",Color.White,22.dp)}
   Text(if(detail.isEmpty())if(tab=="Home")"Olympus"else tab else if(detail.startsWith("event:")&&selectedEvent?.id==detail.substringAfter(":"))incidentTitle(selectedEvent!!)else routeTitle(detail),fontSize=18.sp,fontWeight=FontWeight.SemiBold,color=Paper,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
   if(detail.isEmpty())LiveStatus()
   Row{if(detail.isEmpty())IconButton(onClick={settings=true},modifier=Modifier.size(44.dp)){LabGlyph("Settings",Muted,21.dp)};Box{
    IconButton(onClick={menu=true},modifier=Modifier.size(44.dp).semantics { contentDescription = "More options" }){Text("•••",color=Muted)}
    DeskPopup(menu,{menu=false},if(detail.isNotBlank())listOf("Copy reference" to {(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Olympus resource",detail));message="Reference copied"})else listOf("Settings" to {settings=true}))
   }}
  }

  if(message.isNotEmpty()){Text(message,color=Mint,fontSize=12.sp);LaunchedEffect(message){delay(2200);message=""}}
  BoxWithConstraints(Modifier.weight(1f)){val viewport=maxHeight
  AnimatedContent(targetState=if(wide&&tab=="Attention"&&detail.startsWith("event:"))"Attention" else detail.ifEmpty{tab},modifier=Modifier.fillMaxSize(),transitionSpec={if(reducedMotion)EnterTransition.None togetherWith ExitTransition.None else fadeIn(tween(180)) togetherWith fadeOut(tween(120))},label="page"){page->
   if(wide&&tab=="Attention"&&(page=="Attention"||page.startsWith("event:"))){
    Row(Modifier.fillMaxSize().padding(bottom = 12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
     Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Card).verticalScroll(rememberScrollState()).padding(14.dp)){
      AttentionScreen(deskPane = true){selectedEvent=it;detail="event:${it.id}"}
     }
     Column(Modifier.weight(1.5f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Card).padding(14.dp)){
      val event = selectedEvent?.takeIf { detail == "event:${it.id}" } ?: currentVerdict().incidents.firstOrNull()
      val eventId = if (detail.startsWith("event:")) detail.substringAfter(":") else event?.id
      if(eventId == null) DeskEmpty("Select an incident","Its timeline and related resources will appear here.")
      else EventRoute(eventId,event,{selectedEvent=it},deskPane = true){target->
       detailHistory=ArrayList(detailHistory+detail)
       detail=target
      }
     }
    }
   }else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=8.dp)){
    when{
     page.startsWith("cluster-history:") -> {
      val metric = page.substringAfter(":").substringBefore("@")
      MetricHistoryPanel("kind=cluster-$metric", initialRange = page.substringAfter("@", "1h"))
     }
     page.startsWith("events-history:")->NotificationInbox(page.substringAfter(":"),"History",true){selectedEvent=it;detail="event:${it.id}"}
     page.startsWith("events-source:")->NotificationInbox(page.substringAfter(":")){selectedEvent=it;detail="event:${it.id}"}
     page.startsWith("pod:")->WorkloadScreen(page.removePrefix("pod:")){target->detailHistory=ArrayList(detailHistory+detail);detail=target}
     page.startsWith("monitor:")->MonitorDetailScreen(page.removePrefix("monitor:")){target->detailHistory=ArrayList(detailHistory+detail);detail=target}
     page.startsWith("integration:")->IntegrationDetailScreen(page.removePrefix("integration:")){target->detailHistory=ArrayList(detailHistory+detail);detail=target}
     page.startsWith("node:")->NodeDetailScreen(page.substringAfter(":")){target->detailHistory=ArrayList(detailHistory+detail);detail=target}
     page.startsWith("app:")->ApplicationScreen(page.substringAfter(":")){target->detailHistory=ArrayList(detailHistory+detail);detail=target}
     page.startsWith("event:")->EventRoute(page.substringAfter(":"),selectedEvent,{selectedEvent=it}){target->detailHistory=ArrayList(detailHistory+detail);detail=target}
     page=="Cluster"->ClusterOverview(
      openHistory = { metric, range -> detail = "cluster-history:$metric@$range" },
      openNode = { detail = "node:$it" }
     )
     page=="Services"->{ServicesSummary();ServicesTabs(if (servicesSection == "Monitors") "Monitors" else "Integrations") { servicesSection = it };if(servicesSection=="Monitors")MonitorScreen{detail="monitor:$it"}else IntegrationDirectory(openArgo={detail="Apps"}){detail="integration:$it"}}
     page=="Attention"->AttentionScreen{selectedEvent=it;detail="event:${it.id}"}
     page=="Nodes"->Nodes{detail="node:$it"}
     page=="Events"->NotificationInbox{selectedEvent=it;detail="event:${it.id}"}
     page=="Apps"->Apps{detail="app:$it"}
     page=="Monitors"->MonitorScreen{detail="monitor:$it"}
     else->{
     }
    }
   }
  }
 }


 }
 if (wide && (!dockAutoHide || dockVisible)) {
  Row(
   Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp)
    .clip(RoundedCornerShape(24.dp)).background(Card).padding(3.dp),
   horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
   destinations.forEach { (destination, icon) ->
    val selected = tab == destination
    val tint = if (selected) DeskColors.onSelected else Muted
    Row(
     Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(20.dp))
      .background(if (selected) DeskColors.selected else Color.Transparent)
      .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.Tab, onClick = { navigate(destination) })
      .semantics { contentDescription = destination }.padding(horizontal = 12.dp),
     verticalAlignment = Alignment.CenterVertically,
     horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
     LabGlyph(icon, tint, 18.dp, decorative = true)
     Text(destination, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
   }
  }
 } else if (wide) {
  Spacer(Modifier.height(56.dp))
 } else NavigationBar(containerColor = Card) {
  destinations.forEach { (destination, icon) ->
   NavigationBarItem(
    selected=tab==destination,
    modifier = Modifier.semantics { contentDescription = destination },
    onClick={navigate(destination)},
    icon={LabGlyph(icon,if(tab==destination)DeskColors.onSelected else Muted,22.dp, decorative = true)},
    label={Text(destination,color=if(tab==destination)Paper else Muted)}
   )
  }
 }
 }
 }
 }
 }
 if(settings)SettingsScreen(prefs,reducedMotion,keepAwake,showTests,
  {reducedMotion=it},{keepAwake=it},{showTests=it},
  {settings=false;detail="Monitors"},{settings=false})

}

@Composable private fun LiveStatus() {
 val state = LiveRepository.state
 val connected = state.startsWith("Live")
 val age = snapshotAge()
 val label = when {
  connected && age != null -> "Live · $age"
  connected -> "Live"
  else -> state
 }
 Row(Modifier.clip(RoundedCornerShape(16.dp)).background(DeskColors.raised).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
  VerdictMark(if (connected) Verdict.Calm else Verdict.Unknown)
  Text(label, color = Paper, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 104.dp))
 }
}

private fun snapshotAge(): String? {
 val stamp = runCatching { Instant.parse(LiveRepository.time) }.getOrNull() ?: return null
 val seconds = (Instant.now().epochSecond - stamp.epochSecond).coerceAtLeast(0)
 return if (seconds < 60) "$seconds s" else "${seconds / 60} min"
}

@Composable private fun HomeSourceStatus() {
 if (!LiveRepository.state.startsWith("Live")) return
 val now = Instant.now()
 val stale = LiveRepository.observedAt.mapNotNull { (source, stamp) ->
  val age = observationAgeSeconds(stamp, now)
  if (age != null && classifyFreshness(age) == Freshness.Stale) "$source · ${formatObservationAge(age)} old" else null
 }
 val unavailable = stale + LiveRepository.signals.filterValues { !it }.keys.map { "$it unavailable" } +
  LiveRepository.integrations.filter { it.error.isNotBlank() }.map { "${it.name} integration degraded" }
 if (unavailable.isEmpty()) return
 Row(
  Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
  horizontalArrangement = Arrangement.spacedBy(6.dp)
 ) {
  unavailable.forEach { source ->
   Row(
    Modifier.clip(RoundedCornerShape(16.dp)).background(DeskColors.raised).padding(horizontal = 8.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    verticalAlignment = Alignment.CenterVertically
   ) {
    VerdictMark(Verdict.Unknown)
    Text(source, color = Muted, fontSize = 11.sp)
   }
  }
 }
}

@Composable internal fun Spark(values:List<Float>,color:Color,percent:Boolean=false){if(values.size<2){Text("Collecting trend…",color=Muted,fontSize=10.sp);return};Canvas(Modifier.fillMaxWidth().height(24.dp)){val low=0f;val range=if(percent)100f else values.max().coerceAtLeast(.1f);val path=Path();values.forEachIndexed{i,value->val x=i.toFloat()/(values.size-1)*size.width;val y=size.height-(value.coerceIn(0f,range)-low)/range*(size.height-4)-2;if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};drawPath(path,color,style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))}}

@Composable private fun Title(value:String){Text(value,color=Paper,fontSize=25.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(vertical=12.dp))}
@Composable internal fun Usage(label:String,value:Float?,color:Color){Row(Modifier.fillMaxWidth().padding(top=5.dp,bottom=4.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(label,color=Muted,fontSize=12.sp);Text("${value.fmt()}%",color=Paper,fontSize=12.sp)};if(value!=null)LinearProgressIndicator(progress={(value/100).coerceIn(0f,1f)},color=color,trackColor=DeskColors.raised,modifier=Modifier.fillMaxWidth().height(3.dp),drawStopIndicator={})}
@Composable private fun Nodes(open:(String)->Unit){
 val wide=wideWindow()

 Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){Box(Modifier.weight(1f)){Usage("Cluster CPU",LiveRepository.cpuPercent,Cobalt)};Box(Modifier.weight(1f)){Usage("Cluster memory",LiveRepository.memoryPercent,Lilac)}}
 LiveRepository.nodes.chunked(if(wide)2 else 1).forEach{row->Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){row.forEach{node->Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Card).clickable{open(node.name)}.padding(12.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(node.name,color=Paper,fontSize=16.sp);Text(if(node.ready)"Ready ↗"else"Not ready ↗",color=if(node.ready)Mint else Apricot,fontSize=12.sp)}
  Usage("CPU · ${node.cores} cores",if(node.available)node.cpuPercent else null,Cobalt)
  Usage("Memory · ${node.memoryUsedGiB.fmt()} / ${node.memoryTotalGiB.fmt()} GiB",if(node.available)node.memoryUsedGiB/node.memoryTotalGiB*100 else null,Lilac)
 }}}}
}
private val iconCache=android.util.LruCache<String,android.graphics.Bitmap>(40)
@Composable internal fun AppIcon(inputIcon: String, name: String) {
 val context = LocalContext.current
 val key = "$name:$inputIcon"
 var bitmap by remember(key) { mutableStateOf(iconCache.get(key)) }
 LaunchedEffect(key) {
  if (bitmap == null && (inputIcon.isNotBlank() || name.lowercase() in listOf("argocd", "alloy", "kubernetes", "prometheus"))) {
   bitmap = withContext(Dispatchers.IO) {
    runCatching {
     val file = java.io.File(context.cacheDir, "asset-${key.hashCode()}.png")
     val bytes = if (file.isFile && file.length() in 1..(512L * 1024)) file.readBytes()
      else LiveRepository.requestIcon(Pairing.read(context) ?: error("Pairing required"), name)
       .also { file.writeBytes(it) }
     BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 4 })
      ?.also { iconCache.put(key, it) }
    }.getOrNull()
   }
  }
 }
 bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(26.dp)) }
  ?: Box(
   Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(DeskColors.selected),
   contentAlignment = Alignment.Center
  ) { Text(name.take(1).uppercase(), color = Paper, fontSize = 12.sp) }
}
@Composable private fun Apps(open:(String)->Unit){
 var search by rememberSaveable{mutableStateOf("")}
 var filter by rememberSaveable{mutableStateOf("All")}
 @Composable fun searchField(){DeskSearch(search,{search=it},"Search applications")}
 if(wideWindow())Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
  Box(Modifier.weight(1f)){searchField()};Box(Modifier.width(218.dp)){CompactTabs(listOf("All","Needs attention"),filter,{filter=it})};Text("${LiveRepository.services.size}",color=Muted,fontSize=11.sp)
 }else{searchField();CompactTabs(listOf("All","Needs attention"),filter,{filter=it})}
 val apps=LiveRepository.services.filter{it.name.contains(search,true)&&(filter=="All"||it.health!="Healthy"||it.sync!="Synced")}
 val columns=if(wideWindow())3 else 2
 if(apps.isEmpty())Text("No matching apps",color=Muted,modifier=Modifier.padding(16.dp))
 apps.chunked(columns).forEach{row->Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
  row.forEach{app->Column(Modifier.weight(1f).heightIn(min=110.dp).clip(RoundedCornerShape(17.dp)).background(Card).clickable{open(app.name)}.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){AppIcon(app.icon,app.name);Text(app.name,color=Paper,fontWeight=FontWeight.Medium,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){Box(Modifier.size(5.dp).background(if(app.health=="Healthy")Mint else Apricot,RoundedCornerShape(4.dp)));Text(app.health,color=if(app.health=="Healthy")Mint else Apricot,fontSize=11.sp);Text(app.sync,color=Muted,fontSize=10.sp,maxLines=1)}
   Text("${app.ready}/${app.pods} pods · ${app.namespace}",color=Muted,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
   ResourceMiniMap(app.resources,app.pods)
  }}
  repeat(columns-row.size){Spacer(Modifier.weight(1f))}
 }}
}
@Composable internal fun EventRow(event:LabEvent,click:()->Unit){Column(Modifier.fillMaxWidth().padding(top=10.dp).clip(RoundedCornerShape(18.dp)).background(Card).clickable(onClick=click).padding(16.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(event.source,color=Lilac,fontSize=11.sp);Text(stamp(event.time),color=Muted,fontSize=10.sp)};Text(event.title,color=Paper,fontSize=15.sp,modifier=Modifier.padding(vertical=8.dp));Text(event.kind+" ↗",color=Apricot,fontSize=11.sp)}}

@Composable private fun IntegrationDirectory(openArgo: () -> Unit, open: (String) -> Unit) {
 Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
  Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
   CompanionPanel("Connected tools") {
    val apps = LiveRepository.services
    val argoStatus = if (LiveRepository.signals["Argo CD"] == false) "Unavailable"
     else if (apps.isEmpty()) "No applications reported"
     else "${apps.count { it.sync == "Synced" }} of ${apps.size} applications synced"
    val entries = listOf(Triple("Argo CD", argoStatus, openArgo)) +
     LiveRepository.integrations.filterNot { it.kind in listOf("radarr", "sonarr") }.map { service ->
      Triple(service.name, if (service.error.isBlank()) "Connected" else "Unavailable", { open(service.name) })
     }
    entries.forEachIndexed { index, (name, status, action) ->
     if (index > 0) HorizontalDivider(color = DeskColors.raised)
     Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = action),
      verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Column(Modifier.weight(1f)) {
       Text(name, color = Paper, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
       Text(status, color = Muted, fontSize = 13.sp)
      }
      Text("›", color = Muted, fontSize = 22.sp)
     }
    }
   }
  }
 }
}

private fun routeTitle(route:String):String=when {
 route.startsWith("cluster-history:")->if(route.substringAfter(":").substringBefore("@") == "cpu") "CPU · cluster" else "Memory · cluster"
 route.startsWith("monitor:")->LiveRepository.monitors.find{it.id==route.substringAfter(":")}?.name?:"Monitor"
 route.startsWith("pod:")->route.substringAfter('/').substringBefore('@')
 route.startsWith("event:")->(LiveRepository.active+LiveRepository.events).find{it.id==route.substringAfter(":")}?.let(::incidentTitle)?:"Incident"
 route.startsWith("events-history:")->"Recent activity"
 route.startsWith("events-source:")->"Inbox · ${route.substringAfter(":")}"
 else->route.substringAfter(":")
}

@Composable
private fun ReferenceSettings(open: () -> Unit) {
 IconButton(onClick = open, modifier = Modifier.size(44.dp).semantics { contentDescription = "Settings" }
  .clip(RoundedCornerShape(22.dp)).background(Card)) {
  LabGlyph("Sliders", Paper, 22.dp, decorative = true)
 }
}

