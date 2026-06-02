package de.traxonlp.tinyhud.hud.elements;

import de.traxonlp.tinyhud.TinyHUD;
import de.traxonlp.tinyhud.hud.HudCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;

public class WeatherElement extends TextHudElement {
    public WeatherElement() {
        super(Identifier.fromNamespaceAndPath(TinyHUD.MODID, "weather"), "tinyhud.element.weather");
    }

    @Override
    public HudCategory category() {
        return HudCategory.WORLD;
    }

    @Override
    protected String text(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) return "";
        if (level.isThundering()) return "Thunder";
        if (level.isRaining()) return "Rain";
        return "Clear";
    }

    @Override
    public int defaultX(int screenW, int screenH) {
        return 4;
    }

    @Override
    public int defaultY(int screenW, int screenH) {
        return 24;
    }
}
