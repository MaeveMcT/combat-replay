package com.combatreplay;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ReplayUploadQueue
{
    private static final Logger log = LoggerFactory.getLogger(ReplayUploadQueue.class);
    private static final long POLL_SECONDS = 2L;
    private final ReplayUploadClient client;
    private final UploadSidecarStore sidecars;
    private final ScheduledExecutorService executor;
    private final String token;

    ReplayUploadQueue(ReplayUploadClient client, UploadSidecarStore sidecars,
        ScheduledExecutorService executor, String token)
    {
        this.client = client;
        this.sidecars = sidecars;
        this.executor = executor;
        this.token = token;
    }

    void enqueue(Path source, String recordingId)
    {
        executor.execute(() ->
        {
            try
            {
                UploadSidecarState state = sidecars.load(recordingId);
                if (state == null)
                {
                    state = new UploadSidecarState(recordingId, source.toAbsolutePath().toString());
                }
                if (!"ready".equals(state.status))
                {
                    attempt(state);
                }
            }
            catch (IOException | RuntimeException exception)
            {
                log.warn("Unable to queue replay upload {}", recordingId, exception);
            }
        });
    }

    private void attempt(UploadSidecarState state)
    {
        Path source = Path.of(state.sourcePath);
        if (!Files.isRegularFile(source))
        {
            state.status = "failed";
            state.failureCode = "local_source_missing";
            persist(state);
            return;
        }

        state.status = "uploading";
        state.failureCode = null;
        state.nextAttemptAtEpochMillis = 0L;
        state.attempts++;
        persist(state);
        client.upload(source, state.recordingId, token).whenComplete((result, error) ->
        {
            if (error != null)
            {
                failedAttempt(state, unwrap(error));
                return;
            }
            state.remoteReplayId = result.getReplayId();
            state.status = result.getStatus();
            state.failureCode = result.getFailureCode();
            persist(state);
            if ("processing".equals(result.getStatus()) || "uploaded".equals(result.getStatus()))
            {
                schedulePoll(state);
            }
        });
    }

    private void poll(UploadSidecarState state)
    {
        client.status(state.remoteReplayId, token).whenComplete((result, error) ->
        {
            if (error != null)
            {
                Throwable cause = unwrap(error);
                if (cause instanceof ReplayUploadException
                    && ((ReplayUploadException) cause).isTransientFailure())
                {
                    schedulePoll(state);
                }
                else
                {
                    state.status = "failed";
                    state.failureCode = "status_request_failed";
                    persist(state);
                }
                return;
            }
            state.status = result.getStatus();
            state.failureCode = result.getFailureCode();
            persist(state);
            if ("processing".equals(result.getStatus()) || "uploaded".equals(result.getStatus()))
            {
                schedulePoll(state);
            }
        });
    }

    private void failedAttempt(UploadSidecarState state, Throwable error)
    {
        if (error instanceof ReplayUploadException && ((ReplayUploadException) error).isTransientFailure())
        {
            long delay = Math.min(300L, 1L << Math.min(state.attempts, 8));
            state.status = "retrying";
            state.failureCode = "transient_upload_failure";
            state.nextAttemptAtEpochMillis = System.currentTimeMillis() + Duration.ofSeconds(delay).toMillis();
            persist(state);
            executor.schedule(() -> attempt(state), delay, TimeUnit.SECONDS);
            return;
        }
        state.status = "failed";
        state.failureCode = "upload_rejected";
        persist(state);
    }

    private void schedulePoll(UploadSidecarState state)
    {
        executor.schedule(() -> poll(state), POLL_SECONDS, TimeUnit.SECONDS);
    }

    private void persist(UploadSidecarState state)
    {
        try
        {
            sidecars.save(state);
        }
        catch (IOException exception)
        {
            log.warn("Unable to persist replay upload state {}", state.recordingId, exception);
        }
    }

    private static Throwable unwrap(Throwable error)
    {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
            && current.getCause() != null)
        {
            current = current.getCause();
        }
        return current;
    }
}
