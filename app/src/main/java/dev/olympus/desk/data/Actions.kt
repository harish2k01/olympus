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
    /** Each call returns null on success, or a message to show. */
    fun restart(pair: Pair<String, String>, target: RestartTarget) =
        post(pair, "/v1/actions/restart", JSONObject().put("namespace", target.namespace).put("kind", target.kind).put("name", target.name))

    fun deletePod(pair: Pair<String, String>, namespace: String, pod: String) =
        post(pair, "/v1/actions/delete-pod", JSONObject().put("namespace", namespace).put("name", pod))

    fun sync(pair: Pair<String, String>, app: String) =
        post(pair, "/v1/actions/sync", JSONObject().put("name", app))

    fun snooze(pair: Pair<String, String>, key: String, minutes: Long) =
        post(pair, "/v1/snoozes", JSONObject().put("key", key).put("minutes", minutes))

    private fun post(pair: Pair<String, String>, path: String, body: JSONObject): String? {
        val connection = URL(pair.first + path).openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 7000; connection.readTimeout = 12000
            connection.requestMethod = "POST"; connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer " + pair.second)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            if (connection.responseCode == 200) null
            else runCatching { JSONObject(connection.errorStream.bufferedReader().use { it.readText() }).optString("error") }
                .getOrNull()?.ifBlank { null } ?: "Request failed (${connection.responseCode})"
        } catch (_: Exception) {
            "Couldn't reach the backend"
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Snoozed incidents stay out of the glance verdict until their time is up. The backend shares snoozes
 * between devices; the local copy keeps a snooze working while the backend is unreachable.
 */
internal object Snoozes {
    private const val prefsName = "snoozes"
    private val local = mutableStateMapOf<String, Long>()
    private val server = mutableStateMapOf<String, Long>()
    private var prefs: android.content.SharedPreferences? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        if (prefs != null) return
        appContext = context.applicationContext
        prefs = context.getSharedPreferences(prefsName, 0).also { p ->
            val now = System.currentTimeMillis()
            p.all.forEach { (key, value) -> if (value is Long && value > now) local[key] = value }
        }
    }

    fun applyServer(snoozes: JSONObject?) {
        val incoming = snoozes?.keys()?.asSequence()?.mapNotNull { key ->
            runCatching { java.time.Instant.parse(snoozes.getString(key)).toEpochMilli() }.getOrNull()?.let { key to it }
        }?.toMap().orEmpty()
        server.keys.retainAll(incoming.keys)
        server.putAll(incoming)
    }

    fun key(event: LabEvent) = event.id.ifBlank { "${event.source}/${event.resource}/${event.title}" }

    fun isSnoozed(event: LabEvent, now: Long = System.currentTimeMillis()) =
        maxOf(local[key(event)] ?: 0L, server[key(event)] ?: 0L) > now

    fun snooze(event: LabEvent, minutes: Long) = update(key(event), minutes)

    fun clear(event: LabEvent) = update(key(event), 0)

    private fun update(key: String, minutes: Long) {
        val at = System.currentTimeMillis() + minutes * 60_000
        if (minutes > 0) { local[key] = at; prefs?.edit()?.putLong(key, at)?.apply() }
        else { local.remove(key); server.remove(key); prefs?.edit()?.remove(key)?.apply() }
        val context = appContext ?: return
        Thread { Pairing.read(context)?.let { Actions.snooze(it, key, minutes) } }.start()
    }
}

/** Non-urgent work from the backend; never changes the verdict. */
internal data class Chore(val id: String, val kind: String, val title: String, val detail: String, val due: String, val link: String)

internal data class CertificateSummary(val total: Int, val nextExpiry: String, val nextName: String)

internal fun parseChores(array: org.json.JSONArray?): List<Chore> =
    if (array == null) emptyList() else (0 until array.length()).map {
        val c = array.getJSONObject(it)
        Chore(c.optString("id"), c.optString("kind"), c.optString("title"), c.optString("detail"), c.optString("due"), c.optString("link"))
    }
