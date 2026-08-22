package justfatlard.better_trees;

import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.IdentityHashMap;
import java.util.Map;

public class AncientTrees {

    /** Fraction of qualifying saplings that grow into an ancient giant. */
    public static final float ANCIENT_CHANCE = 0.01f;

    /** Fraction of worldgen trees replaced with their fancy/mega variant + amplified. */
    public static final float WORLDGEN_ANCIENT_CHANCE = 0.005f;

    // ── Sapling redirect ─────────────────────────────────────────────────────
    // Species with a larger vanilla variant to swap in. Anything absent from this map is not
    // excluded from ancient growth: it is amplified where it stands instead, which is what poplar
    // wants, since one grower there picks between three leaf colours and naming a feature would pin
    // every ancient poplar to one of them.

    public static final Map<TreeGrower, ResourceKey<Feature>> FANCY_VARIANTS;
    static {
        IdentityHashMap<TreeGrower, ResourceKey<Feature>> m = new IdentityHashMap<>();
        m.put(TreeGrower.OAK,      TreeFeatures.FANCY_OAK);
        m.put(TreeGrower.BIRCH,    TreeFeatures.SUPER_BIRCH_BEES_0002);
        m.put(TreeGrower.SPRUCE,   TreeFeatures.MEGA_SPRUCE);
        m.put(TreeGrower.JUNGLE,   TreeFeatures.MEGA_JUNGLE_TREE);
        m.put(TreeGrower.MANGROVE, TreeFeatures.TALL_MANGROVE);
        m.put(TreeGrower.ACACIA,   TreeFeatures.ACACIA);          // no larger variant; amplification does the work
        m.put(TreeGrower.CHERRY,   TreeFeatures.CHERRY);          // no larger variant
        m.put(TreeGrower.DARK_OAK, TreeFeatures.DARK_OAK);        // already the mega form
        m.put(TreeGrower.PALE_OAK, TreeFeatures.PALE_OAK_BONEMEAL);
        m.put(TreeGrower.AZALEA,   TreeFeatures.AZALEA_TREE);     // uses oak logs; gets oak amplification
        FANCY_VARIANTS = m;
    }

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

    // ── Runtime flags ────────────────────────────────────────────────────────

    private static final ThreadLocal<Boolean> PENDING =
        ThreadLocal.withInitial(() -> false);

    private static final ThreadLocal<Boolean> IN_ANCIENT_PLACEMENT =
        ThreadLocal.withInitial(() -> false);

    public static void markAncient()    { PENDING.set(true); }
    public static boolean consumeAncient() {
        boolean v = PENDING.get();
        if (v) PENDING.set(false);
        return v;
    }

    public static boolean enterAncientPlacement() {
        if (IN_ANCIENT_PLACEMENT.get()) return false;
        IN_ANCIENT_PLACEMENT.set(true);
        return true;
    }
    public static void exitAncientPlacement()      { IN_ANCIENT_PLACEMENT.set(false); }
    public static boolean isInAncientPlacement()   { return IN_ANCIENT_PLACEMENT.get(); }
}
