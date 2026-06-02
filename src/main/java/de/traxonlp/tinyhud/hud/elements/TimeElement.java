package de.traxonlp.tinyhud.hud.elements;

import de.traxonlp.tinyhud.TinyHUD;
import de.traxonlp.tinyhud.hud.HudCategory;
import de.traxonlp.tinyhud.hud.HudLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;

import java.util.List;

public class TimeElement extends TextHudElement {

    public TimeElement() {
        super(Identifier.fromNamespaceAndPath(TinyHUD.MODID, "time"), "tinyhud.element.time");
    }

    @Override
    public HudCategory category() { return HudCategory.WORLD; }

    @Override
    public List<FormatOption> formats() {
        return List.of(
                new FormatOption("24h",      "tinyhud.format.time.24h"),       // 14:30
                new FormatOption("12h",      "tinyhud.format.time.12h"),       // 2:30 PM
                new FormatOption("24h_secs", "tinyhud.format.time.24h_secs"),  // 14:30:45
                new FormatOption("12h_secs", "tinyhud.format.time.12h_secs")   // 2:30:45 PM
        );
    }

    // Text

    @Override
    protected String text(Minecraft mc) { return ""; }

    @Override
    protected String text(Minecraft mc, HudLayout.Entry entry) {
        ClientLevel level = mc.level;
        if (level == null) return "";

        // 24000 ticks per in-game day represents 24 x 60 x 60 = 86 400 real seconds of time.
        // dayTime 0 == 06:00 in-game.
        long dayTime     = level.getOverworldClockTime() % 24000L;
        long totalSecs   = (dayTime * 86400L / 24000L + 6L * 3600L) % 86400L;
        long hours       = totalSecs / 3600L;
        long minutes     = (totalSecs % 3600L) / 60L;
        long seconds     = totalSecs % 60L;

        boolean secs = "24h_secs".equals(entry.format) || "12h_secs".equals(entry.format);
        boolean use12 = "12h".equals(entry.format) || "12h_secs".equals(entry.format);

        if (use12) {
            long h12   = hours % 12L;
            if (h12 == 0L) h12 = 12L;
            String sfx = hours < 12L ? "AM" : "PM";
            return secs
                    ? String.format("%d:%02d:%02d %s", h12, minutes, seconds, sfx)
                    : String.format("%d:%02d %s", h12, minutes, sfx);
        }
        return secs
                ? String.format("%02d:%02d:%02d", hours, minutes, seconds)
                : String.format("%02d:%02d", hours, minutes);
    }

    @Override public int defaultX(int screenW, int screenH) { return 4; }
    @Override public int defaultY(int screenW, int screenH) { return 4; }
}
