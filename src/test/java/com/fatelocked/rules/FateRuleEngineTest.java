package com.fatelocked.rules;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.FateLockedBundle;
import com.google.gson.Gson;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FateRuleEngineTest
{
    private FateLockedBundle fixture(String name) throws Exception
    {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name))
        {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return FateLockedBundle.loadFromJson(new Gson(), json);
        }
    }

    @Test
    public void preservesAuthoredStatusesAndMatchesNamesCaseInsensitively()
        throws Exception
    {
        FateRuleEngine engine = new FateRuleEngine(
            fixture("bundles/v4-rules.json"), true, false);
        CanonicalChunk chunk = new CanonicalChunk(50, 50);

        assertEquals(PermissionStatus.ALLOWED,
            engine.entry(chunk).getStatus());
        assertEquals(PermissionStatus.ALLOWED,
            engine.target(chunk, "shop", "LUMBRIDGE GENERAL STORE").getStatus());
        assertEquals(PermissionStatus.NOT_READY,
            engine.target(chunk, null, "Cook's Assistant").getStatus());
        assertEquals(PermissionStatus.LOCKED,
            engine.target(chunk, "NPC", "Goblin").getStatus());
        assertEquals(PermissionStatus.UNKNOWN,
            engine.target(chunk, "OBJECT", "Unmapped").getStatus());
        assertEquals(PermissionStatus.LOCKED,
            engine.equipment(4151).getStatus());
        assertEquals(PermissionStatus.UNKNOWN,
            engine.equipment(999999).getStatus());
        assertNull(engine.target(chunk, "OBJECT", "Unmapped").getReason());
    }

    @Test
    public void wrongAccountAndStaleRulesNeverBecomeLocked() throws Exception
    {
        FateLockedBundle bundle = fixture("bundles/v4-rules.json");
        CanonicalChunk chunk = new CanonicalChunk(50, 50);

        assertEquals(PermissionStatus.UNKNOWN,
            new FateRuleEngine(bundle, false, false)
                .target(chunk, "SHOP", "Lumbridge General Store").getStatus());
        assertEquals(PermissionStatus.UNKNOWN,
            new FateRuleEngine(bundle, true, true)
                .target(chunk, "SHOP", "Lumbridge General Store").getStatus());
    }

    @Test
    public void legacyAndUnauthoredChunksRemainUnknown() throws Exception
    {
        FateRuleEngine legacy = new FateRuleEngine(
            fixture("bundles/v3-standard.json"), true, false);
        assertEquals(PermissionStatus.UNKNOWN,
            legacy.entry(new CanonicalChunk(46, 52)).getStatus());
        assertEquals(PermissionStatus.UNKNOWN,
            legacy.target(new CanonicalChunk(46, 52), "BANK", "").getStatus());

        FateRuleEngine current = new FateRuleEngine(
            fixture("bundles/v4-rules.json"), true, false);
        assertEquals(PermissionStatus.UNKNOWN,
            current.entry(new CanonicalChunk(1, 1)).getStatus());
    }
}
