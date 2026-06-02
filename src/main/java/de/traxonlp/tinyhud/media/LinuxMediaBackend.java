package de.traxonlp.tinyhud.media;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

class LinuxMediaBackend implements MediaBackend {

    // position and mpris:length are in microseconds.
    private static final String FORMAT =
            "{{title}}|||{{artist}}|||{{position}}|||{{mpris:length}}|||{{status}}|||{{mpris:artUrl}}";

    @Override
    public MediaInfo poll(String source) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("playerctl");
            if ("spotify".equals(source)) {
                cmd.add("--player=spotify");
            }
            cmd.add("metadata");
            cmd.add("--format");
            cmd.add(FORMAT);

            Process proc = new ProcessBuilder(cmd)
                    .redirectErrorStream(false)
                    .start();
            String line;
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                line = br.readLine();
            }
            proc.waitFor();
            if (line == null || line.isBlank()) return MediaInfo.EMPTY;

            String[] p = line.split("\\|\\|\\|", -1);
            if (p.length < 5) return MediaInfo.EMPTY;

            String title  = p[0];
            String artist = p[1];
            long   posMs  = parseLong(p[2]) / 1000;   // µs -> ms
            long   durMs  = parseLong(p[3]) / 1000;   // µs -> ms
            boolean play  = "Playing".equals(p[4].trim());
            String artUrl = p.length >= 6 && !p[5].isBlank() ? p[5].trim() : null;

            return new MediaInfo(title, artist, posMs, durMs, play, System.nanoTime(), artUrl);
        } catch (Exception e) {
            return MediaInfo.EMPTY;
        }
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); }
        catch (NumberFormatException e) { return 0L; }
    }
}
