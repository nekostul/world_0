package ru.nekostul.worldzero.mixin;

import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.nekostul.worldzero.WorldZeroFallClientController;
import ru.nekostul.worldzero.WorldZeroParalysisClientController;

@Mixin(Options.class)
public abstract class OptionsMixin {
    @Inject(method = "getEffectiveRenderDistance", at = @At("HEAD"), cancellable = true)
    private void worldzero$forceSingleChunkRenderDistance(CallbackInfoReturnable<Integer> callbackInfo) {
        if (WorldZeroParalysisClientController.worldzero$isRenderDistanceForced()) {
            callbackInfo.setReturnValue(WorldZeroParalysisClientController.worldzero$getForcedEffectiveRenderDistance());
            return;
        }

        if (WorldZeroFallClientController.worldzero$isRenderDistanceForced()) {
            callbackInfo.setReturnValue(WorldZeroFallClientController.worldzero$getForcedEffectiveRenderDistance());
        }
    }
}
