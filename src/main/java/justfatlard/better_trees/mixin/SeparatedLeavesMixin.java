package justfatlard.better_trees.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import justfatlard.better_trees.LeafKinship;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Leaves hold on to their own wood, and nothing else's.
 *
 * <p>Wrapped here rather than at {@code getDistanceAt} itself, because that method is handed only
 * the neighbour and the question needs both: which leaf is asking, and what it is asking about.
 * {@code updateDistance} has the leaf in hand, so the neighbour's answer can be refused from
 * inside it.
 *
 * <p>Refusing means answering {@code DECAY_DISTANCE}, which is what vanilla already returns for a
 * block that is no help - a stone wall, say. A log of the wrong species now reads as exactly that,
 * which is the whole change, and it needs no new state and no second pass over the canopy.
 *
 * <p>Applies to this mod's leaf stairs for free: they are {@link LeavesBlock}s, and their names
 * carry their species the same way vanilla's do.
 */
@Mixin(LeavesBlock.class)
public class SeparatedLeavesMixin {

	@WrapOperation(
		method = "updateDistance",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/LeavesBlock;getDistanceAt(Lnet/minecraft/world/level/block/state/BlockState;)I"))
	private static int betterTrees$ownWoodOnly(BlockState neighbour, Operation<Integer> original,
			@Local(argsOnly = true) BlockState leaf) {
		if (!LeafKinship.keeps(leaf, neighbour)) return LeavesBlock.DECAY_DISTANCE;

		return original.call(neighbour);
	}
}
