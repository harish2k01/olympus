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

private val Ink=Color(0xFF14171E)
private val Card=Color(0xFF242832)
private val Cobalt=Color(0xFF879BFF)
private val Apricot=Color(0xFFEDB48F)
private val Lilac=Color(0xFFCDC5FF)
private val Mint=Color(0xFFB4DFBE)
private val Muted=Color(0xFFBFC4D1)
private val Paper=Color(0xFFF5F3EF)
private fun Float?.fmt()=if(this==null)"—" else String.format(java.util.Locale.US,"%.1f",this)
private fun stamp(value:String)=try{DateTimeFormatter.ofPattern("dd MMM · HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.parse(value))}catch(_:Exception){"Time unavailable"}

@Composable private fun ClockWidget(widgetSize:Int,modifier:Modifier){
 var now by remember{mutableStateOf(java.time.ZonedDateTime.now())}
 LaunchedEffect(Unit){while(isActive){now=java.time.ZonedDateTime.now();delay(30000)}}
 Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.SpaceBetween){
  if(widgetSize>0)Text("OLYMPUS / ${now.dayOfWeek.name}",color=Color.White,fontSize=9.sp,letterSpacing=1.sp,maxLines=1)
  Column{
   Text(now.format(DateTimeFormatter.ofPattern("HH:mm")),color=Color.White,fontSize=if(widgetSize==0)28.sp else 45.sp,fontWeight=FontWeight.Light)
   if(widgetSize>0)Text(now.format(DateTimeFormatter.ofPattern("d MMMM yyyy")),color=Color.White,fontSize=11.sp)
  }
  if(widgetSize==2){
   ClusterMascot()
   val greeting=when(now.hour){in 5..11->"A good morning";in 12..16->"A good afternoon";in 17..22->"A good evening";else->"A quiet night"}
   Text("$greeting\nfor your lab.",color=Color.White,fontSize=18.sp,lineHeight=23.sp,fontWeight=FontWeight.SemiBold)
   Text("${LiveRepository.monitorsUp?:"—"} HTTP checks up ↗",color=Color.White,fontSize=11.sp)
  }
 }
}

@Composable internal fun HomeApp(){
 val context=LocalContext.current
 val wide=LocalConfiguration.current.screenWidthDp>650
 val prefs=remember{context.getSharedPreferences("home-v3",0)}
 val layoutKey=if(wide)"landscape" else "portrait"
 var widgets by remember(layoutKey){mutableStateOf(try{DeskLayout.decode(prefs.getString(layoutKey,"")!!)}catch(_:Exception){DeskLayout.defaults()})}
 fun save(items:List<DeskWidget>){widgets=items;prefs.edit().putString(layoutKey,DeskLayout.encode(items)).apply()}
 var tab by rememberSaveable{mutableStateOf("Home")}
 var detail by rememberSaveable{mutableStateOf("")}
 var editing by rememberSaveable{mutableStateOf(false)}
 var picker by remember{mutableStateOf(false)}
 var menu by remember{mutableStateOf(false)}
 var settings by remember{mutableStateOf(false)}
 var importLayout by remember{mutableStateOf(false)}
 var message by remember{mutableStateOf("")}
 var dim by remember{mutableFloatStateOf(prefs.getFloat("brightness",.7f))}
 var reducedMotion by remember{mutableStateOf(prefs.getBoolean("reducedMotion",false))}
 var dimMode by remember{mutableStateOf(prefs.getBoolean("deskMode",false))}
 var dockShown by remember{mutableStateOf(true)}
 var lastInteraction by remember{mutableLongStateOf(System.currentTimeMillis())}
 var focusMode by remember{mutableStateOf(prefs.getBoolean("focusMode",false))}
 LaunchedEffect(lastInteraction,editing,menu,picker,settings){dockShown=true;if(!editing&&!menu&&!picker&&!settings){delay(4000);dockShown=false}}
 var shift by remember{mutableIntStateOf(0)}
 LaunchedEffect(dimMode){while(dimMode){delay(60000);shift=(shift+1)%3}}
 DisposableEffect(dimMode,dim){val window=(context as? Activity)?.window;val old=window?.attributes?.screenBrightness;window?.attributes=window?.attributes?.apply{screenBrightness=if(dimMode)dim else -1f};onDispose{if(old!=null)window?.attributes=window?.attributes?.apply{screenBrightness=old}}}
 fun pin(kind:String,target:String){if(widgets.none{it.kind==kind&&it.target==target})save(widgets+DeskWidget(UUID.randomUUID().toString(),kind,target));message="Pinned to Home"}
 BackHandler(detail.isNotEmpty()||editing){if(detail.isNotEmpty())detail="" else editing=false}
 CompositionLocalProvider(LocalReducedMotion provides reducedMotion){
 Surface(color=Ink,modifier=Modifier.fillMaxSize()){
 Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).pointerInput(Unit){awaitPointerEventScope{while(true){val event=awaitPointerEvent(PointerEventPass.Initial);if(event.changes.any{it.pressed})lastInteraction=System.currentTimeMillis()}}}){
 Column(Modifier.fillMaxSize().padding(horizontal=16.dp).offset(x=if(dimMode)shift.dp else 0.dp)){
  AnimatedVisibility(visible=!focusMode||dockShown||editing){
  Row(Modifier.fillMaxWidth().height(48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
   if(detail.isNotEmpty())TextButton(onClick={detail=""},modifier=Modifier.size(40.dp),contentPadding=PaddingValues(0.dp)){Text("‹",color=Paper,fontSize=34.sp)}else Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF4A63EA)),contentAlignment=Alignment.Center){Text("O",fontWeight=FontWeight.Bold,color=Paper)}
   Text(if(detail.isEmpty())if(tab=="Home")"olympus"else tab else if(detail.startsWith("event:"))"Event" else detail.substringAfter(":"),fontSize=20.sp,fontWeight=FontWeight.SemiBold,color=Paper,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.widthIn(max=if(wide)350.dp else 145.dp))
   if(detail.isEmpty())LiveStatus(reducedMotion)
   Spacer(Modifier.weight(1f))
   if(detail.startsWith("node:")||detail.startsWith("app:"))TextButton(onClick={pin(if(detail.startsWith("node:"))"Node"else"App",detail.substringAfter(":"))}){Text("Pin",color=Cobalt)}
   if(editing){TextButton(onClick={picker=true}){Text("+ Widget",color=Cobalt)};TextButton(onClick={editing=false}){Text("Done",color=Mint)}}
   else Box{TextButton(onClick={menu=true},contentPadding=PaddingValues(4.dp)){Text("•••",color=Muted)};DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
    DropdownMenuItem(text={Text(if(focusMode)"Leave focus mode"else"Focus mode")},onClick={menu=false;focusMode=!focusMode;prefs.edit().putBoolean("focusMode",focusMode).apply()})
    DropdownMenuItem(text={Text("Edit Home")},onClick={menu=false;tab="Home";detail="";editing=true})
    DropdownMenuItem(text={Text("Add widget")},onClick={menu=false;tab="Home";detail="";picker=true})
    DropdownMenuItem(text={Text("Export layout")},onClick={menu=false;(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Olympus layout",DeskLayout.encode(widgets)));message="Layout copied"})
    DropdownMenuItem(text={Text("Import layout")},onClick={menu=false;importLayout=true})
    DropdownMenuItem(text={Text("Desk settings")},onClick={menu=false;settings=true})
   }}
  }
  }
  if(message.isNotEmpty()){Text(message,color=Mint,fontSize=12.sp);LaunchedEffect(message){delay(2200);message=""}}
  AnimatedContent(targetState=detail.ifEmpty{tab},modifier=Modifier.weight(1f),transitionSpec={if(reducedMotion)EnterTransition.None togetherWith ExitTransition.None else fadeIn(tween(180)) togetherWith fadeOut(tween(120))},label="page"){page->
   Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=78.dp)){
    when{
     page.startsWith("events-source:")->NotificationInbox(page.substringAfter(":")){detail="event:${it.id}"}
     page.startsWith("node:")->NodeDetailScreen(page.substringAfter(":"))
     page.startsWith("app:")->ApplicationScreen(page.substringAfter(":")){detail=it}
     page.startsWith("event:")->LiveRepository.events.find{it.id==page.substringAfter(":")}?.let{NotificationDetail(it)}?:Text("Event no longer available",color=Muted)
     page=="Attention"->{Title("Needs attention");if(LiveRepository.issues.isEmpty())Text("No current issues",color=Mint);LiveRepository.issues.forEach{item->Column(Modifier.fillMaxWidth().padding(bottom=10.dp).clip(RoundedCornerShape(16.dp)).background(Card).clickable{detail=item.target}.padding(16.dp)){Text(item.title,color=Paper,fontSize=14.sp);Text(item.severity,color=Apricot,fontSize=11.sp)}}}
     page=="Backups"-> {Title("Backups");Info("Destination",when(LiveRepository.backup){true->"Available";false->"Unavailable";null->"Unknown"});Info("Volumes with backup records",if(LiveRepository.backupVolumes==null)"Unavailable"else"${LiveRepository.backupRecorded} / ${LiveRepository.backupVolumes}");Info("Last recorded backup",LiveRepository.backupLast.ifEmpty{"None recorded"})}
     page=="Nodes"->Nodes{detail="node:$it"}
     page=="Events"->NotificationInbox{detail="event:${it.id}"}
     page=="Apps"->Apps{detail="app:$it"}
     else->WidgetGrid(widgets,wide,editing,reducedMotion,{editing=true},::save,{detail=it})
    }
   }
  }
 }
 AnimatedVisibility(visible=dockShown,modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=10.dp),enter=if(reducedMotion)EnterTransition.None else slideInVertically{it}+fadeIn(),exit=if(reducedMotion)ExitTransition.None else slideOutVertically{it}+fadeOut()){
  FloatingDock(tab){tab=it;detail="";editing=false;lastInteraction=System.currentTimeMillis()}
 }
 if(!dockShown)Box(Modifier.align(Alignment.BottomCenter).padding(bottom=7.dp).size(42.dp,4.dp).clip(RoundedCornerShape(4.dp)).background(Muted.copy(alpha=.4f)).clickable{lastInteraction=System.currentTimeMillis()})
 }
 }
 }
 if(picker)WidgetPicker(onDismiss={picker=false}){kind,target->save(widgets+DeskWidget(UUID.randomUUID().toString(),kind,target));picker=false}
 if(importLayout){var text by remember{mutableStateOf("")};var error by remember{mutableStateOf(false)};AlertDialog(onDismissRequest={importLayout=false},title={Text("Import layout")},text={Column{OutlinedTextField(value=text,onValueChange={text=it},label={Text("Layout JSON")},isError=error);if(error)Text("Invalid layout",color=Apricot)}},confirmButton={TextButton(onClick={try{save(DeskLayout.decode(text));importLayout=false}catch(_:Exception){error=true}}){Text("Import")}},dismissButton={TextButton(onClick={importLayout=false}){Text("Cancel")}})}
 if(settings)AlertDialog(onDismissRequest={settings=false},title={Text("Desk settings")},text={Column{Row(verticalAlignment=Alignment.CenterVertically){Text("Dim desk mode",Modifier.weight(1f));Switch(checked=dimMode,onCheckedChange={dimMode=it;prefs.edit().putBoolean("deskMode",it).apply()})};Slider(value=dim,onValueChange={dim=it;prefs.edit().putFloat("brightness",it).apply()},valueRange=.05f..1f);Row(verticalAlignment=Alignment.CenterVertically){Text("Reduce motion",Modifier.weight(1f));Switch(checked=reducedMotion,onCheckedChange={reducedMotion=it;prefs.edit().putBoolean("reducedMotion",it).apply()})}}},confirmButton={TextButton(onClick={settings=false}){Text("Done")}})
}

@Composable private fun LiveStatus(reduced:Boolean){
 val live=LiveRepository.state.startsWith("Live")
 val transition=rememberInfiniteTransition(label="connection")
 val pulse by transition.animateFloat(.45f,1f,infiniteRepeatable(tween(1200),RepeatMode.Reverse),label="pulse")
 Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
  Box(Modifier.size(6.dp).alpha(if(live&&!reduced)pulse else 1f).background(if(live)Mint else Apricot,RoundedCornerShape(6.dp)))
  Text(when{LiveRepository.state.contains("Partial")->"Partial";live->"Live";LiveRepository.state=="Stale"->"Stale";else->"Offline"},color=if(live)Mint else Apricot,fontSize=11.sp)
 }
}
@Composable private fun WidgetGrid(items:List<DeskWidget>,wide:Boolean,editing:Boolean,reducedMotion:Boolean,edit:()->Unit,save:(List<DeskWidget>)->Unit,open:(String)->Unit){
 val columns=if(wide)3 else 2
 val bounds=remember{mutableMapOf<String,Rect>()}
 var dragging by remember{mutableStateOf<String?>(null)}
 var target by remember{mutableStateOf<String?>(null)}
 var location by remember{mutableStateOf(Offset.Zero)}
 val latest by rememberUpdatedState(items)
 if(items.isEmpty())Text("Add your first widget from •••",color=Muted,modifier=Modifier.padding(24.dp))
 val cells=DeskLayout.place(items,columns)
 val unit=if(wide)61.dp else 66.dp
 Layout(content={cells.forEach{cell->val widget=cell.widget
   Column(Modifier.then(if(reducedMotion)Modifier else Modifier.animateContentSize()).onGloballyPositioned{bounds[widget.id]=it.boundsInRoot()}.pointerInput(widget.id){detectDragGesturesAfterLongPress(onDragStart={start->edit();dragging=widget.id;location=(bounds[widget.id]?.topLeft?:Offset.Zero)+start},onDrag={change,amount->change.consume();location+=amount;target=bounds.entries.firstOrNull{entry->latest.any{it.id==entry.key}&&entry.value.contains(location)}?.key},onDragEnd={val from=latest.indexOfFirst{it.id==dragging};val to=latest.indexOfFirst{it.id==target};if(from>=0&&to>=0&&from!=to){val copy=latest.toMutableList();copy.add(to,copy.removeAt(from));save(copy)};dragging=null;target=null},onDragCancel={dragging=null;target=null})}.border(if(editing||target==widget.id)1.dp else 0.dp,if(target==widget.id)Cobalt else if(editing)Muted else Color.Transparent,RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(if(dragging==widget.id)Color(0xFF313A53)else if(editing)Card else when(widget.kind){"Clock"->Color(0xFF4A63EA);"GitOps"->Color(0xFFBBB6E1);"Monitors","Attention"->Apricot;else->Card}).clickable(enabled=!editing){open(destination(widget))}.padding(if(widget.size==0)10.dp else 14.dp)){
    if(editing&&widget.size==0)CompactWidgetEditor(widget,{size->save(latest.map{if(it.id==widget.id)it.copy(size=size)else it})},{save(latest.filter{it.id!=widget.id})})else{
    if(editing){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("⠿",color=Muted);Spacer(Modifier.weight(1f));TextButton(onClick={save(latest.filter{it.id!=widget.id})},contentPadding=PaddingValues(0.dp),modifier=Modifier.size(32.dp)){Text("×",color=Apricot,fontSize=20.sp)}}}
    if(editing){Text(widget.target.ifBlank{widget.kind},color=Paper,fontSize=12.sp,maxLines=1);Spacer(Modifier.weight(1f))}else WidgetBody(widget,Modifier.weight(1f))
    if(editing){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){listOf("S","M","L").forEachIndexed{size,label->TextButton(onClick={save(latest.map{if(it.id==widget.id)it.copy(size=size)else it})},modifier=Modifier.size(32.dp),contentPadding=PaddingValues(0.dp)){Text(label,color=if(widget.size==size)Cobalt else Muted,fontSize=11.sp)}};Spacer(Modifier.weight(1f));Text("◢",color=Cobalt,modifier=Modifier.padding(4.dp).pointerInput(widget.id,widget.size){var delta=Offset.Zero;detectDragGestures(onDragStart={delta=Offset.Zero},onDrag={change,amount->change.consume();delta+=amount},onDragEnd={val size=(widget.size+if(delta.x+delta.y>30)1 else if(delta.x+delta.y< -30)-1 else 0).coerceIn(0,2);save(latest.map{if(it.id==widget.id)it.copy(size=size)else it})})})}}
    }
   }
 }},modifier=Modifier.fillMaxWidth()){measurables,constraints->
  val gap=10.dp.roundToPx();val cellWidth=(constraints.maxWidth-gap*(columns-1))/columns;val rowHeight=unit.roundToPx()
  val placeables=measurables.mapIndexed{index,measurable->val cell=cells[index];measurable.measure(Constraints.fixed(cellWidth*cell.width+gap*(cell.width-1),rowHeight*cell.height+gap*(cell.height-1)))}
  val rows=cells.maxOfOrNull{it.row+it.height}?:0
  layout(constraints.maxWidth,(rows*rowHeight+gap*(rows-1)).coerceAtLeast(0)){placeables.forEachIndexed{index,placeable->val cell=cells[index];placeable.placeRelative(cell.column*(cellWidth+gap),cell.row*(rowHeight+gap))}}
 }
}
private fun destination(w:DeskWidget)=when(w.kind){"Clock"->"app:uptime-kuma";"Node"->"node:${w.target}";"App"->"app:${w.target}";"Backups"->"Backups";"Events"->if(w.target.isBlank()||w.target=="All")"Events"else"events-source:${w.target}";"Attention"->"Attention";"GitOps"->"Apps";"Downloads"->"app:qbittorrent";else->"Nodes"}
@Composable private fun WidgetBody(w:DeskWidget,modifier:Modifier){if(w.kind=="Clock"){ClockWidget(w.size,modifier);return};val fg=if(w.kind in listOf("GitOps","Attention","Monitors"))Color(0xFF29233D)else Paper;val secondary=if(fg!=Paper)fg.copy(alpha=.8f)else Muted;Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(if(w.size==0)4.dp else 8.dp)){
 val app=LiveRepository.services.find{it.name==w.target};val node=LiveRepository.nodes.find{it.name==w.target}
 Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){if(w.kind=="App"&&w.size>0)AppIcon(app?.icon?:"",w.target);Text(if(w.kind=="App"||w.kind=="Node")w.target else w.kind,color=secondary,fontSize=if(w.size==0)10.sp else 12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
 val missing=when(w.kind){"CPU"->LiveRepository.cpuPercent==null;"Memory"->LiveRepository.memoryPercent==null;"Downloads"->LiveRepository.download==null;"Monitors"->LiveRepository.monitorsTotal==null;"Backups"->LiveRepository.backup==null;"App"->app==null;"Node"->node==null;else->LiveRepository.lastSuccess==0L}
 if(w.size>0&&(missing||!LiveRepository.state.startsWith("Live")))Text(if(missing)"Unavailable"else"Last known data",color=if(fg==Paper)Apricot else fg,fontSize=9.sp)
 val value=when(w.kind){"CPU"->"${LiveRepository.cpuPercent.fmt()}%";"Memory"->"${LiveRepository.memoryPercent.fmt()}%";"Nodes"->"${LiveRepository.nodes.count{it.ready}} / ${LiveRepository.nodes.size}";"Monitors"->"${LiveRepository.monitorsUp?:"—"} / ${LiveRepository.monitorsTotal?:"—"}";"GitOps"->"${LiveRepository.services.count{it.health=="Healthy"&&it.sync=="Synced"}} / ${LiveRepository.services.size}";"Downloads"->"${LiveRepository.download.fmt()} MiB/s";"Backups"->when(LiveRepository.backup){true->"Available";false->"Unavailable";null->"Unknown"};"Attention"->if(LiveRepository.lastSuccess==0L)"Unavailable" else "${LiveRepository.issues.size} issues";"Events"->"${LiveRepository.events.size} events";"App"->app?.health?:"Unavailable";"Node"->if(node==null)"Unavailable" else if(node.ready)"Ready" else "Not ready";else->"—"}
 Text(value,color=fg,fontWeight=FontWeight.SemiBold,fontSize=if(w.size==0)18.sp else 25.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
 if(w.size>0){when(w.kind){
  "CPU"->{Text("${LiveRepository.totalCores} cores",color=secondary,fontSize=11.sp);Spark(LiveRepository.history["cpu"]?:emptyList(),Cobalt)}
  "Memory"->{Text("${LiveRepository.usedMemory.fmt()} / ${LiveRepository.totalMemory.fmt()} GiB",color=secondary,fontSize=11.sp);Spark(LiveRepository.history["memory"]?:emptyList(),Lilac)}
  "App"->{ResourceMiniMap(app?.resources?:emptyList(),app?.pods?:0)}
  "Node"->{Text("CPU ${node?.cpuPercent.fmt()}%",color=secondary,fontSize=11.sp);Spark(LiveRepository.history["node:${w.target}"]?:emptyList(),Cobalt)}
  "Events"->LiveRepository.events.filter{w.target.isBlank()||w.target=="All"||it.source==w.target}.take(if(w.size==2)3 else 1).forEach{Text(it.title,color=fg,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis);Text(stamp(it.time),color=secondary,fontSize=10.sp)}
  "Attention"->{LiveRepository.issues.take(if(w.size==2)3 else 1).forEach{Text(it.title,color=fg,fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis)};if(LiveRepository.issues.isEmpty())Text(if(LiveRepository.lastSuccess==0L)"Connecting…"else"All clear",color=fg,fontSize=12.sp)}
  "Backups"->Text("Longhorn destination",color=secondary,fontSize=11.sp)
  "GitOps"->Text("Healthy & synced",color=secondary,fontSize=11.sp)
  "Nodes"->{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){LiveRepository.nodes.forEach{node->Box(Modifier.weight(1f).height(4.dp).background(if(node.ready)Mint else Apricot,RoundedCornerShape(3.dp)))}};Text("Nodes ready",color=secondary,fontSize=11.sp)}
  "Monitors"->Text("Checks up",color=secondary,fontSize=11.sp)
 } }
}}
@Composable private fun WidgetPicker(onDismiss:()->Unit,add:(String,String)->Unit){var kind by remember{mutableStateOf("")};AlertDialog(onDismissRequest=onDismiss,title={Text(if(kind.isEmpty())"Add widget" else "Choose ${kind.lowercase()}")},text={Column(Modifier.heightIn(max=380.dp).verticalScroll(rememberScrollState())){if(kind.isEmpty())DeskLayout.kinds.forEach{k->TextButton(onClick={if(k=="App"||k=="Node"||k=="Events")kind=k else add(k,"")},modifier=Modifier.fillMaxWidth()){Text(k,color=Paper)}}else (if(kind=="App")LiveRepository.services.map{it.name}else if(kind=="Events")listOf("All")+LiveRepository.events.map{it.source}.distinct()else LiveRepository.nodes.map{it.name}).forEach{target->TextButton(onClick={add(kind,target)},modifier=Modifier.fillMaxWidth()){Text(target,color=Paper)}}}},confirmButton={TextButton(onClick=onDismiss){Text("Close")}})}
@Composable internal fun Spark(values:List<Float>,color:Color){if(values.size<2){Text("Collecting trend…",color=Muted,fontSize=10.sp);return};Canvas(Modifier.fillMaxWidth().height(30.dp)){val low=values.min();val range=(values.max()-low).coerceAtLeast(.1f);val path=Path();values.forEachIndexed{i,value->val x=i.toFloat()/(values.size-1)*size.width;val y=size.height-(value-low)/range*(size.height-4)-2;if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};drawPath(path,color,style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))}}

@Composable private fun Title(value:String){Text(value,color=Paper,fontSize=25.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(vertical=12.dp))}
@Composable private fun Info(label:String,value:String){Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)){Text(label,color=Muted,fontSize=12.sp,modifier=Modifier.weight(1f));Text(value,color=Paper,fontSize=12.sp,modifier=Modifier.weight(1.6f))}}
@Composable internal fun Usage(label:String,value:Float?,color:Color){Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=6.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(label,color=Muted,fontSize=12.sp);Text("${value.fmt()}%",color=Paper,fontSize=12.sp)};if(value!=null)LinearProgressIndicator(progress={(value/100).coerceIn(0f,1f)},color=color,trackColor=Color(0xFF3C4250),modifier=Modifier.fillMaxWidth().height(5.dp))}
@Composable private fun Nodes(open:(String)->Unit){
 val wide=LocalConfiguration.current.screenWidthDp>650

 Usage("Total CPU",LiveRepository.cpuPercent,Cobalt);Usage("Total memory",LiveRepository.memoryPercent,Lilac)
 LiveRepository.nodes.chunked(if(wide)2 else 1).forEach{row->Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){row.forEach{node->Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Card).clickable{open(node.name)}.padding(16.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(node.name,color=Paper,fontSize=19.sp);Text(if(node.ready)"Ready ↗"else"Not ready ↗",color=if(node.ready)Mint else Apricot,fontSize=12.sp)}
  Usage("CPU · ${node.cores} cores",if(node.available)node.cpuPercent else null,Cobalt)
  Usage("Memory · ${node.memoryUsedGiB.fmt()} / ${node.memoryTotalGiB.fmt()} GiB",if(node.available)node.memoryUsedGiB/node.memoryTotalGiB*100 else null,Lilac)
 }}}}
}
@Composable private fun CompactWidgetEditor(widget:DeskWidget,resize:(Int)->Unit,remove:()->Unit){
 var menu by remember{mutableStateOf(false)}
 Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically){Text(widget.target.ifBlank{widget.kind},color=Paper,fontSize=11.sp,maxLines=1,modifier=Modifier.weight(1f));Box{TextButton(onClick={menu=true},modifier=Modifier.size(32.dp),contentPadding=PaddingValues(0.dp)){Text("•••",color=Cobalt)};DropdownMenu(expanded=menu,onDismissRequest={menu=false}){listOf("Small","Medium","Large").forEachIndexed{size,label->DropdownMenuItem(text={Text(label)},onClick={resize(size);menu=false})};DropdownMenuItem(text={Text("Remove")},onClick={remove();menu=false})}}}
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
 val columns=if(LocalConfiguration.current.screenWidthDp>650)3 else 2
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
