package ru.nekostul.worldzero.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.nekostul.worldzero.state.WorldZeroState;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
    @Inject(method = "isPublished", at = @At("HEAD"), cancellable = true)
    private void worldzero$reportPublishedWhenLocked(CallbackInfoReturnable<Boolean> callbackInfo) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && WorldZeroState.hasLocalPublishLock(minecraft)) {
            callbackInfo.setReturnValue(true);
        }
    }
}
