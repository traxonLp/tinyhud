package de.traxonlp.tinyhud.media;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

class MacMediaBackend implements MediaBackend {

    // Spotify duration is already ms; Music duration and position are in seconds.
    // Art URL is not retrieved (AppleScript artwork coercion is unreliable across OS versions).
    private static final String SCRIPT_SPOTIFY_ONLY =
            "set sep to \"|||\"\n" +
            "if application \"Spotify\" is running then\n" +
            "  tell application \"Spotify\"\n" +
            "    if player state is playing or player state is paused then\n" +
            "      set t to name of current track\n" +
            "      set a to artist of current track\n" +
            "      set pos to (player position * 1000) as integer\n" +
            "      set dur to duration of current track\n" +
            "      set pl to (player state is playing)\n" +
            "      set art to artwork url of current track\n" +
            "      return t & sep & a & sep & pos & sep & dur & sep & pl & sep & art\n" +
            "    end if\n" +
            "  end tell\n" +
            "end if\n" +
            "return \"\"";

    private static final String SCRIPT_AUTO =
            SCRIPT_SPOTIFY_ONLY.replace("return \"\"",
            "if application \"Music\" is running then\n" +
            "  tell application \"Music\"\n" +
            "    if player state is playing or player state is paused then\n" +
            "      set t to name of current track\n" +
            "      set a to artist of current track\n" +
            "      set pos to (player position * 1000) as integer\n" +
            "      set dur to (duration of current track * 1000) as integer\n" +
            "      set pl to (player state is playing)\n" +
            "      return t & sep & a & sep & pos & sep & dur & sep & pl & sep & \"\"\n" +
            "    end if\n" +
            "  end tell\n" +
            "end if\n" +
            "return \"\"");

    @Override
    public MediaInfo poll(String source) {
        String script = "spotify".equals(source) ? SCRIPT_SPOTIFY_ONLY : SCRIPT_AUTO;
        try {
            Process proc = new ProcessBuilder("osascript", "-e", script)
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
            long   posMs  = parseLong(p[2]);
            long   durMs  = parseLong(p[3]);
            boolean play  = "true".equalsIgnoreCase(p[4].trim());

            return new MediaInfo(title, artist, posMs, durMs, play, System.nanoTime(), null);
        } catch (Exception e) {
            return MediaInfo.EMPTY;
        }
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); }
        catch (NumberFormatException e) { return 0L; }
    }
}
