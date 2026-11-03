package dev.olympus.desk

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
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val Ink=DeskColors.background
private val Card=DeskColors.surface
private val Cobalt=Color(0xFF879BFF)
private val Apricot=Color(0xFFEDB48F)
private val Lilac=Color(0xFFCDC5FF)
private val Mint=Color(0xFFB4DFBE)
private val Muted=Color(0xFFBFC4D1)
private val Paper=Color(0xFFF5F3EF)
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
 var gridColumns by remember(wide){mutableIntStateOf(if(wide)prefs.getInt("landscape-columns-v7",8)else 4)}
 var widgets by remember(layoutKey){mutableStateOf(try{prefs.getString(layoutKey,null)?.let{DeskLayout.decode(it)}?:DeskLayout.defaults(wide)}catch(_:Exception){DeskLayout.defaults(wide)})}

 var undoHistory by rememberSaveable(layoutKey){mutableStateOf(arrayListOf<String>())}
 val previousLayout=undoHistory.lastOrNull()?.let{DeskLayout.decode(if(it.startsWith("["))it else JSONObject(it).getString("widgets"))}
 var selectedWidgetId by rememberSaveable{mutableStateOf("")}
 fun save(items:List<DeskWidget>){undoHistory=ArrayList((undoHistory+JSONObject().put("columns",gridColumns).put("widgets",DeskLayout.encode(widgets)).toString()).takeLast(20));widgets=items;prefs.edit().putString(layoutKey,DeskLayout.encode(items)).apply()}
 var tab by rememberSaveable{mutableStateOf("Home")}
 var clusterSection by rememberSaveable{mutableStateOf("Nodes")}
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
 var settings by rememberSaveable{mutableStateOf(false)}
 var importLayout by remember{mutableStateOf(false)}
 var message by remember{mutableStateOf("")}
 var dim by remember{mutableFloatStateOf(prefs.getFloat("brightness",.7f))}
 var reducedMotion by remember{mutableStateOf(prefs.getBoolean("reducedMotion",false))}
 var dimMode by remember{mutableStateOf(prefs.getBoolean("deskMode",false))}
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
 Surface(color=Ink,modifier=Modifier.fillMaxSize()){
 Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)){
 val destinations = listOf("Home" to "Home", "Cluster" to "Nodes", "Services" to "Apps", "Inbox" to "Events")
 val navigate: (String) -> Unit = {
  tab=it
  detail=""
  detailHistory=arrayListOf()
  editing=false
 }
 if(wide&&!editing) Column(
  Modifier.width(84.dp).fillMaxHeight().background(Card),
  verticalArrangement=Arrangement.Center
 ){
  destinations.forEach { (destination, icon) ->
   NavigationRailItem(
    selected=tab==destination,
    onClick={navigate(destination)},
    icon={LabGlyph(icon,if(tab==destination)Cobalt else Muted,22.dp)},
    label={Text(destination,color=if(tab==destination)Paper else Muted)}
   )
  }
 }
 Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal=14.dp).offset(x=if(dimMode)shift.dp else 0.dp)){
  Row(Modifier.fillMaxWidth().height(44.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
   if(detail.isNotEmpty())TextButton(onClick={backDetail()},modifier=Modifier.size(40.dp),contentPadding=PaddingValues(0.dp)){LabGlyph("Back",Paper,23.dp)}else Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF4A63EA)),contentAlignment=Alignment.Center){LabGlyph("Brand",Paper,22.dp)}
   Text(if(detail.isEmpty())if(tab=="Home")"olympus"else tab else if(detail.startsWith("event:")&&selectedEvent?.id==detail.substringAfter(":"))incidentTitle(selectedEvent!!)else routeTitle(detail),fontSize=18.sp,fontWeight=FontWeight.SemiBold,color=Paper,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.widthIn(max=if(wide)350.dp else 145.dp))
   if(detail.isEmpty()&&!editing)LiveStatus(reducedMotion)
   Spacer(Modifier.weight(1f))

   if(detail.startsWith("node:")||detail.startsWith("app:"))TextButton(onClick={pin(if(detail.startsWith("node:"))"Node"else"App",detail.substringAfter(":"))}){Text("Pin",color=Cobalt)}
   if(editing){if(previousLayout!=null)TextButton(onClick={val old=previousLayout;val entry=undoHistory.lastOrNull();if(entry!=null&&!entry.startsWith("[")){gridColumns=JSONObject(entry).optInt("columns",gridColumns);if(wide)prefs.edit().putInt("landscape-columns-v7",gridColumns).apply()};undoHistory=ArrayList(undoHistory.dropLast(1));if(old!=null){widgets=old;prefs.edit().putString(layoutKey,DeskLayout.encode(old)).apply()}}){Text("Undo",color=Cobalt)};TextButton(onClick={picker=true}){Text("+ Widget",color=Cobalt)};TextButton(onClick={editing=false}){Text("Done",color=Mint)}}
   else Row{if(detail.isEmpty()&&tab=="Home"&&!compactHeader)IconButton(onClick={if(tab!="Home"||detail.isNotBlank()){tab="Home";detail=""};editing=!editing},modifier=Modifier.size(38.dp)){LabGlyph("Edit",Muted,20.dp)};if(detail.isEmpty()&&!compactHeader)IconButton(onClick={settings=true},modifier=Modifier.size(38.dp)){LabGlyph("Settings",Muted,21.dp)};Box{
    IconButton(onClick={menu=true},modifier=Modifier.size(38.dp)){Text("•••",color=Muted)}
    DeskPopup(menu,{menu=false},if(detail.isNotBlank())listOf("Copy reference" to {(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Olympus resource",detail));message="Reference copied"})else listOf(
     "Edit Home" to {tab="Home";detail="";editing=true},
     "Add widget" to {tab="Home";detail="";picker=true},
     "Export layout" to {(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Olympus layout",DeskLayout.encode(widgets)));message="Layout copied"},
     "Import layout" to {importLayout=true},
     "Restore previous layout" to {val old=prefs.getString(if(wide)"landscape-v6"else"portrait-v6",null);if(old!=null)try{save(DeskLayout.decode(old));gridColumns=if(wide)6 else 4;if(wide)prefs.edit().putInt("landscape-columns-v7",6).apply();message="Previous layout restored"}catch(_:Exception){message="Previous layout unavailable"}else message="No previous layout"},"Settings" to {settings=true}))
   }}
  }
  if(message.isNotEmpty()){Text(message,color=Mint,fontSize=12.sp);LaunchedEffect(message){delay(2200);message=""}}
  BoxWithConstraints(Modifier.weight(1f)){val viewport=maxHeight
  AnimatedContent(targetState=if(wide&&tab=="Inbox"&&detail.startsWith("event:"))"Inbox" else detail.ifEmpty{tab},modifier=Modifier.fillMaxSize(),transitionSpec={if(reducedMotion)EnterTransition.None togetherWith ExitTransition.None else fadeIn(tween(180)) togetherWith fadeOut(tween(120))},label="page"){page->
   if(wide&&tab=="Inbox"&&(page=="Inbox"||page.startsWith("event:"))){
    Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(14.dp)){
     Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom=8.dp)){
      NotificationInbox{selectedEvent=it;detail="event:${it.id}"}
     }
     Column(Modifier.weight(1.2f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom=8.dp)){
      if(!detail.startsWith("event:")) DeskEmpty("Select an incident","Its timeline and related resources will appear here.")
      else EventRoute(detail.substringAfter(":"),selectedEvent,{selectedEvent=it}){target->
       detailHistory=ArrayList(detailHistory+detail)
       detail=target
      }
     }
    }
   }else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=8.dp)){
    when{
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
     page=="Cluster"->{CompactTabs(listOf("Nodes","Applications"),clusterSection,{clusterSection=it});if(clusterSection=="Nodes")Nodes{detail="node:$it"}else Apps{detail="app:$it"}}
     page=="Services"->{CompactTabs(listOf("Monitors","Integrations"),servicesSection,{servicesSection=it});if(servicesSection=="Monitors")MonitorScreen{detail="monitor:$it"}else IntegrationDirectory{detail="integration:$it"}}
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
      @Composable fun canvas(){WidgetGrid(widgets,wide,editing,selected?.id.orEmpty(),reducedMotion,(viewport-12.dp).coerceAtLeast(0.dp),wide&&!editing,{editing=true},::save,{destination->when(destination){"Nodes","Apps"->{tab="Cluster";clusterSection=if(destination=="Nodes")"Nodes"else"Applications"};"Monitors"->{tab="Services";servicesSection="Monitors"};"Events","Attention"->tab="Inbox";else->detail=destination}},{selectedWidgetId=it.id})}
      if(!editing&&wide) Row(
       Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)
      ){
       HomeVerdict(openIncident,Modifier.weight(1f).heightIn(min=180.dp))
       Box(Modifier.weight(3f)){canvas()}
      }
      else if(editing&&wide)Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){
       Box(Modifier.weight(1f)){canvas()}
       Column(Modifier.width(244.dp)){selected?.let{EditorInspector(it,widgets,wide,::save,{configuring=it},{save(widgets.filterNot{w->w.id==it.id})})}}
      }else{if(!editing) HomeVerdict(openIncident);canvas();if(editing)selected?.let{EditorInspector(it,widgets,wide,::save,{configuring=it},{save(widgets.filterNot{w->w.id==it.id})})}}
     }
    }
   }
  }
 }


 if(!wide&&!editing) NavigationBar(containerColor=Card){
  destinations.forEach { (destination, icon) ->
   NavigationBarItem(
    selected=tab==destination,
    onClick={navigate(destination)},
    icon={LabGlyph(icon,if(tab==destination)Cobalt else Muted,22.dp)},
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
   "Reset this layout"->{save(DeskLayout.defaults(wide));gridColumns=if(wide)8 else 4;if(wide)prefs.edit().putInt("landscape-columns-v7",8).apply();tab="Home";detail=""}
  }},{settings=false},focusMode,{focusMode=it})

}

@Composable private fun HomeVerdict(open: (LabEvent) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
 val state = LiveRepository.state
 val connected = state.startsWith("Live")
 val firing = if (connected) LiveRepository.active
  .filter { it.state == "Firing" }
  .distinctBy { it.id }
  .sortedByDescending { it.kind.equals("critical", ignoreCase = true) }
 else emptyList()
 val incident = firing.firstOrNull()
 val verified = state == "Live" && LiveRepository.signals.isNotEmpty() && LiveRepository.signals.values.all { it } && LiveRepository.active.none { it.state == "Unknown" }
 val headline = when {
  incident?.kind.equals("critical", ignoreCase = true) -> "Critical"
  incident != null -> "Needs you"
  verified -> "All calm"
  else -> "Can’t confirm"
 }
 val summary = when {
  incident != null -> "${firing.size} firing • ${incidentTitle(incident)}"
  verified -> "No firing incidents"
  else -> state
 }
 val accent = if (incident != null) Apricot else if (verified) Cobalt else Muted
 Column(
  modifier.padding(bottom=8.dp).clip(RoundedCornerShape(24.dp))
   .background(if(incident!=null) Color(0xFF3A2B14) else Card).padding(16.dp),
  verticalArrangement=Arrangement.spacedBy(6.dp)
 ) {
  Column {
   Text(headline, color=accent, fontSize=26.sp, fontWeight=FontWeight.Bold)
   Text(summary, color=Paper, fontSize=13.sp, maxLines=2, overflow=TextOverflow.Ellipsis)
   if (incident != null && !verified) Text("Partial data", color=Muted, fontSize=12.sp)
  }
  if (incident != null) TextButton(onClick={open(incident)}, modifier=Modifier.heightIn(min=44.dp)) {
   Text("View incident", color=accent)
  }
 }
}

@Composable private fun LiveStatus(reduced:Boolean){
 val live=LiveRepository.state.startsWith("Live")
 val healthy=live&&!LiveRepository.state.contains("Partial")
 val transition=rememberInfiniteTransition(label="connection")
 val pulse by transition.animateFloat(.45f,1f,infiniteRepeatable(tween(1200),RepeatMode.Reverse),label="pulse")
 Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
  Box(Modifier.size(6.dp).alpha(if(live&&!reduced)pulse else 1f).background(if(healthy)Mint else Apricot,RoundedCornerShape(6.dp)))
  Text(when{LiveRepository.state.contains("Partial")->"Partial";live->"Live";LiveRepository.state=="Stale"->"Stale";else->"Offline"},color=if(healthy)Mint else Apricot,fontSize=11.sp)
 }
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
 val stripHeight=78.dp
 val unit=if(statusStrip)((viewport-gapDp*2-stripHeight)/2).coerceAtLeast(76.dp)else deskRowHeight(viewport.value,gapDp.value,rows,wide,fillViewport).dp
 if(items.isEmpty())DeskAction("Add widget",true){edit()}
 Layout(content={cells.forEach{cell->val widget=cell.widget
  var delta by remember(widget.id){mutableStateOf(Offset.Zero)}
  Box(Modifier.zIndex(if(delta!=Offset.Zero)1f else 0f).graphicsLayer{translationX=delta.x;translationY=delta.y}.pointerInput(widget.id,columns,cell.column,cell.row){detectDragGesturesAfterLongPress(onDragStart={edit();configure(widget);delta=Offset.Zero},onDrag={change,amount->change.consume();delta+=amount},onDragEnd={save(DeskLayout.move(latest,widget.id,cell.column+kotlin.math.round(delta.x/pitchX).toInt(),cell.row+kotlin.math.round(delta.y/pitchY).toInt(),columns));delta=Offset.Zero},onDragCancel={delta=Offset.Zero})}.then(if(editing&&widget.id==selectedId)Modifier.border(2.dp,Cobalt.copy(alpha=.85f),RoundedCornerShape(20.dp))else Modifier).clip(RoundedCornerShape(20.dp)).background(widgetColor(widget.kind)).clickable(enabled=editing||widget.kind!="Clock"){if(editing)configure(widget)else open(if(widget.kind=="Attention")LiveRepository.issues.firstOrNull()?.target?:"Events"else destination(widget))}.padding(12.dp)){
   WidgetBody(widget,Modifier.fillMaxSize())
   if(editing&&widget.id==selectedId){
    @Composable fun handle(horizontal:Boolean){
     Box(Modifier.align(if(horizontal)Alignment.CenterEnd else Alignment.BottomCenter).offset(x=if(horizontal)20.dp else 0.dp,y=if(horizontal)0.dp else 20.dp).size(if(horizontal)24.dp else 48.dp,if(horizontal)48.dp else 24.dp).pointerInput(widget.id,cell.width,cell.height,horizontal){var amount=0f;detectDragGestures(onDragStart={amount=0f},onDrag={change,d->change.consume();amount+=if(horizontal)d.x else d.y},onDragEnd={val steps=kotlin.math.round(amount/if(horizontal)pitchX else pitchY).toInt();save(DeskLayout.resize(latest,widget.id,cell.width+if(horizontal)steps else 0,cell.height+if(horizontal)0 else steps,columns))})},contentAlignment=Alignment.Center){Box(Modifier.size(if(horizontal)4.dp else 26.dp,if(horizontal)26.dp else 4.dp).background(Cobalt,RoundedCornerShape(4.dp)))}
    }
    handle(true);handle(false)
   }
  }
 }},modifier=Modifier.fillMaxWidth()){measurables,constraints->
  val gap=gapDp.roundToPx();val cellWidth=(constraints.maxWidth-gap*(columns-1))/columns;val rowHeight=unit.roundToPx()
  pitchX=(cellWidth+gap).toFloat();pitchY=(rowHeight+gap).toFloat()
  val placeables=measurables.mapIndexed{index,m->val c=cells[index];m.measure(Constraints.fixed(cellWidth*c.width+gap*(c.width-1),(if(statusStrip&&c.row==2)stripHeight.roundToPx()else rowHeight*c.height+gap*(c.height-1))))}
  val rows=cells.maxOfOrNull{it.row+it.height}?:0
  layout(constraints.maxWidth,(if(statusStrip)2*rowHeight+stripHeight.roundToPx()+gap*2 else rows*rowHeight+gap*(rows-1)).coerceAtLeast(0)){placeables.forEachIndexed{i,p->val c=cells[i];p.placeRelative(c.column*(cellWidth+gap),c.row*(rowHeight+gap))}}
 }
}

internal fun destination(w:DeskWidget)=when(w.kind){"Clock"->"Home";"Monitors"->"Monitors";"Node"->"node:${w.target}";"App","Service"->"app:${w.target}";"Backups"->"Backups";"Events"->"events-history:${w.target.ifBlank{"All"}}";"Alerts"->if(w.target.isBlank()||w.target=="All")"Events"else"events-source:${w.target}";"Attention"->"Attention";"GitOps"->"Apps";"Downloads"->"app:qbittorrent";else->"Nodes"}
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
 var label by remember{mutableStateOf(initial?.label?:"")};var fields by remember{mutableStateOf(initial?.fields?:emptyList())}
 var category by remember{mutableStateOf("All")};var gallerySearch by remember{mutableStateOf("")};var configuring by remember{mutableStateOf(initial!=null)};var sourceOpen by remember{mutableStateOf(false)};var search by remember{mutableStateOf("")}
 val wide=wideWindow();val columns=LocalGridColumns.current
 val targets=when(kind){"Service"->LiveRepository.integrations.map{it.name};"App"->LiveRepository.services.map{it.name};"Node"->LiveRepository.nodes.map{it.name};"Events","Alerts"->listOf("All")+(LiveRepository.active+LiveRepository.events).map{it.source}.distinct();else->emptyList()}
 LaunchedEffect(kind,targets){if(targets.isNotEmpty()&&target !in targets)target=targets.first()}
 val preview=DeskWidget(initial?.id?:"preview",kind,target,width,height,label,fields,initial?.x?:-1,initial?.y?:-1)
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
internal fun welcomeColor():Color = DeskColors.cobalt
private fun attentionActive()=LiveRepository.issues.isNotEmpty()
private fun widgetColor(kind:String)=when(kind){"Clock"->welcomeColor();"GitOps"->DeskColors.lilac;"Attention"->if(attentionActive())DeskColors.apricot else Card;else->Card}
@Composable internal fun Spark(values:List<Float>,color:Color,percent:Boolean=false){if(values.size<2){Text("Collecting trend…",color=Muted,fontSize=10.sp);return};Canvas(Modifier.fillMaxWidth().height(24.dp)){val low=0f;val range=if(percent)100f else values.max().coerceAtLeast(.1f);val path=Path();values.forEachIndexed{i,value->val x=i.toFloat()/(values.size-1)*size.width;val y=size.height-(value.coerceIn(0f,range)-low)/range*(size.height-4)-2;if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};drawPath(path,color,style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))}}

@Composable private fun Title(value:String){Text(value,color=Paper,fontSize=25.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(vertical=12.dp))}
@Composable private fun Info(label:String,value:String){Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)){Text(label,color=Muted,fontSize=12.sp,modifier=Modifier.weight(1f));Text(value,color=Paper,fontSize=12.sp,modifier=Modifier.weight(1.6f))}}
@Composable internal fun Usage(label:String,value:Float?,color:Color){Row(Modifier.fillMaxWidth().padding(top=5.dp,bottom=4.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(label,color=Muted,fontSize=12.sp);Text("${value.fmt()}%",color=Paper,fontSize=12.sp)};if(value!=null)LinearProgressIndicator(progress={(value/100).coerceIn(0f,1f)},color=color,trackColor=Color(0xFF3C4250),modifier=Modifier.fillMaxWidth().height(3.dp),drawStopIndicator={})}
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
@Composable internal fun AppIcon(inputIcon:String,name:String){val asset=when(name.lowercase()){"argocd"->"https://raw.githubusercontent.com/selfhst/icons/main/png/argo-cd.png";"alloy"->"https://raw.githubusercontent.com/selfhst/icons/main/png/grafana-alloy.png";else->inputIcon};val context=LocalContext.current;var bitmap by remember(asset){mutableStateOf(iconCache.get(asset))};LaunchedEffect(asset){if(asset.isNotEmpty()&&bitmap==null){bitmap=withContext(Dispatchers.IO){try{require(asset.startsWith("https://raw.githubusercontent.com/")||asset.startsWith("https://cdn.jsdelivr.net/"));val file=java.io.File(context.cacheDir,"asset-${asset.hashCode()}.png");val bytes=if(file.exists())file.readBytes()else{val connection=URL(asset).openConnection().apply{connectTimeout=4000;readTimeout=4000};connection.getInputStream().use{it.readBytes().also{data->require(data.size<1048576);file.writeBytes(data)}}};BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=4})?.also{iconCache.put(asset,it)}}catch(_:Exception){null}}}};if(bitmap!=null)Image(bitmap!!.asImageBitmap(),contentDescription=null,modifier=Modifier.size(26.dp))else Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF394462)),contentAlignment=Alignment.Center){Text(name.take(1).uppercase(),color=Paper,fontSize=12.sp)}}
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

@Composable private fun EditorInspector(widget:DeskWidget,items:List<DeskWidget>,wide:Boolean,save:(List<DeskWidget>)->Unit,configure:()->Unit,remove:()->Unit){
 val columns=LocalGridColumns.current
 Column(Modifier.fillMaxWidth().padding(vertical=8.dp).clip(RoundedCornerShape(20.dp)).background(Card).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Text(widget.label.ifBlank{if(widget.kind=="Clock")"Welcome"else widget.kind},color=Paper,fontSize=18.sp,fontWeight=FontWeight.Medium)
  Layout(content={Box(Modifier.size((widget.width*100).dp,(widget.height*112).dp).clip(RoundedCornerShape(16.dp)).background(widgetColor(widget.kind)).padding(12.dp)){WidgetBody(widget,Modifier.fillMaxSize())}},modifier=Modifier.fillMaxWidth().height(116.dp)) {children,constraints->
   val rawWidth=(widget.width*100).dp.roundToPx();val rawHeight=(widget.height*112).dp.roundToPx()
   val item=children.single().measure(Constraints.fixed(rawWidth,rawHeight))
   val scale=minOf(constraints.maxWidth.toFloat()/rawWidth,constraints.maxHeight.toFloat()/rawHeight,1f)
   layout(constraints.maxWidth,constraints.maxHeight){item.placeWithLayer(((constraints.maxWidth-rawWidth*scale)/2).toInt(),0){scaleX=scale;scaleY=scale;transformOrigin=androidx.compose.ui.graphics.TransformOrigin(0f,0f)}}
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
  Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){DeskAction("Configure",click=configure);TextButton(onClick=remove){Text("Remove",color=Apricot)}}
 }
}

@Composable private fun IntegrationDirectory(open:(String)->Unit){
 if(LiveRepository.integrations.isEmpty())Text("No integrations available",color=Muted,modifier=Modifier.padding(16.dp))
 LiveRepository.integrations.forEach{service->
  Column(Modifier.fillMaxWidth().padding(top=10.dp).clip(RoundedCornerShape(18.dp)).background(Card).clickable{open(service.name)}.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){AppIcon(LiveRepository.services.find{it.name==service.name}?.icon?:"",service.name);Text(service.name,color=Paper,fontSize=17.sp)}
   service.fields.entries.chunked(3).forEach{fields->Row(horizontalArrangement=Arrangement.spacedBy(20.dp)){fields.forEach{(field,value)->Column{Text(field,color=Muted,fontSize=11.sp);Text(value?.toInt()?.toString()?:"—",color=Paper,fontSize=20.sp)}}}}
   if(service.error.isNotBlank())Text("Some fields unavailable",color=Apricot,fontSize=12.sp)
  }
 }
}

@Composable private fun MetricWidget(w:DeskWidget,modifier:Modifier){
 val cpu=w.kind=="CPU";val value=if(cpu)LiveRepository.cpuPercent else LiveRepository.memoryPercent
 val values=LiveRepository.history[if(cpu)"cpu"else"memory"].orEmpty()
 Column(modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(2.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(w.label.ifBlank{w.kind},color=Muted,fontSize=12.sp);if(w.width>2)Text(if(!LiveRepository.state.startsWith("Live"))"Cached"else"Recent",color=Muted,fontSize=10.sp)}
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
   Text(if(value==null)"—"else"${value.fmt()}%",color=Paper,fontSize=if(w.width<2)20.sp else 26.sp,lineHeight=29.sp,fontWeight=FontWeight.SemiBold)
   if(w.width>=2)Text(if(cpu)if(value==null)"Usage unavailable"else"${(value/100f*LiveRepository.totalCores).fmt()} / ${LiveRepository.totalCores} cores"else"${LiveRepository.usedMemory.fmt()} / ${LiveRepository.totalMemory.fmt()} GiB",color=Muted,fontSize=10.sp,lineHeight=12.sp,maxLines=2)
  }
  if(values.size<2)Text("Collecting trend…",color=Muted,fontSize=10.sp)else Canvas(Modifier.fillMaxWidth().weight(1f).heightIn(min=12.dp).semantics{contentDescription="${w.kind} recent samples, zero to one hundred percent"}){
   val path=Path();values.forEachIndexed{i,v->val x=i.toFloat()/(values.size-1)*size.width;val y=size.height*(1-v.coerceIn(0f,100f)/100f);if(i==0)path.moveTo(x,y)else path.lineTo(x,y)}
   drawPath(path,if(cpu)Cobalt else Lilac,style=androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx()))
  }
 }
}

@Composable private fun SummaryWidget(w:DeskWidget,modifier:Modifier){
 val pastel=w.kind=="GitOps"||(w.kind=="Attention"&&attentionActive());val fg=if(pastel)Color(0xFF29233D)else Paper;val sub=if(pastel)fg.copy(alpha=.8f)else Muted
 val event=LiveRepository.active.firstOrNull{needsAttention(it)}
 val latest=LiveRepository.events.firstOrNull{(w.target.isBlank()||w.target=="All"||it.source==w.target)&&(LocalShowTests.current||!(it.kind=="Test"||it.source=="Test"||it.title=="Test"))}
 val known=LiveRepository.lastSuccess>0L
 val value=if(!known)"—"else when(w.kind){"Attention"->event?.let(::incidentTitle)?:LiveRepository.issues.firstOrNull()?.title?:if(LiveRepository.state.startsWith("Live")&&LiveRepository.signals.isNotEmpty()&&LiveRepository.signals.values.all{it})"All clear"else"Unconfirmed";"GitOps"->"${LiveRepository.services.count{it.health=="Healthy"&&it.sync=="Synced"}} / ${LiveRepository.services.size}";"Nodes"->"${LiveRepository.nodes.count{it.ready}} / ${LiveRepository.nodes.size}";"Monitors"->"${LiveRepository.monitorsUp?:"—"} / ${LiveRepository.monitorsTotal?:"—"}";else->latest?.let(::incidentTitle)?:"No recent activity"}
 val supporting=if(!known)"Connecting…"else when(w.kind){"Attention"->event?.let{"${it.state} · ${it.kind} · ${it.node.ifBlank{it.source}}"}?:"${LiveRepository.active.count{it.state=="Firing"&&!isInformational(it.kind)}} firing · ${LiveRepository.active.count{it.state=="Pending"&&!isInformational(it.kind)}} pending";"GitOps"->"Healthy & synced";"Nodes"->"Nodes ready";"Monitors"->"Checks up";else->latest?.let{listOf(it.state,it.node.ifBlank{it.namespace}).filter{v->v.isNotBlank()}.joinToString(" · ")+" · "+localTime(it.resolvedAt.ifBlank{it.time})}?:"No records"}
 BoxWithConstraints(modifier.fillMaxSize()){
  val compact=maxHeight<80.dp;val roomy=maxHeight>110.dp
  Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(if(compact)1.dp else 6.dp)){
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(w.label.ifBlank{when(w.kind){"Attention"->if(attentionActive())"Needs attention"else"Cluster status";"Events"->"Recent activity";else->w.kind}},color=sub,fontSize=11.sp,lineHeight=13.sp,modifier=Modifier.weight(1f));if(w.kind=="GitOps")LabGlyph("GitOps",fg,16.dp)}
   Text(value,color=fg,fontSize=if(w.width<2)17.sp else if(value.length>12)if(compact)14.sp else 17.sp else if(compact)23.sp else 32.sp,lineHeight=if(compact)25.sp else 36.sp,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold)
   Text(supporting,color=sub,fontSize=10.sp,lineHeight=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
   if(roomy&&w.kind=="Nodes")Row(Modifier.fillMaxWidth().padding(top=4.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)){LiveRepository.nodes.forEach{node->Column(Modifier.weight(1f)){Box(Modifier.fillMaxWidth().height(4.dp).background(if(node.ready)Mint else Apricot,RoundedCornerShape(3.dp)));Text(node.name,color=sub,fontSize=9.sp,maxLines=1)}}}
  }
 }
}

private fun routeTitle(route:String):String=when {
 route.startsWith("monitor:")->LiveRepository.monitors.find{it.id==route.substringAfter(":")}?.name?:"Monitor"
 route.startsWith("pod:")->route.substringAfter('/').substringBefore('@')
 route.startsWith("event:")->(LiveRepository.active+LiveRepository.events).find{it.id==route.substringAfter(":")}?.let(::incidentTitle)?:"Incident"
 route.startsWith("events-history:")->"Recent activity"
 route.startsWith("events-source:")->"Inbox · ${route.substringAfter(":")}"
 else->route.substringAfter(":")
}
