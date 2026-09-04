package com.combatreplay;

public final class PairingCredentials
{
    private final String token;
    private final long deviceId;
    private final String deviceName;

    PairingCredentials(String token, long deviceId, String deviceName)
    {
        this.token = token;
        this.deviceId = deviceId;
        this.deviceName = deviceName;
    }

    public String getToken()
    {
        return token;
    }

    public long getDeviceId()
    {
        return deviceId;
    }

    public String getDeviceName()
    {
        return deviceName;
    }
}
