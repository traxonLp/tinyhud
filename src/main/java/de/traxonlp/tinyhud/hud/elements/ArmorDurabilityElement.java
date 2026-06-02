package de.traxonlp.tinyhud.hud.elements;

import de.traxonlp.tinyhud.TinyHUD;
import de.traxonlp.tinyhud.hud.HudCategory;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.List;

public class ArmorDurabilityElement implements HudElement {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final int ICON_SIZE = 16;
    private static final int ROW_HEIGHT = 18;
    private static final int ICON_TEXT_GAP = 4;
    private static final int BOX_PADDING = 2;
    private static final int BOX_COLOR = 0xA0000000;

    private final Identifier id =
            Identifier.fromNamespaceAndPath(TinyHUD.MODID, "armor_durability");

    private int lastWidth = 80;
    private int lastHeight = ROW_HEIGHT;

    @Override
    public Identifier id() {
        return id;
    }

    @Override
    public HudCategory category() {
        return HudCategory.PLAYER;
    }

    @Override
    public String translationKey() {
        return "tinyhud.element.armor_durability";
    }

    @Override
    public List<FormatOption> formats() {
        return List.of(
                new FormatOption("ratio", "tinyhud.format.armor.ratio"),
                new FormatOption("percent", "tinyhud.format.armor.percent"));
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
    public int defaultX(int screenW, int screenH) {
        return screenW - 100;
    }

    @Override
    public int defaultY(int screenW, int screenH) {
        return screenH - 100;
    }

    @Override
    public void render(GuiGraphicsExtractor gfx, DeltaTracker delta, int x, int y, HudLayout.Entry entry) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null) {
            lastWidth = 1;
            lastHeight = 1;
            return;
        }
        Font font = mc.font;
        Identifier fontId = parseFont(entry.font);
        Style styled = Style.EMPTY.withFont(new FontDescription.Resource(fontId));

        int activeRows = 0;
        int maxTextW = 0;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = p.getItemBySlot(slot);
            if (stack.isEmpty() || stack.getMaxDamage() <= 0) continue;
            String text = formatDurability(stack, entry.format);
            MutableComponent comp = Component.literal(text).withStyle(styled);
            maxTextW = Math.max(maxTextW, font.width(comp));
            activeRows++;
        }
        int contentW = Math.max(ICON_SIZE, ICON_SIZE + ICON_TEXT_GAP + maxTextW);
        int contentH = Math.max(ROW_HEIGHT, activeRows * ROW_HEIGHT);
        lastWidth = contentW;
        lastHeight = contentH;
        if (activeRows == 0) return;

        Matrix3x2fStack pose = gfx.pose();
        boolean scaled = entry.scale != 1.0f;
        if (scaled) {
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(entry.scale, entry.scale);
        }
        int baseX = scaled ? 0 : x;
        int baseY = scaled ? 0 : y;

        if (entry.box) {
            gfx.fill(baseX - BOX_PADDING, baseY - BOX_PADDING,
                    baseX + contentW + BOX_PADDING, baseY + contentH + BOX_PADDING, BOX_COLOR);
        }

        int row = 0;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = p.getItemBySlot(slot);
            if (stack.isEmpty() || stack.getMaxDamage() <= 0) continue;
            int rowY = baseY + row * ROW_HEIGHT;
            gfx.item(stack, baseX, rowY);
            String text = formatDurability(stack, entry.format);
            MutableComponent comp = Component.literal(text).withStyle(styled);
            int textX = baseX + ICON_SIZE + ICON_TEXT_GAP;
            int textY = rowY + (ICON_SIZE - font.lineHeight) / 2 + 1;
            int color = entry.rainbow ? rainbowColor(row) : entry.color;
            gfx.text(font, comp, textX, textY, color, true);
            row++;
        }

        if (scaled) pose.popMatrix();
    }

    private static String formatDurability(ItemStack stack, String format) {
        int max = stack.getMaxDamage();
        int remaining = max - stack.getDamageValue();
        if ("percent".equals(format)) {
            int pct = max == 0 ? 0 : Math.round(remaining * 100f / max);
            return pct + "%";
        }
        return remaining + "/" + max;
    }

    private static int rainbowColor(int rowIndex) {
        float baseHue = (System.currentTimeMillis() % 3000L) / 3000.0f;
        int rgb = java.awt.Color.HSBtoRGB(baseHue + rowIndex * 0.1f, 1.0f, 1.0f);
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
