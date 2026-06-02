package de.traxonlp.tinyhud.media;

import de.traxonlp.tinyhud.TinyHUD;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class MediaPoller {

    public static final int TEXTURE_SIZE = 256;

    private static final Identifier COVER_TEX_ID =
            Identifier.fromNamespaceAndPath(TinyHUD.MODID, "media_cover");

    private static final AtomicReference<MediaInfo> STATE = new AtomicReference<>(MediaInfo.EMPTY);
    private static volatile boolean coverReady = false;
    private static volatile String  lastArtUrl = null;
    private static volatile String  currentSource = "auto";

    private static volatile boolean started = false;

    private MediaPoller() {}

    // Public API

    /** Returns the most recently polled media state. Starts the poller on first call. */
    public static MediaInfo getState() {
        ensureStarted();
        return STATE.get();
    }

    /**
     * Sets the media source for the background poller.
     * Call this from the render thread on each frame (MediaElement.render).
     * Resets the cached state when the source changes so stale data isn't shown.
     */
    public static void setSource(String source) {
        if (source == null || source.isEmpty()) source = "auto";
        if (!source.equals(currentSource)) {
            currentSource = source;
            STATE.set(MediaInfo.EMPTY);
            lastArtUrl = null;
            coverReady = false;
            TinyHUD.LOGGER.info("[TinyHUD] Media source changed to '{}'", source);
        }
        ensureStarted();
    }

    /** True once a cover texture has been successfully registered with the TextureManager. */
    public static boolean isCoverReady() {
        return coverReady;
    }

    /**
     * Forces the cover texture to be re-registered on the next poll cycle.
     * Call when the cover display is re-enabled so the texture is refreshed.
     */
    public static void invalidateCover() {
        lastArtUrl = null;
        coverReady = false;
    }

    /** The registered texture identifier for the current cover art. */
    public static Identifier getCoverTextureId() {
        return COVER_TEX_ID;
    }

    // Lifecycle

    private static synchronized void ensureStarted() {
        if (started) return;
        started = true;
        Thread t = new Thread(MediaPoller::loop, "tinyhud-media-poller");
        t.setDaemon(true);
        t.start();
    }

    // Background polling loop

    private static void loop() {
        MediaBackend backend = createBackend();
        TinyHUD.LOGGER.info("[TinyHUD] Media poller started (backend: {})", backend.getClass().getSimpleName());
        boolean loggedFirstData = false;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String source = currentSource;
                MediaInfo info = backend.poll(source);
                if (info == null) info = MediaInfo.EMPTY;
                STATE.set(info);
                handleArtUrl(info.artUrl(), info.title(), info.artist());
                if (!loggedFirstData && info.hasData()) {
                    TinyHUD.LOGGER.info("[TinyHUD] Media: '{}' by '{}'", info.title(), info.artist());
                    loggedFirstData = true;
                }
            } catch (Exception e) {
                TinyHUD.LOGGER.debug("[TinyHUD] Media poll error: {}", e.getMessage());
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static MediaBackend createBackend() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win"))                           return new WindowsMediaBackend();
        if (os.contains("mac") || os.contains("darwin")) return new MacMediaBackend();
        if (os.contains("linux"))                         return new LinuxMediaBackend();
        return source -> MediaInfo.EMPTY;
    }

    // Cover art handling

    private static void handleArtUrl(String artUrl, String title, String artist) {
        if (artUrl == lastArtUrl) return;  // same reference - no change (base64 strings reuse same instance)
        if (Objects.equals(artUrl, lastArtUrl)) return;
        lastArtUrl = artUrl;
        coverReady = false;
        if (artUrl == null) {
            TinyHUD.LOGGER.info("[TinyHUD] Cover: no art available");
            return;
        }
        TinyHUD.LOGGER.info("[TinyHUD] Cover: loading art ({}...)", artUrl.length() > 60 ? artUrl.substring(0, 60) : artUrl);

        byte[] bytes = fetchArtBytes(artUrl);
        if (bytes == null) {
            TinyHUD.LOGGER.warn("[TinyHUD] Cover: fetch returned null");
            return;
        }
        TinyHUD.LOGGER.info("[TinyHUD] Cover: fetched {} bytes", bytes.length);

        // Decode + bicubic-scale to TEXTURE_SIZExTEXTURE_SIZE off-thread
        NativeImage resized;
        try {
            java.awt.image.BufferedImage src =
                    javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes));
            if (src == null) throw new Exception("ImageIO returned null");
            java.awt.image.BufferedImage scaled =
                    new java.awt.image.BufferedImage(TEXTURE_SIZE, TEXTURE_SIZE,
                                                     java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g2d = scaled.createGraphics();
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                                 java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                                 java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            g2d.drawImage(src, 0, 0, TEXTURE_SIZE, TEXTURE_SIZE, null);
            g2d.dispose();
            ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(scaled, "PNG", pngOut);
            resized = NativeImage.read(new ByteArrayInputStream(pngOut.toByteArray()));
        } catch (Exception e) {
            TinyHUD.LOGGER.warn("[TinyHUD] Cover image decode failed: {}", e.getMessage());
            return;
        }
        TinyHUD.LOGGER.info("[TinyHUD] Cover: scaled to {}x{}", resized.getWidth(), resized.getHeight());

        // Texture registration and upload must happen on the main/render thread.
        Minecraft.getInstance().execute(() -> {
            try {
                DynamicTexture tex = new DynamicTexture(() -> "tinyhud:media_cover", resized);
                Minecraft.getInstance().getTextureManager().register(COVER_TEX_ID, tex);
                tex.upload();
                coverReady = true;
                TinyHUD.LOGGER.info("[TinyHUD] Cover: texture uploaded");
            } catch (Exception e) {
                TinyHUD.LOGGER.warn("[TinyHUD] Cover texture upload failed: {}", e.getMessage());
                resized.close();
            }
        });
    }

    private static byte[] fetchArtBytes(String artUrl) {
        try {
            if (artUrl.startsWith("base64:")) {
                return Base64.getDecoder().decode(artUrl.substring(7));
            }
            if (artUrl.startsWith("file://")) {
                return Files.readAllBytes(Path.of(URI.create(artUrl)));
            }
            if (artUrl.startsWith("http://") || artUrl.startsWith("https://")) {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(3))
                        .build();
                HttpRequest req = HttpRequest.newBuilder(URI.create(artUrl))
                        .timeout(Duration.ofSeconds(5))
                        .build();
                HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
                return resp.statusCode() == 200 ? resp.body() : null;
            }
        } catch (Exception e) {
            TinyHUD.LOGGER.debug("[TinyHUD] Cover fetch failed for '{}': {}", artUrl, e.getMessage());
        }
        return null;
    }
}
