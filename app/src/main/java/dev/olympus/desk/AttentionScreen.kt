package dev.olympus.desk

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.olympus.desk.data.*

/** Firing incidents that are not snoozed, most severe first. */
internal fun nowIncidents(): List<LabEvent> = currentVerdict().incidents

internal fun snoozedIncidents(): List<LabEvent> =
    LiveRepository.active.filter { it.state == "Firing" && Snoozes.isSnoozed(it) }

/**
 * Attention: what needs you now, chores for later, what you snoozed, and the full history.
 * Now and Later share one look with the glance so walking up to the phone feels continuous.
 */
@Composable
internal fun AttentionScreen(deskPane: Boolean = false, open: (LabEvent) -> Unit) {
    val now = nowIncidents()
    val later = LiveRepository.later
    val snoozed = snoozedIncidents()
    var section by rememberSaveable { mutableStateOf("Now") }
    val labels = listOf("Now" to now.size, "Later" to later.size, "Snoozed" to snoozed.size, "History" to null)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DeskSegments(labels.map { (name, count) -> if (count == null) name else "$name · $count" },
            labels.first { it.first == section }.let { (name, count) -> if (count == null) name else "$name · $count" },
            { choice -> section = choice.substringBefore(" ·") })
        when (section) {
            "Now" -> if (now.isEmpty()) AttentionEmpty("Nothing needs you", if (later.isEmpty()) "No chores waiting either." else "${later.size} for later.")
                else now.forEach { AttentionIncidentRow(it, open) }
            "Later" -> if (later.isEmpty()) AttentionEmpty("No chores", "Certificates, volumes, upgrades and dependency updates show up here before they become urgent.")
                else {
                    Text("Later won't change the glance colour.", color = DeskColors.muted, fontSize = 13.sp)
                    later.forEach { ChoreRow(it) }
                }
            "Snoozed" -> if (snoozed.isEmpty()) AttentionEmpty("Nothing snoozed", "Snooze an incident from its detail screen.")
                else snoozed.forEach { AttentionIncidentRow(it, open) }
            else -> NotificationInbox(deskPane = deskPane, open = open)
        }
    }
}

@Composable
private fun AttentionEmpty(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = DeskColors.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = DeskColors.muted, fontSize = 14.sp)
    }
}

@Composable
private fun AttentionIncidentRow(event: LabEvent, open: (LabEvent) -> Unit) {
    val critical = event.kind.equals("critical", true)
    val sub = listOf(event.namespace, event.node, event.kind.lowercase()).filter(String::isNotBlank).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DeskColors.surface)
            .clickable(role = Role.Button, onClickLabel = "Open incident") { open(event) }
            .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(if (critical) 6.dp else 2.dp)).background(if (critical) Glance.crit else Glance.warn))
        Column(Modifier.weight(1f)) {
            Text(incidentTitle(event), color = DeskColors.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, color = DeskColors.muted, fontSize = 13.sp, maxLines = 1)
        }
        if (Snoozes.isSnoozed(event)) Text("Snoozed", color = DeskColors.muted, fontSize = 13.sp)
        Text("Open", color = DeskColors.blue, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ChoreRow(chore: Chore) {
    val context = LocalContext.current
    val link = chore.link.takeIf { it.startsWith("https://") }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, DeskColors.raised, RoundedCornerShape(16.dp))
            .then(if (link != null) Modifier.clickable(role = Role.Button, onClickLabel = "Open link") {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
            } else Modifier)
            .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(12.dp).border(2.dp, Glance.info, RoundedCornerShape(6.dp)))
        Column(Modifier.weight(1f)) {
            Text(chore.title, color = DeskColors.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (chore.detail.isNotBlank()) Text(chore.detail, color = DeskColors.muted, fontSize = 13.sp, maxLines = 2)
        }
        if (link != null) Text("Open ↗", color = DeskColors.blue, fontSize = 14.sp)
    }
}
