package de.traxonlp.tinyhud.hud;

public enum HudCategory {
    WORLD("tinyhud.category.world"),
    PLAYER("tinyhud.category.player"),
    SYSTEM("tinyhud.category.system");

    private final String translationKey;

    HudCategory(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }
}
