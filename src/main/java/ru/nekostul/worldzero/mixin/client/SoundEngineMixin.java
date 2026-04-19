package ru.nekostul.worldzero.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.nekostul.worldzero.WorldZeroFreezeClientController;
import ru.nekostul.worldzero.WorldZeroKoridorClientController;
import ru.nekostul.worldzero.WorldZeroParalysisClientController;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Unique
    private static final long WORLDZERO_MOB_FADE_START_TICKS = 30L * 60L * 20L;

    @Unique
    private static final long WORLDZERO_MOB_FADE_END_TICKS = 180L * 60L * 20L;

    @Inject(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F", at = @At("RETURN"), cancellable = true)
    private void worldzero$fadeMobSounds(
            float instanceVolume,
            SoundSource source,
            CallbackInfoReturnable<Float> callbackInfo
    ) {
        if (WorldZeroParalysisClientController.worldzero$isSoundIsolationActive() && source != SoundSource.PLAYERS) {
            callbackInfo.setReturnValue(0.0F);
            return;
        }

        if (WorldZeroFreezeClientController.isFreezeActive()) {
            if (source != SoundSource.PLAYERS) {
                callbackInfo.setReturnValue(0.0F);
                return;
            }
        }

        if (WorldZeroKoridorClientController.worldzero$isVolumeForced()) {
            callbackInfo.setReturnValue(instanceVolume);
            return;
        }

        if (source != SoundSource.AMBIENT) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return;
        }

        long gameTime = minecraft.level.getGameTime();
        if (gameTime <= WORLDZERO_MOB_FADE_START_TICKS) {
            return;
        }

        float fadeMultiplier = worldzero$fadeMultiplier(gameTime);
        if (fadeMultiplier >= 0.999f) {
            return;
        }

        callbackInfo.setReturnValue(callbackInfo.getReturnValueF() * fadeMultiplier);
    }

    @Unique
    private static float worldzero$fadeMultiplier(long gameTime) {
        if (gameTime >= WORLDZERO_MOB_FADE_END_TICKS) {
            return 0.0f;
        }

        double progress = (double) (gameTime - WORLDZERO_MOB_FADE_START_TICKS)
                / (double) (WORLDZERO_MOB_FADE_END_TICKS - WORLDZERO_MOB_FADE_START_TICKS);
        if (progress < 0.0D) {
            progress = 0.0D;
        } else if (progress > 1.0D) {
            progress = 1.0D;
        }

        // Cubic easing keeps ambient reduction almost imperceptible in the early phase.
        double easedProgress = progress * progress * progress;
        return (float) (1.0D - easedProgress);
    }

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void worldzero$filterParalysisSounds(SoundInstance soundInstance, CallbackInfo callbackInfo) {
        if (WorldZeroParalysisClientController.worldzero$isSoundIsolationActive()
                && !WorldZeroParalysisClientController.worldzero$isAllowedParalysisSound(soundInstance)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "playDelayed", at = @At("HEAD"), cancellable = true)
    private void worldzero$filterDelayedParalysisSounds(
            SoundInstance soundInstance,
            int delay,
            CallbackInfo callbackInfo
    ) {
        if (WorldZeroParalysisClientController.worldzero$isSoundIsolationActive()
                && !WorldZeroParalysisClientController.worldzero$isAllowedParalysisSound(soundInstance)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "queueTickingSound", at = @At("HEAD"), cancellable = true)
    private void worldzero$filterTickingParalysisSounds(
            TickableSoundInstance soundInstance,
            CallbackInfo callbackInfo
    ) {
        if (WorldZeroParalysisClientController.worldzero$isSoundIsolationActive()
                && !WorldZeroParalysisClientController.worldzero$isAllowedParalysisSound(soundInstance)) {
            callbackInfo.cancel();
        }
    }
}
