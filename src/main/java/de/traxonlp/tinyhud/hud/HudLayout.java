package de.traxonlp.tinyhud.hud;

import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class HudLayout {
    public static final float DEFAULT_SCALE  = 1.0f;
    public static final int   DEFAULT_COLOR  = 0xFFFFFFFF;
    public static final int   DEFAULT_COLOR2 = 0xFF4A9EFF;  // progress-bar accent (light blue)
    public static final String DEFAULT_FONT   = "minecraft:default";
    public static final String DEFAULT_FORMAT = "";

    public static final class Entry {
        public float xFrac;
        public float yFrac;
        public boolean visible;
        public float scale;
        public int color;
        public int color2;
        public String font;
        public boolean rainbow;
        public boolean box;
        public String format;
        public boolean showCover;
        public boolean hideWhenZero;

        public Entry(float xFrac, float yFrac, boolean visible) {
            this(xFrac, yFrac, visible, DEFAULT_SCALE, DEFAULT_COLOR, DEFAULT_COLOR2,
                 DEFAULT_FONT, false, false, DEFAULT_FORMAT, true, false);
        }

        public Entry(float xFrac, float yFrac, boolean visible, float scale, int color, int color2,
                     String font, boolean rainbow, boolean box, String format, boolean showCover,
                     boolean hideWhenZero) {
            this.xFrac        = xFrac;
            this.yFrac        = yFrac;
            this.visible      = visible;
            this.scale        = scale;
            this.color        = color;
            this.color2       = color2;
            this.font         = font;
            this.rainbow      = rainbow;
            this.box          = box;
            this.format       = format;
            this.showCover    = showCover;
            this.hideWhenZero = hideWhenZero;
        }

        /** Create an entry from absolute pixel defaults, normalised to screen fractions. */
        public static Entry fromPixels(int px, int py, int screenW, int screenH) {
            return new Entry((float) px / screenW, (float) py / screenH, true);
        }

        public static Entry fromPixels(int px, int py, boolean visible, float scale, int color, int color2,
                                       String font, boolean rainbow, boolean box, String format,
                                       boolean showCover, int screenW, int screenH) {
            return new Entry((float) px / screenW, (float) py / screenH,
                             visible, scale, color, color2, font, rainbow, box, format, showCover, false);
        }

        /** Resolved pixel X for the current screen width. */
        public int px(int screenW) { return Math.round(xFrac * screenW); }

        /** Resolved pixel Y for the current screen height. */
        public int py(int screenH) { return Math.round(yFrac * screenH); }

        public Entry copy() {
            return new Entry(xFrac, yFrac, visible, scale, color, color2, font, rainbow, box, format, showCover, hideWhenZero);
        }
    }

    private static final HudLayout INSTANCE = new HudLayout();

    public static HudLayout get() {
        return INSTANCE;
    }

    private final Map<Identifier, Entry> entries = new HashMap<>();

    private HudLayout() {
    }

    public Entry entry(HudElement element, int screenW, int screenH) {
        return entries.computeIfAbsent(element.id(),
                id -> Entry.fromPixels(
                        element.defaultX(screenW, screenH),
                        element.defaultY(screenW, screenH),
                        screenW, screenH));
    }

    public Entry rawEntry(Identifier id) {
        return entries.get(id);
    }

    public void put(Identifier id, Entry entry) {
        entries.put(id, entry);
    }

    public Map<Identifier, Entry> snapshot() {
        Map<Identifier, Entry> copy = new HashMap<>(entries.size());
        for (Map.Entry<Identifier, Entry> e : entries.entrySet()) {
            copy.put(e.getKey(), e.getValue().copy());
        }
        return copy;
    }

    public void replaceWith(Map<Identifier, Entry> source) {
        entries.clear();
        for (Map.Entry<Identifier, Entry> e : source.entrySet()) {
            entries.put(e.getKey(), e.getValue().copy());
        }
    }

    public Map<Identifier, Entry> view() {
        return entries;
    }
}
