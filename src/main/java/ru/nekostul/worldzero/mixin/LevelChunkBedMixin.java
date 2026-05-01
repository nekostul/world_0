package ru.nekostul.worldzero.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.nekostul.worldzero.WorldZeroBedBlockMapper;

@Mixin(LevelChunk.class)
public abstract class LevelChunkBedMixin {
    @ModifyVariable(method = "setBlockState", at = @At("HEAD"), argsOnly = true)
    private BlockState worldzero$forceRedBeds(BlockState state) {
        return WorldZeroBedBlockMapper.worldzero$forceRedBed(state);
    }
}
