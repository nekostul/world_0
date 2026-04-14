package ru.nekostul.worldzero;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public final class WorldZeroXrayMask {
    private static final ThreadLocal<Boolean> WORLDZERO_INTERNAL_QUERY = ThreadLocal.withInitial(() -> false);

    private WorldZeroXrayMask() {
    }

    public static boolean worldzero$isInternalQueryActive() {
        return WORLDZERO_INTERNAL_QUERY.get();
    }

    public static void worldzero$beginInternalQuery() {
        WORLDZERO_INTERNAL_QUERY.set(true);
    }

    public static void worldzero$endInternalQuery() {
        WORLDZERO_INTERNAL_QUERY.set(false);
    }

    public static BlockState worldzero$maskBlock(Level level, BlockPos pos, BlockState actualState) {
        if (level == null || pos == null || actualState == null || actualState.isAir()) {
            return actualState;
        }

        if (WorldZeroDevCheats.isAllowedForCurrentClient()) {
            return actualState;
        }

        boolean realOre = worldzero$isProtectedOre(actualState);
        boolean hostRock = worldzero$isHostRock(actualState);
        if (!realOre && !hostRock) {
            return actualState;
        }

        if (worldzero$isExposed(level, pos)) {
            return actualState;
        }

        if (!realOre && pos.getY() > 48) {
            return actualState;
        }

        if (realOre) {
            return worldzero$selectMaskedState(level, pos, actualState, true);
        }

        if (!worldzero$shouldProjectDecoy(pos)) {
            return actualState;
        }

        return worldzero$selectMaskedState(level, pos, actualState, false);
    }

    private static boolean worldzero$isExposed(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.values()) {
            neighborPos.setWithOffset(pos, direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.isAir()) {
                return true;
            }

            FluidState fluidState = neighborState.getFluidState();
            if (!fluidState.isEmpty()) {
                return true;
            }

            if (!neighborState.isSolidRender(level, neighborPos)) {
                return true;
            }
        }

        return false;
    }

    private static BlockState worldzero$selectMaskedState(
            Level level,
            BlockPos pos,
            BlockState actualState,
            boolean realOre
    ) {
        BlockState hostState = worldzero$resolveHostState(actualState, pos);
        int echoSeed = worldzero$hash(pos.getX() >> 1, pos.getY() >> 1, pos.getZ() >> 1);

        if (realOre && (echoSeed & 3) == 0) {
            return hostState;
        }

        if (worldzero$isNetherLike(actualState, level, pos)) {
            return switch (Math.floorMod(echoSeed, 4)) {
                case 0 -> Blocks.NETHER_QUARTZ_ORE.defaultBlockState();
                case 1 -> Blocks.NETHER_GOLD_ORE.defaultBlockState();
                case 2 -> Blocks.BLACKSTONE.defaultBlockState();
                default -> hostState;
            };
        }

        if (pos.getY() <= -32) {
            return switch (Math.floorMod(echoSeed, 6)) {
                case 0 -> Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState();
                case 1 -> Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState();
                case 2 -> Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState();
                case 3 -> Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState();
                case 4 -> Blocks.DEEPSLATE_IRON_ORE.defaultBlockState();
                default -> hostState;
            };
        }

        if (pos.getY() <= 24) {
            return switch (Math.floorMod(echoSeed, 7)) {
                case 0 -> Blocks.DEEPSLATE_IRON_ORE.defaultBlockState();
                case 1 -> Blocks.DEEPSLATE_COPPER_ORE.defaultBlockState();
                case 2 -> Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState();
                case 3 -> Blocks.GOLD_ORE.defaultBlockState();
                case 4 -> Blocks.LAPIS_ORE.defaultBlockState();
                case 5 -> Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState();
                default -> hostState;
            };
        }

        if (pos.getY() <= 72) {
            return switch (Math.floorMod(echoSeed, 7)) {
                case 0 -> Blocks.COAL_ORE.defaultBlockState();
                case 1 -> Blocks.IRON_ORE.defaultBlockState();
                case 2 -> Blocks.COPPER_ORE.defaultBlockState();
                case 3 -> Blocks.LAPIS_ORE.defaultBlockState();
                case 4 -> Blocks.GOLD_ORE.defaultBlockState();
                case 5 -> Blocks.REDSTONE_ORE.defaultBlockState();
                default -> hostState;
            };
        }

        return switch (Math.floorMod(echoSeed, 5)) {
            case 0 -> Blocks.COAL_ORE.defaultBlockState();
            case 1 -> Blocks.IRON_ORE.defaultBlockState();
            case 2 -> Blocks.COPPER_ORE.defaultBlockState();
            case 3 -> Blocks.EMERALD_ORE.defaultBlockState();
            default -> hostState;
        };
    }

    private static boolean worldzero$shouldProjectDecoy(BlockPos pos) {
        int coarse = worldzero$hash(pos.getX() >> 1, pos.getY() >> 1, pos.getZ() >> 1);
        int fine = worldzero$hash(pos.getX(), pos.getY(), pos.getZ());

        int signature = coarse & 31;
        if (signature == 0) {
            return true;
        }

        return signature == 1 && (fine & 1) == 0;
    }

    private static BlockState worldzero$resolveHostState(BlockState actualState, BlockPos pos) {
        Block block = actualState.getBlock();
        if (block == Blocks.DEEPSLATE
                || block == Blocks.TUFF
                || block == Blocks.DEEPSLATE_COAL_ORE
                || block == Blocks.DEEPSLATE_IRON_ORE
                || block == Blocks.DEEPSLATE_COPPER_ORE
                || block == Blocks.DEEPSLATE_GOLD_ORE
                || block == Blocks.DEEPSLATE_REDSTONE_ORE
                || block == Blocks.DEEPSLATE_EMERALD_ORE
                || block == Blocks.DEEPSLATE_LAPIS_ORE
                || block == Blocks.DEEPSLATE_DIAMOND_ORE) {
            return Blocks.DEEPSLATE.defaultBlockState();
        }

        if (block == Blocks.NETHERRACK
                || block == Blocks.BLACKSTONE
                || block == Blocks.BASALT
                || block == Blocks.NETHER_GOLD_ORE
                || block == Blocks.NETHER_QUARTZ_ORE
                || block == Blocks.ANCIENT_DEBRIS) {
            return Blocks.NETHERRACK.defaultBlockState();
        }

        if (block == Blocks.END_STONE) {
            return Blocks.END_STONE.defaultBlockState();
        }

        return pos.getY() < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    private static boolean worldzero$isNetherLike(BlockState actualState, Level level, BlockPos pos) {
        Block block = actualState.getBlock();
        if (block == Blocks.NETHERRACK
                || block == Blocks.BLACKSTONE
                || block == Blocks.BASALT
                || block == Blocks.NETHER_GOLD_ORE
                || block == Blocks.NETHER_QUARTZ_ORE
                || block == Blocks.ANCIENT_DEBRIS) {
            return true;
        }

        return level.dimension() == Level.NETHER && pos.getY() < 128;
    }

    private static boolean worldzero$isProtectedOre(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.COAL_ORE
                || block == Blocks.DEEPSLATE_COAL_ORE
                || block == Blocks.IRON_ORE
                || block == Blocks.DEEPSLATE_IRON_ORE
                || block == Blocks.COPPER_ORE
                || block == Blocks.DEEPSLATE_COPPER_ORE
                || block == Blocks.GOLD_ORE
                || block == Blocks.DEEPSLATE_GOLD_ORE
                || block == Blocks.REDSTONE_ORE
                || block == Blocks.DEEPSLATE_REDSTONE_ORE
                || block == Blocks.EMERALD_ORE
                || block == Blocks.DEEPSLATE_EMERALD_ORE
                || block == Blocks.LAPIS_ORE
                || block == Blocks.DEEPSLATE_LAPIS_ORE
                || block == Blocks.DIAMOND_ORE
                || block == Blocks.DEEPSLATE_DIAMOND_ORE
                || block == Blocks.NETHER_GOLD_ORE
                || block == Blocks.NETHER_QUARTZ_ORE
                || block == Blocks.ANCIENT_DEBRIS;
    }

    private static boolean worldzero$isHostRock(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.STONE
                || block == Blocks.DEEPSLATE
                || block == Blocks.TUFF
                || block == Blocks.ANDESITE
                || block == Blocks.DIORITE
                || block == Blocks.GRANITE
                || block == Blocks.CALCITE
                || block == Blocks.NETHERRACK
                || block == Blocks.BLACKSTONE
                || block == Blocks.BASALT
                || block == Blocks.END_STONE;
    }

    private static int worldzero$hash(int x, int y, int z) {
        int value = x * 73428767 ^ y * 912931 ^ z * 438289;
        value ^= value >>> 13;
        value *= 1274126177;
        value ^= value >>> 16;
        return value;
    }
}
