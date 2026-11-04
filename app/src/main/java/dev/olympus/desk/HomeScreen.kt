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

@Composable private fun ClockWidget(height:Int,modifier:Modifier,title:String=""){
 var now by remember{mutableStateOf(java.time.ZonedDateTime.now())}
 LaunchedEffect(Unit){while(isActive){now=java.time.ZonedDateTime.now();delay(30000)}}
 BoxWithConstraints(modifier.fillMaxSize()){
  if(maxHeight<100.dp){Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text(now.format(DateTimeFormatter.ofPattern("HH:mm")),color=Color.White,fontSize=30.sp);ClusterMascot(Modifier.size(36.dp))};return@BoxWithConstraints}
  val compact=maxHeight<180.dp
  val greeting=when(currentClusterMood()){ClusterMood.Healthy->"${when(now.hour){in 5..11->"A good morning";in 12..16->"A good afternoon";in 17..22->"A good evening";else->"A quiet night"}}\nfor your lab.";ClusterMood.Attention->"Your lab needs\na look.";ClusterMood.Critical->"An issue needs\nyour attention.";ClusterMood.Unknown->"Waiting for\nfresh data."}
  Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(3.dp)){
   if(!compact)Text(title.ifBlank{now.dayOfWeek.name},color=Color.White.copy(alpha=.86f),fontSize=10.sp)
   Text(now.format(DateTimeFormatter.ofPattern("HH:mm")),color=Color.White,fontSize=if(compact)32.sp else 43.sp,lineHeight=if(compact)36.sp else 48.sp,fontWeight=FontWeight.Light)
   if(height>1){Text(now.format(DateTimeFormatter.ofPattern(if(compact)"EEE · d MMM yyyy"else"d MMMM yyyy")),color=Color.White,fontSize=11.sp)
    Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.CenterStart){ClusterMascot(Modifier.size(if(compact)44.dp else 64.dp))}
    Text(greeting,color=Color.White,fontSize=if(compact)14.sp else 16.sp,lineHeight=if(compact)18.sp else 21.sp,fontWeight=FontWeight.Medium)
   }
  }
 }
}

@Composable internal fun HomeApp(notificationId:String="",notificationNonce:Long=0){
 val context=LocalContext.current
 val wide=wideWindow()
 val compactHeader=!wide&&androidx.compose.ui.platform.LocalDensity.current.fontScale>1.15f
 val prefs=remember{context.getSharedPreferences("home-v3",0)}
 val layoutKey=if(wide)"landscape-v7" else "portrait-v7"
 val priorDefaultKey = "$layoutKey-before-redesign"
 var gridColumns by remember(wide){mutableIntStateOf(if(wide)prefs.getInt("landscape-columns-v7",8)else 4)}
 var widgets by remember(layoutKey){mutableStateOf(try{prefs.getString(layoutKey,null)?.let{DeskLayout.decode(it)}?:DeskLayout.defaults(wide)}catch(_:Exception){DeskLayout.defaults(wide)})}
 LaunchedEffect(layoutKey) {
  val stored = prefs.getString(layoutKey, null)
  if (stored != null && !prefs.contains(priorDefaultKey) && (!wide || gridColumns == 8) &&
      DeskLayout.matchesPreviousDefault(widgets, wide)) {
   widgets = DeskLayout.defaults(wide)
   prefs.edit().putString(priorDefaultKey, stored).putString(layoutKey, DeskLayout.encode(widgets)).apply()
  }
 }

 var undoHistory by rememberSaveable(layoutKey){mutableStateOf(arrayListOf<String>())}
 val previousLayout=undoHistory.lastOrNull()?.let{DeskLayout.decode(if(it.startsWith("["))it else JSONObject(it).getString("widgets"))}
 var selectedWidgetId by rememberSaveable{mutableStateOf("")}
 fun save(items:List<DeskWidget>){undoHistory=ArrayList((undoHistory+JSONObject().put("columns",gridColumns).put("widgets",DeskLayout.encode(widgets)).toString()).takeLast(20));widgets=items;prefs.edit().putString(layoutKey,DeskLayout.encode(items)).apply()}
 var tab by rememberSaveable{mutableStateOf("Home")}
 var servicesSection by rememberSaveable{mutableStateOf("Monitors")}
 var selectedEvent by remember{mutableStateOf<LabEvent?>(null)}
 var detail by rememberSaveable{mutableStateOf("")}
 LaunchedEffect(notificationId,notificationNonce){if(notificationId.isNotBlank()){tab="Inbox";detail=if(notificationId=="-inbox-")""else"event:$notificationId"}}
 var detailHistory by rememberSaveable{mutableStateOf(arrayListOf<String>())}
 fun backDetail(){if(detailHistory.isNotEmpty()){detail=detailHistory.last();detailHistory=ArrayList(detailHistory.dropLast(1))}else detail=""}
 var editing by rememberSaveable{mutableStateOf(false)}
 var picker by rememberSaveable{mutableStateOf(false)}
 var configuring by remember{mutableStateOf<DeskWidget?>(null)}
 var menu by remember{mutableStateOf(false)}
 var settings by rememberSaveable{mutableStateOf(Pairing.read(context) == null)}
 var importLayout by remember{mutableStateOf(false)}
 var message by remember{mutableStateOf("")}
 var dim by remember{mutableFloatStateOf(prefs.getFloat("brightness",.7f))}
 var reducedMotion by remember{mutableStateOf(prefs.getBoolean("reducedMotion",false))}
 var dimMode by remember{mutableStateOf(prefs.getBoolean("deskMode",false))}
 DisposableEffect(prefs) {
  val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { settings, key ->
   if (key == "deskMode") dimMode = settings.getBoolean(key, false)
  }
  prefs.registerOnSharedPreferenceChangeListener(listener)
  onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
 }
 var keepAwake by remember{mutableStateOf(prefs.getBoolean("keepAwake",true))}
 var showTests by remember{mutableStateOf(prefs.getBoolean("showTests",false))}
 var dockSeconds by remember{mutableIntStateOf(prefs.getInt("dockSeconds",4))}
 var gridGap by remember{mutableIntStateOf(prefs.getInt("gridGap",10))}
 DisposableEffect(keepAwake){val window=(context as? Activity)?.window;if(keepAwake)window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)else window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);onDispose{}}
 var focusMode by remember{mutableStateOf(prefs.getBoolean("focusMode",false))}
 var shift by remember{mutableIntStateOf(0)}
 LaunchedEffect(dimMode){while(dimMode){delay(60000);shift=(shift+1)%3}}
 DisposableEffect(dimMode,dim){val window=(context as? Activity)?.window;val old=window?.attributes?.screenBrightness;window?.attributes=window?.attributes?.apply{screenBrightness=if(dimMode)dim else -1f};onDispose{if(old!=null)window?.attributes=window?.attributes?.apply{screenBrightness=old}}}
 fun pin(kind:String,target:String){if(widgets.none{it.kind==kind&&it.target==target})save(widgets+DeskWidget(UUID.randomUUID().toString(),kind,target));message="Pinned to Home"}
 BackHandler(detail.isNotEmpty()||editing){if(detail.isNotEmpty())backDetail() else editing=false}
 CompositionLocalProvider(LocalReducedMotion provides reducedMotion,LocalShowTests provides showTests,LocalGridGap provides gridGap,LocalGridColumns provides gridColumns){
 val deskIdleEnabled = wide && dimMode && !editing && !settings && detail.isEmpty() && tab == "Home"
 var ambient by remember { mutableStateOf(false) }
 var lastInteraction by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
 val dockAutoHide = wide && !editing && !settings && detail.isEmpty() && !ambient
 var dockVisible by remember { mutableStateOf(true) }
 var touchInProgress by remember { mutableStateOf(false) }
 LaunchedEffect(dockAutoHide, lastInteraction, touchInProgress, dockSeconds) {
  dockVisible = true
  if (dockAutoHide && !touchInProgress) {
   delay(dockSeconds.coerceAtLeast(1) * 1_000L)
   dockVisible = false
  }
 }
 val verdictState = currentVerdict()
 val incidentKeys = verdictState.incidents.map { "${it.id.ifBlank { it.title }}:${it.kind.lowercase()}" }.toSet()
 var previousIncidentKeys by remember { mutableStateOf(incidentKeys) }
 val idleTimeout = prefs.getInt("ambientDelayMinutes", 5).coerceIn(1, 15) * 60_000L
 LaunchedEffect(deskIdleEnabled, lastInteraction, idleTimeout) {
  ambient = false
  if (deskIdleEnabled) {
   delay(idleTimeout)
   ambient = true
  }
 }
 LaunchedEffect(incidentKeys) {
  if (ambient && (incidentKeys - previousIncidentKeys).isNotEmpty()) {
   lastInteraction = android.os.SystemClock.elapsedRealtime()
  }
  previousIncidentKeys = incidentKeys
 }
 if (deskIdleEnabled && ambient) {
  AmbientDesk(verdictState) { lastInteraction = android.os.SystemClock.elapsedRealtime() }
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
 val destinations = listOf("Home" to "NavHome", "Cluster" to "NavCluster", "Services" to "NavServices", "Inbox" to "NavInbox")
 val navigate: (String) -> Unit = {
  tab=it
  detail=""
  detailHistory=arrayListOf()
  editing=false
 }
 Column(Modifier.weight(1f).fillMaxHeight().offset(x=if(dimMode)shift.dp else 0.dp)){
 Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp)) {
  if (wide && tab == "Inbox" && (detail.isEmpty() || detail.startsWith("event:"))) {
   Spacer(Modifier.height(12.dp))
  } else if (!editing && detail.isEmpty()) {
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
   Text(if(editing)"Edit Home" else if(detail.isEmpty())if(tab=="Home")"Olympus"else tab else if(detail.startsWith("event:")&&selectedEvent?.id==detail.substringAfter(":"))incidentTitle(selectedEvent!!)else routeTitle(detail),fontSize=18.sp,fontWeight=FontWeight.SemiBold,color=Paper,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
   if(detail.isEmpty()&&!editing)LiveStatus()

   if(detail.startsWith("node:")||detail.startsWith("app:"))TextButton(onClick={pin(if(detail.startsWith("node:"))"Node"else"App",detail.substringAfter(":"))}){Text("Pin",color=Cobalt)}
   if(editing){if(previousLayout!=null)TextButton(onClick={val old=previousLayout;val entry=undoHistory.lastOrNull();if(entry!=null&&!entry.startsWith("[")){gridColumns=JSONObject(entry).optInt("columns",gridColumns);if(wide)prefs.edit().putInt("landscape-columns-v7",gridColumns).apply()};undoHistory=ArrayList(undoHistory.dropLast(1));widgets=old;prefs.edit().putString(layoutKey,DeskLayout.encode(old)).apply()}){Text("Undo",color=Cobalt)};TextButton(onClick={picker=true}){Text("+ Widget",color=Cobalt)};TextButton(onClick={editing=false}){Text("Done",color=Mint)}}
   else Row{if(detail.isEmpty()&&tab=="Home"&&!compactHeader)IconButton(onClick={editing=true},modifier=Modifier.size(44.dp)){LabGlyph("Edit",Muted,20.dp)};if(detail.isEmpty())IconButton(onClick={settings=true},modifier=Modifier.size(44.dp)){LabGlyph("Settings",Muted,21.dp)};Box{
    IconButton(onClick={menu=true},modifier=Modifier.size(44.dp).semantics { contentDescription = "More options" }){Text("•••",color=Muted)}
    DeskPopup(menu,{menu=false},if(detail.isNotBlank())listOf("Copy reference" to {(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Olympus resource",detail));message="Reference copied"})else listOf(
     "Edit Home" to {tab="Home";detail="";editing=true},
     "Add widget" to {tab="Home";detail="";picker=true},
     "Export layout" to {(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Olympus layout",DeskLayout.encode(widgets)));message="Layout copied"},
     "Import layout" to {importLayout=true},
     "Restore previous layout" to {val previousDefault = prefs.getString(priorDefaultKey, null); val old=previousDefault ?: prefs.getString(if(wide)"landscape-v6"else"portrait-v6",null);if(old!=null)try{save(DeskLayout.decode(old));gridColumns=if(!wide)4 else if(previousDefault!=null)8 else 6;if(wide)prefs.edit().putInt("landscape-columns-v7",gridColumns).apply();message="Previous layout restored"}catch(_:Exception){message="Previous layout unavailable"}else message="No previous layout"},"Settings" to {settings=true}))
   }}
  }

  if(message.isNotEmpty()){Text(message,color=Mint,fontSize=12.sp);LaunchedEffect(message){delay(2200);message=""}}
  BoxWithConstraints(Modifier.weight(1f)){val viewport=maxHeight
  AnimatedContent(targetState=if(wide&&tab=="Inbox"&&detail.startsWith("event:"))"Inbox" else detail.ifEmpty{tab},modifier=Modifier.fillMaxSize(),transitionSpec={if(reducedMotion)EnterTransition.None togetherWith ExitTransition.None else fadeIn(tween(180)) togetherWith fadeOut(tween(120))},label="page"){page->
   if(wide&&tab=="Inbox"&&(page=="Inbox"||page.startsWith("event:"))){
    Row(Modifier.fillMaxSize().padding(bottom = 12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
     Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Card).verticalScroll(rememberScrollState()).padding(14.dp)){
      NotificationInbox(deskPane = true){selectedEvent=it;detail="event:${it.id}"}
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
     page=="Attention"->{Title("Needs attention");if(LiveRepository.issues.isEmpty())Text("No current issues",color=Mint);LiveRepository.issues.forEach{item->Column(Modifier.fillMaxWidth().padding(bottom=10.dp).clip(RoundedCornerShape(16.dp)).background(Card).clickable{detail=item.target}.padding(16.dp)){Text(item.title,color=Paper,fontSize=14.sp);Text(item.severity,color=Apricot,fontSize=11.sp)}}}
     page=="Backups"-> {Title("Backups");Info("Destination",when(LiveRepository.backup){true->"Available";false->"Unavailable";null->"Unknown"});Info("Volumes with backup records",if(LiveRepository.backupVolumes==null)"Unavailable"else"${LiveRepository.backupRecorded} / ${LiveRepository.backupVolumes}");Info("Last recorded backup",LiveRepository.backupLast.ifEmpty{"None recorded"})}
     page=="Cluster"->ClusterOverview(
      openHistory = { metric, range -> detail = "cluster-history:$metric@$range" },
      openNode = { detail = "node:$it" }
     )
     page=="Services"->{ServicesTabs(if (servicesSection == "Monitors") "Monitors" else "Integrations") { servicesSection = it };if(servicesSection=="Monitors")MonitorScreen{detail="monitor:$it"}else IntegrationDirectory(openArgo={detail="Apps"}){detail="integration:$it"}}
     page=="Inbox"->NotificationInbox{selectedEvent=it;detail="event:${it.id}"}
     page=="Nodes"->Nodes{detail="node:$it"}
     page=="Events"->NotificationInbox{selectedEvent=it;detail="event:${it.id}"}
     page=="Apps"->Apps{detail="app:$it"}
     page=="Monitors"->MonitorScreen{detail="monitor:$it"}
     else->{
      val openIncident: (LabEvent) -> Unit = { incident ->
       tab="Inbox"
       selectedEvent=incident
       detail="event:${incident.id}"
       detailHistory=arrayListOf()
      }
      val selected=widgets.find{it.id==selectedWidgetId}?:widgets.firstOrNull()
      @Composable fun canvas() {
       val cached = LiveRepository.time.isNotBlank() && !LiveRepository.state.startsWith("Live")
       Column(Modifier.alpha(if (cached && !editing) .65f else 1f)) {
        WidgetGrid(widgets,wide,editing,selected?.id.orEmpty(),reducedMotion,(viewport-12.dp).coerceAtLeast(0.dp),wide&&!editing,{editing=true},::save,{destination->when(destination){"Nodes"->{tab="Cluster";detail=destination};"Apps"->{tab="Services";servicesSection="Integrations";detail=destination};"Monitors"->{tab="Services";servicesSection="Monitors"};"Events","Attention"->tab="Inbox";else->detail=destination}},{selectedWidgetId=it.id})
       }
      }
      if (!editing && wide && widgets == DeskLayout.defaults(true)) {
       val height = (viewport - 12.dp).coerceAtLeast(220.dp)
       Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HomeVerdict(openIncident, Modifier.weight(1.5f).fillMaxHeight(), tall = true)
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
         DeskHomeTile(widgets.first { it.kind == "CPU" }, Modifier.weight(1f)) { detail = "cluster-history:cpu" }
         DeskHomeTile(widgets.first { it.kind == "Monitors" }, Modifier.weight(1f)) {
          tab = "Services"; servicesSection = "Monitors"
         }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
         DeskHomeTile(widgets.first { it.kind == "Memory" }, Modifier.weight(1f)) { detail = "cluster-history:memory" }
         DeskHomeTile(widgets.first { it.kind == "GitOps" }, Modifier.weight(1f)) { tab = "Services"; servicesSection = "Integrations"; detail = "Apps" }
        }
        DeskHomeTile(widgets.first { it.kind == "Nodes" }, Modifier.weight(1.1f).fillMaxHeight()) {
         tab = "Cluster"; detail = "Nodes"
        }
       }
      } else if(!editing&&wide) Row(
       Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)
      ){
       HomeVerdict(openIncident, Modifier.weight(1.5f).heightIn(min = (viewport - 12.dp).coerceAtLeast(180.dp)), tall = true)
       Box(Modifier.weight(3.1f)){canvas()}
      }
      else if(editing&&wide)Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){
       Box(Modifier.weight(1f)){canvas()}
       Column(Modifier.width(244.dp)){selected?.let{EditorInspector(it,widgets,wide,::save,{configuring=it},{save(widgets.filterNot{w->w.id==it.id})},{picker=true})}}
            } else if (editing) {
       Column(Modifier.heightIn(min = (viewport - 8.dp).coerceAtLeast(0.dp))) {
        Text("Live preview · drag the handles to resize", color = Muted, fontSize = 12.sp)
        canvas()
        Spacer(Modifier.weight(1f))
        selected?.let {
         EditorInspector(it, widgets, wide, ::save, { configuring = it }, {
          save(widgets.filterNot { widget -> widget.id == it.id })
         }, { picker = true })
        }
       }
      } else {
       HomeOfflineNotice()
       HomeVerdict(openIncident)
       canvas()
      }
     }
    }
   }
  }
 }


 }
 if (wide && !editing && (!dockAutoHide || dockVisible)) {
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
 } else if (wide && !editing) {
  Spacer(Modifier.height(56.dp))
 } else if (!wide && !editing) NavigationBar(containerColor = Card) {
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
 if(picker)WidgetPicker(onDismiss={picker=false}){save(widgets+it);picker=false}
 if(configuring!=null)WidgetPicker(initial=configuring,onDismiss={configuring=null}){updated->save(widgets.map{if(it.id==updated.id)updated else it});configuring=null}
 if(importLayout){var text by remember{mutableStateOf("")};var error by remember{mutableStateOf(false)};AlertDialog(onDismissRequest={importLayout=false},title={Text("Import layout")},text={Column{OutlinedTextField(value=text,onValueChange={text=it},label={Text("Layout JSON")},isError=error);if(error)Text("Invalid layout",color=Apricot)}},confirmButton={TextButton(onClick={try{save(DeskLayout.decode(text));importLayout=false}catch(_:Exception){error=true}}){Text("Import")}},dismissButton={TextButton(onClick={importLayout=false}){Text("Cancel")}})}
 if(settings)SettingsScreen(prefs,dimMode,dim,reducedMotion,keepAwake,showTests,dockSeconds,gridGap,
  {dimMode=it},{dim=it},{reducedMotion=it},{keepAwake=it},{showTests=it},{dockSeconds=it},{gridGap=it},
  {settings=false;detail="Monitors"},{action->settings=false;when(action){
   "Edit Home"->{tab="Home";detail="";editing=true}
   "Add widget"->{tab="Home";detail="";picker=true}
   "Export layout"->{(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Olympus layout",DeskLayout.encode(widgets)));message="Layout copied"}
   "Import layout"->importLayout=true
   "Restore previous layout" -> {
    val priorDefault = prefs.getString(priorDefaultKey, null)
    val previous = priorDefault ?: prefs.getString(if (wide) "landscape-v6" else "portrait-v6", null)
    val restored = previous?.let { runCatching { DeskLayout.decode(it) }.getOrNull() }
    if (restored != null) {
     save(restored)
     if (wide) {
      gridColumns = if (priorDefault == null) 6 else 8
      prefs.edit().putInt("landscape-columns-v7", gridColumns).apply()
     }
     tab = "Home"
     detail = ""
     message = "Previous layout restored"
    } else message = "No previous layout available"
   }
   "Reset this layout"->{save(DeskLayout.defaults(wide));gridColumns=if(wide)8 else 4;if(wide)prefs.edit().putInt("landscape-columns-v7",8).apply();tab="Home";detail=""}
  }},{settings=false},focusMode,{focusMode=it})

}

@Composable private fun HomeVerdict(open: (LabEvent) -> Unit, modifier: Modifier = Modifier.fillMaxWidth(), tall: Boolean = false) {
 val state = LiveRepository.state
 val connected = state.startsWith("Live")
 val result = currentVerdict()
 val verdict = result.verdict
 val firing = result.incidents
 val incident = firing.firstOrNull()
 val verified = state == "Live" && LiveRepository.signals.isNotEmpty() &&
  LiveRepository.signals.values.all { it } && LiveRepository.active.none { it.state == "Unknown" }
 val watching = LiveRepository.active.count { it.state == "Pending" }
 val summary = when {
  incident != null -> incident.detail.ifBlank { if (firing.size == 1) "1 firing incident" else "${firing.size} firing incidents" }
  verdict == Verdict.Calm && watching > 0 -> "$watching pending · watching"
  verdict == Verdict.Calm -> "No firing incidents"
  connected -> "Waiting for fresh source data"
  else -> state
 }
 val accent = verdict.accent
 val heroColor by androidx.compose.animation.animateColorAsState(
  targetValue = if (incident != null) DeskColors.attentionSurface else Card,
  animationSpec = if (LocalReducedMotion.current) androidx.compose.animation.core.snap() else tween(200),
  label = "Verdict card"
 )
 Column(
  modifier.then(if (tall) Modifier else Modifier.padding(bottom = 8.dp)).clip(RoundedCornerShape(24.dp))
   .background(heroColor).padding(16.dp),
  verticalArrangement = if (tall) Arrangement.SpaceBetween else Arrangement.spacedBy(6.dp)
 ) {
  Column {
   Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    VerdictMark(verdict)
    Text(if (incident == null) "HOMELAB VERDICT" else verdict.label, color = if (incident == null) accent else DeskColors.onAttention, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp)
   }
   Text(incident?.let(::incidentTitle) ?: verdict.label, color = if (incident == null) Paper else DeskColors.onAttention,
    fontSize = 34.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
   Text(summary, color=if (incident == null) Muted else DeskColors.onAttention, fontSize=14.sp, maxLines=2, overflow=TextOverflow.Ellipsis)
   if (incident != null && !verified) Text("Partial data", color=Muted, fontSize=12.sp)
   if (!connected && LiveRepository.time.isNotBlank()) {
    Text("Cached snapshot · ${localTime(LiveRepository.time)}", color=Muted, fontSize=12.sp)
   }
   if (connected && !verified) {
    val unavailable = LiveRepository.signals.filterValues { !it }.keys
    if (unavailable.isNotEmpty()) Text(unavailable.joinToString(", ", postfix=" unavailable"), color=Muted, fontSize=12.sp)
   }
  }
  if (incident != null) {
   Button(
    onClick = { open(incident) },
    modifier = Modifier.heightIn(min = 44.dp),
    shape = RoundedCornerShape(22.dp),
    colors = ButtonDefaults.buttonColors(containerColor = accent,
     contentColor = if (DeskColors == DarkDesk) Color(0xFF2B1A00) else Color.White)
   ) { Text("View incident") }
  } else if (tall && !connected && state != "Not paired") {
   TextButton(onClick=LiveRepository::retryNow, modifier=Modifier.heightIn(min=44.dp)) {
    Text("Retry now", color=Cobalt)
   }
  }
 }
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

private fun sourceForWidget(kind: String): String? = when (kind) {
 "CPU", "Memory" -> kind
 "Nodes", "Node" -> "Nodes"
 "Monitors" -> "Uptime Kuma"
 "GitOps", "App" -> "Argo CD"
 else -> null
}

private fun sourceAge(kind: String): Long? =
 sourceForWidget(kind)?.let { source -> LiveRepository.observedAt[source] }
  ?.let { observationAgeSeconds(it, Instant.now()) }

private fun widgetUnavailable(kind: String): Boolean = when (kind) {
 "CPU" -> LiveRepository.cpuPercent == null
 "Memory" -> LiveRepository.memoryPercent == null
 "Nodes", "Node" -> LiveRepository.signals["Nodes"] == false
 "Monitors" -> LiveRepository.signals["Uptime Kuma"] == false
 "GitOps", "App" -> LiveRepository.signals["Argo CD"] == false
 else -> false
}

private fun Modifier.unavailableOutline(color: Color) = drawWithContent {
 drawContent()
 drawRoundRect(
  color = color,
  cornerRadius = CornerRadius(20.dp.toPx()),
  style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
 )
}

@Composable private fun WidgetGrid(items:List<DeskWidget>,wide:Boolean,editing:Boolean,selectedId:String,reducedMotion:Boolean,viewport:androidx.compose.ui.unit.Dp,fillViewport:Boolean,edit:()->Unit,save:(List<DeskWidget>)->Unit,open:(String)->Unit,configure:(DeskWidget)->Unit){
 val columns=LocalGridColumns.current
 val latest by rememberUpdatedState(items)
 var pitchX by remember{mutableFloatStateOf(100f)}
 var pitchY by remember{mutableFloatStateOf(100f)}
 val cells=DeskLayout.place(items,columns)
 val gapDp=LocalGridGap.current.dp
 val rows=cells.maxOfOrNull{it.row+it.height}?:0
 val statusStrip=wide&&columns==8&&rows==3&&cells.any{it.row==2}&&cells.filter{it.row<2}.all{it.row+it.height<=2}&&cells.filter{it.row==2}.all{it.widget.kind in listOf("Nodes","Monitors","Events")&&it.height==1}
 val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
 val accessibilityScale = 1f + ((fontScale - 1f).coerceAtLeast(0f) * .5f)
 val stripHeight=78.dp * accessibilityScale
 val portraitDefault = !wide && items == DeskLayout.defaults(false)
 val unit = if (statusStrip) ((viewport - gapDp * 2 - stripHeight) / 2).coerceAtLeast(76.dp * accessibilityScale)
  else deskRowHeight(viewport.value, gapDp.value, rows, wide, fillViewport).dp * accessibilityScale
 if(items.isEmpty())DeskAction("Add widget",true){edit()}
 Layout(content={cells.forEach{cell->val widget=cell.widget
  val unavailable = !editing && (widgetUnavailable(widget.kind) || classifyFreshness(sourceAge(widget.kind)) == Freshness.Stale || LiveRepository.state == "Stale")
  val staleBorder = Muted
  var delta by remember(widget.id){mutableStateOf(Offset.Zero)}
  Box(Modifier.zIndex(if(delta!=Offset.Zero)1f else 0f).graphicsLayer{translationX=delta.x;translationY=delta.y}.pointerInput(widget.id,columns,cell.column,cell.row){detectDragGesturesAfterLongPress(onDragStart={edit();configure(widget);delta=Offset.Zero},onDrag={change,amount->change.consume();delta+=amount},onDragEnd={save(DeskLayout.move(latest,widget.id,cell.column+kotlin.math.round(delta.x/pitchX).toInt(),cell.row+kotlin.math.round(delta.y/pitchY).toInt(),columns));delta=Offset.Zero},onDragCancel={delta=Offset.Zero})}.then(if(editing&&widget.id==selectedId)Modifier.border(2.dp,Cobalt.copy(alpha=.85f),RoundedCornerShape(20.dp))else Modifier).clip(RoundedCornerShape(24.dp)).background(widgetColor(widget.kind)).then(if (unavailable) Modifier.unavailableOutline(staleBorder).alpha(.75f) else Modifier).clickable(enabled=editing||widget.kind!="Clock"){if(editing)configure(widget)else open(if(widget.kind=="Attention")LiveRepository.issues.firstOrNull()?.target?:"Events"else destination(widget))}.padding(12.dp)){
   WidgetBody(widget,Modifier.fillMaxSize())
   if(editing&&widget.id==selectedId){
    @Composable fun handle(horizontal:Boolean){
     Box(Modifier.align(if(horizontal)Alignment.CenterEnd else Alignment.BottomCenter).size(48.dp).semantics { contentDescription = if (horizontal) "Resize width" else "Resize height" }.pointerInput(widget.id,cell.width,cell.height,horizontal){var amount=0f;detectDragGestures(onDragStart={amount=0f},onDrag={change,d->change.consume();amount+=if(horizontal)d.x else d.y},onDragEnd={val steps=kotlin.math.round(amount/if(horizontal)pitchX else pitchY).toInt();save(DeskLayout.resize(latest,widget.id,cell.width+if(horizontal)steps else 0,cell.height+if(horizontal)0 else steps,columns))})},contentAlignment=if(horizontal) Alignment.CenterEnd else Alignment.BottomCenter){Box(Modifier.size(16.dp).background(Cobalt,RoundedCornerShape(50)))}
    }
    handle(true);handle(false)
   }
  }
 }},modifier=Modifier.fillMaxWidth()){measurables,constraints->
  val gap = gapDp.roundToPx()
  val cellWidth = (constraints.maxWidth - gap * (columns - 1)) / columns
  val portraitRows = listOf(150.dp, 114.dp, 114.dp, 106.dp)
  val rowSizes = List(rows) { index ->
   when {
    portraitDefault -> (portraitRows[index] * accessibilityScale).roundToPx()
    statusStrip && index == 2 -> stripHeight.roundToPx()
    else -> unit.roundToPx()
   }
  }
  val offsets = rowSizes.runningFold(0) { offset, height -> offset + height + gap }
  pitchX = (cellWidth + gap).toFloat()
  pitchY = if (rows == 0) unit.toPx() + gap else offsets.last().toFloat() / rows
  val placeables = measurables.mapIndexed { index, measurable ->
   val cell = cells[index]
   val width = cellWidth * cell.width + gap * (cell.width - 1)
   val height = offsets[cell.row + cell.height] - offsets[cell.row] - gap
   measurable.measure(Constraints.fixed(width, height))
  }
  layout(constraints.maxWidth, (offsets.last() - gap).coerceAtLeast(0)) {
   placeables.forEachIndexed { index, placeable ->
    val cell = cells[index]
    placeable.placeRelative(cell.column * (cellWidth + gap), offsets[cell.row])
   }
  }
 }
}

internal fun destination(w:DeskWidget)=when(w.kind){"CPU","Memory"->if(w.target.isBlank())"cluster-history:${w.kind.lowercase()}@${w.historyRange}"else"node:${w.target}";"Clock"->"Home";"Monitors"->"Monitors";"Node"->"node:${w.target}";"App","Service"->"app:${w.target}";"Backups"->"Backups";"Events"->"events-history:${w.target.ifBlank{"All"}}";"Alerts"->if(w.target.isBlank()||w.target=="All")"Events"else"events-source:${w.target}";"Attention"->"Attention";"GitOps"->"Apps";"Downloads"->"app:qbittorrent";else->"Nodes"}
@Composable private fun WidgetBody(w:DeskWidget,modifier:Modifier){if(w.kind=="Clock"){ClockWidget(w.height,modifier,w.label);return};if(w.kind in listOf("CPU","Memory")){MetricWidget(w,modifier);return};if(w.kind in listOf("Attention","GitOps","Nodes","Monitors","Events")){SummaryWidget(w,modifier);return};val fg=if(w.kind in listOf("GitOps","Attention","Monitors"))Color(0xFF29233D)else Paper;val secondary=if(fg!=Paper)fg.copy(alpha=.8f)else Muted;Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(3.dp)){
 val app=LiveRepository.services.find{it.name==w.target};val node=LiveRepository.nodes.find{it.name==w.target}
 Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){if(w.kind=="App"&&w.width>=2)AppIcon(app?.icon?:"",w.target);Text(w.label.ifBlank{if(w.kind in listOf("App","Node","Service"))w.target else when(w.kind){"Events"->"Recent activity";"Alerts"->"Active alerts";"Attention"->"Needs attention";else->w.kind}},color=secondary,fontSize=if(w.width<2)10.sp else 12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f));if(w.kind=="GitOps")LabGlyph("GitOps",fg,19.dp)}
 val missing=when(w.kind){"CPU"->LiveRepository.cpuPercent==null;"Memory"->LiveRepository.memoryPercent==null;"Downloads"->LiveRepository.download==null;"Monitors"->LiveRepository.monitorsTotal==null;"Backups"->LiveRepository.backup==null;"App"->app==null;"Node"->node==null;else->LiveRepository.lastSuccess==0L}
 if(w.width>=2&&(missing||!LiveRepository.state.startsWith("Live")))Text(if(missing)"Unavailable"else"Last known data",color=if(fg==Paper)Apricot else fg,fontSize=9.sp)
 val visibleEvents=LiveRepository.events.filter{(w.target.isBlank()||w.target=="All"||it.source==w.target)&&(LocalShowTests.current||!(it.kind=="Test"||it.source=="Test"||it.title=="Test"))}
 val service=LiveRepository.integrations.find{it.name==w.target}
 if(w.kind=="Service"){
  if(service==null)Text("Not connected",color=secondary,fontSize=12.sp)
  else {val fields=if(w.fields.isEmpty())service.fields.keys.take(3)else w.fields;fields.forEach{field->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(field,color=secondary,fontSize=12.sp);Text(service.fields[field]?.toInt()?.toString()?:"—",color=fg,fontSize=15.sp,fontWeight=FontWeight.SemiBold)}};if(service.error.isNotBlank())Text("Some fields unavailable",color=Apricot,fontSize=10.sp)}
  return@Column
 }
 val activityCounts=if(LocalShowTests.current)LiveRepository.recentActivityWithTests else LiveRepository.recentActivity
 val recentCount=activityCounts?.get(w.target.ifBlank{"All"})?:if(activityCounts==null)null else 0
 val firing=LiveRepository.active.filter{it.state=="Firing"&&(w.target.isBlank()||w.target=="All"||it.source==w.target)}
 val value=if(missing)"—"else when(w.kind){"CPU"->"${LiveRepository.cpuPercent.fmt()}%";"Memory"->"${LiveRepository.memoryPercent.fmt()}%";"Nodes"->"${LiveRepository.nodes.count{it.ready}} / ${LiveRepository.nodes.size}";"Monitors"->"${LiveRepository.monitorsUp?:"—"} / ${LiveRepository.monitorsTotal?:"—"}";"GitOps"->"${LiveRepository.services.count{it.health=="Healthy"&&it.sync=="Synced"}} / ${LiveRepository.services.size}";"Downloads"->"${LiveRepository.download.fmt()} MiB/s";"Backups"->when(LiveRepository.backup){true->"Available";false->"Unavailable";null->"Unknown"};"Attention"->if(LiveRepository.lastSuccess==0L)"Unavailable" else if(LiveRepository.issues.isNotEmpty())LiveRepository.issues.first().title else if(!LiveRepository.state.startsWith("Live")||LiveRepository.signals.values.any{!it})"Unconfirmed"else"No action needed";"Events"->visibleEvents.firstOrNull()?.title?:"No recent activity";"Alerts"->"${firing.size} firing";"App"->app?.health?:"Unavailable";"Node"->if(node==null)"Unavailable" else if(node.ready)"Ready" else "Not ready";else->"—"}
 Text(value,color=fg,fontWeight=FontWeight.SemiBold,fontSize=if(w.width<2)18.sp else if(value.length>10)19.sp else 28.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
 if(w.width>=2){when(w.kind){
  "CPU"->{Text("${((LiveRepository.cpuPercent?:0f)/100f*LiveRepository.totalCores).fmt()} / ${LiveRepository.totalCores} cores",color=secondary,fontSize=11.sp);Text("Recent samples · 0–100%",color=secondary,fontSize=10.sp);Spark(LiveRepository.history["cpu"]?:emptyList(),Cobalt)}
  "Memory"->{Text("${LiveRepository.usedMemory.fmt()} / ${LiveRepository.totalMemory.fmt()} GiB",color=secondary,fontSize=11.sp);Text("Recent samples · 0–100%",color=secondary,fontSize=10.sp);Spark(LiveRepository.history["memory"]?:emptyList(),Lilac)}
  "App"->{ResourceMiniMap(app?.resources?:emptyList(),app?.pods?:0)}
  "Node"->{Text("CPU ${node?.cpuPercent.fmt()}%",color=secondary,fontSize=11.sp);Spark(LiveRepository.history["node:${w.target}"]?:emptyList(),Cobalt)}
  "Events"->{Text(visibleEvents.firstOrNull()?.let{localTime(it.resolvedAt.ifBlank{it.time})}?:"Last 24 hours",color=secondary,fontSize=11.sp)}
  "Alerts"->{Text("${firing.count{!isInformational(it.kind)}} actionable · ${firing.count{isInformational(it.kind)}} info",color=secondary,fontSize=11.sp);firing.firstOrNull()?.let{Text(it.title,color=fg,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}}
  "Attention"->{LiveRepository.active.firstOrNull{needsAttention(it)}?.let{event->Text(listOf(event.namespace,event.pod,event.node).filter{it.isNotBlank()}.joinToString(" · "),color=fg,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${event.state} · ${event.kind}",color=fg,fontSize=11.sp)};if(LiveRepository.issues.isEmpty())Text(if(LiveRepository.lastSuccess==0L)"Connecting…"else if(LiveRepository.active.any{isInformational(it.kind)&&it.state=="Firing"})"${LiveRepository.active.count{isInformational(it.kind)&&it.state=="Firing"}} informational"else"Healthy",color=fg,fontSize=12.sp)}
  "Backups"->Text("Longhorn destination",color=secondary,fontSize=11.sp)
  "GitOps"->Text("Healthy & synced",color=secondary,fontSize=11.sp)
  "Nodes"->{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){LiveRepository.nodes.forEach{node->Box(Modifier.weight(1f).height(4.dp).background(if(node.ready)Mint else Apricot,RoundedCornerShape(3.dp)))}};Text("Nodes ready",color=secondary,fontSize=11.sp)}
  "Monitors"->Text("Checks up",color=secondary,fontSize=11.sp)
 } }
}}
@Composable private fun WidgetPicker(initial:DeskWidget?=null,onDismiss:()->Unit,add:(DeskWidget)->Unit){
 var kind by remember{mutableStateOf(initial?.kind?:"CPU")};var target by remember{mutableStateOf(initial?.target?:"")}
 var width by remember{mutableIntStateOf(initial?.width?:2)};var height by remember{mutableIntStateOf(initial?.height?:1)}
 var historyRange by remember { mutableStateOf(initial?.historyRange ?: "1h") }
 var label by remember{mutableStateOf(initial?.label?:"")};var fields by remember{mutableStateOf(initial?.fields?:emptyList())}
 var category by remember{mutableStateOf("All")};var gallerySearch by remember{mutableStateOf("")};var configuring by remember{mutableStateOf(initial!=null)};var sourceOpen by remember{mutableStateOf(false)};var search by remember{mutableStateOf("")}
 val wide=wideWindow();val columns=LocalGridColumns.current
 val targets=when(kind){"Service"->LiveRepository.integrations.map{it.name};"App"->LiveRepository.services.map{it.name};"Node"->LiveRepository.nodes.map{it.name};"Events","Alerts"->listOf("All")+(LiveRepository.active+LiveRepository.events).map{it.source}.distinct();else->emptyList()}
 LaunchedEffect(kind,targets){if(targets.isNotEmpty()&&target !in targets)target=targets.first()}
 val preview=DeskWidget(initial?.id?:"preview",kind,target,width,height,label,fields,initial?.x?:-1,initial?.y?:-1,historyRange)
 @Composable fun previewPanel(){Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
  Text("PREVIEW",color=Muted,fontSize=10.sp)
  Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(DeskColors.raised.copy(alpha=.35f)).padding(14.dp),contentAlignment=Alignment.Center){
   Box(Modifier.width((width*82).dp).height((height*108).dp).clip(RoundedCornerShape(18.dp)).background(widgetColor(kind)).padding(12.dp)){WidgetBody(preview,Modifier.fillMaxSize())}
  }
  Text("$width columns × $height ${if(height==1)"row"else"rows"}",color=Muted,fontSize=11.sp)
 }}
 @Composable fun stepper(title:String,value:Int,min:Int,max:Int,change:(Int)->Unit){Column(Modifier.widthIn(min=125.dp).clip(RoundedCornerShape(14.dp)).background(Card).padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
  Text(title,color=Muted,fontSize=11.sp)
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){Box(Modifier.size(44.dp).clip(RoundedCornerShape(9.dp)).background(DeskColors.raised).clickable(enabled=value>min){change(value-1)},contentAlignment=Alignment.Center){Text("−",color=if(value>min)Paper else Muted.copy(alpha=.4f),fontSize=20.sp)};Text("$value",color=Paper,fontSize=17.sp);Box(Modifier.size(44.dp).clip(RoundedCornerShape(9.dp)).background(DeskColors.raised).clickable(enabled=value<max){change(value+1)},contentAlignment=Alignment.Center){Text("+",color=if(value<max)Paper else Muted.copy(alpha=.4f),fontSize=20.sp)}}
 }}
 @Composable fun controls(){Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("SIZE",color=Muted,fontSize=10.sp)
  Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){stepper("Width",width,if(wide)1 else 2,columns){width=it};stepper("Height",height,1,4){height=it}}
  if(targets.isNotEmpty()){
   Text(if(kind in listOf("Events","Alerts"))"SOURCE"else if(kind=="Node")"NODE"else"APPLICATION",color=Muted,fontSize=10.sp)
   Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).clickable{sourceOpen=!sourceOpen}.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text(target,color=Paper,fontSize=13.sp,modifier=Modifier.weight(1f));Text(if(sourceOpen)"⌃"else"⌄",color=Muted)}
   if(sourceOpen){DeskSearch(search,{search=it},"Search");Column(Modifier.heightIn(max=160.dp).verticalScroll(rememberScrollState())){targets.filter{it.contains(search,true)}.forEach{t->Row(Modifier.fillMaxWidth().clickable{target=t;fields=emptyList();sourceOpen=false;search=""}.padding(10.dp)){Text(t,color=if(target==t)Cobalt else Paper,fontSize=13.sp)}}}}
  }
  if (kind == "CPU" || kind == "Memory") {
   Text("CHART HISTORY RANGE", color = Muted, fontSize = 10.sp)
   DeskSegments(DeskLayout.historyRanges, historyRange, { historyRange = it })
  }
  if(kind=="Service"){
   val available=LiveRepository.integrations.find{it.name==target}?.fields?.keys?.toList()?:emptyList()
   if(available.isEmpty())Text("No service integration connected",color=Muted,fontSize=12.sp)
   else {Text("FIELDS",color=Muted,fontSize=10.sp);val selected=if(fields.isEmpty())available.take(3)else fields
    available.chunked(2).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){row.forEach{field->DeskAction((if(field in selected)"✓ "else"+ ")+field,field in selected){val next=if(field in selected)selected-field else selected+field;if(next.isNotEmpty())fields=next}}}}
   }
  }
  Text("TITLE",color=Muted,fontSize=10.sp);DeskSearch(label,{label=it.take(40)},"${kind} · default")
 }}
 androidx.compose.ui.window.Dialog(onDismissRequest=onDismiss,properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
  ImmersiveDialogWindow()
  Surface(color=Ink,contentColor=Paper,modifier=Modifier.fillMaxSize()){
   Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).padding(horizontal=16.dp)){
    Row(Modifier.fillMaxWidth().height(56.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
     IconButton(onClick={if(configuring&&initial==null)configuring=false else onDismiss()}){LabGlyph("Back",Paper,23.dp)}
     Text(if(configuring)"$kind widget"else"Add widget",color=Paper,fontSize=20.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
     if(configuring)DeskAction(if(initial==null)"Add to Home"else"Save",true,enabled=(kind !in listOf("App","Node","Service")||target in targets)){add(preview.copy(id=initial?.id?:UUID.randomUUID().toString()))}
    }
    if(!configuring){
     Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical=10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
      if(wide)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Box(Modifier.weight(1f)){DeskSearch(gallerySearch,{gallerySearch=it},"Find a widget")};Box(Modifier.width(360.dp)){DeskSegments(listOf("All","Cluster","Monitoring","Integrations"),category,{category=it})}}
      else{DeskSearch(gallerySearch,{gallerySearch=it},"Find a widget");DeskSegments(listOf("All","Cluster","Monitoring","Integrations"),category,{category=it})}
      val catalog=DeskLayout.kinds.filter{k->(gallerySearch.isBlank()||k.contains(gallerySearch,true)||(k=="Clock"&&"Welcome".contains(gallerySearch,true)))&&(category=="All"||when(category){"Cluster"->k in listOf("CPU","Memory","Nodes","Node","GitOps","App","Backups");"Monitoring"->k in listOf("Monitors","Attention","Alerts","Events");else->k in listOf("Service","Downloads","Clock")})}
      if(catalog.isEmpty())DeskEmpty("No matching widgets","Try another name or category.")
      catalog.chunked(if(wide)4 else 2).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
       row.forEach{k->Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Card).clickable{kind=k;target="";fields=emptyList();height=if(k=="Clock")2 else 1;configuring=true}.padding(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(14.dp)).background(widgetColor(k)).padding(10.dp)){WidgetBody(DeskWidget("preview-$k",k,width=2,height=if(k=="Clock")1 else 1),Modifier.fillMaxSize())}
        Text(when(k){"Events"->"Recent activity";"Alerts"->"Active alerts";"Clock"->"Welcome";else->k},color=Paper,fontSize=13.sp,fontWeight=FontWeight.Medium)
       }}
       repeat((if(wide)4 else 2)-row.size){Spacer(Modifier.weight(1f))}
      }}
     }
    }else if(wide)Row(Modifier.weight(1f).padding(vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(20.dp)){
     Column(Modifier.weight(.48f).verticalScroll(rememberScrollState())){previewPanel()}
     Column(Modifier.weight(.52f).verticalScroll(rememberScrollState()).padding(bottom=16.dp)){controls()}
    }else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical=10.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){previewPanel();controls()}
   }
  }
 }
}
internal fun welcomeColor():Color = DarkDesk.cobalt
private fun attentionActive() = currentVerdict().incidents.isNotEmpty()
@Composable private fun widgetColor(kind:String)=when(kind){"Clock"->welcomeColor();"Attention"->if(attentionActive())DeskColors.attentionSurface else Card;else->Card}
@Composable internal fun Spark(values:List<Float>,color:Color,percent:Boolean=false){if(values.size<2){Text("Collecting trend…",color=Muted,fontSize=10.sp);return};Canvas(Modifier.fillMaxWidth().height(24.dp)){val low=0f;val range=if(percent)100f else values.max().coerceAtLeast(.1f);val path=Path();values.forEachIndexed{i,value->val x=i.toFloat()/(values.size-1)*size.width;val y=size.height-(value.coerceIn(0f,range)-low)/range*(size.height-4)-2;if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};drawPath(path,color,style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))}}

@Composable private fun Title(value:String){Text(value,color=Paper,fontSize=25.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(vertical=12.dp))}
@Composable private fun Info(label:String,value:String){Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)){Text(label,color=Muted,fontSize=12.sp,modifier=Modifier.weight(1f));Text(value,color=Paper,fontSize=12.sp,modifier=Modifier.weight(1.6f))}}
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

@Composable private fun EditorInspector(widget:DeskWidget,items:List<DeskWidget>,wide:Boolean,save:(List<DeskWidget>)->Unit,configure:()->Unit,remove:()->Unit,add:()->Unit){
 val columns=LocalGridColumns.current
 Column(Modifier.fillMaxWidth().padding(vertical=8.dp).clip(RoundedCornerShape(20.dp)).background(Card).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
   Text("${widget.label.ifBlank { widget.kind }} widget", color = Paper, fontSize = 18.sp, fontWeight = FontWeight.Bold)
   Text(if (wide) "Landscape layout" else "Portrait layout", color = Muted, fontSize = 12.sp)
  }
  if (!wide) {
   Text("Size", color = Muted, fontSize = 12.sp)
   val sizes = mapOf("Compact" to (2 to 1), "Wide" to (4 to 1), "Tall" to (2 to 2), "Large" to (4 to 2))
   val selected = sizes.entries.firstOrNull { it.value == (widget.width to widget.height) }?.key.orEmpty()
   DeskSegments(sizes.keys.toList(), selected, { name ->
    val (width, height) = sizes.getValue(name)
    save(DeskLayout.resize(items, widget.id, width, height, columns))
   })
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
   listOf("Width" to widget.width,"Height" to widget.height).forEach{(name,value)->Column(Modifier.weight(1f)){
    Text(name,color=Muted,fontSize=11.sp)
    Row(verticalAlignment=Alignment.CenterVertically){
     val min=if(name=="Width"&&!wide)2 else 1;val max=if(name=="Width")columns else 4
     fun change(v:Int){save(DeskLayout.resize(items,widget.id,if(name=="Width")v else widget.width,if(name=="Height")v else widget.height,columns))}
     IconButton(onClick={change(value-1)},enabled=value>min,modifier=Modifier.size(40.dp).semantics{contentDescription="Decrease $name"}){Text("−",color=Cobalt,fontSize=22.sp)}
     Text("$value",color=Paper,fontSize=15.sp)
     IconButton(onClick={change(value+1)},enabled=value<max,modifier=Modifier.size(40.dp).semantics{contentDescription="Increase $name"}){Text("+",color=Cobalt,fontSize=22.sp)}
    }
   }}
  }
  if (widget.kind == "CPU" || widget.kind == "Memory") {
   Text("Shows", color = Muted, fontSize = 12.sp)
   val scopes = listOf("Cluster") + LiveRepository.nodes.map { it.name }
   DeskSegments(scopes, widget.target.ifBlank { "Cluster" }, { scope ->
    save(items.map { if (it.id == widget.id) it.copy(target = scope.takeUnless { name -> name == "Cluster" }.orEmpty()) else it })
   })
   if (widget.target.isBlank()) {
    Text("Time range", color = Muted, fontSize = 12.sp)
    DeskSegments(DeskLayout.historyRanges, widget.historyRange, { range ->
     save(items.map { if (it.id == widget.id) it.copy(historyRange = range) else it })
    })
   }
  }
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
   DeskAction("Remove", click = remove)
   DeskAction("Add widget", click = add)
   TextButton(onClick = configure) { Text("More", color = Cobalt) }
  }
 }
}

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

@Composable private fun MetricWidget(widget: DeskWidget, modifier: Modifier) {
 val cpu = widget.kind == "CPU"
 val node = LiveRepository.nodes.find { it.name == widget.target && it.available }
 val reading = when {
  widget.target.isBlank() -> if (cpu) LiveRepository.cpuPercent else LiveRepository.memoryPercent
  cpu -> node?.cpuPercent
  else -> node?.takeIf { it.memoryTotalGiB > 0 }?.let { it.memoryUsedGiB / it.memoryTotalGiB * 100 }
 }
 val denominator = when {
  reading == null -> "Unavailable"
  node != null && cpu -> "${node.name} · ${node.cores} cores"
  node != null -> "${node.name} · ${node.memoryUsedGiB.fmt()} of ${node.memoryTotalGiB.fmt()} GiB"
  cpu -> "of ${LiveRepository.totalCores} cores · 5 min"
  else -> "${LiveRepository.usedMemory.fmt()} of ${LiveRepository.totalMemory.fmt()} GiB"
 }
 val accent = Cobalt
 val track = DeskColors.track
 val reducedMotion = LocalReducedMotion.current
 val progress = androidx.compose.animation.core.animateFloatAsState(
  targetValue = reading?.coerceIn(0f, 100f) ?: 0f,
  animationSpec = if (reducedMotion) androidx.compose.animation.core.snap() else tween(350),
  label = "Metric ring"
 )
 val cached = !LiveRepository.state.startsWith("Live")
 val ageSeconds = sourceAge(widget.kind)
 val stale = classifyFreshness(ageSeconds) == Freshness.Stale
 val age = when {
  cached -> ageSeconds?.let { "Cached · ${formatObservationAge(it)}" } ?: "Cached"
  stale -> ageSeconds?.let { "${formatObservationAge(it)} old" }
  else -> null
 }
 val landscape = wideWindow()
 val historyKey = if (widget.target.isBlank()) { if (cpu) "cpu" else "memory" } else if (cpu) "node:${widget.target}" else "node-memory:${widget.target}"
 val samples = LiveRepository.history[historyKey].orEmpty()
 BoxWithConstraints(modifier.fillMaxSize()) {
  val wideTile = maxWidth >= 110.dp
  val tallTile = maxHeight >= 117.dp
  val showTrend = widget.height > 1 && maxHeight >= 155.dp && samples.size > 1
  val diameter = if (landscape) 58.dp else if (tallTile) 84.dp else 58.dp
  @Composable fun gauge() {
   Box(
    Modifier.size(diameter).semantics {
     contentDescription = if (reading == null) "${widget.kind} unavailable"
      else "${widget.kind} ${reading.fmt()} percent · $denominator"
    },
    contentAlignment = Alignment.Center
   ) {
    Canvas(Modifier.fillMaxSize().padding((diameter * .10f))) {
     val stroke = Stroke((diameter * .105f).toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
     drawCircle(track, style = stroke)
     if (reading != null && progress.value > 0f) drawArc(accent, -90f, progress.value * 3.6f, false, style = stroke)
    }
    Text(reading?.let { "${kotlin.math.round(it).toInt()}%" } ?: "—", color = Paper,
     fontSize = if (diameter >= 84.dp) 19.sp else 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
   }
  }
  @Composable fun caption(centered: Boolean) {
   val align = if (centered) Alignment.CenterHorizontally else Alignment.Start
   val textAlign = if (centered) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start
   Column(horizontalAlignment = align, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(widget.label.ifBlank { widget.kind }, color = Paper, fontSize = 14.sp, lineHeight = 16.sp,
     fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(denominator, color = Muted, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 2, textAlign = textAlign)
    if (age != null) Text(age, color = if (stale && !cached) Apricot else Muted, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 2)
   }
  }
  @Composable fun trend() {
   if (showTrend) {
    Text("Recent samples", color = Muted, fontSize = 10.sp)
    Spark(samples, accent, percent = true)
   }
  }
  if (landscape && wideTile) {
   Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically,
     horizontalArrangement = Arrangement.spacedBy(8.dp)) {
     gauge()
     Box(Modifier.weight(1f)) { caption(false) }
    }
    trend()
   }
  } else {
   Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
    gauge()
    caption(true)
    trend()
   }
  }
 }
}

@Composable private fun SummaryWidget(w:DeskWidget,modifier:Modifier){
 if (w.kind == "Nodes" && !wideWindow()) {
  Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
   Text(w.label.ifBlank { "Nodes · CPU · memory" }, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
   if (LiveRepository.nodes.isEmpty()) Text("Unavailable", color = Muted, fontSize = 12.sp)
   else HomeNodeBars()
  }
  return
 }
 if (w.kind == "GitOps") {
  HomeGitOpsSummary(w.label, modifier)
  return
 }
 if (w.kind == "Monitors") {
  HomeMonitorSummary(w.label, modifier)
  return
 }
 val fg=Paper;val sub=Muted
 val verdict = currentVerdict()
 val event = verdict.incidents.firstOrNull()
 val latest=LiveRepository.events.firstOrNull{(w.target.isBlank()||w.target=="All"||it.source==w.target)&&(LocalShowTests.current||!(it.kind=="Test"||it.source=="Test"||it.title=="Test"))}
 val known=LiveRepository.lastSuccess>0L
 val value=if(!known)"—"else when(w.kind){"Attention"->event?.let(::incidentTitle) ?: verdict.verdict.label;"GitOps"->"${LiveRepository.services.count{it.sync=="Synced"}} / ${LiveRepository.services.size}";"Nodes"->"${LiveRepository.nodes.count{it.ready}} / ${LiveRepository.nodes.size}";"Monitors"->"${LiveRepository.monitorsUp?:"—"} / ${LiveRepository.monitorsTotal?:"—"}";else->latest?.let(::incidentTitle)?:"No recent activity"}
 val supporting=if(!known)"Connecting…"else when(w.kind){"Attention"->event?.let{"${it.state} · ${it.kind} · ${it.node.ifBlank{it.source}}"}?:"${LiveRepository.active.count{it.state=="Firing"&&!isInformational(it.kind)}} firing · ${LiveRepository.active.count{it.state=="Pending"&&!isInformational(it.kind)}} pending";"GitOps"->"Apps synced";"Nodes"->"Nodes ready";"Monitors"->"Checks up";else->latest?.let{listOf(it.state,it.node.ifBlank{it.namespace}).filter{v->v.isNotBlank()}.joinToString(" · ")+" · "+localTime(it.resolvedAt.ifBlank{it.time})}?:"No records"}
 BoxWithConstraints(modifier.fillMaxSize()){
  val compact=maxHeight<80.dp;val roomy=maxHeight>110.dp
  Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(if(compact)1.dp else 6.dp)){
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(w.label.ifBlank{when(w.kind){"Attention"->if(attentionActive())"Needs attention"else"Cluster status";"Events"->"Recent activity";"Nodes"->"Nodes · CPU · memory";else->w.kind}},color=sub,fontSize=11.sp,lineHeight=13.sp,modifier=Modifier.weight(1f));if(w.kind=="GitOps")LabGlyph("GitOps",fg,16.dp)}
   Text(value,color=fg,fontSize=if(w.width<2)17.sp else if(value.length>12)if(compact)14.sp else 17.sp else if(compact)23.sp else 32.sp,lineHeight=if(compact)25.sp else 36.sp,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold)
   Text(supporting,color=sub,fontSize=10.sp,lineHeight=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)

   if (roomy && w.kind == "Nodes") HomeNodeBars()
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
private fun HomeMonitorSummary(label: String, modifier: Modifier) {
 val up = LiveRepository.monitorsUp
 val total = LiveRepository.monitorsTotal
 BoxWithConstraints(modifier.fillMaxSize()) {
  val compact = maxHeight < 80.dp
  val showDots = maxHeight >= 66.dp
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
   Text(label.ifBlank { "Monitors" }, color = Muted, fontSize = 11.sp, lineHeight = 13.sp)
   if (up == null || total == null) Text("Unavailable", color = Paper, fontSize = 16.sp)
   else {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
     Text(up.toString(), color = Paper, fontSize = if (compact) 23.sp else 30.sp,
      lineHeight = if (compact) 25.sp else 32.sp, fontWeight = FontWeight.SemiBold)
     Text("of $total up", color = Muted, fontSize = 12.sp, maxLines = 1)
    }
    if (showDots) MonitorDotGrid(if (compact) 16.dp else 24.dp)
   }
  }
 }
}

@Composable
private fun MonitorDotGrid(height: androidx.compose.ui.unit.Dp) {
 val monitors = LiveRepository.monitors
 if (monitors.isEmpty()) return
 val palette = DeskColors
 val up = monitors.count { it.status == "Up" }
 val down = monitors.count { it.status == "Down" }
 val unknown = monitors.size - up - down
 Canvas(Modifier.fillMaxWidth().height(height).semantics {
  contentDescription = "$up up, $down down, $unknown other monitor states"
 }) {
  val columns = minOf(16, monitors.size)
  val rows = (monitors.size + columns - 1) / columns
  val stepX = size.width / columns
  val stepY = size.height / rows
  val radius = minOf(stepX, stepY) * .33f
  monitors.forEachIndexed { index, monitor ->
   val center = Offset((index % columns + .5f) * stepX, (index / columns + .5f) * stepY)
   when (monitor.status) {
    "Up" -> drawCircle(palette.blue, radius, center)
    "Down" -> drawPath(Path().apply {
     moveTo(center.x, center.y - radius)
     lineTo(center.x + radius, center.y + radius)
     lineTo(center.x - radius, center.y + radius)
     close()
    }, palette.apricot)
    else -> drawCircle(palette.muted, radius, center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
   }
  }
 }
}

@Composable
private fun HomeNodeBars(modifier: Modifier = Modifier, distributed: Boolean = false) {
 val palette = DeskColors
 Column(modifier, verticalArrangement = if (distributed) Arrangement.SpaceEvenly else Arrangement.spacedBy(6.dp)) {
  LiveRepository.nodes.forEach { node ->
   val cpu = node.cpuPercent.takeIf { node.available }
   val memory = if (node.available && node.memoryTotalGiB > 0)
    node.memoryUsedGiB / node.memoryTotalGiB * 100 else null
   Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
     Text(node.name, color = palette.text, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f))
     Text(if (cpu == null || memory == null) "Unavailable" else "${kotlin.math.round(cpu).toInt()}% · ${kotlin.math.round(memory).toInt()}%",
      color = palette.muted, fontSize = 10.sp, maxLines = 1)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
     listOf("CPU" to cpu, "Memory" to memory).forEach { (label, reading) ->
      LinearProgressIndicator(
       progress = { ((reading ?: 0f) / 100).coerceIn(0f, 1f) },
       modifier = Modifier.weight(1f).height(8.dp).semantics {
        contentDescription = if (reading == null) "$label unavailable" else "$label ${reading.fmt()} percent"
       },
       color = if (label == "Memory" && (reading ?: 0f) >= 75f) palette.apricot else palette.blue, trackColor = palette.raised, gapSize = 0.dp, drawStopIndicator = {}
      )
     }
    }
   }
  }
 }
}

@Composable
private fun DeskHomeTile(widget: DeskWidget, modifier: Modifier, open: () -> Unit) {
 val cached = !LiveRepository.state.startsWith("Live") && LiveRepository.time.isNotBlank()
 val unavailable = widgetUnavailable(widget.kind) || classifyFreshness(sourceAge(widget.kind)) == Freshness.Stale
 val outline = Muted
 Box(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Card)
  .then(if (unavailable) Modifier.unavailableOutline(outline) else Modifier)
  .alpha(if (cached || unavailable) .68f else 1f).clickable(onClick = open).padding(14.dp)) {
  when (widget.kind) {
   "Nodes" -> Column(Modifier.fillMaxSize()) {
    val nodes = LiveRepository.nodes
    Text("Nodes · CPU · memory", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    HomeNodeBars(Modifier.weight(1f).fillMaxWidth(), distributed = true)
    Text(if (nodes.isEmpty()) "Unavailable" else "${nodes.count { it.ready }} of ${nodes.size} ready",
     color = Muted, fontSize = 12.sp)
   }
   "GitOps" -> HomeGitOpsSummary(widget.label, Modifier.fillMaxSize())
   else -> WidgetBody(widget, Modifier.fillMaxSize())
  }
 }
}

@Composable
private fun HomeGitOpsSummary(label: String, modifier: Modifier) {
 Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    val apps = LiveRepository.services
    val synced = apps.count { it.sync == "Synced" }
    val lastSync = apps.map { it.lastSync }.filter { it.isNotBlank() }.maxOrNull()
    Text(label.ifBlank { "Argo CD apps" }, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
     Text(if (apps.isEmpty()) "—" else synced.toString(), color = Paper, fontSize = 28.sp,
      lineHeight = 30.sp, fontWeight = FontWeight.Bold)
     if (apps.isNotEmpty()) Text("of ${apps.size} synced", color = Muted, fontSize = 12.sp)
    }
    if (apps.isNotEmpty()) {
     val drifted = apps.count { it.sync == "OutOfSync" }
     Text(listOfNotNull("$drifted drifted", lastSync?.let { "last sync ${localTime(it).substringAfter(" · ").take(5)}" }).joinToString(" · "),
      color = Muted, fontSize = 12.sp)
    }
   }
}

@Composable
private fun ReferenceSettings(open: () -> Unit) {
 IconButton(onClick = open, modifier = Modifier.size(44.dp).semantics { contentDescription = "Settings" }
  .clip(RoundedCornerShape(22.dp)).background(Card)) {
  LabGlyph("Sliders", Paper, 22.dp, decorative = true)
 }
}

@Composable
private fun HomeOfflineNotice() {
 val state = LiveRepository.state
 if (state.startsWith("Live") || state == "Not paired" || state == "Connecting…") return
 Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(24.dp))
  .background(DeskColors.raised).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
  Text("Can't reach your backend", color = Paper, fontSize = 16.sp, fontWeight = FontWeight.Bold)
  Text(if (LiveRepository.time.isBlank()) "Reconnecting automatically."
   else "Showing the last data received. Reconnecting automatically.", color = Muted, fontSize = 12.sp)
  DeskAction("Retry now", click = LiveRepository::retryNow)
 }
}
