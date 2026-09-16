package justfatlard.better_trees.mixin;

import justfatlard.better_trees.AncientMushrooms;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.HugeFungusFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HugeFungusFeature.class)
public class HugeFungusFeatureMixin {
	@Inject(method = "place", at = @At("RETURN"))
	private void betterTrees$afterFungus(WorldGenLevel level, ChunkGenerator generator, RandomSource random,
			BlockPos origin, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) return;
		HugeFungusFeature self = (HugeFungusFeature) (Object) this;
		AncientMushrooms.after(level, random, origin, self.stemState(), self.hatState(), self.decorState());
	}
}
