package com.fatelocked;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static table of common teleport destinations, used to warn before a teleport
 * would drop the player into a locked chunk. Spell / jewellery / tablet menu
 * entries carry no world tile, so we resolve the destination by name instead.
 *
 * Keyed by a lowercase place keyword → canonical chunk (cx = x>>6, cy = y>>6).
 * Coverage is the common spellbook (standard / ancient / lunar / Arceuus),
 * teleport jewellery, and a few staple items — not exhaustive, but the teleports
 * a chunk-locked ironman actually leans on.
 */
final class Teleports
{
    private Teleports() {}

    // Item-name keywords that mark a menu entry as a teleport even when its text
    // doesn't contain the word "teleport" (e.g. jewellery destination options).
    private static final String[] TELE_ITEMS = {
        "teleport", "amulet of glory", "amulet of eternal glory", "ring of dueling",
        "ring of duelling", "games necklace", "combat bracelet", "skills necklace",
        "ring of wealth", "necklace of passage", "burning amulet", "digsite pendant",
        "slayer ring", "ectophial", "royal seed pod", "enchanted lyre",
        "drakan's medallion", "xeric's talisman", "chronicle", "ring of the elements",
        // Transport networks whose right-click options name a destination.
        "spirit tree", "gnome glider", "glider", "charter", "fairy ring", "quetzal",
    };

    private static final Map<String, int[]> PLACES = new LinkedHashMap<>();
    private static final List<String> KEYS_BY_LEN;

    private static void p(String key, int cx, int cy) { PLACES.put(key, new int[]{ cx, cy }); }

    static
    {
        // Standard spellbook
        p("lumbridge", 50, 50);
        p("varrock", 50, 53);
        p("falador", 46, 52);
        p("camelot", 43, 54);
        p("seers", 43, 54);
        p("ardougne", 41, 51);
        p("watchtower", 39, 48);
        p("yanille", 39, 48);
        p("trollheim", 45, 57);
        p("ape atoll", 43, 43);
        p("kourend", 25, 57);

        // Arceuus / library
        p("arceuus library", 25, 59);
        p("draynor manor", 48, 52);
        p("mind altar", 46, 54);
        p("salve graveyard", 53, 54);
        p("fenkenstrain", 55, 55);
        p("west ardougne", 39, 51);
        p("harmony island", 59, 44);
        p("cemetery", 46, 58);
        p("barrows", 55, 51);
        p("battlefront", 21, 58);

        // Ancient
        p("senntisten", 51, 52);
        p("kharyrll", 54, 54);
        p("canifis", 54, 54);
        p("lassar", 46, 54);
        p("dareeyak", 46, 57);
        p("carrallangar", 49, 57);
        p("annakarl", 51, 60);
        p("ghorrock", 46, 60);

        // Lunar
        p("moonclan", 32, 61);
        p("ourania", 38, 50);
        p("waterbirth", 39, 58);
        p("barbarian", 39, 55);
        p("khazard", 41, 49);
        p("fishing guild", 40, 53);
        p("catherby", 43, 53);
        p("ice plateau", 46, 61);

        // Jewellery — amulet of glory
        p("edgeville", 48, 54);
        p("karamja", 45, 49);
        p("draynor village", 48, 50);
        p("al kharid", 51, 49);
        // Ring of dueling
        p("castle wars", 38, 48);
        p("duel arena", 51, 50);
        p("pvp arena", 51, 50);
        p("emir's arena", 51, 50);
        p("ferox enclave", 49, 56);
        // Games necklace
        p("burthorpe", 45, 55);
        p("barbarian outpost", 39, 55);
        p("wintertodt", 25, 61);
        p("tournament", 34, 44);
        p("soul wars", 34, 44);
        // Combat bracelet
        p("warriors' guild", 45, 55);
        p("champions' guild", 49, 52);
        p("monastery", 47, 54);
        p("ranging guild", 41, 53);
        // Skills necklace
        p("crafting guild", 45, 51);
        p("cooking guild", 49, 53);
        p("woodcutting guild", 25, 54);
        p("farming guild", 19, 58);
        p("mining guild", 47, 52);
        // Ring of wealth
        p("miscellania", 39, 60);
        p("grand exchange", 49, 54);
        p("falador park", 46, 52);
        // Necklace of passage
        p("wizards' tower", 48, 49);
        p("the outpost", 37, 52);
        p("eagles' eyrie", 53, 49);
        // Burning amulet (wilderness)
        p("chaos temple", 50, 56);
        p("bandit camp", 47, 57);
        p("lava maze", 47, 60);
        // Digsite pendant
        p("digsite", 52, 53);
        p("fossil island", 58, 59);
        p("lithkren", 55, 61);
        // Slayer ring
        p("stronghold slayer cave", 38, 53);
        p("slayer tower", 53, 55);
        p("fremennik", 43, 56);
        // Items
        p("ver sinhaza", 57, 50);
        p("darkmeyer", 56, 52);
        p("rellekka", 41, 56);
        p("xeric's lookout", 24, 55);

        // Spirit tree network (right-click options are destination names)
        p("tree gnome stronghold", 38, 53);
        p("gnome stronghold", 38, 53);
        p("tree gnome village", 39, 49);
        p("battlefield of khazard", 41, 49);
        p("etceteria", 40, 60);
        p("brimhaven", 43, 50);
        p("hosidius", 24, 53);
        // Gnome glider network
        p("sindarpos", 44, 54);
        p("ta quir priw", 38, 53);
        p("lemanto andra", 51, 53);
        p("kar-hewo", 51, 50);
        p("gandius", 46, 46);
        p("lemantolly undri", 39, 46);
        p("ookookolly undri", 43, 43);

        List<String> keys = new ArrayList<>(PLACES.keySet());
        keys.sort((a, b) -> b.length() - a.length()); // longest first for specificity
        KEYS_BY_LEN = keys;
    }

    /**
     * Destination chunk for a teleport menu entry, or null when the entry isn't a
     * recognised teleport or its destination is unknown.
     */
    static CanonicalChunk destinationChunk(String option, String target)
    {
        String text = ((option == null ? "" : option) + " " + (target == null ? "" : target))
            .toLowerCase()
            .replaceAll("<[^>]*>", " "); // strip colour/format tags

        boolean looksTele = false;
        for (String item : TELE_ITEMS)
        {
            if (text.contains(item)) { looksTele = true; break; }
        }
        if (!looksTele) return null;

        for (String key : KEYS_BY_LEN)
        {
            if (text.contains(key))
            {
                int[] c = PLACES.get(key);
                return new CanonicalChunk(c[0], c[1]);
            }
        }
        return null;
    }
}
