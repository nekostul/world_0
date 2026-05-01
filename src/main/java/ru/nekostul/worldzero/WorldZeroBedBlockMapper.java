package ru.nekostul.worldzero;

import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

public final class WorldZeroBedBlockMapper {
    private WorldZeroBedBlockMapper() {
    }

    public static BlockState worldzero$forceRedBed(BlockState state) {
        if (!(state.getBlock() instanceof BedBlock) || state.is(Blocks.RED_BED)) {
            return state;
        }

        BlockState redBedState = Blocks.RED_BED.defaultBlockState();
        StateDefinition<Block, BlockState> definition = redBedState.getBlock().getStateDefinition();
        for (Property<?> property : state.getProperties()) {
            if (definition.getProperty(property.getName()) != null) {
                redBedState = worldzero$copyProperty(state, redBedState, property);
            }
        }

        return redBedState;
    }

    private static <T extends Comparable<T>> BlockState worldzero$copyProperty(BlockState source, BlockState target, Property<T> property) {
        if (!source.hasProperty(property) || !target.hasProperty(property)) {
            return target;
        }

        return target.setValue(property, source.getValue(property));
    }
}
