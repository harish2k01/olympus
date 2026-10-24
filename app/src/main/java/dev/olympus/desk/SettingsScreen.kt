package dev.olympus.desk

import android.app.Activity
import android.content.SharedPreferences
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.olympus.desk.data.*

internal val LocalShowTests=staticCompositionLocalOf{false}
internal val LocalGridGap=staticCompositionLocalOf{10}

@Composable internal fun SettingsScreen(prefs:SharedPreferences,dimMode:Boolean,dim:Float,reduced:Boolean,awake:Boolean,tests:Boolean,dock:Int,gap:Int,setDimMode:(Boolean)->Unit,setDim:(Float)->Unit,setReduced:(Boolean)->Unit,setAwake:(Boolean)->Unit,setTests:(Boolean)->Unit,setDock:(Int)->Unit,setGap:(Int)->Unit,monitors:()->Unit,home:(String)->Unit,close:()->Unit){
 var section by remember{mutableStateOf("Display")}
 val context=LocalContext.current
 val pair=remember{Pairing.read(context)}
 var url by remember{mutableStateOf(pair?.first?:"")};var token by remember{mutableStateOf("")};var error by remember{mutableStateOf("")}
 fun bool(key:String,value:Boolean,change:(Boolean)->Unit){prefs.edit().putBoolean(key,value).apply();change(value)}
 Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)){
  ImmersiveDialogWindow()
  Surface(color=Color(0xFF14171E),modifier=Modifier.fillMaxSize()){
   Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).padding(horizontal=20.dp)){
    Row(Modifier.fillMaxWidth().height(52.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=close){LabGlyph("Back",Color(0xFFF5F3EF),24.dp)};Text("Settings",fontSize=21.sp);Spacer(Modifier.weight(1f));Text("v${BuildConfig.VERSION_NAME}",color=Color(0xFFBFC4D1),fontSize=11.sp)}
    CompactTabs(listOf("Display","Home","Events","Connection"),section,{section=it})
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
     when(section){
      "Connection"->{
     SettingsHeading("Connection")
     Text(LiveRepository.state,color=if(LiveRepository.state=="Live")Color(0xFFB4DFBE)else Color(0xFFEDB48F),fontSize=13.sp)
     OutlinedTextField(value=url,onValueChange={url=it},label={Text("Backend HTTPS address")},singleLine=true,modifier=Modifier.fillMaxWidth())
     OutlinedTextField(value=token,onValueChange={token=it},label={Text("Pairing token · leave blank to keep")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
     if(error.isNotEmpty())Text(error,color=Color(0xFFEDB48F))
     TextButton(onClick={try{val uri=java.net.URI(url.trim());require(uri.scheme=="https"&&!uri.host.isNullOrBlank()&&uri.userInfo==null&&uri.query==null&&uri.fragment==null);val secret=token.ifBlank{pair?.second?:""};require(secret.length>=32);Pairing.save(context,url.trim(),secret);(context as? Activity)?.recreate();close()}catch(_:Exception){error="Enter a valid HTTPS address and pairing token"}}){Text("Save & reconnect")}
      }
      "Display"->{
     SettingsHeading("Display & navigation")
     SettingSwitch("Keep screen awake",awake){bool("keepAwake",it,setAwake)}
     SettingSwitch("Dim desk mode",dimMode){bool("deskMode",it,setDimMode)}
     if(dimMode){Text("App brightness · ${(dim*100).toInt()}%",fontSize=12.sp);Slider(value=dim,onValueChange={prefs.edit().putFloat("brightness",it).apply();setDim(it)},valueRange=.05f..1f)}
     SettingSwitch("Reduce motion",reduced){bool("reducedMotion",it,setReduced)}
     Text("Dock hide delay · ${dock}s",fontSize=12.sp);Slider(value=dock.toFloat(),onValueChange={val n=it.toInt();prefs.edit().putInt("dockSeconds",n).apply();setDock(n)},valueRange=2f..15f,steps=12)
      }
      "Home"->{
     Text("Widget spacing · ${gap}dp",fontSize=12.sp);Slider(value=gap.toFloat(),onValueChange={val n=it.toInt();prefs.edit().putInt("gridGap",n).apply();setGap(n)},valueRange=6f..16f,steps=9)
       listOf("Edit Home","Add widget","Export layout","Import layout","Reset this layout").forEach{action->DeskAction(action){home(action)}}
      }
      "Events"->{
     SettingsHeading("Notifications & integrations")
     SettingSwitch("Show test notifications",tests){bool("showTests",it,setTests)}
     DeskAction("Monitors · status & response time →"){monitors()}
     val received=LiveRepository.events.filter{it.source!="Kubernetes"&&it.source!="Prometheus"&&it.source!="Test"}.groupBy{it.source}
     if(received.isEmpty())Text("No webhook deliveries in the recent feed",color=Color(0xFFBFC4D1),fontSize=12.sp)
     received.forEach{(source,events)->Row(Modifier.fillMaxWidth().padding(vertical=6.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(source,fontSize=12.sp);Text(localTime(events.maxOf{it.received.ifBlank{it.time}}),fontSize=11.sp,color=Color(0xFFBFC4D1))}}
     SettingsHeading("Storage & privacy")
     Text("Event history · 30 days / 2,000 records\nStored on the backend's 1 GiB volume. Active incidents remain until recovery is confirmed.\nKubernetes warnings and alerts are retained once observed.\nSystem notifications while the app is closed are not enabled.",fontSize=12.sp,lineHeight=20.sp,color=Color(0xFFBFC4D1))
     TextButton(onClick={java.io.File(context.filesDir,"last-frame.json").delete();context.cacheDir.listFiles()?.filter{it.name.startsWith("asset-")}?.forEach{it.delete()}}){Text("Clear local cache")}
      }
     }
     Spacer(Modifier.height(20.dp))
    }
   }
  }
 }
}
@Composable private fun SettingsHeading(text:String){Text(text,color=Color(0xFFCDC5FF),fontSize=14.sp,modifier=Modifier.padding(top=12.dp,bottom=4.dp))}
@Composable private fun SettingSwitch(label:String,value:Boolean,change:(Boolean)->Unit){Row(Modifier.fillMaxWidth().heightIn(min=46.dp),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),fontSize=13.sp);Switch(checked=value,onCheckedChange=change)}}

@Composable internal fun MonitorScreen(){
 var search by remember{mutableStateOf("")};var status by remember{mutableStateOf("All")}
 if(wideWindow())Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Box(Modifier.weight(1f)){DeskSearch(search,{search=it},"Search monitors")};Box(Modifier.width(350.dp)){CompactTabs(listOf("All","Up","Down","Pending","Maintenance"),status,{status=it})}}
 else {DeskSearch(search,{search=it},"Search monitors");CompactTabs(listOf("All","Up","Down","Pending","Maintenance"),status,{status=it})}
 val rows=LiveRepository.monitors.filter{it.name.contains(search,true)&&(status=="All"||it.status==status)}
 if(LiveRepository.monitors.isEmpty())Text("Monitor readings unavailable",modifier=Modifier.padding(16.dp),color=DeskColors.muted)
 val columns=if(wideWindow())2 else 1
 rows.chunked(columns).forEach{group->Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){
  group.forEach{m->Row(Modifier.weight(1f).background(DeskColors.surface,RoundedCornerShape(14.dp)).padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
   Box(Modifier.size(7.dp).background(if(m.status=="Up")DeskColors.green else DeskColors.apricot,RoundedCornerShape(4.dp)))
   Column(Modifier.weight(1f)){Text(m.name,fontSize=13.sp,maxLines=1);Text("${m.type} · ${m.status}",color=DeskColors.muted,fontSize=11.sp)}
   Text(m.responseMs?.let{"${it.toInt()} ms"}?:"—",color=DeskColors.lilac,fontSize=13.sp)
  }}
  if(group.size<columns)Spacer(Modifier.weight(1f))
 }}

}

@Composable internal fun ImmersiveDialogWindow(){
 val view=LocalView.current
 DisposableEffect(view){val window=(view.parent as? DialogWindowProvider)?.window;if(window!=null){WindowCompat.setDecorFitsSystemWindows(window,false);window.attributes=window.attributes.apply{layoutInDisplayCutoutMode=android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES};WindowCompat.getInsetsController(window,view).apply{systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;hide(WindowInsetsCompat.Type.systemBars())}};onDispose{}}
}
