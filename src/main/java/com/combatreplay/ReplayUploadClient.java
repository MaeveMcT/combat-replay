package com.combatreplay;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.zip.GZIPOutputStream;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public final class ReplayUploadClient implements AutoCloseable
{
    private final Gson gson;
    private final URI baseUri;
    private final OkHttpClient httpClient;
    private final Executor preparationExecutor;
    private volatile boolean closed;
    private final java.util.Set<CompletableFuture<?>> requests = new java.util.HashSet<>();

    public ReplayUploadClient(URI baseUri, OkHttpClient httpClient, Executor preparationExecutor, Gson gson)
    {
        validateBaseUri(baseUri);
        this.baseUri = baseUri;
        this.httpClient = java.util.Objects.requireNonNull(httpClient);
        this.preparationExecutor = preparationExecutor;
        this.gson = java.util.Objects.requireNonNull(gson);
    }

    public CompletableFuture<ReplayUploadResult> upload(Path source, String recordingId, String token)
    {
        return CompletableFuture.supplyAsync(() -> prepare(source), preparationExecutor)
            .thenCompose(prepared -> create(recordingId, token, prepared)
                .thenCompose(created -> transfer(created.id, token, prepared)
                    .thenCompose(ignored -> complete(created.id, token)))
                .whenComplete((ignored, error) -> prepared.delete()));
    }

    public CompletableFuture<ReplayUploadResult> status(long replayId, String token)
    {
        Request request = request(apiUri("/api/v1/replays/" + replayId), token).get().build();
        return send(request, 200);
    }

    private CompletableFuture<ReplayResponse> create(String recordingId, String token, PreparedReplay prepared)
    {
        CreateRequest payload = new CreateRequest(recordingId, prepared.sha256, prepared.byteSize);
        Request request = request(apiUri("/api/v1/replays"), token)
            .post(RequestBody.create(MediaType.parse("application/json"), gson.toJson(payload)))
            .build();
        return sendResponse(request, 200, 201);
    }

    private CompletableFuture<ReplayResponse> transfer(long replayId, String token, PreparedReplay prepared)
    {
        Request request = request(apiUri("/api/v1/replays/" + replayId + "/upload"), token)
            .header("Content-Encoding", "gzip")
            .put(RequestBody.create(MediaType.parse("application/gzip"), prepared.path.toFile()))
            .build();
        return sendResponse(request, 200);
    }

    private CompletableFuture<ReplayUploadResult> complete(long replayId, String token)
    {
        Request request = request(apiUri("/api/v1/replays/" + replayId + "/complete"), token)
            .post(RequestBody.create(null, new byte[0]))
            .build();
        return send(request, 202);
    }

    private CompletableFuture<ReplayUploadResult> send(Request request, int... expectedStatuses)
    {
        return sendResponse(request, expectedStatuses).thenApply(ReplayResponse::result);
    }

    private synchronized CompletableFuture<ReplayResponse> sendResponse(Request request, int... expectedStatuses)
    {
        if (closed) return failed(new ReplayUploadException("Upload client closed", 0));
        CompletableFuture<ReplayHttpRequests.Reply> pending = ReplayHttpRequests.send(httpClient, request, 0);
        requests.add(pending);
        pending.whenComplete((response, error) -> removeRequest(pending));
        return pending.handle((response, error) ->
            {
                if (error != null)
                {
                    throw new CompletionException(new ReplayUploadException("Replay upload request failed", 0, error));
                }
                boolean expected = false;
                for (int status : expectedStatuses)
                {
                    expected |= response.status == status;
                }
                if (!expected)
                {
                    String code = null;
                    try
                    {
                        ErrorResponse failure = gson.fromJson(response.body, ErrorResponse.class);
                        if (response.status == 409 && failure != null)
                        {
                            code = failure.error;
                        }
                    }
                    catch (RuntimeException ignored)
                    {
                        // Error pages are not necessarily JSON; retain only the status.
                    }
                    throw new CompletionException(new ReplayUploadException(
                        "Replay upload request failed with HTTP status " + response.status, response.status)
                        .withFailureCode(code));
                }
                ReplayResponse payload = gson.fromJson(response.body, ReplayResponse.class);
                if (payload == null || payload.id <= 0 || payload.recordingId == null || payload.status == null)
                {
                    throw new CompletionException(new ReplayUploadException("Replay upload response was invalid", 0));
                }
                return payload;
            });
    }

    private synchronized void removeRequest(CompletableFuture<?> request)
    {
        requests.remove(request);
    }

    @Override
    public synchronized void close()
    {
        closed = true;
        for (CompletableFuture<?> pending : new java.util.ArrayList<>(requests)) pending.cancel(true);
        requests.clear();
    }

    private Request.Builder request(URI uri, String token)
    {
        return new Request.Builder().url(uri.toString())
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + token);
    }

    private URI apiUri(String path)
    {
        return baseUri.resolve(path);
    }

    private static void validateBaseUri(URI uri)
    {
        String host = uri.getHost();
        boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
            || "[::1]".equals(host);
        boolean secure = "https".equalsIgnoreCase(uri.getScheme());
        if (host == null || uri.getUserInfo() != null || !(secure || loopback && "http".equalsIgnoreCase(uri.getScheme())))
        {
            throw new IllegalArgumentException("Replay uploads require HTTPS except on localhost");
        }
    }

    private PreparedReplay prepare(Path source)
    {
        Path compressed = null;
        try
        {
            compressed = Files.createTempFile("combat-replay-upload-", ".json.gz");
            try (InputStream input = Files.newInputStream(source);
                 OutputStream output = new GZIPOutputStream(Files.newOutputStream(compressed)))
            {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0)
                {
                    if (closed) throw new IOException("Upload client closed");
                    output.write(buffer, 0, read);
                }
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(Files.newInputStream(compressed), digest))
            {
                byte[] buffer = new byte[64 * 1024];
                while (input.read(buffer) >= 0)
                {
                    if (closed) throw new IOException("Upload client closed");
                    // DigestInputStream updates the checksum while the prepared file is consumed.
                }
            }
            return new PreparedReplay(compressed, hex(digest.digest()), Files.size(compressed));
        }
        catch (IOException | NoSuchAlgorithmException exception)
        {
            if (compressed != null)
            {
                try
                {
                    Files.deleteIfExists(compressed);
                }
                catch (IOException ignored)
                {
                    // Preserve the preparation failure.
                }
            }
            throw new CompletionException(new ReplayUploadException("Unable to prepare replay upload", 0, exception));
        }
    }

    private static String hex(byte[] bytes)
    {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes)
        {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }

    private static <T> CompletableFuture<T> failed(Throwable error)
    {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }

    private static final class PreparedReplay
    {
        private final Path path;
        private final String sha256;
        private final long byteSize;

        private PreparedReplay(Path path, String sha256, long byteSize)
        {
            this.path = path;
            this.sha256 = sha256;
            this.byteSize = byteSize;
        }

        private void delete()
        {
            try
            {
                Files.deleteIfExists(path);
            }
            catch (IOException ignored)
            {
                // Temporary upload files are also eligible for operating-system cleanup.
            }
        }
    }

    private static final class CreateRequest
    {
        @SerializedName("recording_id")
        private final String recordingId;
        @SerializedName("source_sha256")
        private final String sourceSha256;
        @SerializedName("source_byte_size")
        private final long sourceByteSize;

        private CreateRequest(String recordingId, String sourceSha256, long sourceByteSize)
        {
            this.recordingId = recordingId;
            this.sourceSha256 = sourceSha256;
            this.sourceByteSize = sourceByteSize;
        }
    }

    private static final class ErrorResponse
    {
        private String error;
    }

    private static final class ReplayResponse
    {
        private long id;
        @SerializedName("recording_id")
        private String recordingId;
        private String status;
        @SerializedName("failure_code")
        private String failureCode;

        private ReplayUploadResult result()
        {
            return new ReplayUploadResult(id, recordingId, status, failureCode);
        }
    }
}
