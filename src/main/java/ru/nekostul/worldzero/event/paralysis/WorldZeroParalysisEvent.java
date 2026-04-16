package ru.nekostul.worldzero;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroParalysisEvent {
    private static final long WORLDZERO_DELAY_AFTER_FALL_TICKS = 20L * 60L * 20L;
    private static final int WORLDZERO_INITIAL_FREEZE_TICKS = 2 * 20;
    private static final int WORLDZERO_HEAD_MOVE_DELAY_TICKS = 3 * 20;
    private static final int WORLDZERO_ECHO_VISIBLE_TICKS = 6 * 20;
    private static final int WORLDZERO_BREATH_LINGER_TICKS = 2 * 20;
    private static final int WORLDZERO_FAKE_FALL_FREEZE_TICKS = 2 * 20;
    private static final int WORLDZERO_RETURN_TO_BED_TICKS = 2 * 20;
    private static final int WORLDZERO_CAMERA_ALIGN_TIMEOUT_TICKS = 15 * 20;
    private static final int WORLDZERO_SLEEP_START_MAX_TICKS = 40;
    private static final int WORLDZERO_BED_SEARCH_RADIUS = 12;
    private static final double WORLDZERO_BLACK_ECHO_FRONT_DISTANCE_BLOCKS = 3.0D;
    private static final double WORLDZERO_BED_ECHO_LENGTH_OFFSET_BLOCKS = 0.80D;
    private static final double WORLDZERO_BED_ECHO_HEIGHT_BLOCKS = 0.76D;
    private static final String WORLDZERO_SAVE_ID = "worldzero_paralysis_event";
    private static final Map<MinecraftServer, SessionState> WORLDZERO_SESSION_STATES = new WeakHashMap<>();

    private WorldZeroParalysisEvent() {
    }

    @SubscribeEvent
    public static void worldzero$onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.level instanceof ServerLevel level) || level.isClientSide() || level.dimension() != Level.OVERWORLD) {
            return;
        }

        MinecraftServer server = level.getServer();
        SessionState state = WORLDZERO_SESSION_STATES.computeIfAbsent(server, ignored -> new SessionState());
        if (state.worldzero$phase != Phase.INACTIVE) {
            worldzero$tickActiveEvent(level, state);
            return;
        }

        if (worldzero$hasConflictingEvent(server)) {
            return;
        }

        ParalysisSaveData saveData = worldzero$getSaveData(level);
        if (saveData.worldzero$completed || saveData.worldzero$scheduledTick < 0L) {
            return;
        }

        if (level.getGameTime() < saveData.worldzero$scheduledTick || !level.isNight()) {
            return;
        }

        StartTarget target = worldzero$pickSleepingTarget(level);
        if (target == null) {
            return;
        }

        if (worldzero$startEvent(level, state, saveData, target.player(), target.bedPos())) {
            saveData.worldzero$completed = true;
            saveData.setDirty();
        }
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_SESSION_STATES.remove(event.getServer());
    }

    public static void worldzero$scheduleAfterFall(ServerPlayer player) {
        if (player == null || player.level().dimension() != Level.OVERWORLD) {
            return;
        }

        ParalysisSaveData saveData = worldzero$getSaveData(player.serverLevel());
        if (saveData.worldzero$completed) {
            return;
        }

        saveData.worldzero$scheduledTick = player.serverLevel().getGameTime() + WORLDZERO_DELAY_AFTER_FALL_TICKS;
        saveData.setDirty();
    }

    public static boolean worldzero$triggerParalysisNow(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator() || player.level().dimension() != Level.OVERWORLD) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null || worldzero$hasConflictingEvent(server)) {
            return false;
        }

        SessionState state = WORLDZERO_SESSION_STATES.computeIfAbsent(server, ignored -> new SessionState());
        if (state.worldzero$phase != Phase.INACTIVE) {
            return false;
        }

        BlockPos bedPos = player.getSleepingPos()
                .map(pos -> worldzero$resolveBedBase(player.serverLevel(), pos))
                .orElseGet(() -> worldzero$findNearestBed(player.serverLevel(), player.blockPosition()));
        if (bedPos == null) {
            return false;
        }

        return worldzero$startEvent(player.serverLevel(), state, null, player, bedPos);
    }

    public static boolean worldzero$isParalysisActive(MinecraftServer server) {
        SessionState state = WORLDZERO_SESSION_STATES.get(server);
        return state != null && state.worldzero$phase != Phase.INACTIVE;
    }

    public static void worldzero$acknowledgeCameraAligned(ServerPlayer player) {
        if (player == null) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        SessionState state = WORLDZERO_SESSION_STATES.get(server);
        if (state == null || state.worldzero$phase != Phase.CAMERA_ALIGN || state.worldzero$targetPlayerId == null) {
            return;
        }

        if (!state.worldzero$targetPlayerId.equals(player.getUUID())) {
            return;
        }

        worldzero$beginBedWatch(player.serverLevel(), state);
    }

    private static boolean worldzero$startEvent(
            ServerLevel level,
            SessionState state,
            @Nullable ParalysisSaveData saveData,
            ServerPlayer player,
            BlockPos bedPos
    ) {
        if (state.worldzero$phase != Phase.INACTIVE) {
            return false;
        }

        state.worldzero$targetPlayerId = player.getUUID();
        state.worldzero$bedPos = bedPos.immutable();
        if (player.isSleeping()) {
            player.stopSleepInBed(false, true);
        }

        state.worldzero$lockedX = player.getX();
        state.worldzero$lockedY = player.getY();
        state.worldzero$lockedZ = player.getZ();
        Vec3 wakeLookTarget = worldzero$bedEchoBasePos(level, bedPos);
        double deltaX = wakeLookTarget.x - player.getX();
        double deltaY = (wakeLookTarget.y + 0.35D) - player.getEyeY();
        double deltaZ = wakeLookTarget.z - player.getZ();
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float targetYaw = (float) (Mth.atan2(deltaZ, deltaX) * (180.0D / Math.PI)) - 90.0F;
        float targetPitch = (float) (-(Mth.atan2(deltaY, horizontalDistance) * (180.0D / Math.PI)));
        state.worldzero$lockedYaw = targetYaw + 28.0F;
        state.worldzero$lockedPitch = targetPitch;
        state.worldzero$phase = Phase.INITIAL_FREEZE;
        state.worldzero$phaseStartTick = level.getGameTime();
        state.worldzero$phaseEndTick = level.getGameTime() + WORLDZERO_INITIAL_FREEZE_TICKS;
        state.worldzero$echoId = null;
        state.worldzero$blackEchoId = null;
        WorldZeroNetwork.sendFreezeStart(player, WORLDZERO_INITIAL_FREEZE_TICKS);

        if (saveData != null) {
            saveData.setDirty();
        }
        return true;
    }

    private static void worldzero$tickActiveEvent(ServerLevel level, SessionState state) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(state.worldzero$targetPlayerId);
        if (player == null || !player.isAlive() || player.isSpectator()) {
            worldzero$clearState(level.getServer(), state, null);
            return;
        }

        switch (state.worldzero$phase) {
            case INITIAL_FREEZE -> worldzero$tickInitialFreeze(level, state, player);
            case CAMERA_ALIGN -> worldzero$tickCameraAlign(level, state, player);
            case BED_WATCH -> worldzero$tickBedWatch(level, state, player);
            case BREATH_ONLY -> worldzero$tickBreathOnly(level, state, player);
            case FAKE_FALL_FREEZE -> worldzero$tickFakeFallFreeze(level, state, player);
            case RETURN_TO_BED -> worldzero$tickReturnToBed(level, state, player);
            default -> {
            }
        }
    }

    private static void worldzero$tickInitialFreeze(ServerLevel level, SessionState state, ServerPlayer player) {
        worldzero$applyPositionLock(player, state, true);
        if (level.getGameTime() < state.worldzero$phaseEndTick) {
            return;
        }

        WorldZeroNetwork.sendFreezeEnd(player);
        WorldZeroEchoEntity echo = worldzero$spawnBedEcho(level, player, state.worldzero$bedPos);
        if (echo == null) {
            worldzero$clearState(level.getServer(), state, player);
            return;
        }

        state.worldzero$echoId = echo.getUUID();
        state.worldzero$phase = Phase.CAMERA_ALIGN;
        state.worldzero$phaseStartTick = level.getGameTime();
        state.worldzero$phaseEndTick = level.getGameTime() + WORLDZERO_CAMERA_ALIGN_TIMEOUT_TICKS;
        WorldZeroNetwork.sendParalysisClientAction(
                player,
                WorldZeroParalysisClientPacket.WORLDZERO_ACTION_START_BED_VIEW,
                state.worldzero$bedPos,
                echo.getId(),
                0
        );
    }

    private static void worldzero$tickCameraAlign(ServerLevel level, SessionState state, ServerPlayer player) {
        worldzero$applyPositionLock(player, state, false);
        WorldZeroEchoEntity echo = worldzero$getActiveEcho(level.getServer(), state.worldzero$echoId);
        if (echo != null) {
            worldzero$updateBedEcho(level, state, echo, false);
        }

        if (level.getGameTime() >= state.worldzero$phaseEndTick) {
            worldzero$beginBedWatch(level, state);
        }
    }

    private static void worldzero$tickBedWatch(ServerLevel level, SessionState state, ServerPlayer player) {
        worldzero$applyPositionLock(player, state, false);
        WorldZeroEchoEntity echo = worldzero$getActiveEcho(level.getServer(), state.worldzero$echoId);
        if (echo != null) {
            worldzero$updateBedEcho(level, state, echo, true);
        }

        if (level.getGameTime() < state.worldzero$phaseEndTick) {
            return;
        }

        if (echo != null) {
            echo.discard();
        }

        state.worldzero$echoId = null;
        state.worldzero$phase = Phase.BREATH_ONLY;
        state.worldzero$phaseStartTick = level.getGameTime();
        state.worldzero$phaseEndTick = level.getGameTime() + WORLDZERO_BREATH_LINGER_TICKS;
        WorldZeroNetwork.sendParalysisClientAction(
                player,
                WorldZeroParalysisClientPacket.WORLDZERO_ACTION_ECHO_GONE,
                BlockPos.ZERO,
                -1,
                WORLDZERO_BREATH_LINGER_TICKS
        );
    }

    private static void worldzero$tickBreathOnly(ServerLevel level, SessionState state, ServerPlayer player) {
        worldzero$applyPositionLock(player, state, false);
        if (level.getGameTime() < state.worldzero$phaseEndTick) {
            return;
        }

        Entity blackEcho = worldzero$spawnFrontBlackEcho(level, player);
        state.worldzero$blackEchoId = blackEcho != null ? blackEcho.getUUID() : null;
        state.worldzero$phase = Phase.FAKE_FALL_FREEZE;
        state.worldzero$phaseStartTick = level.getGameTime();
        state.worldzero$phaseEndTick = level.getGameTime() + WORLDZERO_FAKE_FALL_FREEZE_TICKS;
        WorldZeroNetwork.sendFreezeStart(
                player,
                WORLDZERO_FAKE_FALL_FREEZE_TICKS,
                blackEcho != null ? blackEcho.getId() : -1
        );
        WorldZeroNetwork.sendParalysisClientAction(
                player,
                WorldZeroParalysisClientPacket.WORLDZERO_ACTION_START_WARNING,
                BlockPos.ZERO,
                -1,
                WORLDZERO_FAKE_FALL_FREEZE_TICKS
        );
    }

    private static void worldzero$tickFakeFallFreeze(ServerLevel level, SessionState state, ServerPlayer player) {
        worldzero$applyFakeFallFreeze(level.getServer(), state, player);
        if (level.getGameTime() < state.worldzero$phaseEndTick) {
            return;
        }

        Entity blackEcho = worldzero$findEntity(level.getServer(), state.worldzero$blackEchoId);
        if (blackEcho != null) {
            blackEcho.discard();
        }
        state.worldzero$blackEchoId = null;
        WorldZeroNetwork.sendFreezeEnd(player);
        worldzero$tryReturnPlayerToBed(level, state, player);
        WorldZeroNetwork.sendParalysisClientAction(
                player,
                WorldZeroParalysisClientPacket.WORLDZERO_ACTION_RETURN_TO_BED,
                state.worldzero$bedPos,
                -1,
                0
        );
        state.worldzero$phase = Phase.RETURN_TO_BED;
        state.worldzero$phaseStartTick = level.getGameTime();
        state.worldzero$phaseEndTick = level.getGameTime() + WORLDZERO_RETURN_TO_BED_TICKS;
    }

    private static void worldzero$tickReturnToBed(ServerLevel level, SessionState state, ServerPlayer player) {
        worldzero$tryReturnPlayerToBed(level, state, player);
        if (level.getGameTime() < state.worldzero$phaseEndTick) {
            return;
        }

        worldzero$setMorning(level);
        if (player.isSleeping()) {
            player.stopSleepInBed(false, true);
        }
        worldzero$clearState(level.getServer(), state, player);
    }

    private static void worldzero$applyPositionLock(ServerPlayer player, SessionState state, boolean restoreRotation) {
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.teleportTo(state.worldzero$lockedX, state.worldzero$lockedY, state.worldzero$lockedZ);
        player.fallDistance = 0.0F;
        if (restoreRotation) {
            player.setYRot(state.worldzero$lockedYaw);
            player.setYHeadRot(state.worldzero$lockedYaw);
            player.setYBodyRot(state.worldzero$lockedYaw);
            player.setXRot(state.worldzero$lockedPitch);
        }
    }

    private static void worldzero$applyFakeFallFreeze(MinecraftServer server, SessionState state, ServerPlayer player) {
        Entity blackEcho = worldzero$findEntity(server, state.worldzero$blackEchoId);
        float yaw = state.worldzero$lockedYaw;
        float pitch = state.worldzero$lockedPitch;
        if (blackEcho != null) {
            double deltaX = blackEcho.getX() - state.worldzero$lockedX;
            double deltaY = blackEcho.getEyeY() - player.getEyeHeight() - state.worldzero$lockedY;
            double deltaZ = blackEcho.getZ() - state.worldzero$lockedZ;
            double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            yaw = (float) (Mth.atan2(deltaZ, deltaX) * (180.0D / Math.PI)) - 90.0F;
            pitch = (float) (-(Mth.atan2(deltaY, horizontalDistance) * (180.0D / Math.PI)));
        }

        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.teleportTo(state.worldzero$lockedX, state.worldzero$lockedY, state.worldzero$lockedZ);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
        player.setXRot(pitch);
        player.fallDistance = 0.0F;
    }

    private static void worldzero$beginBedWatch(ServerLevel level, SessionState state) {
        if (state.worldzero$phase != Phase.CAMERA_ALIGN && state.worldzero$phase != Phase.BED_WATCH) {
            return;
        }

        state.worldzero$phase = Phase.BED_WATCH;
        state.worldzero$phaseStartTick = level.getGameTime();
        state.worldzero$phaseEndTick = level.getGameTime() + WORLDZERO_ECHO_VISIBLE_TICKS;
    }

    @Nullable
    private static StartTarget worldzero$pickSleepingTarget(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || !player.isSleeping()) {
                continue;
            }

            if (player.getSleepTimer() <= 0 || player.getSleepTimer() > WORLDZERO_SLEEP_START_MAX_TICKS) {
                continue;
            }

            Optional<BlockPos> sleepingPos = player.getSleepingPos();
            if (sleepingPos.isEmpty()) {
                continue;
            }

            BlockPos bedPos = worldzero$resolveBedBase(level, sleepingPos.get());
            if (bedPos == null) {
                continue;
            }

            return new StartTarget(player, bedPos);
        }
        return null;
    }

    @Nullable
    private static BlockPos worldzero$resolveBedBase(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.BEDS) || !(state.getBlock() instanceof BedBlock)) {
            return null;
        }

        if (!state.hasProperty(BedBlock.PART) || !state.hasProperty(BedBlock.FACING)) {
            return pos.immutable();
        }

        if (state.getValue(BedBlock.PART) == BedPart.HEAD) {
            return pos.relative(state.getValue(BedBlock.FACING).getOpposite()).immutable();
        }
        return pos.immutable();
    }

    @Nullable
    private static BlockPos worldzero$findNearestBed(ServerLevel level, BlockPos center) {
        BlockPos bestPos = null;
        double bestDistanceSqr = Double.MAX_VALUE;
        for (int x = center.getX() - WORLDZERO_BED_SEARCH_RADIUS; x <= center.getX() + WORLDZERO_BED_SEARCH_RADIUS; x++) {
            for (int y = center.getY() - 4; y <= center.getY() + 4; y++) {
                for (int z = center.getZ() - WORLDZERO_BED_SEARCH_RADIUS; z <= center.getZ() + WORLDZERO_BED_SEARCH_RADIUS; z++) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    BlockPos bedPos = worldzero$resolveBedBase(level, candidate);
                    if (bedPos == null) {
                        continue;
                    }

                    double distanceSqr = center.distSqr(bedPos);
                    if (distanceSqr < bestDistanceSqr) {
                        bestDistanceSqr = distanceSqr;
                        bestPos = bedPos;
                    }
                }
            }
        }
        return bestPos;
    }

    @Nullable
    private static WorldZeroEchoEntity worldzero$spawnBedEcho(ServerLevel level, ServerPlayer player, BlockPos bedPos) {
        BlockState bedState = level.getBlockState(bedPos);
        if (!(bedState.getBlock() instanceof BedBlock) || !bedState.hasProperty(BedBlock.FACING)) {
            return null;
        }

        WorldZeroEchoEntity echo = WorldZeroEntities.WORLDZERO_ECHO.get().create(level);
        if (echo == null) {
            return null;
        }

        Direction bedFacing = bedState.getValue(BedBlock.FACING);
        Vec3 basePos = worldzero$bedEchoBasePos(level, bedPos);
        echo.moveTo(basePos.x, basePos.y, basePos.z, bedFacing.toYRot(), 0.0F);
        echo.setCustomName(net.minecraft.network.chat.Component.literal(player.getGameProfile().getName()));
        echo.setCustomNameVisible(false);
        echo.setNoGravity(true);
        echo.setSilent(true);
        echo.setSleepingPos(bedPos);
        echo.setPose(Pose.SLEEPING);
        echo.worldzero$setParalysisBedActive(true);
        level.addFreshEntity(echo);
        return echo;
    }

    private static void worldzero$updateBedEcho(
            ServerLevel level,
            SessionState state,
            WorldZeroEchoEntity echo,
            boolean allowHeadShake
    ) {
        BlockState bedState = level.getBlockState(state.worldzero$bedPos);
        Direction bedFacing = bedState.hasProperty(BedBlock.FACING) ? bedState.getValue(BedBlock.FACING) : Direction.SOUTH;
        long ticksSinceStart = level.getGameTime() - state.worldzero$phaseStartTick;
        double breathOffsetY = Math.sin(ticksSinceStart * 0.18D) * 0.018D;
        float headYawOffset = 0.0F;
        if (allowHeadShake && ticksSinceStart >= WORLDZERO_HEAD_MOVE_DELAY_TICKS) {
            float shakeTicks = (float) (ticksSinceStart - WORLDZERO_HEAD_MOVE_DELAY_TICKS);
            headYawOffset = (float) Math.sin(shakeTicks * 0.42D) * 18.0F;
        }

        float bodyYaw = bedFacing.toYRot();
        float headYaw = bodyYaw + headYawOffset;
        float pitch = allowHeadShake && ticksSinceStart >= WORLDZERO_HEAD_MOVE_DELAY_TICKS
                ? (float) Math.sin((ticksSinceStart - WORLDZERO_HEAD_MOVE_DELAY_TICKS) * 0.24D) * 4.0F
                : 0.0F;
        Vec3 basePos = worldzero$bedEchoBasePos(level, state.worldzero$bedPos);
        echo.setSleepingPos(state.worldzero$bedPos);
        echo.setPose(Pose.SLEEPING);
        echo.setPos(
                basePos.x,
                basePos.y + breathOffsetY,
                basePos.z
        );
        echo.setYRot(bodyYaw);
        echo.yRotO = bodyYaw;
        echo.setYHeadRot(headYaw);
        echo.yHeadRot = headYaw;
        echo.yHeadRotO = headYaw;
        echo.yBodyRot = bodyYaw;
        echo.yBodyRotO = bodyYaw;
        echo.setXRot(pitch);
        echo.xRotO = pitch;
        echo.setDeltaMovement(0.0D, 0.0D, 0.0D);
    }

    private static Vec3 worldzero$bedEchoBasePos(ServerLevel level, BlockPos bedPos) {
        BlockState bedState = level.getBlockState(bedPos);
        Direction bedFacing = bedState.hasProperty(BedBlock.FACING) ? bedState.getValue(BedBlock.FACING) : Direction.SOUTH;
        return new Vec3(
                bedPos.getX() + 0.5D + bedFacing.getStepX() * WORLDZERO_BED_ECHO_LENGTH_OFFSET_BLOCKS,
                bedPos.getY() + WORLDZERO_BED_ECHO_HEIGHT_BLOCKS,
                bedPos.getZ() + 0.5D + bedFacing.getStepZ() * WORLDZERO_BED_ECHO_LENGTH_OFFSET_BLOCKS
        );
    }

    private static void worldzero$tryReturnPlayerToBed(ServerLevel level, SessionState state, ServerPlayer player) {
        Vec3 bedPos = worldzero$bedEchoBasePos(level, state.worldzero$bedPos);
        player.teleportTo(bedPos.x, Math.max(level.getMinBuildHeight(), state.worldzero$bedPos.getY() + 0.2D), bedPos.z);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        if (!player.isSleeping()) {
            player.startSleepInBed(state.worldzero$bedPos);
        }
    }

    @Nullable
    private static Entity worldzero$spawnFrontBlackEcho(ServerLevel level, ServerPlayer player) {
        Vec3 look = player.getViewVector(1.0F);
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() < 0.0001D) {
            float radians = (float) Math.toRadians(player.getYRot());
            horizontalLook = new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
        }
        horizontalLook = horizontalLook.normalize();
        Vec3 basePosition = player.position().add(horizontalLook.scale(WORLDZERO_BLACK_ECHO_FRONT_DISTANCE_BLOCKS));
        int baseX = Mth.floor(basePosition.x);
        int baseZ = Mth.floor(basePosition.z);
        int playerFeetY = Mth.floor(player.getY());

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = playerFeetY + 2; y >= playerFeetY - 4; y--) {
                    BlockPos spawnPos = new BlockPos(baseX + dx, y, baseZ + dz);
                    if (!worldzero$isValidEchoSpawn(level, spawnPos)) {
                        continue;
                    }

                    WorldZeroEchoEntity blackEcho = WorldZeroEntities.WORLDZERO_BLACK_ECHO.get().create(level);
                    if (blackEcho == null) {
                        return null;
                    }

                    double spawnX = spawnPos.getX() + 0.5D;
                    double spawnY = spawnPos.getY();
                    double spawnZ = spawnPos.getZ() + 0.5D;
                    double deltaX = player.getX() - spawnX;
                    double deltaZ = player.getZ() - spawnZ;
                    float yaw = (float) (Mth.atan2(deltaZ, deltaX) * (180.0D / Math.PI)) - 90.0F;
                    blackEcho.moveTo(spawnX, spawnY, spawnZ, yaw, 0.0F);
                    blackEcho.setSilent(true);
                    level.addFreshEntity(blackEcho);
                    return blackEcho;
                }
            }
        }
        return null;
    }

    private static boolean worldzero$isValidEchoSpawn(ServerLevel level, BlockPos spawnPos) {
        BlockState belowState = level.getBlockState(spawnPos.below());
        if (!belowState.isFaceSturdy(level, spawnPos.below(), Direction.UP) || !belowState.getFluidState().isEmpty()) {
            return false;
        }

        if (!level.getBlockState(spawnPos).isAir() || !level.getBlockState(spawnPos.above()).isAir()) {
            return false;
        }

        AABB spawnBox = WorldZeroEntities.WORLDZERO_BLACK_ECHO.get().getDimensions().makeBoundingBox(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D
        );
        return level.noCollision(spawnBox) && !level.containsAnyLiquid(spawnBox);
    }

    @Nullable
    private static WorldZeroEchoEntity worldzero$getActiveEcho(MinecraftServer server, @Nullable UUID echoId) {
        Entity entity = worldzero$findEntity(server, echoId);
        return entity instanceof WorldZeroEchoEntity echo ? echo : null;
    }

    @Nullable
    private static Entity worldzero$findEntity(MinecraftServer server, @Nullable UUID entityId) {
        if (server == null || entityId == null) {
            return null;
        }

        for (ServerLevel serverLevel : server.getAllLevels()) {
            Entity entity = serverLevel.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static void worldzero$setMorning(ServerLevel level) {
        long dayTime = level.getDayTime();
        long nextMorning = ((dayTime / 24000L) + 1L) * 24000L + 1000L;
        level.setDayTime(nextMorning);
    }

    private static boolean worldzero$hasConflictingEvent(MinecraftServer server) {
        return WorldZeroFreezeEvent.worldzero$isFreezeActive(server)
                || WorldZeroFallEvent.worldzero$isFallActive(server)
                || WorldZeroFootstepsEvent.worldzero$isFootstepsActive(server)
                || WorldZeroHouseEvent.worldzero$isHouseActive(server)
                || worldzero$isParalysisActive(server);
    }

    private static void worldzero$clearState(
            MinecraftServer server,
            SessionState state,
            @Nullable ServerPlayer player
    ) {
        Entity echo = worldzero$findEntity(server, state.worldzero$echoId);
        if (echo != null) {
            echo.discard();
        }

        Entity blackEcho = worldzero$findEntity(server, state.worldzero$blackEchoId);
        if (blackEcho != null) {
            blackEcho.discard();
        }

        if (player != null) {
            WorldZeroNetwork.sendParalysisClientAction(
                    player,
                    WorldZeroParalysisClientPacket.WORLDZERO_ACTION_CLEAR,
                    BlockPos.ZERO,
                    -1,
                    0
            );
        }

        state.worldzero$phase = Phase.INACTIVE;
        state.worldzero$targetPlayerId = null;
        state.worldzero$bedPos = null;
        state.worldzero$echoId = null;
        state.worldzero$blackEchoId = null;
        state.worldzero$phaseStartTick = -1L;
        state.worldzero$phaseEndTick = -1L;
    }

    private static ParalysisSaveData worldzero$getSaveData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ParalysisSaveData::load, ParalysisSaveData::new, WORLDZERO_SAVE_ID);
    }

    private enum Phase {
        INACTIVE,
        INITIAL_FREEZE,
        CAMERA_ALIGN,
        BED_WATCH,
        BREATH_ONLY,
        FAKE_FALL_FREEZE,
        RETURN_TO_BED
    }

    private static final class SessionState {
        private Phase worldzero$phase = Phase.INACTIVE;
        private UUID worldzero$targetPlayerId;
        private BlockPos worldzero$bedPos;
        private UUID worldzero$echoId;
        private UUID worldzero$blackEchoId;
        private long worldzero$phaseStartTick = -1L;
        private long worldzero$phaseEndTick = -1L;
        private double worldzero$lockedX;
        private double worldzero$lockedY;
        private double worldzero$lockedZ;
        private float worldzero$lockedYaw;
        private float worldzero$lockedPitch;
    }

    private static final class ParalysisSaveData extends SavedData {
        private long worldzero$scheduledTick = -1L;
        private boolean worldzero$completed;

        public static ParalysisSaveData load(CompoundTag tag) {
            ParalysisSaveData saveData = new ParalysisSaveData();
            saveData.worldzero$scheduledTick = tag.getLong("scheduled_tick");
            saveData.worldzero$completed = tag.getBoolean("completed");
            return saveData;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putLong("scheduled_tick", this.worldzero$scheduledTick);
            tag.putBoolean("completed", this.worldzero$completed);
            return tag;
        }
    }

    private record StartTarget(ServerPlayer player, BlockPos bedPos) {
    }
}
