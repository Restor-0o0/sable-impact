package org.restor.create_aeronautics_impact.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
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

    /**
     * The full-width Mods button at the bottom of the pause menu grid. Its right edge is the grid's right
     * edge, which is the only measurement here that is worth taking off a live widget rather than guessing.
     */
    private static final String NEIGHBOUR = "fml.menu.mods";

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
     * Hangs the settings shortcut off the side of the pause menu, level with the Mods button.
     *
     * <p>Added after the screen has laid itself out rather than into its layout, so that it cannot shift a
     * vanilla button by a pixel: the pause menu is a centred grid, and an extra cell in it would move every
     * row. The position is read back off the laid-out grid instead, which is what puts the button beside the
     * menu rather than in a corner of the screen with nothing near it.
     */
    private static void onScreenInit(final ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof final PauseScreen pause) || !pause.showsPauseMenu()) {
            return;
        }

        final AbstractWidget neighbour = neighbour(event.getListenersList());
        final int x = neighbour == null ? pause.width - 24
                : Math.min(neighbour.getX() + neighbour.getWidth() + 4, pause.width - 24);
        final int y = neighbour == null ? 4 : neighbour.getY();

        final ImageButton button = new ImageButton(x, y, 20, 20, SPRITES,
                press -> Minecraft.getInstance().setScreen(new ConfigurationScreen(container, pause)), TITLE);
        button.setTooltip(Tooltip.create(TITLE));
        event.addListener(button);
    }

    /**
     * The laid-out widget to hang the button off, or null if the menu is not the one we expect.
     *
     * <p>Matched on the translation key rather than the component, because a resource pack is free to change
     * what the button says and this should not care. Nothing is assumed about the result beyond it having a
     * position: another mod may have moved the row, and the button follows it wherever it went.
     */
    @Nullable
    private static AbstractWidget neighbour(final Iterable<GuiEventListener> listeners) {
        for (final GuiEventListener listener : listeners) {
            if (listener instanceof final AbstractWidget widget
                    && widget.getMessage().getContents() instanceof final TranslatableContents contents
                    && NEIGHBOUR.equals(contents.getKey())) {
                return widget;
            }
        }
        return null;
    }
}
