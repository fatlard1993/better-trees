package justfatlard.better_trees.mixin;

import justfatlard.better_trees.SelfSeeding;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives a settled seed its one chance to plant itself; see {@link SelfSeeding}. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

	/**
	 * Whether this item has had its chance.
	 *
	 * <p>Not saved with the entity, so an item that was lying on the ground when the server stopped
	 * gets another roll when it comes back. That is a far smaller wrong than the alternative of
	 * writing to everybody's item entities, and a seed getting a second chance across a restart is
	 * not a thing anybody will notice.
	 */
	@Unique
	private boolean betterTrees$rolled;

	@Inject(method = "tick", at = @At("TAIL"), require = 1)
	private void betterTrees$takeRoot(CallbackInfo info) {
		if (betterTrees$rolled) return;

		ItemEntity self = (ItemEntity) (Object) this;
		if (!(self.level() instanceof ServerLevel level)) return;
		if (self.tickCount < SelfSeeding.SETTLE_TICKS || !self.onGround()) return;

		betterTrees$rolled = true;
		SelfSeeding.tryTakeRoot(level, self);
	}
}
