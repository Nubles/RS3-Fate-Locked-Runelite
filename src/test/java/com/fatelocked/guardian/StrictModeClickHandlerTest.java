package com.fatelocked.guardian;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.rules.FateRuleEngine;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RuleDecision;
import net.runelite.api.events.MenuOptionClicked;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StrictModeClickHandlerTest
{
    @Test
    public void consumesOnlyCertainLockedActions()
    {
        FateRuleEngine engine = mock(FateRuleEngine.class);
        when(engine.target(any(), anyString(), anyString()))
            .thenReturn(new RuleDecision(PermissionStatus.LOCKED, "Goblin", null));
        GuardedAction action = new GuardedAction(
            GuardedAction.Kind.NPC, "attack", "goblin",
            new CanonicalChunk(50, 50), null);
        StrictModeClickHandler handler =
            new StrictModeClickHandler(new StrictModeGuard());

        MenuOptionClicked locked = mock(MenuOptionClicked.class);
        handler.handle(locked, action,
            new GuardContext(true, false, true, true, engine));
        verify(locked).consume();

        MenuOptionClicked disabled = mock(MenuOptionClicked.class);
        handler.handle(disabled, action,
            new GuardContext(false, false, true, true, engine));
        verify(disabled, never()).consume();

        when(engine.target(any(), anyString(), anyString()))
            .thenReturn(new RuleDecision(PermissionStatus.UNKNOWN, "Goblin", null));
        MenuOptionClicked unknown = mock(MenuOptionClicked.class);
        handler.handle(unknown, action,
            new GuardContext(true, false, true, true, engine));
        verify(unknown, never()).consume();
    }
}
