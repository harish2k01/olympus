package dev.olympus.desk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

/** Optical sizes and a shared stroke keep navigation independent of system fonts. */
@Composable internal fun LabGlyph(kind:String,color:Color,size:Dp=24.dp){
 Canvas(Modifier.size(size).semantics{contentDescription=kind}){
  val u=this.size.width/24f;val stroke=1.65f*u
  fun line(x:Float,y:Float,a:Float,b:Float){drawLine(color,Offset(x*u,y*u),Offset(a*u,b*u),stroke,StrokeCap.Round)}
  fun path(vararg points:Pair<Float,Float>,close:Boolean=false){val p=Path();points.forEachIndexed{i,(x,y)->if(i==0)p.moveTo(x*u,y*u)else p.lineTo(x*u,y*u)};if(close)p.close();drawPath(p,color,style=Stroke(stroke,cap=StrokeCap.Round))}
  when(kind){
   "Clock"->{drawCircle(color,8*u,Offset(12*u,12*u),style=Stroke(stroke));path(12f to 7f,12f to 12f,16f to 14f)}
   "CPU"->{path(6f to 6f,18f to 6f,18f to 18f,6f to 18f,close=true);path(9f to 9f,15f to 9f,15f to 15f,9f to 15f,close=true);listOf(8f,12f,16f).forEach{p->line(p,3f,p,6f);line(p,18f,p,21f);line(3f,p,6f,p);line(18f,p,21f,p)}}
   "Memory"->{path(3f to 7f,21f to 7f,21f to 17f,3f to 17f,close=true);path(6f to 10f,10f to 10f,10f to 14f,6f to 14f,close=true);path(14f to 10f,18f to 10f,18f to 14f,14f to 14f,close=true);listOf(5f,9f,13f,17f).forEach{p->line(p,17f,p,20f)}}
   "Attention"->{drawCircle(color,9*u,Offset(12*u,12*u),style=Stroke(stroke));line(12f,7f,12f,12f);drawCircle(color,1*u,Offset(12*u,16*u))}
   "Downloads"->{path(12f to 3f,12f to 15f);path(7f to 10f,12f to 15f,17f to 10f);path(4f to 16f,4f to 21f,20f to 21f,20f to 16f)}
   "Backups"->{path(4f to 5f,20f to 5f,20f to 20f,4f to 20f,close=true);path(8f to 12f,12f to 8f,16f to 12f);line(12f,9f,12f,16f)}

   "Brand"->{drawArc(color.copy(alpha=.65f),25f,285f,false,Offset(2*u,2*u),androidx.compose.ui.geometry.Size(20*u,20*u),style=Stroke(.8f*u,cap=StrokeCap.Round));path(6f to 11f,12f to 6f,18f to 11f);path(8f to 10f,8f to 18f,16f to 18f,16f to 10f);line(10f,13f,14f,13f);line(10f,16f,14f,16f);drawCircle(DeskColors.apricot,1.2f*u,Offset(20*u,6*u))}

   "Home"->{path(3f to 10f,12f to 3f,21f to 10f);path(5f to 9f,5f to 21f,19f to 21f,19f to 9f);path(9f to 21f,9f to 14f,15f to 14f,15f to 21f)}
   "Nodes"->{listOf(4f,13f).forEach{y->path(3f to y,21f to y,21f to y+6,3f to y+6,close=true);drawCircle(color,1f*u,Offset(6*u,(y+3)*u));line(14f,y+3,18f,y+3)}}
   "Monitors"->{path(2f to 12f,6f to 12f,9f to 4f,13f to 20f,17f to 10f,21f to 10f)}
   "Events"->{val p=Path().apply{moveTo(5*u,17*u);quadraticTo(7*u,15*u,7*u,10*u);cubicTo(7*u,3*u,17*u,3*u,17*u,10*u);quadraticTo(17*u,15*u,19*u,17*u);close()};drawPath(p,color,style=Stroke(stroke));line(10f,21f,14f,21f);line(12f,2f,12f,4f)}
   "Apps"->{listOf(3f,14f).forEach{x->listOf(3f,14f).forEach{y->path(x to y,x+7 to y,x+7 to y+7,x to y+7,close=true)}}}
   "GitOps"->{line(6f,5f,6f,18f);path(6f to 14f,16f to 8f,16f to 5f);listOf(6f to 3f,16f to 3f,6f to 20f).forEach{(x,y)->drawCircle(color,2*u,Offset(x*u,y*u),style=Stroke(stroke))}}
   "Back"->path(15f to 4f,7f to 12f,15f to 20f)
   "Edit"->{line(4f,6f,20f,6f);line(4f,12f,20f,12f);line(4f,18f,20f,18f);drawCircle(DeskColors.background,2*u,Offset(9*u,6*u));drawCircle(color,2*u,Offset(9*u,6*u),style=Stroke(stroke));drawCircle(color,2*u,Offset(15*u,12*u),style=Stroke(stroke));drawCircle(color,2*u,Offset(9*u,18*u),style=Stroke(stroke))}
   "Settings"->{drawCircle(color,7*u,Offset(12*u,12*u),style=Stroke(stroke));drawCircle(color,2.5f*u,Offset(12*u,12*u),style=Stroke(stroke));repeat(8){i->val a=i*PI/4;line((12+7*cos(a)).toFloat(),(12+7*sin(a)).toFloat(),(12+10*cos(a)).toFloat(),(12+10*sin(a)).toFloat())}}
   else->{val points=(0..6).map{i->val a=-PI/2+i*2*PI/7;((12+10*cos(a)).toFloat() to (12+10*sin(a)).toFloat())};path(*points.toTypedArray(),close=true);path(7f to 12f,12f to 8f,17f to 12f,17f to 17f,7f to 17f,close=true);repeat(3){i->drawCircle(color,1*u,Offset((9+3*i)*u,14*u))}}
  }
 }
}
