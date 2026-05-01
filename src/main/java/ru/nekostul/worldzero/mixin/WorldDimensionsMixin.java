package ru.nekostul.worldzero.mixin;

import com.mojang.serialization.Lifecycle;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.nekostul.worldzero.worldgen.WorldZeroLegacyChunkGenerator;

@Mixin(WorldDimensions.class)
public abstract class WorldDimensionsMixin {
    @Inject(method = "checkStability", at = @At("HEAD"), cancellable = true)
    private static void worldzero$markLegacyOverworldStable(
            ResourceKey<LevelStem> key,
            LevelStem levelStem,
            CallbackInfoReturnable<Lifecycle> callbackInfo
    ) {
        if (key == LevelStem.OVERWORLD && levelStem.generator() instanceof WorldZeroLegacyChunkGenerator) {
            callbackInfo.setReturnValue(Lifecycle.stable());
        }
    }
}
