package dev.olympus.desk

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
private data class ChangeMarker(val name: String, val time: Long, val sync: Boolean)
private data class HistoricalFrame(val start:Long,val end:Long,val step:Long,val series:List<HistoricalSeries>)

/** A missing sample or missing interval must never become a continuous healthy line. */
internal fun historyEdges(points:List<MetricSample>,step:Long):List<Pair<MetricSample,MetricSample>> =
 points.zipWithNext().filter{(a,b)->a.value?.isFinite()==true&&b.value?.isFinite()==true&&b.time>a.time&&b.time-a.time<=step*3/2}

@Composable internal fun MetricHistoryPanel(
 query: String,
 initialRange: String = "1h",
 openExpanded: ((String) -> Unit)? = null,
 clusterMetric: String? = query.substringAfter("kind=cluster-", "").takeIf { it in listOf("cpu", "memory") },
 changeClusterMetric: ((String) -> Unit)? = null
) {
 val context=LocalContext.current
 var range by rememberSaveable(query){mutableStateOf(initialRange)}
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
 if (clusterMetric != null) {
  Row(Modifier.fillMaxWidth().padding(bottom = 4.dp).horizontalScroll(rememberScrollState()),
   horizontalArrangement = Arrangement.spacedBy(8.dp)) {
   changeClusterMetric?.let { select ->
    ClusterFilter("CPU", clusterMetric == "cpu") { select("cpu") }
    ClusterFilter("Memory", clusterMetric == "memory") { select("memory") }
   }
   listOf("1h", "6h", "24h").forEach { option ->
    ClusterFilter(option.replace("h", " h"), option == range) { range = option }
   }
  }
 }
 val expandedCluster = clusterMetric != null && openExpanded == null
 Column(Modifier.fillMaxWidth().padding(vertical=4.dp).clip(RoundedCornerShape(if (clusterMetric == null) 18.dp else 24.dp))
  .background(DeskColors.surface).padding(if (clusterMetric == null) 10.dp else 14.dp),
  verticalArrangement=Arrangement.spacedBy(5.dp)){
  if (clusterMetric != null) {
   val cpu = clusterMetric == "cpu"
   val current = if (cpu) LiveRepository.cpuPercent else LiveRepository.memoryPercent
   val cached = current != null && !LiveRepository.state.startsWith("Live")
   Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
    Text(clusterPercentage(current), color = DeskColors.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
    if (current != null) {
     val capacity = if (cpu) "of ${LiveRepository.totalCores} logical cores" else
      "${LiveRepository.usedMemory?.let { "%.1f".format(it) }} of ${LiveRepository.totalMemory?.let { "%.1f".format(it) }} GiB"
     Text(capacity, color = DeskColors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
   }
   val updated = frame?.let { " · ${localTime(Instant.ofEpochSecond(it.end).toString())}" }.orEmpty()
   Text("Last ${range.replace("h", " h")}$updated${if (cached) " · cached reading" else ""}", color = DeskColors.muted, fontSize = 12.sp)
  }
  if (clusterMetric == null) DeskSegments(listOf("1h","6h","24h"),range,{range=it}){refresh++}
  when{
   loading->Text("Loading history…",color=DeskColors.muted,fontSize=12.sp)
   failed->{Text("History unavailable",color=DeskColors.apricot,fontSize=13.sp);DeskAction("Retry"){refresh++}}
   else -> frame?.let { data ->
    val (markerSeries, readings) = data.series.partition { it.unit.endsWith("-marker") }
    val markers = changeMarkers(markerSeries)
    if (wideWindow() && readings.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
     readings.forEach { series -> Box(Modifier.weight(1f)) { HistoricalChart(series, data, if (expandedCluster || clusterMetric == null) markers else emptyList(), openExpanded?.let { open -> { open(range) } }, clusterMetric != null) } }
    } else readings.forEach { HistoricalChart(it, data, if (expandedCluster || clusterMetric == null) markers else emptyList(), openExpanded?.let { open -> { open(range) } }, clusterMetric != null) }
    if (openExpanded == null && markerSeries.isNotEmpty()) {
     Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      markerSeries.forEach { series ->
       Row(Modifier.clip(RoundedCornerShape(16.dp)).background(DeskColors.raised).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("▲", color = if (series.unit == "sync-marker") DeskColors.blue else DeskColors.apricot, fontSize = 11.sp)
        Text(series.name, color = DeskColors.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
       }
      }
     }
    }
   }
  }
 }
 if (openExpanded == null) frame?.let { data ->
  val markerSeries = data.series.filter { it.unit.endsWith("-marker") }
  if (markerSeries.isNotEmpty()) {
   CompanionPanel("In this range") {
    val markers = changeMarkers(markerSeries)
    if (markerSeries.all { series -> series.points.none { it.value != null } }) {
     Text("Change sources unavailable", color = DeskColors.muted, fontSize = 12.sp)
    } else if (markers.isEmpty()) {
     Text("No changes detected in the available samples", color = DeskColors.muted, fontSize = 12.sp)
    }
    markers.take(30).forEachIndexed { index, marker ->
     Row(Modifier.fillMaxWidth().heightIn(min = 42.dp), verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      VerdictMark(if (marker.sync) Verdict.Calm else Verdict.Attention)
      Column {
       Text(marker.name, color = DeskColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
       Text(localTime(Instant.ofEpochSecond(marker.time).toString()), color = DeskColors.muted, fontSize = 12.sp)
      }
     }
     if (index < minOf(markers.lastIndex, 29)) HorizontalDivider(color = DeskColors.raised)
    }
   }
   Text("Ticks show the first sample that detected a sync or restart.", color = DeskColors.muted, fontSize = 12.sp)
  }
 }
}

private fun changeMarkers(markerSeries: List<HistoricalSeries>): List<ChangeMarker> =
 markerSeries.flatMap { series ->
  series.points.filterIndexed { index, point ->
   (point.value ?: 0f) > 0f && (series.points.getOrNull(index - 1)?.value ?: 0f) <= 0f
  }.map { ChangeMarker(series.name, it.time, series.unit == "sync-marker") }
 }.sortedByDescending { it.time }

@Composable private fun HistoricalChart(series:HistoricalSeries,frame:HistoricalFrame, markers: List<ChangeMarker>, openExpanded: (() -> Unit)?, cluster: Boolean){
 val timeFormat=remember{DateTimeFormatter.ofPattern("dd MMM · HH:mm").withZone(ZoneId.systemDefault())}
 fun time(value:Long)=timeFormat.format(Instant.ofEpochSecond(value))
 val valid=series.points.filter{it.value?.isFinite()==true}
 var selection by remember(series){mutableStateOf<MetricSample?>(null)}
 val selected=selection ?: valid.lastOrNull().takeUnless { cluster }
 val state=series.unit=="state"
 fun reading(value:Float?)=when{
  value==null->"Unavailable"
  state->when(value.toInt()){0->"Down";1->"Up";2->"Pending";3->"Maintenance";else->"Unknown"}
  series.unit=="cores"->String.format(java.util.Locale.US,"%.3f cores",value)
  else->String.format(java.util.Locale.US,"%.1f %s",value,series.unit)
 }
 Column(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
  if (!cluster) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(series.name,color=DeskColors.text,fontSize=14.sp);Text(reading(selected?.value),color=DeskColors.lilac,fontSize=14.sp)}
  if(valid.isEmpty()){Text("No readings in this period",color=DeskColors.muted,fontSize=12.sp);return@Column}
  val top=if(series.unit=="%") maxOf(100f, valid.maxOf { it.value!! }) else (valid.maxOf{it.value!!}*1.1f).coerceAtLeast(.01f)
  if(!state && !cluster)Text("0–${reading(top)}",color=DeskColors.muted,fontSize=10.sp)
  val span=(frame.end-frame.start).coerceAtLeast(1).toFloat()
  val palette = DeskColors
  val chartHeight = if (state) 22.dp else if (wideWindow()) 86.dp
   else if (openExpanded != null) 90.dp else if (cluster) 104.dp else 190.dp
  Row(
   Modifier.fillMaxWidth().padding(vertical = if (cluster && openExpanded == null && !wideWindow()) 46.dp else 0.dp),
   verticalAlignment = Alignment.CenterVertically,
   horizontalArrangement = Arrangement.spacedBy(5.dp)
  ) {
  if (cluster) Column(Modifier.height(chartHeight + 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
   listOf(top, top / 2, 0f).forEach { Text(clusterPercentage(it), color = palette.muted, fontSize = 10.sp) }
  }
  Canvas(Modifier.weight(1f).height(chartHeight).semantics{contentDescription="${series.name}, ${reading((selected ?: valid.lastOrNull())?.value)}, ${selected?.let{time(it.time)}.orEmpty()}"}.then(if (openExpanded != null) Modifier.clickable(onClickLabel = "Open chart history", onClick = openExpanded)
   else Modifier.pointerInput(series, frame) { detectTapGestures { point ->
    val target = frame.start + (point.x / size.width * span).toLong()
    selection = series.points.minByOrNull { abs(it.time - target) }
   } })){
   fun x(at:Long)=(at-frame.start)/span*size.width
   fun y(value:Float)=size.height*(1-value.coerceIn(0f,top)/top)
   if(state)series.points.forEach{point->
    val color=when(point.value?.toInt()){0->Color(0xFFEF9DA5);1->palette.green;2->palette.apricot;3->palette.lilac;else->palette.raised}
    drawRect(color,Offset(x(point.time),0f),androidx.compose.ui.geometry.Size((x((point.time+frame.step).coerceAtMost(frame.end))-x(point.time)).coerceAtLeast(1f),size.height))
   }else{
    listOf(0f,.5f,1f).forEach{level->drawLine(palette.raised,Offset(0f,size.height*level),Offset(size.width,size.height*level),.5.dp.toPx())}
    historyEdges(series.points, frame.step).forEach { (a, b) ->
     val start = Offset(x(a.time), y(a.value!!))
     val end = Offset(x(b.time), y(b.value!!))
     drawPath(androidx.compose.ui.graphics.Path().apply {
      moveTo(start.x, size.height)
      lineTo(start.x, start.y)
      lineTo(end.x, end.y)
      lineTo(end.x, size.height)
      close()
     }, palette.blue.copy(alpha = .15f))
     drawLine(palette.blue, start, end, 2.dp.toPx())
    }
    markers.forEach { marker ->
     val at = x(marker.time)
     val color = if (marker.sync) palette.blue else palette.apricot
     val tip = 5.dp.toPx()
     drawLine(color, Offset(at, tip), Offset(at, size.height), 1.dp.toPx(),
      pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
     drawPath(androidx.compose.ui.graphics.Path().apply {
      moveTo(at, 0f)
      lineTo(at + tip, tip * 2)
      lineTo(at - tip, tip * 2)
      close()
     }, color)
    }
    selected?.value?.let{drawCircle(palette.lilac,3.dp.toPx(),Offset(x(selected.time),y(it)))}
   }
  }
  }
  if (!cluster) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(time(frame.start),color=DeskColors.muted,fontSize=10.sp);Text(time(frame.end),color=DeskColors.muted,fontSize=10.sp)}
  if(state)Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)){listOf("Up" to DeskColors.green,"Down" to Color(0xFFEF9DA5),"Pending" to DeskColors.apricot,"Maintenance" to DeskColors.lilac).forEach{(label,color)->Text(label,color=color,fontSize=11.sp)}}
  selected?.let{Text("Sample · ${time(it.time)} · ${reading(it.value)}",color=DeskColors.muted,fontSize=10.sp)}
 }
}
