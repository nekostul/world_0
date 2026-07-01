package ru.nekostul.worldzero.mixin.world;

import net.minecraft.world.level.levelgen.feature.treedecorators.BeehiveDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeehiveDecorator.class)
public abstract class BeehiveDecoratorMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void worldzero$disableBeehiveGeneration(TreeDecorator.Context context, CallbackInfo callbackInfo) {
        callbackInfo.cancel();
    }
}
