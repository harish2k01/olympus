package dev.olympus.desk

import dev.olympus.desk.data.CertificateSummary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ClusterAndLightTest {
    @Test fun darkRoomsDimTheBacklightAndBrightRoomsUseAuto() {
        assertEquals(0.04f, brightnessForLux(1f))
        assertEquals(0.15f, brightnessForLux(20f))
        assertEquals(0.4f, brightnessForLux(120f))
        assertEquals(-1f, brightnessForLux(800f))
    }

    @Test fun certificateLineCountsDaysToNextRenewal() {
        val now = Instant.parse("2026-11-19T00:00:00Z")
        assertEquals("12 certificates · next renews in 9 days (wildcard-lab)",
            certificateLine(CertificateSummary(12, "2026-11-28T06:00:00Z", "wildcard-lab"), now))
        assertEquals("3 certificates · old has expired", certificateLine(CertificateSummary(3, "2026-11-01T00:00:00Z", "old"), now))
        assertEquals("1 certificate", certificateLine(CertificateSummary(1, "", ""), now))
    }
}
