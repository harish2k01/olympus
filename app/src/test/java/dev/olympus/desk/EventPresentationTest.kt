package dev.olympus.desk
import dev.olympus.desk.data.*
import org.junit.Assert.*
import org.junit.Test
class EventPresentationTest {
 private fun alert(kind:String,state:String="Firing",id:String="alert:test")=LabEvent("Prometheus","Example","",kind,id,state=state)
 @Test fun informationalAlertDoesNotMakeHealthyClusterSad(){val e=alert("info");assertFalse(needsAttention(e));}
 @Test fun warningAndUnconfirmedStatesRemainVisible(){assertTrue(needsAttention(alert("warning")));assertTrue(needsAttention(alert("warning","Pending")));assertTrue(needsAttention(alert("info","Unknown")));assertFalse(needsAttention(alert("warning","Resolved")))}
 @Test fun recoveryDoesNotKeepPresentTenseWarningCopy(){val event=LabEvent("Argo CD","Sonarr requires attention","", "Warning",state="Resolved");assertEquals("Sonarr recovered",incidentTitle(event));assertEquals("Sonarr requires attention",event.title)}
 @Test fun activeAlertRetainsMeaning(){val event=LabEvent("Prometheus","CPUThrottlingHigh","","Warning",state="Firing");assertEquals("CPU throttling",incidentTitle(event));assertTrue(needsAttention(event))}
 @org.junit.Test fun recoveredGitOpsDoesNotReadLikeACurrentFailure(){org.junit.Assert.assertEquals("olympus recovered",incidentTitle(dev.olympus.desk.data.LabEvent("Argo CD","olympus · Progressing / Synced","","info",state="Resolved")))}
}
