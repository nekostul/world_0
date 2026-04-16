package ru.nekostul.worldzero.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nekostul.worldzero.WorldZeroFallClientController;
import ru.nekostul.worldzero.WorldZeroFreezeClientController;
import ru.nekostul.worldzero.WorldZeroParalysisClientController;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void worldzero$blockCameraDuringFreeze(CallbackInfo callbackInfo) {
        if (worldzero$isMouseLookBlocked()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void worldzero$blockMouseButtonsDuringFreezeAndFall(
            long windowPointer,
            int button,
            int action,
            int modifiers,
            CallbackInfo callbackInfo
    ) {
        WorldZeroParalysisClientController.worldzero$handleMousePress(button, action);
        if (worldzero$isMousePressBlocked()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void worldzero$blockMouseScrollDuringFreezeAndFall(
            long windowPointer,
            double xOffset,
            double yOffset,
            CallbackInfo callbackInfo
    ) {
        if (worldzero$isMouseScrollBlocked()) {
            callbackInfo.cancel();
        }
    }

    private static boolean worldzero$isMouseLookBlocked() {
        return WorldZeroFreezeClientController.isFreezeActive()
                || WorldZeroFallClientController.worldzero$isFallPauseBlocked()
                || WorldZeroParalysisClientController.worldzero$isMouseLookBlocked();
    }

    private static boolean worldzero$isMousePressBlocked() {
        return WorldZeroFreezeClientController.isFreezeActive()
                || WorldZeroFallClientController.worldzero$isFallPauseBlocked()
                || WorldZeroParalysisClientController.worldzero$isMousePressBlocked();
    }

    private static boolean worldzero$isMouseScrollBlocked() {
        return WorldZeroFreezeClientController.isFreezeActive()
                || WorldZeroFallClientController.worldzero$isFallPauseBlocked()
                || WorldZeroParalysisClientController.worldzero$isMouseScrollBlocked();
    }
}
