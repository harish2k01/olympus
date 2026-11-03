package dev.olympus.desk

import android.app.Activity
import android.content.SharedPreferences
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
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
internal val LocalGridColumns=staticCompositionLocalOf{8}
internal val LocalGridGap=staticCompositionLocalOf{10}

@Composable internal fun SettingsScreen(prefs:SharedPreferences,dimMode:Boolean,dim:Float,reduced:Boolean,awake:Boolean,tests:Boolean,dock:Int,gap:Int,setDimMode:(Boolean)->Unit,setDim:(Float)->Unit,setReduced:(Boolean)->Unit,setAwake:(Boolean)->Unit,setTests:(Boolean)->Unit,setDock:(Int)->Unit,setGap:(Int)->Unit,monitors:()->Unit,home:(String)->Unit,close:()->Unit,focus:Boolean,setFocus:(Boolean)->Unit){
 var section by rememberSaveable{mutableStateOf("Display")}
 var resetPending by remember{mutableStateOf(false)}
 var cacheCleared by remember{mutableStateOf(false)}
 val context=LocalContext.current
 var watch by remember{mutableStateOf(prefs.getBoolean("backgroundAlerts",false))}
 var notificationError by remember{mutableStateOf("")}
 fun watchEnabled(enabled:Boolean){watch=enabled;prefs.edit().putBoolean("backgroundAlerts",enabled).apply();if(enabled)context.startForegroundService(android.content.Intent(context,AlertWatchService::class.java))else context.stopService(android.content.Intent(context,AlertWatchService::class.java))}
 val permission=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()){allowed->if(allowed)watchEnabled(true)else notificationError="Notification permission is required."}
 val pair=remember{Pairing.read(context)}
 LaunchedEffect(section){if(section=="Events")pair?.let{LiveRepository.loadDeliveries(it)}}
 var url by remember{mutableStateOf(pair?.first?:"")};var token by remember{mutableStateOf("")};var error by remember{mutableStateOf("")}
 fun bool(key:String,value:Boolean,change:(Boolean)->Unit){prefs.edit().putBoolean(key,value).apply();change(value)}
 Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
  ImmersiveDialogWindow()
  Surface(color=DeskColors.background,contentColor=DeskColors.text,modifier=Modifier.fillMaxSize()){
   Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){
    Column(Modifier.widthIn(max=640.dp).fillMaxWidth().fillMaxHeight().windowInsetsPadding(WindowInsets.displayCutout).imePadding().padding(horizontal=16.dp)){
     Row(Modifier.fillMaxWidth().height(52.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
      IconButton(onClick=close){LabGlyph("Back",DeskColors.text,23.dp)}
      Text("Settings",fontSize=20.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
      Text("v${BuildConfig.VERSION_NAME}",color=DeskColors.muted,fontSize=11.sp)
     }
     CompactTabs(listOf("Display","Home","Events","Connection"),section,{section=it;resetPending=false})
     key(section){
      Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top=8.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
       when(section){
        "Display"->{
         @Composable fun screen(){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
          SettingsHeading("Screen")
          SettingsGroup{
           SettingSwitch("Keep screen awake",awake){bool("keepAwake",it,setAwake)}
           SettingsDivider();SettingSwitch("Dim desk mode",dimMode){bool("deskMode",it,setDimMode)}
           if(dimMode)SettingsRange("App brightness","${(dim*100).toInt()}%",dim,.05f..1f){prefs.edit().putFloat("brightness",it).apply();setDim(it)}
           SettingsDivider();SettingSwitch("Reduce motion",reduced){bool("reducedMotion",it,setReduced)}
          }
         }}
         @Composable fun navigation(){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
          SettingsHeading("Navigation")
          SettingsGroup{
           Text("Hide Home header when idle · touch to reveal",color=DeskColors.muted,fontSize=11.sp,modifier=Modifier.padding(horizontal=12.dp,vertical=8.dp))
          }
         }}
         if(wideWindow())Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){Box(Modifier.weight(1f)){screen()};Box(Modifier.weight(1f)){navigation()}}else{screen();navigation()}
        }
        "Home"->{
         SettingsHeading("Layout")
         SettingsGroup{SettingsRange("Widget spacing","${gap}dp",gap.toFloat(),6f..16f,9){val n=it.toInt();prefs.edit().putInt("gridGap",n).apply();setGap(n)}}
         SettingsGroup{
          listOf("Edit Home","Add widget","Export layout","Import layout").forEachIndexed{i,action->if(i>0)SettingsDivider();SettingsAction(action){home(action)}}
         }
         SettingsGroup{
          SettingsAction("Reset this layout",warning=true){resetPending=!resetPending}
          if(resetPending)Dialog(onDismissRequest={resetPending=false},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
           ImmersiveDialogWindow()
           Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).padding(20.dp),contentAlignment=Alignment.Center){
            Surface(color=DeskColors.surface,contentColor=DeskColors.text,shape=RoundedCornerShape(20.dp),modifier=Modifier.widthIn(max=360.dp).fillMaxWidth()){
             Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
              Text("Reset this layout?",fontSize=18.sp,fontWeight=FontWeight.SemiBold)
              Text("Replace the current orientation’s widgets with the defaults?",color=DeskColors.muted,fontSize=13.sp,lineHeight=19.sp)
              Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.End)){DeskAction("Cancel"){resetPending=false};DeskAction("Reset layout",true){home("Reset this layout")}}
             }
            }
           }
          }
         }
        }
        "Events"->{
         SettingsHeading("Live alerts")
         SettingsGroup{
          SettingSwitch("Background live alerts",watch){enabled->if(enabled&&android.os.Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)else watchEnabled(enabled)}
          Text("Keeps a visible connection notification. Battery restrictions or lost Wi-Fi can interrupt delivery; this is a local live connection, not cloud push.",color=DeskColors.muted,fontSize=11.sp,modifier=Modifier.padding(12.dp))
          var apps by remember{mutableStateOf(prefs.getBoolean("appNotifications",true))};var recoveries by remember{mutableStateOf(prefs.getBoolean("recoveryNotifications",false))};var quiet by remember{mutableStateOf(prefs.getBoolean("quietHours",false))}
          SettingsDivider();SettingSwitch("App notifications",apps){apps=it;prefs.edit().putBoolean("appNotifications",it).apply()}
          SettingsDivider();SettingSwitch("Recovery notifications",recoveries){recoveries=it;prefs.edit().putBoolean("recoveryNotifications",it).apply()}
          SettingsDivider();SettingSwitch("Quiet hours",quiet){quiet=it;prefs.edit().putBoolean("quietHours",it).apply()}
          if(quiet){var start by remember{mutableIntStateOf(prefs.getInt("quietStart",22))};var end by remember{mutableIntStateOf(prefs.getInt("quietEnd",7))};SettingsRange("From","%02d:00".format(start),start.toFloat(),0f..23f,22){start=it.toInt();prefs.edit().putInt("quietStart",start).apply()};SettingsRange("Until","%02d:00".format(end),end.toFloat(),0f..23f,22){end=it.toInt();prefs.edit().putInt("quietEnd",end).apply()}}

         }
         DeskDisclosure("Notification sources"){
          val available=((LiveRepository.active+LiveRepository.events).map{it.source}+LiveRepository.deliveries.map{it.name}).filter{it!="Test"}.distinct().sorted()
          var selectedSources by remember{mutableStateOf(prefs.getStringSet("notificationSources",null)?.toSet())}
          SettingsAction("All sources"){selectedSources=null;prefs.edit().remove("notificationSources").apply()}
          available.forEach{source->SettingSwitch(source,selectedSources==null||source in selectedSources.orEmpty()){enabled->val current=selectedSources?:available.toSet();selectedSources=if(enabled)current+source else current-source;prefs.edit().putStringSet("notificationSources",selectedSources).apply()}}
         }
         if(notificationError.isNotBlank())Text(notificationError,color=DeskColors.apricot,fontSize=12.sp)
         SettingsHeading("Notifications")
         SettingsGroup{SettingSwitch("Show test notifications",tests){bool("showTests",it,setTests)};SettingsDivider();SettingsAction("Monitors"){monitors()}}
         SettingsHeading("Recent webhook activity")
         val received=LiveRepository.deliveries.filter{it.name !in listOf("Kubernetes","Prometheus","Argo CD","Test")}
         SettingsGroup{
          if(received.isEmpty())Text(if(LiveRepository.deliveryError)"Delivery history unavailable"else"No deliveries recorded",color=DeskColors.muted,fontSize=12.sp,modifier=Modifier.padding(12.dp))
          received.forEachIndexed{i,delivery->if(i>0)SettingsDivider();SettingsValue("${delivery.name} · ${delivery.count}",localTime(delivery.lastReceived))}
         }
         SettingsHeading("Storage & privacy")
         SettingsGroup{
          SettingsValue("History retention","30 days · 2,000 records")
          SettingsDivider();SettingsValue("Backend volume","1 GiB")
          Text("Active incidents remain until recovery is confirmed. Live alerts can be enabled above.",fontSize=12.sp,lineHeight=18.sp,color=DeskColors.muted,modifier=Modifier.padding(12.dp))
          SettingsDivider();SettingsAction("Clear device cache"){
           java.io.File(context.filesDir,"last-frame.json").delete();context.cacheDir.listFiles()?.filter{it.name.startsWith("asset-")}?.forEach{it.delete()};cacheCleared=true
          }
         }
         if(cacheCleared)Text("Device cache cleared",color=DeskColors.green,fontSize=12.sp)
        }
        "Connection"->{
         SettingsHeading("Backend")
         SettingsGroup{SettingsValue("Connection",LiveRepository.state)}
         OutlinedTextField(value=url,onValueChange={url=it;error=""},label={Text("HTTPS address")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp))
         OutlinedTextField(value=token,onValueChange={token=it;error=""},label={Text("Pairing token")},supportingText={Text("Leave blank to keep the existing token")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp))
         if(error.isNotEmpty())Text(error,color=DeskColors.apricot,fontSize=12.sp)
         DeskAction("Save & reconnect",true){
          try{val uri=java.net.URI(url.trim());require(uri.scheme=="https"&&!uri.host.isNullOrBlank()&&uri.userInfo==null&&uri.query==null&&uri.fragment==null)
           val secret=token.ifBlank{pair?.second?:""};require(secret.length>=32);Pairing.save(context,url.trim(),secret);(context as? Activity)?.recreate();close()
          }catch(_:Exception){error="Enter a valid HTTPS address and pairing token"}
         }
        }
       }
      }
     }
    }
   }
  }
 }
}
@Composable private fun SettingsHeading(text:String){Text(text,color=DeskColors.muted,fontSize=11.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=4.dp,start=2.dp))}
@Composable private fun SettingsGroup(content:@Composable ColumnScope.()->Unit){Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DeskColors.surface),content=content)}
@Composable private fun SettingsDivider(){HorizontalDivider(color=DeskColors.muted.copy(alpha=.1f),modifier=Modifier.padding(horizontal=12.dp))}
@Composable private fun SettingSwitch(label:String,value:Boolean,change:(Boolean)->Unit){
 Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(value=value,role=Role.Switch,onValueChange=change).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
  Text(label,Modifier.weight(1f),color=DeskColors.text,fontSize=13.sp);Box(Modifier.size(36.dp,22.dp).clip(RoundedCornerShape(12.dp)).background(if(value)DeskColors.cobalt else DeskColors.raised).padding(3.dp),contentAlignment=if(value)Alignment.CenterEnd else Alignment.CenterStart){Box(Modifier.size(16.dp).background(DeskColors.text,RoundedCornerShape(8.dp)))}
 }
}
@Composable private fun SettingsAction(label:String,warning:Boolean=false,click:()->Unit){
 Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(onClick=click).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){Text(label,color=if(warning)DeskColors.apricot else DeskColors.text,fontSize=13.sp,modifier=Modifier.weight(1f));Text("›",color=DeskColors.muted,fontSize=21.sp)}
}
@Composable private fun SettingsValue(label:String,value:String){Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){Text(label,color=DeskColors.muted,fontSize=12.sp,modifier=Modifier.weight(1f));Text(value,color=DeskColors.text,fontSize=12.sp,modifier=Modifier.weight(1f),textAlign=TextAlign.End)}}
@Composable private fun SettingsRange(label:String,value:String,current:Float,range:ClosedFloatingPointRange<Float>,steps:Int=0,change:(Float)->Unit){
 Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp)){
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(label,color=DeskColors.text,fontSize=13.sp,modifier=Modifier.weight(1f));Text(value,color=DeskColors.blue,fontSize=12.sp,fontWeight=FontWeight.SemiBold)}
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
   IconButton(onClick={change((current-(range.endInclusive-range.start)/(if(steps>0)steps+1 else 20)).coerceIn(range))},modifier=Modifier.size(40.dp).semantics{contentDescription="Decrease $label"}){Text("−",color=DeskColors.blue,fontSize=20.sp)}
   LinearProgressIndicator(progress={((current-range.start)/(range.endInclusive-range.start)).coerceIn(0f,1f)},modifier=Modifier.weight(1f).height(4.dp),color=DeskColors.cobalt,trackColor=DeskColors.raised,drawStopIndicator={})
   IconButton(onClick={change((current+(range.endInclusive-range.start)/(if(steps>0)steps+1 else 20)).coerceIn(range))},modifier=Modifier.size(40.dp).semantics{contentDescription="Increase $label"}){Text("+",color=DeskColors.blue,fontSize=20.sp)}
  }

 }
}

@Composable internal fun MonitorScreen(open:(String)->Unit={}){
 var search by remember{mutableStateOf("")};var status by remember{mutableStateOf("All")}
 if(wideWindow())Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Box(Modifier.weight(1f)){DeskSearch(search,{search=it},"Search monitors")};Box(Modifier.width(350.dp)){CompactTabs(listOf("All","Up","Down","Pending","Maintenance"),status,{status=it})}}
 else {DeskSearch(search,{search=it},"Search monitors");CompactTabs(listOf("All","Up","Down","Pending","Maintenance"),status,{status=it})}
 val rows=LiveRepository.monitors.filter{it.name.contains(search,true)&&(status=="All"||it.status==status)}
 if(LiveRepository.monitors.isEmpty())Text("Monitor readings unavailable",modifier=Modifier.padding(16.dp),color=DeskColors.muted)
 val columns=if(wideWindow())2 else 1
 rows.chunked(columns).forEach{group->Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){
  group.forEach{m->Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(DeskColors.surface).clickable{open(m.id)}.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
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
