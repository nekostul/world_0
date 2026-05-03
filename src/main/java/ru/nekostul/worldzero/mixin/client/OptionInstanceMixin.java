package ru.nekostul.worldzero.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nekostul.worldzero.WorldZeroKoridorClientController;
import ru.nekostul.worldzero.WorldZeroSkyWatchClientController;

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
