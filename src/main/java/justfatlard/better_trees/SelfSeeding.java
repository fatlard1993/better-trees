package justfatlard.better_trees;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A dropped sapling that lies on good ground for long enough takes root there.
 *
 * <p>Saplings fall out of trees by the thousand and are picked up by nobody. Left to themselves
 * they sit as items until they despawn, which is the one thing a seed does not do. This gives each
 * one a single chance to do what it is for.
 *
 * <p>Deliberately a chance rather than a certainty. A forest floor carpeted in every sapling that
 * ever fell is a forest nobody can walk through, and the ones that fail still despawn the way they
 * always did.
 */
public final class SelfSeeding {
	private SelfSeeding() {}

	/**
	 * How often a settled seed takes.
	 *
	 * <p>Rolled once per item, not once per tick: a repeated roll of any size is a certainty with
	 * extra steps, and the point is that some of them do not make it.
	 */
	private static final float TAKES_ROOT = 0.6F;

	/**
	 * How long it has to lie still first.
	 *
	 * <p>Long enough that a sapling knocked loose is still in the air, and that anything a player
	 * dropped on purpose can be picked back up before the ground claims it.
	 */
	public static final int SETTLE_TICKS = 100;

	/** Vanilla's bonemeal puff, which is the growth this is standing in for. */
	private static final int GROWTH_PARTICLES = 2005;

	/** @return true where the item planted itself and the stack was drawn down */
	public static boolean tryTakeRoot(ServerLevel level, ItemEntity item) {
		ItemStack stack = item.getItem();
		if (!(stack.getItem() instanceof BlockItem blockItem)) return false;

		BlockState plant = blockItem.getBlock().defaultBlockState();
		if (!isPlant(plant)) return false;

		BlockPos pos = item.blockPosition();
		BlockState here = level.getBlockState(pos);
		if (!here.isAir() && !here.canBeReplaced()) return false;

		// The plant's own answer about whether it can live here, which covers the ground beneath it
		// as well as the space it needs. Asking it is what keeps this from planting a sapling on
		// stone or a lily pad on grass.
		if (!plant.canSurvive(level, pos)) return false;

		if (level.getRandom().nextFloat() >= TAKES_ROOT) return false;

		level.setBlockAndUpdate(pos, plant);
		level.levelEvent(GROWTH_PARTICLES, pos, 0);

		stack.shrink(1);
		if (stack.isEmpty()) item.discard();
		else item.setItem(stack);

		return true;
	}

	/**
	 * Whether this is something that grows.
	 *
	 * <p>Both bases are named because the hierarchy has been split: saplings and flowers sit under
	 * {@link VegetationBlock} now, while {@link BushBlock} is still the parent of a handful of
	 * others. Testing the pair covers plants from either side without naming any of them.
	 */
	private static boolean isPlant(BlockState state) {
		return state.getBlock() instanceof VegetationBlock || state.getBlock() instanceof BushBlock;
	}
}
