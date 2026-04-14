package ru.nekostul.worldzero.mixin;

import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nekostul.worldzero.WorldZeroFreezeClientController;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void worldzero$freezeParticles(CallbackInfo callbackInfo) {
        if (WorldZeroFreezeClientController.isFreezeActive()) {
            callbackInfo.cancel();
        }
    }
}
