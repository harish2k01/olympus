package dev.olympus.desk.data

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FreshnessTest {
    @Test fun thresholdsFollowTheThirtySecondSourceInterval() {
        assertEquals(Freshness.Unknown, classifyFreshness(null))
        assertEquals(Freshness.Fresh, classifyFreshness(59))
        assertEquals(Freshness.Aging, classifyFreshness(60))
        assertEquals(Freshness.Aging, classifyFreshness(299))
        assertEquals(Freshness.Stale, classifyFreshness(300))
    }

    @Test fun timestampAgeUsesSourceTimeAndHandlesClockSkew() {
        val now = Instant.parse("2026-11-03T12:30:00Z")
        assertEquals(60L, observationAgeSeconds("2026-11-03T12:29:00Z", now))
        assertEquals(0L, observationAgeSeconds("2026-11-03T12:30:10Z", now))
        assertEquals(null, observationAgeSeconds("", now))
    }
}
