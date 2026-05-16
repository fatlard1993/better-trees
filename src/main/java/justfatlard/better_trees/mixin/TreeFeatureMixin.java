package justfatlard.better_trees.mixin;

import justfatlard.better_trees.AncientTrees;
import justfatlard.better_trees.LeafStairsProcessor;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TreeFeature.class)
public class TreeFeatureMixin {

    @Inject(method = "place", at = @At("RETURN"))
    private void betterleaves$postProcess(
        FeaturePlaceContext<TreeConfiguration> context,
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
                context.level(), context.origin(), context.random());

        } else if (context.random().nextFloat() < AncientTrees.WORLDGEN_ANCIENT_CHANCE) {
            // Worldgen path: swap the tree with its fancy variant and amplify inside
            // placeAncientExtension, then run the larger edge-stair scan below.
            if (AncientTrees.enterAncientPlacement()) {
                try {
                    ancient = LeafStairsProcessor.placeAncientExtension(
                        context.level(), context.chunkGenerator(),
                        context.origin(), context.random());
                } finally {
                    AncientTrees.exitAncientPlacement();
                }
            }
        }

        LeafStairsProcessor.process(context.level(), context.origin(), context.random(), ancient);
    }
}
