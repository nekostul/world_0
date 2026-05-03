package ru.nekostul.worldzero.mixin.client;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nekostul.worldzero.client.controller.WorldZeroBlankDiscClientController;
import ru.nekostul.worldzero.client.controller.WorldZeroFallClientController;
import ru.nekostul.worldzero.client.controller.WorldZeroFreezeClientController;
import ru.nekostul.worldzero.client.controller.WorldZeroHouseBadClientController;
import ru.nekostul.worldzero.client.controller.WorldZeroParalysisClientController;
import ru.nekostul.worldzero.client.controller.WorldZeroVoidClientController;

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
        if (WorldZeroBlankDiscClientController.worldzero$shouldBlockEscape(key, action)
                || WorldZeroHouseBadClientController.worldzero$shouldBlockEscape(key, action)) {
            callbackInfo.cancel();
            return;
        }

        if (worldzero$isKeyboardBlocked()) {
            callbackInfo.cancel();
        }
    }

    private static boolean worldzero$isKeyboardBlocked() {
        return WorldZeroFreezeClientController.isFreezeActive()
                || WorldZeroFallClientController.worldzero$isFallPauseBlocked()
                || WorldZeroParalysisClientController.worldzero$isKeyboardBlocked()
                || WorldZeroVoidClientController.worldzero$isKeyboardBlocked();
    }
}
