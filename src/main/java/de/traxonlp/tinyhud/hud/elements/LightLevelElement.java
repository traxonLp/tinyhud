package de.traxonlp.tinyhud.hud.elements;

import de.traxonlp.tinyhud.TinyHUD;
import de.traxonlp.tinyhud.hud.HudCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;

public class LightLevelElement extends TextHudElement {
    public LightLevelElement() {
        super(Identifier.fromNamespaceAndPath(TinyHUD.MODID, "light_level"), "tinyhud.element.light_level");
    }

    @Override
    public HudCategory category() {
        return HudCategory.PLAYER;
    }

    @Override
    protected String text(Minecraft mc) {
        ClientLevel level = mc.level;
        Player player = mc.player;
        if (level == null || player == null) return "";
        BlockPos pos = player.blockPosition();
        int block = level.getBrightness(LightLayer.BLOCK, pos);
        int sky = level.getBrightness(LightLayer.SKY, pos);
        return "Light " + block + " (sky " + sky + ")";
    }

    @Override
    public int defaultX(int screenW, int screenH) {
        return 4;
    }

    @Override
    public int defaultY(int screenW, int screenH) {
        return 68;
    }
}
