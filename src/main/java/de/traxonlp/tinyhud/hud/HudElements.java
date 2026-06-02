package de.traxonlp.tinyhud.hud;

import de.traxonlp.tinyhud.hud.elements.ArmorDurabilityElement;
import de.traxonlp.tinyhud.hud.elements.ItemTrackerElement;
import de.traxonlp.tinyhud.hud.elements.MediaElement;
import de.traxonlp.tinyhud.hud.elements.BiomeElement;
import de.traxonlp.tinyhud.hud.elements.CoordinatesElement;
import de.traxonlp.tinyhud.hud.elements.CpuUsageElement;
import de.traxonlp.tinyhud.hud.elements.DayElement;
import de.traxonlp.tinyhud.hud.elements.DirectionElement;
import de.traxonlp.tinyhud.hud.elements.EntityCountElement;
import de.traxonlp.tinyhud.hud.elements.FpsElement;
import de.traxonlp.tinyhud.hud.elements.GpuUsageElement;
import de.traxonlp.tinyhud.hud.elements.LightLevelElement;
import de.traxonlp.tinyhud.hud.elements.LookingAtBlockElement;
import de.traxonlp.tinyhud.hud.elements.MemoryUsageElement;
import de.traxonlp.tinyhud.hud.elements.PingElement;
import de.traxonlp.tinyhud.hud.elements.PortalCoordinatesElement;
import de.traxonlp.tinyhud.hud.elements.SaturationElement;
import de.traxonlp.tinyhud.hud.elements.SlimeChunkElement;
import de.traxonlp.tinyhud.hud.elements.TimeElement;
import de.traxonlp.tinyhud.hud.elements.VelocityElement;
import de.traxonlp.tinyhud.hud.elements.WeatherElement;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HudElements {
    private static final List<HudElement> ELEMENTS = List.of(
            new TimeElement(),
            new DayElement(),
            new WeatherElement(),
            new CoordinatesElement(),
            new DirectionElement(),
            new BiomeElement(),
            new LightLevelElement(),
            new FpsElement(),
            new PingElement(),
            new LookingAtBlockElement(),
            new VelocityElement(),
            new PortalCoordinatesElement(),
            new ArmorDurabilityElement(),
            new EntityCountElement(),
            new SlimeChunkElement(),
            new CpuUsageElement(),
            new MemoryUsageElement(),
            new GpuUsageElement(),
            new SaturationElement(),
            new ItemTrackerElement(),
            new MediaElement()
    );

    private static final Map<Identifier, HudElement> BY_ID;

    static {
        Map<Identifier, HudElement> m = new LinkedHashMap<>();
        for (HudElement e : ELEMENTS) m.put(e.id(), e);
        BY_ID = m;
    }

    private HudElements() {
    }

    public static List<HudElement> all() {
        return ELEMENTS;
    }

    public static HudElement byId(Identifier id) {
        return BY_ID.get(id);
    }
}
