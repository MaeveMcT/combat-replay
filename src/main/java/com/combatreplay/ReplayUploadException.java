package com.combatreplay;

public final class ReplayUploadException extends RuntimeException
{
    private final int statusCode;

    ReplayUploadException(String message, int statusCode, Throwable cause)
    {
        super(message, cause);
        this.statusCode = statusCode;
    }

    ReplayUploadException(String message, int statusCode)
    {
        this(message, statusCode, null);
    }

    public int getStatusCode()
    {
        return statusCode;
    }

    public boolean isTransientFailure()
    {
        return statusCode == 0 || statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }
}
