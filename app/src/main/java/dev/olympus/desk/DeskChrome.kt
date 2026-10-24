package dev.olympus.desk

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 Row(Modifier.widthIn(max=360.dp).fillMaxWidth(.88f).shadow(18.dp,RoundedCornerShape(25.dp)).clip(RoundedCornerShape(25.dp)).background(Color(0xF52A3040)).border(1.dp,Color.White.copy(alpha=.12f),RoundedCornerShape(25.dp)).padding(5.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){
  listOf("Home" to "⌂","Nodes" to "▤","Events" to "◷","Apps" to "⊞").forEach{(name,icon)->
   val scale by animateFloatAsState(if(selected==name)1.04f else 1f,label="dock_selection")
   Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(if(selected==name)Color(0xFF465DD1)else Color.Transparent).clickable{select(name)}.padding(vertical=7.dp),horizontalAlignment=Alignment.CenterHorizontally){
    LabGlyph(name,if(selected==name)Color.White else Color(0xFFBFC4D1),24.dp)
    Text(name,color=if(selected==name)Color.White else Color(0xFFBFC4D1),fontSize=10.sp)
   }
  }
 }
}
@Composable internal fun CompactTabs(items:List<String>,selected:String,select:(String)->Unit,refresh:(()->Unit)?=null){
 Row(Modifier.fillMaxWidth().height(44.dp).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
  items.forEach{label->TextButton(onClick={select(label)},modifier=Modifier.height(34.dp).clip(RoundedCornerShape(10.dp)).background(if(label==selected)Color(0xFF394C9B)else Color(0xFF242832)),contentPadding=PaddingValues(horizontal=12.dp,vertical=0.dp)){Text(label,color=if(label==selected)Color.White else Color(0xFFBFC4D1),fontSize=12.sp)}}
  if(refresh!=null)TextButton(onClick=refresh,modifier=Modifier.size(36.dp),contentPadding=PaddingValues(0.dp)){Text("↻",color=Color(0xFF879BFF),fontSize=22.sp)}
 }
}

/** A pod buddy with orbiting nodes; animation stays local and never polls the cluster. */
@Composable internal fun ClusterMascot(){
 val reduced=LocalReducedMotion.current
 var hover=0f;var phase=0f;var angle=0f
 if(!reduced){val motion=rememberInfiniteTransition(label="pod_buddy");val floating by motion.animateFloat(-2f,2f,infiniteRepeatable(tween(2400,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="float");val blinking by motion.animateFloat(0f,1f,infiniteRepeatable(tween(5200,easing=LinearEasing)),label="blink");val orbit by motion.animateFloat(0f,360f,infiniteRepeatable(tween(18000,easing=LinearEasing)),label="node_orbit");hover=floating;phase=blinking;angle=orbit}
 val nodes=LiveRepository.nodes;val connected=LiveRepository.state.startsWith("Live");val happy=connected&&nodes.isNotEmpty()&&nodes.all{it.ready}&&LiveRepository.issues.isEmpty()
 Canvas(Modifier.size(64.dp)){
  val center=Offset(size.width/2,size.height/2+hover.dp.toPx());val radius=29.dp.toPx()
  drawCircle(Color.White.copy(alpha=.17f),radius,center,style=androidx.compose.ui.graphics.drawscope.Stroke(.7.dp.toPx()))
  repeat(nodes.size.coerceIn(3,6)){i->val radians=(angle+i*360f/nodes.size.coerceIn(3,6))*PI/180;val point=Offset(center.x+cos(radians).toFloat()*radius,center.y+sin(radians).toFloat()*radius);drawCircle(if(connected&&(nodes.getOrNull(i)?.ready!=false))Color(0xFFB4DFBE)else Color(0xFFEDB48F),2.5.dp.toPx(),point)}
  val side=45.dp.toPx();drawRoundRect(Color.White.copy(alpha=.17f),Offset(center.x-side/2,center.y-side/2),Size(side,side),androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()))
  val blink=if(phase>.94f)abs(phase-.97f)/.03f else 1f
  listOf(-7f,7f).forEach{x->drawOval(Color.White,Offset(center.x+x.dp.toPx()-2.dp.toPx(),center.y-6.dp.toPx()),Size(4.dp.toPx(),(1f+3f*blink).dp.toPx()))}
  val mouth=Path().apply{moveTo(center.x-7.dp.toPx(),center.y+8.dp.toPx());quadraticTo(center.x,center.y+(if(happy)14f else if(connected)4f else 8f).dp.toPx(),center.x+7.dp.toPx(),center.y+8.dp.toPx())};drawPath(mouth,Color.White,style=androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
 }
}

@Composable internal fun wideWindow()=LocalWindowInfo.current.containerSize.width/LocalDensity.current.density>650
