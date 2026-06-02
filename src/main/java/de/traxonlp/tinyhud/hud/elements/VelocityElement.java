package de.traxonlp.tinyhud.hud.elements;

import de.traxonlp.tinyhud.TinyHUD;
import de.traxonlp.tinyhud.hud.HudCategory;
import de.traxonlp.tinyhud.hud.HudLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

public class VelocityElement extends TextHudElement {

    public VelocityElement() {
        super(Identifier.fromNamespaceAndPath(TinyHUD.MODID, "velocity"), "tinyhud.element.velocity");
    }

    @Override
    public HudCategory category() { return HudCategory.PLAYER; }

    @Override
    public List<FormatOption> formats() {
        return List.of(
                new FormatOption("ms",  "tinyhud.format.velocity.ms"),   // 3-D total, m/s
                new FormatOption("hms", "tinyhud.format.velocity.hms"),  // horizontal XZ, m/s
                new FormatOption("kph", "tinyhud.format.velocity.kph")   // 3-D total, km/h
        );
    }

    // Text

    @Override
    protected String text(Minecraft mc) {
        // Fallback used only when no entry is available (e.g. width probing).
        Player p = mc.player;
        if (p == null) return "";
        Vec3 v = p.getDeltaMovement();
        double mps = Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z) * 20.0;
        return String.format(Locale.ROOT, "%.1f m/s", mps);
    }

    @Override
    protected String text(Minecraft mc, HudLayout.Entry entry) {
        Player p = mc.player;
        if (p == null) return "";
        Vec3 v = p.getDeltaMovement();

        if ("hms".equals(entry.format)) {
            // Horizontal (XZ) speed only
            double horiz = Math.sqrt(v.x * v.x + v.z * v.z) * 20.0;
            return String.format(Locale.ROOT, "H: %.1f m/s", horiz);
        }
        if ("kph".equals(entry.format)) {
            // 3-D total in km/h  (1 m/s = 3.6 km/h)
            double kph = Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z) * 20.0 * 3.6;
            return String.format(Locale.ROOT, "%.1f km/h", kph);
        }
        // Default: "ms" - 3-D total in m/s
        double mps = Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z) * 20.0;
        return String.format(Locale.ROOT, "%.1f m/s", mps);
    }

    @Override public int defaultX(int screenW, int screenH) { return 4; }
    @Override public int defaultY(int screenW, int screenH) { return 78; }
}
