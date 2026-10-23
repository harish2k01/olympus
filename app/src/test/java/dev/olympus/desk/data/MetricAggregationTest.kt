package dev.olympus.desk.data
import org.junit.Assert.assertEquals
import org.junit.Test
class MetricAggregationTest {
    @Test fun cpuTotalsAreWeightedByCoreCount() {
        val nodes = listOf(NodeMetric("small", "", 2, 100f, 1f, 2f), NodeMetric("large", "", 8, 0f, 1f, 8f))
        assertEquals(20f, DemoRepository.cpuPercent(nodes), .01f)
    }
    @Test fun memoryUsesTotalCapacityRatherThanMeanOfPercentages() {
        val nodes = listOf(NodeMetric("a", "", 1, 0f, 2f, 2f), NodeMetric("b", "", 1, 0f, 0f, 8f))
        assertEquals(20f, DemoRepository.memoryPercent(nodes), .01f)
    }
}
