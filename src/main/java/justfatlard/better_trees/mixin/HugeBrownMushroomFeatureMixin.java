package justfatlard.better_trees.mixin;

import justfatlard.better_trees.AncientMushrooms;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.AbstractHugeMushroomFeature;
import net.minecraft.world.level.levelgen.feature.HugeBrownMushroomFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The cap is the last thing a huge mushroom places, so after it the mushroom is whole. */
@Mixin(HugeBrownMushroomFeature.class)
public class HugeBrownMushroomFeatureMixin {
	@Inject(method = "makeCap", at = @At("RETURN"))
	private void betterTrees$afterCap(WorldGenLevel level, RandomSource random, BlockPos origin, int height,
			BlockPos.MutableBlockPos cursor, CallbackInfo ci) {
		AbstractHugeMushroomFeature self = (AbstractHugeMushroomFeature) (Object) this;
		AncientMushrooms.after(level, random, origin,
			self.stemProvider().value().getState(level, random, origin),
			self.capProvider().value().getState(level, random, origin), null);
	}
}
