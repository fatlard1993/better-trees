package justfatlard.better_trees;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class LeafStairsProcessor {

    private record Conversion(BlockPos pos, BlockState state) {}

    private static final Direction[] HORIZONTALS = {
        Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    // ── Entry point ──────────────────────────────────────────────────────────

    /**
     * @param ancient True when this was an ancient growth (fancy sapling or worldgen swap).
     *                Uses a much larger scan box and enables the outward halo.
     */
    public static void process(LevelAccessor level, BlockPos origin,
                               RandomSource random, boolean ancient) {
        // Ancient amplified trees can reach 60+ blocks tall and spread 12+ horizontally.
        int hRad = ancient ? 16 : 8;
        int maxY  = ancient ? 65 : 16;

        processBox(level, origin.offset(-hRad, 0, -hRad), origin.offset(hRad, maxY, hRad), ancient);
    }

    /**
     * Converts every leaf edge inside an explicit box.
     *
     * <p>Trees drawn into a structure template arrive here rather than through {@link #process}:
     * they are template blocks, never grown by a feature, so there is no trunk origin to measure a
     * scan radius from - only the box the placement wrote into.
     */
    public static void processBox(LevelAccessor level, BlockPos min, BlockPos max, boolean ancient) {
        List<Conversion> conversions = buildEdgeStairs(level, min, max);

        // Inner top-layer pass: flood-fill inward from the outer edge stairs, one ring
        // per iteration, until no top-layer leaves remain; see buildInnerTopLayer.
        {
            Set<BlockPos> occupied = new HashSet<>();
            for (Conversion c : conversions) occupied.add(c.pos());
            List<Conversion> frontier = conversions;
            List<Conversion> layer;
            while (!(layer = buildInnerTopLayer(level, frontier, occupied)).isEmpty()) {
                conversions.addAll(layer);
                frontier = layer;
            }
        }

        if (ancient) {
            conversions.addAll(buildAncientHalo(level, conversions));
        }

        for (Conversion c : conversions) {
            setGuarded(level, c.pos(), c.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    // ── Edge stair conversion ────────────────────────────────────────────────

    private static List<Conversion> buildEdgeStairs(LevelAccessor level,
                                                     BlockPos min, BlockPos max) {
        List<Conversion> conversions = new ArrayList<>();

        for (BlockPos cursor : BlockPos.betweenClosed(min, max)) {
            BlockState state = stateAt(level, cursor);

            if (!state.is(BlockTags.LEAVES)) continue;
            if (state.getValue(LeavesBlock.PERSISTENT)) continue;
            if (state.getBlock() instanceof LeafStairsBlock) continue;

            LeafStairsBlock stairsBlock = Main.LEAF_STAIRS_MAP.get(state.getBlock());
            if (stairsBlock == null) continue;

            int openCount = 0;
            Direction primaryDir = null;
            for (Direction dir : HORIZONTALS) {
                if (isOpen(level, cursor.relative(dir))) {
                    openCount++;
                    if (primaryDir == null) primaryDir = dir;
                }
            }

            if (openCount == 0) continue;
            if (openCount > 2) {
                // Top-layer protrusions (arm-ends of thin discs) have 3-4 open horizontal
                // sides and are skipped by the normal path, leaving the entire top disc
                // unconverted.  Salvage them: if air above and solid below, emit a STRAIGHT
                // BOTTOM-half stair facing toward the one solid neighbor (i.e. inward) so
                // buildInnerTopLayer can flood-fill the rest of the top cap from them.
                if (isOpen(level, cursor.above()) && !isOpen(level, cursor.below())) {
                    Direction capFacing = Direction.NORTH; // fallback for fully isolated leaf
                    for (Direction dir : HORIZONTALS) {
                        if (!isOpen(level, cursor.relative(dir))) { capFacing = dir; break; }
                    }
                    BlockState newState = stairsBlock.defaultBlockState()
                        .setValue(LeafStairsBlock.HORIZONTAL_FACING, capFacing)
                        .setValue(LeafStairsBlock.HALF,              Half.BOTTOM)
                        .setValue(LeafStairsBlock.STAIRS_SHAPE,      StairsShape.STRAIGHT)
                        .setValue(LeavesBlock.DISTANCE,              state.getValue(LeavesBlock.DISTANCE));
                    conversions.add(new Conversion(cursor.immutable(), newState));
                }
                continue;
            }
            if (openCount == 2 && isOpen(level, cursor.relative(primaryDir.getOpposite()))) continue;

            Direction facing   = primaryDir.getOpposite();
            Direction leftDir  = primaryDir.getCounterClockWise();
            Direction rightDir = primaryDir.getClockWise();
            boolean   leftOpen  = isOpen(level, cursor.relative(leftDir));
            boolean   rightOpen = isOpen(level, cursor.relative(rightDir));
            if (leftOpen && rightOpen) continue;

            StairsShape shape = leftOpen  ? StairsShape.OUTER_LEFT
                              : rightOpen ? StairsShape.OUTER_RIGHT
                              :             StairsShape.STRAIGHT;

            boolean openAbove = isOpen(level, cursor.above());
            boolean openBelow = isOpen(level, cursor.below());
            Half half = (!openAbove && openBelow) ? Half.TOP : Half.BOTTOM;

            BlockState newState = stairsBlock.defaultBlockState()
                .setValue(LeafStairsBlock.HORIZONTAL_FACING, facing)
                .setValue(LeafStairsBlock.HALF,              half)
                .setValue(LeafStairsBlock.STAIRS_SHAPE,      shape)
                .setValue(LeavesBlock.DISTANCE,              state.getValue(LeavesBlock.DISTANCE));

            conversions.add(new Conversion(cursor.immutable(), newState));
        }

        return conversions;
    }

    // ── Inner top-layer conversion ───────────────────────────────────────────

    /**
     * One flood-fill step of the inner top-layer pass.  For each STRAIGHT stair in
     * {@code frontier}, looks one step inward (in the stair's facing direction) and
     * converts that leaf if it is on the canopy's top layer (air above, solid below)
     * and not already claimed in {@code occupied}.
     *
     * <p>Called iteratively from {@link #process}: each call adds one more ring of
     * half=TOP stairs until no more top-layer leaves remain reachable, ensuring the
     * entire flat cap is converted regardless of crown width.
     *
     * @param occupied shared set of already-claimed positions; updated in place
     */
    private static List<Conversion> buildInnerTopLayer(LevelAccessor level,
                                                        List<Conversion> frontier,
                                                        Set<BlockPos> occupied) {
        List<Conversion> result = new ArrayList<>();

        for (Conversion c : frontier) {
            BlockState cs = c.state();
            // Propagate inward from STRAIGHT stairs (either half).
            if (cs.getValue(LeafStairsBlock.STAIRS_SHAPE) != StairsShape.STRAIGHT) continue;

            Direction facing   = cs.getValue(LeafStairsBlock.HORIZONTAL_FACING);
            BlockPos  innerPos = c.pos().relative(facing); // one step toward the interior

            if (!occupied.add(innerPos)) continue; // already claimed

            BlockState inner = stateAt(level, innerPos);
            if (!inner.is(BlockTags.LEAVES))             continue;
            if (inner.getValue(LeavesBlock.PERSISTENT))  continue;
            if (inner.getBlock() instanceof LeafStairsBlock) continue;

            // Must be on the canopy top: air above, solid below.
            if (!isOpen(level, innerPos.above())) continue;
            if ( isOpen(level, innerPos.below())) continue;

            LeafStairsBlock stairsBlock = Main.LEAF_STAIRS_MAP.get(inner.getBlock());
            if (stairsBlock == null) continue;

            // Same facing as the outer stair, but half=TOP: solid top surface
            // (smooth from above), bevel at the bottom-exterior edge (visible
            // from the side, continuing the stepped canopy-top profile).
            BlockState newState = stairsBlock.defaultBlockState()
                .setValue(LeafStairsBlock.HORIZONTAL_FACING, facing)
                .setValue(LeafStairsBlock.HALF,              Half.TOP)
                .setValue(LeafStairsBlock.STAIRS_SHAPE,      StairsShape.STRAIGHT)
                .setValue(LeavesBlock.DISTANCE,              inner.getValue(LeavesBlock.DISTANCE));

            result.add(new Conversion(innerPos.immutable(), newState));
        }

        return result;
    }

    // ── Worldgen fancy-feature swap ──────────────────────────────────────────

    /**
     * Replaces the just-placed worldgen tree with its fancy/mega species variant,
     * then amplifies the result into a truly massive ancient form.
     * Returns true on success (caller applies large scan + halo), false if no fancy
     * variant exists for this species or the feature failed (original tree restored).
     */
    public static boolean placeAncientExtension(LevelAccessor levelAccessor,
                                                ChunkGenerator chunkGenerator,
                                                BlockPos origin,
                                                RandomSource random) {
        if (!(levelAccessor instanceof WorldGenLevel level)) return false;

        BlockPos min = origin.offset(-8, 0, -8);
        BlockPos max = origin.offset( 8, 16, 8);

        // Determine species from dominant log type.
        Map<Block, Integer> logCounts = new HashMap<>();
        for (BlockPos cursor : BlockPos.betweenClosed(min, max)) {
            BlockState s = stateAt(level, cursor);
            if (s.is(BlockTags.LOGS)) logCounts.merge(s.getBlock(), 1, Integer::sum);
        }
        Block dominantLog = dominant(logCounts);
        if (dominantLog == null) return false;

        ResourceKey<Feature> fancyKey = AncientTrees.LOG_TO_FANCY.get(dominantLog);

        // No bigger variant to swap in, so grow the ancient form out of the tree already standing.
        //
        // Poplar is why this exists. It has three leaf colours behind one log, so there is no single
        // feature that can be substituted without repainting two thirds of the poplars that go
        // ancient. Amplifying in place reads the dominant leaf from the tree itself, so a yellow
        // poplar stays yellow. Bailing out here, which is what used to happen, meant poplar could
        // never be ancient at all.
        if (fancyKey == null) {
            amplifyAncientTree(level, origin, random);
            return true;
        }

        Optional<Holder.Reference<Feature>> feature = level.registryAccess()
            .lookupOrThrow(Registries.FEATURE)
            .get(fancyKey);
        if (feature.isEmpty()) return false;

        // Save existing tree for restoration on failure.
        Map<BlockPos, BlockState> saved = new HashMap<>();
        for (BlockPos cursor : BlockPos.betweenClosed(min, max)) {
            BlockState s = stateAt(level, cursor);
            if (s.is(BlockTags.LOGS) || (s.is(BlockTags.LEAVES) && !s.getValue(LeavesBlock.PERSISTENT)))
                saved.put(cursor.immutable(), s);
        }
        for (BlockPos pos : saved.keySet()) setGuarded(level, pos, Blocks.AIR.defaultBlockState(), 4);

        if (!feature.get().value().place(level, chunkGenerator, random, origin)) {
            for (Map.Entry<BlockPos, BlockState> e : saved.entrySet()) setGuarded(level, e.getKey(), e.getValue(), 4);
            return false;
        }

        // Fancy feature placed; now amplify it into a massive ancient form.
        amplifyAncientTree(level, origin, random);
        return true;
    }

    // ── Ancient amplification ────────────────────────────────────────────────

    /**
     * Amplifies the fancy feature just placed at {@code origin} into a massive
     * ancient form: thickens the trunk, extends it, then builds species-appropriate
     * crown heads from horizontal disc layers (not spheroids: disc layers give the
     * characteristic Minecraft stepped silhouette and jagged edge).
     *
     * <p>Called for both sapling-grown ancient trees (from {@link TreeFeatureMixin}
     * before edge-stair processing) and worldgen ancient trees (from
     * {@link #placeAncientExtension} after the fancy feature is placed).
     */
    public static void amplifyAncientTree(WorldGenLevel level, BlockPos origin,
                                          RandomSource random) {
        BlockPos surveyMin = origin.offset(-12, 0, -12);
        BlockPos surveyMax = origin.offset( 12, 36, 12);

        Map<Block, Integer> logCounts  = new HashMap<>();
        Map<Block, Integer> leafCounts = new HashMap<>();
        int topmostLogY = origin.getY();

        for (BlockPos cursor : BlockPos.betweenClosed(surveyMin, surveyMax)) {
            BlockState s = stateAt(level, cursor);
            if (s.is(BlockTags.LOGS)) {
                logCounts.merge(s.getBlock(), 1, Integer::sum);
                topmostLogY = Math.max(topmostLogY, cursor.getY());
            } else if (s.is(BlockTags.LEAVES) && !s.getValue(LeavesBlock.PERSISTENT)) {
                leafCounts.merge(s.getBlock(), 1, Integer::sum);
            }
        }

        if (logCounts.isEmpty() || leafCounts.isEmpty()) return;

        Block logType  = dominant(logCounts);
        Block leafType = dominant(leafCounts);
        int   fancyH   = topmostLogY - origin.getY();

        boolean isBirch    = logType == Blocks.BIRCH_LOG;
        boolean isConifer  = logType == Blocks.SPRUCE_LOG;
        boolean isJungle   = logType == Blocks.JUNGLE_LOG;
        boolean isAcacia   = logType == Blocks.ACACIA_LOG;
        boolean isDarkOak  = logType == Blocks.DARK_OAK_LOG;
        boolean isCherry   = logType == Blocks.CHERRY_LOG;
        boolean isPaleOak  = logType == Blocks.PALE_OAK_LOG;
        boolean isMangrove = logType == Blocks.MANGROVE_LOG;
        // Oak and azalea (oak logs) fall through to the oak branch.

        BlockState logState = logType.defaultBlockState();

        // ── Trunk thickening ────────────────────────────────────────────────
        // Lower 2/3 of original tree height → radius-2 circle (~5 wide).
        // Upper portion → radius 1 taper.  Birch and cherry stay radius 1.
        boolean slimTrunk = isBirch || isCherry;
        int thickenTo = origin.getY() + (fancyH * 2 / 3) + 3;

        for (int y = origin.getY(); y <= topmostLogY + 3; y++) {
            int r = (!slimTrunk && y <= thickenTo) ? 2 : 1;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz <= r * r) {
                        BlockPos lp = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
                        BlockState there = stateAt(level, lp);
                        if (there.isAir() || (there.is(BlockTags.LEAVES) && !there.getValue(LeavesBlock.PERSISTENT)))
                            setGuarded(level, lp, logState, 4);
                    }
                }
            }
        }

        // ── Trunk extension ──────────────────────────────────────────────────
        int extension = isJungle  ? 20
                      : isConifer ? 18
                      : isBirch   ? 6    // SUPER_BIRCH is already very tall; short ext avoids bare trunk
                      : isAcacia  ? 8   // acacia is short and wide, not tall
                      : isDarkOak ? 14
                      : isCherry  ? 12
                      : isPaleOak ? 14
                      : isMangrove ? 10
                      :             14; // oak default

        int extRadius = (isConifer || isJungle || isDarkOak) ? 2 : 1;
        BlockPos extTop = new BlockPos(origin.getX(), topmostLogY, origin.getZ());

        for (int i = 1; i <= extension; i++) {
            for (int dx = -extRadius; dx <= extRadius; dx++) {
                for (int dz = -extRadius; dz <= extRadius; dz++) {
                    if (dx * dx + dz * dz <= extRadius * extRadius) {
                        BlockPos lp = extTop.above(i).offset(dx, 0, dz);
                        if (isOpen(level, lp)) setGuarded(level, lp, logState, 4);
                    }
                }
            }
        }
        extTop = extTop.above(extension);

        BlockState crownLeaf = leafType.defaultBlockState()
            .setValue(LeavesBlock.DISTANCE,    6)
            .setValue(LeavesBlock.PERSISTENT,  false)
            .setValue(LeavesBlock.WATERLOGGED, false);

        int v = random.nextInt(2); // 0 or 1: per-tree size variation

        // ── Species-appropriate crown placement ──────────────────────────────

        if (isAcacia) {
            // Acacia is defined by its flat-topped umbrella silhouette, not a dome.
            // Profile builds wide and STAYS wide at the top (the "table" surface),
            // rather than narrowing off; bottom to top: narrow → wide → plateau.
            // Offset must clear the main crown's radius so the secondary umbrella is
            // visually distinct rather than subsumed.
            int offset = 9 + random.nextInt(3);  // 9-11: clears the radius-9 main crown
            Direction dir = HORIZONTALS[random.nextInt(4)];

            // Main: no gap above trunk (above(0)), flat plateau at top
            placeLoggedCluster(level, extTop.above(0),
                         new int[]{ 1, 3+v, 6+v, 8+v, 9+v, 9+v, 8+v }, crownLeaf, logState, random);
            // Secondary umbrella: lower and laterally displaced
            placeLoggedCluster(level, extTop.above(-3).relative(dir, offset),
                         new int[]{ 1, 2+v, 5+v, 7+v, 7+v, 6+v }, crownLeaf, logState, random);
            for (int i = 1; i <= offset; i++) {
                BlockPos b = extTop.above(-3).relative(dir, i);
                if (isOpen(level, b)) setGuarded(level, b, logState, 4);
            }

        } else if (isCherry) {
            // Multi-headed cloud canopy: start crown at trunk top (above(-1)) so
            // the trunk tip is nested inside the crown, not poking through it.
            // Two perpendicular secondary heads give the multi-direction blossom spread.
            int offset = 4 + random.nextInt(3);
            Direction dir1 = HORIZONTALS[random.nextInt(4)];
            Direction dir2 = dir1.getClockWise();

            placeLoggedCluster(level, extTop.above(-1),
                         new int[]{ 3+v, 5+v, 6+v, 6+v, 5+v, 3+v, 2 }, crownLeaf, logState, random);
            placeLoggedCluster(level, extTop.above(-1).relative(dir1, offset),
                         new int[]{ 2, 4+v, 5+v, 5+v, 3+v, 2 }, crownLeaf, logState, random);
            placeLoggedCluster(level, extTop.above(-2).relative(dir2, offset - 1),
                         new int[]{ 2, 3+v, 5+v, 4+v, 2 }, crownLeaf, logState, random);
            for (int i = 1; i <= offset;     i++) { BlockPos b = extTop.above(-1).relative(dir1, i); if (isOpen(level, b)) setGuarded(level, b, logState, 4); }
            for (int i = 1; i <= offset - 1; i++) { BlockPos b = extTop.above(-2).relative(dir2, i); if (isOpen(level, b)) setGuarded(level, b, logState, 4); }

        } else if (isDarkOak) {
            // Very dense multi-head canopy; crown starts at above(0) so the
            // trunk tip is inside the main crown rather than exposed above it.
            int offset = 5 + random.nextInt(3);
            Direction dir1 = HORIZONTALS[random.nextInt(4)];
            Direction dir2 = dir1.getOpposite();
            Direction dir3 = dir1.getClockWise();

            placeLoggedCluster(level, extTop.above(0),
                         new int[]{ 5+v, 7+v, 9+v, 9+v, 7+v, 5+v, 3 }, crownLeaf, logState, random);
            placeLoggedCluster(level, extTop.above(-1).relative(dir1, offset),
                         new int[]{ 4+v, 6+v, 7+v, 6+v, 4+v, 2 }, crownLeaf, logState, random);
            placeLoggedCluster(level, extTop.above(-2).relative(dir2, offset - 1),
                         new int[]{ 3+v, 5+v, 6+v, 5+v, 3+v, 2 }, crownLeaf, logState, random);
            placeLoggedCluster(level, extTop.above(-1).relative(dir3, offset),
                         new int[]{ 3, 5+v, 6+v, 5+v, 3 }, crownLeaf, logState, random);
            for (int i = 1; i <= offset;     i++) { BlockPos b = extTop.above(-1).relative(dir1, i); if (isOpen(level, b)) setGuarded(level, b, logState, 4); }
            for (int i = 1; i <= offset - 1; i++) { BlockPos b = extTop.above(-2).relative(dir2, i); if (isOpen(level, b)) setGuarded(level, b, logState, 4); }
            for (int i = 1; i <= offset;     i++) { BlockPos b = extTop.above(-1).relative(dir3, i); if (isOpen(level, b)) setGuarded(level, b, logState, 4); }

        } else if (isPaleOak) {
            // Eerie asymmetric spread: crown at above(0) so trunk doesn't poke
            // above the canopy edge.  Wide offsets exaggerate the unbalanced silhouette.
            int offset = 6 + random.nextInt(3);
            Direction dir1 = HORIZONTALS[random.nextInt(4)];
            Direction dir2 = dir1.getOpposite();

            placeLoggedCluster(level, extTop.above(0),
                         new int[]{ 4+v, 7+v, 8+v, 8+v, 6+v, 4+v, 2 }, crownLeaf, logState, random);
            placeLoggedCluster(level, extTop.above(0).relative(dir1, offset),
                         new int[]{ 3+v, 6+v, 7+v, 6+v, 4+v, 2 }, crownLeaf, logState, random);
            placeLoggedCluster(level, extTop.above(-2).relative(dir2, offset - 2),
                         new int[]{ 2, 4+v, 5+v, 5+v, 3 }, crownLeaf, logState, random);
            for (int i = 1; i <= offset;     i++) { BlockPos b = extTop.above(0).relative(dir1, i); if (isOpen(level, b)) setGuarded(level, b, logState, 4); }
            for (int i = 1; i <= offset - 2; i++) { BlockPos b = extTop.above(-2).relative(dir2, i); if (isOpen(level, b)) setGuarded(level, b, logState, 4); }

        } else if (isBirch) {
            // Crown wraps trunk top: starts 4 below extTop so no bare trunk visible.
            placeLoggedCluster(level, extTop.above(-4),
                         new int[]{ 1, 2, 3+v, 4+v, 4+v, 3+v, 3, 2, 1 }, crownLeaf, logState, random);

        } else if (isConifer) {
            // The cone begins directly above the MEGA_SPRUCE's original canopy top
            // and ascends through the full extension, so the trunk is always inside foliage.
            // Radius is capped at 6 so every leaf is within 6 hops of the trunk-log column.
            int baseR  = 6 + v;  // max 6 or 7, but cap below ensures ≤ 6
            int coneH  = extension + 3;
            int startY = -(extension - 1);
            for (int layer = 0; layer < coneH; layer++) {
                int r = Math.min(6, Math.max(1, baseR - layer / 3));
                placeDisc(level, extTop.above(startY + layer), r, crownLeaf, random);
            }

        } else if (isJungle) {
            // Two large canopies with a generous offset so they read as separate
            // emergent crowns rather than one merged blob.
            int offset = 7 + random.nextInt(3);  // 7-9: clears the radius-6 main crown
            placeLoggedCluster(level, extTop.above(0),
                         new int[]{ 4+v, 6+v, 6, 6, 6, 5+v, 3 }, crownLeaf, logState, random);
            placeLoggedCluster(level, extTop.above(-1).relative(HORIZONTALS[random.nextInt(4)], offset),
                         new int[]{ 3, 5+v, 6+v, 5+v, 3 }, crownLeaf, logState, random);

        } else if (isMangrove) {
            // Two laterally displaced crowns; stacking them vertically with no offset
            // produces a single merged blob.  A branch log connects to the secondary.
            int offset = 3 + random.nextInt(2);
            Direction dir = HORIZONTALS[random.nextInt(4)];

            placeLoggedCluster(level, extTop.above(0),
                         new int[]{ 3+v, 5+v, 5+v, 4+v, 2 }, crownLeaf, logState, random);
            placeLoggedCluster(level, extTop.above(-2).relative(dir, offset),
                         new int[]{ 2, 4+v, 4+v, 3+v, 2 }, crownLeaf, logState, random);
            for (int i = 1; i <= offset; i++) {
                BlockPos b = extTop.above(-2).relative(dir, i);
                if (isOpen(level, b)) setGuarded(level, b, logState, 4);
            }

        } else {
            // Oak (and azalea): three spreading heads.  Main at above(0) so the
            // log post connects directly to the trunk without a gap.
            int offset = 5 + random.nextInt(3);
            Direction dir1 = HORIZONTALS[random.nextInt(4)];
            Direction dir2 = dir1.getOpposite();

            placeLoggedCluster(level, extTop.above(0),
                         new int[]{ 4+v, 6, 6, 6, 5+v, 3+v, 2 }, crownLeaf, logState, random);
            placeLoggedCluster(level, extTop.above(-1).relative(dir1, offset),
                         new int[]{ 3+v, 5+v, 6+v, 5+v, 3+v, 2 }, crownLeaf, logState, random);
            placeLoggedCluster(level, extTop.above(-3).relative(dir2, offset - 2),
                         new int[]{ 3, 4+v, 5+v, 4+v, 3, 2 }, crownLeaf, logState, random);
            for (int i = 1; i <= offset;     i++) { BlockPos b = extTop.above(-1).relative(dir1, i); if (isOpen(level, b)) setGuarded(level, b, logState, 4); }
            for (int i = 1; i <= offset - 2; i++) { BlockPos b = extTop.above(-3).relative(dir2, i); if (isOpen(level, b)) setGuarded(level, b, logState, 4); }
        }
    }

    // ── Ancient halo ─────────────────────────────────────────────────────────

    private static List<Conversion> buildAncientHalo(LevelAccessor level,
                                                      List<Conversion> base) {
        List<Conversion> extensions = new ArrayList<>();
        Set<BlockPos>    occupied   = new HashSet<>();

        for (Conversion c : base) occupied.add(c.pos());

        for (Conversion c : base) {
            if (c.state().getValue(LeafStairsBlock.STAIRS_SHAPE) != StairsShape.STRAIGHT) continue;

            Direction outward = c.state().getValue(LeafStairsBlock.HORIZONTAL_FACING).getOpposite();
            BlockPos  extPos  = c.pos().relative(outward);

            if (!isOpen(level, extPos)) continue;
            if (!occupied.add(extPos)) continue;

            int dist = c.state().getValue(LeavesBlock.DISTANCE);
            if (dist >= 6) continue;

            extensions.add(new Conversion(extPos, c.state().setValue(LeavesBlock.DISTANCE, dist + 1)));
        }

        return extensions;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Places one horizontal disc layer of leaves at {@code centre}.
     * Outer-ring blocks (within the last pixel of radius {@code r}) are randomly
     * skipped ~35 % of the time, giving the jagged Minecraft leaf-edge appearance.
     */
    private static void placeDisc(LevelAccessor level, BlockPos centre, int r,
                                   BlockState leaf, RandomSource random) {
        int r2     = r * r;
        int inner2 = (r - 1) * (r - 1);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > r2) continue;
                if (d2 > inner2 && random.nextFloat() < 0.35f) continue; // sparse edge
                BlockPos p = centre.offset(dx, 0, dz);
                if (isOpen(level, p)) setGuarded(level, p, leaf, 4);
            }
        }
    }

    /**
     * Stacks one disc per {@code radii} entry upward from {@code base};
     * {@code radii[0]} is the bottom layer, so the array is the crown profile.
     */
    private static void placeCluster(LevelAccessor level, BlockPos base,
                                     int[] radii, BlockState leaf, RandomSource random) {
        for (int dy = 0; dy < radii.length; dy++) {
            if (radii[dy] <= 0) continue;
            placeDisc(level, base.above(dy), radii[dy], leaf, random);
        }
    }

    /**
     * Like {@link #placeCluster} but adds a vertical log post through the cluster
     * centre and caps every layer's radius at 6.
     *
     * <p>Leaf distance propagates outward from logs, and a leaf survives only when
     * it can reach a log within 6 hops. The central post is a permanent anchor
     * (logs never decay regardless of trunk connectivity), so every leaf within
     * horizontal radius 6 of the post is guaranteed distance &le; 6 and will not
     * decay. Capping radius at 6 enforces this.</p>
     */
    private static void placeLoggedCluster(LevelAccessor level, BlockPos base,
                                            int[] radii, BlockState leaf, BlockState log,
                                            RandomSource random) {
        // Vertical log post through the full cluster height.
        for (int dy = 0; dy < radii.length; dy++) {
            BlockPos lp    = base.above(dy);
            BlockState cur = stateAt(level, lp);
            if (cur.isAir() || (cur.is(BlockTags.LEAVES) && !cur.getValue(LeavesBlock.PERSISTENT))) {
                setGuarded(level, lp, log, 4);
            }
        }
        // Leaves: radius capped at 6 so no leaf is ever > 6 horizontal hops from the post.
        for (int dy = 0; dy < radii.length; dy++) {
            int r = Math.min(radii[dy], 6);
            if (r > 0) placeDisc(level, base.above(dy), r, leaf, random);
        }
    }

    private static Block dominant(Map<Block, Integer> counts) {
        return counts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    /**
     * Free for foliage. Positions outside the worldgen reach limit answer false rather than being
     * read: every caller treats false as "leave it alone", so the limit turns into "do not touch
     * another chunk's business" without any of them having to know about it.
     */
    /**
     * Reads a block, or reports air for anything outside the worldgen reach limit.
     *
     * <p>Air is the answer that makes every caller do nothing: surveys stop counting it, stair
     * detection stops converting it. Guarding here rather than at each of the call sites is what
     * makes the budget total - the class simply cannot see past its own boundary, so no future
     * radius or offset can wander over it.
     */
    private static BlockState stateAt(LevelAccessor level, BlockPos pos) {
        if (!AncientTrees.reachable(pos)) return Blocks.AIR.defaultBlockState();

        return level.getBlockState(pos);
    }

    private static boolean isOpen(LevelAccessor level, BlockPos pos) {
        if (!AncientTrees.reachable(pos)) return false;

        BlockState s = level.getBlockState(pos);
        return !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS);
    }

    /**
     * The one place this class writes to the world, so the reach limit only has to be enforced once.
     *
     * <p>Amplified crowns are assembled from offsets and radii scattered across a dozen species
     * branches - an acacia's secondary umbrella alone lands eleven out with a radius-eight cluster on
     * it - and auditing every one of those sums by hand is the kind of arithmetic that is right until
     * somebody adds a species. Funnelling the writes is what makes the bound hold regardless.
     */
    private static boolean setGuarded(LevelAccessor level, BlockPos pos, BlockState state, int flags) {
        if (!AncientTrees.reachable(pos)) return false;

        return level.setBlock(pos, state, flags);
    }
}
