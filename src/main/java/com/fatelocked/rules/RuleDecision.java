package com.fatelocked.rules;

import lombok.Value;

@Value
public class RuleDecision
{
    PermissionStatus status;
    String label;
    String reason;
}
