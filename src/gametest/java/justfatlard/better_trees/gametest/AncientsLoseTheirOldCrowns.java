package justfatlard.better_trees.gametest;

import justfatlard.better_trees.AncientMushrooms;
import justfatlard.better_trees.LeafStairsBlock;
import justfatlard.better_trees.LeafStairsProcessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An ancient wears one crown, at the top. The tree or mushroom it grew from had a crown of its own,
 * and the ancient's goes on a trunk or stem many blocks taller, so the old one, left where it was,
 * hung round the foot of the ancient as a ring of leaves or cap. Grown here for each path an
 * ancient takes: an oak swapped for its fancy form, an azalea grown from the tree standing, and a
 * red mushroom, a brown one and a crimson fungus. None may keep anything of its old crown low down.
 */
public final class AncientsLoseTheirOldCrowns implements FabricClientGameTest {
	private static final int WIDTH = 1920;
	private static final int HEIGHT = 1080;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.runCommand("gamerule advance_time false");
			server.runCommand("time set 1000");
			server.runCommand("gamemode spectator @a");

			BlockPos origin = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			int x = origin.getX(), y = origin.getY(), z = origin.getZ() - 30;
			BlockPos oak = new BlockPos(x - 48, y, z);
			BlockPos azalea = new BlockPos(x - 24, y, z);
			BlockPos red = new BlockPos(x, y, z);
			BlockPos brown = new BlockPos(x + 24, y, z);
			BlockPos fungus = new BlockPos(x + 48, y, z);

			server.runCommand("place feature minecraft:oak %d %d %d".formatted(oak.getX(), oak.getY(), oak.getZ()));
			server.runCommand("place feature minecraft:azalea_tree %d %d %d".formatted(azalea.getX(), azalea.getY(), azalea.getZ()));
			server.runCommand("place feature minecraft:huge_red_mushroom %d %d %d".formatted(red.getX(), red.getY(), red.getZ()));
			server.runCommand("place feature minecraft:huge_brown_mushroom %d %d %d".formatted(brown.getX(), brown.getY(), brown.getZ()));
			server.runCommand("setblock %d %d %d minecraft:crimson_nylium".formatted(fungus.getX(), fungus.getY() - 1, fungus.getZ()));
			server.runCommand("place feature minecraft:crimson_fungus %d %d %d".formatted(fungus.getX(), fungus.getY(), fungus.getZ()));
			context.waitTicks(20);

			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				for (BlockPos tree : new BlockPos[] {oak, azalea}) {
					check(level.getBlockState(tree).is(BlockTags.LOGS), "no tree stood at " + tree + " to grow from");
					RandomSource random = RandomSource.create(20261004L);
					boolean grown = LeafStairsProcessor.placeAncientExtension(level, level.getChunkSource().getGenerator(), tree, random);
					check(grown, "the tree at " + tree + " did not go ancient");
					LeafStairsProcessor.process(level, tree, random, true);
				}
				AncientMushrooms.finish(level, RandomSource.create(1L), red,
					Blocks.MUSHROOM_STEM.defaultBlockState(), Blocks.RED_MUSHROOM_BLOCK.defaultBlockState(), null, true);
				AncientMushrooms.finish(level, RandomSource.create(2L), brown,
					Blocks.MUSHROOM_STEM.defaultBlockState(), Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState(), null, true);
				AncientMushrooms.finish(level, RandomSource.create(3L), fungus,
					Blocks.CRIMSON_STEM.defaultBlockState(), Blocks.NETHER_WART_BLOCK.defaultBlockState(),
					Blocks.SHROOMLIGHT.defaultBlockState(), true);
			});
			context.waitTicks(40);

			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				// A tree's crowns are all up top: nothing of leaf in the lower half of it.
				for (BlockPos tree : new BlockPos[] {oak, azalea}) {
					int top = highest(level, tree, 14, 60);
					int half = tree.getY() + (top - tree.getY()) / 2;
					BlockPos low = foliage(level, tree, 14, tree.getY() + 1, half);
					check(low == null, "the ancient at " + tree + " has leaves low on its trunk, at " + low
						+ " (the tree is " + (top - tree.getY()) + " tall): its old crown was left on it; "
						+ (low == null ? "" : level.getBlockState(low) + ", " + lowLeaves(level, tree, 14, half)));
				}
				// A mushroom's cap is a few layers at the very top: a red dome four, a brown plate two,
				// a fungus's pagoda six. Anything of cap below that is the old one.
				capOnlyAtTop(level, red, Blocks.MUSHROOM_STEM, 5);
				capOnlyAtTop(level, brown, Blocks.MUSHROOM_STEM, 3);
				capOnlyAtTop(level, fungus, Blocks.CRIMSON_STEM, 7);
			});

			look(server, x + 0.5, y + 20.0, z + 70.0, x, y + 20.0, z);
			context.waitTicks(80);
			context.takeScreenshot(TestScreenshotOptions.of("ancients-one-crown").withSize(WIDTH, HEIGHT).disableCounterPrefix());
			// Closer, for the two at the ends of the row, which are past where the far shot can see their feet.
			look(server, oak.getX() + 0.5, y + 8.0, oak.getZ() + 34.0, oak.getX(), y + 14.0, oak.getZ());
			context.waitTicks(60);
			context.takeScreenshot(TestScreenshotOptions.of("ancient-oak-one-crown").withSize(WIDTH, HEIGHT).disableCounterPrefix());
			look(server, fungus.getX() + 0.5, y + 8.0, fungus.getZ() + 28.0, fungus.getX(), y + 14.0, fungus.getZ());
			context.waitTicks(60);
			context.takeScreenshot(TestScreenshotOptions.of("ancient-fungus-one-crown").withSize(WIDTH, HEIGHT).disableCounterPrefix());
		}
	}

	private static void capOnlyAtTop(ServerLevel level, BlockPos at, Block stem, int layers) {
		int top = highest(level, at, 8, 60);
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-8, 1, -8), new BlockPos(at.getX() + 8, top - layers, at.getZ() + 8))) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.is(stem)) continue;
			throw new AssertionError("the ancient at " + at + " has " + state.getBlock() + " at " + pos
				+ ", below its cap at " + top + ": its old cap was left on the stem");
		}
	}

	/** The highest block above the ground around a position, which for an ancient is the top of its crown. */
	private static int highest(ServerLevel level, BlockPos at, int reach, int height) {
		int top = at.getY();
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-reach, 1, -reach), at.offset(reach, height, reach))) {
			if (!level.getBlockState(pos).isAir()) top = Math.max(top, pos.getY());
		}
		return top;
	}

	/** The first leaf or leaf stair between two heights round a tree, or null. */
	private static BlockPos foliage(ServerLevel level, BlockPos at, int reach, int fromY, int toY) {
		for (BlockPos pos : BlockPos.betweenClosed(
				new BlockPos(at.getX() - reach, fromY, at.getZ() - reach), new BlockPos(at.getX() + reach, toY, at.getZ() + reach))) {
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.LEAVES) || state.getBlock() instanceof LeafStairsBlock) return pos.immutable();
		}
		return null;
	}

	/** Every low leaf, for the report: where, and what distance it says it is from a log. */
	private static String lowLeaves(ServerLevel level, BlockPos at, int reach, int toY) {
		StringBuilder out = new StringBuilder();
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-reach, 1, -reach), new BlockPos(at.getX() + reach, toY, at.getZ() + reach))) {
			BlockState state = level.getBlockState(pos);
			if (!state.is(BlockTags.LEAVES)) continue;
			if (n++ < 12) out.append(pos.getX() - at.getX()).append(',').append(pos.getY() - at.getY()).append(',').append(pos.getZ() - at.getZ())
				.append(" d").append(state.getValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE))
				.append(state.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT) ? "p " : " ");
		}
		return n + " low leaves: " + out;
	}

	/** Stand the camera at one place and point it at another; its y is the feet. */
	private static void look(TestServerContext server, double x, double y, double z, double atX, double atY, double atZ) {
		double dx = atX - x, dy = atY - (y + 1.62), dz = atZ - z;
		double yaw = -Math.toDegrees(Math.atan2(dx, dz));
		double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		server.runCommand("tp @a %.2f %.2f %.2f %.1f %.1f".formatted(x, y, z, yaw, pitch));
	}

	private static void check(boolean ok, String complaint) {
		if (!ok) throw new AssertionError(complaint);
	}
}
