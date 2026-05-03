package ru.nekostul.worldzero.mixin.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.nekostul.worldzero.feature.xray.WorldZeroXrayMask;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void worldzero$maskHiddenOreEchoes(BlockPos pos, CallbackInfoReturnable<BlockState> callbackInfo) {
        if (WorldZeroXrayMask.worldzero$isInternalQueryActive()) {
            return;
        }

        LevelChunk chunk = (LevelChunk) (Object) this;
        Level level = chunk.getLevel();
        if (level == null || !level.isClientSide()) {
            return;
        }

        BlockState actualState = callbackInfo.getReturnValue();
        WorldZeroXrayMask.worldzero$beginInternalQuery();
        try {
            BlockState maskedState = WorldZeroXrayMask.worldzero$maskBlock(level, pos, actualState);
            if (maskedState != actualState) {
                callbackInfo.setReturnValue(maskedState);
            }
        } finally {
            WorldZeroXrayMask.worldzero$endInternalQuery();
        }
    }
}
