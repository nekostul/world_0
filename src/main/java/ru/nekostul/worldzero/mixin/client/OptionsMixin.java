package ru.nekostul.worldzero.mixin.client;

import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.nekostul.worldzero.client.controller.WorldZeroFallClientController;
import ru.nekostul.worldzero.client.controller.WorldZeroNightDarknessClientController;
import ru.nekostul.worldzero.client.controller.WorldZeroParalysisClientController;

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
            return;
        }

        if (WorldZeroNightDarknessClientController.worldzero$isRenderDistanceForced()) {
            callbackInfo.setReturnValue(WorldZeroNightDarknessClientController.worldzero$getForcedEffectiveRenderDistance());
        }
    }
}
