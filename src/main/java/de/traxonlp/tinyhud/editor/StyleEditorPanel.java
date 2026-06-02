package de.traxonlp.tinyhud.editor;

import de.traxonlp.tinyhud.hud.HudElement;
import de.traxonlp.tinyhud.hud.HudElement.FormatOption;
import de.traxonlp.tinyhud.hud.HudLayout;
import de.traxonlp.tinyhud.hud.elements.ItemTrackerElement;
import de.traxonlp.tinyhud.osfont.OsFont;
import java.util.HashSet;
import java.util.Set;
import de.traxonlp.tinyhud.osfont.OsFonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class StyleEditorPanel {

    // Layout constants

    private static final int WIDTH            = 220;
    private static final int HEIGHT           = 296;   // standard panel height
    private static final int PADDING          = 6;
    private static final int ROW_GAP          = 4;
    private static final int LABEL_W          = 24;
    private static final int SLIDER_H         = 10;
    private static final int VALUE_W          = 40;
    private static final int CHECKBOX_SIZE    = 10;
    private static final int CHECKBOX_ROW_H   = 14;
    private static final int FONT_ROW_H       = 12;
    private static final int FONT_LIST_VISIBLE = 8;
    private static final int FONT_LIST_H      = FONT_ROW_H * FONT_LIST_VISIBLE;   // 96
    private static final int SCROLLBAR_W      = 4;
    private static final int SEARCH_H         = 12;
    private static final int SEARCH_ROW_GAP   = 2;
    private static final int CLEAR_BTN_W      = 11;
    private static final int FORMAT_H         = 14;
    private static final int FORMAT_ROW_GAP   = 2;

    /** Item-picker rows are taller to fit the 16 px item icon. */
    private static final int ITEM_ROW_H        = 18;
    private static final int ITEM_LIST_VISIBLE  = 4;
    private static final int ITEM_LIST_H        = ITEM_ROW_H * ITEM_LIST_VISIBLE;  // 72
    /** Extra vertical space needed when the item picker is shown. */
    private static final int ITEM_SECTION_H     = SEARCH_H + SEARCH_ROW_GAP + ITEM_LIST_H; // 86

    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 3.0f;

    // Records

    private record FontEntry(String key, String label) {}
    private record ItemEntry(String id, ItemStack stack, String displayName) {}

    // Static caches

    private static List<FontEntry> cachedFontList;
    private static List<ItemEntry> cachedItemList;

    // Instance state

    private final HudElement element;
    private final HudLayout.Entry entry;
    private final boolean hasItemPicker;
    private final boolean hasAccentColor;
    private final boolean hasCoverToggle;
    private final boolean hasHideZero;

    // Font picker
    private final List<FontEntry> fontEntries;
    private List<FontEntry> filteredFontEntries;
    private int fontScrollOffset;
    private String fontSearchQuery = "";

    // Item picker (only used when hasItemPicker == true)
    private final List<ItemEntry> itemEntries;
    private List<ItemEntry> filteredItemEntries;
    private int itemScrollOffset;
    private String itemSearchQuery = "";
    /** Whether typed characters go to the item search (true) or font search (false). */
    private boolean itemSearchFocused;

    private int x;
    private int y;

    private enum Slider { SIZE, R, G, B, R2, G2, B2 }
    private Slider draggingSlider;

    // Construction

    public StyleEditorPanel(HudElement element, HudLayout.Entry entry) {
        this.element       = element;
        this.entry         = entry;
        this.hasItemPicker  = element instanceof ItemTrackerElement;
        this.hasAccentColor = element.hasAccentColor();
        this.hasCoverToggle = element.hasCoverToggle();
        this.hasHideZero    = element.hasHideZeroToggle();

        if (cachedFontList == null) cachedFontList = buildFontList();
        fontEntries         = cachedFontList;
        filteredFontEntries = fontEntries;

        if (hasItemPicker) {
            if (cachedItemList == null) cachedItemList = buildItemList();
            itemEntries         = cachedItemList;
            filteredItemEntries = itemEntries;
            itemSearchFocused   = true;   // start focused on item picker
        } else {
            itemEntries         = List.of();
            filteredItemEntries = List.of();
        }
    }

    private static List<FontEntry> buildFontList() {
        List<FontEntry> list = new ArrayList<>();
        list.add(new FontEntry("minecraft:default", "Default"));
        list.add(new FontEntry("minecraft:alt", "Alt"));
        for (OsFont f : OsFonts.all()) {
            list.add(new FontEntry(OsFonts.fontKeyFor(f), f.displayName()));
        }
        return List.copyOf(list);
    }

    private static List<ItemEntry> buildItemList() {
        List<ItemEntry> list = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            var optKey = BuiltInRegistries.ITEM.getResourceKey(item);
            if (optKey.isEmpty()) continue;
            String id     = optKey.get().identifier().toString();
            ItemStack stk = item.getDefaultInstance();
            String name   = stk.getHoverName().getString();
            list.add(new ItemEntry(id, stk, name));
        }
        list.sort(Comparator.comparing(e -> e.displayName().toLowerCase(Locale.ROOT)));
        return List.copyOf(list);
    }

    // Label (9px) + 2px gap + 3 sliders + 2px gap + 10px swatch + 4px gap
    private static final int ACCENT_SECTION_H = 9 + 2 + 3 * (SLIDER_H + ROW_GAP) + 2 + 10 + 4;

    // Height helper

    private int panelHeight() {
        int h = HEIGHT;
        if (hasItemPicker)  h += ITEM_SECTION_H;
        if (hasAccentColor) h += ACCENT_SECTION_H;
        if (hasCoverToggle) h += CHECKBOX_ROW_H;
        if (hasHideZero)    h += CHECKBOX_ROW_H;
        return h;
    }

    // Public API

    public void position(int anchorX, int anchorY, int screenW, int screenH) {
        this.x = Math.max(2, Math.min(screenW - WIDTH - 2, anchorX));
        this.y = Math.max(2, Math.min(screenH - panelHeight() - 2, anchorY));
        clampFontScroll();
        clampItemScroll();
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + WIDTH
                && mouseY >= y && mouseY < y + panelHeight();
    }

    // Rendering

    public void render(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        gfx.fill(x, y, x + WIDTH, y + panelHeight(), 0xE0101010);
        drawOutline(gfx, x, y, WIDTH, panelHeight(), 0xFFAAAAAA);

        Font font = Minecraft.getInstance().font;
        int rowY = y + PADDING;

        // Title
        gfx.text(font, Component.translatable(element.translationKey()),
                 x + PADDING, rowY, 0xFFFFFF55, false);
        gfx.fill(x + PADDING, rowY + font.lineHeight + 2,
                 x + WIDTH - PADDING, rowY + font.lineHeight + 3, 0x40FFFFFF);
        rowY += font.lineHeight + 6;

        // Sliders
        rowY = renderSlider(gfx, font, "Size",
                String.format(Locale.ROOT, "%.2fx", entry.scale),
                (entry.scale - MIN_SCALE) / (MAX_SCALE - MIN_SCALE), rowY, Slider.SIZE);
        int r = (entry.color >> 16) & 0xFF;
        int g = (entry.color >> 8)  & 0xFF;
        int b = entry.color & 0xFF;
        rowY = renderSlider(gfx, font, "R", String.valueOf(r), r / 255f, rowY, Slider.R);
        rowY = renderSlider(gfx, font, "G", String.valueOf(g), g / 255f, rowY, Slider.G);
        rowY = renderSlider(gfx, font, "B", String.valueOf(b), b / 255f, rowY, Slider.B);

        // Colour swatch
        rowY += 2;
        int swatchX = x + PADDING;
        int swatchW = WIDTH - PADDING * 2;
        gfx.fill(swatchX, rowY, swatchX + swatchW, rowY + 10,
                 0xFF000000 | (entry.color & 0x00FFFFFF));
        drawOutline(gfx, swatchX, rowY, swatchW, 10, 0xFFAAAAAA);
        rowY += 14;

        // Accent color (bar color) - shown only for elements that declare hasAccentColor()
        if (hasAccentColor) {
            gfx.text(font, Component.translatable("tinyhud.style.bar_color"),
                     x + PADDING, rowY + 1, 0xFFAAAAAA, false);
            rowY += font.lineHeight + 2;
            int r2 = (entry.color2 >> 16) & 0xFF;
            int g2 = (entry.color2 >> 8)  & 0xFF;
            int b2 = entry.color2 & 0xFF;
            rowY = renderSlider(gfx, font, "R", String.valueOf(r2), r2 / 255f, rowY, Slider.R2);
            rowY = renderSlider(gfx, font, "G", String.valueOf(g2), g2 / 255f, rowY, Slider.G2);
            rowY = renderSlider(gfx, font, "B", String.valueOf(b2), b2 / 255f, rowY, Slider.B2);
            rowY += 2;
            gfx.fill(swatchX, rowY, swatchX + swatchW, rowY + 10,
                     0xFF000000 | (entry.color2 & 0x00FFFFFF));
            drawOutline(gfx, swatchX, rowY, swatchW, 10, 0xFFAAAAAA);
            rowY += 14;
        }

        // Checkboxes
        renderCheckbox(gfx, font, x + PADDING, rowY,
                Component.translatable("tinyhud.style.rainbow"), entry.rainbow, mouseX, mouseY);
        renderCheckbox(gfx, font, x + WIDTH / 2, rowY,
                Component.translatable("tinyhud.style.box"), entry.box, mouseX, mouseY);
        rowY += CHECKBOX_ROW_H;

        if (hasCoverToggle) {
            renderCheckbox(gfx, font, x + PADDING, rowY,
                    Component.translatable("tinyhud.style.show_cover"), entry.showCover, mouseX, mouseY);
            rowY += CHECKBOX_ROW_H;
        }

        if (hasHideZero) {
            renderCheckbox(gfx, font, x + PADDING, rowY,
                    Component.translatable("tinyhud.style.hide_when_zero"), entry.hideWhenZero, mouseX, mouseY);
            rowY += CHECKBOX_ROW_H;
        }

        // Format cycle button
        List<FormatOption> formats = element.formats();
        int formatRowTop = -1;
        if (!formats.isEmpty()) {
            formatRowTop = rowY;
            rowY = renderFormatRow(gfx, font, rowY, mouseX, mouseY, formats);
        }

        // Item picker (ItemTrackerElement only)
        if (hasItemPicker) {
            rowY = renderItemSearchRow(gfx, font, rowY, mouseX, mouseY);
            renderItemList(gfx, rowY, mouseX, mouseY);
            rowY += ITEM_LIST_H;
        }

        // Font picker
        rowY = renderFontSearchRow(gfx, font, rowY, mouseX, mouseY);
        renderFontList(gfx, font, rowY, mouseX, mouseY);

        // Format tooltip - rendered last so it floats on top of the entire panel
        if (formatRowTop >= 0 && isFormatRowHovered(mouseX, mouseY, formatRowTop)) {
            renderFormatTooltip(gfx, font, formatRowTop, formats);
        }
    }

    // Item picker rendering

    private int renderItemSearchRow(GuiGraphicsExtractor gfx, Font font,
                                    int rowY, int mouseX, int mouseY) {
        Component lbl = Component.translatable("tinyhud.style.item");
        gfx.text(font, lbl, x + PADDING, rowY + 2, 0xFFAAAAAA, false);

        int searchX = x + PADDING + font.width(lbl) + 6;
        int searchW = x + WIDTH - PADDING - searchX;
        boolean focused = itemSearchFocused;
        gfx.fill(searchX, rowY, searchX + searchW, rowY + SEARCH_H, 0xFF1A1A1A);
        int borderColor = focused ? 0xFF9FD06F : (itemSearchQuery.isEmpty() ? 0xFF555555 : 0xFF6FA8DC);
        drawOutline(gfx, searchX, rowY, searchW, SEARCH_H, borderColor);

        boolean hasQ = !itemSearchQuery.isEmpty();
        int rightPad = hasQ ? CLEAR_BTN_W : 0;
        int maxTW = searchW - 6 - rightPad;
        String display = hasQ ? itemSearchQuery
                : Component.translatable("tinyhud.style.search").getString();
        String clipped = display;
        while (!clipped.isEmpty() && font.width(clipped) > maxTW) clipped = clipped.substring(1);
        gfx.text(font, Component.literal(clipped), searchX + 3, rowY + 2,
                 hasQ ? 0xFFFFFFFF : 0xFF777777, false);

        if (hasQ) {
            int btnX = searchX + searchW - CLEAR_BTN_W;
            boolean btnHover = mouseX >= btnX && mouseX < btnX + CLEAR_BTN_W
                    && mouseY >= rowY && mouseY < rowY + SEARCH_H;
            if (btnHover) gfx.fill(btnX, rowY + 1, btnX + CLEAR_BTN_W - 1, rowY + SEARCH_H - 1, 0xFF404040);
            gfx.text(font, Component.literal("x"), btnX + 3, rowY + 2,
                     btnHover ? 0xFFFFFFFF : 0xFFAAAAAA, false);
        }
        return rowY + SEARCH_H + SEARCH_ROW_GAP;
    }

    private void renderItemList(GuiGraphicsExtractor gfx, int listTop,
                                int mouseX, int mouseY) {
        int listX    = x + PADDING;
        int listW    = WIDTH - PADDING * 2;
        int contentW = listW - SCROLLBAR_W - 2;
        gfx.fill(listX, listTop, listX + listW, listTop + ITEM_LIST_H, 0xFF1A1A1A);
        drawOutline(gfx, listX, listTop, listW, ITEM_LIST_H, 0xFF555555);

        Font font = Minecraft.getInstance().font;

        if (filteredItemEntries.isEmpty()) {
            Component empty = Component.translatable("tinyhud.style.no_matches");
            int tw = font.width(empty);
            gfx.text(font, empty,
                     listX + (listW - tw) / 2,
                     listTop + (ITEM_LIST_H - font.lineHeight) / 2,
                     0xFF888888, false);
            return;
        }

        // Build set of currently tracked IDs for O(1) lookup during row render
        Set<String> tracked = new HashSet<>(ItemTrackerElement.parseItemIds(entry.format));

        // ✓ glyph width - used to indent icon + text consistently
        int checkW = font.width("✓ ");

        int max = Math.min(ITEM_LIST_VISIBLE, filteredItemEntries.size() - itemScrollOffset);
        for (int i = 0; i < max; i++) {
            ItemEntry ie   = filteredItemEntries.get(itemScrollOffset + i);
            int rowY       = listTop + i * ITEM_ROW_H;
            boolean active = tracked.contains(ie.id());
            boolean hovered = mouseX >= listX && mouseX < listX + contentW
                    && mouseY >= rowY && mouseY < rowY + ITEM_ROW_H;

            // Row background: green tint for tracked, light for hovered
            int bg = active ? 0xFF1E3A1E : (hovered ? 0x40FFFFFF : 0);
            if (bg != 0) gfx.fill(listX + 1, rowY, listX + contentW - 1, rowY + ITEM_ROW_H, bg);

            // Checkmark column
            if (active) {
                int checkY = rowY + (ITEM_ROW_H - font.lineHeight) / 2;
                gfx.text(font, Component.literal("✓"), listX + 2, checkY, 0xFF55FF55, false);
            }

            // Item icon (offset past checkmark column)
            gfx.item(ie.stack(), listX + 2 + checkW, rowY + 1);

            // Display name (truncated to available space)
            int nameX    = listX + 2 + checkW + 16 + 3;
            int maxNameW = contentW - (nameX - listX) - 2;
            String label = ie.displayName();
            if (font.width(label) > maxNameW) {
                while (label.length() > 1 && font.width(label + "…") > maxNameW)
                    label = label.substring(0, label.length() - 1);
                label = label + "…";
            }
            int nameColor = active ? 0xFFCCFFCC : 0xFFFFFFFF;
            int textY = rowY + (ITEM_ROW_H - font.lineHeight) / 2;
            gfx.text(font, Component.literal(label), nameX, textY, nameColor, false);
        }

        // Scrollbar
        if (filteredItemEntries.size() > ITEM_LIST_VISIBLE) {
            int trackX = listX + listW - SCROLLBAR_W - 1;
            int trackY = listTop + 1;
            int trackH = ITEM_LIST_H - 2;
            gfx.fill(trackX, trackY, trackX + SCROLLBAR_W, trackY + trackH, 0xFF2A2A2A);
            int handleH = Math.max(8, trackH * ITEM_LIST_VISIBLE / filteredItemEntries.size());
            int range   = trackH - handleH;
            int handleY = trackY + (range * itemScrollOffset
                    / Math.max(1, filteredItemEntries.size() - ITEM_LIST_VISIBLE));
            gfx.fill(trackX, handleY, trackX + SCROLLBAR_W, handleY + handleH, 0xFF888888);
        }
    }

    // Font picker rendering

    private int renderFontSearchRow(GuiGraphicsExtractor gfx, Font font,
                                    int rowY, int mouseX, int mouseY) {
        Component fontLabel = Component.translatable("tinyhud.style.font");
        gfx.text(font, fontLabel, x + PADDING, rowY + 2, 0xFFAAAAAA, false);

        int searchX = searchBoxX(font);
        int searchW = searchBoxW(font);
        boolean focused = !hasItemPicker || !itemSearchFocused;
        gfx.fill(searchX, rowY, searchX + searchW, rowY + SEARCH_H, 0xFF1A1A1A);
        int borderColor = focused ? 0xFF9FD06F : (fontSearchQuery.isEmpty() ? 0xFF555555 : 0xFF6FA8DC);
        drawOutline(gfx, searchX, rowY, searchW, SEARCH_H, borderColor);

        boolean hasQ = !fontSearchQuery.isEmpty();
        int rightPad = hasQ ? CLEAR_BTN_W : 0;
        int maxTW = searchW - 6 - rightPad;
        String display = hasQ ? fontSearchQuery
                : Component.translatable("tinyhud.style.search").getString();
        String clipped = display;
        while (!clipped.isEmpty() && font.width(clipped) > maxTW) clipped = clipped.substring(1);
        gfx.text(font, Component.literal(clipped), searchX + 3, rowY + 2,
                 hasQ ? 0xFFFFFFFF : 0xFF777777, false);

        if (hasQ) {
            int btnX = searchX + searchW - CLEAR_BTN_W;
            boolean btnHover = mouseX >= btnX && mouseX < btnX + CLEAR_BTN_W
                    && mouseY >= rowY && mouseY < rowY + SEARCH_H;
            if (btnHover) gfx.fill(btnX, rowY + 1, btnX + CLEAR_BTN_W - 1, rowY + SEARCH_H - 1, 0xFF404040);
            gfx.text(font, Component.literal("x"), btnX + 3, rowY + 2,
                     btnHover ? 0xFFFFFFFF : 0xFFAAAAAA, false);
        }
        return rowY + SEARCH_H + SEARCH_ROW_GAP;
    }

    private void renderFontList(GuiGraphicsExtractor gfx, Font font,
                                int listTop, int mouseX, int mouseY) {
        int listX    = x + PADDING;
        int listW    = WIDTH - PADDING * 2;
        int contentW = listW - SCROLLBAR_W - 2;
        gfx.fill(listX, listTop, listX + listW, listTop + FONT_LIST_H, 0xFF1A1A1A);
        drawOutline(gfx, listX, listTop, listW, FONT_LIST_H, 0xFF555555);

        if (filteredFontEntries.isEmpty()) {
            Component empty = Component.translatable("tinyhud.style.no_fonts");
            int tw = font.width(empty);
            gfx.text(font, empty,
                     listX + (listW - tw) / 2,
                     listTop + (FONT_LIST_H - font.lineHeight) / 2,
                     0xFF888888, false);
            return;
        }

        int max = Math.min(FONT_LIST_VISIBLE, filteredFontEntries.size() - fontScrollOffset);
        for (int i = 0; i < max; i++) {
            FontEntry fe = filteredFontEntries.get(fontScrollOffset + i);
            int rowY     = listTop + i * FONT_ROW_H;
            boolean selected = entry.font.equals(fe.key());
            boolean hovered  = mouseX >= listX && mouseX < listX + contentW
                    && mouseY >= rowY && mouseY < rowY + FONT_ROW_H;
            int bg = selected ? 0xFF6FA8DC : (hovered ? 0x40FFFFFF : 0);
            if (bg != 0) gfx.fill(listX + 1, rowY, listX + contentW - 1, rowY + FONT_ROW_H, bg);
            String label = fe.label();
            if (font.width(label) > contentW - 8) {
                while (label.length() > 1 && font.width(label + "…") > contentW - 8)
                    label = label.substring(0, label.length() - 1);
                label = label + "…";
            }
            gfx.text(font, Component.literal(label), listX + 4, rowY + 2, 0xFFFFFFFF, false);
        }

        if (filteredFontEntries.size() > FONT_LIST_VISIBLE) {
            int trackX = listX + listW - SCROLLBAR_W - 1;
            int trackY = listTop + 1;
            int trackH = FONT_LIST_H - 2;
            gfx.fill(trackX, trackY, trackX + SCROLLBAR_W, trackY + trackH, 0xFF2A2A2A);
            int handleH = Math.max(8, trackH * FONT_LIST_VISIBLE / filteredFontEntries.size());
            int range   = trackH - handleH;
            int handleY = trackY + (range * fontScrollOffset
                    / Math.max(1, filteredFontEntries.size() - FONT_LIST_VISIBLE));
            gfx.fill(trackX, handleY, trackX + SCROLLBAR_W, handleY + handleH, 0xFF888888);
        }
    }

    // Shared sub-renders

    private int renderFormatRow(GuiGraphicsExtractor gfx, Font font, int rowY,
                                int mouseX, int mouseY, List<FormatOption> formats) {
        int rowX = x + PADDING;
        int rowW = WIDTH - PADDING * 2;
        boolean hovered = mouseX >= rowX && mouseX < rowX + rowW
                && mouseY >= rowY && mouseY < rowY + FORMAT_H;
        gfx.fill(rowX, rowY, rowX + rowW, rowY + FORMAT_H,
                 hovered ? 0xFF2A2A2A : 0xFF1A1A1A);
        drawOutline(gfx, rowX, rowY, rowW, FORMAT_H, 0xFF555555);

        FormatOption current = currentFormat(formats);
        Component label = Component.translatable("tinyhud.style.format");
        gfx.text(font, label, rowX + 4, rowY + 3, 0xFFAAAAAA, false);
        int labelW = font.width(label);

        Component value   = Component.translatable(current.translationKey());
        int valueW  = font.width(value);
        int arrowW  = font.width("▶");
        int valueX  = rowX + rowW - 4 - arrowW - 4 - valueW;
        if (valueX < rowX + 4 + labelW + 4) valueX = rowX + 4 + labelW + 4;
        gfx.text(font, value, valueX, rowY + 3, 0xFFFFFFFF, false);
        gfx.text(font, Component.literal("▶"), rowX + rowW - 4 - arrowW, rowY + 3,
                 hovered ? 0xFFFFFFFF : 0xFFAAAAAA, false);

        return rowY + FORMAT_H + FORMAT_ROW_GAP;
    }

    private boolean isFormatRowHovered(int mouseX, int mouseY, int rowY) {
        int rowX = x + PADDING;
        int rowW = WIDTH - PADDING * 2;
        return mouseX >= rowX && mouseX < rowX + rowW
                && mouseY >= rowY && mouseY < rowY + FORMAT_H;
    }

    /**
     * Floating tooltip that appears above (or below, if near top) the Format row.
     * Lists every format option in a single row; the current format is highlighted
     * in yellow with a subtle background box.
     */
    private void renderFormatTooltip(GuiGraphicsExtractor gfx, Font font,
                                     int formatRowY, List<FormatOption> formats) {
        FormatOption current = currentFormat(formats);
        final int TIP_PAD = 5;
        final int TIP_H   = font.lineHeight + TIP_PAD * 2;

        // Measure total width
        int totalW = TIP_PAD * 2;
        for (int i = 0; i < formats.size(); i++) {
            totalW += font.width(Component.translatable(formats.get(i).translationKey()));
            if (i < formats.size() - 1) totalW += font.width(" · ");
        }
        // Clamp to at least the format-row width but never wider than the panel
        int rowW = WIDTH - PADDING * 2;
        totalW = Math.max(rowW, totalW);
        totalW = Math.min(WIDTH - PADDING * 2, totalW);

        int tipX = x + PADDING;
        int tipY = formatRowY - TIP_H - 3;
        if (tipY < 2) tipY = formatRowY + FORMAT_H + 3;   // flip below if no room above

        gfx.fill(tipX, tipY, tipX + totalW, tipY + TIP_H, 0xF0202020);
        drawOutline(gfx, tipX, tipY, totalW, TIP_H, 0xFF888888);

        int curX = tipX + TIP_PAD;
        for (int i = 0; i < formats.size(); i++) {
            FormatOption fo = formats.get(i);
            Component label = Component.translatable(fo.translationKey());
            boolean isCurrent = fo.key().equals(current.key());
            int lw = font.width(label);

            if (isCurrent) {
                // Subtle highlight box behind the active option
                gfx.fill(curX - 2, tipY + 2, curX + lw + 2, tipY + TIP_H - 2, 0x50FFFFFF);
            }
            gfx.text(font, label, curX, tipY + TIP_PAD,
                     isCurrent ? 0xFFFFFF55 : 0xFFAAAAAA, false);
            curX += lw;

            if (i < formats.size() - 1) {
                Component sep = Component.literal(" · ");
                gfx.text(font, sep, curX, tipY + TIP_PAD, 0xFF444444, false);
                curX += font.width(sep);
            }
        }
    }

    private void renderCheckbox(GuiGraphicsExtractor gfx, Font font, int cx, int cy,
                                Component label, boolean checked, int mouseX, int mouseY) {
        boolean hovered = mouseX >= cx && mouseX < cx + CHECKBOX_SIZE + 4 + font.width(label)
                && mouseY >= cy && mouseY < cy + CHECKBOX_SIZE;
        int boxBg = checked ? 0xFF6FA8DC : (hovered ? 0xFF404040 : 0xFF1A1A1A);
        gfx.fill(cx, cy, cx + CHECKBOX_SIZE, cy + CHECKBOX_SIZE, boxBg);
        drawOutline(gfx, cx, cy, CHECKBOX_SIZE, CHECKBOX_SIZE, 0xFF888888);
        if (checked) gfx.fill(cx + 2, cy + 2, cx + CHECKBOX_SIZE - 2, cy + CHECKBOX_SIZE - 2, 0xFFFFFFFF);
        gfx.text(font, label, cx + CHECKBOX_SIZE + 4, cy + 1, 0xFFFFFFFF, false);
    }

    private int renderSlider(GuiGraphicsExtractor gfx, Font font, String label,
                             String valueText, float frac, int rowY, Slider id) {
        gfx.text(font, Component.literal(label), x + PADDING, rowY + 1, 0xFFAAAAAA, false);
        int trackX = x + PADDING + LABEL_W;
        int trackW = WIDTH - PADDING * 2 - LABEL_W - VALUE_W - 4;
        int trackY = rowY + 1;
        gfx.fill(trackX, trackY, trackX + trackW, trackY + SLIDER_H, 0xFF202020);
        drawOutline(gfx, trackX, trackY, trackW, SLIDER_H, 0xFF555555);
        int filled = Math.round(Math.max(0f, Math.min(1f, frac)) * (trackW - 2));
        gfx.fill(trackX + 1, trackY + 1, trackX + 1 + filled, trackY + SLIDER_H - 1, 0xFF6FA8DC);
        int knobX = trackX + 1 + filled - 2;
        gfx.fill(knobX, trackY - 1, knobX + 4, trackY + SLIDER_H + 1, 0xFFFFFFFF);
        gfx.text(font, Component.literal(valueText),
                 x + WIDTH - PADDING - VALUE_W + 2, rowY + 1, 0xFFFFFFFF, false);
        return rowY + SLIDER_H + ROW_GAP;
    }

    // Input: mouse click

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) return false;
        if (button != 0) return true;

        Font font = Minecraft.getInstance().font;
        int rowY = y + PADDING + font.lineHeight + 6;

        int trackX = x + PADDING + LABEL_W;
        int trackW = WIDTH - PADDING * 2 - LABEL_W - VALUE_W - 4;

        // Main sliders: SIZE, R, G, B
        for (Slider s : new Slider[]{Slider.SIZE, Slider.R, Slider.G, Slider.B}) {
            if (mouseX >= trackX && mouseX < trackX + trackW
                    && mouseY >= rowY && mouseY < rowY + SLIDER_H) {
                draggingSlider = s;
                applySlider(s, mouseX, trackX, trackW);
                return true;
            }
            rowY += SLIDER_H + ROW_GAP;
        }
        rowY += 2 + 14; // main swatch

        // Accent sliders: R2, G2, B2
        if (hasAccentColor) {
            rowY += font.lineHeight + 2; // section label
            for (Slider s : new Slider[]{Slider.R2, Slider.G2, Slider.B2}) {
                if (mouseX >= trackX && mouseX < trackX + trackW
                        && mouseY >= rowY && mouseY < rowY + SLIDER_H) {
                    draggingSlider = s;
                    applySlider(s, mouseX, trackX, trackW);
                    return true;
                }
                rowY += SLIDER_H + ROW_GAP;
            }
            rowY += 2 + 14; // accent swatch
        }

        // Checkboxes
        if (mouseY >= rowY && mouseY < rowY + CHECKBOX_SIZE) {
            int rainbowX = x + PADDING;
            int boxX     = x + WIDTH / 2;
            if (mouseX >= rainbowX && mouseX < rainbowX + CHECKBOX_SIZE + 4
                    + font.width(Component.translatable("tinyhud.style.rainbow"))) {
                entry.rainbow = !entry.rainbow;
                return true;
            }
            if (mouseX >= boxX && mouseX < boxX + CHECKBOX_SIZE + 4
                    + font.width(Component.translatable("tinyhud.style.box"))) {
                entry.box = !entry.box;
                return true;
            }
        }
        rowY += CHECKBOX_ROW_H;

        if (hasCoverToggle) {
            if (mouseY >= rowY && mouseY < rowY + CHECKBOX_SIZE) {
                int coverX = x + PADDING;
                if (mouseX >= coverX && mouseX < coverX + CHECKBOX_SIZE + 4
                        + font.width(Component.translatable("tinyhud.style.show_cover"))) {
                    entry.showCover = !entry.showCover;
                    return true;
                }
            }
            rowY += CHECKBOX_ROW_H;
        }

        if (hasHideZero) {
            if (mouseY >= rowY && mouseY < rowY + CHECKBOX_SIZE) {
                int hzX = x + PADDING;
                if (mouseX >= hzX && mouseX < hzX + CHECKBOX_SIZE + 4
                        + font.width(Component.translatable("tinyhud.style.hide_when_zero"))) {
                    entry.hideWhenZero = !entry.hideWhenZero;
                    return true;
                }
            }
            rowY += CHECKBOX_ROW_H;
        }

        // Format cycle
        List<FormatOption> formats = element.formats();
        if (!formats.isEmpty()) {
            int rowX = x + PADDING;
            int rowW = WIDTH - PADDING * 2;
            if (mouseX >= rowX && mouseX < rowX + rowW
                    && mouseY >= rowY && mouseY < rowY + FORMAT_H) {
                cycleFormat(formats);
                return true;
            }
            rowY += FORMAT_H + FORMAT_ROW_GAP;
        }

        // Item picker
        if (hasItemPicker) {
            int searchX = itemSearchBoxX(font);
            int searchW = itemSearchBoxW(font);
            if (mouseX >= searchX && mouseX < searchX + searchW
                    && mouseY >= rowY && mouseY < rowY + SEARCH_H) {
                itemSearchFocused = true;
                if (!itemSearchQuery.isEmpty()) {
                    int btnX = searchX + searchW - CLEAR_BTN_W;
                    if (mouseX >= btnX && mouseX < btnX + CLEAR_BTN_W) {
                        itemSearchQuery = "";
                        onItemSearchChanged();
                    }
                }
                return true;
            }
            rowY += SEARCH_H + SEARCH_ROW_GAP;

            int listX    = x + PADDING;
            int listW    = WIDTH - PADDING * 2;
            int contentW = listW - SCROLLBAR_W - 2;
            if (mouseX >= listX && mouseX < listX + contentW
                    && mouseY >= rowY && mouseY < rowY + ITEM_LIST_H) {
                int index = itemScrollOffset + (int) ((mouseY - rowY) / ITEM_ROW_H);
                if (index >= 0 && index < filteredItemEntries.size()) {
                    String clickedId = filteredItemEntries.get(index).id();
                    // Toggle: add if not present, remove if already tracked
                    List<String> current =
                            new ArrayList<>(ItemTrackerElement.parseItemIds(entry.format));
                    if (current.contains(clickedId)) {
                        current.remove(clickedId);
                    } else {
                        current.add(clickedId);
                    }
                    entry.format = String.join(",", current);
                }
                return true;
            }
            rowY += ITEM_LIST_H;
        }

        // Font search row
        int searchX = searchBoxX(font);
        int searchW = searchBoxW(font);
        if (mouseX >= searchX && mouseX < searchX + searchW
                && mouseY >= rowY && mouseY < rowY + SEARCH_H) {
            itemSearchFocused = false;
            if (!fontSearchQuery.isEmpty()) {
                int btnX = searchX + searchW - CLEAR_BTN_W;
                if (mouseX >= btnX && mouseX < btnX + CLEAR_BTN_W) {
                    fontSearchQuery = "";
                    onFontSearchChanged();
                }
            }
            return true;
        }
        rowY += SEARCH_H + SEARCH_ROW_GAP;

        // Font list
        int listX    = x + PADDING;
        int listW    = WIDTH - PADDING * 2;
        int contentW = listW - SCROLLBAR_W - 2;
        if (mouseX >= listX && mouseX < listX + contentW
                && mouseY >= rowY && mouseY < rowY + FONT_LIST_H) {
            int index = fontScrollOffset + (int) ((mouseY - rowY) / FONT_ROW_H);
            if (index >= 0 && index < filteredFontEntries.size()) {
                entry.font = filteredFontEntries.get(index).key();
            }
            return true;
        }
        return true;
    }

    // Input: drag / release / scroll

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (draggingSlider == null) return false;
        int trackX = x + PADDING + LABEL_W;
        int trackW = WIDTH - PADDING * 2 - LABEL_W - VALUE_W - 4;
        applySlider(draggingSlider, mouseX, trackX, trackW);
        return true;
    }

    public boolean mouseReleased() {
        if (draggingSlider != null) { draggingSlider = null; return true; }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!contains(mouseX, mouseY)) return false;
        // Determine which list the cursor is over by checking against the item list top
        if (hasItemPicker) {
            int itemListTop = itemListTop();
            if (mouseY >= itemListTop && mouseY < itemListTop + ITEM_LIST_H) {
                itemScrollOffset -= (int) Math.signum(scrollY) * 2;
                clampItemScroll();
                return true;
            }
        }
        fontScrollOffset -= (int) Math.signum(scrollY) * 2;
        clampFontScroll();
        return true;
    }

    // Input: keyboard

    public boolean charTyped(CharacterEvent event) {
        int cp = event.codepoint();
        if (cp < 32 || cp == 127) return false;
        String ch = new String(Character.toChars(cp));
        if (hasItemPicker && itemSearchFocused) {
            itemSearchQuery += ch;
            onItemSearchChanged();
        } else {
            fontSearchQuery += ch;
            onFontSearchChanged();
        }
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
            if (hasItemPicker && itemSearchFocused) {
                if (itemSearchQuery.isEmpty()) return false;
                int last = itemSearchQuery.offsetByCodePoints(itemSearchQuery.length(), -1);
                itemSearchQuery = itemSearchQuery.substring(0, last);
                onItemSearchChanged();
                return true;
            } else {
                if (fontSearchQuery.isEmpty()) return false;
                int last = fontSearchQuery.offsetByCodePoints(fontSearchQuery.length(), -1);
                fontSearchQuery = fontSearchQuery.substring(0, last);
                onFontSearchChanged();
                return true;
            }
        }
        return false;
    }

    // Search logic

    private void onItemSearchChanged() {
        if (itemSearchQuery.isEmpty()) {
            filteredItemEntries = itemEntries;
        } else {
            String q = itemSearchQuery.toLowerCase(Locale.ROOT);
            List<ItemEntry> out = new ArrayList<>(itemEntries.size());
            for (ItemEntry ie : itemEntries) {
                if (ie.displayName().toLowerCase(Locale.ROOT).contains(q)
                        || ie.id().toLowerCase(Locale.ROOT).contains(q)) {
                    out.add(ie);
                }
            }
            filteredItemEntries = out;
        }
        itemScrollOffset = 0;
        clampItemScroll();
    }

    private void onFontSearchChanged() {
        if (fontSearchQuery.isEmpty()) {
            filteredFontEntries = fontEntries;
        } else {
            String q = fontSearchQuery.toLowerCase(Locale.ROOT);
            List<FontEntry> out = new ArrayList<>(fontEntries.size());
            for (FontEntry fe : fontEntries) {
                if (fe.label().toLowerCase(Locale.ROOT).contains(q)) out.add(fe);
            }
            filteredFontEntries = out;
        }
        fontScrollOffset = 0;
        clampFontScroll();
    }

    // Scroll clamping

    private void clampFontScroll() {
        int max = Math.max(0, filteredFontEntries.size() - FONT_LIST_VISIBLE);
        fontScrollOffset = Math.max(0, Math.min(fontScrollOffset, max));
    }

    private void clampItemScroll() {
        if (!hasItemPicker) return;
        int max = Math.max(0, filteredItemEntries.size() - ITEM_LIST_VISIBLE);
        itemScrollOffset = Math.max(0, Math.min(itemScrollOffset, max));
    }

    // Slider logic

    private void applySlider(Slider s, double mouseX, int trackX, int trackW) {
        float frac = (float) Math.max(0.0, Math.min(1.0,
                (mouseX - trackX - 1) / (double) (trackW - 2)));
        switch (s) {
            case SIZE -> {
                float raw = MIN_SCALE + frac * (MAX_SCALE - MIN_SCALE);
                entry.scale = Math.round(raw * 20f) / 20f;
            }
            case R  -> entry.color  = (entry.color  & 0xFF00FFFF) | (Math.round(frac * 255f) << 16) | 0xFF000000;
            case G  -> entry.color  = (entry.color  & 0xFFFF00FF) | (Math.round(frac * 255f) << 8)  | 0xFF000000;
            case B  -> entry.color  = (entry.color  & 0xFFFFFF00) | Math.round(frac * 255f)          | 0xFF000000;
            case R2 -> entry.color2 = (entry.color2 & 0xFF00FFFF) | (Math.round(frac * 255f) << 16) | 0xFF000000;
            case G2 -> entry.color2 = (entry.color2 & 0xFFFF00FF) | (Math.round(frac * 255f) << 8)  | 0xFF000000;
            case B2 -> entry.color2 = (entry.color2 & 0xFFFFFF00) | Math.round(frac * 255f)          | 0xFF000000;
        }
    }

    // Format cycle

    private FormatOption currentFormat(List<FormatOption> formats) {
        for (FormatOption fo : formats) {
            if (fo.key().equals(entry.format)) return fo;
        }
        return formats.get(0);
    }

    private void cycleFormat(List<FormatOption> formats) {
        int idx = 0;
        for (int i = 0; i < formats.size(); i++) {
            if (formats.get(i).key().equals(entry.format)) { idx = i; break; }
        }
        entry.format = formats.get((idx + 1) % formats.size()).key();
    }

    // Layout geometry helpers

    /** Pixel Y where the item list starts (used by scroll hit-testing). */
    private int itemListTop() {
        Font font = Minecraft.getInstance().font;
        int rowY = y + PADDING + font.lineHeight + 6;
        rowY += 4 * (SLIDER_H + ROW_GAP); // 4 sliders
        rowY += 2 + 14;                    // swatch
        if (hasAccentColor) rowY += ACCENT_SECTION_H;
        rowY += CHECKBOX_ROW_H;            // rainbow / box row
        if (hasCoverToggle) rowY += CHECKBOX_ROW_H;
        if (hasHideZero)    rowY += CHECKBOX_ROW_H;
        if (!element.formats().isEmpty()) rowY += FORMAT_H + FORMAT_ROW_GAP;
        rowY += SEARCH_H + SEARCH_ROW_GAP; // item search row
        return rowY;
    }

    private int searchBoxX(Font font) {
        return x + PADDING + font.width(Component.translatable("tinyhud.style.font")) + 6;
    }

    private int searchBoxW(Font font) {
        return x + WIDTH - PADDING - searchBoxX(font);
    }

    private int itemSearchBoxX(Font font) {
        return x + PADDING + font.width(Component.translatable("tinyhud.style.item")) + 6;
    }

    private int itemSearchBoxW(Font font) {
        return x + WIDTH - PADDING - itemSearchBoxX(font);
    }

    // Drawing util

    private static void drawOutline(GuiGraphicsExtractor gfx, int x, int y,
                                    int w, int h, int color) {
        gfx.fill(x,         y,         x + w,     y + 1,     color);
        gfx.fill(x,         y + h - 1, x + w,     y + h,     color);
        gfx.fill(x,         y,         x + 1,     y + h,     color);
        gfx.fill(x + w - 1, y,         x + w,     y + h,     color);
    }
}
