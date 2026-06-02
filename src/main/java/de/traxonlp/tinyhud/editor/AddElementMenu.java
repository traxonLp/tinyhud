package de.traxonlp.tinyhud.editor;

import de.traxonlp.tinyhud.hud.HudCategory;
import de.traxonlp.tinyhud.hud.HudElement;
import de.traxonlp.tinyhud.hud.HudElements;
import de.traxonlp.tinyhud.hud.HudLayout;
import de.traxonlp.tinyhud.hud.HudLayoutStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Right-click "add element" overlay.
 * <p>
 * Shows all three categories always.  Click a category header to expand/collapse it;
 * expanded categories list their hidden (addable) elements.  Typing filters elements
 * by name across all categories (matching categories auto-expand).  Clicking an element
 * calls {@link #onShowElement} and signals the owner to close this menu.
 */
public class AddElementMenu {

    // Layout constants

    private static final int WIDTH      = 192;
    private static final int PADDING_X  =   8;
    private static final int PADDING_Y  =   5;
    private static final int SEARCH_H   =  12;
    private static final int SEARCH_GAP =   3;
    private static final int CAT_ROW_H  =  14;
    private static final int ELEM_ROW_H =  12;
    private static final int INDENT     =  12;
    private static final int CLEAR_W    =  11;
    private static final int CFG_HDR_H  =  14;   // "Config" section header
    private static final int CFG_ROW_H  =  12;   // export / import rows
    /** Header + export row + import row. */
    private static final int CFG_SECTION_H = CFG_HDR_H + CFG_ROW_H * 2;

    // State

    private final Map<Identifier, HudLayout.Entry> snapshot;
    private final Consumer<HudElement>              onShowElement;

    /** Anchor coordinates passed at construction (used for re-clamping on resize). */
    private final int anchorX, anchorY, screenW, screenH;

    private final EnumSet<HudCategory> expanded = EnumSet.noneOf(HudCategory.class);
    private String searchQuery = "";

    /** Top-left corner, updated whenever height changes. */
    private int x, y;

    // Construction

    public AddElementMenu(int anchorX, int anchorY, int screenW, int screenH,
                          Map<Identifier, HudLayout.Entry> snapshot,
                          Consumer<HudElement> onShowElement) {
        this.snapshot      = snapshot;
        this.onShowElement = onShowElement;
        this.anchorX       = anchorX;
        this.anchorY       = anchorY;
        this.screenW       = screenW;
        this.screenH       = screenH;
        reposition();
    }

    // Geometry

    private int totalHeight() {
        int h = PADDING_Y + SEARCH_H + SEARCH_GAP + CFG_SECTION_H;
        for (HudCategory cat : HudCategory.values()) {
            h += CAT_ROW_H;
            if (categoryExpanded(cat)) {
                List<HudElement> elems = hiddenElements(cat);
                h += Math.max(1, elems.size()) * ELEM_ROW_H;   // 1 row minimum (placeholder)
            }
        }
        return h + PADDING_Y;
    }

    private void reposition() {
        int h = totalHeight();
        this.x = Math.max(2, Math.min(screenW - WIDTH - 2, anchorX));
        this.y = Math.max(2, Math.min(screenH - h      - 2, anchorY));
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + WIDTH
                && mouseY >= y && mouseY < y + totalHeight();
    }

    // Data helpers

    /** Whether this category should be shown expanded in the current state. */
    private boolean categoryExpanded(HudCategory cat) {
        if (!searchQuery.isEmpty()) {
            // Auto-expand categories that have hidden elements matching the filter.
            return !hiddenElements(cat).isEmpty();
        }
        return expanded.contains(cat);
    }

    /**
     * Hidden (not currently visible) elements in {@code cat} that match the
     * current search query.  The query is matched against the element's
     * translated display name, case-insensitively.
     */
    private List<HudElement> hiddenElements(HudCategory cat) {
        String q = searchQuery.toLowerCase(Locale.ROOT);
        List<HudElement> result = new ArrayList<>();
        for (HudElement el : HudElements.all()) {
            if (el.category() != cat) continue;
            HudLayout.Entry e = snapshot.get(el.id());
            if (e != null && e.visible) continue;           // already visible -> skip
            if (!q.isEmpty()) {
                String name = Component.translatable(el.translationKey())
                                       .getString().toLowerCase(Locale.ROOT);
                if (!name.contains(q)) continue;
            }
            result.add(el);
        }
        return result;
    }

    // Rendering

    public void render(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        int  h    = totalHeight();

        gfx.fill(x, y, x + WIDTH, y + h, 0xE0101010);
        drawOutline(gfx, x, y, WIDTH, h, 0xFFAAAAAA);

        int rowY = y + PADDING_Y;

        // Search bar
        renderSearchBar(gfx, font, rowY, mouseX, mouseY);
        rowY += SEARCH_H + SEARCH_GAP;

        // Config section (export / import)
        gfx.text(font, Component.translatable("tinyhud.menu.config"),
                 x + PADDING_X, rowY + (CFG_HDR_H - font.lineHeight) / 2, 0xFFAAAAAA, false);
        rowY += CFG_HDR_H;
        rowY = renderActionRow(gfx, font, rowY, mouseX, mouseY, "tinyhud.menu.export");
        rowY = renderActionRow(gfx, font, rowY, mouseX, mouseY, "tinyhud.menu.import");

        // Categories - first one draws a divider separating it from the config section
        boolean first = false;
        for (HudCategory cat : HudCategory.values()) {
            if (!first) {
                // Thin hairline divider between categories
                gfx.fill(x + 4, rowY, x + WIDTH - 4, rowY + 1, 0x28FFFFFF);
            }
            first = false;

            boolean exp    = categoryExpanded(cat);
            boolean hovCat = mouseX >= x && mouseX < x + WIDTH
                    && mouseY >= rowY && mouseY < rowY + CAT_ROW_H;
            if (hovCat) gfx.fill(x + 1, rowY, x + WIDTH - 1, rowY + CAT_ROW_H, 0x40FFFFFF);

            // Collapse / expand arrow
            gfx.text(font, Component.literal(exp ? "▼" : "▶"),
                     x + PADDING_X, rowY + (CAT_ROW_H - font.lineHeight) / 2,
                     0xFFAAAAAA, false);

            // Category name
            gfx.text(font, Component.translatable(cat.translationKey()),
                     x + PADDING_X + font.width("▶ "),
                     rowY + (CAT_ROW_H - font.lineHeight) / 2,
                     hovCat ? 0xFFFFFFFF : 0xFFCCCCCC, false);
            rowY += CAT_ROW_H;

            // Elements (only when expanded)
            if (exp) {
                List<HudElement> elems = hiddenElements(cat);
                if (elems.isEmpty()) {
                    // Placeholder row when every element in the category is already visible
                    gfx.text(font, Component.literal("(all visible)"),
                             x + PADDING_X + INDENT,
                             rowY + (ELEM_ROW_H - font.lineHeight) / 2,
                             0xFF555555, false);
                    rowY += ELEM_ROW_H;
                } else {
                    for (HudElement el : elems) {
                        boolean hovEl = mouseX >= x && mouseX < x + WIDTH
                                && mouseY >= rowY && mouseY < rowY + ELEM_ROW_H;
                        if (hovEl) gfx.fill(x + 1, rowY, x + WIDTH - 1, rowY + ELEM_ROW_H, 0x60FFFFFF);
                        gfx.text(font, Component.translatable(el.translationKey()),
                                 x + PADDING_X + INDENT,
                                 rowY + (ELEM_ROW_H - font.lineHeight) / 2,
                                 0xFFFFFFFF, false);
                        rowY += ELEM_ROW_H;
                    }
                }
            }
        }
    }

    /** Renders one clickable config action row, returning the next row's Y. */
    private int renderActionRow(GuiGraphicsExtractor gfx, Font font, int rowY,
                                int mouseX, int mouseY, String translationKey) {
        boolean hov = mouseX >= x && mouseX < x + WIDTH
                && mouseY >= rowY && mouseY < rowY + CFG_ROW_H;
        if (hov) gfx.fill(x + 1, rowY, x + WIDTH - 1, rowY + CFG_ROW_H, 0x60FFFFFF);
        gfx.text(font, Component.translatable(translationKey),
                 x + PADDING_X + INDENT, rowY + (CFG_ROW_H - font.lineHeight) / 2,
                 0xFFFFFFFF, false);
        return rowY + CFG_ROW_H;
    }

    private void renderSearchBar(GuiGraphicsExtractor gfx, Font font,
                                 int rowY, int mouseX, int mouseY) {
        int barX = x + PADDING_X;
        int barW = WIDTH - PADDING_X * 2;

        gfx.fill(barX, rowY, barX + barW, rowY + SEARCH_H, 0xFF1A1A1A);
        drawOutline(gfx, barX, rowY, barW, SEARCH_H,
                    searchQuery.isEmpty() ? 0xFF555555 : 0xFF6FA8DC);

        boolean hasQ    = !searchQuery.isEmpty();
        int     rightPad = hasQ ? CLEAR_W : 0;
        int     maxTW   = barW - 6 - rightPad;

        String display = hasQ ? searchQuery : "Search…";
        // Clip from left when too long
        while (!display.isEmpty() && font.width(display) > maxTW) display = display.substring(1);
        gfx.text(font, Component.literal(display), barX + 3, rowY + 2,
                 hasQ ? 0xFFFFFFFF : 0xFF777777, false);

        if (hasQ) {
            int     clearX = x + WIDTH - PADDING_X - CLEAR_W;
            boolean hovC   = mouseX >= clearX && mouseX < clearX + CLEAR_W
                    && mouseY >= rowY && mouseY < rowY + SEARCH_H;
            if (hovC) gfx.fill(clearX, rowY + 1, clearX + CLEAR_W - 1, rowY + SEARCH_H - 1, 0xFF404040);
            gfx.text(font, Component.literal("x"), clearX + 3, rowY + 2,
                     hovC ? 0xFFFFFFFF : 0xFFAAAAAA, false);
        }
    }

    // Input

    /**
     * Handles a mouse click inside this menu.
     *
     * @return {@code true}  if the menu should be closed (an element was selected),
     *         {@code false} to keep the menu open (category toggled, search edited, etc.)
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) return false;
        // Right-clicks and middle-clicks inside the menu are swallowed but do nothing.
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

        int rowY = y + PADDING_Y;

        // Search bar
        if (mouseY >= rowY && mouseY < rowY + SEARCH_H) {
            if (!searchQuery.isEmpty()) {
                int clearX = x + WIDTH - PADDING_X - CLEAR_W;
                if (mouseX >= clearX && mouseX < clearX + CLEAR_W) {
                    searchQuery = "";
                    reposition();
                }
            }
            return false;
        }
        rowY += SEARCH_H + SEARCH_GAP;

        // Config section: header (skip) + export + import
        rowY += CFG_HDR_H;
        if (mouseY >= rowY && mouseY < rowY + CFG_ROW_H) {
            HudLayoutStorage.exportToFile(snapshot);   // exports the live editing state
            return true;
        }
        rowY += CFG_ROW_H;
        if (mouseY >= rowY && mouseY < rowY + CFG_ROW_H) {
            Map<Identifier, HudLayout.Entry> imported = HudLayoutStorage.importFromFile();
            if (imported != null) {
                snapshot.clear();
                snapshot.putAll(imported);
            }
            return true;
        }
        rowY += CFG_ROW_H;

        for (HudCategory cat : HudCategory.values()) {
            // Category header
            if (mouseY >= rowY && mouseY < rowY + CAT_ROW_H) {
                // Toggle only when not in search mode (search drives auto-expand)
                if (searchQuery.isEmpty()) {
                    if (expanded.contains(cat)) expanded.remove(cat);
                    else                        expanded.add(cat);
                    reposition();
                }
                return false;
            }
            rowY += CAT_ROW_H;

            // Element rows (only if category is expanded)
            if (categoryExpanded(cat)) {
                List<HudElement> elems = hiddenElements(cat);
                if (elems.isEmpty()) {
                    rowY += ELEM_ROW_H;         // placeholder row
                } else {
                    for (HudElement el : elems) {
                        if (mouseY >= rowY && mouseY < rowY + ELEM_ROW_H) {
                            onShowElement.accept(el);
                            return true;        // close menu
                        }
                        rowY += ELEM_ROW_H;
                    }
                }
            }
        }
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        int cp = event.codepoint();
        if (cp < 32 || cp == 127) return false;
        searchQuery += new String(Character.toChars(cp));
        reposition();
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
            int last = searchQuery.offsetByCodePoints(searchQuery.length(), -1);
            searchQuery = searchQuery.substring(0, last);
            reposition();
            return true;
        }
        return false;
    }

    // Drawing util

    private static void drawOutline(GuiGraphicsExtractor gfx,
                                    int x, int y, int w, int h, int color) {
        gfx.fill(x,         y,         x + w,     y + 1,     color);
        gfx.fill(x,         y + h - 1, x + w,     y + h,     color);
        gfx.fill(x,         y,         x + 1,     y + h,     color);
        gfx.fill(x + w - 1, y,         x + w,     y + h,     color);
    }
}
