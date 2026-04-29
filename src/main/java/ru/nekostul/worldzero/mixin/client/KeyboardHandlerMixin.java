package ru.nekostul.worldzero.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nekostul.worldzero.WorldZeroBlankDiscClientController;
import ru.nekostul.worldzero.WorldZeroFallClientController;
import ru.nekostul.worldzero.WorldZeroFreezeClientController;
import ru.nekostul.worldzero.WorldZeroHouseBadClientController;
import ru.nekostul.worldzero.WorldZeroParalysisClientController;
import ru.nekostul.worldzero.WorldZeroVoidClientController;
import ru.nekostul.worldzero.WorldZeroVoidDimension;

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
        if (worldzero$isDevInVoid()) {
            return false;
        }

        return WorldZeroFreezeClientController.isFreezeActive()
                || WorldZeroFallClientController.worldzero$isFallPauseBlocked()
                || WorldZeroParalysisClientController.worldzero$isKeyboardBlocked()
                || WorldZeroVoidClientController.worldzero$isKeyboardBlocked();
    }

    private static boolean worldzero$isDevInVoid() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null
                && minecraft.player != null
                && minecraft.level != null
                && "Dev".equals(minecraft.player.getGameProfile().getName())
                && minecraft.level.dimension() == WorldZeroVoidDimension.WORLDZERO_VOID_LEVEL;
    }
}
