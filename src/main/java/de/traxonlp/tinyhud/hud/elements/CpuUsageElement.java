package de.traxonlp.tinyhud.hud.elements;

import de.traxonlp.tinyhud.TinyHUD;
import de.traxonlp.tinyhud.hud.HudCategory;
import de.traxonlp.tinyhud.util.SystemMetrics;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class CpuUsageElement extends TextHudElement {
    public CpuUsageElement() {
        super(Identifier.fromNamespaceAndPath(TinyHUD.MODID, "cpu_usage"), "tinyhud.element.cpu_usage");
    }

    @Override
    public HudCategory category() {
        return HudCategory.SYSTEM;
    }

    @Override
    protected String text(Minecraft mc) {
        return "CPU: " + Math.round(SystemMetrics.cpuLoad() * 100.0) + "%";
    }

    @Override
    public int defaultX(int screenW, int screenH) {
        return screenW - 60;
    }

    @Override
    public int defaultY(int screenW, int screenH) {
        return 44;
    }
}
