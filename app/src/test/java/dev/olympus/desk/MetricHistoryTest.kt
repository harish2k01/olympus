package dev.olympus.desk

import org.junit.Assert.*
import org.junit.Test

class MetricHistoryTest {
 @Test fun unavailableSamplesBreakTheLine(){
  val points=listOf(MetricSample(0,1f),MetricSample(30,null),MetricSample(60,2f),MetricSample(90,3f))
  val edges=historyEdges(points,30)
  assertEquals(1,edges.size)
  assertEquals(60L,edges.first().first.time)
 }
 @Test fun missingIntervalsAreNotBridged(){
  assertTrue(historyEdges(listOf(MetricSample(0,1f),MetricSample(120,2f)),30).isEmpty())
 }
 @Test fun nonFiniteAndReverseSamplesAreNotDrawn(){
  assertTrue(historyEdges(listOf(MetricSample(30,Float.NaN),MetricSample(0,2f)),30).isEmpty())
 }
}
