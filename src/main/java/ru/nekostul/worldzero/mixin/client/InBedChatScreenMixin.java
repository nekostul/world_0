package ru.nekostul.worldzero.mixin;

import net.minecraft.client.gui.screens.InBedChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nekostul.worldzero.WorldZeroParalysisClientController;

@Mixin(InBedChatScreen.class)
public abstract class InBedChatScreenMixin {
    @Inject(method = "sendWakeUp", at = @At("HEAD"), cancellable = true)
    private void worldzero$blockWakeUpDuringParalysisSleep(CallbackInfo callbackInfo) {
        if (WorldZeroParalysisClientController.worldzero$isBedExitBlocked()) {
            callbackInfo.cancel();
        }
    }
}
