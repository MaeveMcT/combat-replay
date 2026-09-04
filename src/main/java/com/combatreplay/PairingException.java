package com.combatreplay;

public final class PairingException extends RuntimeException
{
    private final int statusCode;

    PairingException(int statusCode)
    {
        super("Pairing request failed with HTTP status " + statusCode);
        this.statusCode = statusCode;
    }

    public int getStatusCode()
    {
        return statusCode;
    }
}
