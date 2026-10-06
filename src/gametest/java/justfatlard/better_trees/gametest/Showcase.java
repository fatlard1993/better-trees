package justfatlard.better_trees.gametest;

import justfatlard.better_trees.AncientMushrooms;
import justfatlard.better_trees.LeafStairsProcessor;
import justfatlard.pandorical.gametest.Pictures;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The pictures for the readme and the mod page, in a generated world: a forest as this mod grows
 * it, an ancient oak, and huge mushrooms, one of them ancient.
 *
 * <p>The forest is the mod's own worldgen, found by its biome: canopies that taper off rather than
 * ending in a flat green cube are a thing only a real forest shows. An ancient is a one in two
 * hundred roll during worldgen, so waiting for one is not a plan: the test calls the same
 * {@code placeAncientExtension} the roll calls, on an oak placed the way worldgen places one.
 */
public final class Showcase implements FabricClientGameTest {
	private static final long SEED = 20261005L;
	/** What a tree or a huge mushroom will grow on. */
	private static final java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> SOIL =
		state -> state.is(net.minecraft.tags.BlockTags.DIRT) || state.is(Blocks.GRASS_BLOCK);

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = Pictures.world(context, SEED)) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			Pictures.stage(context, server, Pictures.MORNING);

			BlockPos spawn = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());

			// A forest as it generates, from above its canopy: the canopies are the mod.
			BlockPos forest = server.computeOnServer(s -> Pictures.near(s.overworld(), spawn, "minecraft:forest"));
			check(forest != null, "no forest near spawn on seed " + SEED);
			arrive(context, server, connection, forest);
			Vec3 crowns = server.computeOnServer(s -> {
				int top = Integer.MIN_VALUE;
				for (int dx = -8; dx <= 8; dx += 4) for (int dz = -8; dz <= 8; dz += 4)
					top = Math.max(top, Pictures.canopy(s.overworld(), forest.getX() + dx, forest.getZ() + dz).getY());
				return new Vec3(forest.getX() + 0.5, top - 2, forest.getZ() + 0.5);
			});
			frame(server, crowns, 28, 12, 135);
			Pictures.shoot(context, connection, "forest");

			// An ancient oak, out on dry open ground, and huge mushrooms beside it.
			// Level open plains, so the oak stands alone and the mushrooms have room.
			BlockPos plains = server.computeOnServer(s -> Pictures.flattest(s.overworld(), spawn, "minecraft:plains", 800, 64));
			check(plains != null, "no plains near spawn on seed " + SEED);
			arrive(context, server, connection, plains);
			BlockPos oak = server.computeOnServer(s -> Pictures.dryGround(s.overworld(), plains.getX(), plains.getZ(), 24, SOIL));
			check(oak != null, "no dry ground in the plains at " + plains);
			// The stage, cleared of the plains' own trees: the oak and the mushrooms beside it, and
			// the ground between them and the camera.
			server.runOnServer(s -> Pictures.clearAround(s.overworld(), oak.offset(-12, 0, 0), 34, 40));
			server.runCommand("place feature minecraft:oak %d %d %d".formatted(oak.getX(), oak.getY(), oak.getZ()));
			context.waitTicks(10);
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				RandomSource random = RandomSource.create(SEED);
				boolean grown = LeafStairsProcessor.placeAncientExtension(level, level.getChunkSource().getGenerator(), oak, random);
				check(grown, "the oak did not go ancient");
				LeafStairsProcessor.process(level, oak, random, true);
			});
			frame(server, Vec3.atBottomCenterOf(oak).add(0, 16, 0), 30, -8, 150);
			Pictures.shoot(context, connection, "ancient");

			BlockPos red = server.computeOnServer(s -> Pictures.dryGround(s.overworld(), oak.getX() - 22, oak.getZ() + 4, 6, SOIL));
			BlockPos brown = server.computeOnServer(s -> Pictures.dryGround(s.overworld(), oak.getX() - 30, oak.getZ() - 8, 6, SOIL));
			BlockPos small = server.computeOnServer(s -> Pictures.dryGround(s.overworld(), oak.getX() - 14, oak.getZ() - 6, 6, SOIL));
			check(red != null && brown != null && small != null, "no dry ground for the mushrooms");
			server.runCommand("place feature minecraft:huge_red_mushroom %d %d %d".formatted(red.getX(), red.getY(), red.getZ()));
			server.runCommand("place feature minecraft:huge_brown_mushroom %d %d %d".formatted(brown.getX(), brown.getY(), brown.getZ()));
			server.runCommand("place feature minecraft:huge_red_mushroom %d %d %d".formatted(small.getX(), small.getY(), small.getZ()));
			context.waitTicks(10);
			server.runOnServer(s -> AncientMushrooms.finish(s.overworld(), RandomSource.create(SEED), red,
				Blocks.MUSHROOM_STEM.defaultBlockState(), Blocks.RED_MUSHROOM_BLOCK.defaultBlockState(), null, true));
			frame(server, Vec3.atBottomCenterOf(red).add(-4, 7, -2), 26, 1, 165);
			Pictures.shoot(context, connection, "mushrooms");
		}
	}

	/** Stand the camera where it can see {@code target}, or fail saying it could not. */
	private static void frame(TestServerContext server, Vec3 target, double distance, double rise, double degrees) {
		Vec3 from = server.computeOnServer(s -> Pictures.vantage(s.overworld(), target, distance, rise, degrees));
		check(from != null, "nowhere in reach to see " + target + " from");
		Pictures.look(server, from, target);
	}

	/** Put the camera over a place and wait for its chunks, so the ground there can be asked about. */
	private static void arrive(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, BlockPos at) {
		server.runCommand("tp @a %d 200 %d".formatted(at.getX(), at.getZ()));
		Pictures.settle(context, connection);
	}

	private static void check(boolean ok, String complaint) {
		if (!ok) throw new AssertionError(complaint);
	}
}
