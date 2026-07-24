package com.fatelocked.guardian;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GuardedActionFactoryTest
{
    private final GuardedActionFactory factory = new GuardedActionFactory();
    private final Client client = mock(Client.class);

    @Test
    public void normalizesNpcAndBankActors()
    {
        NPC npc = mock(NPC.class);
        when(npc.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
        MenuEntry attack = entry("Attack", "<col=ffff00>Goblin</col>");
        when(attack.getNpc()).thenReturn(npc);
        assertEquals(GuardedAction.Kind.NPC,
            factory.from(attack, client).getKind());
        assertEquals("goblin", factory.from(attack, client).getTarget());

        MenuEntry bank = entry("Bank", "Banker");
        when(bank.getNpc()).thenReturn(npc);
        assertEquals(GuardedAction.Kind.BANK,
            factory.from(bank, client).getKind());
    }

    @Test
    public void recognizesMovementTeleportAndEquipment()
    {
        MenuEntry walk = entry("Walk here", "");
        when(walk.getType()).thenReturn(MenuAction.WALK);
        when(walk.getParam0()).thenReturn(-1);
        assertEquals(GuardedAction.Kind.MOVEMENT,
            factory.from(walk, client).getKind());
        assertNull(factory.from(walk, client).getChunk());

        MenuEntry teleport = entry("Teleport", "Falador");
        assertEquals(GuardedAction.Kind.TELEPORT,
            factory.from(teleport, client).getKind());

        MenuEntry wield = entry("Wield", "Abyssal whip");
        when(wield.getItemId()).thenReturn(4151);
        assertEquals(GuardedAction.Kind.EQUIPMENT,
            factory.from(wield, client).getKind());
        assertEquals(Integer.valueOf(4151),
            factory.from(wield, client).getItemId());
    }

    @Test
    public void examineAndUnrelatedWidgetsStayUnknown()
    {
        assertEquals(GuardedAction.Kind.UNKNOWN,
            factory.from(entry("Examine", "Goblin"), client).getKind());
        assertEquals(GuardedAction.Kind.UNKNOWN,
            factory.from(entry("Continue", ""), client).getKind());
    }

    private static MenuEntry entry(String option, String target)
    {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getOption()).thenReturn(option);
        when(entry.getTarget()).thenReturn(target);
        when(entry.getType()).thenReturn(MenuAction.UNKNOWN);
        when(entry.getItemId()).thenReturn(-1);
        return entry;
    }
}
