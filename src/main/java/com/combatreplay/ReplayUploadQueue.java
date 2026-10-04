package com.combatreplay;

import java.io.IOException;
import net.runelite.client.util.Filepath;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ReplayUploadQueue implements AutoCloseable
{
    private static final Logger log = LoggerFactory.getLogger(ReplayUploadQueue.class);
    private static final long POLL_SECONDS = 2L;
    private final ReplayUploadClient client;
    private final UploadSidecarStore sidecars;
    private final ScheduledExecutorService executor;
    private final String token;
    private final Consumer<String> statusChanged;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile boolean closed;
    private CompletableFuture<?> request;
    private Filepath source;
    private ScheduledFuture<?> scheduled;

    ReplayUploadQueue(ReplayUploadClient client, UploadSidecarStore sidecars,
        ScheduledExecutorService executor, String token)
    {
        this(client, sidecars, executor, token, ignored -> { });
    }

    ReplayUploadQueue(ReplayUploadClient client, UploadSidecarStore sidecars,
        ScheduledExecutorService executor, String token, Consumer<String> statusChanged)
    {
        this.client = client;
        this.sidecars = sidecars;
        this.executor = executor;
        this.token = token;
        this.statusChanged = statusChanged;
    }

    void enqueue(Filepath source, String recordingId)
    {
        enqueue(source, recordingId, false);
    }

    void enqueue(Filepath source, String recordingId, boolean manualRetry)
    {
        if (closed || !running.compareAndSet(false, true)) return;
        executor.execute(() ->
        {
            if (closed) return;
            try
            {
                UploadSidecarState state = sidecars.load(recordingId);
                if (state == null)
                {
                    state = new UploadSidecarState(recordingId, source.getFileName());
                }
                if (!manualRetry && ("ready".equals(state.status) || "paused".equals(state.status)))
                {
                    running.set(false);
                    statusChanged.accept(message(state));
                    return;
                }
                // Retry the validated library selection, never a path read from the sidecar.
                this.source = source;
                state.sourcePath = source.getFileName();
                attempt(state);
            }
            catch (IOException | RuntimeException exception)
            {
                running.set(false);
                log.warn("Unable to queue replay upload {}", recordingId);
            }
        });
    }

    private synchronized void attempt(UploadSidecarState state)
    {
        if (closed) return;
        if (!source.isFile())
        {
            state.status = "failed";
            state.failureCode = "local_source_missing";
            running.set(false);
            persist(state);
            return;
        }
        state.status = "uploading";
        state.failureCode = null;
        state.nextAttemptAtEpochMillis = 0L;
        state.attempts++;
        persist(state);
        request = client.upload(source, state.recordingId, token).whenComplete((result, error) ->
        {
            if (closed) return;
            if (error != null)
            {
                failedAttempt(state, unwrap(error));
                return;
            }
            state.remoteReplayId = result.getReplayId();
            received(state, result);
        });
    }

    private synchronized void poll(UploadSidecarState state)
    {
        if (closed) return;
        request = client.status(state.remoteReplayId, token).whenComplete((result, error) ->
        {
            if (closed) return;
            if (error != null)
            {
                Throwable cause = unwrap(error);
                if (cause instanceof ReplayUploadException
                    && ((ReplayUploadException) cause).isTransientFailure())
                {
                    schedule(() -> poll(state), POLL_SECONDS);
                }
                else
                {
                    state.status = "failed";
                    state.failureCode = "status_request_failed";
                    running.set(false);
                    persist(state);
                }
                return;
            }
            received(state, result);
        });
    }

    private void received(UploadSidecarState state, ReplayUploadResult result)
    {
        state.status = result.getStatus();
        state.failureCode = result.getFailureCode();
        boolean processing = "processing".equals(state.status) || "uploaded".equals(state.status);
        if (!processing) running.set(false);
        persist(state);
        if (processing) schedule(() -> poll(state), POLL_SECONDS);
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
            schedule(() -> attempt(state), delay);
            return;
        }
        boolean storageFull = error instanceof ReplayUploadException
            && "storage_full".equals(((ReplayUploadException) error).getFailureCode());
        state.status = storageFull ? "paused" : "failed";
        state.failureCode = storageFull ? "storage_full" : "upload_rejected";
        state.nextAttemptAtEpochMillis = 0L;
        running.set(false);
        persist(state);
    }

    private synchronized void schedule(Runnable action, long seconds)
    {
        if (!closed) scheduled = executor.schedule(action, seconds, TimeUnit.SECONDS);
    }

    private synchronized void persist(UploadSidecarState state)
    {
        if (closed) return;
        try
        {
            sidecars.save(state);
            statusChanged.accept(message(state));
        }
        catch (IOException exception)
        {
            log.warn("Unable to persist replay upload state {}", state.recordingId);
        }
    }

    static String message(UploadSidecarState state)
    {
        if (state == null) return "Not uploaded";
        if ("storage_full".equals(state.failureCode))
        {
            return "Storage full. Free space on the web, then select Upload / retry. Local recording retained.";
        }
        switch (state.status)
        {
            case "ready": return "Web replay ready";
            case "uploading": return "Uploading replay";
            case "uploaded":
            case "processing": return "Processing web replay";
            case "retrying": return "Upload will retry after a temporary failure";
            default: return "Upload not complete. Local recording retained; select Upload / retry.";
        }
    }

    @Override
    public synchronized void close()
    {
        closed = true;
        client.close();
        if (scheduled != null) scheduled.cancel(false);
        if (request != null) request.cancel(true);
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
