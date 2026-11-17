package dev.olympus.desk

import dev.olympus.desk.data.LabEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlanceTest {
    private fun firing(kind: String, id: String) = LabEvent("Prometheus", "Pod crash", "", kind, id = id, state = "Firing")

    @Test fun nightWindowWrapsMidnight() {
        assertTrue(isNightHour(23)); assertTrue(isNightHour(2)); assertTrue(isNightHour(6))
        assertFalse(isNightHour(7)); assertFalse(isNightHour(14)); assertFalse(isNightHour(22))
    }

    @Test fun nightWindowWithinOneDay() {
        assertTrue(isNightHour(13, start = 12, end = 14)); assertFalse(isNightHour(14, start = 12, end = 14))
    }

    @Test fun headlineCountsIncidents() {
        assertEquals("All clear", glanceHeadline(VerdictState(Verdict.Calm, emptyList())))
        assertEquals("Unknown", glanceHeadline(VerdictState(Verdict.Unknown, emptyList())))
        assertEquals("1 needs you", glanceHeadline(VerdictState(Verdict.Attention, listOf(firing("warning", "a")))))
        assertEquals("2 need you", glanceHeadline(VerdictState(Verdict.Critical, listOf(firing("critical", "a"), firing("warning", "b")))))
    }
}
