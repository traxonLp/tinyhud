package de.traxonlp.tinyhud.hud.elements;

import de.traxonlp.tinyhud.TinyHUD;
import de.traxonlp.tinyhud.hud.HudCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Random;

public class SlimeChunkElement extends TextHudElement {
    public SlimeChunkElement() {
        super(Identifier.fromNamespaceAndPath(TinyHUD.MODID, "slime_chunk"), "tinyhud.element.slime_chunk");
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
        if (level.dimension() != Level.OVERWORLD) return "";
        if (!mc.hasSingleplayerServer()) return "Slime Chunk: ?";
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return "Slime Chunk: ?";
        long worldSeed = server.getWorldGenSettings().options().seed();
        int chunkX = player.blockPosition().getX() >> 4;
        int chunkZ = player.blockPosition().getZ() >> 4;
        return isSlimeChunk(worldSeed, chunkX, chunkZ) ? "Slime Chunk" : "No Slime Chunk";
    }

    private static boolean isSlimeChunk(long worldSeed, int x, int z) {
        int xx = x * x * 4987142;
        int xs = x * 5947611;
        long zz = (long) (z * z) * 4392871L;
        int zs = z * 389711;
        long combined = worldSeed + xx + xs + zz + zs;
        long seed = combined ^ 987234911L;
        return new Random(seed).nextInt(10) == 0;
    }

    @Override
    public int defaultX(int screenW, int screenH) {
        return screenW - 90;
    }

    @Override
    public int defaultY(int screenW, int screenH) {
        return 34;
    }
}
