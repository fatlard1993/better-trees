package justfatlard.better_trees.mixin;

import justfatlard.better_trees.AncientTrees;
import justfatlard.better_trees.LeafStairsProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TreeFeature.class)
public class TreeFeatureMixin {

    @Inject(method = "place", at = @At("RETURN"))
    private void betterleaves$postProcess(
        WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos pos,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValueZ()) return;

        // Skip inner firings while placeAncientExtension is placing a fancy feature.
        // The outer call handles all amplification + edge-stair processing.
        if (AncientTrees.isInAncientPlacement()) return;

        boolean ancient = AncientTrees.consumeAncient(); // true for sapling-grown fancy variants

        if (ancient) {
            // Sapling path: amplify BEFORE running edge-stair processing so the
            // new trunk/crown leaves are included in the stair scan.
            LeafStairsProcessor.amplifyAncientTree(
                level, pos, random);

        } else if (random.nextFloat() < AncientTrees.WORLDGEN_ANCIENT_CHANCE) {
            // Worldgen path: swap the tree with its fancy variant and amplify inside
            // placeAncientExtension, then run the larger edge-stair scan below.
            if (AncientTrees.enterAncientPlacement()) {
                try {
                    ancient = LeafStairsProcessor.placeAncientExtension(
                        level, chunkGenerator,
                        pos, random);
                } finally {
                    AncientTrees.exitAncientPlacement();
                }
            }
        }

        LeafStairsProcessor.process(level, pos, random, ancient);
    }
}
