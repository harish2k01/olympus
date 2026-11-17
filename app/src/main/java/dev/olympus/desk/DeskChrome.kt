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
import dev.olympus.desk.data.isInformational
import kotlin.math.*

internal val LocalReducedMotion=staticCompositionLocalOf{false}

@Composable internal fun FloatingDock(selected:String,select:(String)->Unit){
 Row(Modifier.widthIn(max=340.dp).fillMaxWidth(.88f).shadow(18.dp,RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Color(0xF52A3040)).border(1.dp,Color.White.copy(alpha=.12f),RoundedCornerShape(22.dp)).padding(5.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){
  listOf("Home" to "Home","Cluster" to "Nodes","Services" to "Apps","Inbox" to "Events").forEach{(name,icon)->
   
   Column(Modifier.weight(1f).clip(RoundedCornerShape(17.dp)).background(if(selected==name)Color(0xFF465DD1)else Color.Transparent).clickable{select(name)}.heightIn(min=44.dp).padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally){
    LabGlyph(icon,if(selected==name)Color.White else Color(0xFFBFC4D1),20.dp)
    Text(name,color=if(selected==name)Color.White else Color(0xFFBFC4D1),fontSize=9.sp)
   }
  }
 }
}
@Composable internal fun CompactTabs(items:List<String>,selected:String,select:(String)->Unit,refresh:(()->Unit)?=null){DeskSegments(items,selected,select,refresh)}

@Composable internal fun wideWindow()=LocalWindowInfo.current.containerSize.width/LocalDensity.current.density>650


