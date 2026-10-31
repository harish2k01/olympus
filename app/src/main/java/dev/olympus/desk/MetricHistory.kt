package dev.olympus.desk

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.olympus.desk.data.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

internal data class MetricSample(val time:Long,val value:Float?)
private data class HistoricalSeries(val name:String,val unit:String,val points:List<MetricSample>)
private data class HistoricalFrame(val start:Long,val end:Long,val step:Long,val series:List<HistoricalSeries>)

/** A missing sample or missing interval must never become a continuous healthy line. */
internal fun historyEdges(points:List<MetricSample>,step:Long):List<Pair<MetricSample,MetricSample>> =
 points.zipWithNext().filter{(a,b)->a.value?.isFinite()==true&&b.value?.isFinite()==true&&b.time>a.time&&b.time-a.time<=step*3/2}

@Composable internal fun MetricHistoryPanel(query:String){
 val context=LocalContext.current
 var range by rememberSaveable(query){mutableStateOf("1h")}
 var refresh by remember{mutableIntStateOf(0)}
 var frame by remember(query){mutableStateOf<HistoricalFrame?>(null)}
 var failed by remember(query){mutableStateOf(false)}
 var loading by remember(query){mutableStateOf(true)}
 LaunchedEffect(query,range,refresh){
  loading=true;failed=false;frame=null
  try{
   val pair=Pairing.read(context)?:error("Pairing required")
   val json=withContext(Dispatchers.IO){JSONObject(LiveRepository.request(pair,"/v1/metrics/history?$query&range=$range"))}
   val list=json.getJSONArray("series")
   val series=(0 until list.length()).map{i->val item=list.getJSONObject(i);val values=item.getJSONArray("points");HistoricalSeries(item.getString("name"),item.getString("unit"),(0 until values.length()).map{j->val point=values.getJSONObject(j);MetricSample(point.getLong("time"),if(point.isNull("value"))null else point.optDouble("value").toFloat().takeIf{it.isFinite()})})}
   frame=HistoricalFrame(json.getLong("start"),json.getLong("end"),json.getLong("step"),series)
  }catch(e:CancellationException){throw e}catch(_:Exception){failed=true}finally{loading=false}
 }
 Column(Modifier.fillMaxWidth().padding(vertical=4.dp).clip(RoundedCornerShape(18.dp)).background(DeskColors.surface).padding(10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
  DeskSegments(listOf("1h","6h","24h"),range,{range=it}){refresh++}
  when{
   loading->Text("Loading history…",color=DeskColors.muted,fontSize=12.sp)
   failed->{Text("History unavailable",color=DeskColors.apricot,fontSize=13.sp);DeskAction("Retry"){refresh++}}
   else->frame?.let{data->if(wideWindow())Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){data.series.forEach{series->Box(Modifier.weight(1f)){HistoricalChart(series,data)}}}else data.series.forEach{series->HistoricalChart(series,data)}}
  }
 }
}

@Composable private fun HistoricalChart(series:HistoricalSeries,frame:HistoricalFrame){
 val timeFormat=remember{DateTimeFormatter.ofPattern("dd MMM · HH:mm").withZone(ZoneId.systemDefault())}
 fun time(value:Long)=timeFormat.format(Instant.ofEpochSecond(value))
 val valid=series.points.filter{it.value?.isFinite()==true}
 var selection by remember(series){mutableStateOf<MetricSample?>(null)}
 val selected=selection?:valid.lastOrNull()
 val state=series.unit=="state"
 fun reading(value:Float?)=when{
  value==null->"Unavailable"
  state->when(value.toInt()){0->"Down";1->"Up";2->"Pending";3->"Maintenance";else->"Unknown"}
  series.unit=="cores"->String.format(java.util.Locale.US,"%.3f cores",value)
  else->String.format(java.util.Locale.US,"%.1f %s",value,series.unit)
 }
 Column(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(series.name,color=DeskColors.text,fontSize=14.sp);Text(reading(selected?.value),color=DeskColors.lilac,fontSize=14.sp)}
  if(valid.isEmpty()){Text("No readings in this period",color=DeskColors.muted,fontSize=12.sp);return@Column}
  val top=if(series.unit=="%")100f else (valid.maxOf{it.value!!}*1.1f).coerceAtLeast(.01f)
  if(!state)Text("0–${reading(top)}",color=DeskColors.muted,fontSize=10.sp)
  val span=(frame.end-frame.start).coerceAtLeast(1).toFloat()
  Canvas(Modifier.fillMaxWidth().height(if(state)22.dp else if(wideWindow())72.dp else 110.dp).semantics{contentDescription="${series.name}, ${reading(selected?.value)}, ${selected?.let{time(it.time)}.orEmpty()}"}.pointerInput(series,frame){detectTapGestures{point->val target=frame.start+(point.x/size.width*span).toLong();selection=series.points.minByOrNull{abs(it.time-target)}}}){
   fun x(at:Long)=(at-frame.start)/span*size.width
   fun y(value:Float)=size.height*(1-value.coerceIn(0f,top)/top)
   if(state)series.points.forEach{point->
    val color=when(point.value?.toInt()){0->Color(0xFFEF9DA5);1->DeskColors.green;2->DeskColors.apricot;3->DeskColors.lilac;else->DeskColors.raised}
    drawRect(color,Offset(x(point.time),0f),androidx.compose.ui.geometry.Size((x((point.time+frame.step).coerceAtMost(frame.end))-x(point.time)).coerceAtLeast(1f),size.height))
   }else{
    listOf(0f,.5f,1f).forEach{level->drawLine(DeskColors.raised,Offset(0f,size.height*level),Offset(size.width,size.height*level),.5.dp.toPx())}
    historyEdges(series.points,frame.step).forEach{(a,b)->drawLine(DeskColors.blue,Offset(x(a.time),y(a.value!!)),Offset(x(b.time),y(b.value!!)),1.6.dp.toPx())}
    selected?.value?.let{drawCircle(DeskColors.lilac,3.dp.toPx(),Offset(x(selected.time),y(it)))}
   }
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(time(frame.start),color=DeskColors.muted,fontSize=10.sp);Text(time(frame.end),color=DeskColors.muted,fontSize=10.sp)}
  if(state)Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)){listOf("Up" to DeskColors.green,"Down" to Color(0xFFEF9DA5),"Pending" to DeskColors.apricot,"Maintenance" to DeskColors.lilac).forEach{(label,color)->Text(label,color=color,fontSize=11.sp)}}
  selected?.let{Text("Sample · ${time(it.time)}",color=DeskColors.muted,fontSize=10.sp)}
 }
}
