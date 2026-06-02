package de.traxonlp.tinyhud.hud.elements;

import de.traxonlp.tinyhud.TinyHUD;
import de.traxonlp.tinyhud.hud.HudCategory;
import de.traxonlp.tinyhud.util.SystemMetrics;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class GpuUsageElement extends TextHudElement {
    public GpuUsageElement() {
        super(Identifier.fromNamespaceAndPath(TinyHUD.MODID, "gpu_usage"), "tinyhud.element.gpu_usage");
    }

    @Override
    public HudCategory category() {
        return HudCategory.SYSTEM;
    }

    @Override
    protected String text(Minecraft mc) {
        Integer pct = SystemMetrics.gpuPercent();
        return "GPU: " + (pct == null ? "?" : pct + "%");
    }

    @Override
    public int defaultX(int screenW, int screenH) {
        return screenW - 60;
    }

    @Override
    public int defaultY(int screenW, int screenH) {
        return 64;
    }
}
