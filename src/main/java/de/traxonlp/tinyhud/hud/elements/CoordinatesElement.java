package de.traxonlp.tinyhud.hud.elements;

import de.traxonlp.tinyhud.TinyHUD;
import de.traxonlp.tinyhud.hud.HudCategory;
import de.traxonlp.tinyhud.hud.HudLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

public class CoordinatesElement extends TextHudElement {

    public CoordinatesElement() {
        super(Identifier.fromNamespaceAndPath(TinyHUD.MODID, "coordinates"), "tinyhud.element.coordinates");
    }

    @Override
    public HudCategory category() { return HudCategory.PLAYER; }

    @Override
    public List<FormatOption> formats() {
        return List.of(
                new FormatOption("floored",     "tinyhud.format.coords.floored"),      // X, Y, Z
                new FormatOption("decimal",     "tinyhud.format.coords.decimal"),      // X.X, Y.Y, Z.Z
                new FormatOption("floored_dim", "tinyhud.format.coords.floored_dim"),  // [OW] X, Y, Z
                new FormatOption("decimal_dim", "tinyhud.format.coords.decimal_dim")   // [OW] X.X, Y.Y, Z.Z
        );
    }

    // Text

    @Override
    protected String text(Minecraft mc) { return ""; }

    @Override
    protected String text(Minecraft mc, HudLayout.Entry entry) {
        Player p = mc.player;
        if (p == null) return "";

        boolean decimal = "decimal".equals(entry.format) || "decimal_dim".equals(entry.format);
        boolean showDim = "floored_dim".equals(entry.format) || "decimal_dim".equals(entry.format);

        String coords = decimal
                ? String.format(Locale.ROOT, "%.1f, %.1f, %.1f", p.getX(), p.getY(), p.getZ())
                : String.format("%d, %d, %d",
                        (int) Math.floor(p.getX()),
                        (int) Math.floor(p.getY()),
                        (int) Math.floor(p.getZ()));

        if (showDim && mc.level != null) {
            String dim;
            if (mc.level.dimension().equals(Level.NETHER))         dim = "NE";
            else if (mc.level.dimension().equals(Level.END))       dim = "END";
            else                                                    dim = "OW";
            return dim + ": " + coords;
        }
        return coords;
    }

    @Override public int defaultX(int screenW, int screenH) { return 4; }
    @Override public int defaultY(int screenW, int screenH) { return 38; }
}
