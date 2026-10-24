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
   "Home"->{path(3f to 10f,12f to 3f,21f to 10f);path(5f to 9f,5f to 21f,19f to 21f,19f to 9f);path(9f to 21f,9f to 14f,15f to 14f,15f to 21f)}
   "Nodes"->{listOf(4f,13f).forEach{y->path(3f to y,21f to y,21f to y+6,3f to y+6,close=true);drawCircle(color,1f*u,Offset(6*u,(y+3)*u));line(14f,y+3,18f,y+3)}}
   "Monitors"->{path(2f to 12f,6f to 12f,9f to 4f,13f to 20f,17f to 10f,21f to 10f)}
   "Events"->{val p=Path().apply{moveTo(5*u,17*u);quadraticTo(7*u,15*u,7*u,10*u);cubicTo(7*u,3*u,17*u,3*u,17*u,10*u);quadraticTo(17*u,15*u,19*u,17*u);close()};drawPath(p,color,style=Stroke(stroke));line(10f,21f,14f,21f);line(12f,2f,12f,4f)}
   "Apps"->{listOf(3f,14f).forEach{x->listOf(3f,14f).forEach{y->path(x to y,x+7 to y,x+7 to y+7,x to y+7,close=true)}}}
   "GitOps"->{line(6f,5f,6f,18f);path(6f to 14f,16f to 8f,16f to 5f);listOf(6f to 3f,16f to 3f,6f to 20f).forEach{(x,y)->drawCircle(color,2*u,Offset(x*u,y*u),style=Stroke(stroke))}}
   "Back"->path(15f to 4f,7f to 12f,15f to 20f)
   "Settings"->{drawCircle(color,7*u,Offset(12*u,12*u),style=Stroke(stroke));drawCircle(color,2.5f*u,Offset(12*u,12*u),style=Stroke(stroke));repeat(8){i->val a=i*PI/4;line((12+7*cos(a)).toFloat(),(12+7*sin(a)).toFloat(),(12+10*cos(a)).toFloat(),(12+10*sin(a)).toFloat())}}
   else->{val points=(0..6).map{i->val a=-PI/2+i*2*PI/7;((12+10*cos(a)).toFloat() to (12+10*sin(a)).toFloat())};path(*points.toTypedArray(),close=true);path(7f to 12f,12f to 8f,17f to 12f,17f to 17f,7f to 17f,close=true);repeat(3){i->drawCircle(color,1*u,Offset((9+3*i)*u,14*u))}}
  }
 }
}
