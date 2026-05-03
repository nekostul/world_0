package ru.nekostul.worldzero.mixin.client;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.nekostul.worldzero.client.controller.WorldZeroFallDamageClientController;
import ru.nekostul.worldzero.client.controller.WorldZeroKoridorClientController;

@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
    @ModifyVariable(method = "play", at = @At("HEAD"), argsOnly = true)
    private SoundInstance worldzero$replaceKoridorDoorSound(SoundInstance soundInstance) {
        if (WorldZeroKoridorClientController.worldzero$isVanillaDoorSoundInstance(soundInstance)) {
            return soundInstance;
        }

        SoundInstance mutedFallDamageSound = WorldZeroFallDamageClientController.worldzero$createFallDamageMute(soundInstance);
        if (mutedFallDamageSound != null) {
            return mutedFallDamageSound;
        }

        SoundInstance replacementSound = WorldZeroKoridorClientController.worldzero$createDoorReplacement(
                soundInstance,
                0
        );
        if (replacementSound != null) {
            return replacementSound;
        }

        SoundInstance fallReplacement = WorldZeroFallDamageClientController.worldzero$createFallDamageReplacement(soundInstance);
        return fallReplacement != null ? fallReplacement : soundInstance;
    }

    @ModifyVariable(method = "playDelayed", at = @At("HEAD"), argsOnly = true)
    private SoundInstance worldzero$replaceDelayedKoridorDoorSound(SoundInstance soundInstance) {
        if (WorldZeroKoridorClientController.worldzero$isVanillaDoorSoundInstance(soundInstance)) {
            return soundInstance;
        }

        SoundInstance mutedFallDamageSound = WorldZeroFallDamageClientController.worldzero$createFallDamageMute(soundInstance);
        if (mutedFallDamageSound != null) {
            return mutedFallDamageSound;
        }

        SoundInstance replacementSound = WorldZeroKoridorClientController.worldzero$createDoorReplacement(
                soundInstance,
                0
        );
        if (replacementSound != null) {
            return replacementSound;
        }

        SoundInstance fallReplacement = WorldZeroFallDamageClientController.worldzero$createFallDamageReplacement(soundInstance);
        return fallReplacement != null ? fallReplacement : soundInstance;
    }
}
