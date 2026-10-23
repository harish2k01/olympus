package dev.olympus.desk.data

data class NodeMetric(val name: String, val role: String, val cores: Int, val cpuPercent: Float, val memoryUsedGiB: Float, val memoryTotalGiB: Float, val available:Boolean=true, val ready:Boolean=true)
data class LabEvent(val source: String, val title: String, val detail: String, val kind: String)

/** Fixed illustrative data: never presented as a current cluster connection. */
object DemoRepository {
    val nodes = listOf(
        NodeMetric("zeus", "Control plane", 4, 8.7f, 2.99f, 3.80f),
        NodeMetric("apollo", "Worker", 4, 8.7f, 3.07f, 7.73f),
        NodeMetric("athena", "Worker", 4, 35.5f, 8.02f, 15.48f),
        NodeMetric("hermes", "Worker", 8, 16.8f, 8.98f, 14.53f)
    )
    val events = listOf(
        LabEvent("Robusta", "Ghost CPU throttling", "Informational finding · service still responding", "Info"),
        LabEvent("Uptime Kuma", "Immich recovered", "Illustrative HTTP recovery notification", "Recovery"),
        LabEvent("Sonarr", "Episode imported", "Illustrative media import event", "Activity"),
        LabEvent("Radarr", "Download completed", "Illustrative download event", "Activity"),
        LabEvent("Prowlarr", "Indexer health restored", "Illustrative indexer recovery", "Recovery")
    )
    fun cpuPercent(nodes: List<NodeMetric>) = if (nodes.isEmpty()) 0f else nodes.sumOf { (it.cpuPercent * it.cores).toDouble() }.toFloat() / nodes.sumOf { it.cores }
    fun memoryPercent(nodes: List<NodeMetric>) = if (nodes.isEmpty()) 0f else nodes.sumOf { it.memoryUsedGiB.toDouble() }.toFloat() / nodes.sumOf { it.memoryTotalGiB.toDouble() }.toFloat() * 100
}
