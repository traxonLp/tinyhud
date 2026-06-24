package de.traxonlp.tinyhud.hud;

import de.traxonlp.tinyhud.TinyHUD;
import de.traxonlp.tinyhud.editor.HudEditorScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;

public class HudRenderer implements GuiLayer {

    @Override
    public void render(GuiGraphicsExtractor gfx, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof HudEditorScreen) return;
        if (mc.gui.hud.isHidden()) return;   // respect vanilla "Hide HUD" (F1)
        if (mc.player == null || mc.level == null) return;
        int sw = gfx.guiWidth();
        int sh = gfx.guiHeight();
        for (HudElement element : HudElements.all()) {
            HudLayout.Entry entry = HudLayout.get().entry(element, sw, sh);
            if (!entry.visible) continue;
            try {
                element.render(gfx, delta, entry.px(sw), entry.py(sh), entry);
            } catch (Exception e) {
                TinyHUD.LOGGER.error("[TinyHUD] Exception rendering element '{}': {}", element.id(), e.getMessage(), e);
            }
        }
    }
}
