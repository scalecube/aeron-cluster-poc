package io.scalecube.acpoc;

import io.aeron.cluster.ClusterBackupEventsListener;
import io.aeron.cluster.ClusterMember;
import io.aeron.cluster.RecordingLog;
import io.aeron.cluster.RecordingLog.Snapshot;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ClusterBackupEventsListenerImpl implements ClusterBackupEventsListener {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ClusterBackupEventsListenerImpl.class);

  @Override
  public void onBackupQuery() {
    LOGGER.info("[onBackupQuery]");
  }

  @Override
  public void onPossibleFailure(Exception ex) {
    LOGGER.warn("[onPossibleClusterFailure]", ex);
  }

  @Override
  public void onBackupResponse(
      ClusterMember[] clusterMembers,
      ClusterMember leaderMember,
      List<Snapshot> snapshotsToRetrieve) {
    LOGGER.info(
        "[onBackupResponse] clusterMembers: {}, leader: {}, snapshotsToRetrieve: {}",
        clusterMembers,
        leaderMember,
        snapshotsToRetrieve);
  }

  @Override
  public void onUpdatedRecordingLog(RecordingLog recordingLog, List<Snapshot> snapshotsRetrieved) {
    LOGGER.info(
        "[onUpdatedRecordingLog] recordingLog: {}, snapshotsRetrieved: {}",
        recordingLog,
        snapshotsRetrieved);
  }

  @Override
  public void onLiveLogProgress(long recordingId, long recordingPosCounterId, long logPosition) {
    LOGGER.info(
        "[onLiveLogProgress] recordingId: {}, recordingPosCounterId: {}, logPosition: {}",
        recordingId,
        recordingPosCounterId,
        logPosition);
  }
}
