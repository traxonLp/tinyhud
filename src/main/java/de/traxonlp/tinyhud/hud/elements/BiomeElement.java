package de.traxonlp.tinyhud.hud.elements;

import de.traxonlp.tinyhud.TinyHUD;
import de.traxonlp.tinyhud.hud.HudCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;

public class BiomeElement extends TextHudElement {
    public BiomeElement() {
        super(Identifier.fromNamespaceAndPath(TinyHUD.MODID, "biome"), "tinyhud.element.biome");
    }

    @Override
    public HudCategory category() {
        return HudCategory.WORLD;
    }

    @Override
    protected String text(Minecraft mc) {
        ClientLevel level = mc.level;
        Player player = mc.player;
        if (level == null || player == null) return "";
        BlockPos pos = player.blockPosition();
        Holder<Biome> biome = level.getBiome(pos);
        return biome.unwrapKey()
                .map(k -> prettify(k.identifier().getPath()))
                .orElse("Unknown");
    }

    private static String prettify(String path) {
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            if (parts[i].isEmpty()) continue;
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return sb.toString();
    }

    @Override
    public int defaultX(int screenW, int screenH) {
        return 4;
    }

    @Override
    public int defaultY(int screenW, int screenH) {
        return 58;
    }
}
