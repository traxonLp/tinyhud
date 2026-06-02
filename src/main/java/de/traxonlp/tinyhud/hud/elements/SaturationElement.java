package de.traxonlp.tinyhud.hud.elements;

import de.traxonlp.tinyhud.TinyHUD;
import de.traxonlp.tinyhud.hud.HudCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

public class SaturationElement extends TextHudElement {
    public SaturationElement() {
        super(Identifier.fromNamespaceAndPath(TinyHUD.MODID, "saturation"), "tinyhud.element.saturation");
    }

    @Override
    public HudCategory category() {
        return HudCategory.PLAYER;
    }

    @Override
    protected String text(Minecraft mc) {
        Player p = mc.player;
        if (p == null) return "";
        float sat = p.getFoodData().getSaturationLevel();
        return String.format(Locale.ROOT, "Saturation: %.1f", sat);
    }

    @Override
    public int defaultX(int screenW, int screenH) {
        return 4;
    }

    @Override
    public int defaultY(int screenW, int screenH) {
        return 98;
    }
}
