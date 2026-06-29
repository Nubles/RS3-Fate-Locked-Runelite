package com.fatelocked;

import com.google.gson.Gson;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.Notifier;
import net.runelite.client.game.ItemManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.RuneLite;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.Text;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.api.Point;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@PluginDescriptor(
    name = "Fate Locked Ironman",
    description = "Renders authored chunks from the Fate Locked tracker and warns on locked-region transitions",
    tags = { "chunk", "ironman", "locked", "map", "fate" }
)
public class FateLockedPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private FateLockedConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private FateLockedWorldMapOverlay worldMapOverlay;
    @Inject private FateLockedSceneOverlay sceneOverlay;
    @Inject private FateLockedMinimapOverlay minimapOverlay;
    @Inject private FateLockedHudOverlay hudOverlay;
    @Inject private FateLockedFlashOverlay flashOverlay;
    @Inject private ChatMessageManager chatMessageManager;
    @Inject private ClientToolbar clientToolbar;
    @Inject private FateLockedPanel panel;
    @Inject private Gson gson;
    @Inject private ScheduledExecutorService executor;
    @Inject private ItemManager itemManager;
    @Inject private Notifier notifier;
    @Inject private WorldMapPointManager worldMapPointManager;
    @Inject private InfoBoxManager infoBoxManager;
    @Inject private KeyManager keyManager;
    @Inject private OkHttpClient okHttpClient;

    private ScheduledFuture<?> relayPollFuture;
    private volatile String lastRelayVersion;

    /** Configurable hotkey: re-import the bundle from the clipboard. */
    private final HotkeyListener reimportHotkey = new HotkeyListener(() -> config.reimportHotkey())
    {
        @Override
        public void hotkeyPressed()
        {
            reimportFromClipboard();
        }
    };

    @Getter private volatile FateLockedBundle bundle = FateLockedBundle.empty();

    /** How long the locked-entry screen flash lasts. */
    public static final long LOCKED_FLASH_MS = 1600;
    @Getter private volatile long lockedFlashUntil;

    private CanonicalChunk lastChunk;
    private FateLockedBundle.LockState lastLockState;
    private NavigationButton navButton;
    private ScheduledFuture<?> watcherFuture;
    private Path watcherLoadedPath;
    private FileTime watcherLastModified;

    /** Last seen real level per skill, to detect genuine level-ups for roll nudges. */
    private final Map<Skill, Integer> lastLevels = new EnumMap<>(Skill.class);

    /** Achievement-diary completion varbits (1 = that tier done), watched for 0→1. */
    private static final int[] DIARY_VARBITS = {
        Varbits.DIARY_ARDOUGNE_EASY, Varbits.DIARY_ARDOUGNE_MEDIUM, Varbits.DIARY_ARDOUGNE_HARD, Varbits.DIARY_ARDOUGNE_ELITE,
        Varbits.DIARY_DESERT_EASY, Varbits.DIARY_DESERT_MEDIUM, Varbits.DIARY_DESERT_HARD, Varbits.DIARY_DESERT_ELITE,
        Varbits.DIARY_FALADOR_EASY, Varbits.DIARY_FALADOR_MEDIUM, Varbits.DIARY_FALADOR_HARD, Varbits.DIARY_FALADOR_ELITE,
        Varbits.DIARY_FREMENNIK_EASY, Varbits.DIARY_FREMENNIK_MEDIUM, Varbits.DIARY_FREMENNIK_HARD, Varbits.DIARY_FREMENNIK_ELITE,
        Varbits.DIARY_KANDARIN_EASY, Varbits.DIARY_KANDARIN_MEDIUM, Varbits.DIARY_KANDARIN_HARD, Varbits.DIARY_KANDARIN_ELITE,
        Varbits.DIARY_KARAMJA_EASY, Varbits.DIARY_KARAMJA_MEDIUM, Varbits.DIARY_KARAMJA_HARD, Varbits.DIARY_KARAMJA_ELITE,
        Varbits.DIARY_KOUREND_EASY, Varbits.DIARY_KOUREND_MEDIUM, Varbits.DIARY_KOUREND_HARD, Varbits.DIARY_KOUREND_ELITE,
        Varbits.DIARY_LUMBRIDGE_EASY, Varbits.DIARY_LUMBRIDGE_MEDIUM, Varbits.DIARY_LUMBRIDGE_HARD, Varbits.DIARY_LUMBRIDGE_ELITE,
        Varbits.DIARY_MORYTANIA_EASY, Varbits.DIARY_MORYTANIA_MEDIUM, Varbits.DIARY_MORYTANIA_HARD, Varbits.DIARY_MORYTANIA_ELITE,
        Varbits.DIARY_VARROCK_EASY, Varbits.DIARY_VARROCK_MEDIUM, Varbits.DIARY_VARROCK_HARD, Varbits.DIARY_VARROCK_ELITE,
        Varbits.DIARY_WESTERN_EASY, Varbits.DIARY_WESTERN_MEDIUM, Varbits.DIARY_WESTERN_HARD, Varbits.DIARY_WESTERN_ELITE,
        Varbits.DIARY_WILDERNESS_EASY, Varbits.DIARY_WILDERNESS_MEDIUM, Varbits.DIARY_WILDERNESS_HARD, Varbits.DIARY_WILDERNESS_ELITE,
    };
    /** Last seen value per diary varbit; first observation per login is a baseline (no nudge). */
    private final Map<Integer, Integer> diaryState = new HashMap<>();
    /** Widget group shown when a quest is completed (the reward scroll). */
    private static final int QUEST_COMPLETED_GROUP_ID = 153;
    /** Crystal key — a gold-key item icon for the Keys infobox. */
    private static final int KEYS_ICON_ITEM = 989;
    /** Plugin-specific data dir under .runelite/ — all file I/O is confined here. */
    private static final File DATA_DIR = new File(RuneLite.RUNELITE_DIR, "fate-locked");

    /** RuneLite equipment slot → the web app's slot name (for tier lookup). */
    private static final Map<EquipmentInventorySlot, String> SLOT_NAMES = new LinkedHashMap<>();
    static
    {
        SLOT_NAMES.put(EquipmentInventorySlot.HEAD, "Head");
        SLOT_NAMES.put(EquipmentInventorySlot.CAPE, "Cape");
        SLOT_NAMES.put(EquipmentInventorySlot.AMULET, "Neck");
        SLOT_NAMES.put(EquipmentInventorySlot.AMMO, "Ammo");
        SLOT_NAMES.put(EquipmentInventorySlot.WEAPON, "Weapon");
        SLOT_NAMES.put(EquipmentInventorySlot.BODY, "Body");
        SLOT_NAMES.put(EquipmentInventorySlot.SHIELD, "Shield");
        SLOT_NAMES.put(EquipmentInventorySlot.LEGS, "Legs");
        SLOT_NAMES.put(EquipmentInventorySlot.GLOVES, "Gloves");
        SLOT_NAMES.put(EquipmentInventorySlot.BOOTS, "Boots");
        SLOT_NAMES.put(EquipmentInventorySlot.RING, "Ring");
    }

    /** Active world-map markers for locked areas (so we can remove them on refresh). */
    private final List<WorldMapPoint> mapMarkers = new ArrayList<>();
    private BufferedImage lockedPinImage;

    /** Worn-gear slots currently above your unlocked tier, for the HUD (null = none). */
    @Getter private volatile String overTierSummary;
    /** Item ids already warned about this session, to avoid chat spam. */
    private final Set<Integer> warnedOverTier = new HashSet<>();

    /** Assigned slayer monster from chat (matches both "to kill X;" and Konar's "in <area>"). */
    private static final Pattern SLAYER_TASK =
        Pattern.compile("to kill\\s+(?:the\\s+)?(.+?)(?:\\s+in\\s+|[;:.])", Pattern.CASE_INSENSITIVE);
    /** Current slayer task monster name (raw), or null. */
    private String slayerTask;
    /** The locked slayer task to show on the HUD, or null. */
    @Getter private volatile String slayerTaskWarn;
    /** Task we've already chat-warned about, to warn at most once per assignment. */
    private String slayerWarnedFor;
    /** Last account name we warned about, so we nag at most once per character. */
    private String lastAccountWarned;


    @Provides
    FateLockedConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(FateLockedConfig.class);
    }

    @Override
    protected void startUp()
    {
        if (!DATA_DIR.exists()) DATA_DIR.mkdirs();
        overlayManager.add(worldMapOverlay);
        overlayManager.add(sceneOverlay);
        overlayManager.add(minimapOverlay);
        overlayManager.add(hudOverlay);
        overlayManager.add(flashOverlay);

        panel.setCallbacks(this::applyPastedBundle, () -> clientThread.invoke(this::reloadBundle));
        navButton = NavigationButton.builder()
            .tooltip("Fate Locked Ironman")
            .icon(createIcon())
            .priority(7)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navButton);

        reloadBundle();
        startWatcher();
        refreshInfoBoxes();
        keyManager.registerKeyListener(reimportHotkey);
        startRelayPoll();
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(worldMapOverlay);
        overlayManager.remove(sceneOverlay);
        overlayManager.remove(minimapOverlay);
        overlayManager.remove(hudOverlay);
        overlayManager.remove(flashOverlay);
        if (navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }
        stopWatcher();
        stopRelayPoll();
        keyManager.unregisterKeyListener(reimportHotkey);
        for (WorldMapPoint p : mapMarkers) worldMapPointManager.remove(p);
        mapMarkers.clear();
        infoBoxManager.removeIf(b -> b instanceof FateLockedInfoBox);
        bundle = FateLockedBundle.empty();
        lastChunk = null;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged ev)
    {
        if (!FateLockedConfig.GROUP.equals(ev.getGroup())) return;
        String key = ev.getKey();
        if ("autoReload".equals(key))
        {
            reloadBundle();
            stopWatcher();
            startWatcher();
        }
        else if ("warnOverTierGear".equals(key))
        {
            recomputeOverTierGear();
        }
        else if ("warnLockedSlayer".equals(key))
        {
            recomputeSlayer();
        }
        else if ("worldMapMarkers".equals(key))
        {
            refreshWorldMapMarkers();
        }
        else if ("showInfoBoxes".equals(key))
        {
            refreshInfoBoxes();
        }
        else if ("syncCode".equals(key) || "relayUrl".equals(key))
        {
            lastRelayVersion = null; // re-fetch from the new code/URL
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged ev)
    {
        if (ev.getGameState() == GameState.LOGGED_IN)
        {
            lastChunk = null; // force re-announce on next tick
            lastAccountWarned = null; // re-check the bound account for this login
            // The client fires StatChanged for every skill at login; clearing
            // here lets those re-establish the baseline without firing nudges.
            lastLevels.clear();
            diaryState.clear(); // re-baseline diaries this login (don't nudge already-done tiers)
            warnedOverTier.clear(); // re-warn over-tier gear once per session

        }
        else if (ev.getGameState() == GameState.LOGIN_SCREEN)
        {
            lastAccountWarned = null;
        }
    }

    // ── Roll reminders ────────────────────────────────────────────────────────
    // Read-only nudges: a chat line when something that may grant a roll in the
    // tracker happens (level-up, quest, diary, combat achievement). Purely
    // informational — the plugin never acts on the player's behalf.

    @Subscribe
    public void onStatChanged(StatChanged ev)
    {
        if (!config.rollNudges()) return;
        Skill skill = ev.getSkill();
        int level = ev.getLevel();
        Integer prev = lastLevels.put(skill, level);
        // prev == null is the login baseline; only nudge on a real increase.
        if (prev != null && level > prev)
        {
            nudge("Leveled " + skillName(skill) + " to " + level + " — may be worth a roll.");
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage ev)
    {
        if (ev.getType() != ChatMessageType.GAMEMESSAGE && ev.getType() != ChatMessageType.SPAM) return;
        String raw = ev.getMessage() == null ? "" : ev.getMessage();
        String m = raw.toLowerCase();

        // Combat achievements stay on chat (their varbits are progress counts with
        // totals that shift as Jagex adds tasks). Diaries are detected via varbit
        // (onVarbitChanged), quests via the reward widget — both more reliable.
        if (config.rollNudges() && m.contains("combat task:"))
        {
            nudge("Combat achievement complete — may be worth a roll.");
        }

        // Slayer assignment / task-check messages mention the monster.
        if (config.warnLockedSlayer() && m.contains("to kill"))
        {
            Matcher mat = SLAYER_TASK.matcher(raw);
            if (mat.find())
            {
                slayerTask = mat.group(1).trim();
                recomputeSlayer();
            }
        }
    }

    /** Re-check whether the current slayer task's monster is in an unlocked chunk. */
    private void recomputeSlayer()
    {
        if (!config.warnLockedSlayer() || slayerTask == null || slayerTask.isEmpty())
        {
            slayerTaskWarn = null;
            return;
        }
        FateLockedBundle.Reach reach = bundle.monsterReach(slayerTask);
        if (reach == FateLockedBundle.Reach.LOCKED)
        {
            slayerTaskWarn = slayerTask;
            if (!slayerTask.equalsIgnoreCase(slayerWarnedFor))
            {
                slayerWarnedFor = slayerTask;
                ChatMessageBuilder msg = new ChatMessageBuilder()
                    .append(ChatColorType.HIGHLIGHT).append("[Fate Locked] ")
                    .append(ChatColorType.NORMAL).append("Your slayer task (")
                    .append(ChatColorType.HIGHLIGHT).append(slayerTask)
                    .append(ChatColorType.NORMAL).append(") is in a locked area.");
                chatMessageManager.queue(QueuedMessage.builder()
                    .type(ChatMessageType.GAMEMESSAGE)
                    .runeLiteFormattedMessage(msg.build())
                    .build());
                notifyIfEnabled("Slayer task (" + slayerTask + ") is in a locked area");
            }
        }
        else
        {
            slayerTaskWarn = null; // reachable or unknown — no warning
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded ev)
    {
        if (!config.rollNudges()) return;
        if (ev.getGroupId() == QUEST_COMPLETED_GROUP_ID)
        {
            nudge("Quest complete — may be worth a roll.");
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged ev)
    {
        if (!config.rollNudges()) return;
        // Reliable diary-tier detection: each varbit flips 0→1 when that tier is
        // finished. The first value seen per login is a baseline (no nudge).
        for (int id : DIARY_VARBITS)
        {
            int v = client.getVarbitValue(id);
            Integer prev = diaryState.put(id, v);
            if (prev != null && prev == 0 && v == 1)
            {
                nudge("Achievement diary tier complete — may be worth a roll.");
            }
        }
    }

    /** Friendly skill name, e.g. "Woodcutting" from the WOODCUTTING enum. */
    private static String skillName(Skill skill)
    {
        String n = skill.getName();
        return n.isEmpty() ? n : Character.toUpperCase(n.charAt(0)) + n.substring(1).toLowerCase();
    }

    /** Fire a RuneLite notification if the user enabled it (respects their global settings). */
    private void notifyIfEnabled(String text)
    {
        if (config.useNotifier()) notifier.notify(text);
    }

    /** Queue a one-line informational chat nudge (client-side only). */
    private void nudge(String text)
    {
        ChatMessageBuilder msg = new ChatMessageBuilder()
            .append(ChatColorType.HIGHLIGHT).append("[Fate Locked] ")
            .append(ChatColorType.NORMAL).append(text);
        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.GAMEMESSAGE)
            .runeLiteFormattedMessage(msg.build())
            .build());
    }

    // ── Over-tier gear warning ────────────────────────────────────────────────
    // Warn (chat once + HUD line) when a worn item's tier exceeds the unlocked
    // tier for its slot. The plugin can't block equipping (server-authoritative),
    // only flag it — same as the locked-chunk warnings.

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged ev)
    {
        if (ev.getContainerId() == InventoryID.EQUIPMENT.getId())
        {
            recomputeOverTierGear();
        }
    }

    private void recomputeOverTierGear()
    {
        if (!config.warnOverTierGear())
        {
            overTierSummary = null;
            return;
        }
        FateLockedBundle b = bundle;
        Map<String, Integer> tiers = b.getItemTiers();
        FateLockedBundle.RunState st = b.getState();
        Map<String, Integer> equip = st == null ? null : st.getEquipment();
        if (tiers.isEmpty() || equip == null)
        {
            overTierSummary = null; // bundle predates the tier data — feature dormant
            return;
        }

        ItemContainer eq = client.getItemContainer(InventoryID.EQUIPMENT);
        if (eq == null)
        {
            overTierSummary = null;
            return;
        }

        List<String> over = new ArrayList<>();
        for (Map.Entry<EquipmentInventorySlot, String> e : SLOT_NAMES.entrySet())
        {
            Item item = eq.getItem(e.getKey().getSlotIdx());
            if (item == null || item.getId() <= 0) continue;
            Integer tier = tiers.get(String.valueOf(item.getId()));
            if (tier == null) continue; // unknown item — don't flag
            int unlocked = equip.getOrDefault(e.getValue(), 0);
            if (tier <= unlocked) continue;

            over.add(e.getValue());
            if (warnedOverTier.add(item.getId()))
            {
                String name = itemManager.getItemComposition(item.getId()).getName();
                ChatMessageBuilder msg = new ChatMessageBuilder()
                    .append(ChatColorType.HIGHLIGHT).append("[Fate Locked] ")
                    .append(ChatColorType.NORMAL).append(name)
                    .append(" is T" + tier + " but your " + e.getValue() + " is only unlocked to T" + unlocked + ".");
                chatMessageManager.queue(QueuedMessage.builder()
                    .type(ChatMessageType.GAMEMESSAGE)
                    .runeLiteFormattedMessage(msg.build())
                    .build());
                notifyIfEnabled(name + " is above your unlocked " + e.getValue() + " tier");
            }
        }
        overTierSummary = over.isEmpty() ? null : String.join(", ", over);
    }

    /** Normalise an OSRS name for comparison via RuneLite's Text.sanitize (handles
     *  non-breaking spaces, tags and stray whitespace), then case-fold. */
    static String normName(String s)
    {
        return s == null ? "" : Text.sanitize(s).toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Warn (once per login) if the bound account doesn't match the logged-in
     * character — the run's progress is tied to one OSRS account.
     */
    private void checkBoundAccount()
    {
        if (!config.warnAccountMismatch()) return;
        FateLockedBundle.RunState st = bundle.getState();
        String bound = st == null ? null : st.getLinkedAccount();
        if (bound == null || bound.trim().isEmpty()) return;

        Player local = client.getLocalPlayer();
        String current = local == null ? null : local.getName();
        if (current == null || current.isEmpty()) return;

        if (normName(bound).equals(normName(current))) return;
        if (normName(bound).equals(lastAccountWarned)) return;
        lastAccountWarned = normName(bound);

        ChatMessageBuilder msg = new ChatMessageBuilder()
            .append(ChatColorType.HIGHLIGHT).append("[Fate Locked] ")
            .append(ChatColorType.NORMAL).append("This run is bound to ")
            .append(ChatColorType.HIGHLIGHT).append(bound)
            .append(ChatColorType.NORMAL).append(" — you're logged in as ")
            .append(ChatColorType.HIGHLIGHT).append(current)
            .append(ChatColorType.NORMAL).append(".");
        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.GAMEMESSAGE)
            .runeLiteFormattedMessage(msg.build())
            .build());
        client.playSoundEffect(2277);
        notifyIfEnabled("You're logged in as " + current + ", not the bound account " + bound);
    }

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        Player local = client.getLocalPlayer();
        if (local == null) return;

        // Once per login, flag if the character doesn't match the bound account.
        checkBoundAccount();

        WorldPoint wp = local.getWorldLocation();
        if (wp == null) return;

        CanonicalChunk current = CanonicalChunk.of(wp);
        FateLockedBundle b = bundle;
        FateLockedBundle.LockState lock = b.lockStateAt(current);
        String label = b.labelAt(current);
        boolean unlocked = lock == FateLockedBundle.LockState.UNLOCKED;


        boolean changed = !current.equals(lastChunk);
        if (changed)
        {
            panel.update(b, current, label, unlocked);
            if (config.chatOnEnter())
            {
                announceEntry(current, label, unlocked);
            }
            // Visual flash only on the transition INTO locked territory.
            if (lock == FateLockedBundle.LockState.LOCKED
                && lastLockState != FateLockedBundle.LockState.LOCKED)
            {
                lockedFlashUntil = System.currentTimeMillis() + LOCKED_FLASH_MS;
            }
            lastChunk = current;
            lastLockState = lock;
        }
    }

    /**
     * Tag right-click menu entries whose target stands in a locked chunk with a
     * red (LOCKED) marker — the "are you sure?" before you ever click.
     */
    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        if (!config.tagLockedMenus() && !config.tagLockedTeleports()) return;
        FateLockedBundle b = bundle;
        if (b.getRegionChunks().isEmpty()) return;

        MenuEntry entry = event.getMenuEntry();
        boolean locked = false;

        // Tile-based: NPC/object/ground/walk entries standing in a locked chunk.
        if (config.tagLockedMenus())
        {
            WorldPoint target = menuTargetWorldPoint(entry);
            if (target != null
                && b.lockStateAt(CanonicalChunk.of(target)) == FateLockedBundle.LockState.LOCKED)
            {
                locked = true;
            }
        }

        // Name-based: teleports (spells/jewellery/tablets) whose destination chunk
        // is locked — these carry no world tile, so resolve by name.
        if (!locked && config.tagLockedTeleports())
        {
            CanonicalChunk dest = Teleports.destinationChunk(entry.getOption(), entry.getTarget());
            if (dest != null && b.lockStateAt(dest) == FateLockedBundle.LockState.LOCKED)
            {
                locked = true;
            }
        }

        if (!locked) return;
        String t = entry.getTarget();
        String base = t == null ? "" : t;
        if (!base.contains("(LOCKED)"))
        {
            entry.setTarget(base + " <col=ef4444>(LOCKED)</col>");
        }
    }

    /**
     * Resolve the world tile a menu entry points at, where feasible — using the
     * MenuEntry's typed accessors rather than decoding the raw type int (which
     * carries a +2000 offset on deprioritized entries and led to both missed
     * tags and false positives).
     */
    private WorldPoint menuTargetWorldPoint(MenuEntry entry)
    {
        // NPCs: use the resolved actor, independent of the (possibly offset)
        // action type, so deprioritized NPC options are still tagged.
        NPC npc = entry.getNpc();
        if (npc != null) return npc.getWorldLocation();

        switch (entry.getType())
        {
            case GAME_OBJECT_FIRST_OPTION:
            case GAME_OBJECT_SECOND_OPTION:
            case GAME_OBJECT_THIRD_OPTION:
            case GAME_OBJECT_FOURTH_OPTION:
            case GAME_OBJECT_FIFTH_OPTION:
            case EXAMINE_OBJECT:
            case GROUND_ITEM_FIRST_OPTION:
            case GROUND_ITEM_SECOND_OPTION:
            case GROUND_ITEM_THIRD_OPTION:
            case GROUND_ITEM_FOURTH_OPTION:
            case GROUND_ITEM_FIFTH_OPTION:
            case EXAMINE_ITEM_GROUND:
            case WALK:
            {
                // For these, param0/param1 are scene coordinates. Validate the
                // range so a non-tile entry with junk params can't resolve to a
                // bogus (often "locked") far-off chunk.
                int sceneX = entry.getParam0();
                int sceneY = entry.getParam1();
                if (sceneX < 0 || sceneX >= Constants.SCENE_SIZE
                    || sceneY < 0 || sceneY >= Constants.SCENE_SIZE) return null;
                return WorldPoint.fromScene(client, sceneX, sceneY, client.getPlane());
            }
            default:
                return null;
        }
    }

    private void announceEntry(CanonicalChunk chunk, String region, boolean unlocked)
    {
        ChatMessageBuilder msg = new ChatMessageBuilder()
            .append(ChatColorType.HIGHLIGHT).append("[Fate Locked] ")
            .append(ChatColorType.NORMAL).append("Chunk ")
            .append("(" + chunk.getCx() + ", " + chunk.getCy() + ")");

        if (region == null)
        {
            msg.append(ChatColorType.NORMAL).append(" — unauthored");
        }
        else if (unlocked)
        {
            msg.append(ChatColorType.NORMAL).append(" · ")
               .append(ChatColorType.HIGHLIGHT).append(region)
               .append(ChatColorType.NORMAL).append(" ✓ unlocked");
        }
        else
        {
            msg.append(ChatColorType.NORMAL).append(" · ")
               .append(ChatColorType.HIGHLIGHT).append(region)
               .append(ChatColorType.NORMAL).append(" ⚠ LOCKED");
        }

        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.GAMEMESSAGE)
            .runeLiteFormattedMessage(msg.build())
            .build());

        if (!unlocked && region != null && config.warnOnLocked())
        {
            client.playSoundEffect(2277); // death squelch — good "you done messed up" cue
            notifyIfEnabled("Entered LOCKED chunk: " + region);
        }
    }

    private void reloadBundle()
    {
        Path file = effectiveBundlePath();
        // Mark what we're about to read up-front so the watcher doesn't re-fire
        // every tick if the file is missing or fails to parse.
        watcherLoadedPath = file;
        watcherLastModified = file == null ? null : lastModified(file);

        if (file == null)
        {
            bundle = FateLockedBundle.empty();
            refreshPanel();
            return;
        }
        try
        {
            bundle = FateLockedBundle.loadFromFile(gson, file);
            log.info("Fate Locked bundle loaded from {}: {} regions, {} unlocked",
                file, bundle.getRegionChunks().size(), bundle.getUnlockedRegions().size());
        }
        catch (IOException | RuntimeException ex)
        {
            log.warn("Failed to load bundle at {}: {}", file, ex.getMessage());
            bundle = FateLockedBundle.empty();
        }
        refreshPanel();
    }

    /**
     * The bundle file to read: the newest fate-locked-bundle-*.json in the
     * plugin's data dir under .runelite/. All file I/O is confined there (Hub
     * rule); drop a bundle in there, or use the clipboard import instead.
     */
    private Path effectiveBundlePath()
    {
        File[] files = DATA_DIR.listFiles((d, name) ->
            name.startsWith("fate-locked-bundle") && name.toLowerCase().endsWith(".json"));
        if (files == null || files.length == 0) return null;
        File newest = null;
        for (File f : files)
        {
            if (newest == null || f.lastModified() > newest.lastModified()) newest = f;
        }
        return newest == null ? null : newest.toPath();
    }

    /** Hotkey action: read the clipboard and import it as a bundle (on the client thread). */
    private void reimportFromClipboard()
    {
        String text;
        try
        {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            text = data == null ? "" : data.toString().trim();
        }
        catch (Exception ex)
        {
            panel.flashStatus("couldn't read clipboard", false);
            return;
        }
        if (text.isEmpty())
        {
            panel.flashStatus("clipboard empty", false);
            return;
        }
        final String t = text;
        clientThread.invoke(() -> applyPastedBundle(t));
    }

    /** Load a bundle from JSON pasted into the side panel. */
    private void applyPastedBundle(String json)
    {
        try
        {
            FateLockedBundle parsed = FateLockedBundle.loadFromJson(gson, json);
            bundle = parsed;
            log.info("Fate Locked bundle imported from paste: {} regions", parsed.getRegionChunks().size());
            panel.flashStatus("imported " + parsed.getRegionChunks().size() + " regions", true);
            refreshPanel();
        }
        catch (RuntimeException ex)
        {
            log.warn("Pasted bundle could not be parsed: {}", ex.getMessage());
            panel.flashStatus("import failed — invalid JSON", false);
        }
    }

    /** Recompute the player's current chunk and push everything to the panel. */
    private void refreshPanel()
    {
        CanonicalChunk current = null;
        Player local = client.getLocalPlayer();
        if (local != null && local.getWorldLocation() != null)
        {
            current = CanonicalChunk.of(local.getWorldLocation());
        }
        String label = current == null ? null : bundle.labelAt(current);
        boolean unlocked = current != null
            && bundle.lockStateAt(current) == FateLockedBundle.LockState.UNLOCKED;
        panel.update(bundle, current, label, unlocked);
        // A fresh bundle may change unlocked tiers / areas — re-check worn gear,
        // the current slayer task, and the world-map markers.
        recomputeOverTierGear();
        recomputeSlayer();
        refreshWorldMapMarkers();
    }

    /** Place a click-to-jump marker on each authored area you haven't unlocked yet. */
    private void refreshWorldMapMarkers()
    {
        for (WorldMapPoint p : mapMarkers) worldMapPointManager.remove(p);
        mapMarkers.clear();
        if (!config.worldMapMarkers()) return;

        FateLockedBundle b = bundle;
        for (Map.Entry<String, Set<CanonicalChunk>> e : b.getSubAreaChunks().entrySet())
        {
            String area = e.getKey();
            if (b.isUnlocked(area)) continue; // only pin what's still locked

            Set<CanonicalChunk> chunks = e.getValue();
            if (chunks.isEmpty()) continue;
            long sx = 0, sy = 0;
            for (CanonicalChunk c : chunks) { sx += c.getCx(); sy += c.getCy(); }
            int cx = (int) (sx / chunks.size());
            int cy = (int) (sy / chunks.size());
            WorldPoint wp = new WorldPoint((cx << 6) + 32, (cy << 6) + 32, 0);

            WorldMapPoint point = new WorldMapPoint(wp, lockedPinImage());
            point.setName(area);
            point.setTooltip(area + " — LOCKED");
            point.setTarget(wp);
            point.setJumpOnClick(true);
            point.setSnapToEdge(false);
            point.setImagePoint(new Point(lockedPinImage().getWidth() / 2, lockedPinImage().getHeight() / 2));
            worldMapPointManager.add(point);
            mapMarkers.add(point);
        }
    }

    /** Small red lock-style pin, generated once. */
    private BufferedImage lockedPinImage()
    {
        if (lockedPinImage != null) return lockedPinImage;
        int s = 15;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(239, 68, 68, 235));
        g.fillOval(1, 1, s - 2, s - 2);
        g.setColor(new Color(20, 20, 20, 200));
        g.drawOval(1, 1, s - 2, s - 2);
        g.setColor(Color.WHITE);
        g.fillRect(s / 2 - 2, s / 2, 5, 4);          // lock body
        g.drawArc(s / 2 - 2, s / 2 - 3, 4, 5, 0, 180); // shackle
        g.dispose();
        lockedPinImage = img;
        return img;
    }

    // ── Infoboxes (keys / fate / unlock progress) ─────────────────────────────

    private void refreshInfoBoxes()
    {
        infoBoxManager.removeIf(b -> b instanceof FateLockedInfoBox);
        if (!config.showInfoBoxes()) return;

        infoBoxManager.addInfoBox(new FateLockedInfoBox(itemManager.getImage(KEYS_ICON_ITEM), this,
            new Color(245, 158, 11),
            () -> { FateLockedBundle.RunState s = bundle.getState(); return s == null ? "—" : String.valueOf(s.getKeys()); },
            () -> {
                FateLockedBundle.RunState s = bundle.getState();
                return s == null ? "Fate Locked keys"
                    : "Keys: " + s.getKeys() + " · Omni " + s.getSpecialKeys() + " · Chaos " + s.getChaosKeys();
            }));

        infoBoxManager.addInfoBox(new FateLockedInfoBox(discIcon(new Color(168, 85, 247)), this,
            new Color(196, 145, 255),
            () -> { FateLockedBundle.RunState s = bundle.getState(); return s == null ? "—" : String.valueOf(s.getFatePoints()); },
            () -> "Fate points"));

        infoBoxManager.addInfoBox(new FateLockedInfoBox(discIcon(new Color(52, 211, 153)), this,
            new Color(52, 211, 153),
            () -> {
                FateLockedBundle b = bundle;
                if (b.getTotalChunks() <= 0) return "—";
                return Math.round(100.0 * b.getUnlockedChunks() / b.getTotalChunks()) + "%";
            },
            () -> {
                FateLockedBundle b = bundle;
                return "Unlock progress: " + b.getUnlockedAreas() + "/" + b.getTotalAreas()
                    + " areas · " + b.getUnlockedChunks() + "/" + b.getTotalChunks() + " chunks";
            }));
    }

    /** A small filled-disc infobox icon in the given colour. */
    private static BufferedImage discIcon(Color c)
    {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(c);
        g.fillOval(1, 1, 14, 14);
        g.setColor(new Color(0, 0, 0, 140));
        g.drawOval(1, 1, 14, 14);
        g.dispose();
        return img;
    }

    private static BufferedImage createIcon()
    {
        BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Key bow
        g.setColor(new Color(245, 158, 11));
        g.fillOval(2, 7, 11, 11);
        g.setColor(new Color(15, 17, 21));
        g.fillOval(5, 10, 5, 5);
        // Shaft + teeth
        g.setColor(new Color(245, 158, 11));
        g.fillRect(12, 11, 10, 3);
        g.fillRect(17, 14, 2, 4);
        g.fillRect(20, 14, 2, 4);
        g.dispose();
        return img;
    }

    // ---- hot-reload watcher --------------------------------------------------

    private void startWatcher()
    {
        if (!config.autoReload()) return;

        // Poll the bundle file in .runelite/fate-locked on RuneLite's shared
        // executor, reloading when it changes (or a newer one is dropped in). No
        // background worker of our own and no blocking calls.
        watcherFuture = executor.scheduleWithFixedDelay(() -> {
            Path file = effectiveBundlePath();
            if (file == null) return;
            FileTime now = lastModified(file);
            if (now == null) return;
            if (!file.equals(watcherLoadedPath) || !now.equals(watcherLastModified))
            {
                clientThread.invoke(this::reloadBundle);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void stopWatcher()
    {
        if (watcherFuture != null)
        {
            watcherFuture.cancel(false);
            watcherFuture = null;
        }
    }

    // ── Online relay sync ─────────────────────────────────────────────────────
    // Optional, opt-in (config.syncCode). Outbound-only: poll the relay for the
    // run bundle and import on change. Uses the injected OkHttpClient, async, off
    // the client thread — no inbound server, Hub-compliant.

    private void startRelayPoll()
    {
        relayPollFuture = executor.scheduleWithFixedDelay(this::pollRelay, 2, 4, TimeUnit.SECONDS);
    }

    private void stopRelayPoll()
    {
        if (relayPollFuture != null)
        {
            relayPollFuture.cancel(false);
            relayPollFuture = null;
        }
    }

    private void pollRelay()
    {
        String code = config.syncCode();
        String base = config.relayUrl();
        if (code == null || code.trim().isEmpty() || base == null || base.trim().isEmpty()) return;

        final Request request;
        try
        {
            Request.Builder rb = new Request.Builder()
                .url(base.trim().replaceAll("/+$", "") + "/r/" + code.trim());
            if (lastRelayVersion != null) rb.header("If-None-Match", lastRelayVersion);
            request = rb.build();
        }
        catch (IllegalArgumentException ex)
        {
            return; // malformed relay URL — ignore until corrected
        }

        okHttpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.debug("Relay poll failed: {}", e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                try (Response r = response)
                {
                    if (r.code() == 304 || !r.isSuccessful() || r.body() == null) return;
                    RelayMessage msg = gson.fromJson(r.body().string(), RelayMessage.class);
                    if (msg == null || msg.payload == null) return;
                    String etag = r.header("ETag");
                    lastRelayVersion = etag != null ? etag : String.valueOf(msg.version);
                    String payload = msg.payload;
                    clientThread.invoke(() -> applyPastedBundle(payload));
                }
                catch (Exception ex)
                {
                    log.debug("Relay payload parse failed: {}", ex.getMessage());
                }
            }
        });
    }

    private static final class RelayMessage
    {
        int version;
        String payload;
    }

    /** Last-modified time of the bundle file, or null if it doesn't exist yet. */
    private static FileTime lastModified(Path file)
    {
        try
        {
            return Files.exists(file) ? Files.getLastModifiedTime(file) : null;
        }
        catch (IOException ex)
        {
            return null;
        }
    }
}
