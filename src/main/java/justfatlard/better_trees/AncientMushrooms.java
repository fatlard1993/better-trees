package justfatlard.better_trees;

import net.minecraft.core.BlockPos;
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
			LeafStairsProcessor.process(level, origin, random, ancient);
		} finally {
			if (worldgen) AncientTrees.clearReachLimit();
		}
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

	/** The outline of a circle at a height: a cap's hanging rim. */
	private static void ring(WorldGenLevel level, BlockPos centre, int r, BlockState cap) {
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 > r * r || d2 <= (r - 1) * (r - 1)) continue;
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
