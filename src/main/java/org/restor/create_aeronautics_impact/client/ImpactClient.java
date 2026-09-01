package org.restor.create_aeronautics_impact.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.restor.create_aeronautics_impact.CreateAeronauticsImpact;

/**
 * Everything this mod puts on screen, which is one way into its settings and nothing else.
 *
 * <p>A second entry point rather than a branch inside {@link CreateAeronauticsImpact}, because every class
 * it touches is client-only: loading them on a dedicated server would fail, and {@code dist = Dist.CLIENT}
 * is how the loader is told not to.
 *
 * <p>The settings themselves are NeoForge's, not this mod's. {@link ConfigurationScreen} builds a full
 * editor out of any {@code ModConfigSpec}, comments and ranges included, so the only thing worth writing
 * here is the two places a player looks for it: the Config button on the mod list entry, which is what
 * {@link IConfigScreenFactory} is, and a shortcut in the pause menu, which is what the button is.
 *
 * <p>There is deliberately nothing on the title screen. The config is {@code ModConfig.Type.SERVER} and so
 * lives in the save; with no world loaded there is no file to edit, and NeoForge greys the screen out. A
 * button that can only ever tell you to load a world first is worse than no button.
 */
@Mod(value = CreateAeronauticsImpact.MODID, dist = Dist.CLIENT)
public final class ImpactClient {

    private static final WidgetSprites SPRITES = new WidgetSprites(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsImpact.MODID, "widget/impact_button"),
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsImpact.MODID, "widget/impact_button_highlighted"));

    private static final Component TITLE =
            Component.translatable(CreateAeronauticsImpact.MODID + ".configuration.title");

    private static ModContainer container;

    public ImpactClient(final ModContainer container) {
        ImpactClient.container = container;

        final IConfigScreenFactory factory = ConfigurationScreen::new;
        container.registerExtensionPoint(IConfigScreenFactory.class, factory);

        NeoForge.EVENT_BUS.addListener(ImpactClient::onScreenInit);
    }

    /**
     * Hangs the settings shortcut in the corner of the pause menu.
     *
     * <p>Added after the screen has laid itself out rather than into its layout, so that it cannot shift a
     * vanilla button by a pixel: the pause menu is a centred grid, and an extra cell in it would move every
     * row. A corner is the one place a widget can be put without knowing where anything else ended up.
     */
    private static void onScreenInit(final ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof final PauseScreen pause) || !pause.showsPauseMenu()) {
            return;
        }

        final ImageButton button = new ImageButton(pause.width - 24, 4, 20, 20, SPRITES,
                press -> Minecraft.getInstance().setScreen(new ConfigurationScreen(container, pause)), TITLE);
        button.setTooltip(Tooltip.create(TITLE));
        event.addListener(button);
    }
}
