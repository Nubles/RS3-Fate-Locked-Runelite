package com.fatelocked;

import com.google.gson.Gson;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.EnumMap;
import java.util.Map;
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
    /** Widget group shown when a quest is completed (the reward scroll). */
    private static final int QUEST_COMPLETED_GROUP_ID = 153;
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
        bundle = FateLockedBundle.empty();
        lastChunk = null;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged ev)
    {
        if (!FateLockedConfig.GROUP.equals(ev.getGroup())) return;
        String key = ev.getKey();
        if ("bundlePath".equals(key) || "autoReload".equals(key) || "autoDetectDownloads".equals(key))
        {
            reloadBundle();
            stopWatcher();
            startWatcher();
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
        if (!config.rollNudges()) return;
        if (ev.getType() != ChatMessageType.GAMEMESSAGE && ev.getType() != ChatMessageType.SPAM) return;
        String m = ev.getMessage() == null ? "" : ev.getMessage().toLowerCase();

        if (m.contains("combat task:"))
        {
            nudge("Combat achievement complete — may be worth a roll.");
        }
        else if (m.contains("completed all of the") && m.contains("tasks"))
        {
            nudge("Achievement diary tier complete — may be worth a roll.");
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

    /** Friendly skill name, e.g. "Woodcutting" from the WOODCUTTING enum. */
    private static String skillName(Skill skill)
    {
        String n = skill.getName();
        return n.isEmpty() ? n : Character.toUpperCase(n.charAt(0)) + n.substring(1).toLowerCase();
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

    /** Normalise an OSRS name for comparison (RuneLite uses non-breaking spaces). */
    static String normName(String s)
    {
        return s == null ? "" : s.replace((char)0x00A0, ' ').trim().toLowerCase();
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
        if (!config.tagLockedMenus()) return;
        FateLockedBundle b = bundle;
        if (b.getRegionChunks().isEmpty()) return;

        WorldPoint target = menuTargetWorldPoint(event);
        if (target == null) return;

        if (b.lockStateAt(CanonicalChunk.of(target)) == FateLockedBundle.LockState.LOCKED)
        {
            String t = event.getTarget();
            if (t != null && !t.contains("(LOCKED)"))
            {
                event.getMenuEntry().setTarget(t + " <col=ef4444>(LOCKED)</col>");
            }
        }
    }

    /** Resolve the world tile a menu entry points at, where feasible. */
    private WorldPoint menuTargetWorldPoint(MenuEntryAdded event)
    {
        MenuAction action = MenuAction.of(event.getType());
        switch (action)
        {
            case NPC_FIRST_OPTION:
            case NPC_SECOND_OPTION:
            case NPC_THIRD_OPTION:
            case NPC_FOURTH_OPTION:
            case NPC_FIFTH_OPTION:
            case EXAMINE_NPC:
            {
                NPC npc = event.getMenuEntry().getNpc();
                return npc == null ? null : npc.getWorldLocation();
            }
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
                int sceneX = event.getActionParam0();
                int sceneY = event.getActionParam1();
                if (sceneX < 0 || sceneY < 0) return null;
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
     * The bundle file to read: the configured path if set, otherwise (when
     * auto-detect is on) the newest fate-locked-bundle file in Downloads.
     */
    private Path effectiveBundlePath()
    {
        String p = config.bundlePath();
        if (p != null && !p.trim().isEmpty()) return Paths.get(p.trim());
        if (config.autoDetectDownloads()) return findNewestDownloadBundle();
        return null;
    }

    /** Newest fate-locked-bundle-*.json in the user's Downloads folder, or null. */
    private Path findNewestDownloadBundle()
    {
        File dir = new File(System.getProperty("user.home"), "Downloads");
        File[] files = dir.listFiles((d, name) ->
            name.startsWith("fate-locked-bundle") && name.toLowerCase().endsWith(".json"));
        if (files == null || files.length == 0) return null;
        File newest = null;
        for (File f : files)
        {
            if (newest == null || f.lastModified() > newest.lastModified()) newest = f;
        }
        return newest == null ? null : newest.toPath();
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

        // Poll the effective bundle file on RuneLite's shared executor, reloading
        // when it changes — or when a newer file becomes the auto-detect target
        // (e.g. a fresh export landed in Downloads). No background worker of our
        // own and no blocking calls.
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
