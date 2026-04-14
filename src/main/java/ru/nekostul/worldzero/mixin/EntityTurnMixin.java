package ru.nekostul.worldzero.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nekostul.worldzero.WorldZeroFreezeClientController;

@Mixin(Entity.class)
public abstract class EntityTurnMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void worldzero$blockPlayerTurn(double yawDelta, double pitchDelta, CallbackInfo callbackInfo) {
        if (!WorldZeroFreezeClientController.isFreezeActive()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return;
        }

        if ((Object) this == minecraft.player) {
            callbackInfo.cancel();
        }
    }
}
