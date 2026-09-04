package com.combatreplay;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class PairingClient
{
    private static final Gson GSON = new Gson();
    private final URI exchangeUri;
    private final HttpClient httpClient;

    public PairingClient(URI baseUri)
    {
        this(baseUri, HttpClient.newHttpClient());
    }

    PairingClient(URI baseUri, HttpClient httpClient)
    {
        exchangeUri = baseUri.resolve("/api/v1/pairing/exchange");
        this.httpClient = httpClient;
    }

    public CompletableFuture<PairingCredentials> exchange(
        String code,
        String deviceName,
        String pluginVersion,
        String runeLiteVersion)
    {
        PairingRequest payload = new PairingRequest(code, deviceName, pluginVersion, runeLiteVersion);
        HttpRequest request = HttpRequest.newBuilder(exchangeUri)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
            .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(this::parseResponse);
    }

    private PairingCredentials parseResponse(HttpResponse<String> response)
    {
        if (response.statusCode() != 201)
        {
            throw new PairingException(response.statusCode());
        }

        PairingResponse payload = GSON.fromJson(response.body(), PairingResponse.class);
        if (payload == null || payload.device == null || payload.token == null)
        {
            throw new PairingException(response.statusCode());
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
