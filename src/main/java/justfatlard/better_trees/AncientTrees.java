package justfatlard.better_trees;

import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import java.util.IdentityHashMap;
import java.util.Map;

public class AncientTrees {

    /** Fraction of qualifying saplings that grow into an ancient giant. */
    public static final float ANCIENT_CHANCE = 0.01f;

    /** Fraction of worldgen trees replaced with their fancy/mega variant + amplified. */
    public static final float WORLDGEN_ANCIENT_CHANCE = 0.005f;

    // ── Sapling redirect ─────────────────────────────────────────────────────
    // All 9 TreeGrower singletons are covered.
    // Species without a larger vanilla variant map to their own feature —
    // amplifyAncientTree handles the size increase regardless.

    public static final Map<TreeGrower, ResourceKey<ConfiguredFeature<?, ?>>> FANCY_VARIANTS;
    static {
        IdentityHashMap<TreeGrower, ResourceKey<ConfiguredFeature<?, ?>>> m = new IdentityHashMap<>();
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

    public static final Map<Block, ResourceKey<ConfiguredFeature<?, ?>>> LOG_TO_FANCY;
    static {
        Map<Block, ResourceKey<ConfiguredFeature<?, ?>>> m = new java.util.HashMap<>();
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
