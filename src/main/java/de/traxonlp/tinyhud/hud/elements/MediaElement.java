package de.traxonlp.tinyhud.hud.elements;

import de.traxonlp.tinyhud.TinyHUD;
import de.traxonlp.tinyhud.hud.HudCategory;
import de.traxonlp.tinyhud.hud.HudElement;
import de.traxonlp.tinyhud.hud.HudLayout;
import de.traxonlp.tinyhud.media.MediaInfo;
import de.traxonlp.tinyhud.media.MediaPoller;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.List;

public class MediaElement implements HudElement {

    private static final int COVER_SIZE   = 40;
    private static final int COVER_GAP    = 4;
    private static final int BAR_HEIGHT   = 4;
    private static final int LINE_GAP     = 2;
    private static final int BOX_PADDING  = 2;
    private static final int BOX_COLOR    = 0xA0000000;
    private static final int MIN_BAR_W    = 120;
    private static final int BAR_TRACK    = 0x40FFFFFF;

    private final Identifier id =
            Identifier.fromNamespaceAndPath(TinyHUD.MODID, "media");

    private int     lastWidth     = MIN_BAR_W;
    private int     lastHeight    = 9;
    private boolean lastShowCover = true;

    // Render-side position smoothing - a local clock that advances by real frame time and
    // eases toward the polled position, so the OS timeline's jitter never causes a visible jump.
    private double displayMs   = 0;
    private long   lastFrameNs = 0L;
    private String smoothedKey = "";

    @Override public Identifier  id()            { return id; }
    @Override public HudCategory category()       { return HudCategory.SYSTEM; }
    @Override public String      translationKey() { return "tinyhud.element.media"; }
    @Override public int         width()          { return lastWidth; }
    @Override public int         height()         { return lastHeight; }
    @Override public boolean     hasAccentColor()  { return true; }
    @Override public boolean     hasCoverToggle()  { return true; }

    @Override public int defaultX(int sw, int sh) { return 4; }
    @Override public int defaultY(int sw, int sh) { return sh - 60; }

    @Override
    public List<FormatOption> formats() {
        return List.of(
                new FormatOption("auto",    "tinyhud.format.media.auto"),
                new FormatOption("spotify", "tinyhud.format.media.spotify")
        );
    }

    @Override
    public void render(GuiGraphicsExtractor gfx, DeltaTracker delta, int x, int y, HudLayout.Entry entry) {
        MediaPoller.setSource(entry.format);
        if (entry.showCover && !lastShowCover) {
            MediaPoller.invalidateCover();
        }
        lastShowCover = entry.showCover;
        MediaInfo state = MediaPoller.getState();
        if (!state.hasData()) {
            lastWidth  = 1;
            lastHeight = 1;
            return;
        }

        Minecraft mc   = Minecraft.getInstance();
        Font      font = mc.font;
        Identifier fontId = parseFont(entry.font);
        Style      styled = Style.EMPTY.withFont(new FontDescription.Resource(fontId));

        // Cover art present?
        boolean hasCover = entry.showCover && MediaPoller.isCoverReady();

        // Components
        String titleText  = truncate(state.title(),  font, styled, 200);
        String artistText = truncate(state.artist(), font, styled, 200);
        MutableComponent titleComp  = Component.literal(titleText).withStyle(styled);
        MutableComponent artistComp = Component.literal(artistText).withStyle(styled);

        // Smoothed progress. Advance a local clock by real frame time and ease it toward the
        // polled position; snap only on a track change or a real seek (>2.5 s gap). This absorbs
        // the jitter and small backward steps in the OS-reported playback timeline.
        long   now      = System.nanoTime();
        String trackKey = state.title() + "\t" + state.artist();
        boolean trackChanged = !trackKey.equals(smoothedKey);

        long elapsedMs = (now - state.pollTimeNs()) / 1_000_000L;
        long targetMs  = state.positionMs() + (state.playing() ? elapsedMs : 0);
        targetMs = Math.max(0, Math.min(targetMs, state.durationMs()));

        if (trackChanged || lastFrameNs == 0L) {
            displayMs   = targetMs;
            smoothedKey = trackKey;
        } else {
            long frameDeltaMs = (now - lastFrameNs) / 1_000_000L;
            if (state.playing()) displayMs += frameDeltaMs;   // run the local clock forward
            double diff = targetMs - displayMs;
            if (Math.abs(diff) > 2500) {
                displayMs = targetMs;                          // real seek / big desync - snap
            } else {
                displayMs += diff * 0.10;                      // ease out small corrections
            }
        }
        lastFrameNs = now;
        displayMs = Math.max(0, Math.min(displayMs, state.durationMs()));
        long  posMs = (long) displayMs;
        float frac  = state.durationMs() > 0 ? (float) posMs / state.durationMs() : 0f;

        String timeStr = formatTime(posMs) + " / " + formatTime(state.durationMs());
        MutableComponent timeComp = Component.literal(timeStr).withStyle(styled);

        int lineH  = font.lineHeight;
        int titleW = font.width(titleComp);
        int artistW = font.width(artistComp);
        int timeW   = font.width(timeComp);
        int barW    = Math.max(MIN_BAR_W, Math.max(titleW, Math.max(artistW, timeW)));

        // Content block: title + artist + bar + time
        int contentH = lineH + LINE_GAP + lineH + LINE_GAP + BAR_HEIGHT + LINE_GAP + lineH;
        int totalH   = hasCover ? Math.max(COVER_SIZE, contentH) : contentH;
        int totalW   = (hasCover ? COVER_SIZE + COVER_GAP : 0) + barW;

        lastWidth  = Math.max(1, totalW);
        lastHeight = Math.max(1, totalH);

        // Scale transform
        Matrix3x2fStack pose   = gfx.pose();
        boolean         scaled = entry.scale != 1.0f;
        if (scaled) {
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(entry.scale, entry.scale);
        }
        int bx = scaled ? 0 : x;
        int by = scaled ? 0 : y;
        int tx = hasCover ? bx + COVER_SIZE + COVER_GAP : bx;   // text column X

        if (entry.box) {
            gfx.fill(bx - BOX_PADDING, by - BOX_PADDING,
                     bx + totalW + BOX_PADDING, by + totalH + BOX_PADDING, BOX_COLOR);
        }

        // Cover art - use linear sampler for crisp rendering from the 256x256 source texture
        if (hasCover) {
            AbstractTexture coverTex = Minecraft.getInstance().getTextureManager()
                    .getTexture(MediaPoller.getCoverTextureId());
            gfx.blit(coverTex.getTextureView(),
                     RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
                     bx, by, bx + COVER_SIZE, by + COVER_SIZE, 0f, 1f, 0f, 1f);
        }

        // Vertically centre text block inside cover (if cover is taller)
        int textOffY = (hasCover && COVER_SIZE > contentH) ? (COVER_SIZE - contentH) / 2 : 0;
        int curY = by + textOffY;

        // Title + artist
        if (entry.rainbow) {
            int n = renderRainbow(gfx, font, styled, titleText, tx, curY, 0);
            curY += lineH + LINE_GAP;
            renderRainbow(gfx, font, styled, artistText, tx, curY, n);
        } else {
            gfx.text(font, titleComp, tx, curY, entry.color, true);
            curY += lineH + LINE_GAP;
            gfx.text(font, artistComp, tx, curY, dimAlpha(entry.color, 0.70f), true);
        }
        curY += lineH + LINE_GAP;

        // Progress bar
        int filled = Math.round(frac * barW);
        gfx.fill(tx, curY, tx + barW, curY + BAR_HEIGHT, BAR_TRACK);
        if (filled > 0) {
            gfx.fill(tx, curY, tx + filled, curY + BAR_HEIGHT, entry.color2);
        }
        curY += BAR_HEIGHT + LINE_GAP;

        // Time string - dimmed alpha
        gfx.text(font, timeComp, tx, curY, dimAlpha(entry.color, 0.55f), false);

        if (scaled) pose.popMatrix();
    }

    // Helpers

    /** Renders text in rainbow colours; returns the next glyph index so callers can chain lines. */
    private static int renderRainbow(GuiGraphicsExtractor gfx, Font font, Style styled,
                                     String text, int x, int y, int startGlyph) {
        float baseHue = (System.currentTimeMillis() % 3000L) / 3000.0f;
        int cursorX   = x;
        int glyph     = startGlyph;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            int rgb = java.awt.Color.HSBtoRGB(baseHue + glyph * 0.05f, 1.0f, 1.0f);
            MutableComponent charComp = Component.literal(ch).withStyle(styled);
            gfx.text(font, charComp, cursorX, y, 0xFF000000 | (rgb & 0xFFFFFF), true);
            cursorX += font.width(charComp);
            i += Character.charCount(cp);
            glyph++;
        }
        return glyph;
    }

    private static int dimAlpha(int argb, float factor) {
        int a = Math.round((argb >>> 24) * factor);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static String formatTime(long ms) {
        if (ms <= 0) return "0:00";
        long secs = ms / 1000;
        return (secs / 60) + ":" + String.format("%02d", secs % 60);
    }

    private static String truncate(String text, Font font, Style styled, int maxW) {
        if (font.width(Component.literal(text).withStyle(styled)) <= maxW) return text;
        while (!text.isEmpty()) {
            text = text.substring(0, text.length() - 1);
            if (font.width(Component.literal(text + "…").withStyle(styled)) <= maxW) return text + "…";
        }
        return "…";
    }

    private static Identifier parseFont(String s) {
        try { return Identifier.parse(s); }
        catch (Exception e) { return Identifier.parse(HudLayout.DEFAULT_FONT); }
    }

}
