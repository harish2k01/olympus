package dev.olympus.desk.data

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A workload the backend can rollout-restart, derived from the pod an incident names. */
internal data class RestartTarget(val namespace: String, val kind: String, val name: String)

private val deploymentPod = Regex("^(.+)-[a-z0-9]{6,10}-[a-z0-9]{5}$")
private val statefulPod = Regex("^(.+)-\\d+$")

/** Deployment pods are name-<replicaset hash>-<suffix>; StatefulSet pods are name-<ordinal>. Anything else is not restartable. */
internal fun restartTargetFor(namespace: String, pod: String): RestartTarget? {
    if (namespace.isBlank() || pod.isBlank()) return null
    deploymentPod.matchEntire(pod)?.let { return RestartTarget(namespace, "deployment", it.groupValues[1]) }
    statefulPod.matchEntire(pod)?.let { return RestartTarget(namespace, "statefulset", it.groupValues[1]) }
    return null
}

internal object Actions {
    /** Returns null on success, or a message to show. */
    fun restart(pair: Pair<String, String>, target: RestartTarget): String? {
        val connection = URL(pair.first + "/v1/actions/restart").openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 7000; connection.readTimeout = 12000
            connection.requestMethod = "POST"; connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer " + pair.second)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use {
                it.write(JSONObject().put("namespace", target.namespace).put("kind", target.kind).put("name", target.name).toString().toByteArray())
            }
            if (connection.responseCode == 200) null
            else runCatching { JSONObject(connection.errorStream.bufferedReader().use { it.readText() }).optString("error") }
                .getOrNull()?.ifBlank { null } ?: "Restart failed (${connection.responseCode})"
        } catch (_: Exception) {
            "Couldn't reach the backend"
        } finally {
            connection.disconnect()
        }
    }
}

/** Snoozed incidents stay out of the glance verdict until their time is up. Stored on this device. */
internal object Snoozes {
    private const val prefsName = "snoozes"
    private val until = mutableStateMapOf<String, Long>()
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences(prefsName, 0).also { p ->
            val now = System.currentTimeMillis()
            p.all.forEach { (key, value) -> if (value is Long && value > now) until[key] = value }
        }
    }

    fun key(event: LabEvent) = event.id.ifBlank { "${event.source}/${event.resource}/${event.title}" }

    fun isSnoozed(event: LabEvent, now: Long = System.currentTimeMillis()) = (until[key(event)] ?: 0L) > now

    fun snooze(event: LabEvent, minutes: Long) {
        val at = System.currentTimeMillis() + minutes * 60_000
        until[key(event)] = at
        prefs?.edit()?.putLong(key(event), at)?.apply()
    }

    fun clear(event: LabEvent) {
        until.remove(key(event))
        prefs?.edit()?.remove(key(event))?.apply()
    }
}
