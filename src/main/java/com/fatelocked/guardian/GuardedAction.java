package com.fatelocked.guardian;

import com.fatelocked.CanonicalChunk;
import lombok.Value;

@Value
public class GuardedAction
{
    public enum Kind
    {
        NPC, OBJECT, BANK, TELEPORT, EQUIPMENT, MOVEMENT, UNKNOWN
    }

    Kind kind;
    String option;
    String target;
    CanonicalChunk chunk;
    Integer itemId;
}
