package com.fatelocked.guardian;

import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RuleDecision;

public final class StrictModeGuard
{
    public GuardResult decide(GuardedAction action, GuardContext context)
    {
        if (action == null || context == null || !context.isEnabled()
            || context.isPaused() || !context.isAccountMatches()
            || !context.isFreshRules() || context.getRules() == null)
        {
            return allow();
        }

        RuleDecision decision = decision(action, context);
        if (action.getKind() == GuardedAction.Kind.MOVEMENT)
        {
            return decision != null
                && decision.getStatus() == PermissionStatus.LOCKED
                ? new GuardResult(GuardResult.Outcome.WARN_ONLY, decision)
                : allow(decision);
        }
        if (decision != null && decision.getStatus() == PermissionStatus.LOCKED)
        {
            return new GuardResult(GuardResult.Outcome.BLOCK, decision);
        }
        return allow(decision);
    }

    private static RuleDecision decision(
        GuardedAction action,
        GuardContext context)
    {
        switch (action.getKind())
        {
            case EQUIPMENT:
                return action.getItemId() == null ? null
                    : context.getRules().equipment(action.getItemId());
            case TELEPORT:
            case MOVEMENT:
                return action.getChunk() == null ? null
                    : context.getRules().entry(action.getChunk());
            case BANK:
                return action.getChunk() == null ? null
                    : context.getRules().target(action.getChunk(), "BANK", "");
            case NPC:
                return action.getChunk() == null ? null
                    : context.getRules().target(
                        action.getChunk(), "NPC", action.getTarget());
            case OBJECT:
                return action.getChunk() == null ? null
                    : context.getRules().target(
                        action.getChunk(), "OBJECT", action.getTarget());
            default:
                return null;
        }
    }

    private static GuardResult allow()
    {
        return new GuardResult(GuardResult.Outcome.ALLOW, null);
    }

    private static GuardResult allow(RuleDecision decision)
    {
        return new GuardResult(GuardResult.Outcome.ALLOW, decision);
    }
}
