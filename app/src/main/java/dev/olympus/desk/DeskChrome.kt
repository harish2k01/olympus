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
