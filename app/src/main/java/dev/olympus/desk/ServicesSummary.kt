package dev.olympus.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.olympus.desk.data.LiveRepository
import dev.olympus.desk.data.MonitorState
import kotlin.math.roundToInt

/** Problems first, then slowest first: the order that answers "what is struggling?". */
internal fun monitorOrder(monitors: List<MonitorState>): List<MonitorState> =
    monitors.sortedWith(compareBy<MonitorState>({ when (it.status) { "Down" -> 0; "Unknown" -> 1; "Up" -> 3; else -> 2 } }, { -(it.responseMs ?: -1f) }))

/** Top of Services, as in the design: the struggling monitors at a glance, then media-stack counters. */
@Composable
internal fun ServicesSummary() {
    val monitors = monitorOrder(LiveRepository.monitors)
    val up = LiveRepository.monitorsUp
    val total = LiveRepository.monitorsTotal
    if (monitors.isEmpty() && LiveRepository.integrations.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (up != null && total != null) Text("$up of $total up · slowest first", color = DeskColors.muted, fontSize = 14.sp)
        val columns = if (wideWindow()) 3 else 2
        val shown = monitors.take(columns * 3)
        shown.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { m ->
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(DeskColors.surface).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val color = when (m.status) { "Up" -> if ((m.responseMs ?: 0f) > 500f) Glance.warn else Glance.ok; "Down" -> Glance.crit; else -> DeskColors.muted }
                        Box(Modifier.size(9.dp).clip(RoundedCornerShape(if (m.status == "Up" && (m.responseMs ?: 0f) <= 500f) 5.dp else 2.dp)).background(color))
                        Text(m.name, Modifier.weight(1f), color = DeskColors.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(m.responseMs?.let { "${it.roundToInt()} ms" } ?: m.status, color = DeskColors.muted, fontSize = 13.sp)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (monitors.size > shown.size) Text("+ ${monitors.size - shown.size} more below", color = DeskColors.muted, fontSize = 13.sp)

        fun sum(field: String, kinds: Set<String>) = LiveRepository.integrations.filter { it.kind in kinds }
            .mapNotNull { it.fields[field] }.takeIf { it.isNotEmpty() }?.sum()?.roundToInt()
        fun parts(field: String, kinds: Set<String>) = LiveRepository.integrations.filter { it.kind in kinds && it.fields[field] != null }
            .joinToString(" · ") { "${it.name} ${it.fields[field]!!.roundToInt()}" }
        val arr = setOf("sonarr", "radarr")
        val cards = listOfNotNull(
            sum("Queued", arr)?.let { Triple("Downloads queue", it.toString(), parts("Queued", arr)) },
            sum("Wanted", arr)?.let { Triple("Wanted", it.toString(), "missing episodes and movies") },
            sum("Indexers", setOf("prowlarr"))?.let { n -> Triple("Indexers", n.toString(), sum("Health issues", setOf("prowlarr"))?.takeIf { it > 0 }?.let { "$it health issues" } ?: "healthy") }
        )
        if (cards.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            cards.forEach { (label, value, sub) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(DeskColors.surface).padding(14.dp)) {
                    Text(label, color = DeskColors.muted, fontSize = 13.sp)
                    Text(value, color = DeskColors.text, fontFamily = SpaceGrotesk, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    Text(sub, color = DeskColors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
