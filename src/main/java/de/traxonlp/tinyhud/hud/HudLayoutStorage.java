package de.traxonlp.tinyhud.hud;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.traxonlp.tinyhud.TinyHUD;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLPaths;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public final class HudLayoutStorage {
    private static final String FILE_NAME = "tinyhud-layout.json";

    private HudLayoutStorage() {
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    public static void load() {
        Path path = file();
        if (!Files.exists(path)) {
            TinyHUD.LOGGER.info("No TinyHUD layout file found; using defaults.");
            return;
        }
        try {
            Map<Identifier, HudLayout.Entry> parsed = deserialize(Files.readString(path));
            if (parsed == null) return;
            HudLayout.get().replaceWith(parsed);
            TinyHUD.LOGGER.info("Loaded TinyHUD layout ({} entries).", parsed.size());
        } catch (IOException | RuntimeException ex) {
            TinyHUD.LOGGER.error("Failed to load TinyHUD layout from {}", path, ex);
        }
    }

    public static void save() {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(FILE_NAME + ".tmp");
            Files.writeString(tmp, serialize(HudLayout.get().view()).toString());
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            TinyHUD.LOGGER.error("Failed to save TinyHUD layout to {}", path, ex);
        }
    }

    // Export / Import (native file dialogs)

    /**
     * Opens a native save dialog and writes the given layout to the chosen file (pretty-printed).
     * Runs the dialog on the calling (render) thread - it blocks until the user confirms or cancels.
     */
    public static void exportToFile(Map<Identifier, HudLayout.Entry> entries) {
        try {
            String chosen = TinyFileDialogs.tinyfd_saveFileDialog(
                    "Export TinyHUD config", FILE_NAME, null, "JSON config");
            if (chosen == null || chosen.isBlank()) return;   // cancelled
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(serialize(entries));
            Files.writeString(Path.of(chosen), json);
            TinyHUD.LOGGER.info("Exported TinyHUD config to {}", chosen);
        } catch (Exception ex) {
            TinyHUD.LOGGER.error("Failed to export TinyHUD config", ex);
        }
    }

    /**
     * Opens a native open dialog and parses the chosen file.
     * @return the parsed layout, or {@code null} if cancelled or the file was invalid.
     */
    public static Map<Identifier, HudLayout.Entry> importFromFile() {
        try {
            String chosen = TinyFileDialogs.tinyfd_openFileDialog(
                    "Import TinyHUD config", "", null, "JSON config", false);
            if (chosen == null || chosen.isBlank()) return null;   // cancelled
            Map<Identifier, HudLayout.Entry> parsed = deserialize(Files.readString(Path.of(chosen)));
            if (parsed == null || parsed.isEmpty()) {
                TinyHUD.LOGGER.warn("Imported config from {} had no valid entries", chosen);
                return null;
            }
            TinyHUD.LOGGER.info("Imported TinyHUD config from {} ({} entries)", chosen, parsed.size());
            return parsed;
        } catch (Exception ex) {
            TinyHUD.LOGGER.error("Failed to import TinyHUD config", ex);
            return null;
        }
    }

    // (De)serialization

    private static JsonObject serialize(Map<Identifier, HudLayout.Entry> entries) {
        JsonObject root = new JsonObject();
        JsonObject elements = new JsonObject();
        for (Map.Entry<Identifier, HudLayout.Entry> e : entries.entrySet()) {
            HudLayout.Entry entry = e.getValue();
            JsonObject entryObj = new JsonObject();
            entryObj.addProperty("x", entry.xFrac);
            entryObj.addProperty("y", entry.yFrac);
            entryObj.addProperty("visible", entry.visible);
            entryObj.addProperty("scale", entry.scale);
            entryObj.addProperty("color", entry.color);
            entryObj.addProperty("color2", entry.color2);
            entryObj.addProperty("font", entry.font);
            entryObj.addProperty("rainbow", entry.rainbow);
            entryObj.addProperty("box", entry.box);
            entryObj.addProperty("format", entry.format);
            entryObj.addProperty("showCover", entry.showCover);
            entryObj.addProperty("hideWhenZero", entry.hideWhenZero);
            elements.add(e.getKey().toString(), entryObj);
        }
        root.add("elements", elements);
        return root;
    }

    /** Parses a layout JSON document. Returns {@code null} if the structure is invalid. */
    private static Map<Identifier, HudLayout.Entry> deserialize(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) return null;
        JsonElement elementsEl = root.getAsJsonObject().get("elements");
        if (elementsEl == null || !elementsEl.isJsonObject()) return null;
        Map<Identifier, HudLayout.Entry> parsed = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : elementsEl.getAsJsonObject().entrySet()) {
            Identifier id;
            try {
                id = Identifier.parse(e.getKey());
            } catch (Exception ex) {
                TinyHUD.LOGGER.warn("Skipping malformed element id in layout: {}", e.getKey());
                continue;
            }
            if (HudElements.byId(id) == null) {
                TinyHUD.LOGGER.warn("Skipping unknown HUD element id in layout: {}", id);
                continue;
            }
            if (!e.getValue().isJsonObject()) continue;
            JsonObject o = e.getValue().getAsJsonObject();
            float xFrac = o.has("x") ? o.get("x").getAsFloat() : 0f;
            float yFrac = o.has("y") ? o.get("y").getAsFloat() : 0f;
            boolean visible = !o.has("visible") || o.get("visible").getAsBoolean();
            float scale = o.has("scale") ? o.get("scale").getAsFloat() : HudLayout.DEFAULT_SCALE;
            int color = o.has("color") ? o.get("color").getAsInt() : HudLayout.DEFAULT_COLOR;
            int color2 = o.has("color2") ? o.get("color2").getAsInt() : HudLayout.DEFAULT_COLOR2;
            String font = o.has("font") ? o.get("font").getAsString() : HudLayout.DEFAULT_FONT;
            boolean rainbow = o.has("rainbow") && o.get("rainbow").getAsBoolean();
            boolean box = o.has("box") && o.get("box").getAsBoolean();
            String format = o.has("format") ? o.get("format").getAsString() : HudLayout.DEFAULT_FORMAT;
            boolean showCover = !o.has("showCover") || o.get("showCover").getAsBoolean();
            boolean hideWhenZero = o.has("hideWhenZero") && o.get("hideWhenZero").getAsBoolean();
            parsed.put(id, new HudLayout.Entry(xFrac, yFrac, visible, scale, color, color2, font, rainbow, box, format, showCover, hideWhenZero));
        }
        return parsed;
    }
}
