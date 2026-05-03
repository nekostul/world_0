package ru.nekostul.worldzero.mixin.client;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nekostul.worldzero.client.controller.WorldZeroKoridorClientController;
import ru.nekostul.worldzero.client.controller.WorldZeroSkyWatchClientController;

@Mixin(OptionInstance.class)
public abstract class OptionInstanceMixin {
    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private void worldzero$blockKoridorSoundSliderChange(Object value, CallbackInfo callbackInfo) {
        if (WorldZeroKoridorClientController.worldzero$shouldBlockSoundOptionChange(
                (OptionInstance<?>) (Object) this,
                value
        ) || WorldZeroSkyWatchClientController.worldzero$shouldBlockSoundOptionChange(
                (OptionInstance<?>) (Object) this,
                value
        )) {
            callbackInfo.cancel();
        }
    }
}
