package ru.nekostul.worldzero.event.stalker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.nekostul.worldzero.entity.WorldZeroEchoEntity;
import ru.nekostul.worldzero.entity.WorldZeroEntities;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class WorldZeroStalkerEvent {
    private static final int WORLDZERO_DURATION_TICKS = 60 * 20;
    private static final double WORLDZERO_LOOK_DOT = 0.72D;
    private static final double WORLDZERO_APPROACH_SPEED = 0.11D;
    private static final double WORLDZERO_WATCHED_FOLLOW_SPEED = 0.045D;
    private static final double WORLDZERO_VISIBLE_FOLLOW_DISTANCE = 13.0D;
    private static final double WORLDZERO_SPAWN_MIN_DISTANCE = 14.0D;
    private static final double WORLDZERO_SPAWN_MAX_DISTANCE = 26.0D;
    private static final double WORLDZERO_SPAWN_BACK_DOT = -0.35D;
    private static final double WORLDZERO_SPAWN_BACK_ARC = 0.72D;
    private static final int WORLDZERO_MAX_VERTICAL_OFFSET = 6;
    private static final double WORLDZERO_HIDE_DISTANCE = 12.0D;
    private static final double WORLDZERO_REVEAL_DISTANCE = 16.0D;
    private static final double WORLDZERO_DISTANCE_CHANGE_THRESHOLD = 0.18D;
    private static final double WORLDZERO_HOVER_Y_OFFSET = 0.62D;
    private static final double WORLDZERO_RISE_SPEED = 0.34D;
    private static final double WORLDZERO_SINK_SPEED = 0.52D;
    private static final double WORLDZERO_HIDDEN_MIN_DEPTH_BELOW_PLAYER = 8.5D;
    private static final double WORLDZERO_HIDDEN_MIN_DEPTH_BELOW_GROUND = 3.25D;
    private static final double WORLDZERO_MOB_INFLUENCE_RANGE = 28.0D;
    private static final double WORLDZERO_PASSIVE_FLEE_SPEED = 1.28D;
    private static final double WORLDZERO_HOSTILE_PULL_SPEED = 1.0D;
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
        if (!worldzero$isNight(level)) {
            return false;
        }

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
        echo.setNoGravity(true);
        if (!level.addFreshEntity(echo)) {
            return false;
        }

        WORLDZERO_STATES.put(server, new ActiveState(
                player.getUUID(),
                echo.getUUID(),
                level.getGameTime() + WORLDZERO_DURATION_TICKS
        ));
        worldzero$playSpawnStalkerSound(level, player, echo, 1.15F, 0.45F);
        return true;
    }

    public static void worldzero$tick(ServerLevel level) {
        ActiveState state = WORLDZERO_STATES.get(level.getServer());
        if (state == null || state.worldzero$dimension != null && state.worldzero$dimension != level.dimension()) {
            return;
        }

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(state.worldzero$playerId);
        Entity entity = level.getEntity(state.worldzero$echoId);
        if (!worldzero$isValidPlayer(player) || !(entity instanceof WorldZeroEchoEntity echo)) {
            worldzero$stopNow(level.getServer());
            return;
        }

        Vec3 echoPos = echo.position();
        Vec3 playerPos = player.position();
        double horizontalDistance = Math.sqrt(worldzero$horizontalDistanceSqr(echoPos, playerPos));
        if (level.getGameTime() >= state.worldzero$endTick) {
            state.worldzero$ending = true;
            state.worldzero$hidden = true;
        }

        boolean watched = worldzero$isWatched(player, echo);
        boolean movingAway = state.worldzero$lastHorizontalDistance >= 0.0D
                && horizontalDistance > state.worldzero$lastHorizontalDistance + WORLDZERO_DISTANCE_CHANGE_THRESHOLD;
        boolean movingCloser = state.worldzero$lastHorizontalDistance >= 0.0D
                && horizontalDistance < state.worldzero$lastHorizontalDistance - WORLDZERO_DISTANCE_CHANGE_THRESHOLD;
        boolean wasHidden = state.worldzero$hidden;

        if (state.worldzero$ending) {
            state.worldzero$hidden = true;
        } else if (state.worldzero$hidden) {
            if (horizontalDistance >= WORLDZERO_REVEAL_DISTANCE && movingAway) {
                state.worldzero$hidden = false;
            }
        } else if (horizontalDistance <= WORLDZERO_HIDE_DISTANCE
                || (movingCloser && horizontalDistance <= WORLDZERO_REVEAL_DISTANCE)) {
            state.worldzero$hidden = true;
        }

        if (!wasHidden && state.worldzero$hidden && !state.worldzero$hideSoundPlayed) {
            state.worldzero$hideSoundPlayed = true;
            worldzero$playStalkerSound(level, echo, 1.15F, 0.33F);
        }

        echo.setNoGravity(true);
        echo.setDeltaMovement(Vec3.ZERO);
        echo.setSprinting(false);
        worldzero$influenceNearbyMobs(level, player, echo, state);
        if (state.worldzero$hidden) {
            state.worldzero$watchedTicks = 0;
            worldzero$moveEchoUnderground(level, player, echo, horizontalDistance);
        } else {
            double targetY = worldzero$getHoverY(level, echo.getX(), echo.getZ());
            if (watched) {
                state.worldzero$watchedTicks++;
            } else {
                state.worldzero$watchedTicks = 0;
            }

            double followSpeed = !watched && horizontalDistance > WORLDZERO_VISIBLE_FOLLOW_DISTANCE
                    ? WORLDZERO_APPROACH_SPEED
                    : WORLDZERO_WATCHED_FOLLOW_SPEED;
            if (horizontalDistance > WORLDZERO_HIDE_DISTANCE + 0.25D) {
                worldzero$moveEchoToward(echo, level, playerPos, followSpeed, targetY);
            } else {
                worldzero$moveEchoVertical(echo, targetY, WORLDZERO_RISE_SPEED);
            }
        }
        state.worldzero$lastHorizontalDistance = horizontalDistance;

        echo.setYRot(worldzero$yawTo(echo.position(), player.position()));
        echo.yHeadRot = echo.getYRot();
        echo.yBodyRot = echo.getYRot();
        if (state.worldzero$ending && worldzero$hasReachedHiddenDepth(level, player, echo, horizontalDistance)) {
            worldzero$stopNow(level.getServer());
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

        ServerPlayer player = server.getPlayerList().getPlayer(state.worldzero$playerId);
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(state.worldzero$echoId);
            if (entity != null) {
                entity.discard();
            }

            worldzero$restoreNearbyMobs(level, player, state);
        }
        return true;
    }

    private static void worldzero$moveEchoToward(
            WorldZeroEchoEntity echo,
            ServerLevel level,
            Vec3 target,
            double speed,
            double targetY
    ) {
        Vec3 horizontal = new Vec3(target.x - echo.getX(), 0.0D, target.z - echo.getZ());
        if (horizontal.lengthSqr() < 0.0001D) {
            worldzero$moveEchoVertical(echo, targetY, WORLDZERO_RISE_SPEED);
            return;
        }

        Vec3 step = horizontal.normalize().scale(speed);
        double nextX = echo.getX() + step.x;
        double nextZ = echo.getZ() + step.z;
        double nextY = worldzero$stepTowardY(echo.getY(), targetY, WORLDZERO_RISE_SPEED);

        echo.setPos(nextX, nextY, nextZ);
        echo.hasImpulse = false;
    }

    private static void worldzero$moveEchoUnderground(
            ServerLevel level,
            ServerPlayer player,
            WorldZeroEchoEntity echo,
            double horizontalDistance
    ) {
        double targetY = worldzero$getHiddenY(level, player, echo.getX(), echo.getZ(), horizontalDistance);
        worldzero$moveEchoVertical(echo, targetY, WORLDZERO_SINK_SPEED);
    }

    private static void worldzero$moveEchoVertical(WorldZeroEchoEntity echo, double targetY, double maxStep) {
        echo.setPos(echo.getX(), worldzero$stepTowardY(echo.getY(), targetY, maxStep), echo.getZ());
        echo.hasImpulse = false;
    }

    private static boolean worldzero$hasReachedHiddenDepth(
            ServerLevel level,
            ServerPlayer player,
            WorldZeroEchoEntity echo,
            double horizontalDistance
    ) {
        double targetY = worldzero$getHiddenY(level, player, echo.getX(), echo.getZ(), horizontalDistance);
        return echo.getY() <= targetY + 0.15D;
    }

    private static double worldzero$getHoverY(ServerLevel level, double x, double z) {
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        return groundY + WORLDZERO_HOVER_Y_OFFSET;
    }

    private static double worldzero$getHiddenY(
            ServerLevel level,
            ServerPlayer player,
            double x,
            double z,
            double horizontalDistance
    ) {
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        double proximityDepth = Mth.clamp(WORLDZERO_HIDE_DISTANCE - horizontalDistance, 0.0D, WORLDZERO_HIDE_DISTANCE) * 0.55D;
        double belowGround = groundY - WORLDZERO_HIDDEN_MIN_DEPTH_BELOW_GROUND - proximityDepth;
        double belowPlayer = player.getY() - WORLDZERO_HIDDEN_MIN_DEPTH_BELOW_PLAYER - proximityDepth;
        return Math.min(belowGround, belowPlayer);
    }

    private static double worldzero$stepTowardY(double currentY, double targetY, double maxStep) {
        if (currentY < targetY) {
            return Math.min(targetY, currentY + maxStep);
        }
        return Math.max(targetY, currentY - maxStep);
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

    private static void worldzero$influenceNearbyMobs(
            ServerLevel level,
            ServerPlayer player,
            WorldZeroEchoEntity echo,
            ActiveState state
    ) {
        AABB area = echo.getBoundingBox().inflate(WORLDZERO_MOB_INFLUENCE_RANGE);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area, candidate ->
                candidate != null
                        && candidate.isAlive()
                        && candidate != echo
                        && candidate.getType() != WorldZeroEntities.WORLDZERO_BLACK_ECHO.get()
        )) {
            if (mob instanceof Monster monster) {
                if (monster.getTarget() == player) {
                    state.worldzero$retargetedHostiles.add(monster.getUUID());
                }
                if (monster.getTarget() != echo) {
                    monster.setTarget(echo);
                }
                monster.getNavigation().moveTo(echo, WORLDZERO_HOSTILE_PULL_SPEED);
                continue;
            }

            if (mob instanceof PathfinderMob pathfinderMob) {
                state.worldzero$spookedPassives.add(pathfinderMob.getUUID());
                Vec3 fleePos = DefaultRandomPos.getPosAway(pathfinderMob, 16, 7, echo.position());
                if (fleePos != null) {
                    pathfinderMob.getNavigation().moveTo(fleePos.x, fleePos.y, fleePos.z, WORLDZERO_PASSIVE_FLEE_SPEED);
                } else {
                    Vec3 fallback = pathfinderMob.position().subtract(echo.position());
                    Vec3 flatFallback = new Vec3(fallback.x, 0.0D, fallback.z);
                    if (flatFallback.lengthSqr() > 0.0001D) {
                        Vec3 target = pathfinderMob.position().add(flatFallback.normalize().scale(10.0D));
                        pathfinderMob.getNavigation().moveTo(target.x, pathfinderMob.getY(), target.z, WORLDZERO_PASSIVE_FLEE_SPEED);
                    }
                }
            }
        }
    }

    private static void worldzero$restoreNearbyMobs(ServerLevel level, @Nullable ServerPlayer player, ActiveState state) {
        for (UUID mobId : state.worldzero$spookedPassives) {
            Entity entity = level.getEntity(mobId);
            if (entity instanceof PathfinderMob pathfinderMob && !(pathfinderMob instanceof Monster)) {
                pathfinderMob.getNavigation().stop();
            }
        }

        for (UUID mobId : state.worldzero$retargetedHostiles) {
            Entity entity = level.getEntity(mobId);
            if (!(entity instanceof Monster monster) || !monster.isAlive()) {
                continue;
            }

            if (player != null && player.isAlive() && player.level() == level) {
                monster.setTarget(player);
                monster.getNavigation().moveTo(player, WORLDZERO_HOSTILE_PULL_SPEED);
            } else {
                monster.setTarget(null);
                monster.getNavigation().stop();
            }
        }
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
        Vec3 playerPos = player.position();
        double baseAngle = Math.atan2(-horizontalLook.z, -horizontalLook.x);

        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = baseAngle + Mth.nextDouble(level.random, -WORLDZERO_SPAWN_BACK_ARC, WORLDZERO_SPAWN_BACK_ARC);
            double distance = Mth.nextDouble(level.random, WORLDZERO_SPAWN_MIN_DISTANCE, WORLDZERO_SPAWN_MAX_DISTANCE);
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            double y = surfaceY + WORLDZERO_HOVER_Y_OFFSET;
            Vec3 candidate = new Vec3(x, y, z);
            if (worldzero$isSpawnValid(level, player, playerPos, horizontalLook, candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private static boolean worldzero$isSpawnValid(
            ServerLevel level,
            ServerPlayer player,
            Vec3 playerPos,
            Vec3 horizontalLook,
            Vec3 candidate
    ) {
        AABB box = WorldZeroEntities.WORLDZERO_BLACK_ECHO.get().getDimensions().makeBoundingBox(candidate.x, candidate.y, candidate.z);
        if (!level.noCollision(box) || level.containsAnyLiquid(box)) {
            return false;
        }

        double horizontalDistanceSqr = new Vec3(candidate.x - playerPos.x, 0.0D, candidate.z - playerPos.z).lengthSqr();
        if (horizontalDistanceSqr < WORLDZERO_SPAWN_MIN_DISTANCE * WORLDZERO_SPAWN_MIN_DISTANCE
                || horizontalDistanceSqr > WORLDZERO_SPAWN_MAX_DISTANCE * WORLDZERO_SPAWN_MAX_DISTANCE) {
            return false;
        }

        if (Math.abs(candidate.y - playerPos.y) > WORLDZERO_MAX_VERTICAL_OFFSET) {
            return false;
        }

        BlockPos feetPos = BlockPos.containing(candidate);
        BlockPos belowPos = feetPos.below();
        BlockState belowState = level.getBlockState(belowPos);
        if (!belowState.isFaceSturdy(level, belowPos, Direction.UP) || !belowState.getFluidState().isEmpty()) {
            return false;
        }

        if (!level.canSeeSky(feetPos) || !level.canSeeSky(feetPos.above())) {
            return false;
        }

        Vec3 toCandidate = new Vec3(candidate.x - playerPos.x, 0.0D, candidate.z - playerPos.z);
        if (toCandidate.lengthSqr() < 0.0001D) {
            return false;
        }

        if (horizontalLook.normalize().dot(toCandidate.normalize()) > WORLDZERO_SPAWN_BACK_DOT) {
            return false;
        }

        Vec3 target = new Vec3(candidate.x, candidate.y + 0.9D, candidate.z);
        return worldzero$hasSightLine(level, player, target);
    }

    private static boolean worldzero$hasSightLine(ServerLevel level, ServerPlayer player, Vec3 target) {
        Vec3 eyePos = player.getEyePosition();
        HitResult hitResult = level.clip(new ClipContext(
                eyePos,
                target,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        return hitResult.getType() == HitResult.Type.MISS
                || hitResult.getLocation().distanceToSqr(target) <= 1.25D;
    }

    private static float worldzero$yawTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
    }

    private static void worldzero$playStalkerSound(ServerLevel level, Entity stalker, float volume, float pitch) {
        if (level == null || stalker == null) {
            return;
        }

        level.playSound(
                null,
                stalker.getX(),
                stalker.getY(),
                stalker.getZ(),
                SoundEvents.AMBIENT_CAVE.value(),
                SoundSource.HOSTILE,
                volume,
                pitch
        );
    }

    private static void worldzero$playSpawnStalkerSound(
            ServerLevel level,
            ServerPlayer player,
            Entity stalker,
            float volume,
            float pitch
    ) {
        if (level == null || player == null || stalker == null) {
            return;
        }

        Vec3 fromPlayerToStalker = stalker.position().subtract(player.position());
        Vec3 horizontalDirection = new Vec3(fromPlayerToStalker.x, 0.0D, fromPlayerToStalker.z);
        if (horizontalDirection.lengthSqr() < 0.0001D) {
            worldzero$playStalkerSound(level, stalker, volume, pitch);
            return;
        }

        Vec3 soundPos = player.position()
                .add(horizontalDirection.normalize().scale(3.25D))
                .add(0.0D, 0.75D, 0.0D);
        level.playSound(
                null,
                soundPos.x,
                soundPos.y,
                soundPos.z,
                SoundEvents.AMBIENT_CAVE.value(),
                SoundSource.HOSTILE,
                volume,
                pitch
        );
    }

    private static double worldzero$horizontalDistanceSqr(Vec3 first, Vec3 second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return dx * dx + dz * dz;
    }

    private static boolean worldzero$isNight(ServerLevel level) {
        return level != null && level.isNight();
    }

    private static boolean worldzero$isValidPlayer(ServerPlayer player) {
        return player != null && player.isAlive() && !player.isSpectator();
    }

    private static final class ActiveState {
        private final UUID worldzero$playerId;
        private final UUID worldzero$echoId;
        private final long worldzero$endTick;
        private final net.minecraft.resources.ResourceKey<Level> worldzero$dimension = Level.OVERWORLD;
        private final Set<UUID> worldzero$spookedPassives = new HashSet<>();
        private final Set<UUID> worldzero$retargetedHostiles = new HashSet<>();
        private boolean worldzero$hidden;
        private boolean worldzero$ending;
        private boolean worldzero$hideSoundPlayed;
        private double worldzero$lastHorizontalDistance = -1.0D;
        private int worldzero$watchedTicks;

        private ActiveState(UUID playerId, UUID echoId, long endTick) {
            this.worldzero$playerId = playerId;
            this.worldzero$echoId = echoId;
            this.worldzero$endTick = endTick;
        }
    }
}
