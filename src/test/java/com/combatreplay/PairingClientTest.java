package com.combatreplay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okio.Timeout;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PairingClientTest
{
    private final Gson gson = new Gson();
    private final OkHttpClient http = new OkHttpClient();
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
    public void acceptsHttpsHostnamesAndIpAddressesAndLocalHttpOnly()
    {
        for (String address : new String[]{"https://replay.example.com", "https://192.0.2.1:3000",
            "https://[2001:db8::1]:3000", "http://localhost:3000", "http://127.0.0.1:3000",
            "http://[::1]:3000"})
        {
            new PairingClient(URI.create(address), http, gson);
        }
        assertThrows(IllegalArgumentException.class,
            () -> new PairingClient(URI.create("http://192.0.2.1:3000"), http, gson));
    }

    @Test
    public void pairingRequestHasTimeoutAndCanBeCancelledOnShutdown()
    {
        OkHttpClient mockHttp = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        Timeout timeout = new Timeout();
        when(mockHttp.newCall(any(Request.class))).thenReturn(call);
        when(call.timeout()).thenReturn(timeout);
        PairingClient client = new PairingClient(baseUri, mockHttp, gson);
        client.exchange("CODE", "desktop", "1", "1");
        assertEquals(TimeUnit.SECONDS.toNanos(20), timeout.timeoutNanos());
        client.close();
        verify(call).cancel();
    }

    @Test
    public void closedPairingClientRejectsNewRequests()
    {
        PairingClient client = new PairingClient(baseUri, http, gson);
        client.close();
        assertThrows(IllegalStateException.class,
            () -> client.exchange("CODE", "desktop", "1", "1"));
    }

    @Test
    public void exchangesPairingCodeAgainstVersionedApi() throws Exception
    {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/api/v1/pairing/exchange", exchange ->
        {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = ("{\"token\":\"42.device-secret\","
                + "\"device\":{\"id\":42,\"name\":\"Gaming desktop\"}}")
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        PairingClient client = new PairingClient(baseUri, http, gson);
        PairingCredentials credentials = client.exchange(
            "ABCDE-FG234", "Gaming desktop", "1.0.0", "1.11.0")
            .get(5, TimeUnit.SECONDS);

        assertEquals("42.device-secret", credentials.getToken());
        assertEquals(42L, credentials.getDeviceId());
        assertEquals("Gaming desktop", credentials.getDeviceName());
        assertTrue(requestBody.get().contains("\"code\":\"ABCDE-FG234\""));
        assertTrue(requestBody.get().contains("\"plugin_version\":\"1.0.0\""));
    }
}
