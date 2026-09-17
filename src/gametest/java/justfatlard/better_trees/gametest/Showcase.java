package justfatlard.better_trees.gametest;

import justfatlard.better_trees.LeafStairsProcessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * The pictures for the readme and the mod page: an ancient tree, and huge mushrooms finished the
 * way the trees are.
 *
 * <p>An ancient is a one in two hundred roll during worldgen, so waiting for one is not a plan.
 * The test calls the same {@code placeAncientExtension} the roll calls, which is what a player
 * comes across rather than a copy of it. Run it under xvfb-run; the frames land in
 * build/run/clientGameTest/screenshots.
 */
public final class Showcase implements FabricClientGameTest {

	private static final int WIDTH = 1920;
	private static final int HEIGHT = 1080;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();

			context.getInput().pressKey(options -> options.keyToggleGui);
			context.runOnClient(client -> client.options.renderDistance().set(16));
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doWeatherCycle false");
			server.runCommand("weather clear");
			server.runCommand("time set 1000");
			server.runCommand("gamemode spectator @a");

			BlockPos origin = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			int x = origin.getX();
			int y = origin.getY();
			int z = origin.getZ();

			// An ancient oak, grown through the path the worldgen roll takes: an ordinary tree
			// first, because what the roll does is amplify one that has just been placed.
			BlockPos ancient = new BlockPos(x, y, z - 20);
			server.runCommand("place feature minecraft:oak %d %d %d"
				.formatted(ancient.getX(), ancient.getY(), ancient.getZ()));
			context.waitTicks(20);
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				RandomSource random = RandomSource.create(20260916L);
				boolean grown = LeafStairsProcessor.placeAncientExtension(level,
					level.getChunkSource().getGenerator(), ancient, random);
				LeafStairsProcessor.process(level, ancient, random, grown);
			});
			context.waitTicks(80);

			look(server, x + 26.0, y + 12.0, z + 2.0, ancient.getX(), y + 14.0, ancient.getZ());
			context.waitTicks(60);
			shoot(context, "ancient");

			// And the huge mushrooms, whose caps are finished the way a canopy is.
			server.runCommand("place feature minecraft:huge_red_mushroom %d %d %d".formatted(x - 6, y, z + 6));
			server.runCommand("place feature minecraft:huge_brown_mushroom %d %d %d".formatted(x + 6, y, z + 6));
			context.waitTicks(60);

			look(server, x + 0.5, y + 3.0, z + 22.0, x, y + 4.0, z + 6.0);
			context.waitTicks(40);
			shoot(context, "mushrooms");
		}
	}

	/**
	 * Stand the camera at one place and point it at another. The camera's y is the feet, so it
	 * looks from 1.62 above where it stands.
	 */
	private void look(TestServerContext server, double x, double y, double z,
			double atX, double atY, double atZ) {
		double dx = atX - x;
		double dy = atY - (y + 1.62);
		double dz = atZ - z;
		double yaw = -Math.toDegrees(Math.atan2(dx, dz));
		double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		server.runCommand("tp @a %.2f %.2f %.2f %.1f %.1f".formatted(x, y, z, yaw, pitch));
	}

	private void shoot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name)
			.withSize(WIDTH, HEIGHT)
			.disableCounterPrefix());
	}
}
