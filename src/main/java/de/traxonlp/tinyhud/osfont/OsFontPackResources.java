package de.traxonlp.tinyhud.osfont;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.traxonlp.tinyhud.TinyHUD;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OsFontPackResources implements PackResources {
    private final PackLocationInfo location;
    private final List<OsFont> fonts;
    private final Map<Identifier, byte[]> jsonCache = new HashMap<>();
    private final Map<Identifier, OsFont> ttfRoutes = new HashMap<>();

    public OsFontPackResources(PackLocationInfo location, List<OsFont> fonts) {
        this.location = location;
        this.fonts = fonts;
        for (OsFont f : fonts) {
            Identifier jsonId = Identifier.fromNamespaceAndPath(TinyHUD.MODID, "font/os_" + f.id() + ".json");
            jsonCache.put(jsonId, buildFontJson(f));
            Identifier ttfId = Identifier.fromNamespaceAndPath(TinyHUD.MODID, "font/os/" + f.id() + ".ttf");
            ttfRoutes.put(ttfId, f);
        }
    }

    private static byte[] buildFontJson(OsFont f) {
        JsonObject root = new JsonObject();
        JsonArray providers = new JsonArray();
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "ttf");
        provider.addProperty("file", TinyHUD.MODID + ":os/" + f.id() + ".ttf");
        JsonArray shift = new JsonArray();
        shift.add(0.0);
        shift.add(0.5);
        provider.add("shift", shift);
        provider.addProperty("size", 11.0f);
        provider.addProperty("oversample", 4.0f);
        providers.add(provider);
        root.add("providers", providers);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
        return null;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        if (type != PackType.CLIENT_RESOURCES) return null;
        byte[] json = jsonCache.get(location);
        if (json != null) return () -> new ByteArrayInputStream(json);
        OsFont f = ttfRoutes.get(location);
        if (f != null) return () -> Files.newInputStream(f.file());
        return null;
    }

    @Override
    public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES || !namespace.equals(TinyHUD.MODID)) return;
        for (Map.Entry<Identifier, byte[]> e : jsonCache.entrySet()) {
            if (e.getKey().getPath().startsWith(directory)) {
                byte[] bytes = e.getValue();
                output.accept(e.getKey(), () -> new ByteArrayInputStream(bytes));
            }
        }
        for (Map.Entry<Identifier, OsFont> e : ttfRoutes.entrySet()) {
            if (e.getKey().getPath().startsWith(directory)) {
                OsFont f = e.getValue();
                output.accept(e.getKey(), () -> Files.newInputStream(f.file()));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of(TinyHUD.MODID) : Set.of();
    }

    @Override
    public <T> @Nullable T getMetadataSection(MetadataSectionType<T> metadataSerializer) throws IOException {
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return location;
    }

    @Override
    public void close() {
    }

    public int fontCount() {
        return fonts.size();
    }
}
