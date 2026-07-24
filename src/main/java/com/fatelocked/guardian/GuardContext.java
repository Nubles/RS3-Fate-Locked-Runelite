package com.fatelocked.guardian;

import com.fatelocked.rules.FateRuleEngine;
import lombok.Value;

@Value
public class GuardContext
{
    boolean enabled;
    boolean paused;
    boolean accountMatches;
    boolean freshRules;
    FateRuleEngine rules;
}
