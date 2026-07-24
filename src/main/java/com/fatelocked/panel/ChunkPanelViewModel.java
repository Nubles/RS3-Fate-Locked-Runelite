package com.fatelocked.panel;

import com.fatelocked.rules.PermissionStatus;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public final class ChunkPanelViewModel
{
    private final String name;
    private final String region;
    private final String coordinates;
    private final PermissionStatus entryStatus;
    private final String freshnessLabel;
    private final int allowedCount;
    private final int notReadyCount;
    private final int lockedCount;
    private final int unknownCount;
    private final List<CategoryView> categories;

    ChunkPanelViewModel(
        String name,
        String region,
        String coordinates,
        PermissionStatus entryStatus,
        String freshnessLabel,
        int allowedCount,
        int notReadyCount,
        int lockedCount,
        int unknownCount,
        List<CategoryView> categories)
    {
        this.name = name;
        this.region = region;
        this.coordinates = coordinates;
        this.entryStatus = entryStatus;
        this.freshnessLabel = freshnessLabel;
        this.allowedCount = allowedCount;
        this.notReadyCount = notReadyCount;
        this.lockedCount = lockedCount;
        this.unknownCount = unknownCount;
        this.categories = Collections.unmodifiableList(categories);
    }

    public CategoryView category(String id)
    {
        for (CategoryView category : categories)
        {
            if (category.getId().equals(id)) return category;
        }
        return null;
    }

    @Getter
    public static final class CategoryView
    {
        private final String id;
        private final String title;
        private final List<RowView> rows;

        CategoryView(String id, String title, List<RowView> rows)
        {
            this.id = id;
            this.title = title;
            this.rows = Collections.unmodifiableList(rows);
        }
    }

    @Getter
    public static final class RowView
    {
        private final String name;
        private final PermissionStatus status;
        private final String statusGlyph;
        private final String statusText;
        private final String detail;

        RowView(
            String name,
            PermissionStatus status,
            String statusGlyph,
            String statusText,
            String detail)
        {
            this.name = name;
            this.status = status;
            this.statusGlyph = statusGlyph;
            this.statusText = statusText;
            this.detail = detail;
        }
    }
}
