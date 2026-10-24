package dev.olympus.desk

internal enum class ClusterMood { Healthy, Attention, Critical, Unknown }
internal fun clusterMood(live:Boolean, complete:Boolean, nodes:Int, unready:Int, apps:Int, unhealthyApps:Int, monitors:Int, down:Int, firing:Int, pending:Int, uncertain:Int, critical:Int):ClusterMood = when {
 !live -> ClusterMood.Unknown
 unready>0 || down>0 || critical>0 -> ClusterMood.Critical
 !complete || nodes==0 || apps==0 || monitors==0 || uncertain>0 -> ClusterMood.Unknown
 unhealthyApps>0 || firing>0 || pending>0 -> ClusterMood.Attention
 else -> ClusterMood.Healthy
}
