package dev.olympus.desk

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.olympus.desk.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Snooze and the allowlisted restart for a firing incident. Restart always asks first. */
@Composable
internal fun IncidentActions(event: LabEvent, vertical: Boolean = false) {
    if (event.state != "Firing") return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val target = remember(event.namespace, event.pod) { restartTargetFor(event.namespace, event.pod) }
    var confirming by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember(event.id) { mutableStateOf("") }
    val snoozed = Snoozes.isSnoozed(event)

    @Composable fun buttons() {
        if (LiveRepository.actionsEnabled && target != null)
            DeskAction("Restart ${target.kind}…", primary = true, enabled = !busy) { confirming = true }
        if (snoozed) DeskAction("Unsnooze") { Snoozes.clear(event); message = "Back on the glance" }
        else {
            DeskAction("Snooze 1 h") { Snoozes.snooze(event, 60); message = "Snoozed for 1 hour" }
            DeskAction("Snooze 8 h") { Snoozes.snooze(event, 480); message = "Snoozed for 8 hours" }
        }
    }
    if (vertical) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) { buttons() }
    else Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
    if (vertical) Text("Actions ask to confirm and are logged.", color = DeskColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    if (message.isNotEmpty()) Text(message, color = DeskColors.muted, fontSize = 13.sp)

    if (confirming && target != null) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text("Restart ${target.name}?") },
        text = { Text("Rolls every pod of ${target.kind} ${target.namespace}/${target.name}, the same as kubectl rollout restart.") },
        confirmButton = {
            TextButton(onClick = {
                confirming = false
                val pair = Pairing.read(context)
                if (pair == null) { message = "Not paired"; return@TextButton }
                busy = true
                scope.launch {
                    val error = withContext(Dispatchers.IO) { Actions.restart(pair, target) }
                    busy = false
                    message = error ?: "Restarting ${target.name}"
                }
            }) { Text("Restart") }
        },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } }
    )
}
