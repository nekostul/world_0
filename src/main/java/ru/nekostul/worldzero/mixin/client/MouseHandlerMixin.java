package ru.nekostul.worldzero.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nekostul.worldzero.WorldZeroFallClientController;
import ru.nekostul.worldzero.WorldZeroFreezeClientController;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void worldzero$blockCameraDuringFreeze(CallbackInfo callbackInfo) {
        if (worldzero$isMouseBlocked()) {
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
        if (worldzero$isMouseBlocked()) {
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
        if (worldzero$isMouseBlocked()) {
            callbackInfo.cancel();
        }
    }

    private static boolean worldzero$isMouseBlocked() {
        return WorldZeroFreezeClientController.isFreezeActive()
                || WorldZeroFallClientController.worldzero$isFallPauseBlocked();
    }
}
