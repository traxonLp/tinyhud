package de.traxonlp.tinyhud.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.List;

public interface HudElement {
    Identifier id();

    HudCategory category();

    int width();

    int height();

    void render(GuiGraphicsExtractor gfx, DeltaTracker delta, int x, int y, HudLayout.Entry entry);

    int defaultX(int screenW, int screenH);

    int defaultY(int screenW, int screenH);

    String translationKey();

    /** Available format options; the first entry is the default. Empty = no format choice. */
    default List<FormatOption> formats() {
        return List.of();
    }

    /**
     * True if this element uses {@link de.traxonlp.tinyhud.hud.HudLayout.Entry#color2} as an
     * accent color (e.g. progress bar fill). When true the style editor shows a second RGB group.
     */
    default boolean hasAccentColor() {
        return false;
    }

    /** True if this element has cover art that can be shown or hidden. */
    default boolean hasCoverToggle() {
        return false;
    }

    /** True if this element supports the "hide entries with count 0" toggle (item tracker). */
    default boolean hasHideZeroToggle() {
        return false;
    }

    record FormatOption(String key, String translationKey) {}
}
