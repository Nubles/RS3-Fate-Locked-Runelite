package com.fatelocked.events;

import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FateEventRelayClientTest
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Gson gson = new Gson();
    private MockWebServer server;
    private FateEventOutbox outbox;
    private Map<String, String> tokens;

    @Before
    public void setUp() throws Exception
    {
        server = new MockWebServer();
        server.start();
        Path path = folder.getRoot().toPath().resolve("outbox.json");
        outbox = new FateEventOutbox(gson, path);
        outbox.enqueue(event("evt-1"));
        tokens = new HashMap<>();
    }

    @After
    public void tearDown() throws Exception
    {
        server.shutdown();
    }

    private FateEvent event(String eventId)
    {
        return FateEvent.builder()
            .protocolVersion(1)
            .eventId(eventId)
            .runId("run-1")
            .account("Nubles")
            .runRevision(1)
            .eventType(FateEventType.QUEST)
            .canonicalLabel("Dragon Slayer")
            .occurredAt(System.currentTimeMillis())
            .sessionSequence(1)
            .bundleVersion(3)
            .rulesVersion("1")
            .contentVersion(1)
            .detectorId("quest-widget-v1")
            .detectorVersion(1)
            .confidence(EventConfidence.EXACT)
            .evidence(Collections.<String, Object>emptyMap())
            .build();
    }

    private FateEventRelayClient client()
    {
        return new FateEventRelayClient(
            new OkHttpClient(), gson, () -> true,
            new FateEventRelayClient.TokenStore()
            {
                @Override
                public String get(String key)
                {
                    return tokens.get(key);
                }

                @Override
                public void put(String key, String value)
                {
                    tokens.put(key, value);
                }
            });
    }

    @Test
    public void flushSendsEnvelopeAndRetainsUntilAcknowledged() throws Exception
    {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{\"version\":1,\"token\":\"tok\",\"accepted\":[\"evt-1\"],\"duplicates\":[]}"));

        client().flush(server.url("/").toString(), "ABCD", outbox);

        RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/r/ABCD/events", request.getPath());
        assertEquals("evt-1", gson.fromJson(request.getBody().readUtf8(), com.google.gson.JsonObject.class)
            .getAsJsonArray("events").get(0).getAsJsonObject()
            .get("eventId").getAsString());
        assertTrue(outbox.contains("evt-1"));
    }

    @Test
    public void failedSendCanRetryTheSameOccurrenceId() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(500));
        client().flush(server.url("/").toString(), "ABCD", outbox);
        RecordedRequest failed = server.takeRequest(2, TimeUnit.SECONDS);

        server.enqueue(new MockResponse().setResponseCode(200)
            .setBody("{\"version\":2,\"token\":\"tok\",\"accepted\":[\"evt-1\"],\"duplicates\":[]}"));
        client().flush(server.url("/").toString(), "ABCD", outbox);
        RecordedRequest retry = server.takeRequest(2, TimeUnit.SECONDS);

        String firstId = gson.fromJson(failed.getBody().readUtf8(), com.google.gson.JsonObject.class)
            .getAsJsonArray("events").get(0).getAsJsonObject().get("eventId").getAsString();
        String retryId = gson.fromJson(retry.getBody().readUtf8(), com.google.gson.JsonObject.class)
            .getAsJsonArray("events").get(0).getAsJsonObject().get("eventId").getAsString();
        assertEquals(firstId, retryId);
    }

    @Test
    public void acknowledgementRemovesTerminalEvent() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(200)
            .setBody("{\"version\":1,\"acknowledgements\":["
                + "{\"eventId\":\"evt-1\",\"state\":\"COMPLETED\",\"acknowledgedAt\":1}]}"));

        client().pollAcknowledgements(server.url("/").toString(), "ABCD", outbox);
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
        for (int attempt = 0; attempt < 40 && outbox.contains("evt-1"); attempt++)
        {
            Thread.sleep(25);
        }

        assertTrue(!outbox.contains("evt-1"));
    }
    @Test
    public void disabledSyncMakesNoNetworkRequest() throws Exception
    {
        FateEventRelayClient disabled = new FateEventRelayClient(
            new OkHttpClient(), gson, () -> false,
            new FateEventRelayClient.TokenStore()
            {
                @Override
                public String get(String key) { return null; }

                @Override
                public void put(String key, String value) { }
            });

        disabled.flush(server.url("/").toString(), "ABCD", outbox);
        disabled.pollAcknowledgements(server.url("/").toString(), "ABCD", outbox);

        assertEquals(null, server.takeRequest(200, TimeUnit.MILLISECONDS));
        assertTrue(outbox.contains("evt-1"));
    }
}
