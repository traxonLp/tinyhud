package de.traxonlp.tinyhud.osfont;

import de.traxonlp.tinyhud.TinyHUD;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class OsFontDiscovery {
    private OsFontDiscovery() {
    }

    public static List<OsFont> discover() {
        List<Path> dirs = directoriesForOs();
        Map<String, OsFont> byId = new HashMap<>();
        for (Path dir : dirs) {
            scan(dir, byId);
        }
        List<OsFont> result = new ArrayList<>(byId.values());
        result.sort(Comparator.comparing(OsFont::displayName, String.CASE_INSENSITIVE_ORDER));
        TinyHUD.LOGGER.info("Discovered {} OS fonts.", result.size());
        return result;
    }

    private static List<Path> directoriesForOs() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<Path> dirs = new ArrayList<>();
        if (os.contains("win")) {
            dirs.add(Paths.get(System.getenv().getOrDefault("WINDIR", "C:\\Windows"), "Fonts"));
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                dirs.add(Paths.get(localAppData, "Microsoft", "Windows", "Fonts"));
            }
        } else if (os.contains("mac")) {
            dirs.add(Paths.get("/Library/Fonts"));
            dirs.add(Paths.get("/System/Library/Fonts"));
            dirs.add(Paths.get(System.getProperty("user.home"), "Library", "Fonts"));
        } else {
            dirs.add(Paths.get("/usr/share/fonts"));
            dirs.add(Paths.get("/usr/local/share/fonts"));
            dirs.add(Paths.get(System.getProperty("user.home"), ".fonts"));
            dirs.add(Paths.get(System.getProperty("user.home"), ".local", "share", "fonts"));
        }
        return dirs;
    }

    private static void scan(Path dir, Map<String, OsFont> byId) {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> stream = Files.walk(dir, 8)) {
            stream.filter(Files::isRegularFile)
                    .filter(OsFontDiscovery::isFontFile)
                    .forEach(p -> add(p, byId));
        } catch (IOException ex) {
            TinyHUD.LOGGER.warn("Failed to scan font directory {}: {}", dir, ex.getMessage());
        }
    }

    private static boolean isFontFile(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".ttf") || n.endsWith(".ttc");
    }

    /**
     * Returns true if the font file contains a standard Unicode cmap entry.
     * Fonts without one (symbol fonts, Wingdings, etc.) are rejected because
     * Minecraft's TTF provider requires a Unicode charmap to render text.
     *
     * Handles both single TTF and TTC (font collection) files by checking
     * only the first font in a collection.
     */
    static boolean hasUnicodeCmap(Path file) {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            byte[] sig = new byte[4];
            raf.readFully(sig);

            long fontOffset = 0;
            if (sig[0] == 't' && sig[1] == 't' && sig[2] == 'c' && sig[3] == 'f') {
                // TTC collection: version (4 bytes), numFonts (4 bytes), then font offsets
                raf.skipBytes(4);
                int numFonts = raf.readInt();
                if (numFonts < 1) return false;
                fontOffset = Integer.toUnsignedLong(raf.readInt());
            }

            // Table directory: skip sfVersion (4), read numTables (2), skip 6 more
            raf.seek(fontOffset + 4);
            int numTables = raf.readUnsignedShort();
            raf.skipBytes(6);

            // Scan table records for "cmap"
            long cmapOffset = -1;
            for (int i = 0; i < numTables; i++) {
                byte[] tag = new byte[4];
                raf.readFully(tag);
                raf.skipBytes(4); // checkSum
                long tblOffset = Integer.toUnsignedLong(raf.readInt());
                raf.skipBytes(4); // length
                if (tag[0] == 'c' && tag[1] == 'm' && tag[2] == 'a' && tag[3] == 'p') {
                    cmapOffset = tblOffset;
                    break;
                }
            }

            if (cmapOffset < 0) return false;

            raf.seek(cmapOffset + 2); // skip version
            int numEncodings = raf.readUnsignedShort();
            for (int i = 0; i < numEncodings; i++) {
                int platformID = raf.readUnsignedShort();
                int encodingID = raf.readUnsignedShort();
                raf.skipBytes(4); // offset to subtable
                // Platform 0 = Unicode; Platform 3 encoding 1 = Windows BMP, 10 = full Unicode
                if (platformID == 0
                        || (platformID == 3 && (encodingID == 1 || encodingID == 10))) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static final Set<String> USED_IDS = new HashSet<>();

    private static void add(Path file, Map<String, OsFont> byId) {
        if (!hasUnicodeCmap(file)) return;
        String filename = file.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        String stem = dot > 0 ? filename.substring(0, dot) : filename;
        String id = sanitizeId(stem);
        if (id.isEmpty()) return;
        String unique = id;
        int suffix = 2;
        while (byId.containsKey(unique)) {
            unique = id + "_" + suffix++;
        }
        byId.put(unique, new OsFont(unique, prettyName(stem), file));
    }

    private static String sanitizeId(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                sb.append(c);
            } else {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') sb.append('_');
            }
        }
        while (sb.length() > 0 && (sb.charAt(0) == '_' || sb.charAt(0) == '-' || sb.charAt(0) == '.')) {
            sb.deleteCharAt(0);
        }
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '_') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    private static String prettyName(String stem) {
        StringBuilder sb = new StringBuilder(stem.length() + 4);
        for (int i = 0; i < stem.length(); i++) {
            char c = stem.charAt(i);
            if (c == '_' || c == '-') {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') sb.append(' ');
            } else {
                if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(stem.charAt(i - 1))) {
                    sb.append(' ');
                }
                sb.append(c);
            }
        }
        return sb.length() == 0 ? stem : sb.toString();
    }
}
