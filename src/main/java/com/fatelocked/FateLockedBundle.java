package com.fatelocked;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import lombok.Getter;
import lombok.Value;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

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
 *   "unlockedChunks": ["50,50", "50,51"],   // Chunked mode only — see below
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
 *
 * <p><b>Chunked mode</b> is a different unlock model entirely — one map-region
 * chunk unlocked at a time, not whole named areas — so it carries its own
 * field, {@code unlockedChunks}, instead of {@code unlockedRegions}. Its
 * <em>presence</em> in the payload (not its length) is what marks a bundle as
 * Chunked: a fresh Chunked run has zero chunks rolled yet, so the array is
 * legitimately empty at the very start, and that must not be mistaken for
 * "not a Chunked bundle" — the free start chunk (the Lumbridge castle
 * courtyard, cx=50,cy=50, matching utils/chunkAdjacency.ts's CHUNKED_START on
 * the web side) still needs to read as unlocked in-game from the first
 * moment. When a bundle carries this field, {@link #lockStateAt} and
 * {@link #isUnlocked} resolve purely from raw chunk-coordinate membership
 * instead of named-region unlock state.
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
    /**
     * Chunked mode's raw unlock set — present (possibly empty) only when the
     * bundle came from a Chunked-mode run; null for every other mode, which
     * is how {@link #isChunkedBundle()} tells the two apart. Always includes
     * {@link #CHUNKED_START}, mirroring the web app's always-free start chunk.
     */
    private final Set<CanonicalChunk> chunkedUnlockedSet;
    /** The Lumbridge castle courtyard chunk — Chunked mode's fixed, always-free
     *  starting point. Matches CHUNKED_START in utils/chunkAdjacency.ts. */
    public static final CanonicalChunk CHUNKED_START = new CanonicalChunk(50, 50);
    /** Item-id (as string) → equipment tier, for the over-tier gear warning. v3+, optional. */
    private final Map<String, Integer> itemTiers;
    /** Normalised slayer task name → chunks its monster appears in (complete coverage). v3+, optional. */
    private final Map<String, Set<CanonicalChunk>> slayerChunks;
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
    /** The mode's free-at-start areas (bundle freeAreas; Misthalin fallback). */
    private final Set<String> alwaysUnlocked;
    /** Sub-area name → its parent continent, derived from regionGroups. */
    private final Map<String, String> parentContinent;

    /** Cached unlock-progress counts for the HUD. */
    @Getter private final int unlockedChunks;
    /** Bank-locked modes: whether banking is gated, and the bank-chunk ids
     *  (canonical "cx*256+cy") the player has rolled. Mirrors the web app's
     *  rules.bankLocks + unlocks.banks; empty/false in unrestricted runs. */
    private final boolean bankLocks;
    private final Set<String> unlockedBanks;
    @Getter private final int totalChunks;
    @Getter private final int unlockedAreas;
    @Getter private final int totalAreas;
    /** Normalised monster name → chunks it appears in (lazy; from chunkContent). */
    private Map<String, Set<CanonicalChunk>> monsterIndex;
    /** Chunks with a bank/deposit box, from chunkContent poi ("nearest bank" HUD). */
    private final Set<CanonicalChunk> bankChunks;
    /** Chunks with at least one shop, from chunkContent ("nearest shop" HUD). */
    private final Set<CanonicalChunk> shopChunks;

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

        // Chunked mode: unlockedChunks' mere presence (even as an empty list)
        // marks this as a Chunked bundle — see the class javadoc. Offsets are
        // applied the same way index() applies them to chunks/subAreaChunks,
        // for consistency, even though current exports always use {0,0}.
        if (raw != null && raw.unlockedChunks != null)
        {
            int offsetCx = raw.chunkOffset != null ? raw.chunkOffset.cx : 0;
            int offsetCy = raw.chunkOffset != null ? raw.chunkOffset.cy : 0;
            Set<CanonicalChunk> parsed = new HashSet<>();
            parsed.add(CHUNKED_START);
            for (String key : raw.unlockedChunks)
            {
                CanonicalChunk c = parseChunkKey(key);
                if (c != null) parsed.add(new CanonicalChunk(c.getCx() - offsetCx, c.getCy() - offsetCy));
            }
            this.chunkedUnlockedSet = parsed;
        }
        else
        {
            this.chunkedUnlockedSet = null;
        }

        this.bankLocks = raw != null && raw.bankLocks;
        Set<String> banks = new HashSet<>();
        if (raw != null && raw.unlockedBanks != null)
        {
            for (String id : raw.unlockedBanks) if (id != null) banks.add(id.trim());
        }
        this.unlockedBanks = banks;

        this.chunkContent = raw == null || raw.chunkContent == null
            ? Collections.<String, Map<String, List<String>>>emptyMap() : raw.chunkContent;
        this.itemTiers = raw == null || raw.itemTiers == null
            ? Collections.<String, Integer>emptyMap() : raw.itemTiers;

        Map<String, Set<CanonicalChunk>> slayer = new HashMap<>();
        if (raw != null && raw.slayerChunks != null)
        {
            for (Map.Entry<String, List<RawChunk>> e : raw.slayerChunks.entrySet())
            {
                Set<CanonicalChunk> set = new HashSet<>();
                if (e.getValue() != null)
                {
                    for (RawChunk rc : e.getValue()) set.add(new CanonicalChunk(rc.cx, rc.cy));
                }
                slayer.put(e.getKey(), set);
            }
        }
        this.slayerChunks = slayer;
        this.state = raw == null ? null : raw.state;
        this.chunkToRegion = chunkToRegion;
        this.chunkToSubArea = chunkToSubArea;

        // Bank/shop chunk indexes for the nearest-unlocked HUD lines. Banks are
        // recognised by any poi entry containing "bank" — booths, chests and
        // deposit boxes all count for an ironman.
        Set<CanonicalChunk> bankSet = new HashSet<>();
        Set<CanonicalChunk> shopSet = new HashSet<>();
        for (Map.Entry<String, Map<String, List<String>>> e : this.chunkContent.entrySet())
        {
            CanonicalChunk c = parseChunkKey(e.getKey());
            if (c == null) continue;
            List<String> shop = e.getValue().get("shop");
            if (shop != null && !shop.isEmpty()) shopSet.add(c);
            List<String> poi = e.getValue().get("poi");
            if (poi != null)
            {
                for (String p : poi)
                {
                    if (p != null && p.toLowerCase().contains("bank"))
                    {
                        bankSet.add(c);
                        break;
                    }
                }
            }
        }
        this.bankChunks = bankSet;
        this.shopChunks = shopSet;

        // Free-at-start baseline. v3.1+ bundles carry the mode's actual free
        // set (full Misthalin / Lumbridge-only / none); older bundles fall
        // back to the historical full-Misthalin assumption.
        Set<String> always = new HashSet<>();
        if (raw != null && raw.freeAreas != null)
        {
            for (String a : raw.freeAreas) if (a != null) always.add(a);
        }
        else
        {
            always.add("Misthalin");
            List<String> misthalinKids = this.regionGroups.get("Misthalin");
            if (misthalinKids != null) always.addAll(misthalinKids);
        }
        this.alwaysUnlocked = always;

        // Sub-area → parent continent, for the continent-level unlock rules.
        Map<String, String> parents = new HashMap<>();
        for (Map.Entry<String, List<String>> e : this.regionGroups.entrySet())
        {
            if (e.getValue() == null) continue;
            for (String sub : e.getValue()) parents.putIfAbsent(sub, e.getKey());
        }
        this.parentContinent = parents;

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

        if (isChunkedBundle())
        {
            // Chunked mode doesn't unlock whole named areas at a time, so the
            // "areas" concept doesn't apply the way it does for every other
            // mode — alias it to the (already chunk-accurate) chunk counts
            // rather than leaving it permanently 0/0 in the HUD.
            this.totalAreas = tc;
            this.unlockedAreas = uc;
        }
        else
        {
            Set<String> areas = subAreaChunks.isEmpty() ? regionChunks.keySet() : subAreaChunks.keySet();
            int ua = 0;
            for (String a : areas) if (isUnlocked(a)) ua++;
            this.totalAreas = areas.size();
            this.unlockedAreas = ua;
        }
    }

    public static FateLockedBundle empty()
    {
        return new FateLockedBundle(null,
            Collections.<String, Set<CanonicalChunk>>emptyMap(),
            Collections.<String, Set<CanonicalChunk>>emptyMap(),
            Collections.<CanonicalChunk, String>emptyMap(),
            Collections.<CanonicalChunk, String>emptyMap());
    }

    /** Marker prefix the web app uses for a gzip+base64 clipboard payload. */
    private static final String GZ_PREFIX = "FLGZ:";

    public static FateLockedBundle loadFromFile(Gson gson, Path path) throws IOException, JsonSyntaxException
    {
        String json = new String(Files.readAllBytes(path));
        return loadFromJson(gson, json);
    }

    /** Inflate a base64-encoded gzip payload (the compressed clipboard form) to JSON. */
    private static String inflate(String base64)
    {
        try
        {
            byte[] gz = Base64.getDecoder().decode(base64.trim());
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz));
                 ByteArrayOutputStream out = new ByteArrayOutputStream())
            {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        }
        catch (IllegalArgumentException | IOException ex)
        {
            throw new JsonSyntaxException("Could not read compressed bundle", ex);
        }
    }

    public static FateLockedBundle loadFromJson(Gson gson, String json) throws JsonSyntaxException
    {
        // The web app compresses the clipboard copy (gzip+base64, "FLGZ:" prefix)
        // so it isn't dumping ~115 KB of mostly-static data onto the clipboard.
        // The downloaded file stays plain JSON. Accept both.
        String text = json != null && json.trim().startsWith(GZ_PREFIX)
            ? inflate(json.trim().substring(GZ_PREFIX.length())) : json;

        RawBundle raw = gson.fromJson(text, RawBundle.class);
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
        return contentAt(chunk, Integer.MAX_VALUE);
    }

    /** contentAt with each category capped at maxPerCat names ("…, +N more") —
     *  for the world-map hover tooltip, where a stacked dungeon chunk could
     *  otherwise list dozens of monsters. */
    public List<String> contentAt(CanonicalChunk chunk, int maxPerCat)
    {
        Map<String, List<String>> e = chunkContent.get(chunk.getCx() + "," + chunk.getCy());
        if (e == null || e.isEmpty()) return Collections.<String>emptyList();
        List<String> out = new ArrayList<>();
        for (String[] cat : CONTENT_CATS)
        {
            List<String> v = e.get(cat[0]);
            if (v == null || v.isEmpty()) continue;
            if (v.size() <= maxPerCat)
            {
                out.add(cat[1] + ": " + String.join(", ", v));
            }
            else
            {
                out.add(cat[1] + ": " + String.join(", ", v.subList(0, maxPerCat))
                    + ", +" + (v.size() - maxPerCat) + " more");
            }
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
        if (isChunkedBundle())
        {
            // Chunked mode: a named area is "unlocked" if the player has a
            // foothold in ANY of its chunks — quest/diary/resource data is
            // authored in named areas, not raw coords, so this is the closest
            // equivalent at that data granularity (mirrors
            // isNamedAreaReachableViaChunks in utils/reachability.ts).
            Set<CanonicalChunk> chunks = subAreaChunks.get(name);
            if (chunks == null || chunks.isEmpty()) chunks = regionChunks.get(name);
            if (chunks == null || chunks.isEmpty()) return false;
            for (CanonicalChunk c : chunks)
            {
                if (chunkedUnlockedSet.contains(c)) return true;
            }
            return false;
        }
        if (alwaysUnlocked.contains(name)) return true;
        if (unlockedRegions.contains(name)) return true;
        // Mirror the web map's isRegionUnlocked (utils/reachability.ts) —
        // pinned by the app's runelitePluginParity test:
        // a sub-area is unlocked when its parent continent is free/rolled
        // directly, or when the continent is complete (every sibling
        // unlocked-or-free); a continent when all its children are.
        String parent = parentContinent.get(name);
        if (parent != null)
        {
            if (alwaysUnlocked.contains(parent) || unlockedRegions.contains(parent)) return true;
            if (allUnlockedOrFree(regionGroups.get(parent))) return true;
        }
        return allUnlockedOrFree(regionGroups.get(name));
    }

    private boolean allUnlockedOrFree(List<String> names)
    {
        if (names == null || names.isEmpty()) return false;
        for (String n : names)
        {
            if (!unlockedRegions.contains(n) && !alwaysUnlocked.contains(n)) return false;
        }
        return true;
    }

    /**
     * Lock state of a chunk. Chunked-mode bundles resolve this purely from raw
     * chunk-coordinate membership; every other mode uses sub-area-first,
     * continent-fallback named-region resolution.
     */
    public LockState lockStateAt(CanonicalChunk chunk)
    {
        String sub = subAreaAt(chunk);
        String region = regionAt(chunk);
        if (sub == null && region == null) return LockState.UNAUTHORED;
        if (isChunkedBundle())
        {
            return chunkedUnlockedSet.contains(chunk) ? LockState.UNLOCKED : LockState.LOCKED;
        }
        if (sub != null) return isUnlocked(sub) ? LockState.UNLOCKED : LockState.LOCKED;
        return isUnlocked(region) ? LockState.UNLOCKED : LockState.LOCKED;
    }

    /**
     * Is a monster (e.g. a slayer task) reachable? REACHABLE if it appears in any
     * unlocked chunk, LOCKED if every chunk holding it is locked, UNKNOWN if we
     * have no location for it (its name isn't in the chunk-content summary).
     */
    public Reach monsterReach(String monsterName)
    {
        if (monsterName == null || monsterName.trim().isEmpty()) return Reach.UNKNOWN;
        String key = normMonster(monsterName);
        // Prefer the complete slayer index (uncapped); fall back to the slim
        // per-chunk monster summary (capped) for anything it doesn't cover.
        Set<CanonicalChunk> chunks = slayerChunks.get(key);
        if (chunks == null || chunks.isEmpty())
        {
            if (monsterIndex == null) buildMonsterIndex();
            chunks = monsterIndex.get(key);
        }
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

    /** Parse a "cx,cy" chunk key (as used in unlockedChunks/chunkContent); null if malformed. */
    private static CanonicalChunk parseChunkKey(String key)
    {
        if (key == null) return null;
        String[] parts = key.split(",");
        if (parts.length != 2) return null;
        try
        {
            return new CanonicalChunk(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        }
        catch (NumberFormatException ex)
        {
            return null;
        }
    }

    /** Is this a Chunked-mode bundle? Determined by unlockedChunks' presence, not its length. */
    /**
     * Chunked mode's frontier: this (authored) chunk is locked but orthogonally
     * adjacent to an unlocked chunk — i.e. it's one of the chunks the next roll
     * could land on. Mirrors utils/chunkAdjacency.ts isFrontierChunk. Always
     * false outside Chunked bundles. Callers pass authored chunks (the overlay
     * only iterates the authored grid), so authored-ness isn't re-checked.
     */
    public boolean isFrontierChunk(CanonicalChunk chunk)
    {
        if (!isChunkedBundle()) return false;
        if (chunkedUnlockedSet.contains(chunk)) return false;
        return chunkedUnlockedSet.contains(new CanonicalChunk(chunk.getCx() + 1, chunk.getCy()))
            || chunkedUnlockedSet.contains(new CanonicalChunk(chunk.getCx() - 1, chunk.getCy()))
            || chunkedUnlockedSet.contains(new CanonicalChunk(chunk.getCx(), chunk.getCy() + 1))
            || chunkedUnlockedSet.contains(new CanonicalChunk(chunk.getCx(), chunk.getCy() - 1));
    }

    /** Nearest-usable query result: target chunk + straight-line chunk distance. */
    @Value
    public static class Nearest
    {
        CanonicalChunk chunk;
        int distanceChunks;
    }

    /** Whether the bundle carries chunk-content to power nearest queries (v3+). */
    public boolean hasNearestData()
    {
        return !bankChunks.isEmpty() || !shopChunks.isEmpty();
    }

    /** Closest chunk with a usable bank (region-unlocked AND, in bank-locked
     *  runs, individually rolled). Distance 0 = the from chunk itself. Null
     *  when no bank anywhere is usable. */
    public Nearest nearestUsableBank(CanonicalChunk from)
    {
        return nearest(from, bankChunks, true);
    }

    /** Closest chunk with a shop the player can reach. */
    public Nearest nearestUsableShop(CanonicalChunk from)
    {
        return nearest(from, shopChunks, false);
    }

    private Nearest nearest(CanonicalChunk from, Set<CanonicalChunk> candidates, boolean requireBankUnlock)
    {
        if (from == null || candidates.isEmpty()) return null;
        CanonicalChunk best = null;
        int bestDist = Integer.MAX_VALUE;
        for (CanonicalChunk c : candidates)
        {
            if (lockStateAt(c) != LockState.UNLOCKED) continue;
            if (requireBankUnlock && !isBankUnlocked(c)) continue;
            int d = Math.max(Math.abs(c.getCx() - from.getCx()), Math.abs(c.getCy() - from.getCy()));
            // Ties break toward smaller cx, then cy, so the result is stable
            // frame-to-frame regardless of set iteration order.
            if (d < bestDist
                || (d == bestDist && best != null
                    && (c.getCx() < best.getCx()
                        || (c.getCx() == best.getCx() && c.getCy() < best.getCy()))))
            {
                best = c;
                bestDist = d;
            }
        }
        return best == null ? null : new Nearest(best, bestDist);
    }

    /** Does this run lock banks individually (rules.bankLocks)? */
    public boolean banksLocked()
    {
        return bankLocks;
    }

    /** Is the bank at this chunk usable? True when banks aren't locked; else
     *  the chunk's canonical id must be in the rolled set. */
    public boolean isBankUnlocked(CanonicalChunk chunk)
    {
        if (!bankLocks) return true;
        return unlockedBanks.contains(Integer.toString(chunk.getCx() * 256 + chunk.getCy()));
    }

    public boolean isChunkedBundle()
    {
        return chunkedUnlockedSet != null;
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
        List<String> freeAreas;
        List<String> unlockedChunks;
        boolean bankLocks;
        List<String> unlockedBanks;
        Map<String, Map<String, List<String>>> chunkContent;
        Map<String, Integer> itemTiers;
        Map<String, List<RawChunk>> slayerChunks;
        RunState state;
    }

    private static final class RawChunk
    {
        int cx;
        int cy;
    }
}
