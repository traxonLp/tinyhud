package de.traxonlp.tinyhud.hud.elements;

import de.traxonlp.tinyhud.TinyHUD;
import de.traxonlp.tinyhud.hud.HudCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class LookingAtBlockElement extends TextHudElement {
    public LookingAtBlockElement() {
        super(Identifier.fromNamespaceAndPath(TinyHUD.MODID, "looking_at"), "tinyhud.element.looking_at");
    }

    @Override
    public HudCategory category() {
        return HudCategory.PLAYER;
    }

    @Override
    protected String text(Minecraft mc) {
        ClientLevel level = mc.level;
        HitResult hit = mc.hitResult;
        if (level == null || !(hit instanceof BlockHitResult bhr) || hit.getType() == HitResult.Type.MISS) {
            return "";
        }
        BlockPos pos = bhr.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return "";
        ItemStack stack = state.getCloneItemStack(level, pos, false);
        if (!stack.isEmpty()) {
            return stack.getHoverName().getString();
        }
        return state.getBlock().getName().getString();
    }

    @Override
    public int defaultX(int screenW, int screenH) {
        return screenW - 120;
    }

    @Override
    public int defaultY(int screenW, int screenH) {
        return screenH - 60;
    }
}
