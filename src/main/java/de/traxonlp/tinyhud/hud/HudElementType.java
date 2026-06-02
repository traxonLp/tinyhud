package de.traxonlp.tinyhud.hud;

import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

public record HudElementType(Identifier id, String translationKey, Supplier<HudElement> factory) {
    public HudElement create() {
        return factory.get();
    }
}
