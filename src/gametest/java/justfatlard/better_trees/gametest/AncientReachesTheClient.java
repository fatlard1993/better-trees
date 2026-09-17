package justfatlard.better_trees.gametest;

import justfatlard.better_trees.LeafStairsProcessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;

/**
 * An ancient grown next to somebody is an ancient they can see.
 *
 * <p>The amplifier writes its logs and leaves with {@code Block.UPDATE_INVISIBLE}, which is the
 * right flag while a chunk is being generated and the wrong one afterwards: a chunk already sent
 * to a client is never told. In play that is a tree with holes in it, or a crown hanging in the
 * air, until something makes the chunk reload - which is what fatlard saw on the server.
 *
 * <p>This counts the blocks the server has and the client does not, without reloading anything.
 */
public final class AncientReachesTheClient implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();

			BlockPos origin = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			BlockPos at = origin.offset(6, 0, 6);

			server.runCommand("place feature minecraft:oak %d %d %d"
				.formatted(at.getX(), at.getY(), at.getZ()));
			context.waitTicks(20);
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				RandomSource random = RandomSource.create(20260916L);
				boolean grown = LeafStairsProcessor.placeAncientExtension(level,
					level.getChunkSource().getGenerator(), at, random);
				LeafStairsProcessor.process(level, at, random, grown);
			});
			// Long enough for anything the server means to send to have arrived.
			context.waitTicks(100);

			BlockPos min = at.offset(-16, 0, -16);
			BlockPos max = at.offset(16, 48, 16);
			int onServer = server.computeOnServer(s -> count(s.overworld(), min, max));
			int onClient = context.computeOnClient(client -> count(client.level, min, max));

			check(onServer > 200, "the ancient did not grow: only " + onServer + " blocks of it");
			check(onClient == onServer, "the client is missing " + (onServer - onClient) + " of the "
				+ onServer + " blocks this tree is made of, and will keep missing them until the "
				+ "chunk reloads");
		}
	}

	/** Every log and leaf in the box, which is the tree and nothing else out here. */
	private static int count(net.minecraft.world.level.BlockGetter level, BlockPos min, BlockPos max) {
		int found = 0;
		for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
			var state = level.getBlockState(pos);
			if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) found++;
		}
		return found;
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
