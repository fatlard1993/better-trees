package justfatlard.better_trees.mixin;

import justfatlard.better_trees.AncientTrees;
import justfatlard.better_trees.LeafStairsProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dresses trees that are drawn into a structure template as plain blocks.
 *
 * <p>Most trees in the world are grown by a feature, and {@link TreeFeatureMixin} catches those.
 * A handful are not: {@code village/plains/town_centers/plains_meeting_point_3} carries its oak as
 * forty-nine leaf blocks in the template's own palette, so no feature ever runs and the tree stays
 * vanilla while every tree around it does not. It also keeps vanilla's random-tick decay, which
 * takes minutes where leaf stairs go in a tick, so cutting it reads as decay being broken.
 */
@Mixin(StructureTemplate.class)
public class StructureTemplateMixin {

    @Inject(method = "placeInWorld", at = @At("RETURN"))
    private void betterTrees$dressTemplateTrees(
        ServerLevelAccessor level, BlockPos offset, BlockPos pos, StructurePlaceSettings settings,
        RandomSource random, int flags, CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValueZ()) return;

        BoundingBox placed = ((StructureTemplate) (Object) this).getBoundingBox(settings, offset);
        BoundingBox region = intersect(placed, settings.getBoundingBox());
        if (region == null) return;

        // Structure pieces are written one chunk at a time, so a template straddling a boundary
        // arrives here half-placed with the rest still to come. Fencing the pass to the region that
        // was actually written keeps it from reading the void where those blocks will land: the
        // processor treats anything out of reach as closed, so a leaf on the seam is left plain
        // rather than turned into a stair facing a neighbour that does not exist yet.
        AncientTrees.limitReachTo(region);

        try {
            LeafStairsProcessor.processBox(level,
                new BlockPos(region.minX(), region.minY(), region.minZ()),
                new BlockPos(region.maxX(), region.maxY(), region.maxZ()), false);
        } finally {
            AncientTrees.clearReachLimit();
        }
    }

    /** Null when the boxes do not overlap, so nothing was written and there is nothing to dress. */
    private static BoundingBox intersect(BoundingBox a, BoundingBox b) {
        if (b == null) return a;

        int minX = Math.max(a.minX(), b.minX()), maxX = Math.min(a.maxX(), b.maxX());
        int minY = Math.max(a.minY(), b.minY()), maxY = Math.min(a.maxY(), b.maxY());
        int minZ = Math.max(a.minZ(), b.minZ()), maxZ = Math.min(a.maxZ(), b.maxZ());
        if (minX > maxX || minY > maxY || minZ > maxZ) return null;

        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
