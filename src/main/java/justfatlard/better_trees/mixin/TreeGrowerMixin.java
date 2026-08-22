package justfatlard.better_trees.mixin;

import justfatlard.better_trees.AncientTrees;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Intercepts sapling growth for species that have a fancy/mega variant.
 * On the ancient roll, replaces the normal feature with the fancy one.
 *
 * The ancient flag is set BEFORE calling place() so that TreeFeatureMixin,
 * which fires nested inside that call at TreeFeature.place() RETURN, sees it.
 */
@Mixin(TreeGrower.class)
public class TreeGrowerMixin {

    @Inject(method = "growTree", at = @At("HEAD"), cancellable = true)
    private void betterleaves$maybeGrowAncient(
        ServerLevel level, ChunkGenerator chunkGenerator, BlockPos pos,
        BlockState state, RandomSource random,
        CallbackInfoReturnable<Boolean> cir
    ) {
        ResourceKey<Feature> fancyKey =
            AncientTrees.FANCY_VARIANTS.get((TreeGrower) (Object) this);

        if (random.nextFloat() >= AncientTrees.ANCIENT_CHANCE) return;

        // No bigger variant for this species: mark it and get out of the way. Vanilla grows whatever
        // the sapling would have grown, and TreeFeatureMixin amplifies it on the way out. Poplar
        // needs this - one grower picks between three leaf colours, and naming a feature here would
        // pin every ancient poplar to whichever colour was named.
        if (fancyKey == null) {
            AncientTrees.markAncient();
            return;
        }

        Optional<Holder.Reference<Feature>> feature = level.registryAccess()
            .lookupOrThrow(Registries.FEATURE)
            .get(fancyKey);

        if (feature.isEmpty()) return;

        // Mark BEFORE place(): TreeFeatureMixin fires inside this call and must see the flag.
        AncientTrees.markAncient();

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 4);

        if (feature.get().value().place(level, chunkGenerator, random, pos)) {
            cir.setReturnValue(true);
        } else {
            // Feature failed: undo the mark and restore the sapling.
            AncientTrees.consumeAncient();
            level.setBlock(pos, state, 4);
            cir.setReturnValue(false);
        }
    }
}
