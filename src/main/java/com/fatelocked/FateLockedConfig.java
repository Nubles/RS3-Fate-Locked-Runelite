package com.fatelocked;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;

import java.awt.Color;

@ConfigGroup(FateLockedConfig.GROUP)
public interface FateLockedConfig extends Config
{
    String GROUP = "fatelocked";

    @ConfigSection(
        name = "Bundle",
        description = "Load your run data exported from the Fate Locked web app",
        position = 0
    )
    String bundleSection = "bundleSection";

    @ConfigItem(
        keyName = "autoReload",
        name = "Auto-reload on change",
        description = "Re-load the newest fate-locked-bundle-*.json from .runelite/fate-locked whenever it changes. (Or just use Import from clipboard in the side panel.)",
        section = bundleSection,
        position = 0
    )
    default boolean autoReload()
    {
        return true;
    }

    @ConfigItem(
        keyName = "onlineSync",
        name = "Enable online sync",
        description = "Sync your run from the web app over the internet via a pairing code, instead of clipboard/files. Off by default; no network calls unless enabled.",
        section = bundleSection,
        position = 4,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by Runelite developers"
    )
    default boolean onlineSync()
    {
        return false;
    }

    @ConfigItem(
        keyName = "syncCode",
        name = "Online sync code",
        description = "Pairing code from the web app's Online sync (used only when 'Enable online sync' is on).",
        section = bundleSection,
        position = 5
    )
    default String syncCode()
    {
        return "";
    }

    @ConfigItem(
        keyName = "relayUrl",
        name = "Relay URL",
        description = "Base URL of the online-sync relay. Change only if you host your own.",
        section = bundleSection,
        position = 6
    )
    default String relayUrl()
    {
        return "https://fate-relay.fatelocked.workers.dev";
    }

    @ConfigItem(
        keyName = "reimportHotkey",
        name = "Re-import hotkey",
        description = "Hotkey to re-import the bundle from your clipboard — press it after clicking RuneLite in the web app to re-sync without opening the panel",
        section = bundleSection,
        position = 3
    )
    default Keybind reimportHotkey()
    {
        return Keybind.NOT_SET;
    }

    @ConfigSection(
        name = "Warnings",
        description = "Chat + HUD alerts for locked-chunk transitions",
        position = 1
    )
    String warningsSection = "warningsSection";

    @ConfigItem(
        keyName = "chatOnEnter",
        name = "Chat on chunk entry",
        description = "Post a chat message each time you enter a new chunk",
        section = warningsSection
    )
    default boolean chatOnEnter()
    {
        return true;
    }

    @ConfigItem(
        keyName = "warnOnLocked",
        name = "Warn entering locked chunk",
        description = "Loud red warning when you step into a region you haven't unlocked",
        section = warningsSection
    )
    default boolean warnOnLocked()
    {
        return true;
    }

    @ConfigItem(
        keyName = "flashOnLocked",
        name = "Screen flash on locked entry",
        description = "Pulse a red border around the viewport when crossing into locked territory",
        section = warningsSection
    )
    default boolean flashOnLocked()
    {
        return true;
    }

    @ConfigItem(
        keyName = "warnAccountMismatch",
        name = "Warn on wrong account",
        description = "Chat warning when the logged-in character isn't the account this run is bound to",
        section = warningsSection
    )
    default boolean warnAccountMismatch()
    {
        return true;
    }

    @ConfigItem(
        keyName = "tagLockedMenus",
        name = "Tag locked right-click targets",
        description = "Append a red (LOCKED) tag to menu entries for NPCs/objects standing in locked chunks",
        section = warningsSection
    )
    default boolean tagLockedMenus()
    {
        return true;
    }

    @ConfigItem(
        keyName = "tagLockedTeleports",
        name = "Tag teleports to locked chunks",
        description = "Append a red (LOCKED) tag to teleport options (spells, jewellery, tablets) whose destination is in a locked chunk",
        section = warningsSection
    )
    default boolean tagLockedTeleports()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showHud",
        name = "Show in-game HUD",
        description = "Overlay with keys, fate points, active buff, next goal and current chunk status",
        section = warningsSection
    )
    default boolean showHud()
    {
        return true;
    }

    @ConfigItem(
        keyName = "useNotifier",
        name = "Send RuneLite notifications",
        description = "Also fire a RuneLite notification (tray / sound, per your global settings) for locked-chunk entry, locked slayer tasks, over-tier gear and wrong-account logins",
        section = warningsSection
    )
    default boolean useNotifier()
    {
        return false;
    }

    @ConfigItem(
        keyName = "warnLockedSlayer",
        name = "Warn on locked slayer task",
        description = "Chat + HUD warning when your assigned slayer monster only lives in chunks you haven't unlocked",
        section = warningsSection
    )
    default boolean warnLockedSlayer()
    {
        return true;
    }

    @ConfigItem(
        keyName = "warnOverTierGear",
        name = "Warn on over-tier gear",
        description = "Chat + HUD warning when you're wearing an item above your unlocked equipment tier for that slot",
        section = warningsSection
    )
    default boolean warnOverTierGear()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showInfoBoxes",
        name = "Show key/fate/progress infoboxes",
        description = "Add dockable infoboxes for your keys, fate points and unlock progress (native RuneLite infobox row)",
        section = warningsSection
    )
    default boolean showInfoBoxes()
    {
        return false;
    }

    @ConfigItem(
        keyName = "rollNudges",
        name = "Roll reminders",
        description = "Chat reminder on level-up, quest, diary, combat-achievement, boss kill and raid completion that it may be worth a roll in the tracker",
        section = warningsSection
    )
    default boolean rollNudges()
    {
        return true;
    }

    @ConfigSection(
        name = "Rendering",
        description = "How chunks are drawn on the map and in-world",
        position = 2
    )
    String renderingSection = "renderingSection";

    @ConfigItem(
        keyName = "drawWorldMap",
        name = "Draw on world map",
        description = "Tint authored chunks on the full world map",
        section = renderingSection
    )
    default boolean drawWorldMap()
    {
        return true;
    }

    @ConfigItem(
        keyName = "drawScene",
        name = "Draw around player",
        description = "Tint tiles on the main game view for the player's current chunk",
        section = renderingSection
    )
    default boolean drawScene()
    {
        return true;
    }

    @ConfigItem(
        keyName = "drawMinimap",
        name = "Draw on minimap",
        description = "Tint the player's current chunk on the minimap",
        section = renderingSection
    )
    default boolean drawMinimap()
    {
        return true;
    }

    @ConfigItem(
        keyName = "highlightLockedBorders",
        name = "Highlight locked borders",
        description = "Outline the edges of your current chunk that border a locked chunk",
        section = renderingSection
    )
    default boolean highlightLockedBorders()
    {
        return true;
    }

    @ConfigItem(
        keyName = "shadeNearbyLocked",
        name = "Shade nearby locked chunks",
        description = "Lightly tint every locked chunk visible around you in the game view and minimap, not just the one you're standing in",
        section = renderingSection
    )
    default boolean shadeNearbyLocked()
    {
        return true;
    }

    @ConfigItem(
        keyName = "worldMapMarkers",
        name = "Pin locked areas on world map",
        description = "Place a marker on each authored area you haven't unlocked; click a marker to jump the world map there. Off by default to avoid clutter.",
        section = renderingSection
    )
    default boolean worldMapMarkers()
    {
        return false;
    }

    @ConfigItem(
        keyName = "worldMapTooltip",
        name = "World map hover tooltip",
        description = "Hover an authored chunk on the world map to see its area name and lock status",
        section = renderingSection
    )
    default boolean worldMapTooltip()
    {
        return true;
    }

    @ConfigItem(
        keyName = "unlockedColor",
        name = "Unlocked color",
        description = "Color for chunks inside unlocked regions",
        section = renderingSection
    )
    default Color unlockedColor()
    {
        return new Color(16, 185, 129, 110);
    }

    @ConfigItem(
        keyName = "lockedColor",
        name = "Locked color",
        description = "Color for chunks inside authored-but-not-yet-unlocked regions",
        section = renderingSection
    )
    default Color lockedColor()
    {
        return new Color(239, 68, 68, 110);
    }

    @ConfigItem(
        keyName = "unauthoredColor",
        name = "Unauthored color",
        description = "Color for chunks not claimed by any region (empty-space alert)",
        section = renderingSection
    )
    default Color unauthoredColor()
    {
        return new Color(107, 114, 128, 60);
    }
}
