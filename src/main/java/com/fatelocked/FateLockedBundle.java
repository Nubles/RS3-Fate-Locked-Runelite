package com.fatelocked;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import lombok.Getter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parsed bundle exported by the Fate Locked web app.
 *
 * v3 wire format (v1/v2 bundles still load; missing fields degrade gracefully):
 * <pre>
 * {
 *   "version": 3,
 *   "chunkOffset": { "cx": 0, "cy": 0 },
 *   "chunks":         { "Asgarnia": [{"cx":46,"cy":52}], ... },   // continents
 *   "subAreaChunks":  { "Falador":  [{"cx":46,"cy":52}], ... },   // named areas
 *   "regionGroups":   { "Asgarnia": ["Falador", "Port Sarim"], ... },
 *   "unlockedRegions": ["Falador", "Lumbridge"],
 *   "state": { "keys": 3, "specialKeys": 0, "chaosKeys": 0,
 *              "fatePoints": 12, "activeBuff": "NONE", "pinnedGoals": [],
 *              "linkedAccount": "Zezima" }   // v3: account the run is bound to
 * }
 * </pre>
 *
 * Lock state mirrors the web app exactly: a chunk inside a named sub-area
 * (e.g. Falador) is unlocked iff that sub-area is unlocked; chunks in unnamed
 * terrain fall back to their continent; Misthalin and its starter areas are
 * always unlocked.
 */
@Getter
public class FateLockedBundle
{
    public enum LockState { UNLOCKED, LOCKED, UNAUTHORED }
    public enum Reach { REACHABLE, LOCKED, UNKNOWN }

    private final String runId;
    private final String profileName;
    private final int version;

    /** Continent name → set of canonical chunks owned by that continent. */
    private final Map<String, Set<CanonicalChunk>> regionChunks;
    /** Named sub-area (Falador, Port Sarim, …) → its chunks. v2 only. */
    private final Map<String, Set<CanonicalChunk>> subAreaChunks;
    /** Continent → its child sub-area names. v2 only. */
    private final Map<String, List<String>> regionGroups;
    /** Sub-area names the player has unlocked. */
    private final Set<String> unlockedRegions;
    /** Item-id (as string) → equipment tier, for the over-tier gear warning. v3+, optional. */
    private final Map<String, Integer> itemTiers;
    /** Slim "what's here" per chunk: "cx,cy" → category ("mon"/"shop"/"farm"/"poi") → names. v3+, optional. */
    private final Map<String, Map<String, List<String>>> chunkContent;
    /** Category key → display label, in render order. */
    private static final String[][] CONTENT_CATS = {
        { "mon", "Monsters" }, { "shop", "Shops" }, { "farm", "Farming" }, { "poi", "Points" }
    };
    /** Live run state for the HUD (null on v1 bundles). */
    private final RunState state;

    private final Map<CanonicalChunk, String> chunkToRegion;
    private final Map<CanonicalChunk, String> chunkToSubArea;
    /** Misthalin + its starter areas: always unlocked, mirroring the app. */
    private final Set<String> alwaysUnlocked;

    /** Cached unlock-progress counts for the HUD. */
    @Getter private final int unlockedChunks;
    @Getter private final int totalChunks;
    @Getter private final int unlockedAreas;
    @Getter private final int totalAreas;
    /** Normalised monster name → chunks it appears in (lazy; from chunkContent). */
    private Map<String, Set<CanonicalChunk>> monsterIndex;

    private FateLockedBundle(RawBundle raw,
                             Map<String, Set<CanonicalChunk>> regionChunks,
                             Map<String, Set<CanonicalChunk>> subAreaChunks,
                             Map<CanonicalChunk, String> chunkToRegion,
                             Map<CanonicalChunk, String> chunkToSubArea)
    {
        this.runId = raw == null ? null : raw.runId;
        this.profileName = raw == null ? null : raw.profileName;
        this.version = raw == null ? 0 : raw.version;
        this.regionChunks = regionChunks;
        this.subAreaChunks = subAreaChunks;
        this.regionGroups = raw == null || raw.regionGroups == null
            ? Collections.<String, List<String>>emptyMap() : raw.regionGroups;
        this.unlockedRegions = raw == null || raw.unlockedRegions == null
            ? Collections.<String>emptySet() : new HashSet<>(raw.unlockedRegions);
        this.chunkContent = raw == null || raw.chunkContent == null
            ? Collections.<String, Map<String, List<String>>>emptyMap() : raw.chunkContent;
        this.itemTiers = raw == null || raw.itemTiers == null
            ? Collections.<String, Integer>emptyMap() : raw.itemTiers;
        this.state = raw == null ? null : raw.state;
        this.chunkToRegion = chunkToRegion;
        this.chunkToSubArea = chunkToSubArea;

        Set<String> always = new HashSet<>();
        always.add("Misthalin");
        List<String> misthalinKids = this.regionGroups.get("Misthalin");
        if (misthalinKids != null) always.addAll(misthalinKids);
        this.alwaysUnlocked = always;

        // Unlock progress (computed once; lockStateAt/isUnlocked are ready now).
        int tc = 0, uc = 0;
        for (Set<CanonicalChunk> set : regionChunks.values())
        {
            for (CanonicalChunk c : set)
            {
                tc++;
                if (lockStateAt(c) == LockState.UNLOCKED) uc++;
            }
        }
        this.totalChunks = tc;
        this.unlockedChunks = uc;

        Set<String> areas = subAreaChunks.isEmpty() ? regionChunks.keySet() : subAreaChunks.keySet();
        int ua = 0;
        for (String a : areas) if (isUnlocked(a)) ua++;
        this.totalAreas = areas.size();
        this.unlockedAreas = ua;
    }

    public static FateLockedBundle empty()
    {
        return new FateLockedBundle(null,
            Collections.<String, Set<CanonicalChunk>>emptyMap(),
            Collections.<String, Set<CanonicalChunk>>emptyMap(),
            Collections.<CanonicalChunk, String>emptyMap(),
            Collections.<CanonicalChunk, String>emptyMap());
    }

    public static FateLockedBundle loadFromFile(Gson gson, Path path) throws IOException, JsonSyntaxException
    {
        String json = new String(Files.readAllBytes(path));
        return loadFromJson(gson, json);
    }

    public static FateLockedBundle loadFromJson(Gson gson, String json) throws JsonSyntaxException
    {
        RawBundle raw = gson.fromJson(json, RawBundle.class);
        if (raw == null || raw.chunks == null)
        {
            return empty();
        }

        int offsetCx = raw.chunkOffset != null ? raw.chunkOffset.cx : 0;
        int offsetCy = raw.chunkOffset != null ? raw.chunkOffset.cy : 0;

        Map<String, Set<CanonicalChunk>> byRegion = new HashMap<>();
        Map<CanonicalChunk, String> regionReverse = new HashMap<>();
        index(raw.chunks, offsetCx, offsetCy, byRegion, regionReverse);

        Map<String, Set<CanonicalChunk>> bySubArea = new HashMap<>();
        Map<CanonicalChunk, String> subReverse = new HashMap<>();
        if (raw.subAreaChunks != null)
        {
            index(raw.subAreaChunks, offsetCx, offsetCy, bySubArea, subReverse);
        }

        return new FateLockedBundle(raw, byRegion, bySubArea, regionReverse, subReverse);
    }

    private static void index(Map<String, List<RawChunk>> source, int offsetCx, int offsetCy,
                              Map<String, Set<CanonicalChunk>> byName,
                              Map<CanonicalChunk, String> reverse)
    {
        for (Map.Entry<String, List<RawChunk>> entry : source.entrySet())
        {
            if (entry.getValue() == null) continue;
            Set<CanonicalChunk> chunks = new HashSet<>();
            for (RawChunk rc : entry.getValue())
            {
                CanonicalChunk c = new CanonicalChunk(rc.cx - offsetCx, rc.cy - offsetCy);
                chunks.add(c);
                reverse.putIfAbsent(c, entry.getKey());
            }
            byName.put(entry.getKey(), chunks);
        }
    }

    // ---- queries ---------------------------------------------------------------

    /** Continent the chunk belongs to, or null when unauthored. */
    public String regionAt(CanonicalChunk chunk)
    {
        return chunkToRegion.get(chunk);
    }

    /** Named sub-area the chunk belongs to (Falador, …), or null. v2 only. */
    public String subAreaAt(CanonicalChunk chunk)
    {
        return chunkToSubArea.get(chunk);
    }

    /**
     * "What's here" for a chunk as display lines ("Monsters: …", "Shops: …",
     * "Farming: …", "Points: …"); empty when none/unknown.
     */
    public List<String> contentAt(CanonicalChunk chunk)
    {
        Map<String, List<String>> e = chunkContent.get(chunk.getCx() + "," + chunk.getCy());
        if (e == null || e.isEmpty()) return Collections.<String>emptyList();
        List<String> out = new ArrayList<>();
        for (String[] cat : CONTENT_CATS)
        {
            List<String> v = e.get(cat[0]);
            if (v != null && !v.isEmpty()) out.add(cat[1] + ": " + String.join(", ", v));
        }
        return out;
    }

    /** "Falador · Asgarnia", "Asgarnia", or null when unauthored. */
    public String labelAt(CanonicalChunk chunk)
    {
        String sub = subAreaAt(chunk);
        String region = regionAt(chunk);
        if (sub != null && region != null && !sub.equals(region)) return sub + " · " + region;
        if (sub != null) return sub;
        return region;
    }

    /**
     * Is this name unlocked? Mirrors the app: always-free Misthalin areas, the
     * player's unlocked sub-areas, or a continent whose every child is unlocked.
     */
    public boolean isUnlocked(String name)
    {
        if (name == null) return false;
        if (alwaysUnlocked.contains(name)) return true;
        if (unlockedRegions.contains(name)) return true;
        List<String> children = regionGroups.get(name);
        if (children != null && !children.isEmpty())
        {
            for (String c : children)
            {
                if (!unlockedRegions.contains(c) && !alwaysUnlocked.contains(c)) return false;
            }
            return true;
        }
        return false;
    }

    /** Lock state of a chunk: sub-area first, continent fallback. */
    public LockState lockStateAt(CanonicalChunk chunk)
    {
        String sub = subAreaAt(chunk);
        if (sub != null) return isUnlocked(sub) ? LockState.UNLOCKED : LockState.LOCKED;
        String region = regionAt(chunk);
        if (region != null) return isUnlocked(region) ? LockState.UNLOCKED : LockState.LOCKED;
        return LockState.UNAUTHORED;
    }

    /**
     * Is a monster (e.g. a slayer task) reachable? REACHABLE if it appears in any
     * unlocked chunk, LOCKED if every chunk holding it is locked, UNKNOWN if we
     * have no location for it (its name isn't in the chunk-content summary).
     */
    public Reach monsterReach(String monsterName)
    {
        if (monsterName == null || monsterName.trim().isEmpty()) return Reach.UNKNOWN;
        if (monsterIndex == null) buildMonsterIndex();
        Set<CanonicalChunk> chunks = monsterIndex.get(normMonster(monsterName));
        if (chunks == null || chunks.isEmpty()) return Reach.UNKNOWN;
        for (CanonicalChunk c : chunks)
        {
            if (lockStateAt(c) == LockState.UNLOCKED) return Reach.REACHABLE;
        }
        return Reach.LOCKED;
    }

    private void buildMonsterIndex()
    {
        Map<String, Set<CanonicalChunk>> idx = new HashMap<>();
        for (Map.Entry<String, Map<String, List<String>>> e : chunkContent.entrySet())
        {
            List<String> mon = e.getValue().get("mon");
            if (mon == null) continue;
            String[] parts = e.getKey().split(",");
            if (parts.length != 2) continue;
            CanonicalChunk c;
            try { c = new CanonicalChunk(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])); }
            catch (NumberFormatException ex) { continue; }
            for (String m : mon)
            {
                idx.computeIfAbsent(normMonster(m), k -> new HashSet<>()).add(c);
            }
        }
        monsterIndex = idx;
    }

    /** Lowercase + drop a trailing 's' so slayer plurals match singular monster names. */
    private static String normMonster(String s)
    {
        String t = s.toLowerCase().trim();
        return t.endsWith("s") ? t.substring(0, t.length() - 1) : t;
    }

    // ---- wire format ---------------------------------------------------------

    /** Live run stats included in v2+ bundles for the in-game HUD. */
    @Getter
    public static final class RunState
    {
        int keys;
        int specialKeys;
        int chaosKeys;
        int fatePoints;
        String activeBuff;
        List<String> pinnedGoals;
        /** OSRS account the run is bound to (v3). null/empty on older bundles. */
        String linkedAccount;
        /** Per-slot unlocked equipment tier (e.g. {"Head":2}). v3+, optional. */
        Map<String, Integer> equipment;
    }

    private static final class RawBundle
    {
        int version;
        String runId;
        String profileName;
        RawChunk chunkOffset;
        Map<String, List<RawChunk>> chunks;
        Map<String, List<RawChunk>> subAreaChunks;
        Map<String, List<String>> regionGroups;
        List<String> unlockedRegions;
        Map<String, Map<String, List<String>>> chunkContent;
        Map<String, Integer> itemTiers;
        RunState state;
    }

    private static final class RawChunk
    {
        int cx;
        int cy;
    }
}
