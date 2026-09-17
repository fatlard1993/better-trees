package justfatlard.better_trees;

import net.minecraft.core.BlockPos;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AncientTrees {


    /** Fraction of worldgen trees replaced with their fancy/mega variant + amplified. */
    public static final float WORLDGEN_ANCIENT_CHANCE = 0.005f;

    // ── Worldgen detection ───────────────────────────────────────────────────
    // Maps the dominant log type of a placed worldgen tree to its fancy feature.
    // Azalea trees use OAK_LOG so they are handled by the OAK entry automatically.

    public static final Map<Block, ResourceKey<Feature>> LOG_TO_FANCY;
    static {
        Map<Block, ResourceKey<Feature>> m = new java.util.HashMap<>();
        m.put(Blocks.OAK_LOG,      TreeFeatures.FANCY_OAK);
        m.put(Blocks.BIRCH_LOG,    TreeFeatures.SUPER_BIRCH_BEES_0002);
        m.put(Blocks.SPRUCE_LOG,   TreeFeatures.MEGA_SPRUCE);
        m.put(Blocks.JUNGLE_LOG,   TreeFeatures.MEGA_JUNGLE_TREE);
        m.put(Blocks.MANGROVE_LOG, TreeFeatures.TALL_MANGROVE);
        m.put(Blocks.ACACIA_LOG,   TreeFeatures.ACACIA);
        m.put(Blocks.CHERRY_LOG,   TreeFeatures.CHERRY);
        m.put(Blocks.DARK_OAK_LOG, TreeFeatures.DARK_OAK);
        m.put(Blocks.PALE_OAK_LOG, TreeFeatures.PALE_OAK_BONEMEAL);
        LOG_TO_FANCY = m;
    }

    // ── Worldgen reach limit ─────────────────────────────────────────────────

    /**
     * Furthest, horizontally, that tree post-processing may touch from the tree's own origin
     * while a chunk is being generated.
     *
     * <p>During the features step a feature owns its chunk and the ring around it - chunks
     * {@code C-1} through {@code C+1}, which is blocks {@code 16C-16} through {@code 16C+31}. A tree
     * can be planted anywhere in its chunk, so the only offset that is safe wherever it lands is
     * sixteen. Reading past that touches a chunk that has not finished its own generation yet, which
     * the game reports as an unsafe terrain read: worldgen stops being deterministic, and in the bad
     * case it deadlocks.
     *
     * <p>Ancient trees were overrunning it in two places - the halo extends one block past a scan box
     * already sized at exactly sixteen, and an acacia's secondary umbrella sits up to eleven out with
     * a radius-eight crown on it, so nineteen.
     */
    public static final int WORLDGEN_REACH = 16;

    /** Far enough out to be no bound at all; a tree's limit is horizontal only. */
    private static final int NO_VERTICAL_LIMIT = 30_000_000;

    /**
     * Region the current context may read or write, or null when nothing needs restraining.
     *
     * <p>Null is the normal case for a sapling grown on a live server: every chunk it could reach is
     * already loaded. The two worldgen callers each have their own shape of limit - a tree gets a
     * square around its trunk, a structure template gets the box its placement was clipped to - so
     * this holds the region itself rather than an origin to measure from.
     */
    private static final ThreadLocal<BoundingBox> ALLOWED_REGION = new ThreadLocal<>();

    /** Limits reach to {@link #WORLDGEN_REACH} blocks horizontally around a tree's origin. */
    public static void limitReachTo(net.minecraft.core.BlockPos origin) {
        ALLOWED_REGION.set(new BoundingBox(
            origin.getX() - WORLDGEN_REACH, -NO_VERTICAL_LIMIT, origin.getZ() - WORLDGEN_REACH,
            origin.getX() + WORLDGEN_REACH,  NO_VERTICAL_LIMIT, origin.getZ() + WORLDGEN_REACH));
    }

    /** Limits reach to an explicit region, for callers that already know their own bounds. */
    public static void limitReachTo(BoundingBox region) { ALLOWED_REGION.set(region); }

    public static void clearReachLimit() { ALLOWED_REGION.remove(); }

    /** Whether the current context is allowed to read or write at this position. */
    public static boolean reachable(net.minecraft.core.BlockPos pos) {
        BoundingBox region = ALLOWED_REGION.get();

        return region == null || region.isInside(pos);
    }

    // ── What was written, for the clients that were not told ─────────────────

    /**
     * Where the amplifier has written during this placement, when anybody is collecting.
     *
     * <p>Worldgen writes through a {@code WorldGenRegion}, which puts blocks into whichever chunk
     * owns them and tells nobody: it has no players to tell. That is right for the chunk being
     * generated, which nobody has seen yet, and wrong for its neighbours, which may have been sent
     * to somebody standing there - an ancient reaches sixteen blocks out and sixty up, so it spills
     * into them routinely. Those blocks are in the world and absent from the screen until the chunk
     * reloads, which is the hole fatlard saw on the server.
     *
     * <p>So the positions are kept, and {@link #tellClients} hands them to the server thread to
     * re-broadcast once the placement is done. A chunk nobody is tracking ignores the lot.
     */
    private static final ThreadLocal<List<BlockPos>> WRITTEN = new ThreadLocal<>();

    /** Start keeping a note of what gets written, for a placement that is about to happen. */
    public static void collectWrites() {
        WRITTEN.set(new ArrayList<>());
    }

    /** Note one write; does nothing unless somebody is collecting. */
    public static void wrote(BlockPos pos) {
        List<BlockPos> written = WRITTEN.get();
        if (written != null) written.add(pos.immutable());
    }

    /**
     * Tell the clients about everything written since {@link #collectWrites}, and stop collecting.
     *
     * <p>Scheduled onto the server thread rather than done here: worldgen runs on a worker, and the
     * chunk map is the server thread's.
     */
    public static void tellClients(LevelAccessor level) {
        List<BlockPos> written = WRITTEN.get();
        WRITTEN.remove();
        if (written == null || written.isEmpty()) return;
        if (!(level instanceof ServerLevelAccessor access)) return;

        ServerLevel server = access.getLevel();
        server.getServer().execute(() -> {
            ServerChunkCache chunks = server.getChunkSource();
            for (BlockPos pos : written) chunks.blockChanged(pos);
        });
    }

    // ── Runtime flags ────────────────────────────────────────────────────────

    private static final ThreadLocal<Boolean> IN_ANCIENT_PLACEMENT =
        ThreadLocal.withInitial(() -> false);

    public static boolean enterAncientPlacement() {
        if (IN_ANCIENT_PLACEMENT.get()) return false;
        IN_ANCIENT_PLACEMENT.set(true);
        return true;
    }
    public static void exitAncientPlacement()      { IN_ANCIENT_PLACEMENT.set(false); }
    public static boolean isInAncientPlacement()   { return IN_ANCIENT_PLACEMENT.get(); }
}
