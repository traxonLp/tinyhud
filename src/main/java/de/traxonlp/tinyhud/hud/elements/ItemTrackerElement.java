package de.traxonlp.tinyhud.hud.elements;

import de.traxonlp.tinyhud.TinyHUD;
import de.traxonlp.tinyhud.hud.HudCategory;
import de.traxonlp.tinyhud.hud.HudElement;
import de.traxonlp.tinyhud.hud.HudLayout;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ItemTrackerElement implements HudElement {

    private static final int ICON_SIZE   = 16;
    private static final int ROW_HEIGHT  = ICON_SIZE;       // rows are icon-tall
    private static final int ICON_GAP    = 4;
    private static final int BOX_PADDING = 2;
    private static final int BOX_COLOR   = 0xA0000000;

    private final Identifier id =
            Identifier.fromNamespaceAndPath(TinyHUD.MODID, "item_tracker");

    private int lastWidth  = ICON_SIZE;
    private int lastHeight = ICON_SIZE;

    // HudElement identity

    @Override public Identifier   id()             { return id; }
    @Override public HudCategory  category()        { return HudCategory.PLAYER; }
    @Override public String       translationKey()  { return "tinyhud.element.item_tracker"; }
    @Override public int          width()           { return lastWidth; }
    @Override public int          height()          { return lastHeight; }
    @Override public boolean      hasHideZeroToggle() { return true; }

    @Override public int defaultX(int sw, int sh) { return 10; }
    @Override public int defaultY(int sw, int sh) { return 10; }

    // Rendering

    @Override
    public void render(GuiGraphicsExtractor gfx, DeltaTracker delta,
                       int x, int y, HudLayout.Entry entry) {

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { lastWidth = 1; lastHeight = 1; return; }

        List<String> ids = parseItemIds(entry.format);

        if (ids.isEmpty()) {
            Font font = mc.font;
            Component msg = Component.translatable("tinyhud.element.item_tracker.none");
            lastWidth  = Math.max(1, font.width(msg));
            lastHeight = font.lineHeight;
            gfx.text(font, msg, x, y, 0xFF888888, false);
            return;
        }

        Font font = mc.font;
        Identifier fontId = parseFont(entry.font);
        Style styled = Style.EMPTY.withFont(new FontDescription.Resource(fontId));

        // Resolve each configured item; skip unknown IDs silently
        List<Row> rows = new ArrayList<>(ids.size());
        int maxTextW = 0;
        for (String rawId : ids) {
            Identifier rid;
            try { rid = Identifier.parse(rawId); } catch (Exception e) { continue; }
            Optional<Item> opt = BuiltInRegistries.ITEM.getOptional(rid);
            if (opt.isEmpty()) continue;
            Item item = opt.get();
            int count = countItem(mc, item);
            if (count == 0 && entry.hideWhenZero) continue;   // skip absent items when enabled
            String txt = String.valueOf(count);
            rows.add(new Row(item.getDefaultInstance(), txt));
            maxTextW = Math.max(maxTextW, font.width(Component.literal(txt).withStyle(styled)));
        }

        if (rows.isEmpty()) { lastWidth = 1; lastHeight = 1; return; }

        int totalW = ICON_SIZE + ICON_GAP + Math.max(1, maxTextW);
        int totalH = rows.size() * ROW_HEIGHT;
        lastWidth  = totalW;
        lastHeight = totalH;

        Matrix3x2fStack pose = gfx.pose();
        boolean scaled = entry.scale != 1.0f;
        if (scaled) {
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(entry.scale, entry.scale);
        }
        int bx = scaled ? 0 : x;
        int by = scaled ? 0 : y;

        if (entry.box) {
            gfx.fill(bx - BOX_PADDING, by - BOX_PADDING,
                     bx + totalW + BOX_PADDING, by + totalH + BOX_PADDING, BOX_COLOR);
        }

        for (int r = 0; r < rows.size(); r++) {
            Row row  = rows.get(r);
            int rowY = by + r * ROW_HEIGHT;
            gfx.item(row.stack(), bx, rowY);
            int color  = entry.rainbow ? rainbowColor(r) : entry.color;
            int textY  = rowY + (ICON_SIZE - font.lineHeight) / 2 + 1;
            MutableComponent comp = Component.literal(row.countText()).withStyle(styled);
            gfx.text(font, comp, bx + ICON_SIZE + ICON_GAP, textY, color, true);
        }

        if (scaled) pose.popMatrix();
    }

    // Helpers

    private record Row(ItemStack stack, String countText) {}

    /**
     * Parses the format field (comma-separated item IDs) into an ordered list.
     * Accessible to StyleEditorPanel for the same parse logic.
     */
    public static List<String> parseItemIds(String format) {
        if (format == null || format.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : format.split(",")) {
            String s = part.trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private static int countItem(Minecraft mc, Item item) {
        int count = 0;
        int size = mc.player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack st = mc.player.getInventory().getItem(i);
            if (!st.isEmpty() && st.is(item)) count += st.getCount();
        }
        ItemStack offhand = mc.player.getOffhandItem();
        if (!offhand.isEmpty() && offhand.is(item)) count += offhand.getCount();
        return count;
    }

    private static int rainbowColor(int rowIndex) {
        float hue = (System.currentTimeMillis() % 3000L) / 3000.0f + rowIndex * 0.1f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private static Identifier parseFont(String s) {
        try {
            return Identifier.parse(s);
        } catch (Exception ex) {
            return Identifier.parse(HudLayout.DEFAULT_FONT);
        }
    }
}
