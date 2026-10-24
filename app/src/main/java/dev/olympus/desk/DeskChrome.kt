package dev.olympus.desk

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.olympus.desk.data.LiveRepository
import kotlin.math.*

internal val LocalReducedMotion=staticCompositionLocalOf{false}

@Composable internal fun FloatingDock(selected:String,select:(String)->Unit){
 Row(Modifier.widthIn(max=410.dp).fillMaxWidth(.88f).shadow(18.dp,RoundedCornerShape(25.dp)).clip(RoundedCornerShape(25.dp)).background(Color(0xF52A3040)).border(1.dp,Color.White.copy(alpha=.12f),RoundedCornerShape(25.dp)).padding(5.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){
  listOf("Home" to "⌂","Nodes" to "▤","Apps" to "⊞","Monitors" to "◉","Events" to "◷").forEach{(name,icon)->
   
   Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(if(selected==name)Color(0xFF465DD1)else Color.Transparent).clickable{select(name)}.padding(vertical=7.dp),horizontalAlignment=Alignment.CenterHorizontally){
    LabGlyph(name,if(selected==name)Color.White else Color(0xFFBFC4D1),24.dp)
    Text(name,color=if(selected==name)Color.White else Color(0xFFBFC4D1),fontSize=10.sp)
   }
  }
 }
}
@Composable internal fun CompactTabs(items:List<String>,selected:String,select:(String)->Unit,refresh:(()->Unit)?=null){DeskSegments(items,selected,select,refresh)}

@Composable internal fun wideWindow()=LocalWindowInfo.current.containerSize.width/LocalDensity.current.density>650

/** Draws locally from the shared live snapshot; no requests or additional assets. */
@Composable internal fun ClusterMascot(modifier:Modifier=Modifier){
 val mood=clusterMood(LiveRepository.state.startsWith("Live"),LiveRepository.signals.isNotEmpty()&&LiveRepository.signals.values.all{it},LiveRepository.nodes.size,LiveRepository.nodes.count{!it.ready},LiveRepository.services.size,LiveRepository.services.count{it.health!="Healthy"||it.sync!="Synced"},LiveRepository.monitors.size,LiveRepository.monitors.count{it.status=="Down"},LiveRepository.active.count{it.state=="Firing"},LiveRepository.active.count{it.state=="Pending"},LiveRepository.active.count{it.state=="Unknown"},LiveRepository.active.count{it.state=="Firing"&&it.kind.lowercase() in listOf("critical","high","fatal")})
 val reduced=LocalReducedMotion.current
 var hover=0f;var phase=0f;var angle=0f
 if(!reduced){val motion=rememberInfiniteTransition(label="cluster_smiley")
  val floating by motion.animateFloat(-1.5f,1.5f,infiniteRepeatable(tween(2400,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="float")
  val blinking by motion.animateFloat(0f,1f,infiniteRepeatable(tween(5200,easing=LinearEasing)),label="blink")
  val orbit by motion.animateFloat(0f,360f,infiniteRepeatable(tween(if(mood==ClusterMood.Healthy)18000 else 24000,easing=LinearEasing)),label="orbit")
  hover=floating;phase=blinking;angle=orbit
 }
 val target=when(mood){ClusterMood.Healthy->14f;ClusterMood.Attention->5f;ClusterMood.Critical->1f;ClusterMood.Unknown->8f}
 val curve by animateFloatAsState(target,tween(if(reduced)0 else 450),label="expression")
 val accent by androidx.compose.animation.animateColorAsState(when(mood){ClusterMood.Healthy->DeskColors.green;ClusterMood.Attention->DeskColors.apricot;ClusterMood.Critical->Color(0xFFFFA8B8);ClusterMood.Unknown->DeskColors.lilac},tween(if(reduced)0 else 450),label="health_accent")
 val description=when(mood){ClusterMood.Healthy->"Cluster healthy";ClusterMood.Attention->"Cluster needs attention";ClusterMood.Critical->"Cluster has a failure";ClusterMood.Unknown->"Cluster status unconfirmed"}
 Canvas(modifier.semantics{contentDescription=description}){
  val u=size.minDimension/64f;val center=Offset(size.width/2,size.height/2+hover*u);val radius=28*u
  drawCircle(Color.White.copy(alpha=.17f),radius,center,style=androidx.compose.ui.graphics.drawscope.Stroke(.7f*u))
  repeat(LiveRepository.nodes.size.coerceIn(3,6)){i->val radians=(angle+i*360f/LiveRepository.nodes.size.coerceIn(3,6))*PI/180
   val point=Offset(center.x+cos(radians).toFloat()*radius,center.y+sin(radians).toFloat()*radius)
   drawCircle(if(LiveRepository.state.startsWith("Live")&&LiveRepository.nodes.getOrNull(i)?.ready==false)Color(0xFFFFA8B8)else accent,2.5f*u,point)
  }
  drawRoundRect(Color.White.copy(alpha=.17f),Offset(center.x-21*u,center.y-21*u),Size(42*u,42*u),androidx.compose.ui.geometry.CornerRadius(13*u))
  val blink=if(phase>.94f)abs(phase-.97f)/.03f else 1f
  listOf(-7f,7f).forEach{x->drawOval(Color.White,Offset(center.x+(x-2)*u,center.y-6*u),Size(4*u,(1+3*blink)*u))}
  if(mood==ClusterMood.Critical){drawLine(Color.White,Offset(center.x-10*u,center.y-12*u),Offset(center.x-4*u,center.y-10*u),1.4f*u);drawLine(Color.White,Offset(center.x+4*u,center.y-10*u),Offset(center.x+10*u,center.y-12*u),1.4f*u)}
  val mouth=Path().apply{moveTo(center.x-7*u,center.y+8*u);quadraticTo(center.x,center.y+curve*u,center.x+7*u,center.y+8*u)}
  drawPath(mouth,Color.White,style=androidx.compose.ui.graphics.drawscope.Stroke(1.6f*u))
 }
}
