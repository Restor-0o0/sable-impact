package org.restor.create_aeronautics_impact;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.joml.Vector3d;
import org.restor.create_aeronautics_impact.mixin.SubLevelSplitListenersMixin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Taking a build apart when it has stopped being one, and when what is left of it cannot hold itself up.
 *
 * <p>{@link Splitter} was written on the assumption that Sable's connectivity search was right and merely
 * slow, and for most of what happens to a build that is true. It is not true after a crash. The search is an
 * incremental distance field rather than a flood fill from scratch: a block that goes away is not re-walked,
 * it is compared against the heat its neighbours were left with, and a neighbour only becomes the root of a
 * new region when no <em>other</em> neighbour of it is nearer the old root. On a hull losing one block to a
 * pickaxe that test is exactly right. On a hull losing a thousand blocks in four ticks the heat it is reading
 * belongs to a build that no longer exists, and the answer it settles on is that nothing came apart - not
 * late, but wrongly, and running it again cannot change it. That is a wreck cut clean in half with daylight
 * through the cut, flying in formation with itself.
 *
 * <p>So the question gets asked here instead, and asked the one way that cannot be wrong: walk the build's
 * blocks and see what is actually reachable from what. A build that comes back in more than one piece is
 * handed to Sable's own assembly, piece by piece, exactly as Sable's own split would have handed it - the
 * largest piece stays where it is so the client has a body to trace the new ones back to, and the listeners
 * other mods hang on a split are told first.
 *
 * <p>The second half of it is {@link Ligament}, and it is about builds that have <em>not</em> come apart but
 * have no business still being in one piece: the deck plank bridging a hull broken almost in half, the two
 * blocks a gantry is pivoting on. Connectivity has nothing to say about those, because touching is a yes or a
 * no and three blocks touch as firmly as three hundred. The walk that answers the first question sorts the
 * build into layers on the way, and a layer is a cut already made - so the layers are priced by what they are
 * holding, the one that cannot hold it is broken, and a moment later the first half of this notices that the
 * build is now two builds.
 *
 * <p>All of it is bounded: one pass per build per {@code interval} ticks, nothing above a plot of
 * {@code volume} blocks looked at, and one millisecond ceiling shared by the whole level.
 */
public final class Sever {

    private static final Map<ServerLevel, Map<ServerSubLevel, Long>> CHECKED = new WeakHashMap<>();

    /** The six faces first, then the twelve edges, so a face-only walk is the first six of them. */
    private static final int[] DIRECTIONS = {
            1, 0, 0, -1, 0, 0, 0, 1, 0, 0, -1, 0, 0, 0, 1, 0, 0, -1,
            1, 1, 0, 1, -1, 0, -1, 1, 0, -1, -1, 0,
            1, 0, 1, 1, 0, -1, -1, 0, 1, -1, 0, -1,
            0, 1, 1, 0, 1, -1, 0, -1, 1, 0, -1, -1,
    };

    private static final byte SOLID = 1;
    private static final byte SEEN = 2;

    private static byte[] grid = new byte[0];

    private Sever() {
    }

    /**
     * Looks at the builds this mod has damaged recently, until the budget runs out.
     *
     * <p>Called from the break pass once the bounds are consistent again, because a pass of this can assemble
     * a new sub-level out of what it finds and can destroy blocks of its own.
     */
    public static void resolve(final ServerLevel level, final ImpactConfig.Tuning tuning) {
        if (!tuning.severSeparate() && !tuning.severLigament()) {
            return;
        }
        final ServerSubLevel[] hurried = Splitter.hurried(level);
        if (hurried.length == 0) {
            return;
        }

        final long now = level.getGameTime();
        final long deadline = System.nanoTime() + (long) (tuning.severMillis() * 1.0e6);
        final Map<ServerSubLevel, Long> checked =
                CHECKED.computeIfAbsent(level, ignored -> new WeakHashMap<>());
        checked.keySet().removeIf(ServerSubLevel::isRemoved);

        for (final ServerSubLevel subLevel : hurried) {
            if (System.nanoTime() >= deadline) {
                return;
            }
            if (subLevel.isRemoved()) {
                continue;
            }
            final Long last = checked.get(subLevel);
            if (last != null && now - last < tuning.severInterval()) {
                continue;
            }
            checked.put(subLevel, now);
            examine(level, subLevel, tuning);
        }
    }

    /**
     * One build: read it into a grid, walk it, and act on what the walk found.
     *
     * <p>A plot chunk that is not resident stops the pass outright. Connectivity read off a build with a hole
     * in it where a chunk should be is worse than no answer at all, and loading the chunk to find out is a
     * stall in the middle of a tick.
     */
    private static void examine(final ServerLevel level,
                                final ServerSubLevel subLevel,
                                final ImpactConfig.Tuning tuning) {
        final BoundingBox3ic plot = subLevel.getPlot().getBoundingBox();
        final int x0 = plot.minX();
        final int y0 = plot.minY();
        final int z0 = plot.minZ();
        final int sx = plot.maxX() - x0 + 1;
        final int sy = plot.maxY() - y0 + 1;
        final int sz = plot.maxZ() - z0 + 1;
        if (sx <= 0 || sy <= 0 || sz <= 0) {
            return;
        }

        final long volume = (long) sx * sy * sz;
        if (volume < 2 || volume > tuning.severVolume()) {
            return;
        }

        final int cells = (int) volume;
        if (grid.length < cells) {
            grid = new byte[cells];
        }
        Arrays.fill(grid, 0, cells, (byte) 0);

        if (!read(level, x0, y0, z0, sx, sy, sz)) {
            return;
        }

        final int directions = tuning.severDiagonals() ? 18 : 6;
        final List<IntArrayList> pieces = pieces(sx, sy, sz, directions);
        if (pieces.isEmpty()) {
            return;
        }

        if (pieces.size() > 1) {
            if (tuning.severSeparate()) {
                separate(level, subLevel, pieces, x0, y0, z0, sx, sz, tuning);
            }
            return;
        }
        if (tuning.severLigament()) {
            ligament(level, subLevel, pieces.get(0), x0, y0, z0, sx, sy, sz, directions, tuning);
        }
    }

    /**
     * Fills the grid from the world, a section at a time.
     *
     * <p>Solid means here what it means to Sable - anything that is not air - because the point of the walk is
     * to answer the question Sable answers, on the same blocks. A pass that disagreed about what counts would
     * split builds Sable thinks are whole.
     *
     * @return whether the whole of the plot was readable.
     */
    private static boolean read(final ServerLevel level,
                                final int x0, final int y0, final int z0,
                                final int sx, final int sy, final int sz) {
        final int minY = Math.max(y0, level.getMinBuildHeight());
        final int maxY = Math.min(y0 + sy - 1, level.getMaxBuildHeight() - 1);
        if (minY > maxY) {
            return false;
        }

        final int firstSection = level.getSectionIndex(minY);
        final int lastSection = level.getSectionIndex(maxY);

        for (int cx = x0 >> 4; cx <= (x0 + sx - 1) >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= (z0 + sz - 1) >> 4; cz++) {
                final LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    return false;
                }

                final int fromX = Math.max(x0, cx << 4);
                final int toX = Math.min(x0 + sx - 1, (cx << 4) + 15);
                final int fromZ = Math.max(z0, cz << 4);
                final int toZ = Math.min(z0 + sz - 1, (cz << 4) + 15);

                for (int index = firstSection; index <= lastSection; index++) {
                    if (index < 0 || index >= chunk.getSections().length) {
                        continue;
                    }
                    final LevelChunkSection section = chunk.getSection(index);
                    if (section.hasOnlyAir()) {
                        continue;
                    }

                    final int base = level.getSectionYFromSectionIndex(index) << 4;
                    final int fromY = Math.max(minY, base);
                    final int toY = Math.min(maxY, base + 15);

                    for (int y = fromY; y <= toY; y++) {
                        for (int z = fromZ; z <= toZ; z++) {
                            for (int x = fromX; x <= toX; x++) {
                                if (section.getBlockState(x & 15, y & 15, z & 15).isAir()) {
                                    continue;
                                }
                                grid[((y - y0) * sz + (z - z0)) * sx + (x - x0)] = SOLID;
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    /** Everything the build is made of, in reachable groups, largest first. */
    private static List<IntArrayList> pieces(final int sx, final int sy, final int sz, final int directions) {
        final List<IntArrayList> found = new ArrayList<>(2);
        final int cells = sx * sy * sz;

        for (int seed = 0; seed < cells; seed++) {
            if (grid[seed] != SOLID) {
                continue;
            }
            final IntArrayList piece = new IntArrayList();
            piece.add(seed);
            grid[seed] |= SEEN;
            spread(piece, sx, sy, sz, directions);
            found.add(piece);
        }

        found.sort(Comparator.comparingInt((final IntArrayList piece) -> piece.size()).reversed());
        return found;
    }

    /** Walks outwards from what is already in the list, appending everything it reaches in the order reached. */
    private static void spread(final IntArrayList reached,
                               final int sx, final int sy, final int sz,
                               final int directions) {
        for (int head = 0; head < reached.size(); head++) {
            final int at = reached.getInt(head);
            final int x = at % sx;
            final int rest = at / sx;
            final int z = rest % sz;
            final int y = rest / sz;

            for (int dir = 0; dir < directions; dir++) {
                final int nx = x + DIRECTIONS[dir * 3];
                final int ny = y + DIRECTIONS[dir * 3 + 1];
                final int nz = z + DIRECTIONS[dir * 3 + 2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) {
                    continue;
                }
                final int to = (ny * sz + nz) * sx + nx;
                if (grid[to] == SOLID) {
                    grid[to] |= SEEN;
                    reached.add(to);
                }
            }
        }
    }

    /**
     * Hands every piece but the largest to Sable's own assembly.
     *
     * <p>Which is what Sable does, in the same order and with the same two guards: the biggest piece keeps the
     * sub-level it was already in, so the client has a body to trace the new ones back to, and a piece that
     * turns out to weigh nothing is destroyed rather than left as an invisible passenger. The listeners go
     * first because a mod keeping its own record of what a contraption is made of has to be told before the
     * blocks move, not after.
     */
    private static void separate(final ServerLevel level,
                                 final ServerSubLevel subLevel,
                                 final List<IntArrayList> pieces,
                                 final int x0, final int y0, final int z0,
                                 final int sx, final int sz,
                                 final ImpactConfig.Tuning tuning) {
        final int limit = Math.min(pieces.size() - 1, tuning.severPieces());
        for (int index = 1; index <= limit; index++) {
            if (subLevel.isRemoved()) {
                return;
            }
            final IntArrayList piece = pieces.get(index);
            final List<BlockPos> blocks = new ArrayList<>(piece.size());
            for (int slot = 0; slot < piece.size(); slot++) {
                final int at = piece.getInt(slot);
                final int rest = at / sx;
                blocks.add(new BlockPos(x0 + at % sx, y0 + rest / sz, z0 + rest % sz));
            }

            final BoundingBox3i from = BoundingBox3i.from(blocks);
            if (from == null) {
                continue;
            }
            final BoundingBox3i bounds = from.expand(1, 1, 1);

            for (final SubLevelHeatMapManager.SplitListener listener
                    : SubLevelSplitListenersMixin.create_aeronautics_impact$listeners()) {
                listener.addBlocks(level, bounds, blocks);
            }

            final ServerSubLevel split = SubLevelAssemblyHelper.assembleBlocks(
                    level, blocks.get(0), blocks, bounds);
            if (split.getSelfMassTracker().getCenterOfMass() == null
                    || split.getSelfMassTracker().getMass() <= 0.0) {
                split.getPlot().destroyAllBlocks();
                final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
                if (container != null) {
                    container.removeSubLevel(split, SubLevelRemovalReason.REMOVED);
                }
            }
        }
    }

    /**
     * Breaks the layer that cannot hold what is on the other side of it.
     *
     * <p>The walk starts from the last block the first one reached rather than from wherever the grid happened
     * to begin, because layers are only cross-sections of a build when the walk runs along it: seeded in the
     * middle of a ship they are shells around the middle of a ship, and a joint in the bow shares a shell with
     * a joint in the stern. One walk from anywhere gives an end to start the real one from, and the first pass
     * of this already did that walk.
     *
     * <p>Only layers narrow enough to be a joint are weighed at all, which is what keeps this off the great
     * majority of a build: a cross-section of a hull is hundreds of blocks and is dismissed on its block count
     * without one of them being looked at.
     */
    private static void ligament(final ServerLevel level,
                                 final ServerSubLevel subLevel,
                                 final IntArrayList piece,
                                 final int x0, final int y0, final int z0,
                                 final int sx, final int sy, final int sz,
                                 final int directions,
                                 final ImpactConfig.Tuning tuning) {
        if (piece.size() < tuning.severMinSide() * 2) {
            return;
        }
        for (int slot = 0; slot < piece.size(); slot++) {
            grid[piece.getInt(slot)] = SOLID;
        }

        final List<IntArrayList> layers = layers(piece.getInt(piece.size() - 1), sx, sy, sz, directions);
        if (layers.size() < 3) {
            return;
        }

        final int[] size = new int[layers.size()];
        final double[] strength = new double[layers.size()];
        final int neck = tuning.severNeck();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int layer = 0; layer < layers.size(); layer++) {
            final IntArrayList blocks = layers.get(layer);
            size[layer] = blocks.size();
            strength[layer] = blocks.size() > neck
                    ? Double.POSITIVE_INFINITY
                    : held(level, blocks, x0, y0, z0, sx, sz, cursor);
        }

        final int cut = Ligament.fails(size, strength, tuning.severMinSide(), tuning.severCarry());
        if (cut < 0) {
            return;
        }

        final Vector3d fallback = new Vector3d(subLevel.logicalPose().position());
        final IntArrayList breaking = layers.get(cut);
        for (int slot = 0; slot < breaking.size(); slot++) {
            if (subLevel.isRemoved()) {
                return;
            }
            final int at = breaking.getInt(slot);
            final int rest = at / sx;
            cursor.set(x0 + at % sx, y0 + rest / sz, z0 + rest % sz);
            final BlockState state = level.getBlockState(cursor);
            if (state.isAir()) {
                continue;
            }
            final BlockProfile profile = BlockProfile.of(level, cursor, state);
            if (profile.indestructible() || profile.passable()) {
                continue;
            }
            final BlockPos pos = cursor.immutable();
            if (BlockScatter.shatterContraptionBlock(level, pos, state, fallback,
                    tuning.bearingFallSpeed(), profile.resistance())) {
                CrackTracker.spall(level, pos, true, tuning);
            }
        }
    }

    /** The same walk again, keeping what it reached at each step apart rather than all in one list. */
    private static List<IntArrayList> layers(final int seed,
                                             final int sx, final int sy, final int sz,
                                             final int directions) {
        final List<IntArrayList> found = new ArrayList<>();
        IntArrayList frontier = new IntArrayList();
        frontier.add(seed);
        grid[seed] |= SEEN;

        while (!frontier.isEmpty()) {
            found.add(frontier);
            final IntArrayList next = new IntArrayList();
            for (int slot = 0; slot < frontier.size(); slot++) {
                final int at = frontier.getInt(slot);
                final int x = at % sx;
                final int rest = at / sx;
                final int z = rest % sz;
                final int y = rest / sz;

                for (int dir = 0; dir < directions; dir++) {
                    final int nx = x + DIRECTIONS[dir * 3];
                    final int ny = y + DIRECTIONS[dir * 3 + 1];
                    final int nz = z + DIRECTIONS[dir * 3 + 2];
                    if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) {
                        continue;
                    }
                    final int to = (ny * sz + nz) * sx + nx;
                    if (grid[to] == SOLID) {
                        grid[to] |= SEEN;
                        next.add(to);
                    }
                }
            }
            frontier = next;
        }
        return found;
    }

    /** What one layer is made of, or infinity if any of it cannot be broken. */
    private static double held(final ServerLevel level,
                               final IntArrayList blocks,
                               final int x0, final int y0, final int z0,
                               final int sx, final int sz,
                               final BlockPos.MutableBlockPos cursor) {
        double sum = 0.0;
        for (int slot = 0; slot < blocks.size(); slot++) {
            final int at = blocks.getInt(slot);
            final int rest = at / sx;
            cursor.set(x0 + at % sx, y0 + rest / sz, z0 + rest % sz);
            final BlockState state = level.getBlockState(cursor);
            if (state.isAir()) {
                continue;
            }
            final BlockProfile profile = BlockProfile.of(level, cursor, state);
            if (profile.indestructible()) {
                return Double.POSITIVE_INFINITY;
            }
            if (!profile.passable()) {
                sum += profile.resistance();
            }
        }
        return sum;
    }
}
