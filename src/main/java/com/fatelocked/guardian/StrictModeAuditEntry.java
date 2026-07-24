package com.fatelocked.guardian;

import lombok.Value;

@Value
public class StrictModeAuditEntry
{
    long timestamp;
    String actionKind;
    String target;
    String chunk;
    String reason;
}
