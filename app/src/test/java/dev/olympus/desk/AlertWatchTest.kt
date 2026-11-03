package dev.olympus.desk
import org.junit.Assert.*
import org.junit.Test
import dev.olympus.desk.data.LabEvent
class AlertWatchTest{
 private fun event(state:String="Firing",kind:String="warning",source:String="Prometheus")=LabEvent(source,"Alert","",kind,id="one",state=state)
 @Test fun notificationsDoNotRepeatUnchangedState(){assertTrue(alertCandidates(listOf(event()),mapOf("one" to "Firing"),true,true).isEmpty())}
 @Test fun informationalAndPendingAreNotPages(){assertTrue(alertCandidates(listOf(event(kind="info"),event(state="Pending")),emptyMap(),true,true).isEmpty())}
 @Test fun recoveryRequiresAnObservedFiring(){assertTrue(alertCandidates(listOf(event("Resolved")),emptyMap(),true,true).isEmpty());assertEquals(1,alertCandidates(listOf(event("Resolved")),mapOf("one" to "Firing"),true,true).size)}
 @Test fun incomingRobustaWarningsNotifyWithoutPagingOldKubernetesEvents(){assertEquals(1,alertCandidates(listOf(event("Observed",source="Robusta")),emptyMap(),true,false).size);assertTrue(alertCandidates(listOf(event("Observed",source="Kubernetes")),emptyMap(),true,false).isEmpty())}
 @Test fun changedSeverityIsNotSuppressedAsADuplicate(){assertEquals(1,alertCandidates(listOf(event(kind="critical")),mapOf("one" to "Firing|warning"),true,true).size);assertTrue(alertCandidates(listOf(event()),mapOf("one" to "Firing|warning"),true,true).isEmpty())}
 @Test fun arrActivityRespectsTheAppNotificationToggle(){assertEquals(1,alertCandidates(listOf(event("Activity",source="Sonarr")),emptyMap(),true,false).size);assertTrue(alertCandidates(listOf(event("Activity",source="Sonarr")),emptyMap(),false,false).isEmpty())}
 @Test fun quietHoursSupportOvernightAndSameDay(){assertTrue(quietHour(23,22,7));assertTrue(quietHour(6,22,7));assertFalse(quietHour(12,22,7));assertTrue(quietHour(12,9,17));assertFalse(quietHour(12,7,7))}
 @Test fun criticalBypassesQuietHoursButRespectsExplicitSilence() {
  assertTrue(shouldNotifyNow(quiet = true, silenced = false, critical = true))
  assertFalse(shouldNotifyNow(quiet = true, silenced = false, critical = false))
  assertFalse(shouldNotifyNow(quiet = false, silenced = true, critical = true))
 }
}