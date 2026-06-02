package de.traxonlp.tinyhud.osfont;

import de.traxonlp.tinyhud.TinyHUD;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OsFonts {
    private static final Map<String, OsFont> BY_ID = new LinkedHashMap<>();
    private static List<OsFont> all = List.of();

    private OsFonts() {
    }

    static void set(List<OsFont> fonts) {
        BY_ID.clear();
        for (OsFont f : fonts) BY_ID.put(f.id(), f);
        all = List.copyOf(fonts);
    }

    public static List<OsFont> all() {
        return all;
    }

    public static OsFont byId(String id) {
        return BY_ID.get(id);
    }

    public static String fontKeyFor(OsFont font) {
        return TinyHUD.MODID + ":os_" + font.id();
    }

    public static Identifier fontIdentifier(OsFont font) {
        return Identifier.fromNamespaceAndPath(TinyHUD.MODID, "os_" + font.id());
    }
}
