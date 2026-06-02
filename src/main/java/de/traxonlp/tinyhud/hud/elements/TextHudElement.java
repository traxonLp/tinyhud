package de.traxonlp.tinyhud.hud.elements;

import de.traxonlp.tinyhud.hud.HudElement;
import de.traxonlp.tinyhud.hud.HudLayout;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

public abstract class TextHudElement implements HudElement {
    private static final int BOX_PADDING = 2;
    private static final int BOX_COLOR = 0xA0000000;
    /** OS truetype fonts are rasterised at this size; vanilla bitmap font reports 9. */
    private static final int OS_FONT_HEIGHT = 11;

    private final Identifier id;
    private final String translationKey;
    private String lastText = "";
    private int lastWidth = 1;
    private int lastHeight = 9;

    protected TextHudElement(Identifier id, String translationKey) {
        this.id = id;
        this.translationKey = translationKey;
    }

    @Override
    public Identifier id() {
        return id;
    }

    @Override
    public String translationKey() {
        return translationKey;
    }

    protected abstract String text(Minecraft mc);

    protected String text(Minecraft mc, HudLayout.Entry entry) {
        return text(mc);
    }

    @Override
    public int width() {
        return lastWidth;
    }

    @Override
    public int height() {
        return lastHeight;
    }

    @Override
    public void render(GuiGraphicsExtractor gfx, DeltaTracker delta, int x, int y, HudLayout.Entry entry) {
        Minecraft mc = Minecraft.getInstance();
        String text = text(mc, entry);
        lastText = text == null ? "" : text;
        if (lastText.isEmpty()) {
            lastWidth = 1;
            lastHeight = mc.font.lineHeight;
            return;
        }
        Font font = mc.font;
        Identifier fontId = parseFont(entry.font);
        Style styled = Style.EMPTY.withFont(new FontDescription.Resource(fontId));
        MutableComponent component = Component.literal(lastText).withStyle(styled);

        int textW = font.width(component);
        int textH = isCustomFont(entry.font) ? Math.max(font.lineHeight, OS_FONT_HEIGHT) : font.lineHeight;
        lastWidth = Math.max(1, textW);
        lastHeight = textH;

        Matrix3x2fStack pose = gfx.pose();
        boolean scaled = entry.scale != 1.0f;
        if (scaled) {
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(entry.scale, entry.scale);
        }
        int drawX = scaled ? 0 : x;
        int drawY = scaled ? 0 : y;

        if (entry.box) {
            gfx.fill(drawX - BOX_PADDING, drawY - BOX_PADDING,
                    drawX + textW + BOX_PADDING, drawY + textH + BOX_PADDING, BOX_COLOR);
        }

        if (entry.rainbow) {
            renderRainbow(gfx, font, styled, drawX, drawY);
        } else {
            gfx.text(font, component, drawX, drawY, entry.color, true);
        }

        if (scaled) pose.popMatrix();
    }

    private void renderRainbow(GuiGraphicsExtractor gfx, Font font, Style styled, int x, int y) {
        float baseHue = (System.currentTimeMillis() % 3000L) / 3000.0f;
        int cursorX = x;
        int glyphIndex = 0;
        for (int i = 0; i < lastText.length(); ) {
            int cp = lastText.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            float hue = baseHue + glyphIndex * 0.05f;
            int rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
            int colorArgb = 0xFF000000 | (rgb & 0xFFFFFF);
            MutableComponent charComp = Component.literal(ch).withStyle(styled);
            gfx.text(font, charComp, cursorX, y, colorArgb, true);
            cursorX += font.width(charComp);
            i += Character.charCount(cp);
            glyphIndex++;
        }
    }

    private static boolean isCustomFont(String fontKey) {
        return !"minecraft:default".equals(fontKey) && !"minecraft:alt".equals(fontKey);
    }

    private static Identifier parseFont(String s) {
        try {
            return Identifier.parse(s);
        } catch (Exception ex) {
            return Identifier.parse(HudLayout.DEFAULT_FONT);
        }
    }
}
