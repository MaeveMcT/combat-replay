package com.combatreplay;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.zip.GZIPOutputStream;

public final class ReplayUploadClient implements AutoCloseable
{
    private static final Gson GSON = new Gson();
    private final URI baseUri;
    private final HttpClient httpClient;
    private final Executor preparationExecutor;
    private volatile boolean closed;
    private final java.util.Set<CompletableFuture<?>> requests = new java.util.HashSet<>();

    public ReplayUploadClient(URI baseUri, Executor preparationExecutor)
    {
        this(baseUri, HttpClient.newHttpClient(), preparationExecutor);
    }

    ReplayUploadClient(URI baseUri, HttpClient httpClient, Executor preparationExecutor)
    {
        validateBaseUri(baseUri);
        this.baseUri = baseUri;
        this.httpClient = httpClient;
        this.preparationExecutor = preparationExecutor;
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
        HttpRequest request = request(apiUri("/api/v1/replays/" + replayId), token).GET().build();
        return send(request, 200);
    }

    private CompletableFuture<ReplayResponse> create(String recordingId, String token, PreparedReplay prepared)
    {
        CreateRequest payload = new CreateRequest(recordingId, prepared.sha256, prepared.byteSize);
        HttpRequest request = request(apiUri("/api/v1/replays"), token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
            .build();
        return sendResponse(request, 200, 201);
    }

    private CompletableFuture<ReplayResponse> transfer(long replayId, String token, PreparedReplay prepared)
    {
        final HttpRequest.BodyPublisher body;
        try
        {
            body = HttpRequest.BodyPublishers.ofFile(prepared.path);
        }
        catch (IOException exception)
        {
            return failed(new ReplayUploadException("Unable to read prepared replay", 0, exception));
        }
        HttpRequest request = request(apiUri("/api/v1/replays/" + replayId + "/upload"), token)
            .header("Content-Type", "application/gzip")
            .header("Content-Encoding", "gzip")
            .PUT(body)
            .build();
        return sendResponse(request, 200);
    }

    private CompletableFuture<ReplayUploadResult> complete(long replayId, String token)
    {
        HttpRequest request = request(apiUri("/api/v1/replays/" + replayId + "/complete"), token)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        return send(request, 202);
    }

    private CompletableFuture<ReplayUploadResult> send(HttpRequest request, int... expectedStatuses)
    {
        return sendResponse(request, expectedStatuses).thenApply(ReplayResponse::result);
    }

    private synchronized CompletableFuture<ReplayResponse> sendResponse(HttpRequest request, int... expectedStatuses)
    {
        if (closed) return failed(new ReplayUploadException("Upload client closed", 0));
        CompletableFuture<HttpResponse<String>> pending = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
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
                    expected |= response.statusCode() == status;
                }
                if (!expected)
                {
                    String code = null;
                    try
                    {
                        ErrorResponse failure = GSON.fromJson(response.body(), ErrorResponse.class);
                        if (response.statusCode() == 409 && failure != null)
                        {
                            code = failure.error;
                        }
                    }
                    catch (RuntimeException ignored)
                    {
                        // Error pages are not necessarily JSON; retain only the status.
                    }
                    throw new CompletionException(new ReplayUploadException(
                        "Replay upload request failed with HTTP status " + response.statusCode(), response.statusCode())
                        .withFailureCode(code));
                }
                ReplayResponse payload = GSON.fromJson(response.body(), ReplayResponse.class);
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

    private HttpRequest.Builder request(URI uri, String token)
    {
        return HttpRequest.newBuilder(uri)
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
