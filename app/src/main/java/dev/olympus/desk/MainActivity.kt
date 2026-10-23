package dev.olympus.desk

import android.os.Bundle
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
private fun Float.pretty()=String.format(Locale.US,"%.1f",this)

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme=darkColorScheme(primary=Cobalt,background=Ink,surface=Tile,onSurface=Paper,onBackground=Paper)) { OlympusApp() }
        }
    }
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
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal=20.dp,vertical=if(landscape)4.dp else 12.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                Column { Text("olympus.",fontSize=if(landscape)19.sp else 24.sp,fontWeight=FontWeight.Medium); if(!landscape)Text("YOUR LAB, IN REACH",fontSize=10.sp,letterSpacing=2.sp,color=Muted) }
                if(landscape)Text("DEMO · No cluster connection",fontSize=10.sp,color=Lilac)
                if(tab=="Desk"&&detail.isEmpty()) TextButton(onClick={arrange=!arrange}) { Text(if(arrange)"Done" else "Arrange",color=Paper) }
            }
            if(!landscape)Row(Modifier.fillMaxWidth().padding(top=10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2B3040)).padding(9.dp),horizontalArrangement=Arrangement.SpaceBetween){
                Text("DEMO MODE",fontSize=10.sp,color=Lilac,fontWeight=FontWeight.Medium)
                Text("Illustrative data · No cluster connection",fontSize=10.sp,color=Muted)
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
                                screen.startsWith("node:")->NodeDetail(DemoRepository.nodes.first{it.name==screen.substringAfter(":")})
                                screen=="Cluster resources"->Resources{detail="node:$it"}
                                screen=="Protection"->{Heading("Storage needs backup coverage.","PROTECTION / DEMO"); Panel("Longhorn target unavailable","Illustrative finding. Storage health and backup coverage are separate. A successful backup job is not a tested restoration.",Apricot,Color(0xFF452613));Text("No configuration changes are available in this demo.",color=Muted,fontSize=13.sp,modifier=Modifier.padding(top=16.dp))}
                                screen=="Recovery demo"->{Recovery(recovery){recovery=it}}
                                else->{Heading(screen,"SERVICE / DEMO");Panel("Ready in this example","The connected version will load status, metrics, events and Loki logs here.");Spacer(Modifier.height(16.dp));Button(onClick={detail="Recovery demo"}){Text("Explore recovery flow")}}
                            }
                        }
                        screen=="Desk"->Desk(media,machines,swapped){detail=it}
                        screen=="Nodes"->Resources{detail="node:$it"}
                        screen=="Events"->EventFeed()
                        screen=="Services"->{Heading("Everything has its place.","YOUR SERVICES");listOf("Ghost","Immich","RailWatch","qBittorrent","Argo CD","Protection").chunked(2).forEach{pair->Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.padding(bottom=10.dp)){pair.forEach{item->Module(item,"Explore →","Demo service",Tile,Paper,Modifier.weight(1f)){detail=item}}}}}
                    }
                }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Tile).padding(5.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)){
                listOf("Desk","Nodes","Events","Services").forEach{item->
                    val bg by animateColorAsState(if(tab==item&&detail.isEmpty())Cobalt else Tile,label="navigation")
                    TextButton(onClick={tab=item;detail="";recovery=0;arrange=false},modifier=Modifier.weight(1f).background(bg,RoundedCornerShape(13.dp)),contentPadding=PaddingValues(horizontal=3.dp,vertical=10.dp)){Text(item,color=if(tab==item)Paper else Muted,fontSize=12.sp)}
                }
            }
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
            Column{Text("4 nodes ready",fontSize=14.sp);Text("Illustrative cluster snapshot ↗",fontSize=11.sp,color=Color.White.copy(alpha=.8f))}
        }
    }
}
@Composable private fun DeskModules(media:Boolean,machines:Boolean,swapped:Boolean,open:(String)->Unit){
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
        val cpu=DemoRepository.cpuPercent(DemoRepository.nodes)
        if(!swapped)Module("CPU usage","${cpu.pretty()}%","20 cores · Weighted total",Tile,Paper,Modifier.weight(1f)){open("Cluster resources")}
        Module("Protection","Backup gap","Longhorn · Review →",Apricot,Color(0xFF452613),Modifier.weight(1f)){open("Protection")}
        if(swapped)Module("CPU usage","${cpu.pretty()}%","20 cores · Weighted total",Tile,Paper,Modifier.weight(1f)){open("Cluster resources")}
    }
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
        Module("Memory","${DemoRepository.memoryPercent(DemoRepository.nodes).pretty()}%","23.1 / 41.5 GiB · Demo",Lilac,Color(0xFF302848),Modifier.weight(1f)){open("Cluster resources")}
        Module("GitOps","58 / 58","Healthy & Synced · Demo",Tile,Paper,Modifier.weight(1f)){open("Argo CD")}
    }
    AnimatedVisibility(media||machines){Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
        if(media)Module("Downloads","1.21 MiB/s","qBittorrent · Demo",Tile,Paper,Modifier.weight(1f)){open("qBittorrent")}
        if(machines)Module("Machines","6 running","3 stopped · Demo",Tile,Paper,Modifier.weight(1f)){open("Proxmox")}
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
@Composable private fun Meter(label:String,value:Float,color:Color){
    val animated by animateFloatAsState(value/100f,tween(600),label="usage")
    Row(Modifier.fillMaxWidth().padding(top=14.dp,bottom=7.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(label,fontSize=12.sp,color=Muted);Text("${value.pretty()}%",fontSize=12.sp)}
    LinearProgressIndicator(progress={animated.coerceIn(0f,1f)},color=color,trackColor=Color(0xFF3B3E49),modifier=Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)))
}
@Composable private fun Resources(open:(String)->Unit){
    if(LocalConfiguration.current.screenWidthDp>650){
        Text("Cluster resources / DEMO",fontSize=17.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(bottom=10.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(18.dp)){
            Column(Modifier.weight(.8f)){
                Meter("CPU · 20 cores",DemoRepository.cpuPercent(DemoRepository.nodes),Cobalt)
                Meter("Memory · 41.5 GiB",DemoRepository.memoryPercent(DemoRepository.nodes),Lilac)
                Text("Capacity-weighted cluster totals.\nExternal hosts excluded.",fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=12.dp))
            }
            Column(Modifier.weight(1.5f),verticalArrangement=Arrangement.spacedBy(8.dp)){
                DemoRepository.nodes.chunked(2).forEach{pair->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){pair.forEach{n->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Tile).clickable{open(n.name)}.padding(12.dp)){
                        Text("${n.name} ↗",fontSize=16.sp,fontWeight=FontWeight.Medium)
                        Text("CPU ${n.cpuPercent.pretty()}% · ${n.cores} cores",fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=6.dp))
                        Text("RAM ${(n.memoryUsedGiB/n.memoryTotalGiB*100).pretty()}%",fontSize=11.sp,color=Lilac,modifier=Modifier.padding(top=4.dp))
                    }
                }}}
            }
        }
        return
    }
    Heading("A closer look at Olympus.","RESOURCES / DEMO")
    Panel("Cluster totals","CPU is weighted by core count. Memory is weighted by capacity. Raspberry Pi and Proxmox hosts are outside these totals.")
    Meter("CPU · 20 cores",DemoRepository.cpuPercent(DemoRepository.nodes),Cobalt)
    Meter("Memory · 41.5 GiB",DemoRepository.memoryPercent(DemoRepository.nodes),Lilac)
    Spacer(Modifier.height(18.dp))
    DemoRepository.nodes.forEach{n->Column(Modifier.fillMaxWidth().padding(bottom=10.dp).clip(RoundedCornerShape(20.dp)).background(Tile).clickable{open(n.name)}.padding(17.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(n.name,fontSize=19.sp,fontWeight=FontWeight.Medium);Text("${n.role} ↗",fontSize=11.sp,color=Mint)}
        Meter("CPU · ${n.cores} cores",n.cpuPercent,Cobalt);Meter("Memory · ${n.memoryUsedGiB.pretty()} / ${n.memoryTotalGiB.pretty()} GiB",n.memoryUsedGiB/n.memoryTotalGiB*100,Lilac)
    }}
}
@Composable private fun NodeDetail(n:NodeMetric){Heading(n.name,"${n.role.uppercase()} / DEMO");Panel("Ready","${n.cores} CPU cores · ${n.memoryTotalGiB.pretty()} GiB host memory. Figures are fixed demonstration data.");Meter("CPU usage",n.cpuPercent,Cobalt);Meter("Memory usage",n.memoryUsedGiB/n.memoryTotalGiB*100,Lilac);Spacer(Modifier.height(20.dp));Panel("History comes with the backend","We will query Prometheus for real time-series data. No invented trend is shown as measured history.")}
@Composable private fun EventFeed(){
    var source by rememberSaveable{mutableStateOf("All")}
    Heading("One feed. Your whole lab.","NOTIFICATIONS & EVENTS / DEMO")
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
        listOf("All","Robusta","Uptime Kuma","Sonarr","Radarr","Prowlarr").forEach{s->FilterChip(selected=source==s,onClick={source=s},label={Text(s)})}
    }
    Text("Examples only. No webhook sources connected.",fontSize=12.sp,color=Muted,modifier=Modifier.padding(vertical=12.dp))
    DemoRepository.events.filter{source=="All"||it.source==source}.forEach{event->
        Column(Modifier.fillMaxWidth().padding(bottom=10.dp).clip(RoundedCornerShape(19.dp)).background(Tile).padding(18.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(event.source,color=Lilac,fontSize=11.sp);Text(event.kind,color=if(event.kind=="Recovery")Mint else Apricot,fontSize=11.sp)}
            Text(event.title,fontSize=17.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(vertical=8.dp));Text(event.detail,color=Muted,fontSize=12.sp,lineHeight=18.sp)
        }
    }
}
@Composable private fun Recovery(stage:Int,advance:(Int)->Unit){
    Heading(if(stage==3)"Back to a good evening." else "Immich recovery preview","SIMULATED ACTION / NO CLUSTER WRITES")
    when(stage){
        0->{Panel("Restart immich/immich-server?","May interrupt active requests. This demo does not submit an action.",Apricot,Color(0xFF452613));Spacer(Modifier.height(16.dp));Button(onClick={advance(1)}){Text("Confirm demo restart")}}
        else->{listOf("Restart submitted","Workload ready","HTTP check passed").forEachIndexed{i,label->
            val complete=stage>i
            val color by animateColorAsState(if(complete)Mint else Muted,label="recovery")
            Row(Modifier.fillMaxWidth().padding(vertical=15.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){Text(if(complete)"✓" else "○",color=color);Text(label,color=color)}
        };Panel(if(stage==3)"Recovery verified · Demo" else "Recovery not yet verified",if(stage==3)"Both readiness and HTTP checks passed in this example." else "An accepted action is not proof of recovery.");Spacer(Modifier.height(14.dp));if(stage<3)Button(onClick={advance(stage+1)}){Text(if(stage==1)"Simulate workload readiness" else "Simulate HTTP recovery")}}
    }
}
