package dev.olympus.desk
import org.junit.Assert.assertEquals
import org.junit.Test
class ClusterMoodTest {
 private fun mood(live:Boolean=true,complete:Boolean=true,unready:Int=0,down:Int=0,firing:Int=0,pending:Int=0,uncertain:Int=0,critical:Int=0,unhealthy:Int=0)=clusterMood(live,complete,4,unready,59,unhealthy,34,down,firing,pending,uncertain,critical)
 @Test fun healthyRequiresCompleteLiveObservations(){assertEquals(ClusterMood.Healthy,mood());assertEquals(ClusterMood.Unknown,mood(live=false));assertEquals(ClusterMood.Unknown,mood(complete=false));assertEquals(ClusterMood.Unknown,mood(uncertain=1))}
 @Test fun failuresProduceConcernedExpression(){assertEquals(ClusterMood.Critical,mood(unready=1));assertEquals(ClusterMood.Critical,mood(down=1));assertEquals(ClusterMood.Critical,mood(critical=1))}
 @Test fun warningsPendingAndUnsyncedAppsNeedAttention(){assertEquals(ClusterMood.Attention,mood(firing=1));assertEquals(ClusterMood.Attention,mood(pending=1));assertEquals(ClusterMood.Attention,mood(unhealthy=1))}
 @Test fun staleFailuresCannotPretendToBeCurrent(){assertEquals(ClusterMood.Unknown,mood(live=false,down=1));assertEquals(ClusterMood.Critical,mood(complete=false,down=1))}
}
