package de.traxonlp.tinyhud.input;

import com.mojang.blaze3d.platform.InputConstants;
import de.traxonlp.tinyhud.TinyHUD;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = TinyHUD.MODID, value = Dist.CLIENT)
public final class KeyBindings {
    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(TinyHUD.MODID, "main"));

    public static final KeyMapping OPEN_EDITOR = new KeyMapping(
            "key.tinyhud.open_editor",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY
    );

    private KeyBindings() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_EDITOR);
    }
}
