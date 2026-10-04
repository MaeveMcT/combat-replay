package com.combatreplay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.runelite.client.util.Filepath;

import static com.combatreplay.TestFilepaths.filepath;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ReplayUploadClientTest
{
    private static final String RECORDING_ID = "123e4567-e89b-42d3-a456-426614174000";
    private final Gson gson = new Gson();
    private final OkHttpClient http = new OkHttpClient();

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private HttpServer server;
    private URI baseUri;

    @Before
    public void setUp() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        server.start();
    }

    @After
    public void tearDown()
    {
        server.stop(0);
    }

    @Test
    public void preparesAndUploadsGzipReplayThroughVersionedApi() throws Exception
    {
        String sourceJson = "{\"format_version\":1,\"recording_id\":\"" + RECORDING_ID + "\"}";
        Path source = temporary.newFile("recording.json").toPath();
        Files.writeString(source, sourceJson, StandardCharsets.UTF_8);
        AtomicReference<JsonObject> createRequest = new AtomicReference<>();
        AtomicReference<byte[]> uploaded = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();

        server.createContext("/api/v1/replays", exchange ->
        {
            createRequest.set(new JsonParser().parse(new String(
                exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
            respond(exchange, 201, response("awaiting_upload"));
        });
        server.createContext("/api/v1/replays/42/upload", exchange ->
        {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            assertEquals("gzip", exchange.getRequestHeaders().getFirst("Content-Encoding"));
            uploaded.set(exchange.getRequestBody().readAllBytes());
            respond(exchange, 200, response("uploaded"));
        });
        server.createContext("/api/v1/replays/42/complete", exchange ->
            respond(exchange, 202, response("processing")));

        ReplayUploadResult result = new ReplayUploadClient(baseUri, http, Runnable::run, gson)
            .upload(filepath(source), RECORDING_ID, "42.device-secret")
            .get(5, TimeUnit.SECONDS);

        assertEquals(42L, result.getReplayId());
        assertEquals("processing", result.getStatus());
        assertEquals("Bearer 42.device-secret", authorization.get());
        assertEquals(RECORDING_ID, createRequest.get().get("recording_id").getAsString());
        assertEquals(uploaded.get().length, createRequest.get().get("source_byte_size").getAsLong());
        assertEquals(hex(MessageDigest.getInstance("SHA-256").digest(uploaded.get())),
            createRequest.get().get("source_sha256").getAsString());
        assertEquals(sourceJson, gunzip(uploaded.get()));
        assertTrue("local recording remains available", Files.exists(source));
        assertNoUploadTemporaryFiles();
    }

    @Test
    public void acceptsHttpsHostnamesAndIpAddressesAndLocalHttpOnly()
    {
        for (String address : new String[]{"https://replay.example.com", "https://192.0.2.1:3000",
            "https://[2001:db8::1]:3000", "http://localhost:3000", "http://127.0.0.1:3000",
            "http://[::1]:3000"})
        {
            new ReplayUploadClient(URI.create(address), http, Runnable::run, gson);
        }
        assertThrows(IllegalArgumentException.class,
            () -> new ReplayUploadClient(URI.create("http://192.0.2.1:3000"), http, Runnable::run, gson));
    }

    @Test
    public void classifiesServerFailuresForRetry() throws Exception
    {
        server.createContext("/api/v1/replays", exchange -> respond(exchange, 503, "{}"));
        Path source = temporary.newFile("recording.json").toPath();
        Files.writeString(source, "{}", StandardCharsets.UTF_8);

        try
        {
            new ReplayUploadClient(baseUri, http, Runnable::run, gson)
                .upload(filepath(source), RECORDING_ID, "token")
                .get(5, TimeUnit.SECONDS);
        }
        catch (java.util.concurrent.ExecutionException exception)
        {
            Throwable cause = exception.getCause();
            while (cause.getCause() != null && !(cause instanceof ReplayUploadException))
            {
                cause = cause.getCause();
            }
            assertTrue(cause instanceof ReplayUploadException);
            assertTrue(((ReplayUploadException) cause).isTransientFailure());
            assertEquals(503, ((ReplayUploadException) cause).getStatusCode());
            assertNoUploadTemporaryFiles();
            return;
        }
        throw new AssertionError("Expected upload to fail");
    }

    @Test
    public void storageFullPausesPersistentlyUntilExplicitRetry() throws Exception
    {
        java.util.concurrent.atomic.AtomicInteger requests = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicBoolean full = new java.util.concurrent.atomic.AtomicBoolean(true);
        server.createContext("/api/v1/replays", exchange ->
        {
            requests.incrementAndGet();
            respond(exchange, full.get() ? 409 : 201,
                full.get() ? "{\"error\":\"storage_full\"}" : response("awaiting_upload"));
        });
        server.createContext("/api/v1/replays/42/upload", exchange ->
        {
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 200, response("uploaded"));
        });
        server.createContext("/api/v1/replays/42/complete", exchange -> respond(exchange, 202, response("ready")));
        Path source = temporary.newFile("quota-recording.json").toPath();
        Files.writeString(source, "{}", StandardCharsets.UTF_8);
        UploadSidecarStore sidecars = new UploadSidecarStore(new com.google.gson.Gson(), Filepath.Unchecked.getRooted(temporary.getRoot().toPath()));
        java.util.concurrent.ScheduledExecutorService executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        java.util.concurrent.CountDownLatch paused = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(1);
        ReplayUploadClient client = new ReplayUploadClient(baseUri, http, Runnable::run, gson);
        try (ReplayUploadQueue queue = new ReplayUploadQueue(client, sidecars, executor, "token", message ->
        {
            if (message.startsWith("Storage full")) paused.countDown();
            if (message.equals("Web replay ready")) ready.countDown();
        }))
        {
            queue.enqueue(filepath(source), RECORDING_ID);
            assertTrue(paused.await(5, TimeUnit.SECONDS));
            UploadSidecarState state = sidecars.load(RECORDING_ID);
            assertEquals("paused", state.status);
            assertEquals("storage_full", state.failureCode);
            assertEquals(0L, state.nextAttemptAtEpochMillis);
            assertEquals(1, requests.get());
            assertTrue(Files.exists(source));
            // A fresh queue also respects the persisted pause, including after a restart.
            try (ReplayUploadQueue resumed = new ReplayUploadQueue(new ReplayUploadClient(baseUri, http, Runnable::run, gson), sidecars, executor, "token"))
            {
                resumed.enqueue(filepath(source), RECORDING_ID);
                executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
                assertEquals(1, requests.get());
            }
            // A persisted path must not override the Filepath selected from the library.
            state.sourcePath = "/outside-plugin-data/recording.json";
            sidecars.save(state);
            full.set(false);
            queue.enqueue(filepath(source), RECORDING_ID, true);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            assertEquals(2, requests.get());
            assertEquals("ready", sidecars.load(RECORDING_ID).status);
            assertTrue(Files.exists(source));
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    private void assertNoUploadTemporaryFiles() throws IOException
    {
        try (java.util.stream.Stream<Path> paths = Files.list(temporary.getRoot().toPath()))
        {
            assertTrue(paths.noneMatch(path -> path.getFileName().toString().startsWith("combat-replay-upload-")));
        }
    }

    @Test
    public void arbitraryServerErrorsAreNotRetained()
    {
        ReplayUploadException failure = new ReplayUploadException("Rejected", 409)
            .withFailureCode("sensitive server payload");
        assertEquals(null, failure.getFailureCode());
        assertTrue(!failure.isTransientFailure());
    }

    private static String response(String status)
    {
        return "{\"id\":42,\"recording_id\":\"" + RECORDING_ID + "\",\"status\":\"" + status + "\","
            + "\"failure_code\":null}";
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException
    {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static String gunzip(byte[] compressed) throws IOException
    {
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = input.read(buffer)) >= 0)
            {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String hex(byte[] bytes)
    {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes)
        {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }
}
