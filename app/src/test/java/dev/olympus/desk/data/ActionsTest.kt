package dev.olympus.desk.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionsTest {
    @Test fun deploymentPodsMapToTheirDeployment() {
        assertEquals(RestartTarget("media", "deployment", "jellyfin"), restartTargetFor("media", "jellyfin-7d9f8b6c5d-x2k9p"))
        assertEquals(RestartTarget("media", "deployment", "sonarr-api"), restartTargetFor("media", "sonarr-api-5c6d7f8b9-abcde"))
    }

    @Test fun statefulPodsMapToTheirStatefulSet() {
        assertEquals(RestartTarget("db", "statefulset", "postgres"), restartTargetFor("db", "postgres-0"))
    }

    @Test fun unknownShapesAreNotRestartable() {
        assertNull(restartTargetFor("", "jellyfin-7d9f8b6c5d-x2k9p"))
        assertNull(restartTargetFor("media", ""))
        assertNull(restartTargetFor("media", "standalone"))
    }
}
