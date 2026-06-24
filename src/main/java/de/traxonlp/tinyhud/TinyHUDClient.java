package de.traxonlp.tinyhud;

import de.traxonlp.tinyhud.editor.HudEditorScreen;
import de.traxonlp.tinyhud.hud.HudLayoutStorage;
import de.traxonlp.tinyhud.hud.HudRenderer;
import de.traxonlp.tinyhud.input.KeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = TinyHUD.MODID, dist = Dist.CLIENT)
public class TinyHUDClient {

    public TinyHUDClient(IEventBus modEventBus, ModContainer container) {
        // Mod-bus events (lifecycle + client registration)
        modEventBus.addListener(TinyHUDClient::onClientSetup);
        modEventBus.addListener(TinyHUDClient::onRegisterGuiLayers);
        // Game-bus event (per-tick input polling)
        NeoForge.EVENT_BUS.addListener(TinyHUDClient::onClientTick);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(HudLayoutStorage::load);
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.HOTBAR,
                Identifier.fromNamespaceAndPath(TinyHUD.MODID, "elements"),
                new HudRenderer()
        );
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (KeyBindings.OPEN_EDITOR.consumeClick()) {
            if (mc.gui.screen() == null) {
                mc.setScreenAndShow(new HudEditorScreen());
            }
        }
    }
}
