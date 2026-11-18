package dev.olympus.desk

import android.content.Intent
import android.net.Uri
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

/** A cluster write the user must confirm: what the dialog says and what runs on Confirm. */
private data class PendingAction(val title: String, val body: String, val confirm: String, val done: String, val run: (Pair<String, String>) -> String?)

/**
 * Actions for a firing incident: logs, Argo CD, snooze and the allowlisted cluster writes
 * (restart, delete pod, sync). Writes appear only when the backend enables them and this
 * screen allows them, and each asks first.
 */
@Composable
internal fun IncidentActions(event: LabEvent, vertical: Boolean = false, open: (String) -> Unit = {}) {
    if (event.state != "Firing") return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val target = remember(event.namespace, event.pod) { restartTargetFor(event.namespace, event.pod) }
    val app = LiveRepository.services.firstOrNull { it.namespace == event.namespace && event.pod.startsWith(it.name) }
    val argo = LiveRepository.dashboardLinks["argo"]
    val writes = LiveRepository.actionsEnabled && context.getSharedPreferences("home-v3", 0).getBoolean("allowActions", true)
    var pending by remember { mutableStateOf<PendingAction?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember(event.id) { mutableStateOf("") }
    val snoozed = Snoozes.isSnoozed(event)

    @Composable fun buttons() {
        if (event.pod.isNotBlank() && event.namespace.isNotBlank())
            DeskAction("Previous logs", primary = !writes) { open("pod:${event.namespace}/${event.pod}@${event.node}") }
        if (writes && target != null) DeskAction("Restart ${target.kind}…", primary = true, enabled = !busy) {
            pending = PendingAction("Restart ${target.name}?", "Rolls every pod of ${target.kind} ${target.namespace}/${target.name}, the same as kubectl rollout restart.",
                "Restart", "Restarting ${target.name}") { Actions.restart(it, target) }
        }
        if (writes && event.pod.isNotBlank() && event.namespace.isNotBlank()) DeskAction("Delete pod…", enabled = !busy) {
            pending = PendingAction("Delete ${event.pod}?", "Its controller creates a replacement. Use this for a pod that is stuck or wedged.",
                "Delete", "Deleted ${event.pod}") { Actions.deletePod(it, event.namespace, event.pod) }
        }
        if (writes && app != null) DeskAction("Sync ${app.name}…", enabled = !busy) {
            pending = PendingAction("Sync ${app.name}?", "Asks Argo CD to sync the application to Git, the same as pressing Sync.",
                "Sync", "Syncing ${app.name}") { Actions.sync(it, app.name) }
        }
        if (snoozed) DeskAction("Unsnooze") { Snoozes.clear(event); message = "Back on the glance" }
        else {
            DeskAction("Snooze 1 h") { Snoozes.snooze(event, 60); message = "Snoozed for 1 hour" }
            DeskAction("Snooze 8 h") { Snoozes.snooze(event, 480); message = "Snoozed for 8 hours" }
        }
        if (argo != null && app != null) DeskAction("Open in Argo CD") {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${argo.trimEnd('/')}/applications/${Uri.encode(app.name)}"))) }
        }
    }
    if (vertical) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) { buttons() }
    else Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
    if (vertical && writes) Text("Actions ask to confirm and are logged.", color = DeskColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    if (message.isNotEmpty()) Text(message, color = DeskColors.muted, fontSize = 13.sp)

    pending?.let { action ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(action.title) },
            text = { Text(action.body) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    val pair = Pairing.read(context)
                    if (pair == null) { message = "Not paired"; return@TextButton }
                    busy = true
                    scope.launch {
                        val error = withContext(Dispatchers.IO) { action.run(pair) }
                        busy = false
                        message = error ?: action.done
                    }
                }) { Text(action.confirm) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }
}
