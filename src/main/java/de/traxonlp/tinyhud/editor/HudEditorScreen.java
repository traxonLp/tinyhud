package de.traxonlp.tinyhud.editor;

import de.traxonlp.tinyhud.TinyHUD;
import de.traxonlp.tinyhud.hud.HudElement;
import de.traxonlp.tinyhud.hud.HudElements;
import de.traxonlp.tinyhud.hud.HudLayout;
import de.traxonlp.tinyhud.hud.HudLayoutStorage;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class HudEditorScreen extends Screen {
    private static final int SNAP_THRESHOLD       = 6;
    private static final int SNAP_GUIDE_COLOR         = 0xC0FFFF55;  // yellow - screen edges
    private static final int SNAP_GUIDE_ELEMENT_COLOR = 0xC055FFFF;  // cyan - element edges
    private static final int HANDLE_SIZE          = 8;   // resize grip, centred on the corner
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 3.0f;

    private Map<Identifier, HudLayout.Entry> snapshot;

    private Identifier draggingId;
    private Identifier resizingId;
    private int dragOffsetX;
    private int dragOffsetY;
    /** Captured at resize start: cursor distance from the element origin, and the scale then. */
    private double resizeRefDist;
    private float  resizeStartScale;
    private int snapGuideX = -1;
    private int snapGuideY = -1;
    /** True when the active snap guide is aligned to another element (cyan), false = screen edge (yellow). */
    private boolean snapGuideXElement;
    private boolean snapGuideYElement;

    private ContextMenu openMenu;
    private AddElementMenu openAddMenu;
    private StyleEditorPanel openPanel;

    /**
     * Swallow the single {@code charTyped} event that fires in the same GLFW poll
     * cycle as the key press that opened this screen (the editor keybind).  Without
     * this guard the keybind character (e.g. {@code ^} on some layouts) would appear
     * as the first character in whichever search box is active.
     */
    private boolean suppressNextChar = false;

    private long openTime;

    public HudEditorScreen() {
        super(Component.translatable("tinyhud.editor.title"));
    }

    @Override
    protected void init() {
        super.init();
        if (snapshot == null) {
            openTime = System.currentTimeMillis();
            snapshot = HudLayout.get().snapshot();
            ensureAllPresent();
            suppressNextChar = true;   // discard the char event from the opening keybind
        }
    }

    private void ensureAllPresent() {
        for (HudElement element : HudElements.all()) {
            snapshot.computeIfAbsent(element.id(), id -> HudLayout.Entry.fromPixels(
                    element.defaultX(this.width, this.height),
                    element.defaultY(this.width, this.height),
                    this.width, this.height));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        if (snapshot == null) return;
        float ease = fadeEase();
        gfx.fill(0, 0, this.width, this.height, (Math.round(ease * 0x80) << 24) | 0x101010);
        super.extractRenderState(gfx, mouseX, mouseY, partialTick);

        DeltaTracker delta = Minecraft.getInstance().getDeltaTracker();
        for (HudElement element : HudElements.all()) {
            HudLayout.Entry entry = snapshot.get(element.id());
            if (entry == null || !entry.visible) continue;
            int ex = entry.px(this.width);
            int ey = entry.py(this.height);
            try {
                element.render(gfx, delta, ex, ey, entry);
            } catch (Exception e) {
                TinyHUD.LOGGER.error("[TinyHUD] Exception rendering element '{}' in editor: {}", element.id(), e.getMessage(), e);
            }
            int w = Math.max(1, Math.round(element.width() * entry.scale));
            int h = Math.max(1, Math.round(element.height() * entry.scale));
            boolean hovered = !isOverModal(mouseX, mouseY)
                    && mouseX >= ex - 2 && mouseX < ex + w + 2
                    && mouseY >= ey - 2 && mouseY < ey + h + 2;
            boolean resizing = element.id().equals(resizingId);
            boolean active = element.id().equals(draggingId) || resizing;
            int color = fadeColor(active ? 0xFFFFFF55 : (hovered ? 0xFFFFFFFF : 0x80FFFFFF), ease);
            drawOutline(gfx, ex - 2, ey - 2, w + 4, h + 4, color);

            // Resize grip on the bottom-right corner (shown when hovered or actively resizing)
            if (hovered || resizing) {
                int hx = ex + w - HANDLE_SIZE / 2;
                int hy = ey + h - HANDLE_SIZE / 2;
                int hcol = fadeColor(resizing ? 0xFFFFFF55 : 0xFFFFFFFF, ease);
                gfx.fill(hx, hy, hx + HANDLE_SIZE, hy + HANDLE_SIZE, hcol);
            }
        }

        gfx.centeredText(this.font, Component.translatable("tinyhud.editor.hint"),
                this.width / 2, 6, (Math.round(ease * 0xFF) << 24) | 0x00FFFFFF);

        if (draggingId != null) {
            if (snapGuideX >= 0) {
                int col = snapGuideXElement ? SNAP_GUIDE_ELEMENT_COLOR : SNAP_GUIDE_COLOR;
                gfx.fill(snapGuideX, 0, snapGuideX + 1, this.height, col);
            }
            if (snapGuideY >= 0) {
                int col = snapGuideYElement ? SNAP_GUIDE_ELEMENT_COLOR : SNAP_GUIDE_COLOR;
                gfx.fill(0, snapGuideY, this.width, snapGuideY + 1, col);
            }
        }

        if (openPanel != null)    openPanel.render(gfx, mouseX, mouseY);
        if (openMenu != null)     openMenu.render(gfx, mouseX, mouseY);
        if (openAddMenu != null)  openAddMenu.render(gfx, mouseX, mouseY);
    }

    private boolean isOverModal(int mouseX, int mouseY) {
        return (openMenu != null    && openMenu.contains(mouseX, mouseY))
                || (openPanel != null    && openPanel.contains(mouseX, mouseY))
                || (openAddMenu != null  && openAddMenu.contains(mouseX, mouseY));
    }

    private static void drawOutline(GuiGraphicsExtractor gfx, int x, int y, int w, int h, int color) {
        gfx.fill(x, y, x + w, y + 1, color);
        gfx.fill(x, y + h - 1, x + w, y + h, color);
        gfx.fill(x, y, x + 1, y + h, color);
        gfx.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        if (openAddMenu != null) {
            if (openAddMenu.contains(mouseX, mouseY)) {
                boolean close = openAddMenu.mouseClicked(mouseX, mouseY, button);
                if (close) openAddMenu = null;
                return true;
            }
            openAddMenu = null;
        }

        if (openPanel != null) {
            if (openPanel.contains(mouseX, mouseY)) {
                openPanel.mouseClicked(mouseX, mouseY, button);
                return true;
            }
            openPanel = null;
        }

        if (openMenu != null) {
            if (openMenu.contains(mouseX, mouseY)) {
                openMenu.click(mouseX, mouseY);
                openMenu = null;
                return true;
            }
            openMenu = null;
        }

        HudElement hit = topmostElementAt(mouseX, mouseY);

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            openMenu    = null;
            openAddMenu = null;
            if (hit != null) {
                openMenu = buildElementActionMenu(hit, (int) mouseX, (int) mouseY);
            } else {
                openAddMenu = new AddElementMenu((int) mouseX, (int) mouseY,
                        this.width, this.height, snapshot, this::showElement);
            }
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            // Resize grip takes priority over moving - check the corner handles first.
            HudElement resizeHit = topmostResizeHandleAt(mouseX, mouseY);
            if (resizeHit != null) {
                resizingId = resizeHit.id();
                HudLayout.Entry re = snapshot.get(resizingId);
                double dx = mouseX - re.px(this.width);
                double dy = mouseY - re.py(this.height);
                resizeRefDist    = Math.max(1.0, Math.hypot(dx, dy));
                resizeStartScale = re.scale;
                return true;
            }
            if (hit != null) {
                HudLayout.Entry entry = snapshot.get(hit.id());
                draggingId = hit.id();
                dragOffsetX = (int) Math.round(mouseX - entry.px(this.width));
                dragOffsetY = (int) Math.round(mouseY - entry.py(this.height));
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    private HudElement topmostElementAt(double mouseX, double mouseY) {
        List<HudElement> reversed = new ArrayList<>(HudElements.all());
        Collections.reverse(reversed);
        for (HudElement element : reversed) {
            HudLayout.Entry entry = snapshot.get(element.id());
            if (entry == null || !entry.visible) continue;
            int ex = entry.px(this.width);
            int ey = entry.py(this.height);
            int w = Math.max(1, Math.round(element.width() * entry.scale));
            int h = Math.max(1, Math.round(element.height() * entry.scale));
            if (mouseX >= ex - 2 && mouseX < ex + w + 2
                    && mouseY >= ey - 2 && mouseY < ey + h + 2) {
                return element;
            }
        }
        return null;
    }

    /** Topmost visible element whose bottom-right resize grip contains the point, or null. */
    private HudElement topmostResizeHandleAt(double mouseX, double mouseY) {
        List<HudElement> reversed = new ArrayList<>(HudElements.all());
        Collections.reverse(reversed);
        for (HudElement element : reversed) {
            HudLayout.Entry entry = snapshot.get(element.id());
            if (entry == null || !entry.visible) continue;
            int ex = entry.px(this.width);
            int ey = entry.py(this.height);
            int w = Math.max(1, Math.round(element.width() * entry.scale));
            int h = Math.max(1, Math.round(element.height() * entry.scale));
            int hx = ex + w - HANDLE_SIZE / 2;
            int hy = ey + h - HANDLE_SIZE / 2;
            if (mouseX >= hx && mouseX < hx + HANDLE_SIZE
                    && mouseY >= hy && mouseY < hy + HANDLE_SIZE) {
                return element;
            }
        }
        return null;
    }

    private ContextMenu buildElementActionMenu(HudElement element, int x, int y) {
        ContextMenu menu = new ContextMenu();
        menu.addHeader(Component.translatable(element.translationKey()));
        menu.addItem(Component.translatable("tinyhud.menu.edit_style"), () -> {
            HudLayout.Entry entry = snapshot.get(element.id());
            if (entry == null) return;
            openPanel = new StyleEditorPanel(element, entry);
            int w = Math.max(1, Math.round(element.width() * entry.scale));
            openPanel.position(entry.px(this.width) + w + 8, entry.py(this.height), this.width, this.height);
        });
        menu.addItem(Component.translatable("tinyhud.menu.hide"), () -> {
            HudLayout.Entry entry = snapshot.get(element.id());
            if (entry != null) entry.visible = false;
        });
        menu.position(x, y, this.width, this.height);
        return menu;
    }

    private void showElement(HudElement element) {
        HudLayout.Entry entry = snapshot.get(element.id());
        if (entry == null) {
            entry = HudLayout.Entry.fromPixels(
                    element.defaultX(this.width, this.height),
                    element.defaultY(this.width, this.height),
                    this.width, this.height);
            snapshot.put(element.id(), entry);
        } else {
            entry.visible = true;
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (openPanel != null && openPanel.mouseDragged(event.x(), event.y())) {
            return true;
        }
        if (resizingId != null && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            HudLayout.Entry e = snapshot.get(resizingId);
            if (e != null) {
                // Scale relative to how far the cursor moved from the element origin since grab.
                // Radial (distance-based) so it's independent of the element's aspect ratio -
                // a short, wide element no longer jumps to max on a slight vertical drag.
                double dist = Math.hypot(event.x() - e.px(this.width), event.y() - e.py(this.height));
                double s = resizeStartScale * (dist / resizeRefDist);
                s = Math.max(MIN_SCALE, Math.min(MAX_SCALE, s));
                e.scale = Math.round(s * 20f) / 20f;   // 0.05 steps, matches the Size slider
            }
            return true;
        }
        if (draggingId != null && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            HudLayout.Entry entry = snapshot.get(draggingId);
            HudElement element = HudElements.byId(draggingId);
            if (entry != null && element != null) {
                int w = Math.max(1, Math.round(element.width() * entry.scale));
                int h = Math.max(1, Math.round(element.height() * entry.scale));
                int newX = (int) Math.round(event.x() - dragOffsetX);
                int newY = (int) Math.round(event.y() - dragOffsetY);
                snapGuideX = -1;
                snapGuideY = -1;
                snapGuideXElement = false;
                snapGuideYElement = false;
                if (!this.minecraft.hasShiftDown()) {
                    // X-axis snap targets
                    // Each entry: [snappedLeftX, guideX, isElementSnap(0/1)]
                    List<Integer> xTargets = new ArrayList<>();
                    List<Integer> xGuides  = new ArrayList<>();
                    List<Boolean> xElem    = new ArrayList<>();
                    // Screen edges / center
                    xTargets.add(0);              xGuides.add(0);          xElem.add(false);
                    xTargets.add(this.width - w); xGuides.add(this.width); xElem.add(false);
                    xTargets.add((this.width - w) / 2); xGuides.add(this.width / 2); xElem.add(false);
                    // Other visible elements
                    for (HudElement other : HudElements.all()) {
                        if (other.id().equals(draggingId)) continue;
                        HudLayout.Entry oe = snapshot.get(other.id());
                        if (oe == null || !oe.visible) continue;
                        int ow = Math.max(1, Math.round(other.width() * oe.scale));
                        addElementSnapsX(xTargets, xGuides, xElem, oe.px(this.width), ow, w);
                    }
                    int[] sx = snapAxis(newX, xTargets, xGuides);
                    newX = sx[0];
                    snapGuideX = sx[1];
                    if (sx[1] >= 0) snapGuideXElement = xElem.get(sx[2]);

                    // Y-axis snap targets
                    List<Integer> yTargets = new ArrayList<>();
                    List<Integer> yGuides  = new ArrayList<>();
                    List<Boolean> yElem    = new ArrayList<>();
                    yTargets.add(0);               yGuides.add(0);           yElem.add(false);
                    yTargets.add(this.height - h); yGuides.add(this.height); yElem.add(false);
                    yTargets.add((this.height - h) / 2); yGuides.add(this.height / 2); yElem.add(false);
                    for (HudElement other : HudElements.all()) {
                        if (other.id().equals(draggingId)) continue;
                        HudLayout.Entry oe = snapshot.get(other.id());
                        if (oe == null || !oe.visible) continue;
                        int oh = Math.max(1, Math.round(other.height() * oe.scale));
                        addElementSnapsY(yTargets, yGuides, yElem, oe.py(this.height), oh, h);
                    }
                    int[] sy = snapAxis(newY, yTargets, yGuides);
                    newY = sy[0];
                    snapGuideY = sy[1];
                    if (sy[1] >= 0) snapGuideYElement = yElem.get(sy[2]);
                }
                int finalX = Math.max(0, Math.min(this.width - w, newX));
                int finalY = Math.max(0, Math.min(this.height - h, newY));
                entry.xFrac = (float) finalX / this.width;
                entry.yFrac = (float) finalY / this.height;
                return true;
            }
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    /**
     * Finds the best snap target within {@link #SNAP_THRESHOLD} px of {@code desired}.
     * Returns {@code [snappedPos, guideCoord, bestIndex]} - guideCoord is -1 if no snap.
     */
    private static int[] snapAxis(int desired, List<Integer> targets, List<Integer> guides) {
        int bestIdx   = -1;
        int bestDelta = SNAP_THRESHOLD + 1;
        for (int i = 0; i < targets.size(); i++) {
            int d = Math.abs(targets.get(i) - desired);
            if (d < bestDelta) { bestDelta = d; bestIdx = i; }
        }
        if (bestIdx < 0) return new int[]{desired, -1, -1};
        return new int[]{targets.get(bestIdx), guides.get(bestIdx), bestIdx};
    }

    /**
     * Appends 5 X-axis snap targets for aligning the dragged element (width {@code w})
     * against a stationary element at x=ox with width ow:
     * left↔left, left↔right, right↔right, right↔left, center↔center.
     */
    private static void addElementSnapsX(List<Integer> targets, List<Integer> guides,
                                          List<Boolean> elem, int ox, int ow, int w) {
        targets.add(ox);           guides.add(ox);        elem.add(true);  // left ↔ left
        targets.add(ox + ow);      guides.add(ox + ow);   elem.add(true);  // left ↔ right
        targets.add(ox + ow - w);  guides.add(ox + ow);   elem.add(true);  // right ↔ right
        targets.add(ox - w);       guides.add(ox);        elem.add(true);  // right ↔ left
        targets.add(ox + (ow - w) / 2); guides.add(ox + ow / 2); elem.add(true); // center ↔ center
    }

    /** Same as {@link #addElementSnapsX} but for the Y axis. */
    private static void addElementSnapsY(List<Integer> targets, List<Integer> guides,
                                          List<Boolean> elem, int oy, int oh, int h) {
        targets.add(oy);           guides.add(oy);        elem.add(true);
        targets.add(oy + oh);      guides.add(oy + oh);   elem.add(true);
        targets.add(oy + oh - h);  guides.add(oy + oh);   elem.add(true);
        targets.add(oy - h);       guides.add(oy);        elem.add(true);
        targets.add(oy + (oh - h) / 2); guides.add(oy + oh / 2); elem.add(true);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (openPanel != null && openPanel.mouseReleased()) {
            return true;
        }
        if (resizingId != null && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            resizingId = null;
            return true;
        }
        if (draggingId != null && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            draggingId = null;
            snapGuideX = -1;
            snapGuideY = -1;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (openPanel != null && openPanel.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (suppressNextChar) { suppressNextChar = false; return false; }
        if (openAddMenu != null && openAddMenu.charTyped(event)) return true;
        if (openPanel   != null && openPanel.charTyped(event))   return true;
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (openAddMenu != null && openAddMenu.keyPressed(event)) return true;
        if (openPanel   != null && openPanel.keyPressed(event))   return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        if (snapshot != null) {
            HudLayout.get().replaceWith(snapshot);
            HudLayoutStorage.save();
        }
    }

    /** Ease-out-quad fraction for the open animation (0 -> 1 over 200 ms). */
    private float fadeEase() {
        float t = Math.min(1.0f, (System.currentTimeMillis() - openTime) / 200.0f);
        return 1.0f - (1.0f - t) * (1.0f - t);
    }

    /** Scale the alpha channel of an ARGB color by {@code alpha} (0-1). */
    private static int fadeColor(int argb, float alpha) {
        int a = Math.round(((argb >> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
