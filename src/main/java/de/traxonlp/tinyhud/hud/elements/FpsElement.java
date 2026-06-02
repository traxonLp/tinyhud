package de.traxonlp.tinyhud.hud.elements;

import de.traxonlp.tinyhud.TinyHUD;
import de.traxonlp.tinyhud.hud.HudCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class FpsElement extends TextHudElement {
    public FpsElement() {
        super(Identifier.fromNamespaceAndPath(TinyHUD.MODID, "fps"), "tinyhud.element.fps");
    }

    @Override
    public HudCategory category() {
        return HudCategory.SYSTEM;
    }

    @Override
    protected String text(Minecraft mc) {
        return mc.getFps() + " fps";
    }

    @Override
    public int defaultX(int screenW, int screenH) {
        return screenW - 60;
    }

    @Override
    public int defaultY(int screenW, int screenH) {
        return 4;
    }
}
