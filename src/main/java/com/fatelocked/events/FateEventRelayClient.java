package com.fatelocked.events;

import com.fatelocked.FateLockedConfig;
import com.google.gson.Gson;
import net.runelite.client.config.ConfigManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

public class FateEventRelayClient
{
    private static final MediaType JSON = MediaType.parse("application/json");
    private static final int MAX_FLUSH = 20;
    private static final long[] RETRY_DELAYS_MS = { 5_000, 10_000, 20_000, 40_000, 60_000 };

    interface TokenStore
    {
        String get(String key);
        void put(String key, String value);
    }

    private final OkHttpClient client;
    private final Gson gson;
    private final BooleanSupplier enabled;
    private final TokenStore tokens;
    private final AtomicBoolean flushInFlight = new AtomicBoolean();
    private final AtomicBoolean acknowledgementInFlight = new AtomicBoolean();
    private volatile int failureCount;
    private volatile long retryAfter;

    public FateEventRelayClient(
        OkHttpClient client,
        Gson gson,
        ConfigManager configManager,
        FateLockedConfig config)
    {
        this(client, gson, config::onlineSync, new TokenStore()
        {
            @Override
            public String get(String key)
            {
                return configManager.getConfiguration(FateLockedConfig.GROUP, key);
            }

            @Override
            public void put(String key, String value)
            {
                configManager.setConfiguration(FateLockedConfig.GROUP, key, value);
            }
        });
    }

    FateEventRelayClient(
        OkHttpClient client,
        Gson gson,
        BooleanSupplier enabled,
        TokenStore tokens)
    {
        this.client = client;
        this.gson = gson;
        this.enabled = enabled;
        this.tokens = tokens;
    }

    public void flush(String relayBase, String code, FateEventOutbox outbox)
    {
        if (!canContact(relayBase, code) || System.currentTimeMillis() < retryAfter
            || !flushInFlight.compareAndSet(false, true))
        {
            return;
        }
        List<FateEvent> pending = outbox.pending();
        if (pending.isEmpty())
        {
            flushInFlight.set(false);
            return;
        }
        List<FateEvent> batch = new ArrayList<>(
            pending.subList(0, Math.min(MAX_FLUSH, pending.size())));
        Map<String, Object> body = new HashMap<>();
        String token = tokens.get(tokenKey(code));
        if (token != null && !token.trim().isEmpty()) body.put("token", token);
        body.put("events", batch);

        Request request;
        try
        {
            request = new Request.Builder()
                .url(resourceUrl(relayBase, code, "events"))
                .post(RequestBody.create(JSON, gson.toJson(body)))
                .build();
        }
        catch (IllegalArgumentException ex)
        {
            flushInFlight.set(false);
            noteFailure();
            return;
        }

        client.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException exception)
            {
                noteFailure();
                flushInFlight.set(false);
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                try (Response closed = response)
                {
                    if (!closed.isSuccessful() || closed.body() == null)
                    {
                        noteFailure();
                        return;
                    }
                    AppendResponse parsed = gson.fromJson(closed.body().string(), AppendResponse.class);
                    if (parsed != null && parsed.token != null)
                    {
                        tokens.put(tokenKey(code), parsed.token);
                    }
                    noteSuccess();
                }
                catch (Exception ex)
                {
                    noteFailure();
                }
                finally
                {
                    flushInFlight.set(false);
                }
            }
        });
    }

    public void pollAcknowledgements(String relayBase, String code, FateEventOutbox outbox)
    {
        if (!canContact(relayBase, code)
            || !acknowledgementInFlight.compareAndSet(false, true))
        {
            return;
        }
        Request request;
        try
        {
            request = new Request.Builder()
                .url(resourceUrl(relayBase, code, "acks"))
                .build();
        }
        catch (IllegalArgumentException ex)
        {
            acknowledgementInFlight.set(false);
            return;
        }

        client.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException exception)
            {
                acknowledgementInFlight.set(false);
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                try (Response closed = response)
                {
                    if (closed.code() == 404 || !closed.isSuccessful() || closed.body() == null) return;
                    AcknowledgementBatch parsed = gson.fromJson(
                        closed.body().string(), AcknowledgementBatch.class);
                    if (parsed == null || parsed.acknowledgements == null) return;
                    Set<String> terminal = new HashSet<>();
                    for (Acknowledgement acknowledgement : parsed.acknowledgements)
                    {
                        if (acknowledgement != null && acknowledgement.eventId != null
                            && ("COMPLETED".equals(acknowledgement.state)
                            || "DISMISSED".equals(acknowledgement.state)
                            || "DUPLICATE".equals(acknowledgement.state)))
                        {
                            terminal.add(acknowledgement.eventId);
                        }
                    }
                    outbox.acknowledge(terminal);
                }
                catch (Exception ignored)
                {
                    // A malformed response or local persistence failure leaves the event pending.
                }
                finally
                {
                    acknowledgementInFlight.set(false);
                }
            }
        });
    }

    private boolean canContact(String relayBase, String code)
    {
        return enabled.getAsBoolean()
            && relayBase != null && !relayBase.trim().isEmpty()
            && code != null && !code.trim().isEmpty();
    }

    private String resourceUrl(String relayBase, String code, String resource)
    {
        return relayBase.trim().replaceAll("/+$", "") + "/r/" + code.trim() + "/" + resource;
    }

    private String tokenKey(String code)
    {
        return "eventToken." + code.trim();
    }

    private synchronized void noteFailure()
    {
        failureCount = Math.min(failureCount + 1, RETRY_DELAYS_MS.length);
        retryAfter = System.currentTimeMillis() + RETRY_DELAYS_MS[failureCount - 1];
    }

    private synchronized void noteSuccess()
    {
        failureCount = 0;
        retryAfter = 0;
    }

    private static final class AppendResponse
    {
        String token;
    }

    private static final class AcknowledgementBatch
    {
        List<Acknowledgement> acknowledgements;
    }

    private static final class Acknowledgement
    {
        String eventId;
        String state;
    }
}
