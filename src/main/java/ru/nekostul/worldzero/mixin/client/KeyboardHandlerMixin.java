package ru.nekostul.worldzero.mixin;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nekostul.worldzero.WorldZeroFallClientController;
import ru.nekostul.worldzero.WorldZeroFreezeClientController;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void worldzero$blockKeyboardDuringFallAndFreeze(
            long windowPointer,
            int key,
            int scanCode,
            int action,
            int modifiers,
            CallbackInfo callbackInfo
    ) {
        if (worldzero$isKeyboardBlocked()) {
            callbackInfo.cancel();
        }
    }

    private static boolean worldzero$isKeyboardBlocked() {
        return WorldZeroFreezeClientController.isFreezeActive()
                || WorldZeroFallClientController.worldzero$isFallPauseBlocked();
    }
}
