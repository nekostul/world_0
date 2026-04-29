package ru.nekostul.worldzero;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class WorldZeroStalkerEvent {
    private static final int WORLDZERO_DURATION_TICKS = 90 * 20;
    private static final double WORLDZERO_LOOK_DOT = 0.72D;
    private static final double WORLDZERO_APPROACH_SPEED = 0.11D;
    private static final double WORLDZERO_RETREAT_SPEED = 0.055D;
    private static final double WORLDZERO_END_DISTANCE = 7.0D;
    private static final double WORLDZERO_MIN_DISTANCE = 5.0D;
    private static final Map<MinecraftServer, ActiveState> WORLDZERO_STATES = new WeakHashMap<>();

    private WorldZeroStalkerEvent() {
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
        Vec3 spawnPos = worldzero$findSpawn(level, player);
        if (spawnPos == null) {
            return false;
        }

        WorldZeroEchoEntity echo = WorldZeroEntities.WORLDZERO_BLACK_ECHO.get().create(level);
        if (echo == null) {
            return false;
        }

        echo.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, worldzero$yawTo(spawnPos, player.position()), 0.0F);
        echo.setDeltaMovement(0.0D, 0.0D, 0.0D);
        echo.setSilent(true);
        if (!level.addFreshEntity(echo)) {
            return false;
        }

        WORLDZERO_STATES.put(server, new ActiveState(
                player.getUUID(),
                echo.getUUID(),
                level.getGameTime() + WORLDZERO_DURATION_TICKS,
                level.random.nextBoolean()
        ));
        level.playSound(
                null,
                echo.blockPosition(),
                SoundEvents.AMBIENT_CAVE.value(),
                SoundSource.HOSTILE,
                0.85F,
                0.45F
        );
        return true;
    }

    public static void worldzero$tick(ServerLevel level) {
        ActiveState state = WORLDZERO_STATES.get(level.getServer());
        if (state == null || state.worldzero$dimension != null && state.worldzero$dimension != level.dimension()) {
            return;
        }

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(state.worldzero$playerId);
        Entity entity = level.getEntity(state.worldzero$echoId);
        if (!worldzero$isValidPlayer(player) || !(entity instanceof WorldZeroEchoEntity echo) || level.getGameTime() >= state.worldzero$endTick) {
            worldzero$stopNow(level.getServer());
            return;
        }

        Vec3 echoPos = echo.position();
        Vec3 playerPos = player.position();
        double distanceSqr = echo.distanceToSqr(player);
        if (distanceSqr <= WORLDZERO_END_DISTANCE * WORLDZERO_END_DISTANCE) {
            worldzero$stopNow(level.getServer());
            return;
        }

        boolean watched = worldzero$isWatched(player, echo);
        if (watched) {
            state.worldzero$watchedTicks++;
            if (state.worldzero$retreatWhenWatched && state.worldzero$watchedTicks % 3 == 0) {
                worldzero$moveEcho(echo, echoPos.subtract(playerPos), WORLDZERO_RETREAT_SPEED);
            }
        } else {
            state.worldzero$watchedTicks = 0;
            if (distanceSqr > WORLDZERO_MIN_DISTANCE * WORLDZERO_MIN_DISTANCE) {
                worldzero$moveEcho(echo, playerPos.subtract(echoPos), WORLDZERO_APPROACH_SPEED);
            }
        }

        echo.setYRot(worldzero$yawTo(echo.position(), player.position()));
        echo.yHeadRot = echo.getYRot();
        echo.yBodyRot = echo.getYRot();
        if (level.getGameTime() % 90L == 0L) {
            level.playSound(null, echo.blockPosition(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.HOSTILE, 0.25F, 0.55F);
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

        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(state.worldzero$echoId);
            if (entity != null) {
                entity.discard();
                break;
            }
        }
        return true;
    }

    private static void worldzero$moveEcho(WorldZeroEchoEntity echo, Vec3 direction, double speed) {
        Vec3 horizontal = new Vec3(direction.x, 0.0D, direction.z);
        if (horizontal.lengthSqr() < 0.0001D) {
            return;
        }

        Vec3 step = horizontal.normalize().scale(speed);
        double nextX = echo.getX() + step.x;
        double nextZ = echo.getZ() + step.z;
        double nextY = echo.getY();
        if (echo.level() instanceof ServerLevel level) {
            int groundY = level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Mth.floor(nextX),
                    Mth.floor(nextZ)
            );
            double clampedGroundY = Math.min((double) groundY, echo.getY() + 1.05D);
            if (clampedGroundY >= echo.getY() - 1.5D) {
                nextY = clampedGroundY;
            }
        }

        echo.setPos(nextX, nextY, nextZ);
        echo.setDeltaMovement(0.0D, 0.0D, 0.0D);
        echo.setSprinting(true);
        echo.hasImpulse = true;
    }

    private static boolean worldzero$isWatched(ServerPlayer player, Entity entity) {
        if (!player.hasLineOfSight(entity)) {
            return false;
        }

        Vec3 toEcho = entity.getBoundingBox().getCenter().subtract(player.getEyePosition());
        if (toEcho.lengthSqr() < 0.0001D) {
            return false;
        }

        return player.getViewVector(1.0F).normalize().dot(toEcho.normalize()) >= WORLDZERO_LOOK_DOT;
    }

    @Nullable
    private static Vec3 worldzero$findSpawn(ServerLevel level, ServerPlayer player) {
        Vec3 look = player.getViewVector(1.0F);
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() < 0.0001D) {
            float radians = (float) Math.toRadians(player.getYRot());
            horizontalLook = new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
        }
        horizontalLook = horizontalLook.normalize();
        double baseAngle = Math.atan2(horizontalLook.z, horizontalLook.x) + Math.PI;

        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = baseAngle + Mth.nextDouble(level.random, -1.1D, 1.1D);
            double distance = Mth.nextDouble(level.random, 18.0D, 24.0D);
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;
            int baseY = Mth.floor(player.getY());
            for (int yOffset = 2; yOffset >= -5; yOffset--) {
                Vec3 candidate = new Vec3(x, baseY + yOffset, z);
                if (worldzero$isSpawnValid(level, candidate)) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private static boolean worldzero$isSpawnValid(ServerLevel level, Vec3 candidate) {
        AABB box = WorldZeroEntities.WORLDZERO_BLACK_ECHO.get().getDimensions().makeBoundingBox(candidate.x, candidate.y, candidate.z);
        if (!level.noCollision(box) || level.containsAnyLiquid(box)) {
            return false;
        }

        BlockPos feetPos = BlockPos.containing(candidate);
        BlockPos belowPos = feetPos.below();
        BlockState belowState = level.getBlockState(belowPos);
        return belowState.isFaceSturdy(level, belowPos, Direction.UP) && belowState.getFluidState().isEmpty();
    }

    private static float worldzero$yawTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
    }

    private static boolean worldzero$isValidPlayer(ServerPlayer player) {
        return player != null && player.isAlive() && !player.isSpectator();
    }

    private static final class ActiveState {
        private final UUID worldzero$playerId;
        private final UUID worldzero$echoId;
        private final long worldzero$endTick;
        private final boolean worldzero$retreatWhenWatched;
        private final net.minecraft.resources.ResourceKey<Level> worldzero$dimension = Level.OVERWORLD;
        private int worldzero$watchedTicks;

        private ActiveState(UUID playerId, UUID echoId, long endTick, boolean retreatWhenWatched) {
            this.worldzero$playerId = playerId;
            this.worldzero$echoId = echoId;
            this.worldzero$endTick = endTick;
            this.worldzero$retreatWhenWatched = retreatWhenWatched;
        }
    }
}
