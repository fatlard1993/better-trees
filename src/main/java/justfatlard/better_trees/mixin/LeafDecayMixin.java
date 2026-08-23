package justfatlard.better_trees.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cut leaves fall now, rather than a minute from now.
 *
 * <p>Vanilla splits leaf decay across two clocks. The scheduled tick is prompt and only
 * recomputes {@code DISTANCE}; the block is not actually dropped until a <em>random</em> tick
 * finds it already out of range, and a random tick reaches a given block about once a minute.
 * So a felled tree leaves its whole crown hanging in the air, thinning at random, long after
 * the trunk is in the player's hands.
 *
 * <p>This does the drop on the scheduled tick instead, on the same condition vanilla uses and
 * with the same two calls. Removing a leaf notifies its neighbours, which schedules their tick,
 * so the canopy comes down one ring per tick from where the log was - fast enough to read as
 * part of felling the tree rather than as weather.
 *
 * <p>Deliberately on {@link LeavesBlock} rather than on this mod's stair-shaped leaves alone.
 * The stairs are the outer shell of a crown whose core is vanilla leaves; dropping only the
 * shell strips the silhouette and leaves the middle floating, which is worse than either doing
 * it slowly or doing it all at once. Player-placed leaves are unaffected: they are PERSISTENT,
 * and {@code decaying} already says no to those.
 */
@Mixin(LeavesBlock.class)
public abstract class LeafDecayMixin {

	@Shadow
	protected abstract boolean decaying(BlockState state);

	@Inject(method = "tick", at = @At("TAIL"), require = 1)
	private void betterTrees$dropOnceOutOfRange(BlockState state, ServerLevel level, BlockPos pos,
			RandomSource random, CallbackInfo info) {
		// The tick above has just written the new distance, so ask the world rather than the
		// state this was called with.
		BlockState settled = level.getBlockState(pos);
		if (!(settled.getBlock() instanceof LeavesBlock) || !decaying(settled)) return;

		Block.dropResources(settled, level, pos);
		level.removeBlock(pos, false);
	}
}
