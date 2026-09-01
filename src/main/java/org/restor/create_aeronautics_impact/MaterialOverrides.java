package org.restor.create_aeronautics_impact;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the config has to say about particular blocks, over and above what vanilla's own numbers say.
 *
 * <p>Everything else in this mod derives a block's strength from mining hardness and blast resistance,
 * which works because those two are the only stats every block in every mod actually has. What they are not
 * is a considered opinion: an author picks them to place a block on a pickaxe tier, so the immovable heart
 * of a machine can easily come out softer than gravel. This is where a pack says otherwise - per block, per
 * tag, or per mod - without anyone recompiling anything.
 *
 * <p>An entry is a selector followed by settings, separated by spaces or commas:
 *
 * <pre>
 * "minecraft:obsidian      resistance=6"
 * "#minecraft:leaves       soft=true"
 * "create:*                scale=1.5"
 * "mekanism:*              indestructible=false"
 * </pre>
 *
 * <p>Selectors are an exact block id, a block tag written with a leading hash, every block in a namespace
 * written as the namespace and a star, or every block at all written as a bare star. A block may be caught
 * by several at once, and the most specific one to name a given setting is the one that decides it: an exact
 * id beats a tag, a tag beats a namespace, a namespace beats the bare star, and among equals the entry
 * written later wins. Settings are read one at a time rather than as a block, so a namespace rule setting
 * scale and an exact rule setting fragile both apply to the same block.
 *
 * <p>Anything that does not parse is dropped with a line in the log and the rest of the file is honoured. A
 * rule naming a block or tag that no installed mod provides is not an error - it simply never matches -
 * which is what lets one config file cover a pack whose mod list changes.
 */
public final class MaterialOverrides {

    private static final Logger LOG = LoggerFactory.getLogger("create_aeronautics_impact");

    /** No rule said anything, so every question falls through to what the block itself says. */
    public static final Rule NONE = new Rule(null, null, null, null, null, null);

    /**
     * What the config has decided about a block, with null for everything it did not mention.
     *
     * @param resistance     the block's strength outright, in the units the model works in once the hardness
     *                       range has been compressed - around 0.6 for dirt, 1.4 for stone, 3 for obsidian -
     *                       replacing the derivation from vanilla stats entirely.
     * @param scale          a multiplier on that strength, applied whether it was derived or given.
     * @param indestructible whether the block is never broken by anything this mod does.
     * @param fragile        whether the block shatters at a fraction of the speed its material would ask for,
     *                       the way Sable's own fragile blocks do.
     * @param soft           whether a hull passes through the block the way it passes through undergrowth,
     *                       clearing it rather than being stopped by it.
     * @param failure        how the block gives way under a shock, overriding what its sound implied.
     */
    public record Rule(@Nullable Double resistance,
                       @Nullable Double scale,
                       @Nullable Boolean indestructible,
                       @Nullable Boolean fragile,
                       @Nullable Boolean soft,
                       @Nullable Failure failure) {

        /** This rule with everything the more specific one had an opinion about taken from that one instead. */
        Rule under(final Rule finer) {
            return new Rule(
                    finer.resistance != null ? finer.resistance : this.resistance,
                    finer.scale != null ? finer.scale : this.scale,
                    finer.indestructible != null ? finer.indestructible : this.indestructible,
                    finer.fragile != null ? finer.fragile : this.fragile,
                    finer.soft != null ? finer.soft : this.soft,
                    finer.failure != null ? finer.failure : this.failure);
        }

        boolean empty() {
            return this.resistance == null && this.scale == null && this.indestructible == null
                    && this.fragile == null && this.soft == null && this.failure == null;
        }

        /** The strength this rule leaves a block with, given what the block's own stats came to. */
        public double resistance(final double derived) {
            final double base = this.resistance != null ? this.resistance : derived;
            return this.scale != null ? base * this.scale : base;
        }

        /** Whether the block is indestructible, given what its own stats came to. */
        public boolean indestructible(final boolean derived) {
            return this.indestructible != null ? this.indestructible : derived;
        }

        /** Whether the block is fragile, given what Sable said about it. */
        public boolean fragile(final boolean derived) {
            return this.fragile != null ? this.fragile : derived;
        }

        /** Whether the block is soft, given what its collision shape said. */
        public boolean soft(final boolean derived) {
            return this.soft != null ? this.soft : derived;
        }

        /** How the block fails, given what its sound implied. */
        public Failure failure(final Failure derived) {
            return this.failure != null ? this.failure : derived;
        }
    }

    /**
     * The parsed file, keyed for lookup: one rule for everything, one per namespace, one per block.
     *
     * <p>Tags are a list rather than a map because there is nothing to key them by - a block state cannot
     * name the tags it is in, so each has to be asked in turn. That keeps them in file order, which is what
     * makes "the entry written later wins" true between two tags that both match.
     */
    private record Table(Rule everything,
                         Map<String, Rule> namespaces,
                         List<Map.Entry<TagKey<Block>, Rule>> tags,
                         Map<ResourceLocation, Rule> blocks) {

        static final Table EMPTY = new Table(NONE, Map.of(), List.of(), Map.of());

        boolean idle() {
            return this.everything.empty() && this.namespaces.isEmpty()
                    && this.tags.isEmpty() && this.blocks.isEmpty();
        }
    }

    private static volatile Table table = Table.EMPTY;

    private MaterialOverrides() {
    }

    /**
     * Rebuilds the table from the config. Called whenever the config is reloaded, and whenever tags are,
     * since a rule written against a tag means nothing until the tag has been populated.
     */
    public static void reload(@Nullable final List<? extends String> entries) {
        table = parse(entries);
    }

    /** Whether anything at all is overridden, which is the common case and worth answering cheaply. */
    public static boolean idle() {
        return table.idle();
    }

    /** What the config says about this block, or {@link #NONE}. */
    public static Rule of(final BlockState state) {
        final Table current = table;
        if (current.idle()) {
            return NONE;
        }

        final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        Rule rule = current.everything;

        final Rule namespace = current.namespaces.get(id.getNamespace());
        if (namespace != null) {
            rule = rule.under(namespace);
        }
        for (final Map.Entry<TagKey<Block>, Rule> tag : current.tags) {
            if (state.is(tag.getKey())) {
                rule = rule.under(tag.getValue());
            }
        }
        final Rule exact = current.blocks.get(id);
        if (exact != null) {
            rule = rule.under(exact);
        }
        return rule;
    }

    /**
     * Builds a table from the config lines. Never throws: a line that does not parse is logged and skipped,
     * because one typo should not take the other forty rules down with it.
     */
    private static Table parse(@Nullable final List<? extends String> entries) {
        if (entries == null || entries.isEmpty()) {
            return Table.EMPTY;
        }

        Rule everything = NONE;
        final Map<String, Rule> namespaces = new HashMap<>();
        final List<Map.Entry<TagKey<Block>, Rule>> tags = new ArrayList<>();
        final Map<ResourceLocation, Rule> blocks = new HashMap<>();

        for (final String entry : entries) {
            final String line = entry == null ? "" : entry.trim();
            if (line.isEmpty()) {
                continue;
            }

            final String[] parts = line.split("[\\s,]+");
            final Rule rule = settings(line, parts);
            if (rule == null) {
                continue;
            }

            final String selector = parts[0];
            if (selector.equals("*")) {
                everything = everything.under(rule);
            } else if (selector.startsWith("#")) {
                final ResourceLocation id = ResourceLocation.tryParse(selector.substring(1));
                if (id == null) {
                    LOG.warn("materialOverrides: [{}] is not a tag id, in [{}]", selector, line);
                    continue;
                }
                tags.add(Map.entry(TagKey.create(Registries.BLOCK, id), rule));
            } else if (selector.endsWith(":*")) {
                final String namespace = selector.substring(0, selector.length() - 2);
                namespaces.merge(namespace, rule, Rule::under);
            } else {
                final ResourceLocation id = ResourceLocation.tryParse(selector);
                if (id == null) {
                    LOG.warn("materialOverrides: [{}] is not a block id, in [{}]", selector, line);
                    continue;
                }
                blocks.merge(id, rule, Rule::under);
            }
        }

        return new Table(everything, namespaces, tags, blocks);
    }

    /**
     * The settings half of one entry, or null if any part of it was unusable.
     *
     * <p>All or nothing per line. A rule that half applied would be worse than one that did not: the half
     * that took effect is invisible, so the log line about the half that did not looks like the whole story.
     *
     * @param parts the whole entry already split, with the selector still at index 0.
     */
    @Nullable
    private static Rule settings(final String line, final String[] parts) {
        if (parts.length < 2) {
            LOG.warn("materialOverrides: [{}] selects blocks but sets nothing on them", line);
            return null;
        }

        Double resistance = null;
        Double scale = null;
        Boolean indestructible = null;
        Boolean fragile = null;
        Boolean soft = null;
        Failure failure = null;

        for (int i = 1; i < parts.length; i++) {
            final String setting = parts[i];
            final int split = setting.indexOf('=');
            if (split <= 0 || split == setting.length() - 1) {
                LOG.warn("materialOverrides: [{}] is not a key=value setting, in [{}]", setting, line);
                return null;
            }
            final String key = setting.substring(0, split).toLowerCase(Locale.ROOT);
            final String value = setting.substring(split + 1);
            final boolean read = switch (key) {
                case "resistance" -> (resistance = number(line, setting, value)) != null;
                case "scale" -> (scale = number(line, setting, value)) != null;
                case "indestructible" -> (indestructible = flag(line, setting, value)) != null;
                case "fragile" -> (fragile = flag(line, setting, value)) != null;
                case "soft" -> (soft = flag(line, setting, value)) != null;
                case "failure" -> (failure = failure(line, setting, value)) != null;
                default -> {
                    LOG.warn("materialOverrides: no setting called [{}], in [{}]", key, line);
                    yield false;
                }
            };
            if (!read) {
                return null;
            }
        }

        return new Rule(resistance, scale, indestructible, fragile, soft, failure);
    }

    /** One of the three failure modes, case-insensitively, or null with a line in the log. */
    @Nullable
    private static Failure failure(final String line, final String setting, final String value) {
        for (final Failure mode : Failure.values()) {
            if (mode.name().equalsIgnoreCase(value)) {
                return mode;
            }
        }
        LOG.warn("materialOverrides: [{}] is not brittle, ductile or structural, in [{}]", setting, line);
        return null;
    }

    /** A non-negative number within the range the config allows, or null with a line in the log. */
    @Nullable
    private static Double number(final String line, final String setting, final String value) {
        final double parsed;
        try {
            parsed = Double.parseDouble(value);
        } catch (final NumberFormatException notANumber) {
            LOG.warn("materialOverrides: [{}] is not a number, in [{}]", setting, line);
            return null;
        }
        if (!(parsed >= 0.0) || parsed > 1.0E6) {
            LOG.warn("materialOverrides: [{}] is outside 0 to 1000000, in [{}]", setting, line);
            return null;
        }
        return parsed;
    }

    /** {@code true} or {@code false}, case-insensitively, or null with a line in the log. */
    @Nullable
    private static Boolean flag(final String line, final String setting, final String value) {
        if (value.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (value.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        LOG.warn("materialOverrides: [{}] is not true or false, in [{}]", setting, line);
        return null;
    }
}
