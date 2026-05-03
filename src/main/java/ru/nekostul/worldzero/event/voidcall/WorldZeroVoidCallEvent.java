package ru.nekostul.worldzero.event.voidcall;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.nekostul.worldzero.dimension.WorldZeroVoidPortalDimension;
import ru.nekostul.worldzero.network.WorldZeroNetwork;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class WorldZeroVoidCallEvent {
    private static final int WORLDZERO_PULL_MAX_TICKS = 22 * 20;
    private static final int WORLDZERO_VOID_RETURN_TICKS = 90 * 20;
    private static final double WORLDZERO_PULL_STEP_MIN = 0.08D;
    private static final double WORLDZERO_PULL_STEP_MAX = 0.32D;
    private static final Map<MinecraftServer, ActiveState> WORLDZERO_STATES = new WeakHashMap<>();

    private WorldZeroVoidCallEvent() {
    }

    public static boolean worldzero$triggerNow(ServerPlayer player) {
        if (!worldzero$isValidPlayer(player) || player.serverLevel().dimension() != Level.OVERWORLD) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null || worldzero$isActive(server)) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        BlockPos portalPos = worldzero$findPortalPos(level, player);
        if (portalPos == null) {
            return false;
        }

        BlockState originalState = level.getBlockState(portalPos);
        level.setBlock(portalPos, Blocks.END_PORTAL.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        level.playSound(null, portalPos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.65F, 0.65F);
        WorldZeroNetwork.sendKeyboardBlock(player, WORLDZERO_PULL_MAX_TICKS + 10);
        WORLDZERO_STATES.put(server, new ActiveState(
                player.getUUID(),
                portalPos.immutable(),
                originalState,
                level.getGameTime() + WORLDZERO_PULL_MAX_TICKS
        ));
        return true;
    }

    public static void worldzero$tick(ServerLevel level) {
        ActiveState state = WORLDZERO_STATES.get(level.getServer());
        if (state == null) {
            return;
        }

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(state.worldzero$playerId);
        if (!worldzero$isValidPlayer(player)) {
            worldzero$stopNow(level.getServer());
            return;
        }

        if (state.worldzero$phase == Phase.PULLING) {
            worldzero$tickPull(level, state, player);
        } else if (level.getGameTime() >= state.worldzero$returnTick) {
            WorldZeroVoidPortalDimension.worldzero$returnPlayerFromVoidPortal(player);
            WORLDZERO_STATES.remove(level.getServer());
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

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null) {
            worldzero$restorePortal(overworld, state);
        }

        ServerPlayer player = server.getPlayerList().getPlayer(state.worldzero$playerId);
        if (player != null) {
            player.noPhysics = false;
            if (player.serverLevel().dimension() == WorldZeroVoidPortalDimension.WORLDZERO_VOIDPORTAL_LEVEL) {
                WorldZeroVoidPortalDimension.worldzero$returnPlayerFromVoidPortal(player);
            }
            WorldZeroNetwork.sendKeyboardBlock(player, 0);
        }
        return true;
    }

    public static boolean worldzero$returnFromVoidPortalNow(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        ActiveState state = WORLDZERO_STATES.get(server);
        if (state != null && state.worldzero$playerId.equals(player.getUUID())) {
            WORLDZERO_STATES.remove(server);
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            if (overworld != null) {
                worldzero$restorePortal(overworld, state);
            }
            player.noPhysics = false;
            WorldZeroNetwork.sendKeyboardBlock(player, 0);
        }

        return WorldZeroVoidPortalDimension.worldzero$returnPlayerFromVoidPortal(player);
    }

    private static void worldzero$tickPull(ServerLevel level, ActiveState state, ServerPlayer player) {
        if (player.serverLevel() != level || level.dimension() != Level.OVERWORLD) {
            worldzero$stopNow(level.getServer());
            return;
        }

        Vec3 portalCenter = Vec3.atCenterOf(state.worldzero$portalPos).add(0.0D, 0.25D, 0.0D);
        Vec3 toPortal = portalCenter.subtract(player.position());
        double distance = toPortal.length();
        if (distance <= 1.15D || level.getGameTime() >= state.worldzero$pullEndTick) {
            worldzero$restorePortal(level, state);
            player.noPhysics = false;
            if (WorldZeroVoidPortalDimension.worldzero$teleportPlayerToVoidPortal(player)) {
                state.worldzero$phase = Phase.IN_VOID;
                state.worldzero$returnTick = level.getGameTime() + WORLDZERO_VOID_RETURN_TICKS;
                return;
            }

            worldzero$stopNow(level.getServer());
            return;
        }

        Vec3 step = toPortal.normalize().scale(Mth.clamp(distance * 0.055D, WORLDZERO_PULL_STEP_MIN, WORLDZERO_PULL_STEP_MAX));
        player.noPhysics = true;
        player.teleportTo(level, player.getX() + step.x, player.getY() + step.y, player.getZ() + step.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        if (level.getGameTime() % 20L == 0L) {
            level.playSound(null, state.worldzero$portalPos, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.35F, 0.55F);
        }
    }

    private static void worldzero$restorePortal(ServerLevel level, ActiveState state) {
        if (level.getBlockState(state.worldzero$portalPos).is(Blocks.END_PORTAL)) {
            level.setBlock(state.worldzero$portalPos, state.worldzero$originalState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    @Nullable
    private static BlockPos worldzero$findPortalPos(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < 80; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double distance = Mth.nextDouble(level.random, 12.0D, 25.0D);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            int baseY = Mth.floor(player.getY());
            for (int yOffset = 4; yOffset >= -8; yOffset--) {
                BlockPos pos = new BlockPos(x, baseY + yOffset, z);
                if (worldzero$isPortalPosValid(level, pos)) {
                    return pos;
                }
            }
        }

        return null;
    }

    private static boolean worldzero$isPortalPosValid(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockPos abovePos = pos.above();
        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);
        AABB box = new AABB(
                pos.getX() + 0.05D,
                pos.getY(),
                pos.getZ() + 0.05D,
                pos.getX() + 0.95D,
                pos.getY() + 1.95D,
                pos.getZ() + 0.95D
        );
        return state.isAir()
                && level.getBlockState(abovePos).isAir()
                && belowState.isFaceSturdy(level, belowPos, Direction.UP)
                && belowState.getFluidState().isEmpty()
                && level.noCollision(box)
                && !level.containsAnyLiquid(box);
    }

    private static boolean worldzero$isValidPlayer(ServerPlayer player) {
        return player != null && player.isAlive() && !player.isSpectator();
    }

    private enum Phase {
        PULLING,
        IN_VOID
    }

    private static final class ActiveState {
        private final UUID worldzero$playerId;
        private final BlockPos worldzero$portalPos;
        private final BlockState worldzero$originalState;
        private final long worldzero$pullEndTick;
        private Phase worldzero$phase = Phase.PULLING;
        private long worldzero$returnTick;

        private ActiveState(UUID playerId, BlockPos portalPos, BlockState originalState, long pullEndTick) {
            this.worldzero$playerId = playerId;
            this.worldzero$portalPos = portalPos;
            this.worldzero$originalState = originalState;
            this.worldzero$pullEndTick = pullEndTick;
        }
    }
}
