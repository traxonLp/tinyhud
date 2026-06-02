package de.traxonlp.tinyhud.media;

public record MediaInfo(
        String title,
        String artist,
        long positionMs,
        long durationMs,
        boolean playing,
        long pollTimeNs,
        String artUrl          // file://, https://, base64:<data>, or null
) {
    public static final MediaInfo EMPTY = new MediaInfo("", "", 0, 0, false, 0L, null);

    public boolean hasData() {
        return !title.isEmpty() || !artist.isEmpty();
    }
}
