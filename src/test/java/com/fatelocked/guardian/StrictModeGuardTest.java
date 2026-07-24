package com.fatelocked.guardian;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.rules.FateRuleEngine;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RuleDecision;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StrictModeGuardTest
{
    private final CanonicalChunk chunk = new CanonicalChunk(50, 50);
    private final StrictModeGuard guard = new StrictModeGuard();

    @Test
    public void onlyFreshCertainLockedActionsBlock()
    {
        FateRuleEngine engine = mock(FateRuleEngine.class);
        when(engine.target(any(), anyString(), anyString()))
            .thenReturn(decision(PermissionStatus.LOCKED));
        when(engine.entry(any())).thenReturn(decision(PermissionStatus.LOCKED));
        when(engine.equipment(anyInt())).thenReturn(decision(PermissionStatus.LOCKED));
        GuardContext enabled = new GuardContext(true, false, true, true, engine);
        GuardedAction npc = action(GuardedAction.Kind.NPC);

        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decide(npc, new GuardContext(false, false, true, true, engine)).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decide(npc, new GuardContext(true, true, true, true, engine)).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decide(npc, new GuardContext(true, false, false, true, engine)).getOutcome());
        assertEquals(GuardResult.Outcome.ALLOW,
            guard.decide(npc, new GuardContext(true, false, true, false, engine)).getOutcome());
        assertEquals(GuardResult.Outcome.WARN_ONLY,
            guard.decide(action(GuardedAction.Kind.MOVEMENT), enabled).getOutcome());
        assertEquals(GuardResult.Outcome.BLOCK,
            guard.decide(npc, enabled).getOutcome());
        assertEquals(GuardResult.Outcome.BLOCK,
            guard.decide(action(GuardedAction.Kind.BANK), enabled).getOutcome());
        assertEquals(GuardResult.Outcome.BLOCK,
            guard.decide(action(GuardedAction.Kind.TELEPORT), enabled).getOutcome());
        assertEquals(GuardResult.Outcome.BLOCK,
            guard.decide(new GuardedAction(
                GuardedAction.Kind.EQUIPMENT, "wield", "whip", null, 4151),
                enabled).getOutcome());
    }

    @Test
    public void blockAlwaysImpliesLocked()
    {
        for (PermissionStatus status : PermissionStatus.values())
        {
            FateRuleEngine engine = mock(FateRuleEngine.class);
            when(engine.target(any(), anyString(), anyString()))
                .thenReturn(decision(status));
            GuardResult result = guard.decide(
                action(GuardedAction.Kind.NPC),
                new GuardContext(true, false, true, true, engine));
            if (result.getOutcome() == GuardResult.Outcome.BLOCK)
            {
                assertEquals(PermissionStatus.LOCKED,
                    result.getDecision().getStatus());
            }
            else if (status != PermissionStatus.LOCKED)
            {
                assertEquals(GuardResult.Outcome.ALLOW, result.getOutcome());
            }
        }
    }

    private GuardedAction action(GuardedAction.Kind kind)
    {
        return new GuardedAction(kind, "use", "goblin", chunk, null);
    }

    private static RuleDecision decision(PermissionStatus status)
    {
        return new RuleDecision(status, "Target", null);
    }
}
