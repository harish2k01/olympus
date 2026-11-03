package dev.olympus.desk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.olympus.desk.data.incidentTitle
import dev.olympus.desk.data.isInformational
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.olympus.desk.data.*
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal enum class Verdict(val label: String) {
    Calm("All calm"), Attention("Needs you"), Critical("Critical"), Unknown("Can’t confirm");

    val accent: Color @Composable get() = when (this) {
        Calm -> DeskColors.blue
        Attention -> DeskColors.apricot
        Critical -> DeskColors.critical
        Unknown -> DeskColors.muted
    }
}

internal data class VerdictState(val verdict: Verdict, val incidents: List<LabEvent>)

internal fun resolveVerdict(
    connection: String,
    sources: Collection<Boolean>,
    events: List<LabEvent>
): VerdictState {
    if (!connection.startsWith("Live")) return VerdictState(Verdict.Unknown, emptyList())
    val firing = events.filter { it.state == "Firing" && !isInformational(it.kind) }
        .sortedByDescending { it.kind.equals("critical", ignoreCase = true) }
        .distinctBy { it.id.ifBlank { "${it.source}/${it.resource}/${it.title}" } }
    val verdict = when {
        firing.firstOrNull()?.kind.equals("critical", ignoreCase = true) -> Verdict.Critical
        firing.isNotEmpty() -> Verdict.Attention
        connection == "Live" && sources.isNotEmpty() && sources.all { it } &&
            events.none { it.state == "Unknown" } -> Verdict.Calm
        else -> Verdict.Unknown
    }
    return VerdictState(verdict, firing)
}

internal fun currentVerdict(): VerdictState {
    val now = java.time.Instant.now()
    val verifiedSources = LiveRepository.signals.map { (name, available) ->
        val age = LiveRepository.observedAt[name]?.let { observationAgeSeconds(it, now) }
        available && classifyFreshness(age) != Freshness.Stale
    }
    return resolveVerdict(LiveRepository.state, verifiedSources, LiveRepository.active)
}

@Composable
internal fun VerdictMark(verdict: Verdict) {
    val accent = verdict.accent
    Canvas(Modifier.size(12.dp)) {
        val side = size.minDimension
        when (verdict) {
            Verdict.Calm -> drawCircle(accent)
            Verdict.Unknown -> drawCircle(
                accent, radius = side * .40f,
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)))
            )
            Verdict.Attention, Verdict.Critical -> drawPath(Path().apply {
                moveTo(side / 2, 0f)
                lineTo(side, if (verdict == Verdict.Critical) side / 2 else side)
                if (verdict == Verdict.Critical) lineTo(side / 2, side)
                lineTo(0f, if (verdict == Verdict.Critical) side / 2 else side)
                close()
            }, accent)
        }
    }
}

@Composable
internal fun AmbientDesk(state: VerdictState, openHome: () -> Unit) {
    CompositionLocalProvider(LocalDeskPalette provides DarkDesk) {
        var time by remember { mutableStateOf(LocalTime.now()) }
        LaunchedEffect(Unit) {
            while (true) {
                time = LocalTime.now()
                delay((60 - time.second) * 1000L)
            }
        }
        val displacement = ((time.minute % 5) - 2).dp
        Column(
            Modifier.fillMaxSize().background(Color.Black)
                .clickable(role = Role.Button, onClickLabel = "Open Home", onClick = openHome)
                .offset(x = displacement, y = -displacement).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val ambientText = Color(0xFF8B94A3)
            Text(time.format(DateTimeFormatter.ofPattern("HH:mm")), color = ambientText, fontSize = 132.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                VerdictMark(state.verdict)
                val headline = when {
                    state.verdict != Verdict.Attention -> state.verdict.label
                    state.incidents.size == 1 -> "1 incident needs you"
                    else -> "${state.incidents.size} incidents need you"
                }
                Text(headline, color = if (state.incidents.isEmpty()) ambientText else state.verdict.accent,
                    fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
            state.incidents.firstOrNull()?.let {
                Text(incidentTitle(it), color = ambientText, fontSize = 14.sp, maxLines = 2)
            }
        }
    }
}
