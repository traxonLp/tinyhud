package de.traxonlp.tinyhud.hud.elements;

import de.traxonlp.tinyhud.TinyHUD;
import de.traxonlp.tinyhud.hud.HudCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;

public class PingElement extends TextHudElement {
    public PingElement() {
        super(Identifier.fromNamespaceAndPath(TinyHUD.MODID, "ping"), "tinyhud.element.ping");
    }

    @Override
    public HudCategory category() {
        return HudCategory.SYSTEM;
    }

    @Override
    protected String text(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) return "";
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        if (info == null) return "";
        int latency = info.getLatency();
        if (latency <= 0 && mc.hasSingleplayerServer()) return "";
        return latency + " ms";
    }

    @Override
    public int defaultX(int screenW, int screenH) {
        return screenW - 60;
    }

    @Override
    public int defaultY(int screenW, int screenH) {
        return 14;
    }
}
