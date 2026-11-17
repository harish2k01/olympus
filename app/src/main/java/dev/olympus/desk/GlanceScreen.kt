package dev.olympus.desk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.olympus.desk.data.*
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Glance palette: always dark, tuned for an always-on panel read from a distance. */
internal object Glance {
    val ground = Color(0xFF0A0C0F)
    val surface = Color(0xFF13171C)
    val line = Color(0xFF1E242B)
    val text = Color(0xFFE8EAED)
    val muted = Color(0xFF9AA3AD)
    val faint = Color(0xFF5D6670)
    val ok = Color(0xFF3DD68C)
    val warn = Color(0xFFF5A524)
    val warnText = Color(0xFFF5C26B)
    val warnSurface = Color(0xFF2A2010)
    val crit = Color(0xFFFF6B5E)
    val info = Color(0xFF6FA8FF)
    val critText = Color(0xFFFF8A7E)
    val critSurface = Color(0xFF2A1412)
    val unknown = Color(0xFFC4CAD1)
    val night = Color(0xFF7C8189)
}

/** Night window: the glance drops to a dim clock between these hours. */
internal fun isNightHour(hour: Int, start: Int = 23, end: Int = 7): Boolean =
    if (start <= end) hour in start until end else hour >= start || hour < end

internal fun glanceHeadline(state: VerdictState): String = when (state.verdict) {
    Verdict.Calm -> "All clear"
    Verdict.Unknown -> "Unknown"
    else -> if (state.incidents.size == 1) "1 needs you" else "${state.incidents.size} need you"
}

private fun glanceAccent(verdict: Verdict) = when (verdict) {
    Verdict.Calm -> Glance.ok
    Verdict.Attention -> Glance.warnText
    Verdict.Critical -> Glance.critText
    Verdict.Unknown -> Glance.unknown
}

private fun isCritical(event: LabEvent) = event.kind.equals("critical", ignoreCase = true)

private fun ageLabel(event: LabEvent, now: java.time.Instant): String {
    val seconds = observationAgeSeconds(event.time.ifBlank { event.received }, now) ?: return ""
    return when {
        seconds < 60 -> "now"
        seconds < 3600 -> "${seconds / 60} min"
        seconds < 86_400 -> "${seconds / 3600} h"
        else -> "${seconds / 86_400} d"
    }
}

@Composable
private fun GlanceMark(verdict: Verdict, size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        when (verdict) {
            Verdict.Calm -> {
                drawCircle(Color(0xFF123526))
                drawPath(Path().apply {
                    moveTo(s * .29f, s * .52f); lineTo(s * .42f, s * .65f); lineTo(s * .71f, s * .35f)
                }, Glance.ok, style = Stroke(width = s * .09f))
            }
            Verdict.Unknown -> drawCircle(Glance.muted, radius = s * .44f,
                style = Stroke(width = s * .06f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * .12f, s * .1f))))
            else -> {
                val color = if (verdict == Verdict.Critical) Glance.crit else Glance.warn
                drawPath(Path().apply { moveTo(s / 2, s * .1f); lineTo(s * .94f, s * .88f); lineTo(s * .06f, s * .88f); close() }, color)
                drawLine(Glance.ground, Offset(s / 2, s * .38f), Offset(s / 2, s * .6f), strokeWidth = s * .08f)
                drawCircle(Glance.ground, radius = s * .055f, center = Offset(s / 2, s * .73f))
            }
        }
    }
}

/** Severity dot: circle for critical, square for warning, so state never rests on colour alone. */
@Composable
private fun SeverityDot(critical: Boolean) {
    Box(Modifier.size(12.dp).clip(if (critical) RoundedCornerShape(6.dp) else RoundedCornerShape(2.dp))
        .background(if (critical) Glance.crit else Glance.warn))
}

@Composable
private fun Vital(label: String, value: String, of: String?, stale: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape)
            .then(if (stale) Modifier.border(1.dp, Color(0xFF3A424B), shape) else Modifier.background(Glance.surface))
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .semantics { contentDescription = "$label $value${of?.let { " of $it" } ?: ""}" },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label.uppercase(), color = Glance.muted, fontSize = 13.sp, letterSpacing = .5.sp)
        Text(buildAnnotatedString {
            append(value)
            if (of != null) withStyle(SpanStyle(color = Glance.faint)) { append("/$of") }
        }, color = if (stale) Glance.muted else Glance.text, fontSize = 38.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun IncidentCard(event: LabEvent, age: String, large: Boolean, open: () -> Unit) {
    val critical = isCritical(event)
    val sub = listOf(event.namespace, event.node.takeIf { it.isNotBlank() }?.let { "on $it" }, event.kind.lowercase())
        .filterNotNull().filter(String::isNotBlank).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (critical) Glance.critSurface else Glance.warnSurface)
            .clickable(role = Role.Button, onClickLabel = "Open incident", onClick = open)
            .padding(horizontal = 20.dp, vertical = if (large) 16.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SeverityDot(critical)
        Column(Modifier.weight(1f)) {
            Text(incidentTitle(event), color = Glance.text, fontSize = if (large) 24.sp else 20.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, color = if (critical) Color(0xFFE6B3AD) else Color(0xFFE3C79A),
                fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (age.isNotBlank()) Text(age, color = if (critical) Color(0xFFFFB3AB) else Glance.warnText, fontSize = 22.sp)
    }
}

private data class GlanceFigures(
    val nodesReady: String, val nodesTotal: String,
    val appsSynced: String, val appsTotal: String,
    val servicesUp: String, val servicesTotal: String,
    val cpu: String, val memory: String
)

private fun figures(): GlanceFigures {
    val nodes = LiveRepository.nodes
    val apps = LiveRepository.services
    fun pct(v: Float?) = v?.let { "${it.toInt()}%" } ?: "—"
    return GlanceFigures(
        if (nodes.isEmpty()) "—" else nodes.count { it.ready }.toString(), nodes.size.takeIf { it > 0 }?.toString() ?: "—",
        if (apps.isEmpty()) "—" else apps.count { it.health == "Healthy" && it.sync == "Synced" }.toString(), apps.size.takeIf { it > 0 }?.toString() ?: "—",
        LiveRepository.monitorsUp?.toString() ?: "—", LiveRepository.monitorsTotal?.toString() ?: "—",
        pct(LiveRepository.cpuPercent), pct(LiveRepository.memoryPercent)
    )
}

private fun liveLine(now: java.time.Instant): Pair<Boolean, String> {
    val live = LiveRepository.state.startsWith("Live")
    val age = observationAgeSeconds(LiveRepository.time, now)
    return if (live) true to "Live${age?.let { " · ${formatObservationAge(it)} ago" } ?: ""}"
    else false to (age?.let { "Offline · figures ${formatObservationAge(it)} old" } ?: LiveRepository.state)
}

/**
 * The always-on glance. Landscape is the primary layout (Redmi Note 7 on its side, ~851×393 dp);
 * portrait stacks the same content. Tapping an incident opens it; tapping elsewhere opens Attention.
 */
@Composable
internal fun GlanceScreen(wide: Boolean, nightEnabled: Boolean, openIncident: (LabEvent) -> Unit, openAttention: () -> Unit) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = ZonedDateTime.now(); delay(1000L * (60 - now.second).coerceIn(1, 60)) } }
    val state = currentVerdict()
    val prefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("home-v3", 0)
    val shift = if (prefs.getBoolean("burnIn", true)) ((now.minute % 5) - 2).dp else 0.dp
    val wakeOnCritical = prefs.getBoolean("wakeOnCritical", true)
    val night = nightEnabled && isNightHour(now.hour) && !(wakeOnCritical && state.verdict == Verdict.Critical)
    val instant = now.toInstant()
    val activity = androidx.activity.compose.LocalActivity.current
    // At night the panel drops to near-minimum backlight; a critical incident ends night mode and restores it.
    DisposableEffect(night, activity) {
        val window = activity?.window
        window?.attributes = window?.attributes?.apply { screenBrightness = if (night) 0.01f else -1f }
        onDispose { window?.attributes = window?.attributes?.apply { screenBrightness = -1f } }
    }
    if (night) {
        NightGlance(now, state, Modifier.offset(x = shift, y = -shift), openAttention)
        return
    }
    val clock = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))
    val stale = state.verdict == Verdict.Unknown
    val f = figures()
    val (live, liveText) = liveLine(instant)
    val sub = when (state.verdict) {
        Verdict.Calm -> "Nothing needs you"
        Verdict.Unknown -> if (LiveRepository.state.startsWith("Live")) "Some sources unconfirmed" else "Can't reach the backend"
        else -> {
            val crit = state.incidents.count(::isCritical)
            listOfNotNull(crit.takeIf { it > 0 }?.let { "$it critical" },
                (state.incidents.size - crit).takeIf { it > 0 }?.let { "$it warning" }).joinToString(" · ")
        }
    }

    @Composable fun Verdict(markSize: Dp, headSize: TextUnit) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.semantics(mergeDescendants = true) {}) {
            GlanceMark(state.verdict, markSize)
            Column {
                Text(glanceHeadline(state), color = glanceAccent(state.verdict), fontSize = headSize, fontWeight = FontWeight.Bold, lineHeight = headSize)
                Text(sub, color = Glance.muted, fontSize = 15.sp)
            }
        }
    }

    @Composable fun Vitals(modifier: Modifier) {
        Column(modifier.alpha(if (stale) .55f else 1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Vital("Nodes", f.nodesReady, f.nodesTotal, stale, Modifier.weight(1f).fillMaxHeight())
                Vital("Apps synced", f.appsSynced, f.appsTotal, stale, Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Vital("Services up", f.servicesUp, f.servicesTotal, stale, Modifier.weight(1f).fillMaxHeight())
                val restarts = LiveRepository.restarts24h
                if (restarts != null) Vital("Restarts · 24 h", restarts.toString(), null, stale, Modifier.weight(1f).fillMaxHeight())
                else Vital("Memory", f.memory, null, stale, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }

    @Composable fun Incidents(modifier: Modifier, max: Int) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.incidents.take(max).forEach { IncidentCard(it, ageLabel(it, instant), state.incidents.size <= 2) { openIncident(it) } }
            if (state.incidents.size > max) Text("+ ${state.incidents.size - max} more", color = Glance.muted, fontSize = 15.sp)
        }
    }

    @Composable fun Strip() {
        Row(Modifier.fillMaxWidth().background(Glance.ground).padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                .then(if (live) Modifier.background(Glance.ok) else Modifier.border(2.dp, Glance.muted, RoundedCornerShape(4.dp))))
            Text(liveText, color = Glance.muted, fontSize = 14.sp, modifier = Modifier.padding(start = 0.dp))
            Text("CPU ${f.cpu}", color = Glance.muted, fontSize = 14.sp)
            Text("Memory ${f.memory}", color = Glance.muted, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            val later = LiveRepository.later.size
            Text(if (later > 0) "$later for later · tap for details" else "Tap for details", color = Glance.muted, fontSize = 14.sp)
        }
    }

    Column(
        Modifier.fillMaxSize().background(Glance.ground)
            .clickable(role = Role.Button, onClickLabel = "Open attention list", onClick = openAttention)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .offset(x = shift, y = -shift)
            .padding(start = 32.dp, end = 32.dp, top = if (wide) 24.dp else 48.dp, bottom = 16.dp)
    ) {
        if (wide) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                val calm = state.incidents.isEmpty()
                Column(Modifier.width(if (calm) 370.dp else 300.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(clock, color = Glance.text, fontSize = if (calm) 104.sp else 80.sp, lineHeight = if (calm) 100.sp else 78.sp, fontWeight = FontWeight.SemiBold)
                        Text(date, color = Glance.muted, fontSize = 19.sp)
                    }
                    Verdict(if (calm) 52.dp else 46.dp, if (calm) 44.sp else 40.sp)
                }
                if (calm) Vitals(Modifier.weight(1f).fillMaxHeight()) else Incidents(Modifier.weight(1f), 3)
            }
        } else {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text(clock, color = Glance.text, fontSize = 92.sp, lineHeight = 90.sp, fontWeight = FontWeight.SemiBold)
                    Text(date, color = Glance.muted, fontSize = 18.sp)
                }
                Verdict(44.dp, 38.sp)
                if (state.incidents.isNotEmpty()) Incidents(Modifier.fillMaxWidth(), 2)
                Vitals(Modifier.fillMaxWidth().height(if (state.incidents.isEmpty()) 240.dp else 200.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Glance.line))
        Strip()
    }
}

@Composable
private fun NightGlance(now: ZonedDateTime, state: VerdictState, modifier: Modifier, wake: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(role = Role.Button, onClickLabel = "Open attention list", onClick = wake)) {
        Column(modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Glance.night, fontSize = 116.sp, lineHeight = 116.sp, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlanceMark(state.verdict, 14.dp)
                Text(glanceHeadline(state), color = Glance.night, fontSize = 18.sp)
            }
        }
    }
}
