package ru.nekostul.worldzero.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Unique
    private static final long WORLDZERO_MOB_FADE_START_TICKS = 30L * 60L * 20L;

    @Unique
    private static final long WORLDZERO_MOB_FADE_END_TICKS = 90L * 60L * 20L;

    @Inject(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F", at = @At("RETURN"), cancellable = true)
    private void worldzero$fadeMobSounds(
            float instanceVolume,
            SoundSource source,
            CallbackInfoReturnable<Float> callbackInfo
    ) {
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
}
