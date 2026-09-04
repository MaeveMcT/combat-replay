package com.combatreplay;

public final class ReplayUploadResult
{
    private final long replayId;
    private final String recordingId;
    private final String status;
    private final String failureCode;

    ReplayUploadResult(long replayId, String recordingId, String status, String failureCode)
    {
        this.replayId = replayId;
        this.recordingId = recordingId;
        this.status = status;
        this.failureCode = failureCode;
    }

    public long getReplayId()
    {
        return replayId;
    }

    public String getRecordingId()
    {
        return recordingId;
    }

    public String getStatus()
    {
        return status;
    }

    public String getFailureCode()
    {
        return failureCode;
    }
}
