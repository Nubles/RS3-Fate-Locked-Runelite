package com.fatelocked.rules;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.FateLockedBundle;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Lookup-only access to the app-authored v4 rules. Unknown data always stays
 * Unknown; this layer never invents a lock from missing mappings.
 */
public final class FateRuleEngine
{
    private final FateLockedBundle bundle;
    private final boolean accountMatches;
    private final boolean staleImport;

    public FateRuleEngine(
        FateLockedBundle bundle,
        boolean accountMatches,
        boolean staleImport)
    {
        this.bundle = bundle;
        this.accountMatches = accountMatches;
        this.staleImport = staleImport;
    }

    public RuleDecision entry(CanonicalChunk chunk)
    {
        RuleDecision trust = trustDecision();
        if (trust != null) return trust;
        Optional<ChunkPermissionSnapshot> snapshot = bundle.permissionsAt(chunk);
        if (!snapshot.isPresent()) return unknown(null);
        ChunkPermissionSnapshot value = snapshot.get();
        return new RuleDecision(
            value.getEntry(),
            value.getName() == null ? value.getChunkKey() : value.getName(),
            null);
    }

    public RuleDecision target(
        CanonicalChunk chunk,
        String targetKind,
        String targetName)
    {
        RuleDecision trust = trustDecision();
        if (trust != null) return trust;
        Optional<ChunkPermissionSnapshot> optional = bundle.permissionsAt(chunk);
        if (!optional.isPresent()) return unknown(targetName);
        ChunkPermissionSnapshot snapshot = optional.get();
        String kind = normalize(targetKind);
        String name = normalize(targetName);

        for (Map.Entry<String, List<ChunkPermissionRow>> category :
            snapshot.getCategories().entrySet())
        {
            for (ChunkPermissionRow row : category.getValue())
            {
                boolean kindMatches = kind.isEmpty()
                    || kind.equals(normalize(row.getTargetKind()));
                boolean nameMatches = name.isEmpty()
                    || name.equals(normalize(row.getName()));
                if (!kindMatches || !nameMatches) continue;
                PermissionStatus status = snapshot.getEntry() == PermissionStatus.LOCKED
                    ? PermissionStatus.LOCKED : row.getStatus();
                return new RuleDecision(status, row.getName(), row.getDetail());
            }
        }
        return unknown(targetName);
    }

    private RuleDecision trustDecision()
    {
        if (!accountMatches)
        {
            return new RuleDecision(
                PermissionStatus.UNKNOWN, "Wrong account", "Wrong account");
        }
        if (staleImport)
        {
            return new RuleDecision(
                PermissionStatus.UNKNOWN, "Rules out of date", "Rules out of date");
        }
        if (bundle == null || bundle.isLegacyRules())
        {
            return unknown(null);
        }
        return null;
    }

    private static RuleDecision unknown(String label)
    {
        return new RuleDecision(
            PermissionStatus.UNKNOWN,
            label == null || label.trim().isEmpty() ? "Unknown" : label,
            null);
    }

    private static String normalize(String value)
    {
        if (value == null) return "";
        return value
            .replaceAll("<[^>]+>", "")
            .replace("(LOCKED)", "")
            .trim()
            .toLowerCase(Locale.ROOT);
    }
}
