package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;

import java.util.Collections;
import java.util.Optional;

public final class MinigameCompletionDetector
{
    private long pestControlWindowUntil;
    private boolean emitted;

    public void onPestControlWidget(long now)
    {
        pestControlWindowUntil = now + 5000;
        emitted = false;
    }

    public Optional<DetectedEvent> onMessage(String message, long now)
    {
        if (emitted || now > pestControlWindowUntil
            || !"you have won the game!".equalsIgnoreCase(
                message == null ? "" : message.trim()))
            return Optional.empty();
        emitted = true;
        return Optional.of(DetectedEvent.builder()
            .type(FateEventType.MINIGAME_COMPLETION)
            .canonicalLabel("Pest Control")
            .confidence(EventConfidence.UNCERTAIN)
            .detectorId("minigame-completion-v1")
            .detectorVersion(1)
            .evidence(Collections.<String, Object>singletonMap(
                "completion", "pest-control-win-v1"))
            .build());
    }
}
