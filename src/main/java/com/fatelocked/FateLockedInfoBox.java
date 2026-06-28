package com.fatelocked;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.function.Supplier;

/**
 * A simple text infobox whose value, colour and tooltip are read live from
 * suppliers — so keys / fate / unlock-progress update as the bundle changes
 * without rebuilding the box.
 */
class FateLockedInfoBox extends InfoBox
{
    private final Supplier<String> text;
    private final Supplier<String> tooltip;
    private final Color color;

    FateLockedInfoBox(BufferedImage image, Plugin plugin, Color color,
                      Supplier<String> text, Supplier<String> tooltip)
    {
        super(image, plugin);
        this.color = color;
        this.text = text;
        this.tooltip = tooltip;
    }

    @Override
    public String getText()
    {
        return text.get();
    }

    @Override
    public Color getTextColor()
    {
        return color;
    }

    @Override
    public String getTooltip()
    {
        return tooltip.get();
    }
}
