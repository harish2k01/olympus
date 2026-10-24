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
 Column(modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(5.dp)){
  Text(title.ifBlank{"OLYMPUS / ${now.dayOfWeek.name}"},color=Color.White.copy(alpha=.86f),fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){Text(now.format(DateTimeFormatter.ofPattern("HH:mm")),color=Color.White,fontSize=if(height>1)43.sp else 30.sp,fontWeight=FontWeight.Light,lineHeight=if(height>1)48.sp else 34.sp)};if(height==1)ClusterMascot(Modifier.size(32.dp))}
  if(height>1){Text(now.format(DateTimeFormatter.ofPattern("d MMMM yyyy")),color=Color.White,fontSize=12.sp);Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.CenterStart){ClusterMascot(Modifier.size(58.dp))};Text("${when(now.hour){in 5..11->"A good morning";in 12..16->"A good afternoon";in 17..22->"A good evening";else->"A quiet night"}}\nfor your lab.",color=Color.White,fontSize=16.sp,lineHeight=21.sp,fontWeight=FontWeight.Medium)}
 }
}

@Composable internal fun HomeApp(){
 val context=LocalContext.current
 val wide=wideWindow()
 val prefs=remember{context.getSharedPreferences("home-v3",0)}
 val layoutKey=if(wide)"landscape-v4" else "portrait-v4"
 var widgets by remember(layoutKey){mutableStateOf(try{val saved=DeskLayout.decode(prefs.getString(layoutKey,prefs.getString(if(wide)"landscape"else"portrait",""))!!);if(prefs.getString(layoutKey,null)==null)saved+DeskLayout.defaults().filter{it.kind in listOf("Monitors","Service","Downloads")&&saved.none{old->old.kind==it.kind}}.map{it.copy(id=UUID.randomUUID().toString())}else saved}catch(_:Exception){DeskLayout.defaults()})}

 fun save(items:List<DeskWidget>){widgets=items;prefs.edit().putString(layoutKey,DeskLayout.encode(items)).apply()}
 var tab by rememberSaveable{mutableStateOf("Home")}
 var selectedEvent by remember{mutableStateOf<LabEvent?>(null)}
 var detail by rememberSaveable{mutableStateOf("")}
 var editing by rememberSaveable{mutableStateOf(false)}
 var picker by remember{mutableStateOf(false)}
 var configuring by remember{mutableStateOf<DeskWidget?>(null)}
 var menu by remember{mutableStateOf(false)}
 var settings by remember{mutableStateOf(false)}
 var importLayout by remember{mutableStateOf(false)}
 var message by remember{mutableStateOf("")}
 var dim by remember{mutableFloatStateOf(prefs.getFloat("brightness",.7f))}
 var reducedMotion by remember{mutableStateOf(prefs.getBoolean("reducedMotion",false))}
 var dimMode by remember{mutableStateOf(prefs.getBoolean("deskMode",false))}
 var dockShown by remember{mutableStateOf(true)}
 var lastInteraction by remember{mutableLongStateOf(System.currentTimeMillis())}
 var keepAwake by remember{mutableStateOf(prefs.getBoolean("keepAwake",true))}
 var showTests by remember{mutableStateOf(prefs.getBoolean("showTests",false))}
 var dockSeconds by remember{mutableIntStateOf(prefs.getInt("dockSeconds",4))}
 var gridGap by remember{mutableIntStateOf(prefs.getInt("gridGap",10))}
 DisposableEffect(keepAwake){val window=(context as? Activity)?.window;if(keepAwake)window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)else window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);onDispose{}}
 var focusMode by remember{mutableStateOf(prefs.getBoolean("focusMode",false))}
 LaunchedEffect(lastInteraction,editing,menu,picker,settings,configuring,dockSeconds){dockShown=true;if(!editing&&!menu&&!picker&&!settings&&configuring==null){delay(dockSeconds*1000L);dockShown=false}}
 var shift by remember{mutableIntStateOf(0)}
 LaunchedEffect(dimMode){while(dimMode){delay(60000);shift=(shift+1)%3}}
 DisposableEffect(dimMode,dim){val window=(context as? Activity)?.window;val old=window?.attributes?.screenBrightness;window?.attributes=window?.attributes?.apply{screenBrightness=if(dimMode)dim else -1f};onDispose{if(old!=null)window?.attributes=window?.attributes?.apply{screenBrightness=old}}}
 fun pin(kind:String,target:String){if(widgets.none{it.kind==kind&&it.target==target})save(widgets+DeskWidget(UUID.randomUUID().toString(),kind,target));message="Pinned to Home"}
 BackHandler(detail.isNotEmpty()||editing){if(detail.isNotEmpty())detail="" else editing=false}
 CompositionLocalProvider(LocalReducedMotion provides reducedMotion,LocalShowTests provides showTests,LocalGridGap provides gridGap){
 Surface(color=Ink,modifier=Modifier.fillMaxSize()){
 Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).pointerInput(Unit){awaitPointerEventScope{while(true){val event=awaitPointerEvent(PointerEventPass.Initial);if(event.changes.any{it.pressed})lastInteraction=System.currentTimeMillis()}}}){
 Column(Modifier.fillMaxSize().padding(horizontal=16.dp).offset(x=if(dimMode)shift.dp else 0.dp)){
  AnimatedVisibility(visible=!focusMode||dockShown||editing){
  Row(Modifier.fillMaxWidth().height(52.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
   if(detail.isNotEmpty())TextButton(onClick={detail=""},modifier=Modifier.size(40.dp),contentPadding=PaddingValues(0.dp)){LabGlyph("Back",Paper,23.dp)}else Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF4A63EA)),contentAlignment=Alignment.Center){LabGlyph("Brand",Paper,22.dp)}
   Text(if(detail.isEmpty())if(tab=="Home")"olympus"else tab else if(detail.startsWith("events-history:"))"Recent activity"else if(detail.startsWith("event:"))"Event" else detail.substringAfter(":"),fontSize=20.sp,fontWeight=FontWeight.SemiBold,color=Paper,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.widthIn(max=if(wide)350.dp else 145.dp))
   if(detail.isEmpty()&&!editing)LiveStatus(reducedMotion)
   Spacer(Modifier.weight(1f))
   if(detail.startsWith("node:")||detail.startsWith("app:"))TextButton(onClick={pin(if(detail.startsWith("node:"))"Node"else"App",detail.substringAfter(":"))}){Text("Pin",color=Cobalt)}
   if(editing){TextButton(onClick={picker=true}){Text("+ Widget",color=Cobalt)};TextButton(onClick={editing=false}){Text("Done",color=Mint)}}
   else Row{IconButton(onClick={if(tab!="Home"||detail.isNotBlank()){tab="Home";detail=""};editing=!editing},modifier=Modifier.size(38.dp)){LabGlyph("Apps",Muted,20.dp)};IconButton(onClick={settings=true},modifier=Modifier.size(38.dp)){LabGlyph("Settings",Muted,21.dp)};Box{
    IconButton(onClick={menu=true},modifier=Modifier.size(38.dp)){Text("•••",color=Muted)}
    DeskPopup(menu,{menu=false},listOf(
     (if(focusMode)"Leave focus mode"else"Focus mode") to {focusMode=!focusMode;prefs.edit().putBoolean("focusMode",focusMode).apply()},
     "Edit Home" to {tab="Home";detail="";editing=true},
     "Add widget" to {tab="Home";detail="";picker=true},
     "Export layout" to {(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Olympus layout",DeskLayout.encode(widgets)));message="Layout copied"},
     "Import layout" to {importLayout=true},"Settings" to {settings=true}))
   }}
  }
  }
  if(message.isNotEmpty()){Text(message,color=Mint,fontSize=12.sp);LaunchedEffect(message){delay(2200);message=""}}
  BoxWithConstraints(Modifier.weight(1f)){val viewport=maxHeight
  AnimatedContent(targetState=detail.ifEmpty{tab},modifier=Modifier.fillMaxSize(),transitionSpec={if(reducedMotion)EnterTransition.None togetherWith ExitTransition.None else fadeIn(tween(180)) togetherWith fadeOut(tween(120))},label="page"){page->
   Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=if(page=="Home")0.dp else 10.dp)){
    when{
     page.startsWith("events-history:")->NotificationInbox(page.substringAfter(":"),"History",true){selectedEvent=it;detail="event:${it.id}"}
     page.startsWith("events-source:")->NotificationInbox(page.substringAfter(":")){selectedEvent=it;detail="event:${it.id}"}
     page.startsWith("node:")->NodeDetailScreen(page.substringAfter(":"))
     page.startsWith("app:")->ApplicationScreen(page.substringAfter(":")){detail=it}
     page.startsWith("event:")->((LiveRepository.active+LiveRepository.events).find{it.id==page.substringAfter(":")}?:selectedEvent?.takeIf{it.id==page.substringAfter(":")})?.let{NotificationDetail(it)}?:Text("Event no longer available",color=Muted)
     page=="Attention"->{Title("Needs attention");if(LiveRepository.issues.isEmpty())Text("No current issues",color=Mint);LiveRepository.issues.forEach{item->Column(Modifier.fillMaxWidth().padding(bottom=10.dp).clip(RoundedCornerShape(16.dp)).background(Card).clickable{detail=item.target}.padding(16.dp)){Text(item.title,color=Paper,fontSize=14.sp);Text(item.severity,color=Apricot,fontSize=11.sp)}}}
     page=="Backups"-> {Title("Backups");Info("Destination",when(LiveRepository.backup){true->"Available";false->"Unavailable";null->"Unknown"});Info("Volumes with backup records",if(LiveRepository.backupVolumes==null)"Unavailable"else"${LiveRepository.backupRecorded} / ${LiveRepository.backupVolumes}");Info("Last recorded backup",LiveRepository.backupLast.ifEmpty{"None recorded"})}
     page=="Nodes"->Nodes{detail="node:$it"}
     page=="Events"->NotificationInbox{selectedEvent=it;detail="event:${it.id}"}
     page=="Apps"->Apps{detail="app:$it"}
     page=="Monitors"->MonitorScreen()
     else->WidgetGrid(widgets,wide,editing,reducedMotion,(viewport-10.dp).coerceAtLeast(0.dp),focusMode&&!dockShown&&!editing,{editing=true},::save,{detail=it},{configuring=it})
    }
   }
  }
 }
 }
 AnimatedVisibility(visible=dockShown&&!editing,modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=10.dp),enter=if(reducedMotion)EnterTransition.None else slideInVertically{it}+fadeIn(),exit=if(reducedMotion)ExitTransition.None else slideOutVertically{it}+fadeOut()){
  FloatingDock(tab){tab=it;detail="";editing=false;lastInteraction=System.currentTimeMillis()}
 }
 if(!dockShown)Box(Modifier.align(Alignment.BottomCenter).padding(bottom=7.dp).size(42.dp,4.dp).clip(RoundedCornerShape(4.dp)).background(Muted.copy(alpha=.4f)).clickable{lastInteraction=System.currentTimeMillis()})
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
   "Reset this layout"->{save(DeskLayout.defaults());tab="Home";detail=""}
  }},{settings=false},focusMode,{focusMode=it})

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
@Composable private fun WidgetGrid(items:List<DeskWidget>,wide:Boolean,editing:Boolean,reducedMotion:Boolean,viewport:androidx.compose.ui.unit.Dp,fillViewport:Boolean,edit:()->Unit,save:(List<DeskWidget>)->Unit,open:(String)->Unit,configure:(DeskWidget)->Unit){
 val columns=if(wide)6 else 4
 val latest by rememberUpdatedState(items)
 var pitchX by remember{mutableFloatStateOf(100f)}
 var pitchY by remember{mutableFloatStateOf(100f)}
 val cells=DeskLayout.place(items,columns)
 val gapDp=LocalGridGap.current.dp
 val rows=cells.maxOfOrNull{it.row+it.height}?:0
 val unit=deskRowHeight(viewport.value,gapDp.value,rows,wide,fillViewport).dp
 if(items.isEmpty())DeskAction("Add widget",true){edit()}
 Layout(content={cells.forEach{cell->val widget=cell.widget
  var delta by remember(widget.id){mutableStateOf(Offset.Zero)}
  Box(Modifier.zIndex(if(delta!=Offset.Zero)1f else 0f).graphicsLayer{translationX=delta.x;translationY=delta.y}.pointerInput(widget.id,columns,cell.column,cell.row){detectDragGesturesAfterLongPress(onDragStart={edit();delta=Offset.Zero},onDrag={change,amount->change.consume();delta+=amount},onDragEnd={save(DeskLayout.move(latest,widget.id,cell.column+kotlin.math.round(delta.x/pitchX).toInt(),cell.row+kotlin.math.round(delta.y/pitchY).toInt(),columns));delta=Offset.Zero},onDragCancel={delta=Offset.Zero})}.border(if(editing)1.dp else 0.dp,Cobalt.copy(alpha=if(editing).65f else 0f),RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(widgetColor(widget.kind)).clickable(enabled=!editing&&widget.kind!="Clock"){open(destination(widget))}.padding(12.dp)){
   WidgetBody(widget,Modifier.fillMaxSize())
   if(editing){
    Row(Modifier.align(Alignment.TopEnd).background(Ink,RoundedCornerShape(8.dp))){
     Box(Modifier.size(28.dp).clickable{configure(widget)},contentAlignment=Alignment.Center){LabGlyph("Settings",Cobalt,17.dp)}
     Box(Modifier.size(28.dp).clickable{save(latest.filter{it.id!=widget.id})},contentAlignment=Alignment.Center){Text("×",color=Apricot,fontSize=20.sp)}
    }
    Box(Modifier.align(Alignment.BottomEnd).size(32.dp).background(Ink,RoundedCornerShape(8.dp)).pointerInput(widget.id,cell.width,cell.height){var amount=Offset.Zero;detectDragGestures(onDragStart={amount=Offset.Zero},onDrag={change,d->change.consume();amount+=d},onDragEnd={save(DeskLayout.resize(latest,widget.id,cell.width+kotlin.math.round(amount.x/pitchX).toInt(),cell.height+kotlin.math.round(amount.y/pitchY).toInt(),columns))})},contentAlignment=Alignment.Center){Text("↘",color=Cobalt,fontSize=23.sp)}
   }
  }
 }},modifier=Modifier.fillMaxWidth()){measurables,constraints->
  val gap=gapDp.roundToPx();val cellWidth=(constraints.maxWidth-gap*(columns-1))/columns;val rowHeight=unit.roundToPx()
  pitchX=(cellWidth+gap).toFloat();pitchY=(rowHeight+gap).toFloat()
  val placeables=measurables.mapIndexed{index,m->val c=cells[index];m.measure(Constraints.fixed(cellWidth*c.width+gap*(c.width-1),rowHeight*c.height+gap*(c.height-1)))}
  val rows=cells.maxOfOrNull{it.row+it.height}?:0
  layout(constraints.maxWidth,(rows*rowHeight+gap*(rows-1)).coerceAtLeast(0)){placeables.forEachIndexed{i,p->val c=cells[i];p.placeRelative(c.column*(cellWidth+gap),c.row*(rowHeight+gap))}}
 }
}

private fun destination(w:DeskWidget)=when(w.kind){"Clock"->"Home";"Monitors"->"Monitors";"Node"->"node:${w.target}";"App","Service"->"app:${w.target}";"Backups"->"Backups";"Events"->"events-history:${w.target.ifBlank{"All"}}";"Alerts"->if(w.target.isBlank()||w.target=="All")"Events"else"events-source:${w.target}";"Attention"->"Attention";"GitOps"->"Apps";"Downloads"->"app:qbittorrent";else->"Nodes"}
@Composable private fun WidgetBody(w:DeskWidget,modifier:Modifier){if(w.kind=="Clock"){ClockWidget(w.height,modifier,w.label);return};val fg=if(w.kind in listOf("GitOps","Attention","Monitors"))Color(0xFF29233D)else Paper;val secondary=if(fg!=Paper)fg.copy(alpha=.8f)else Muted;Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(5.dp)){
 val app=LiveRepository.services.find{it.name==w.target};val node=LiveRepository.nodes.find{it.name==w.target}
 Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){if(w.kind=="App"&&w.width>=2)AppIcon(app?.icon?:"",w.target);Text(w.label.ifBlank{if(w.kind in listOf("App","Node","Service"))w.target else when(w.kind){"Events"->"Recent activity";"Alerts"->"Firing alerts";else->w.kind}},color=secondary,fontSize=if(w.width<2)10.sp else 12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f));if(w.kind=="GitOps")LabGlyph("GitOps",fg,19.dp)}
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
 val value=if(missing)"—"else when(w.kind){"CPU"->"${LiveRepository.cpuPercent.fmt()}%";"Memory"->"${LiveRepository.memoryPercent.fmt()}%";"Nodes"->"${LiveRepository.nodes.count{it.ready}} / ${LiveRepository.nodes.size}";"Monitors"->"${LiveRepository.monitorsUp?:"—"} / ${LiveRepository.monitorsTotal?:"—"}";"GitOps"->"${LiveRepository.services.count{it.health=="Healthy"&&it.sync=="Synced"}} / ${LiveRepository.services.size}";"Downloads"->"${LiveRepository.download.fmt()} MiB/s";"Backups"->when(LiveRepository.backup){true->"Available";false->"Unavailable";null->"Unknown"};"Attention"->if(LiveRepository.lastSuccess==0L)"Unavailable" else if(LiveRepository.issues.isNotEmpty())"${LiveRepository.issues.size} issues"else if(!LiveRepository.state.startsWith("Live")||LiveRepository.signals.values.any{!it})"Unconfirmed"else"No action needed";"Events"->"${recentCount?.toString()?:"—"} records";"Alerts"->"${firing.size} firing";"App"->app?.health?:"Unavailable";"Node"->if(node==null)"Unavailable" else if(node.ready)"Ready" else "Not ready";else->"—"}
 Text(value,color=fg,fontWeight=FontWeight.SemiBold,fontSize=if(w.width<2)18.sp else if(value.length>10)22.sp else 32.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
 if(w.width>=2){when(w.kind){
  "CPU"->{Text("${LiveRepository.totalCores} cores",color=secondary,fontSize=11.sp);Spark(LiveRepository.history["cpu"]?:emptyList(),Cobalt)}
  "Memory"->{Text("${LiveRepository.usedMemory.fmt()} / ${LiveRepository.totalMemory.fmt()} GiB",color=secondary,fontSize=11.sp);Spark(LiveRepository.history["memory"]?:emptyList(),Lilac)}
  "App"->{ResourceMiniMap(app?.resources?:emptyList(),app?.pods?:0)}
  "Node"->{Text("CPU ${node?.cpuPercent.fmt()}%",color=secondary,fontSize=11.sp);Spark(LiveRepository.history["node:${w.target}"]?:emptyList(),Cobalt)}
  "Events"->{Text("Last 24 hours",color=secondary,fontSize=11.sp);Text("${firing.size} firing · ${firing.count{isInformational(it.kind)}} info",color=secondary,fontSize=10.sp)}
  "Alerts"->{Text("${firing.count{!isInformational(it.kind)}} actionable · ${firing.count{isInformational(it.kind)}} info",color=secondary,fontSize=11.sp);firing.firstOrNull()?.let{Text(it.title,color=fg,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}}
  "Attention"->{LiveRepository.issues.take(if(w.height>=2)3 else 1).forEach{Text(it.title,color=fg,fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis)};if(LiveRepository.issues.isEmpty())Text(if(LiveRepository.lastSuccess==0L)"Connecting…"else if(LiveRepository.active.any{isInformational(it.kind)&&it.state=="Firing"})"${LiveRepository.active.count{isInformational(it.kind)&&it.state=="Firing"}} informational"else"Healthy",color=fg,fontSize=12.sp)}
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
 var configuring by remember{mutableStateOf(initial!=null)};var sourceOpen by remember{mutableStateOf(false)};var search by remember{mutableStateOf("")}
 val wide=wideWindow();val columns=if(wide)6 else 4
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
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(DeskColors.raised).clickable(enabled=value>min){change(value-1)},contentAlignment=Alignment.Center){Text("−",color=if(value>min)Paper else Muted.copy(alpha=.4f),fontSize=20.sp)};Text("$value",color=Paper,fontSize=17.sp);Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(DeskColors.raised).clickable(enabled=value<max){change(value+1)},contentAlignment=Alignment.Center){Text("+",color=if(value<max)Paper else Muted.copy(alpha=.4f),fontSize=20.sp)}}
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
      DeskLayout.kinds.chunked(if(wide)4 else 2).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
       row.forEach{k->Column(Modifier.weight(1f).height(100.dp).clip(RoundedCornerShape(16.dp)).background(Card).border(1.dp,DeskColors.raised,RoundedCornerShape(16.dp)).clickable{kind=k;target="";fields=emptyList();height=if(k=="Clock")2 else 1;configuring=true}.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(widgetColor(k)) ,contentAlignment=Alignment.Center){LabGlyph(when(k){"Service","App"->"Apps";"Node"->"Nodes";"Alerts"->"Attention";else->k},if(k in listOf("GitOps","Monitors","Attention"))Ink else if(k=="Clock")Paper else Cobalt,20.dp)}
        Text(when(k){"Events"->"Recent activity";"Alerts"->"Firing alerts";else->k},color=Paper,fontSize=14.sp,fontWeight=FontWeight.Medium)
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
private fun widgetColor(kind:String)=when(kind){"Clock"->welcomeColor();"GitOps"->Color(0xFFBBB6E1);"Monitors","Attention"->Apricot;else->Card}
@Composable internal fun Spark(values:List<Float>,color:Color){if(values.size<2){Text("Collecting trend…",color=Muted,fontSize=10.sp);return};Canvas(Modifier.fillMaxWidth().height(30.dp)){val low=values.min();val range=(values.max()-low).coerceAtLeast(.1f);val path=Path();values.forEachIndexed{i,value->val x=i.toFloat()/(values.size-1)*size.width;val y=size.height-(value-low)/range*(size.height-4)-2;if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};drawPath(path,color,style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))}}

@Composable private fun Title(value:String){Text(value,color=Paper,fontSize=25.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(vertical=12.dp))}
@Composable private fun Info(label:String,value:String){Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)){Text(label,color=Muted,fontSize=12.sp,modifier=Modifier.weight(1f));Text(value,color=Paper,fontSize=12.sp,modifier=Modifier.weight(1.6f))}}
@Composable internal fun Usage(label:String,value:Float?,color:Color){Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=6.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(label,color=Muted,fontSize=12.sp);Text("${value.fmt()}%",color=Paper,fontSize=12.sp)};if(value!=null)LinearProgressIndicator(progress={(value/100).coerceIn(0f,1f)},color=color,trackColor=Color(0xFF3C4250),modifier=Modifier.fillMaxWidth().height(5.dp))}
@Composable private fun Nodes(open:(String)->Unit){
 val wide=wideWindow()

 Usage("Total CPU",LiveRepository.cpuPercent,Cobalt);Usage("Total memory",LiveRepository.memoryPercent,Lilac)
 LiveRepository.nodes.chunked(if(wide)2 else 1).forEach{row->Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){row.forEach{node->Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Card).clickable{open(node.name)}.padding(16.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(node.name,color=Paper,fontSize=19.sp);Text(if(node.ready)"Ready ↗"else"Not ready ↗",color=if(node.ready)Mint else Apricot,fontSize=12.sp)}
  Usage("CPU · ${node.cores} cores",if(node.available)node.cpuPercent else null,Cobalt)
  Usage("Memory · ${node.memoryUsedGiB.fmt()} / ${node.memoryTotalGiB.fmt()} GiB",if(node.available)node.memoryUsedGiB/node.memoryTotalGiB*100 else null,Lilac)
 }}}}
}
private val iconCache=android.util.LruCache<String,android.graphics.Bitmap>(40)
@Composable internal fun AppIcon(inputIcon:String,name:String){val asset=when(name.lowercase()){"argocd"->"https://raw.githubusercontent.com/selfhst/icons/main/png/argo-cd.png";"alloy"->"https://raw.githubusercontent.com/selfhst/icons/main/png/grafana-alloy.png";else->inputIcon};val context=LocalContext.current;var bitmap by remember(asset){mutableStateOf(iconCache.get(asset))};LaunchedEffect(asset){if(asset.isNotEmpty()&&bitmap==null){bitmap=withContext(Dispatchers.IO){try{require(asset.startsWith("https://raw.githubusercontent.com/")||asset.startsWith("https://cdn.jsdelivr.net/"));val file=java.io.File(context.cacheDir,"asset-${asset.hashCode()}.png");val bytes=if(file.exists())file.readBytes()else{val connection=URL(asset).openConnection().apply{connectTimeout=4000;readTimeout=4000};connection.getInputStream().use{it.readBytes().also{data->require(data.size<1048576);file.writeBytes(data)}}};BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=4})?.also{iconCache.put(asset,it)}}catch(_:Exception){null}}}};if(bitmap!=null)Image(bitmap!!.asImageBitmap(),contentDescription=null,modifier=Modifier.size(26.dp))else Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF394462)),contentAlignment=Alignment.Center){Text(name.take(1).uppercase(),color=Paper,fontSize=12.sp)}}
@Composable private fun Apps(open:(String)->Unit){
 var search by rememberSaveable{mutableStateOf("")}
 var filter by rememberSaveable{mutableStateOf("All")}
 Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
  androidx.compose.foundation.text.BasicTextField(value=search,onValueChange={search=it},singleLine=true,textStyle=androidx.compose.ui.text.TextStyle(color=Paper,fontSize=13.sp),modifier=Modifier.weight(1f).clip(RoundedCornerShape(13.dp)).background(Card).padding(horizontal=14.dp,vertical=12.dp),decorationBox={inner->Box{if(search.isEmpty())Text("Search apps",color=Muted,fontSize=13.sp);inner()}})
  Text("${LiveRepository.services.size} apps",color=Muted,fontSize=11.sp)
 }
 CompactTabs(listOf("All","Needs attention"),filter,{filter=it})
 val apps=LiveRepository.services.filter{it.name.contains(search,true)&&(filter=="All"||it.health!="Healthy"||it.sync!="Synced")}
 val columns=if(wideWindow())3 else 2
 if(apps.isEmpty())Text("No matching apps",color=Muted,modifier=Modifier.padding(16.dp))
 apps.chunked(columns).forEach{row->Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
  row.forEach{app->Column(Modifier.weight(1f).height(138.dp).clip(RoundedCornerShape(17.dp)).background(Card).clickable{open(app.name)}.padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){AppIcon(app.icon,app.name);Text(app.name,color=Paper,fontWeight=FontWeight.Medium,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){Box(Modifier.size(5.dp).background(if(app.health=="Healthy")Mint else Apricot,RoundedCornerShape(4.dp)));Text(app.health,color=if(app.health=="Healthy")Mint else Apricot,fontSize=11.sp);Text(app.sync,color=Muted,fontSize=10.sp,maxLines=1)}
   Text("${app.ready}/${app.pods} pods · ${app.namespace}",color=Muted,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
   ResourceMiniMap(app.resources,app.pods)
  }}
  repeat(columns-row.size){Spacer(Modifier.weight(1f))}
 }}
}
@Composable internal fun EventRow(event:LabEvent,click:()->Unit){Column(Modifier.fillMaxWidth().padding(top=10.dp).clip(RoundedCornerShape(18.dp)).background(Card).clickable(onClick=click).padding(16.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(event.source,color=Lilac,fontSize=11.sp);Text(stamp(event.time),color=Muted,fontSize=10.sp)};Text(event.title,color=Paper,fontSize=15.sp,modifier=Modifier.padding(vertical=8.dp));Text(event.kind+" ↗",color=Apricot,fontSize=11.sp)}}
