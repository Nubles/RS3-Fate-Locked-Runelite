package com.fatelocked.rules;

import lombok.Getter;

@Getter
public final class ChunkPermissionRow
{
    private String key;
    private String name;
    private PermissionStatus status;
    private String detail;
    private String targetKind;

    ChunkPermissionRow normalized()
    {
        ChunkPermissionRow copy = new ChunkPermissionRow();
        copy.key = key == null ? "" : key;
        copy.name = name == null ? "" : name;
        copy.status = status == null ? PermissionStatus.UNKNOWN : status;
        copy.detail = detail;
        copy.targetKind = targetKind;
        return copy;
    }
}
