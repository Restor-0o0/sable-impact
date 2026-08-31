package org.restor.create_aeronautics_impact;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/**
 * The mod entry point, which is only a handful of registrations.
 *
 * <p>Nothing here starts anything. The work is driven from three places the game already calls into:
 * {@link ImpactCallback}, which Sable reaches through the {@code BlockMixin} on every collision;
 * {@link VoxelClassifier}, which Sable reaches on every collider remesh; and two level-tick listeners,
 * {@link HullSweeper} for everything the solver will not report on its own and {@link PendingBreaks} for
 * applying what a tick decided once the physics step is over.
 *
 * <p>The config is registered as {@link ModConfig.Type#SERVER}, so it lives in the save rather than in the
 * installation. Every event listener below exists to drop the caches that depend on it.
 */
@Mod(CreateAeronauticsImpact.MODID)
public class CreateAeronauticsImpact {

    public static final String MODID = "create_aeronautics_impact";

    /** Registers the config, the listeners that drop its caches, and the two level-tick hooks. */
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
