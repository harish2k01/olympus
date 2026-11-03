package dev.olympus.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.olympus.desk.data.LiveRepository
import kotlin.math.roundToInt

@Composable
internal fun ClusterOverview(
 openHistory: (String, String) -> Unit,
 openNode: (String) -> Unit
) {
 var metric by rememberSaveable { mutableStateOf("cpu") }
 @Composable fun metricOverview() {
  MetricHistoryPanel(
   "kind=cluster-$metric",
   openExpanded = { range -> openHistory(metric, range) },
   clusterMetric = metric,
   changeClusterMetric = { metric = it }
  )
 }
 if (wideWindow()) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
   Column(Modifier.weight(1.15f)) {
    metricOverview()
   }
   Column(Modifier.weight(1f)) { ClusterNodes(openNode) }
  }
 } else {
  metricOverview()
  ClusterNodes(openNode)
 }
}

@Composable
internal fun ClusterFilter(label: String, selected: Boolean, onClick: () -> Unit) {
 Box(
  Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(20.dp))
   .background(if (selected) DeskColors.blue.copy(alpha = .19f) else DeskColors.raised)
   .selectable(selected = selected, role = Role.Tab, onClick = onClick)
   .padding(horizontal = 12.dp),
  contentAlignment = Alignment.Center
 ) {
  Text(label, color = if (selected) DeskColors.blue else DeskColors.text,
   fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
 }
}

internal fun clusterPercentage(value: Float?): String =
 value?.takeIf { it.isFinite() }?.let { "${it.roundToInt()}%" } ?: "—"

@Composable
internal fun ClusterResourceBar(value: Float?, modifier: Modifier = Modifier) {
 LinearProgressIndicator(
  progress = { ((value ?: 0f) / 100f).coerceIn(0f, 1f) },
  modifier = modifier.height(8.dp),
  color = if ((value ?: 0f) >= 75f) DeskColors.apricot else DeskColors.blue,
  trackColor = DeskColors.raised,
  drawStopIndicator = {}
 )
}

@Composable
private fun ClusterNodes(open: (String) -> Unit) {
 CompanionPanel("Nodes") {
  val nodes = LiveRepository.nodes
  if (nodes.isEmpty()) Text("Node readings unavailable", color = DeskColors.muted, fontSize = 13.sp)
  nodes.forEachIndexed { index, node ->
   val cpu = node.cpuPercent.takeIf { node.available }
   val memory = if (node.available && node.memoryTotalGiB > 0) node.memoryUsedGiB / node.memoryTotalGiB * 100 else null
   val verdict = when {
    !node.ready -> Verdict.Unknown
    (memory ?: 0f) >= 75f -> Verdict.Attention
    else -> Verdict.Calm
   }
   Row(
    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { open(node.name) },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
   ) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
     Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
      Text(node.name, color = DeskColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
       maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
      VerdictMark(verdict, 11.dp)
      Spacer(Modifier.weight(1f))
      val columnWidth = with(androidx.compose.ui.platform.LocalDensity.current) { 60.sp.toDp() }
      val valueColor = DeskColors.text
      listOf("CPU" to cpu, "MEM" to memory).forEach { (label, value) ->
       Text(androidx.compose.ui.text.buildAnnotatedString {
        append("$label ")
        pushStyle(androidx.compose.ui.text.SpanStyle(fontSize = 14.sp, color = valueColor))
        append(clusterPercentage(value))
        pop()
       }, Modifier.width(columnWidth),
        color = DeskColors.muted, fontSize = 12.sp, maxLines = 1,
        textAlign = androidx.compose.ui.text.style.TextAlign.End)
      }
     }
     Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      ClusterResourceBar(cpu, Modifier.weight(1f))
      ClusterResourceBar(memory, Modifier.weight(1f))
     }
    }
    Text("›", color = DeskColors.text, fontSize = 20.sp)
   }
   if (index < nodes.lastIndex) HorizontalDivider(color = DeskColors.raised)
  }
 }
}
