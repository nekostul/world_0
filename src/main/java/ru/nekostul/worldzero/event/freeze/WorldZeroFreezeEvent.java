package ru.nekostul.worldzero;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroFreezeEvent {
    private static final long WORLDZERO_FREEZE_WINDOW_START_TICKS = 45L * 60L * 20L;
    private static final long WORLDZERO_FREEZE_WINDOW_END_TICKS = 60L * 60L * 20L;
    private static final long WORLDZERO_DELAY_AFTER_FALL_MIN_TICKS = 15L * 60L * 20L;
    private static final long WORLDZERO_DELAY_AFTER_FALL_MAX_TICKS = 30L * 60L * 20L;
    private static final int WORLDZERO_FREEZE_DURATION_TICKS = 5 * 20;
    private static final int WORLDZERO_BLACK_ECHO_PASS_MIN_TICKS = 20;
    private static final int WORLDZERO_BLACK_ECHO_PASS_MAX_TICKS = 40;
    private static final double WORLDZERO_BLACK_ECHO_TRAVEL_MIN_BLOCKS = 6.0D;
    private static final double WORLDZERO_BLACK_ECHO_TRAVEL_MAX_BLOCKS = 9.0D;
    private static final double WORLDZERO_BLACK_ECHO_SPAWN_SIDE_MIN_BLOCKS = 12.0D;
    private static final double WORLDZERO_BLACK_ECHO_SPAWN_SIDE_MAX_BLOCKS = 18.0D;
    private static final double WORLDZERO_BLACK_ECHO_SPAWN_FORWARD_MIN_BLOCKS = 10.0D;
    private static final double WORLDZERO_BLACK_ECHO_SPAWN_FORWARD_MAX_BLOCKS = 16.0D;
    private static final String WORLDZERO_SAVE_ID = "worldzero_freeze_event";
    private static final AABB WORLDZERO_ENTITY_SCAN_AABB = new AABB(
            -30_000_000.0D,
            -2_048.0D,
            -30_000_000.0D,
            30_000_000.0D,
            4_096.0D,
            30_000_000.0D
    );
    private static final Map<MinecraftServer, SessionState> WORLDZERO_SESSION_STATES = new WeakHashMap<>();

    private WorldZeroFreezeEvent() {
    }

    @SubscribeEvent
    public static void worldzero$onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.level instanceof ServerLevel level) || level.isClientSide()) {
            return;
        }

        if (level.dimension() != Level.OVERWORLD) {
            return;
        }

        MinecraftServer server = level.getServer();
        SessionState state = WORLDZERO_SESSION_STATES.computeIfAbsent(server, ignored -> new SessionState());
        if (state.worldzero$eventActive) {
            worldzero$applyFreeze(server, state);
            if (level.getGameTime() >= state.worldzero$freezeEndTick) {
                worldzero$finishFreeze(level, state);
            }
            return;
        }

        if (WorldZeroFallEvent.worldzero$isFallActive(server)) {
            return;
        }

        if (WorldZeroAmbientSoundEvent.worldzero$isMajorEventStartBlocked(level)) {
            return;
        }

        FreezeSaveData saveData = worldzero$getSaveData(level);
        if (saveData.worldzero$completed) {
            return;
        }

        if (saveData.worldzero$triggerTick < 0L) {
            long tickSpan = WORLDZERO_FREEZE_WINDOW_END_TICKS - WORLDZERO_FREEZE_WINDOW_START_TICKS + 1L;
            saveData.worldzero$triggerTick = WORLDZERO_FREEZE_WINDOW_START_TICKS
                    + (long) (level.random.nextDouble() * tickSpan);
            saveData.setDirty();
        }

        long gameTime = level.getGameTime();
        if (!state.worldzero$eventTriggered) {
            if (gameTime > WORLDZERO_FREEZE_WINDOW_END_TICKS
                    && saveData.worldzero$triggerTick <= WORLDZERO_FREEZE_WINDOW_END_TICKS) {
                state.worldzero$eventTriggered = true;
                saveData.worldzero$completed = true;
                saveData.setDirty();
            } else if (gameTime >= saveData.worldzero$triggerTick) {
                if (worldzero$tryStartFreezeEvent(level, state, saveData)) {
                    state.worldzero$eventTriggered = true;
                }
            }
        }
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_SESSION_STATES.remove(event.getServer());
    }

    public static boolean worldzero$triggerFreezeNow(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        SessionState state = WORLDZERO_SESSION_STATES.computeIfAbsent(level.getServer(), ignored -> new SessionState());
        if (state.worldzero$eventActive) {
            return false;
        }

        return worldzero$startFreezeEvent(level, state, player, null);
    }

    public static boolean worldzero$isFreezeActive(MinecraftServer server) {
        if (server == null) {
            return false;
        }

        SessionState state = WORLDZERO_SESSION_STATES.get(server);
        return state != null && state.worldzero$eventActive;
    }

    public static void worldzero$rescheduleAfterFall(ServerLevel level) {
        if (level == null || level.isClientSide() || level.dimension() != Level.OVERWORLD) {
            return;
        }

        FreezeSaveData saveData = worldzero$getSaveData(level);
        if (saveData.worldzero$completed) {
            return;
        }

        saveData.worldzero$triggerTick = level.getGameTime() + worldzero$randomDelayAfterFall(level);
        saveData.setDirty();
    }

    private static boolean worldzero$tryStartFreezeEvent(
            ServerLevel level,
            SessionState state,
            FreezeSaveData saveData
    ) {
        ServerPlayer targetPlayer = worldzero$pickTargetPlayer(level);
        if (targetPlayer == null) {
            return false;
        }

        return worldzero$startFreezeEvent(level, state, targetPlayer, saveData);
    }

    private static boolean worldzero$startFreezeEvent(
            ServerLevel level,
            SessionState state,
            ServerPlayer targetPlayer,
            @javax.annotation.Nullable FreezeSaveData saveData
    ) {
        state.worldzero$eventActive = true;
        state.worldzero$eventTriggered = true;
        state.worldzero$freezeEndTick = level.getGameTime() + WORLDZERO_FREEZE_DURATION_TICKS;
        state.worldzero$targetPlayerId = targetPlayer.getUUID();
        state.worldzero$lockedX = targetPlayer.getX();
        state.worldzero$lockedY = targetPlayer.getY();
        state.worldzero$lockedZ = targetPlayer.getZ();
        state.worldzero$lockedYaw = targetPlayer.getYRot();
        state.worldzero$lockedPitch = targetPlayer.getXRot();
        WorldZeroAmbientSoundEvent.worldzero$notifyMajorEventStarted(level);

        if (saveData != null) {
            saveData.worldzero$completed = true;
            saveData.setDirty();
            WorldZeroFallEvent.worldzero$rescheduleAfterFreeze(level);
        }

        worldzero$spawnFreezeBlackEcho(level, targetPlayer);
        WorldZeroNetwork.sendFreezeStart(targetPlayer, WORLDZERO_FREEZE_DURATION_TICKS);
        return true;
    }

    private static ServerPlayer worldzero$pickTargetPlayer(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator()) {
                return player;
            }
        }

        return null;
    }

    private static void worldzero$applyFreeze(MinecraftServer server, SessionState state) {
        ServerPlayer targetPlayer = server.getPlayerList().getPlayer(state.worldzero$targetPlayerId);
        if (targetPlayer != null && targetPlayer.isAlive()) {
            targetPlayer.setDeltaMovement(0.0D, 0.0D, 0.0D);
            targetPlayer.teleportTo(state.worldzero$lockedX, state.worldzero$lockedY, state.worldzero$lockedZ);
            targetPlayer.setYRot(state.worldzero$lockedYaw);
            targetPlayer.setYHeadRot(state.worldzero$lockedYaw);
            targetPlayer.setYBodyRot(state.worldzero$lockedYaw);
            targetPlayer.setXRot(state.worldzero$lockedPitch);
            targetPlayer.fallDistance = 0.0F;
        }

        for (ServerLevel serverLevel : server.getAllLevels()) {
            for (Mob mob : serverLevel.getEntitiesOfClass(
                    Mob.class,
                    WORLDZERO_ENTITY_SCAN_AABB,
                    candidate -> candidate.getType() != WorldZeroEntities.WORLDZERO_BLACK_ECHO.get()
            )) {
                mob.setDeltaMovement(0.0D, 0.0D, 0.0D);
                mob.getNavigation().stop();
            }
        }
    }

    private static void worldzero$finishFreeze(ServerLevel level, SessionState state) {
        state.worldzero$eventActive = false;
        ServerPlayer targetPlayer = level.getServer().getPlayerList().getPlayer(state.worldzero$targetPlayerId);
        if (targetPlayer != null) {
            WorldZeroNetwork.sendFreezeEnd(targetPlayer);
            targetPlayer.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("java.lang.OutOfMemoryError: Java heap space")
                            .withStyle(ChatFormatting.GRAY),
                    false
            );
            targetPlayer.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("Unable to free world instance (world_0)")
                            .withStyle(ChatFormatting.GRAY),
                    false
            );
        }
    }

    private static void worldzero$spawnFreezeBlackEcho(ServerLevel level, ServerPlayer player) {
        Vec3 look = player.getViewVector(1.0F);
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() < 0.0001D) {
            float radians = (float) Math.toRadians(player.getYRot());
            horizontalLook = new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
        }
        horizontalLook = horizontalLook.normalize();
        Vec3 side = new Vec3(-horizontalLook.z, 0.0D, horizontalLook.x).normalize();

        for (int attempt = 0; attempt < 24; attempt++) {
            double sideSign = (attempt % 2 == 0) ? 1.0D : -1.0D;
            double sideDistance = Mth.nextDouble(
                    level.random,
                    WORLDZERO_BLACK_ECHO_SPAWN_SIDE_MIN_BLOCKS,
                    WORLDZERO_BLACK_ECHO_SPAWN_SIDE_MAX_BLOCKS
            );
            double forwardDistance = Mth.nextDouble(
                    level.random,
                    WORLDZERO_BLACK_ECHO_SPAWN_FORWARD_MIN_BLOCKS,
                    WORLDZERO_BLACK_ECHO_SPAWN_FORWARD_MAX_BLOCKS
            );

            Vec3 spawnCandidate = player.position()
                    .add(horizontalLook.scale(forwardDistance))
                    .add(side.scale(sideDistance * sideSign));

            if (worldzero$trySpawnBlackEcho(level, player, spawnCandidate, side.scale(sideSign))) {
                return;
            }
        }
    }

    private static boolean worldzero$trySpawnBlackEcho(
            ServerLevel level,
            ServerPlayer player,
            Vec3 spawnCandidate,
            Vec3 travelDirection
    ) {
        int blockX = Mth.floor(spawnCandidate.x);
        int blockZ = Mth.floor(spawnCandidate.z);
        int spawnY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
        BlockPos spawnPos = new BlockPos(blockX, spawnY, blockZ);
        BlockPos belowPos = spawnPos.below();
        BlockState belowState = level.getBlockState(belowPos);

        if (!belowState.isFaceSturdy(level, belowPos, net.minecraft.core.Direction.UP)) {
            return false;
        }

        if (!level.getBlockState(spawnPos).isAir() || !level.getBlockState(spawnPos.above()).isAir()) {
            return false;
        }

        double spawnX = blockX + 0.5D;
        double spawnZ = blockZ + 0.5D;
        AABB spawnBox = WorldZeroEntities.WORLDZERO_BLACK_ECHO.get().getDimensions().makeBoundingBox(spawnX, spawnY, spawnZ);
        if (!level.noCollision(spawnBox) || level.containsAnyLiquid(spawnBox)) {
            return false;
        }

        WorldZeroEchoEntity blackEcho = WorldZeroEntities.WORLDZERO_BLACK_ECHO.get().create(level);
        if (blackEcho == null) {
            return false;
        }

        int passDuration = Mth.nextInt(level.random, WORLDZERO_BLACK_ECHO_PASS_MIN_TICKS, WORLDZERO_BLACK_ECHO_PASS_MAX_TICKS);
        double travelDistance = Mth.nextDouble(level.random, WORLDZERO_BLACK_ECHO_TRAVEL_MIN_BLOCKS, WORLDZERO_BLACK_ECHO_TRAVEL_MAX_BLOCKS);
        double speed = travelDistance / (double) passDuration;
        blackEcho.moveTo(spawnX, spawnY, spawnZ, player.getYRot(), 0.0F);
        blackEcho.setSilent(false);
        blackEcho.worldzero$configureFreezePass(travelDirection.x, travelDirection.z, speed, passDuration);
        level.addFreshEntity(blackEcho);
        return true;
    }

    private static FreezeSaveData worldzero$getSaveData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                FreezeSaveData::worldzero$load,
                FreezeSaveData::new,
                WORLDZERO_SAVE_ID
        );
    }

    private static long worldzero$randomDelayAfterFall(ServerLevel level) {
        long span = WORLDZERO_DELAY_AFTER_FALL_MAX_TICKS - WORLDZERO_DELAY_AFTER_FALL_MIN_TICKS + 1L;
        return WORLDZERO_DELAY_AFTER_FALL_MIN_TICKS + (long) (level.random.nextDouble() * span);
    }

    private static final class SessionState {
        private boolean worldzero$eventTriggered;
        private boolean worldzero$eventActive;
        private long worldzero$freezeEndTick;
        private UUID worldzero$targetPlayerId;
        private double worldzero$lockedX;
        private double worldzero$lockedY;
        private double worldzero$lockedZ;
        private float worldzero$lockedYaw;
        private float worldzero$lockedPitch;
    }

    private static final class FreezeSaveData extends SavedData {
        private long worldzero$triggerTick = -1L;
        private boolean worldzero$completed;

        private static FreezeSaveData worldzero$load(CompoundTag tag) {
            FreezeSaveData saveData = new FreezeSaveData();
            saveData.worldzero$triggerTick = tag.getLong("trigger_tick");
            saveData.worldzero$completed = tag.getBoolean("completed");
            return saveData;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putLong("trigger_tick", this.worldzero$triggerTick);
            tag.putBoolean("completed", this.worldzero$completed);
            return tag;
        }
    }
}
