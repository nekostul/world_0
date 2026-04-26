package ru.nekostul.worldzero.mixin;

import net.minecraft.advancements.Advancement;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.nekostul.worldzero.WorldZeroHouseBadDimension;
import ru.nekostul.worldzero.WorldZeroHouseDimension;
import ru.nekostul.worldzero.WorldZeroKoridorDimension;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At("HEAD"), cancellable = true)
    private void worldzero$blockAdvancementsInCustomDimensions(
            Advancement advancement,
            String criterionName,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (this.player == null) {
            return;
        }

        if (this.player.level().dimension() == WorldZeroHouseDimension.WORLDZERO_HOUSE_LEVEL
                || this.player.level().dimension() == WorldZeroHouseBadDimension.WORLDZERO_HOUSE_BAD_LEVEL
                || this.player.level().dimension() == WorldZeroKoridorDimension.WORLDZERO_KORIDOR_LEVEL) {
            callbackInfo.setReturnValue(false);
        }
    }
}
