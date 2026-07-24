package com.fatelocked.events;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FateEventOutbox
{
    private static final int MAX_PENDING = 250;
    private static final long ACK_RETENTION_MS = 30L * 24 * 60 * 60 * 1000;

    private final Gson gson;
    private final Path path;
    private final List<FateEvent> pending = new ArrayList<>();
    private final Map<String, Long> acknowledged = new HashMap<>();

    public FateEventOutbox(Gson gson, Path path) throws IOException
    {
        this.gson = gson;
        this.path = path;
        load();
    }

    public synchronized boolean enqueue(FateEvent event) throws IOException
    {
        if (event == null || event.getEventId() == null
            || acknowledged.containsKey(event.getEventId()) || contains(event.getEventId())
            || pending.size() >= MAX_PENDING)
        {
            return false;
        }
        pending.add(event);
        persist();
        return true;
    }

    public synchronized List<FateEvent> pending()
    {
        return Collections.unmodifiableList(new ArrayList<>(pending));
    }

    public synchronized void acknowledge(Set<String> eventIds) throws IOException
    {
        if (eventIds == null || eventIds.isEmpty()) return;
        long now = System.currentTimeMillis();
        pending.removeIf(event -> eventIds.contains(event.getEventId()));
        for (String eventId : eventIds)
        {
            if (eventId != null) acknowledged.put(eventId, now);
        }
        pruneAcknowledgements(now);
        persist();
    }

    public synchronized boolean contains(String eventId)
    {
        for (FateEvent event : pending)
        {
            if (event.getEventId().equals(eventId)) return true;
        }
        return false;
    }

    private void load() throws IOException
    {
        if (!Files.exists(path)) return;
        try
        {
            State state = gson.fromJson(
                new String(Files.readAllBytes(path), StandardCharsets.UTF_8),
                State.class);
            if (state == null) throw new JsonParseException("empty outbox");
            if (state.pending != null) pending.addAll(state.pending);
            if (state.acknowledged != null) acknowledged.putAll(state.acknowledged);
            pruneAcknowledgements(System.currentTimeMillis());
        }
        catch (RuntimeException ex)
        {
            Path corrupt = path.resolveSibling(path.getFileName() + ".corrupt-"
                + System.currentTimeMillis());
            Files.move(path, corrupt, StandardCopyOption.REPLACE_EXISTING);
            pending.clear();
            acknowledged.clear();
        }
    }

    private void pruneAcknowledgements(long now)
    {
        Iterator<Map.Entry<String, Long>> iterator = acknowledged.entrySet().iterator();
        while (iterator.hasNext())
        {
            Map.Entry<String, Long> entry = iterator.next();
            if (entry.getValue() == null || now - entry.getValue() > ACK_RETENTION_MS)
            {
                iterator.remove();
            }
        }
    }

    private void persist() throws IOException
    {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        State state = new State();
        state.pending = new ArrayList<>(pending);
        state.acknowledged = new HashMap<>(acknowledged);
        Files.write(temporary, gson.toJson(state).getBytes(StandardCharsets.UTF_8));
        try
        {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        }
        catch (AtomicMoveNotSupportedException ex)
        {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static final class State
    {
        List<FateEvent> pending;
        Map<String, Long> acknowledged;
    }
}
