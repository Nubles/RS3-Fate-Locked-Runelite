package com.fatelocked.guardian;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.Teleports;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.util.Text;

import java.util.Locale;

public final class GuardedActionFactory
{
    public GuardedAction from(MenuEntry entry, Client client)
    {
        if (entry == null) return unknown("", "");
        String option = normalize(entry.getOption());
        String target = normalize(entry.getTarget());
        MenuAction type = entry.getType();
        if (option.startsWith("examine")) return unknown(option, target);
        if (type == MenuAction.WALK)
        {
            return new GuardedAction(
                GuardedAction.Kind.MOVEMENT, option, target,
                tileChunk(entry, client), null);
        }

        CanonicalChunk teleport = Teleports.destinationChunk(
            entry.getOption(), entry.getTarget());
        if (teleport != null)
        {
            return new GuardedAction(
                GuardedAction.Kind.TELEPORT, option, target, teleport, null);
        }

        if (option.equals("wear") || option.equals("wield") || option.equals("equip"))
        {
            int id = entry.getItemId();
            return new GuardedAction(
                GuardedAction.Kind.EQUIPMENT, option, target, null,
                id < 0 ? null : id);
        }

        NPC npc = entry.getNpc();
        if (npc != null)
        {
            WorldPoint point = npc.getWorldLocation();
            GuardedAction.Kind kind = isBankOption(option)
                ? GuardedAction.Kind.BANK : GuardedAction.Kind.NPC;
            return new GuardedAction(
                kind, option, target,
                point == null ? null : CanonicalChunk.of(point), null);
        }

        CanonicalChunk tile = tileChunk(entry, client);
        if (tile != null)
        {
            GuardedAction.Kind kind = isBankOption(option)
                ? GuardedAction.Kind.BANK : GuardedAction.Kind.OBJECT;
            return new GuardedAction(kind, option, target, tile, null);
        }
        return unknown(option, target);
    }

    private static boolean isBankOption(String option)
    {
        return option.equals("bank") || option.equals("collect")
            || option.equals("deposit") || option.equals("use-bank");
    }

    private static CanonicalChunk tileChunk(MenuEntry entry, Client client)
    {
        if (client == null || !hasSceneCoordinates(entry.getType())) return null;
        int x = entry.getParam0();
        int y = entry.getParam1();
        if (x < 0 || x >= Constants.SCENE_SIZE
            || y < 0 || y >= Constants.SCENE_SIZE) return null;
        WorldPoint point = WorldPoint.fromScene(client, x, y, client.getPlane());
        return point == null ? null : CanonicalChunk.of(point);
    }

    private static boolean hasSceneCoordinates(MenuAction action)
    {
        if (action == null) return false;
        switch (action)
        {
            case GAME_OBJECT_FIRST_OPTION:
            case GAME_OBJECT_SECOND_OPTION:
            case GAME_OBJECT_THIRD_OPTION:
            case GAME_OBJECT_FOURTH_OPTION:
            case GAME_OBJECT_FIFTH_OPTION:
            case GROUND_ITEM_FIRST_OPTION:
            case GROUND_ITEM_SECOND_OPTION:
            case GROUND_ITEM_THIRD_OPTION:
            case GROUND_ITEM_FOURTH_OPTION:
            case GROUND_ITEM_FIFTH_OPTION:
            case WALK:
                return true;
            default:
                return false;
        }
    }

    private static GuardedAction unknown(String option, String target)
    {
        return new GuardedAction(
            GuardedAction.Kind.UNKNOWN, option, target, null, null);
    }

    private static String normalize(String value)
    {
        if (value == null) return "";
        return Text.removeTags(value)
            .replace("(LOCKED)", "")
            .replaceAll("\\s+", " ")
            .trim()
            .toLowerCase(Locale.ROOT);
    }
}
