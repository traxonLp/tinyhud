package de.traxonlp.tinyhud;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(TinyHUD.MODID)
public class TinyHUD {
    public static final String MODID = "tinyhud";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TinyHUD(IEventBus modEventBus, ModContainer modContainer) {
    }
}
