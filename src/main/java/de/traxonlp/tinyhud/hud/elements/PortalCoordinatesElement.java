package de.traxonlp.tinyhud.hud.elements;

import de.traxonlp.tinyhud.TinyHUD;
import de.traxonlp.tinyhud.hud.HudCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class PortalCoordinatesElement extends TextHudElement {
    public PortalCoordinatesElement() {
        super(Identifier.fromNamespaceAndPath(TinyHUD.MODID, "portal_coordinates"), "tinyhud.element.portal_coordinates");
    }

    @Override
    public HudCategory category() {
        return HudCategory.WORLD;
    }

    @Override
    protected String text(Minecraft mc) {
        ClientLevel level = mc.level;
        Player p = mc.player;
        if (level == null || p == null) return "";
        ResourceKey<Level> dim = level.dimension();
        int x = (int) Math.floor(p.getX());
        int z = (int) Math.floor(p.getZ());
        if (dim == Level.OVERWORLD) {
            return "Nether: " + (x / 8) + ", " + (z / 8);
        } else if (dim == Level.NETHER) {
            return "Overworld: " + (x * 8) + ", " + (z * 8);
        }
        return "";
    }

    @Override
    public int defaultX(int screenW, int screenH) {
        return 4;
    }

    @Override
    public int defaultY(int screenW, int screenH) {
        return 88;
    }
}
