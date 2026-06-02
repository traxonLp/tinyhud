package de.traxonlp.tinyhud.hud.elements;

import de.traxonlp.tinyhud.TinyHUD;
import de.traxonlp.tinyhud.hud.HudCategory;
import de.traxonlp.tinyhud.hud.HudLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public class DirectionElement extends TextHudElement {

    // index 0 = South (yaw 0), clockwise: S SW W NW N NE E SE
    private static final String[] COMPASS_SHORT = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private static final String[] COMPASS_FULL  = {
            "South", "Southwest", "West", "Northwest",
            "North", "Northeast", "East", "Southeast"
    };

    public DirectionElement() {
        super(Identifier.fromNamespaceAndPath(TinyHUD.MODID, "direction"), "tinyhud.element.direction");
    }

    @Override
    public HudCategory category() { return HudCategory.PLAYER; }

    @Override
    public List<FormatOption> formats() {
        return List.of(
                new FormatOption("short",   "tinyhud.format.direction.short"),   // "N", "NE", …
                new FormatOption("full",    "tinyhud.format.direction.full"),    // "North", …
                new FormatOption("degrees", "tinyhud.format.direction.degrees")  // "270°"
        );
    }

    // Text

    @Override
    protected String text(Minecraft mc) {
        return compassShort(mc);
    }

    @Override
    protected String text(Minecraft mc, HudLayout.Entry entry) {
        if ("full".equals(entry.format))    return compassFull(mc);
        if ("degrees".equals(entry.format)) return bearingDegrees(mc);
        return compassShort(mc);    // default: "short" (also covers "" for fresh entries)
    }

    // Helpers

    private static String compassShort(Minecraft mc) {
        Player p = mc.player;
        if (p == null) return "";
        return COMPASS_SHORT[compassIndex(p)];
    }

    private static String compassFull(Minecraft mc) {
        Player p = mc.player;
        if (p == null) return "";
        return COMPASS_FULL[compassIndex(p)];
    }

    private static String bearingDegrees(Minecraft mc) {
        Player p = mc.player;
        if (p == null) return "";
        // Minecraft yaw: 0 = South, -90 = East, ±180 = North, 90 = West.
        // Bearing from North clockwise: N=0°, E=90°, S=180°, W=270°.
        int bearing = ((Math.round(p.getYRot()) + 180) % 360 + 360) % 360;
        return bearing + "°";
    }

    /** Returns 0-7 compass octant index (0 = S, clockwise). */
    private static int compassIndex(Player p) {
        float yaw = ((p.getYRot() % 360f) + 360f) % 360f;
        return Math.round(yaw / 45f) % 8;
    }

    @Override public int defaultX(int screenW, int screenH) { return 4; }
    @Override public int defaultY(int screenW, int screenH) { return 48; }
}
