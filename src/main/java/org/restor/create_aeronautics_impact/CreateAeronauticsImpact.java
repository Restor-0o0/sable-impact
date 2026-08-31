package org.restor.create_aeronautics_impact;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

@Mod(CreateAeronauticsImpact.MODID)
public class CreateAeronauticsImpact {

    public static final String MODID = "create_aeronautics_impact";

    public CreateAeronauticsImpact(final ModContainer container, final IEventBus modBus) {
        container.registerConfig(ModConfig.Type.SERVER, ImpactConfig.SPEC);

        modBus.addListener(ModConfigEvent.Loading.class, event -> ImpactConfig.invalidate());
        modBus.addListener(ModConfigEvent.Reloading.class, event -> ImpactConfig.invalidate());
        modBus.addListener(ModConfigEvent.Unloading.class, event -> ImpactConfig.invalidate());

        // A material rule written against a tag means nothing until the tag has been populated, and tags
        // arrive after the config does - and again on every datapack reload.
        NeoForge.EVENT_BUS.addListener(TagsUpdatedEvent.class, event -> ImpactConfig.invalidate());

        NeoForge.EVENT_BUS.addListener(PendingBreaks::onLevelTick);
        NeoForge.EVENT_BUS.addListener(HullSweeper::onLevelTick);
    }
}
