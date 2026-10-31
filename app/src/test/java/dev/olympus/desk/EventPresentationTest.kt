package dev.olympus.desk
import dev.olympus.desk.data.*
import org.junit.Assert.*
import org.junit.Test
class EventPresentationTest {
 private fun alert(kind:String,state:String="Firing",id:String="alert:test")=LabEvent("Prometheus","Example","",kind,id,state=state)
 @Test fun informationalAlertDoesNotMakeHealthyClusterSad(){val e=alert("info");assertFalse(needsAttention(e));assertTrue(mergeAttention(emptyList(),listOf(e)).isEmpty());assertEquals(ClusterMood.Healthy,clusterMood(true,true,4,0,59,0,34,0,listOf(e).count{!isInformational(it.kind)},0,0,0))}
 @Test fun warningAndUnconfirmedStatesRemainVisible(){assertTrue(needsAttention(alert("warning")));assertTrue(needsAttention(alert("warning","Pending")));assertTrue(needsAttention(alert("info","Unknown")));assertFalse(needsAttention(alert("warning","Resolved")))}
 @Test fun activeNodeIssueDoesNotDuplicateLegacySummary(){val old=AttentionItem("Node not ready","node:apollo","Warning");val active=alert("warning",id="node:apollo");val items=mergeAttention(listOf(old),listOf(active));assertEquals(1,items.size);assertEquals("event:node:apollo",items.first().target)}
 @Test fun recoveryDoesNotKeepPresentTenseWarningCopy(){val event=LabEvent("Argo CD","Sonarr requires attention","", "Warning",state="Resolved");assertEquals("Sonarr recovered",incidentTitle(event));assertEquals("Sonarr requires attention",event.title)}
 @Test fun activeAlertRetainsMeaning(){val event=LabEvent("Prometheus","CPUThrottlingHigh","","Warning",state="Firing");assertEquals("CPU throttling",incidentTitle(event));assertTrue(needsAttention(event))}
}
