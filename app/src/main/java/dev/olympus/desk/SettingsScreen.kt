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
internal val LocalGridGap=staticCompositionLocalOf{10}

@Composable internal fun SettingsScreen(prefs:SharedPreferences,dimMode:Boolean,dim:Float,reduced:Boolean,awake:Boolean,tests:Boolean,dock:Int,gap:Int,setDimMode:(Boolean)->Unit,setDim:(Float)->Unit,setReduced:(Boolean)->Unit,setAwake:(Boolean)->Unit,setTests:(Boolean)->Unit,setDock:(Int)->Unit,setGap:(Int)->Unit,monitors:()->Unit,home:(String)->Unit,close:()->Unit,focus:Boolean,setFocus:(Boolean)->Unit){
 var section by rememberSaveable{mutableStateOf("Display")}
 var resetPending by remember{mutableStateOf(false)}
 var cacheCleared by remember{mutableStateOf(false)}
 val context=LocalContext.current
 val pair=remember{Pairing.read(context)}
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
           SettingSwitch("Focus mode",focus){bool("focusMode",it,setFocus)}
           Text("Hide header when idle · touch to reveal",color=DeskColors.muted,fontSize=11.sp,modifier=Modifier.padding(horizontal=12.dp,vertical=8.dp))
           SettingsDivider();SettingsRange("Dock hide delay","${dock}s",dock.toFloat(),2f..15f,12){val n=it.toInt();prefs.edit().putInt("dockSeconds",n).apply();setDock(n)}
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
         SettingsHeading("Notifications")
         SettingsGroup{SettingSwitch("Show test notifications",tests){bool("showTests",it,setTests)};SettingsDivider();SettingsAction("Monitors"){monitors()}}
         SettingsHeading("Recent webhook activity")
         val received=LiveRepository.events.filter{it.source !in listOf("Kubernetes","Prometheus","Argo CD","Test")&&(tests||!(it.kind=="Test"||it.title=="Test"))}.groupBy{it.source}
         SettingsGroup{
          if(received.isEmpty())Text("No recent deliveries",color=DeskColors.muted,fontSize=12.sp,modifier=Modifier.padding(12.dp))
          received.entries.forEachIndexed{i,(source,events)->
           if(i>0)SettingsDivider()
           Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Text(source,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
            Text(localTime(events.maxOf{it.received.ifBlank{it.time}}),fontSize=11.sp,color=DeskColors.muted)
           }
          }
         }
         SettingsHeading("Storage & privacy")
         SettingsGroup{
          SettingsValue("History retention","30 days · 2,000 records")
          SettingsDivider();SettingsValue("Backend volume","1 GiB")
          Text("Active incidents remain until recovery is confirmed. Background system notifications are not enabled.",fontSize=12.sp,lineHeight=18.sp,color=DeskColors.muted,modifier=Modifier.padding(12.dp))
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
 Row(Modifier.fillMaxWidth().heightIn(min=52.dp).toggleable(value=value,role=Role.Switch,onValueChange=change).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
  Text(label,Modifier.weight(1f),color=DeskColors.text,fontSize=13.sp);Switch(checked=value,onCheckedChange=null)
 }
}
@Composable private fun SettingsAction(label:String,warning:Boolean=false,click:()->Unit){
 Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(onClick=click).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){Text(label,color=if(warning)DeskColors.apricot else DeskColors.text,fontSize=13.sp,modifier=Modifier.weight(1f));Text("›",color=DeskColors.muted,fontSize=21.sp)}
}
@Composable private fun SettingsValue(label:String,value:String){Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){Text(label,color=DeskColors.muted,fontSize=12.sp,modifier=Modifier.weight(1f));Text(value,color=DeskColors.text,fontSize=12.sp,modifier=Modifier.weight(1f),textAlign=TextAlign.End)}}
@Composable private fun SettingsRange(label:String,value:String,current:Float,range:ClosedFloatingPointRange<Float>,steps:Int=0,change:(Float)->Unit){
 Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=10.dp)){
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(label,color=DeskColors.text,fontSize=13.sp,modifier=Modifier.weight(1f));Text(value,color=DeskColors.blue,fontSize=12.sp,fontWeight=FontWeight.SemiBold)}
  Slider(value=current,onValueChange=change,valueRange=range,steps=steps,modifier=Modifier.fillMaxWidth())
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
