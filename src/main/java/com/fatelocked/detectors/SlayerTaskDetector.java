package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class SlayerTaskDetector
{
    private final Gson gson;
    private final Path path;
    private State state;

    public SlayerTaskDetector(Gson gson, Path path) throws IOException
    {
        this.gson = gson;
        this.path = path;
        state = Files.exists(path)
            ? gson.fromJson(Files.readString(path), State.class) : new State();
        if (state == null) state = new State();
    }

    public synchronized void assignment(
        String name, String master, int count, boolean joinedMidAssignment)
        throws IOException
    {
        state.name = name;
        state.master = master;
        state.startCount = count;
        state.joinedMidAssignment = joinedMidAssignment;
        state.completed = false;
        persist();
    }

    public synchronized Optional<DetectedEvent> completion(String signature)
        throws IOException
    {
        if (state.name == null || state.completed) return Optional.empty();
        state.completed = true;
        persist();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("assignment", state.name);
        if (state.master != null) evidence.put("master", state.master);
        evidence.put("startCount", state.startCount);
        evidence.put("completionSignature", signature == null ? "" : signature);
        evidence.put("joinedMidAssignment", state.joinedMidAssignment);
        return Optional.of(DetectedEvent.builder()
            .type(FateEventType.SLAYER_TASK)
            .canonicalLabel(state.name)
            .confidence(EventConfidence.UNCERTAIN)
            .detectorId("slayer-task-v1")
            .detectorVersion(1)
            .evidence(evidence)
            .build());
    }

    public synchronized void cancel() throws IOException
    {
        state = new State();
        persist();
    }

    private void persist() throws IOException
    {
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, gson.toJson(state), StandardCharsets.UTF_8);
        try
        {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        }
        catch (java.nio.file.AtomicMoveNotSupportedException ex)
        {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static final class State
    {
        String name;
        String master;
        int startCount;
        boolean joinedMidAssignment;
        boolean completed;
    }
}
