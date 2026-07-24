package com.fatelocked.guardian;

import com.fatelocked.rules.RuleDecision;
import lombok.Value;

@Value
public class GuardResult
{
    public enum Outcome { ALLOW, BLOCK, WARN_ONLY }

    Outcome outcome;
    RuleDecision decision;
}
