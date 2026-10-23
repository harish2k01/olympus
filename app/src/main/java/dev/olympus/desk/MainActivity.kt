package dev.olympus.desk

import android.os.Bundle
import android.content.Intent
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.*
import androidx.core.content.edit
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.olympus.desk.data.*
import java.util.Locale

private val Ink=Color(0xFF14171E)
private val Tile=Color(0xFF242832)
private val Cobalt=Color(0xFF506CF4)
private val Apricot=Color(0xFFEDB48F)
private val Lilac=Color(0xFFBBB6E1)
private val Mint=Color(0xFFB4DFBE)
private val Muted=Color(0xFFB4B6C0)
private val Paper=Color(0xFFF5F3EF)
private fun Float?.pretty()=if(this==null)"—" else String.format(Locale.US,"%.1f",this)

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        window.attributes=window.attributes.apply {layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES}
        immersive()
        pairFromDebugIntent(intent)
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(isActive){
                val pair=Pairing.read(this@MainActivity)
                if(pair!=null)try{val payload=withContext(Dispatchers.IO){LiveRepository.fetch(pair)};LiveRepository.apply(payload)}catch(_:Exception){LiveRepository.unavailable()}
                delay(5000)
            }
        }}
        setContent {
            MaterialTheme(colorScheme=darkColorScheme(primary=Cobalt,background=Ink,surface=Tile,onSurface=Paper,onBackground=Paper)) { OlympusApp() }
        }
    }
    private fun immersive(){WindowCompat.getInsetsController(window,window.decorView).apply{systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;hide(WindowInsetsCompat.Type.systemBars())}}
    override fun onWindowFocusChanged(hasFocus:Boolean){super.onWindowFocusChanged(hasFocus);if(hasFocus)immersive()}
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);pairFromDebugIntent(intent)}
    private fun pairFromDebugIntent(intent:Intent){if(BuildConfig.DEBUG){val token=intent.getStringExtra("pairToken");val url=intent.getStringExtra("backendUrl");if(token!=null&&url!=null)Pairing.save(this,url,token)}}
}

@Composable private fun OlympusApp(){
    val landscape=LocalConfiguration.current.screenWidthDp>650
    val prefs=LocalContext.current.getSharedPreferences("desk",0)
    var tab by rememberSaveable { mutableStateOf("Desk") }
    var detail by rememberSaveable { mutableStateOf("") }
    var arrange by rememberSaveable { mutableStateOf(false) }
    var media by rememberSaveable { mutableStateOf(prefs.getBoolean("media",true)) }
    var machines by rememberSaveable { mutableStateOf(prefs.getBoolean("machines",false)) }
    var swapped by rememberSaveable { mutableStateOf(prefs.getBoolean("swapped",false)) }
    var recovery by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(detail.isNotEmpty()){detail="";recovery=0}
    Surface(color=Ink,modifier=Modifier.fillMaxSize()){
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).padding(horizontal=if(landscape)12.dp else 20.dp,vertical=if(landscape)0.dp else 12.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                Column { Text("olympus.",fontSize=if(landscape)19.sp else 24.sp,fontWeight=FontWeight.Medium); if(!landscape)Text("YOUR LAB, IN REACH",fontSize=10.sp,letterSpacing=2.sp,color=Muted) }
                if(landscape)Text(LiveRepository.state,fontSize=10.sp,color=if(LiveRepository.state.startsWith("Live"))Mint else Apricot)
                if(landscape)CompactNavigation(tab,detail){tab=it;detail="";arrange=false}
                if(tab=="Desk"&&detail.isEmpty()) TextButton(onClick={arrange=!arrange}) { Text(if(arrange)"Done" else "Arrange",color=Paper) }
            }
            if(!landscape)Row(Modifier.fillMaxWidth().padding(top=10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2B3040)).padding(9.dp),horizontalArrangement=Arrangement.SpaceBetween){
                Text(LiveRepository.state.uppercase(),fontSize=10.sp,color=Mint,fontWeight=FontWeight.Medium)
                Text(if(LiveRepository.lastSuccess==0L)"Waiting for backend" else "Updated ${LiveRepository.time.substringAfter('T').take(8)} UTC",fontSize=10.sp,color=Muted)
            }
            AnimatedVisibility(arrange&&detail.isEmpty()&&tab=="Desk"){
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(0.dp)){
                    FilterChip(selected=media,onClick={media=!media;prefs.edit{putBoolean("media",media)}},label={Text("Media")})
                    FilterChip(selected=machines,onClick={machines=!machines;prefs.edit{putBoolean("machines",machines)}},label={Text("Machines")})
                    TextButton(onClick={swapped=!swapped;prefs.edit{putBoolean("swapped",swapped)}}){Text("Swap panels ⇄",color=Lilac)}
                }
            }
            AnimatedContent(targetState=if(detail.isNotEmpty())detail else tab,modifier=Modifier.weight(1f),transitionSpec={ (slideInHorizontally(tween(250)){it/12}+fadeIn(tween(200))) togetherWith (slideOutHorizontally(tween(200)){-it/12}+fadeOut(tween(150))) },label="screen") { screen ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=12.dp)){
                    when {
                        detail.isNotEmpty()->{
                            TextButton(onClick={detail="";recovery=0},contentPadding=PaddingValues(0.dp)){Text("← Back",color=Muted)}
                            when {
                                screen.startsWith("node:")->LiveRepository.nodes.firstOrNull{it.name==screen.substringAfter(":")}?.let{NodeDetail(it)}
                                screen=="Cluster resources"->Resources{detail="node:$it"}
                                screen=="Protection"->{Heading("Backup protection","LONGHORN / LIVE");Panel(when(LiveRepository.backup){true->"Backup target available";false->"Backup target unavailable";null->"Backup target status unavailable"},"Storage health and backup coverage are separate. An available destination is not proof of a tested restoration.",Apricot,Color(0xFF452613))}
                                else->{Heading(screen,"ARGO CD / LIVE");val app=LiveRepository.services.find{it.name==screen};Panel(app?.health?:"Status unavailable",app?.let{"Namespace: ${it.namespace} · Sync: ${it.sync}"}?:"No matching Argo Application was returned. No readiness has been inferred.")}
                            }
                        }
                        screen=="Desk"->Desk(media,machines,swapped){detail=it}
                        screen=="Nodes"->Resources{detail="node:$it"}
                        screen=="Events"->EventFeed()
                        screen=="Services"->{Heading("Applications","ARGO CD / LIVE");if(LiveRepository.services.isEmpty())Text("Application data unavailable",color=Muted);LiveRepository.services.chunked(2).forEach{pair->Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.padding(bottom=10.dp)){pair.forEach{item->Module(item.name,item.health,"${item.namespace} · ${item.sync}",Tile,Paper,Modifier.weight(1f)){detail=item.name}}}}}
                    }
                }
            }
            if(!landscape)Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Tile).padding(5.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)){
                listOf("Desk","Nodes","Events","Services").forEach{item->
                    val bg by animateColorAsState(if(tab==item&&detail.isEmpty())Cobalt else Tile,label="navigation")
                    TextButton(onClick={tab=item;detail="";recovery=0;arrange=false},modifier=Modifier.weight(1f).background(bg,RoundedCornerShape(13.dp)),contentPadding=PaddingValues(horizontal=3.dp,vertical=10.dp)){Text(item,color=if(tab==item)Paper else Muted,fontSize=12.sp)}
                }
            }
        }
    }
}

@Composable private fun CompactNavigation(tab:String,detail:String,select:(String)->Unit){
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Tile).padding(3.dp),horizontalArrangement=Arrangement.spacedBy(2.dp)){
        listOf("Desk","Nodes","Events","Services").forEach{item->
            TextButton(onClick={select(item)},modifier=Modifier.height(40.dp).background(if(tab==item&&detail.isEmpty())Cobalt else Tile,RoundedCornerShape(9.dp)),contentPadding=PaddingValues(horizontal=12.dp,vertical=0.dp)){Text(item,color=if(tab==item)Paper else Muted,fontSize=11.sp)}
        }
    }
}

@Composable private fun Desk(media:Boolean,machines:Boolean,swapped:Boolean,open:(String)->Unit){
    BoxWithConstraints {
        val wide=maxWidth>650.dp
        if(wide){Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Hero(Modifier.weight(.8f)){open("Cluster resources")}
            Column(Modifier.weight(2f),verticalArrangement=Arrangement.spacedBy(10.dp)){DeskModules(media,machines,swapped,open)}
        }}else{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Hero(Modifier.fillMaxWidth()){open("Cluster resources")};DeskModules(media,machines,swapped,open)}}
    }
}
@Composable private fun Hero(modifier:Modifier=Modifier,onClick:()->Unit){
    Column(modifier.clip(RoundedCornerShape(26.dp)).background(Cobalt).clickable(onClick=onClick).padding(22.dp)){
        Text("OLYMPUS / YOUR DESK",fontSize=10.sp,letterSpacing=1.5.sp)
        Text("A good evening\nfor your lab.",fontSize=29.sp,lineHeight=35.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(vertical=14.dp))
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Box(Modifier.size(49.dp).clip(RoundedCornerShape(17.dp)).background(Color.White.copy(alpha=.18f)),contentAlignment=Alignment.Center){Text("•  •",fontSize=19.sp)}
            Column{Text("${LiveRepository.nodes.count{it.ready}} / ${LiveRepository.nodes.size} nodes ready",fontSize=14.sp);Text("${LiveRepository.monitorsUp ?: "—"} / ${LiveRepository.monitorsTotal ?: "—"} HTTP checks up ↗",fontSize=11.sp,color=Color.White.copy(alpha=.8f))}
        }
    }
}
@Composable private fun DeskModules(media:Boolean,machines:Boolean,swapped:Boolean,open:(String)->Unit){
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
        if(!swapped)Module("CPU usage","${LiveRepository.cpuPercent.pretty()}%","${LiveRepository.totalCores} cores · Weighted total",Tile,Paper,Modifier.weight(1f)){open("Cluster resources")}
        Module("Protection",when(LiveRepository.backup){true->"Available";false->"Backup gap";null->"Unavailable"},"Longhorn · Review →",Apricot,Color(0xFF452613),Modifier.weight(1f)){open("Protection")}
        if(swapped)Module("CPU usage","${LiveRepository.cpuPercent.pretty()}%","${LiveRepository.totalCores} cores · Weighted total",Tile,Paper,Modifier.weight(1f)){open("Cluster resources")}
    }
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
        Module("Memory","${LiveRepository.memoryPercent.pretty()}%","${LiveRepository.usedMemory.pretty()} / ${LiveRepository.totalMemory.pretty()} GiB",Lilac,Color(0xFF302848),Modifier.weight(1f)){open("Cluster resources")}
        Module("GitOps","${LiveRepository.services.count{it.health=="Healthy"&&it.sync=="Synced"}} / ${LiveRepository.services.size}","Healthy & Synced",Tile,Paper,Modifier.weight(1f)){open("argocd")}
    }
    AnimatedVisibility(media||machines){Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
        if(media)Module("Downloads","${LiveRepository.download.pretty()} MiB/s","qBittorrent exporter",Tile,Paper,Modifier.weight(1f)){open("qbittorrent")}
        if(machines)Module("Compute","${LiveRepository.nodes.size} nodes","Ready: ${LiveRepository.nodes.count{it.ready}}",Tile,Paper,Modifier.weight(1f)){open("Cluster resources")}
    }}
}
@Composable private fun Module(label:String,value:String,meta:String,bg:Color,fg:Color,modifier:Modifier=Modifier,click:()->Unit){
    Column(modifier.animateContentSize().clip(RoundedCornerShape(23.dp)).background(bg).clickable(onClick=click).padding(17.dp).heightIn(min=96.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,fontSize=12.sp,color=fg);Text("↗",color=fg)}
        Text(value,fontSize=24.sp,fontWeight=FontWeight.Medium,color=fg,modifier=Modifier.padding(top=12.dp,bottom=7.dp))
        Text(meta,fontSize=11.sp,color=fg.copy(alpha=.75f),lineHeight=16.sp)
    }
}
@Composable private fun Heading(title:String,eyebrow:String){Text(eyebrow,fontSize=10.sp,letterSpacing=1.5.sp,color=Muted);Text(title,fontSize=27.sp,fontWeight=FontWeight.Medium,lineHeight=33.sp,modifier=Modifier.padding(top=8.dp,bottom=18.dp))}
@Composable private fun Panel(title:String,body:String,bg:Color=Tile,fg:Color=Paper){Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(bg).padding(20.dp)){Text(title,fontSize=17.sp,fontWeight=FontWeight.Medium,color=fg);Text(body,fontSize=13.sp,lineHeight=21.sp,color=fg.copy(alpha=.78f),modifier=Modifier.padding(top=9.dp))}}
@Composable private fun Meter(label:String,value:Float?,color:Color){
    val animated by animateFloatAsState((value?:0f)/100f,tween(600),label="usage")
    Row(Modifier.fillMaxWidth().padding(top=10.dp,bottom=6.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(label,fontSize=12.sp,color=Muted);Text(if(value==null)"Unavailable" else "${value.pretty()}%",fontSize=12.sp)}
    if(value!=null)LinearProgressIndicator(progress={animated.coerceIn(0f,1f)},color=color,trackColor=Color(0xFF3B3E49),modifier=Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)))
}
@Composable private fun Resources(open:(String)->Unit){
    if(LocalConfiguration.current.screenWidthDp>650){
        Text("Cluster resources",fontSize=17.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(bottom=10.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(18.dp)){
            Column(Modifier.weight(.8f)){
                Meter("CPU · ${LiveRepository.totalCores} cores",LiveRepository.cpuPercent,Cobalt)
                Meter("Memory · ${LiveRepository.totalMemory.pretty()} GiB",LiveRepository.memoryPercent,Lilac)
                Text("${LiveRepository.usedMemory.pretty()} GiB used · Capacity weighted\nExternal hosts excluded.",fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=12.dp))
            }
            Column(Modifier.weight(1.5f),verticalArrangement=Arrangement.spacedBy(8.dp)){
                LiveRepository.nodes.chunked(2).forEach{pair->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){pair.forEach{n->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Tile).clickable{open(n.name)}.padding(12.dp)){
                        Text("${n.name} ↗",fontSize=16.sp,fontWeight=FontWeight.Medium)
                        Text(if(n.available)"CPU ${n.cpuPercent.pretty()}% · ${n.cores} cores" else "Metrics unavailable",fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=6.dp))
                        Text(if(n.available)"RAM ${(n.memoryUsedGiB/n.memoryTotalGiB*100).pretty()}%" else if(n.ready)"Node ready" else "Node not ready",fontSize=11.sp,color=Lilac,modifier=Modifier.padding(top=4.dp))
                    }
                }}}
            }
        }
    }else{
        Heading("A closer look at Olympus.","LIVE RESOURCES")
        Meter("CPU · ${LiveRepository.totalCores} cores",LiveRepository.cpuPercent,Cobalt)
        Meter("Memory · ${LiveRepository.totalMemory.pretty()} GiB",LiveRepository.memoryPercent,Lilac)
        Spacer(Modifier.height(18.dp))
        LiveRepository.nodes.forEach{n->Column(Modifier.fillMaxWidth().padding(bottom=10.dp).clip(RoundedCornerShape(20.dp)).background(Tile).clickable{open(n.name)}.padding(17.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(n.name,fontSize=19.sp,fontWeight=FontWeight.Medium);Text("${n.role} ↗",fontSize=11.sp,color=if(n.ready)Mint else Apricot)}
            Meter("CPU · ${n.cores} cores",if(n.available)n.cpuPercent else null,Cobalt);Meter("Memory · ${n.memoryUsedGiB.pretty()} / ${n.memoryTotalGiB.pretty()} GiB",if(n.available)n.memoryUsedGiB/n.memoryTotalGiB*100 else null,Lilac)
        }}
    }
    if(LiveRepository.nodes.isEmpty())Text("Node data unavailable. Waiting for an authenticated backend connection.",color=Muted)
}
@Composable private fun NodeDetail(n:NodeMetric){Heading(n.name,n.role.uppercase());Panel(if(n.ready)"Ready" else "Not ready","${n.cores} CPU cores · ${n.memoryTotalGiB.pretty()} GiB host memory. Prometheus CPU is a five-minute average; memory is total minus available.");Meter("CPU usage",if(n.available)n.cpuPercent else null,Cobalt);Meter("Memory usage",if(n.available)n.memoryUsedGiB/n.memoryTotalGiB*100 else null,Lilac);Spacer(Modifier.height(20.dp));Text("Updated: ${LiveRepository.time}",color=Muted,fontSize=12.sp)}
@Composable private fun EventFeed(){
    var source by rememberSaveable{mutableStateOf("All")}
    Text("Notifications & events",fontSize=22.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(bottom=8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
        (listOf("All")+LiveRepository.events.map{it.source}.distinct().sorted()).forEach{s->FilterChip(selected=source==s,onClick={source=s},label={Text(s)})}
    }
    Text("Current alerts, Kubernetes warnings and received webhooks. Webhook history resets when the backend restarts.",fontSize=11.sp,color=Muted,modifier=Modifier.padding(vertical=8.dp))
    val events=LiveRepository.events.filter{source=="All"||it.source==source}
    if(events.isEmpty())Text("No events returned for this source.",color=Muted)
    events.forEach{event->
        Column(Modifier.fillMaxWidth().padding(bottom=10.dp).clip(RoundedCornerShape(19.dp)).background(Tile).padding(16.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(event.source,color=Lilac,fontSize=11.sp);Text(event.kind,color=if(event.kind=="Recovery")Mint else Apricot,fontSize=11.sp)}
            Text(event.title,fontSize=16.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(vertical=6.dp));Text(event.detail,color=Muted,fontSize=12.sp,lineHeight=18.sp)
        }
    }
}
