package de.traxonlp.tinyhud.hud.elements;

import de.traxonlp.tinyhud.TinyHUD;
import de.traxonlp.tinyhud.hud.HudCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;

public class DayElement extends TextHudElement {
    public DayElement() {
        super(Identifier.fromNamespaceAndPath(TinyHUD.MODID, "day"), "tinyhud.element.day");
    }

    @Override
    public HudCategory category() {
        return HudCategory.WORLD;
    }

    @Override
    protected String text(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) return "";
        long day = level.getOverworldClockTime() / 24000L + 1L;
        return "Day " + day;
    }

    @Override
    public int defaultX(int screenW, int screenH) {
        return 4;
    }

    @Override
    public int defaultY(int screenW, int screenH) {
        return 14;
    }
}
