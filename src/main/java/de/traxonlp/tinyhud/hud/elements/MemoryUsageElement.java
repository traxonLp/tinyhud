package de.traxonlp.tinyhud.hud.elements;

import de.traxonlp.tinyhud.TinyHUD;
import de.traxonlp.tinyhud.hud.HudCategory;
import de.traxonlp.tinyhud.hud.HudLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Locale;

public class MemoryUsageElement extends TextHudElement {

    public MemoryUsageElement() {
        super(Identifier.fromNamespaceAndPath(TinyHUD.MODID, "memory_usage"), "tinyhud.element.memory_usage");
    }

    @Override
    public HudCategory category() { return HudCategory.SYSTEM; }

    @Override
    public List<FormatOption> formats() {
        return List.of(
                new FormatOption("percent", "tinyhud.format.memory.percent"),  // "Mem: 42%"
                new FormatOption("mb",      "tinyhud.format.memory.mb"),       // "Mem: 1234/4096 MB"
                new FormatOption("gb",      "tinyhud.format.memory.gb")        // "Mem: 1.2/4.0 GB"
        );
    }

    // Text
    // Reports this Minecraft JVM's heap usage (used vs. -Xmx), not whole-system RAM.

    @Override
    protected String text(Minecraft mc) {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        long max  = rt.maxMemory();
        return "Mem: " + (max > 0 ? Math.round(used * 100.0 / max) : 0) + "%";
    }

    @Override
    protected String text(Minecraft mc, HudLayout.Entry entry) {
        Runtime rt = Runtime.getRuntime();
        long usedB = rt.totalMemory() - rt.freeMemory();
        long maxB  = rt.maxMemory();
        if ("mb".equals(entry.format)) {
            return String.format("Mem: %d/%d MB", usedB / (1024L * 1024L), maxB / (1024L * 1024L));
        }
        if ("gb".equals(entry.format)) {
            double used = usedB / (1024.0 * 1024.0 * 1024.0);
            double max  = maxB  / (1024.0 * 1024.0 * 1024.0);
            return String.format(Locale.ROOT, "Mem: %.1f/%.1f GB", used, max);
        }
        return text(mc);   // default / "percent"
    }

    @Override
    public int defaultX(int screenW, int screenH) { return screenW - 60; }

    @Override
    public int defaultY(int screenW, int screenH) { return 54; }
}
