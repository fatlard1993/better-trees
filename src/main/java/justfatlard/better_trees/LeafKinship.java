package justfatlard.better_trees;

import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which wood a leaf belongs to, so it can be told from the wood it merely happens to touch.
 *
 * <p>Vanilla asks only whether there is <em>a</em> log within six blocks, so a birch beam through
 * a wall keeps a felled oak's crown hanging in the air, and a tree farm leaves a shell of somebody
 * else's leaves behind. Asking which log it is costs nothing and answers both.
 *
 * <p>Worked out from names rather than declared in a table, which is what makes it cover wood sets
 * this mod has never heard of: a mod shipping {@code walnut_log} and {@code walnut_leaves} is
 * handled the day it is installed, with no datapack and no entry here. The handful that do not
 * follow the rule are named below.
 *
 * <p><b>Unknown means yes.</b> A block whose name says nothing - a modded log with an unusual
 * shape, anything this cannot place - is left to vanilla's own answer. The rule only ever takes
 * support away from a leaf when it is certain the neighbour belongs to a different tree, because
 * being wrong in that direction deletes somebody's canopy and being wrong in the other merely
 * leaves it standing the way it always did.
 */
public final class LeafKinship {
	private LeafKinship() {}

	/** Suffixes a wood set hangs off its own name, longest first so {@code _leaf_stairs} wins. */
	private static final List<String> PARTS =
		List.of("_leaf_stairs", "_leaves", "_hyphae", "_stem", "_wood", "_log");

	/** The ones whose name does not say what they belong to. */
	private static final Map<String, String> IRREGULAR = Map.of(
		// An azalea is a tree with oak in it, so its leaves hang on oak.
		"azalea", "oak",
		"flowering_azalea", "oak",
		"bamboo_block", "bamboo");

	/**
	 * Whether this neighbour is allowed to hold this leaf up.
	 *
	 * @return false only when both are placed and they belong to different trees
	 */
	public static boolean keeps(BlockState leaf, BlockState neighbour) {
		String mine = speciesOf(leaf);
		if (mine == null) return true;

		String theirs = speciesOf(neighbour);
		if (theirs == null) return true;

		return mine.equals(theirs);
	}

	/** The wood set this block belongs to, or null when its name does not say. */
	public static String speciesOf(BlockState state) {
		String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();

		String irregular = IRREGULAR.get(name);
		if (irregular != null) return irregular;

		// Stripped is a finish, not a species: stripped birch is still birch.
		if (name.startsWith("stripped_")) name = name.substring("stripped_".length());

		for (String part : PARTS) {
			if (!name.endsWith(part)) continue;

			String species = name.substring(0, name.length() - part.length());
			return species.isEmpty() ? null : IRREGULAR.getOrDefault(species, species);
		}
		return null;
	}
}
