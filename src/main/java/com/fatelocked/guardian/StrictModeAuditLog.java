package com.fatelocked.guardian;

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
import java.util.List;

public final class StrictModeAuditLog
{
    private static final int MAX_ENTRIES = 100;
    private final Gson gson;
    private final Path path;
    private final List<StrictModeAuditEntry> entries = new ArrayList<>();

    public StrictModeAuditLog(Gson gson, Path path) throws IOException
    {
        this.gson = gson;
        this.path = path;
        load();
    }

    public synchronized void append(StrictModeAuditEntry entry) throws IOException
    {
        if (entry == null) return;
        entries.add(entry);
        while (entries.size() > MAX_ENTRIES) entries.remove(0);
        persist();
    }

    public synchronized List<StrictModeAuditEntry> recent(int limit)
    {
        int count = Math.max(0, Math.min(limit, entries.size()));
        List<StrictModeAuditEntry> result = new ArrayList<>();
        for (int i = entries.size() - 1; i >= entries.size() - count; i--)
        {
            result.add(entries.get(i));
        }
        return Collections.unmodifiableList(result);
    }

    private void load() throws IOException
    {
        if (!Files.exists(path)) return;
        try
        {
            State state = gson.fromJson(
                new String(Files.readAllBytes(path), StandardCharsets.UTF_8),
                State.class);
            if (state != null && state.entries != null) entries.addAll(state.entries);
            while (entries.size() > MAX_ENTRIES) entries.remove(0);
        }
        catch (JsonParseException ex)
        {
            entries.clear();
        }
    }

    private void persist() throws IOException
    {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        State state = new State();
        state.entries = new ArrayList<>(entries);
        Files.write(temporary,
            gson.toJson(state).getBytes(StandardCharsets.UTF_8));
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
        List<StrictModeAuditEntry> entries;
    }
}
