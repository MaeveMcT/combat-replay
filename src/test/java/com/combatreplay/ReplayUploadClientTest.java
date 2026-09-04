package com.combatreplay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

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
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ReplayUploadClientTest
{
    private static final String RECORDING_ID = "123e4567-e89b-42d3-a456-426614174000";

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

        ReplayUploadResult result = new ReplayUploadClient(baseUri, Runnable::run)
            .upload(source, RECORDING_ID, "42.device-secret")
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
    }

    @Test
    public void refusesToSendDeviceTokenOverRemotePlaintextHttp()
    {
        assertThrows(IllegalArgumentException.class,
            () -> new ReplayUploadClient(URI.create("http://example.com"), Runnable::run));
    }

    @Test
    public void classifiesServerFailuresForRetry() throws Exception
    {
        server.createContext("/api/v1/replays", exchange -> respond(exchange, 503, "{}"));
        Path source = temporary.newFile("recording.json").toPath();
        Files.writeString(source, "{}", StandardCharsets.UTF_8);

        try
        {
            new ReplayUploadClient(baseUri, Runnable::run)
                .upload(source, RECORDING_ID, "token")
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
            return;
        }
        throw new AssertionError("Expected upload to fail");
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
