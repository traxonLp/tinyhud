package de.traxonlp.tinyhud.osfont;

import de.traxonlp.tinyhud.TinyHUD;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = TinyHUD.MODID, value = Dist.CLIENT)
public final class OsFontRegistration {
    private static final String PACK_ID = TinyHUD.MODID + "_os_fonts";
    private static List<OsFont> discovered;

    private OsFontRegistration() {
    }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        if (discovered == null) {
            discovered = OsFontDiscovery.discover();
            OsFonts.set(discovered);
        }
        if (discovered.isEmpty()) return;

        PackLocationInfo location = new PackLocationInfo(
                PACK_ID,
                Component.literal("TinyHUD OS Fonts"),
                PackSource.BUILT_IN,
                Optional.empty()
        );
        OsFontPackResources resources = new OsFontPackResources(location, discovered);
        Pack.Metadata meta = new Pack.Metadata(
                Component.literal("OS fonts exposed for TinyHUD"),
                PackCompatibility.COMPATIBLE,
                FeatureFlagSet.of(),
                List.of(),
                true
        );
        PackSelectionConfig selectionConfig = new PackSelectionConfig(true, Pack.Position.TOP, true);
        Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
            @Override
            public net.minecraft.server.packs.PackResources openPrimary(PackLocationInfo info) {
                return resources;
            }

            @Override
            public net.minecraft.server.packs.PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
                return resources;
            }
        };
        event.addRepositorySource(consumer ->
                consumer.accept(new Pack(location, supplier, meta, selectionConfig)));
        TinyHUD.LOGGER.info("Registered TinyHUD OS font pack with {} fonts.", discovered.size());
    }
}
