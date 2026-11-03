package dev.olympus.desk

import dev.olympus.desk.data.LabEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class DeskVerdictTest {
    private fun event(state: String, kind: String = "warning") =
        LabEvent("Prometheus", "Memory pressure", "", kind, state = state)

    @Test fun calmRequiresVerifiedSourcesAndNoFiringIncidents() {
        val history = listOf(event("Resolved", "critical"), event("Pending"), event("Firing", "info"))
        assertEquals(Verdict.Calm, resolveVerdict("Live", listOf(true), history).verdict)
        assertEquals(Verdict.Unknown, resolveVerdict("Live", listOf(false), history).verdict)
        assertEquals(Verdict.Unknown, resolveVerdict("Live", emptyList(), history).verdict)
    }

    @Test fun disconnectedDataCannotConfirmAnActiveIncident() {
        assertEquals(Verdict.Unknown,
            resolveVerdict("Offline", listOf(true), listOf(event("Firing"))).verdict)
    }

    @Test fun criticalIncidentWinsEvenWithPartialCoverage() {
        val warning = event("Firing").copy(id = "warning")
        val critical = event("Firing", "critical").copy(id = "critical")
        val verdict = resolveVerdict("Live · Partial", listOf(false), listOf(warning, critical, critical))
        assertEquals(Verdict.Critical, verdict.verdict)
        assertEquals(listOf(critical, warning), verdict.incidents)
    }
}
