package com.combatreplay;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public final class PairingClient implements AutoCloseable
{
    private final Gson gson;
    private final URI exchangeUri;
    private final OkHttpClient httpClient;
    private volatile boolean closed;
    private CompletableFuture<?> pending;

    public PairingClient(URI baseUri, OkHttpClient httpClient, Gson gson)
    {
        validateBaseUri(baseUri);
        exchangeUri = baseUri.resolve("/api/v1/pairing/exchange");
        this.httpClient = Objects.requireNonNull(httpClient);
        this.gson = Objects.requireNonNull(gson);
    }

    private static void validateBaseUri(URI uri)
    {
        String host = uri.getHost();
        boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
            || "[::1]".equals(host);
        boolean secure = "https".equalsIgnoreCase(uri.getScheme());
        if (host == null || uri.getUserInfo() != null || !(secure || loopback && "http".equalsIgnoreCase(uri.getScheme())))
        {
            throw new IllegalArgumentException("Device pairing requires HTTPS except on localhost");
        }
    }

    public CompletableFuture<PairingCredentials> exchange(
        String code,
        String deviceName,
        String pluginVersion,
        String runeLiteVersion)
    {
        PairingRequest payload = new PairingRequest(code, deviceName, pluginVersion, runeLiteVersion);
        Request request = new Request.Builder().url(exchangeUri.toString())
            .header("Accept", "application/json")
            .post(RequestBody.create(MediaType.parse("application/json"), gson.toJson(payload)))
            .build();

        synchronized (this)
        {
            if (closed) throw new IllegalStateException("Pairing client closed");
            CompletableFuture<ReplayHttpRequests.Reply> response = ReplayHttpRequests.send(httpClient, request, 20);
            pending = response;
            response.whenComplete((result, error) -> clearPending(response));
            return response.thenApply(this::parseResponse);
        }
    }

    private synchronized void clearPending(CompletableFuture<?> response)
    {
        if (pending == response) pending = null;
    }

    @Override
    public synchronized void close()
    {
        closed = true;
        if (pending != null) pending.cancel(true);
        pending = null;
    }

    private PairingCredentials parseResponse(ReplayHttpRequests.Reply response)
    {
        if (response.status != 201)
        {
            throw new PairingException(response.status);
        }

        PairingResponse payload = gson.fromJson(response.body, PairingResponse.class);
        if (payload == null || payload.device == null || payload.token == null)
        {
            throw new PairingException(response.status);
        }
        return new PairingCredentials(
            payload.token,
            payload.device.id,
            Objects.requireNonNull(payload.device.name));
    }

    private static final class PairingRequest
    {
        private final String code;
        @SerializedName("device_name")
        private final String deviceName;
        @SerializedName("plugin_version")
        private final String pluginVersion;
        @SerializedName("runelite_version")
        private final String runeLiteVersion;

        private PairingRequest(String code, String deviceName, String pluginVersion, String runeLiteVersion)
        {
            this.code = code;
            this.deviceName = deviceName;
            this.pluginVersion = pluginVersion;
            this.runeLiteVersion = runeLiteVersion;
        }
    }

    private static final class PairingResponse
    {
        private String token;
        private DeviceResponse device;
    }

    private static final class DeviceResponse
    {
        private long id;
        private String name;
    }
}
