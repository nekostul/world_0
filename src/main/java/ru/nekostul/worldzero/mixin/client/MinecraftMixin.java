package ru.nekostul.worldzero.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.nekostul.worldzero.WorldZeroBlankDiscClientController;
import ru.nekostul.worldzero.WorldZeroHouseBadClientController;
import ru.nekostul.worldzero.WorldZeroSkyWatchClientController;
import ru.nekostul.worldzero.WorldZeroVoidClientController;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    private void worldzero$blockPauseDuringBlankDisc(boolean showPauseMenu, CallbackInfo callbackInfo) {
        if (WorldZeroBlankDiscClientController.worldzero$isPauseBlocked()
                || WorldZeroHouseBadClientController.worldzero$isPauseBlocked()
                || WorldZeroSkyWatchClientController.worldzero$isPauseBlocked()
                || WorldZeroVoidClientController.worldzero$isPauseBlocked()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "isPaused", at = @At("HEAD"), cancellable = true)
    private void worldzero$forceUnpausedDuringBlankDisc(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (WorldZeroBlankDiscClientController.worldzero$isPauseBlocked()
                || WorldZeroHouseBadClientController.worldzero$isPauseBlocked()
                || WorldZeroSkyWatchClientController.worldzero$isPauseBlocked()
                || WorldZeroVoidClientController.worldzero$isPauseBlocked()) {
            callbackInfo.setReturnValue(false);
        }
    }
}
