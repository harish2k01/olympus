package dev.olympus.desk

import androidx.compose.foundation.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

internal object DeskColors {
 val background=Color(0xFF11151E);val surface=Color(0xFF222733);val raised=Color(0xFF2C3445)
 val text=Color(0xFFF1F2F8);val muted=Color(0xFFB3BBCF);val blue=Color(0xFF8D9EFF)
 val cobalt=Color(0xFF5268EF);val lilac=Color(0xFFC7BFED);val apricot=Color(0xFFEFBC99);val green=Color(0xFFB5DEC6)
}
@Composable internal fun DeskAction(label:String,primary:Boolean=false,enabled:Boolean=true,click:()->Unit){
 Box(Modifier.heightIn(min=40.dp).clip(RoundedCornerShape(11.dp)).background(if(primary)DeskColors.cobalt else DeskColors.raised).clickable(enabled=enabled,onClick=click).padding(horizontal=13.dp),contentAlignment=Alignment.Center){Text(label,color=if(enabled)DeskColors.text else DeskColors.muted.copy(alpha=.5f),fontSize=13.sp,fontWeight=FontWeight.Medium)}
}
@Composable internal fun DeskSegments(items:List<String>,selected:String,choose:(String)->Unit,refresh:(()->Unit)?=null){
 Row(Modifier.fillMaxWidth().height(40.dp).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
  Row(Modifier.clip(RoundedCornerShape(12.dp)).background(DeskColors.surface).padding(3.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)){
   items.forEach{label->Box(Modifier.height(32.dp).clip(RoundedCornerShape(9.dp)).background(if(label==selected)DeskColors.raised else Color.Transparent).selectable(selected=label==selected,role=Role.Tab,onClick={choose(label)}).padding(horizontal=12.dp),contentAlignment=Alignment.Center){Text(label,color=if(label==selected)DeskColors.text else DeskColors.muted,fontSize=12.sp)}}
  }
  if(refresh!=null)IconButton(onClick=refresh,modifier=Modifier.size(40.dp)){Text("↻",color=DeskColors.blue,fontSize=22.sp)}
 }
}
@Composable internal fun DeskDisclosure(title:String,initiallyExpanded:Boolean=false,content:@Composable ()->Unit){
 var expanded by remember(title){mutableStateOf(initiallyExpanded)}
 Column(Modifier.fillMaxWidth()){
  HorizontalDivider(color=DeskColors.muted.copy(alpha=.13f),modifier=Modifier.padding(top=8.dp))
  Row(Modifier.fillMaxWidth().heightIn(min=40.dp).clickable{expanded=!expanded},verticalAlignment=Alignment.CenterVertically){Text(title,color=DeskColors.text,fontSize=13.sp,modifier=Modifier.weight(1f));Text(if(expanded)"⌃"else"⌄",color=DeskColors.muted,fontSize=19.sp)}
  if(expanded)content()
 }
}
@Composable internal fun DeskSearch(value:String,change:(String)->Unit,label:String){
 Row(Modifier.fillMaxWidth().padding(vertical=4.dp).clip(RoundedCornerShape(12.dp)).background(DeskColors.surface).padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
  androidx.compose.foundation.text.BasicTextField(value=value,onValueChange=change,singleLine=true,textStyle=androidx.compose.ui.text.TextStyle(color=DeskColors.text,fontSize=14.sp),modifier=Modifier.weight(1f),decorationBox={inner->Box{if(value.isBlank())Text(label,color=DeskColors.muted,fontSize=14.sp);inner()}})
 }
}
@Composable internal fun DeskPopup(expanded:Boolean,dismiss:()->Unit,items:List<Pair<String,()->Unit>>){if(expanded)Popup(alignment=Alignment.TopEnd,onDismissRequest=dismiss,properties=PopupProperties(focusable=true)){
 Column(Modifier.width(228.dp).clip(RoundedCornerShape(16.dp)).background(DeskColors.raised).border(1.dp,DeskColors.muted.copy(alpha=.12f),RoundedCornerShape(16.dp)).padding(6.dp)){
  items.forEach{(label,action)->Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).clickable{dismiss();action()}.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){Text(label,color=DeskColors.text,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}}
 }
}}

@Composable internal fun StatusPill(label:String,color:Color=DeskColors.green){
 Row(Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha=.10f)).padding(horizontal=8.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
  Box(Modifier.size(5.dp).background(color,RoundedCornerShape(3.dp)))
  Text(label,color=color,fontSize=11.sp,maxLines=1)
 }
}
@Composable internal fun DeskEmpty(title:String,detail:String=""){
 Column(Modifier.fillMaxWidth().padding(vertical=22.dp,horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
  Text(title,color=DeskColors.text,fontSize=15.sp,fontWeight=FontWeight.Medium)
  if(detail.isNotBlank())Text(detail,color=DeskColors.muted,fontSize=12.sp)
 }
}

@Composable internal fun DeskModal(title:String,dismiss:()->Unit,content:@Composable ColumnScope.()->Unit){
 androidx.compose.ui.window.Dialog(onDismissRequest=dismiss,properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
  ImmersiveDialogWindow()
  Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).padding(20.dp),contentAlignment=Alignment.Center){
   Column(Modifier.widthIn(max=420.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(DeskColors.surface).padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
    Text(title,color=DeskColors.text,fontSize=18.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
    content()
   }
  }
 }
}
