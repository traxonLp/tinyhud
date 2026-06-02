package de.traxonlp.tinyhud.hud.elements;

import de.traxonlp.tinyhud.TinyHUD;
import de.traxonlp.tinyhud.hud.HudCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;

public class EntityCountElement extends TextHudElement {
    public EntityCountElement() {
        super(Identifier.fromNamespaceAndPath(TinyHUD.MODID, "entity_count"), "tinyhud.element.entity_count");
    }

    @Override
    public HudCategory category() {
        return HudCategory.WORLD;
    }

    @Override
    protected String text(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) return "";
        return "Entities: " + level.getEntityCount();
    }

    @Override
    public int defaultX(int screenW, int screenH) {
        return screenW - 90;
    }

    @Override
    public int defaultY(int screenW, int screenH) {
        return 24;
    }
}
