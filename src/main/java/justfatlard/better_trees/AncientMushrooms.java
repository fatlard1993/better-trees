package justfatlard.better_trees;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What happens to a huge mushroom or fungus once the game has grown it: its cap is finished
 * with stairs the way a tree's crown is, and during worldgen, at the same odds as a tree, it
 * comes up ancient.
 *
 * <p>An ancient red mushroom is a thick stem twice the height under a domed cap wider than any
 * the game grows; a brown one a plate the width of a house on a stem you could not reach round.
 * An ancient fungus is the nether's landmark: a stem a dozen blocks taller than the tallest the
 * game makes, under a cap of wart that steps out and back in like a pagoda roof, lit through
 * with shroomlight. None of it is a new block. It is the game's own blocks, more of them.
 */
public final class AncientMushrooms {
	private AncientMushrooms() {}

	/** Called after a huge mushroom's cap, or a huge fungus, has been placed at {@code origin}. */
	public static void after(WorldGenLevel level, RandomSource random, BlockPos origin,
			BlockState stem, BlockState cap, BlockState glow) {
		boolean worldgen = level instanceof WorldGenRegion;
		if (worldgen) AncientTrees.limitReachTo(origin);
		try {
			boolean ancient = worldgen && random.nextFloat() < AncientTrees.WORLDGEN_ANCIENT_CHANCE;
			if (ancient) amplify(level, random, origin, stem, cap, glow);
			closeCorners(level, origin, cap, ancient ? 16 : 8, ancient ? 65 : 16);
			LeafStairsProcessor.process(level, origin, random, ancient);
		} finally {
			if (worldgen) AncientTrees.clearReachLimit();
		}
	}

	/**
	 * Fill the pinch where a cap turns a corner diagonally.
	 *
	 * <p>A huge mushroom's hanging skirt is a ring with its corners cut, which is the game's own
	 * shape and has always had a slot at each corner where two arms of the ring meet at a point.
	 * Nothing here made those; what this mod did was smooth everything around them, and a gap that
	 * read as blockiness among blocky things reads as a hole once its neighbours are bevelled. You
	 * can see the stem through the corner of a red one.
	 *
	 * <p>A cell is a pinch when it is empty, two of its four sides are cap, those two are at right
	 * angles, and the cell catty-corner between them is empty too. The last clause is the whole
	 * difference between a slot and a step: every rasterised circle turns in stairs, and each of
	 * those steps also has two cap sides meeting at a right angle - but with the circle's own
	 * filling behind it, so there is nothing to see through. Without that clause this squared off
	 * the outline of every ancient cap, which is the exact opposite of the job.
	 *
	 * <p>Filling it closes the slot and leaves the silhouette otherwise as the game drew it. Run
	 * before the stairs, so the corner gets bevelled along with everything else rather than
	 * standing square among them.
	 */
	private static void closeCorners(WorldGenLevel level, BlockPos origin, BlockState cap,
			int reach, int height) {
		List<BlockPos> pinches = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(
				origin.offset(-reach, 0, -reach), origin.offset(reach, height, reach))) {
			if (!level.getBlockState(pos).isAir()) continue;

			boolean north = level.getBlockState(pos.north()).is(cap.getBlock());
			boolean south = level.getBlockState(pos.south()).is(cap.getBlock());
			boolean east = level.getBlockState(pos.east()).is(cap.getBlock());
			boolean west = level.getBlockState(pos.west()).is(cap.getBlock());
			if (!(north || south) || !(east || west) || north == south || east == west) continue;

			BlockPos across = (north ? pos.north() : pos.south()).relative(east ? Direction.EAST : Direction.WEST);
			if (level.getBlockState(across).is(cap.getBlock())) continue;

			pinches.add(pos.immutable());
		}
		// Collected first: filling as it goes would let one corner make another out of the block it
		// just placed, and a mushroom would grow square corners outward a ring at a time.
		for (BlockPos pos : pinches) put(level, pos, cap, cap);
	}

	private static void amplify(WorldGenLevel level, RandomSource random, BlockPos origin,
			BlockState stem, BlockState cap, BlockState glow) {
		// The stem as it stands: from the ground to its last block.
		BlockPos top = origin;
		while (level.getBlockState(top.above()).is(stem.getBlock()) && top.getY() - origin.getY() < 40) top = top.above();
		int height = top.getY() - origin.getY() + 1;

		// Thickened to three across the whole way, and carried on up.
		int extension = glow != null ? 10 + random.nextInt(4) : 7 + random.nextInt(4);
		for (int y = 0; y < height + extension; y++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					put(level, origin.offset(dx, y, dz), stem, cap);
				}
			}
		}
		BlockPos crown = origin.above(height + extension - 1);

		if (glow != null) {
			// A fungus: a pagoda of wart, each storey a disc stepping out then in, with a
			// shroomlight here and there in the rim of every storey.
			int[] radii = {4, 6, 6, 5, 3, 1};
			for (int i = 0; i < radii.length; i++) disc(level, random, crown.above(i), radii[i], cap, glow, 0.12F);
		} else if (cap.is(Blocks.BROWN_MUSHROOM_BLOCK)) {
			// A brown one: a plate the width of a house, a block thick, with a rim hanging
			// one below at its edge, the shape the game gives it grown up.
			ring(level, crown, 7, cap);
			disc(level, random, crown.above(), 7, cap, null, 0);
		} else {
			// A red one: a dome, its rim hanging at the widest, closing to a crown on top.
			ring(level, crown.above(-1), 6, cap);
			disc(level, random, crown, 6, cap, null, 0);
			disc(level, random, crown.above(1), 5, cap, null, 0);
			disc(level, random, crown.above(2), 3, cap, null, 0);
		}
	}

	/** A filled circle at a height; the rim of a fungus's storey gets its share of glow. */
	private static void disc(WorldGenLevel level, RandomSource random, BlockPos centre, int r,
			BlockState cap, BlockState glow, float glowChance) {
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 > r * r) continue;
				boolean rim = d2 > (r - 1) * (r - 1);
				BlockState block = glow != null && rim && random.nextFloat() < glowChance ? glow : cap;
				put(level, centre.offset(dx, 0, dz), block, cap);
			}
		}
	}

	/**
	 * How wide the hanging rim is drawn, as a radius.
	 *
	 * <p>Not one. A band one wide between two rasterised circles is not a ring at all: where the
	 * outline runs at forty-five degrees the two circles land on the same cells and the band
	 * pinches out, so an r=6 rim came out as arcs joined at the corners, with a clean hole at the
	 * top of each one. Every block in it hangs below the widest disc with nothing beside it, which
	 * is exactly where a hole is easiest to see and hardest to explain.
	 *
	 * <p>One and a half closes it: the band never pinches below one cell, the ring is orthogonally
	 * connected the whole way round, and a rim that reads as a thick lip is what a mushroom has
	 * anyway.
	 */
	private static final double RIM_WIDTH = 1.5;

	/** The outline of a circle at a height: a cap's hanging rim. */
	private static void ring(WorldGenLevel level, BlockPos centre, int r, BlockState cap) {
		double inner = (r - RIM_WIDTH) * (r - RIM_WIDTH);
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 > r * r || d2 <= inner) continue;
				put(level, centre.offset(dx, 0, dz), cap, cap);
			}
		}
	}

	/** Set a block where there is air, or the mushroom's own cap, and never past the worldgen fence. */
	private static void put(WorldGenLevel level, BlockPos pos, BlockState block, BlockState cap) {
		if (!AncientTrees.reachable(pos)) return;
		BlockState there = level.getBlockState(pos);
		if (!there.isAir() && !there.is(cap.getBlock()) && !there.is(net.minecraft.tags.BlockTags.REPLACEABLE)) return;
		level.setBlock(pos, block, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}
}
