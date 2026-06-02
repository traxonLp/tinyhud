package de.traxonlp.tinyhud.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ContextMenu {
    private static final int ROW_HEIGHT = 12;
    private static final int HEADER_HEIGHT = 11;
    private static final int PADDING_X = 8;
    private static final int PADDING_Y = 4;
    private static final int MIN_WIDTH = 100;

    private final List<Row> rows = new ArrayList<>();
    private int x;
    private int y;
    private int width;
    private int height;

    public static abstract class Row {
        final Component label;

        Row(Component label) {
            this.label = label;
        }

        int height() {
            return ROW_HEIGHT;
        }

        abstract boolean clickable();
    }

    public static class Item extends Row {
        final Runnable action;

        public Item(Component label, Runnable action) {
            super(label);
            this.action = action;
        }

        @Override
        boolean clickable() {
            return true;
        }
    }

    public static class Header extends Row {
        public Header(Component label) {
            super(label);
        }

        @Override
        int height() {
            return HEADER_HEIGHT;
        }

        @Override
        boolean clickable() {
            return false;
        }
    }

    public ContextMenu addHeader(Component label) {
        rows.add(new Header(label));
        return this;
    }

    public ContextMenu addItem(Component label, Runnable action) {
        rows.add(new Item(label, action));
        return this;
    }

    public boolean isEmpty() {
        for (Row r : rows) if (r.clickable()) return false;
        return true;
    }

    public void position(int desiredX, int desiredY, int screenW, int screenH) {
        Font font = Minecraft.getInstance().font;
        int maxLabel = MIN_WIDTH - PADDING_X * 2;
        for (Row r : rows) {
            maxLabel = Math.max(maxLabel, font.width(r.label));
        }
        this.width = maxLabel + PADDING_X * 2;
        int h = PADDING_Y * 2;
        for (Row r : rows) h += r.height();
        this.height = h;
        this.x = Math.max(2, Math.min(screenW - width - 2, desiredX));
        this.y = Math.max(2, Math.min(screenH - height - 2, desiredY));
    }

    public void render(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        gfx.fill(x, y, x + width, y + height, 0xE0101010);
        drawOutline(gfx, x, y, width, height, 0xFFAAAAAA);

        Font font = Minecraft.getInstance().font;
        int rowY = y + PADDING_Y;
        for (Row r : rows) {
            int rh = r.height();
            if (r instanceof Item && hovered(mouseX, mouseY, rowY, rh)) {
                gfx.fill(x + 1, rowY, x + width - 1, rowY + rh, 0x60FFFFFF);
            }
            int color = r instanceof Header ? 0xFFAAAAAA : 0xFFFFFFFF;
            int textY = rowY + (rh - font.lineHeight) / 2;
            gfx.text(font, r.label, x + PADDING_X, textY, color, false);
            if (r instanceof Header && rowY > y + PADDING_Y) {
                gfx.fill(x + 4, rowY, x + width - 4, rowY + 1, 0x40FFFFFF);
            }
            rowY += rh;
        }
    }

    private boolean hovered(int mouseX, int mouseY, int rowY, int rh) {
        return mouseX >= x + 1 && mouseX < x + width - 1 && mouseY >= rowY && mouseY < rowY + rh;
    }

    /**
     * Returns true if the click was inside this menu (caller should consume it).
     * If a clickable item was hit, fires its action.
     */
    public boolean click(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)) return false;
        int rowY = y + PADDING_Y;
        for (Row r : rows) {
            int rh = r.height();
            if (r instanceof Item item && mouseX >= x + 1 && mouseX < x + width - 1
                    && mouseY >= rowY && mouseY < rowY + rh) {
                item.action.run();
                return true;
            }
            rowY += rh;
        }
        return true;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static void drawOutline(GuiGraphicsExtractor gfx, int x, int y, int w, int h, int color) {
        gfx.fill(x, y, x + w, y + 1, color);
        gfx.fill(x, y + h - 1, x + w, y + h, color);
        gfx.fill(x, y, x + 1, y + h, color);
        gfx.fill(x + w - 1, y, x + w, y + h, color);
    }

    static Component label(String key) {
        return Component.translatable(key);
    }
}
