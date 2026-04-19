package ru.nekostul.worldzero.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.nekostul.worldzero.WorldZeroKoridorClientController;

@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
    @ModifyVariable(method = "play", at = @At("HEAD"), argsOnly = true)
    private SoundInstance worldzero$replaceKoridorDoorSound(SoundInstance soundInstance) {
        if (WorldZeroKoridorClientController.worldzero$isVanillaDoorSoundInstance(soundInstance)) {
            return soundInstance;
        }

        SoundInstance replacementSound = WorldZeroKoridorClientController.worldzero$createDoorReplacement(
                soundInstance,
                0
        );
        return replacementSound != null ? replacementSound : soundInstance;
    }

    @ModifyVariable(method = "playDelayed", at = @At("HEAD"), argsOnly = true)
    private SoundInstance worldzero$replaceDelayedKoridorDoorSound(SoundInstance soundInstance) {
        if (WorldZeroKoridorClientController.worldzero$isVanillaDoorSoundInstance(soundInstance)) {
            return soundInstance;
        }

        SoundInstance replacementSound = WorldZeroKoridorClientController.worldzero$createDoorReplacement(
                soundInstance,
                0
        );
        return replacementSound != null ? replacementSound : soundInstance;
    }
}
