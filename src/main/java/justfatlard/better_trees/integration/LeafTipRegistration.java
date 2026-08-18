package justfatlard.better_trees.integration;

import justfatlard.block_tip.api.BlockTipApi;
import justfatlard.better_trees.Main;

/**
 * Gives the leaf stairs a picture, since they have no item of their own.
 *
 * <p>These blocks exist only because worldgen placed them: nothing registers an
 * item, because nobody is meant to carry one. Which left Block Tip drawing an
 * empty square beside the name, and an empty square is worse than no card.
 *
 * <p>The stand-in is the vanilla leaves each one copies, which is also what the
 * block is pretending to be from every angle a player will ever see it.
 *
 * <p>Names block-tip types directly, so it must only be loaded behind the
 * isModLoaded guard in the entry point.
 */
public final class LeafTipRegistration {
	private LeafTipRegistration() {}

	public static void register(String blockId, String vanillaLeavesId) {
		BlockTipApi.icon(Main.MOD_ID + ":" + blockId, vanillaLeavesId);
	}
}
