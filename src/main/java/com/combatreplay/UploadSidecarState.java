package com.combatreplay;

final class UploadSidecarState
{
    String recordingId;
    String sourcePath;
    String status;
    int attempts;
    long remoteReplayId;
    long nextAttemptAtEpochMillis;
    String failureCode;

    UploadSidecarState(String recordingId, String sourcePath)
    {
        this.recordingId = recordingId;
        this.sourcePath = sourcePath;
        this.status = "pending";
    }
}
