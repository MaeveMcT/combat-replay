package com.combatreplay;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class ReplayHttpRequests
{
    private ReplayHttpRequests()
    {
    }

    static CompletableFuture<Reply> send(OkHttpClient client, Request request, int timeoutSeconds)
    {
        Call call = client.newCall(request);
        if (timeoutSeconds > 0)
        {
            call.timeout().timeout(timeoutSeconds, TimeUnit.SECONDS);
        }
        CompletableFuture<Reply> result = new CompletableFuture<Reply>()
        {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning)
            {
                call.cancel();
                return super.cancel(mayInterruptIfRunning);
            }
        };
        call.enqueue(new Callback()
        {
            @Override
            public void onFailure(Call ignored, IOException exception)
            {
                result.completeExceptionally(exception);
            }

            @Override
            public void onResponse(Call ignored, Response response)
            {
                try (Response closeable = response)
                {
                    ResponseBody body = response.body();
                    result.complete(new Reply(response.code(), body == null ? "" : body.string()));
                }
                catch (IOException exception)
                {
                    result.completeExceptionally(exception);
                }
            }
        });
        return result;
    }

    static final class Reply
    {
        final int status;
        final String body;

        Reply(int status, String body)
        {
            this.status = status;
            this.body = body;
        }
    }
}
