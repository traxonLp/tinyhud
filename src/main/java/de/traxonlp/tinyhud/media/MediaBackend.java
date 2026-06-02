package de.traxonlp.tinyhud.media;

interface MediaBackend {
    /** @param source one of "auto", "spotify" (or "" treated as "auto") */
    MediaInfo poll(String source);
}
