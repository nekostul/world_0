package ru.nekostul.worldzero.event.growth;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import ru.nekostul.worldzero.event.house.WorldZeroHouseDetector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class WorldZeroGrowthEvent {
    private static final int WORLDZERO_DURATION_TICKS = 42 * 20;
    private static final int WORLDZERO_SPREAD_INTERVAL_TICKS = 8;
    private static final int WORLDZERO_MAX_BLOCKS = 34;
    private static final Map<MinecraftServer, ActiveState> WORLDZERO_STATES = new WeakHashMap<>();

    private WorldZeroGrowthEvent() {
    }

    public static boolean worldzero$triggerNow(ServerPlayer player) {
        if (!worldzero$isValidPlayer(player) || player.serverLevel().dimension() != Level.OVERWORLD) {
            return false;
        }
        if (WorldZeroHouseDetector.worldzero$findContainingHouse(player) != null) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null || worldzero$isActive(server)) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        WORLDZERO_STATES.put(server, new ActiveState(
                player.getUUID(),
                level.getGameTime() + WORLDZERO_DURATION_TICKS,
                level.getGameTime()
        ));
        level.playSound(null, player.blockPosition(), SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 0.45F, 0.7F);
        level.playSound(null, player.blockPosition(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.35F, 0.5F);
        return true;
    }

    public static void worldzero$tick(ServerLevel level) {
        ActiveState state = WORLDZERO_STATES.get(level.getServer());
        if (state == null) {
            return;
        }

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(state.worldzero$playerId);
        if (!worldzero$isValidPlayer(player) || level.getGameTime() >= state.worldzero$endTick) {
            worldzero$stopNow(level.getServer());
            return;
        }

        if (level.getGameTime() < state.worldzero$nextSpreadTick || state.worldzero$placedBlocks.size() >= WORLDZERO_MAX_BLOCKS) {
            return;
        }

        state.worldzero$nextSpreadTick = level.getGameTime() + WORLDZERO_SPREAD_INTERVAL_TICKS;
        int attempts = 0;
        while (attempts++ < 10 && state.worldzero$placedBlocks.size() < WORLDZERO_MAX_BLOCKS) {
            BlockPos pos = worldzero$randomGrowthPos(level, player);
            if (pos == null) {
                continue;
            }

            BlockState originalState = level.getBlockState(pos);
            BlockState placedState = worldzero$growthState(level);
            level.setBlock(pos, placedState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            state.worldzero$placedBlocks.add(new PlacedBlock(pos, originalState, placedState));
            if (level.random.nextBoolean()) {
                level.playSound(null, pos, SoundEvents.VINE_PLACE, SoundSource.BLOCKS, 0.25F, 0.75F);
            }
            break;
        }
    }

    public static boolean worldzero$isActive(MinecraftServer server) {
        return server != null && WORLDZERO_STATES.containsKey(server);
    }

    public static boolean worldzero$stopNow(MinecraftServer server) {
        ActiveState state = WORLDZERO_STATES.remove(server);
        if (server == null || state == null) {
            return false;
        }

        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level != null) {
            Iterator<PlacedBlock> iterator = state.worldzero$placedBlocks.iterator();
            while (iterator.hasNext()) {
                PlacedBlock placedBlock = iterator.next();
                BlockState currentState = level.getBlockState(placedBlock.worldzero$pos);
                if (currentState == placedBlock.worldzero$placedState || currentState.equals(placedBlock.worldzero$placedState)) {
                    level.setBlock(placedBlock.worldzero$pos, placedBlock.worldzero$originalState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                iterator.remove();
            }
        }
        return true;
    }

    private static BlockState worldzero$growthState(ServerLevel level) {
        BlockState state = switch (level.random.nextInt(3)) {
            case 0 -> Blocks.OAK_LEAVES.defaultBlockState();
            case 1 -> Blocks.SPRUCE_LEAVES.defaultBlockState();
            default -> Blocks.VINE.defaultBlockState();
        };
        if (state.hasProperty(LeavesBlock.PERSISTENT)) {
            state = state.setValue(LeavesBlock.PERSISTENT, true);
        }
        return state;
    }

    private static BlockPos worldzero$randomGrowthPos(ServerLevel level, ServerPlayer player) {
        BlockPos base = player.blockPosition();
        int radius = 2 + Math.min(5, (int) ((WORLDZERO_MAX_BLOCKS + level.random.nextInt(6)) / 8));
        BlockPos pos = base.offset(
                Mth.nextInt(level.random, -radius, radius),
                Mth.nextInt(level.random, -1, 2),
                Mth.nextInt(level.random, -radius, radius)
        );
        if (!level.getBlockState(pos).isAir() || new AABB(pos).intersects(player.getBoundingBox().inflate(0.35D))) {
            return null;
        }
        if (pos.distSqr(base) < 2.0D * 2.0D) {
            return null;
        }

        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);
        if (belowState.isFaceSturdy(level, belowPos, Direction.UP)) {
            return pos;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos sidePos = pos.relative(direction);
            if (level.getBlockState(sidePos).isFaceSturdy(level, sidePos, direction.getOpposite())) {
                return pos;
            }
        }
        return null;
    }

    private static boolean worldzero$isValidPlayer(ServerPlayer player) {
        return player != null && player.isAlive() && !player.isSpectator();
    }

    private static final class ActiveState {
        private final UUID worldzero$playerId;
        private final long worldzero$endTick;
        private long worldzero$nextSpreadTick;
        private final List<PlacedBlock> worldzero$placedBlocks = new ArrayList<>();

        private ActiveState(UUID playerId, long endTick, long nextSpreadTick) {
            this.worldzero$playerId = playerId;
            this.worldzero$endTick = endTick;
            this.worldzero$nextSpreadTick = nextSpreadTick;
        }
    }

    private record PlacedBlock(BlockPos worldzero$pos, BlockState worldzero$originalState, BlockState worldzero$placedState) {
    }
}
