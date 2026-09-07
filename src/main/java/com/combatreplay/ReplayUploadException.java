package com.combatreplay;

public final class ReplayUploadException extends RuntimeException
{
    private final int statusCode;
    private String failureCode;

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

    ReplayUploadException withFailureCode(String code)
    {
        // Never retain arbitrary server-provided error strings or payloads.
        failureCode = "storage_full".equals(code) ? code : null;
        return this;
    }

    public String getFailureCode()
    {
        return failureCode;
    }

    public boolean isTransientFailure()
    {
        return statusCode == 0 || statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }
}
