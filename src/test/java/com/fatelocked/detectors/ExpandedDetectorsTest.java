package com.fatelocked.detectors;

import com.google.gson.Gson;
import org.junit.Test;

import java.nio.file.Files;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExpandedDetectorsTest
{
    @Test
    public void slayerPersistsAndDeduplicatesCompletion() throws Exception
    {
        java.nio.file.Path path = Files.createTempFile("slayer", ".json");
        Files.delete(path);
        SlayerTaskDetector detector = new SlayerTaskDetector(new Gson(), path);
        detector.assignment("Abyssal demons", "Duradel", 150, false);
        assertTrue(detector.completion("return to a Slayer master").isPresent());
        assertFalse(detector.completion("duplicate").isPresent());
        assertFalse(new SlayerTaskDetector(new Gson(), path)
            .completion("restart duplicate").isPresent());
    }

    @Test
    public void diaryEmitsOnlyZeroToOne()
    {
        DiaryTierReviewDetector detector = new DiaryTierReviewDetector();
        assertTrue(detector.onVarbit("Ardougne Easy", 0, 1).isPresent());
        assertFalse(detector.onVarbit("Ardougne Easy", 1, 1).isPresent());
    }

    @Test
    public void petRequiresNewPetSignature()
    {
        PetDropDetector detector = new PetDropDetector();
        assertEquals("Vorki", detector.detect(
            "You have a funny feeling like you're being followed.", 8029, 10_000)
            .map(DetectedEvent::getCanonicalLabel).orElse(null));
        assertFalse(detector.detect("Your pet is insured.", 8029, 20_000).isPresent());
    }

    @Test
    public void pestControlRequiresBothSignals()
    {
        MinigameCompletionDetector detector = new MinigameCompletionDetector();
        assertFalse(detector.onMessage("You have won the game!", 1000).isPresent());
        detector.onPestControlWidget(2000);
        assertTrue(detector.onMessage("You have won the game!", 3000).isPresent());
        assertFalse(detector.onMessage("You have completed a farming contract.", 3001).isPresent());
    }

    @Test
    public void bossV2UsesNamedMappingNotCombatLevel()
    {
        BossKillDetectorV2 detector = new BossKillDetectorV2();
        assertTrue(detector.detect("NPC", "Vorkath", 1).isPresent());
        assertFalse(detector.detect("NPC", "Abyssal demon", 2).isPresent());
        assertFalse(detector.detect("NPC", "Vorkath", 1).isPresent());
    }
}
