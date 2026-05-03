package ru.nekostul.worldzero.mixin.client;

import net.minecraft.client.User;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.resources.SplashManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SplashManager.class)
public abstract class SplashManagerMixin {
    @Shadow
    @Final
    private User user;

    @Inject(method = "getSplash", at = @At("RETURN"), cancellable = true)
    private void worldzero$replaceNamedSplash(CallbackInfoReturnable<SplashRenderer> callbackInfo) {
        SplashRenderer splashRenderer = callbackInfo.getReturnValue();
        if (splashRenderer == null) {
            return;
        }

        String splash = ((SplashRendererAccessor) (Object) splashRenderer).worldzero$getSplash();
        if (splash == null || !splash.contains("{name}")) {
            return;
        }

        String playerName = this.user != null && this.user.getName() != null && !this.user.getName().isBlank()
                ? this.user.getName()
                : "player";
        callbackInfo.setReturnValue(new SplashRenderer(splash.replace("{name}", playerName)));
    }
}
