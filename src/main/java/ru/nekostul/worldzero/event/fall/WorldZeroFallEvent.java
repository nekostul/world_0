package ru.nekostul.worldzero;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroFallEvent {
    private static final long WORLDZERO_FALL_WINDOW_START_TICKS = 45L * 60L * 20L;
    private static final long WORLDZERO_FALL_WINDOW_END_TICKS = 120L * 60L * 20L;
    private static final long WORLDZERO_DELAY_AFTER_FREEZE_MIN_TICKS = 15L * 60L * 20L;
    private static final long WORLDZERO_DELAY_AFTER_FREEZE_MAX_TICKS = 30L * 60L * 20L;
    private static final int WORLDZERO_FALL_FREEZE_TICKS = 5 * 20;
    private static final double WORLDZERO_BLACK_ECHO_FRONT_DISTANCE_BLOCKS = 3.0D;
    private static final int WORLDZERO_HOLE_RADIUS_BLOCKS = 1;
    private static final int WORLDZERO_RESPAWN_SEARCH_RADIUS_MIN = 2;
    private static final int WORLDZERO_RESPAWN_SEARCH_RADIUS_MAX = 5;
    private static final int WORLDZERO_RESPAWN_SEARCH_VERTICAL_UP = 4;
    private static final int WORLDZERO_RESPAWN_SEARCH_VERTICAL_DOWN = 8;
    private static final AABB WORLDZERO_ENTITY_SCAN_AABB = new AABB(
            -30_000_000.0D,
            -2_048.0D,
            -30_000_000.0D,
            30_000_000.0D,
            4_096.0D,
            30_000_000.0D
    );
    private static final String WORLDZERO_SAVE_ID = "worldzero_fall_event";
    private static final Map<MinecraftServer, SessionState> WORLDZERO_SESSION_STATES = new WeakHashMap<>();

    private WorldZeroFallEvent() {
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
        SessionState sessionState = WORLDZERO_SESSION_STATES.computeIfAbsent(server, ignored -> new SessionState());
        if (sessionState.worldzero$phase != Phase.INACTIVE) {
            worldzero$tickActiveEvent(level, sessionState);
            return;
        }

        if (WorldZeroFreezeEvent.worldzero$isFreezeActive(server)) {
            return;
        }

        FallSaveData saveData = worldzero$getSaveData(level);
        if (saveData.worldzero$completed) {
            return;
        }

        if (saveData.worldzero$triggerTick < 0L) {
            long triggerSpan = WORLDZERO_FALL_WINDOW_END_TICKS - WORLDZERO_FALL_WINDOW_START_TICKS + 1L;
            saveData.worldzero$triggerTick = WORLDZERO_FALL_WINDOW_START_TICKS
                    + (long) (level.random.nextDouble() * triggerSpan);
            saveData.setDirty();
        }

        if (level.getGameTime() < saveData.worldzero$triggerTick || !level.isNight()) {
            return;
        }

        ServerPlayer targetPlayer = worldzero$pickRunningPlayer(level);
        if (targetPlayer == null) {
            return;
        }

        worldzero$startEvent(level, sessionState, saveData, targetPlayer, false);
    }

    @SubscribeEvent
    public static void worldzero$onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        SessionState state = WORLDZERO_SESSION_STATES.get(server);
        if (state == null || state.worldzero$phase == Phase.INACTIVE || state.worldzero$targetPlayerId == null) {
            return;
        }

        if (state.worldzero$targetPlayerId.equals(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && worldzero$isInteractionBlocked(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && worldzero$isInteractionBlocked(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && worldzero$isInteractionBlocked(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_SESSION_STATES.remove(event.getServer());
    }

    public static boolean worldzero$triggerFallNow(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator() || player.level().dimension() != Level.OVERWORLD) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null || WorldZeroFreezeEvent.worldzero$isFreezeActive(server)) {
            return false;
        }

        SessionState sessionState = WORLDZERO_SESSION_STATES.computeIfAbsent(server, ignored -> new SessionState());
        if (sessionState.worldzero$phase != Phase.INACTIVE) {
            return false;
        }

        return worldzero$startEvent(player.serverLevel(), sessionState, null, player, true);
    }

    public static void worldzero$acknowledgeFakeRespawn(ServerPlayer player) {
        if (player == null) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        SessionState state = WORLDZERO_SESSION_STATES.get(server);
        if (state == null || state.worldzero$phase != Phase.WAITING_RESPAWN || state.worldzero$targetPlayerId == null) {
            return;
        }

        if (!state.worldzero$targetPlayerId.equals(player.getUUID())) {
            return;
        }

        WorldZeroNetwork.sendFallClientAction(player, WorldZeroFallClientPacket.WORLDZERO_ACTION_CLEAR);
        WorldZeroParalysisEvent.worldzero$scheduleAfterFall(player);
        worldzero$clearState(server, state);
    }

    public static boolean worldzero$isFallActive(MinecraftServer server) {
        SessionState state = WORLDZERO_SESSION_STATES.get(server);
        return state != null && state.worldzero$phase != Phase.INACTIVE;
    }

    public static void worldzero$rescheduleAfterFreeze(ServerLevel level) {
        if (level == null || level.isClientSide() || level.dimension() != Level.OVERWORLD) {
            return;
        }

        FallSaveData saveData = worldzero$getSaveData(level);
        if (saveData.worldzero$completed) {
            return;
        }

        saveData.worldzero$triggerTick = level.getGameTime() + worldzero$randomDelayAfterFreeze(level);
        saveData.setDirty();
    }

    private static boolean worldzero$startEvent(
            ServerLevel level,
            SessionState state,
            @Nullable FallSaveData saveData,
            ServerPlayer player,
            boolean debugForced
    ) {
        WorldZeroEchoEntity blackEcho = worldzero$spawnFrontBlackEcho(level, player);
        if (blackEcho == null) {
            return false;
        }

        BlockPos holeCenter = player.blockPosition();
        Vec3 respawnPos = worldzero$findRespawnPosition(level, holeCenter, holeCenter.getY());
        if (respawnPos == null) {
            blackEcho.discard();
            return false;
        }

        state.worldzero$phase = Phase.FREEZE;
        state.worldzero$targetPlayerId = player.getUUID();
        state.worldzero$echoId = blackEcho.getUUID();
        state.worldzero$phaseEndTick = level.getGameTime() + WORLDZERO_FALL_FREEZE_TICKS;
        state.worldzero$lockedX = player.getX();
        state.worldzero$lockedY = player.getY();
        state.worldzero$lockedZ = player.getZ();
        state.worldzero$lockedYaw = player.getYRot();
        state.worldzero$lockedPitch = player.getXRot();
        state.worldzero$holeCenter = holeCenter.immutable();
        state.worldzero$respawnX = respawnPos.x;
        state.worldzero$respawnY = respawnPos.y;
        state.worldzero$respawnZ = respawnPos.z;
        state.worldzero$debugForced = debugForced;

        if (saveData != null) {
            saveData.worldzero$completed = true;
            saveData.setDirty();
            WorldZeroFreezeEvent.worldzero$rescheduleAfterFall(level);
        }

        WorldZeroNetwork.sendFreezeStart(player, WORLDZERO_FALL_FREEZE_TICKS, blackEcho.getId());
        WorldZeroNetwork.sendFallClientAction(player, WorldZeroFallClientPacket.WORLDZERO_ACTION_BEGIN);
        return true;
    }

    private static void worldzero$tickActiveEvent(ServerLevel level, SessionState state) {
        ServerPlayer targetPlayer = level.getServer().getPlayerList().getPlayer(state.worldzero$targetPlayerId);
        if (targetPlayer == null || !targetPlayer.isAlive()) {
            worldzero$clearState(level.getServer(), state);
            return;
        }

        switch (state.worldzero$phase) {
            case FREEZE -> worldzero$tickFreezePhase(level, state, targetPlayer);
            case FALLING -> worldzero$tickFallingPhase(level, state, targetPlayer);
            case WAITING_RESPAWN -> worldzero$tickWaitingRespawnPhase(state, targetPlayer);
            default -> {
            }
        }
    }

    private static void worldzero$tickFreezePhase(ServerLevel level, SessionState state, ServerPlayer player) {
        worldzero$applyFreeze(level.getServer(), state, player);
        if (level.getGameTime() < state.worldzero$phaseEndTick) {
            return;
        }

        Entity blackEcho = worldzero$findEntity(level.getServer(), state.worldzero$echoId);
        if (blackEcho != null) {
            blackEcho.discard();
        }

        WorldZeroNetwork.sendFreezeEnd(player);
        worldzero$createHole(level, state.worldzero$holeCenter);
        player.setDeltaMovement(0.0D, -1.2D, 0.0D);
        player.fallDistance = 0.0F;
        WorldZeroNetwork.sendFallClientAction(player, WorldZeroFallClientPacket.WORLDZERO_ACTION_START_FALL);
        state.worldzero$phase = Phase.FALLING;
        state.worldzero$phaseEndTick = -1L;
        state.worldzero$echoId = null;
    }

    private static void worldzero$tickFallingPhase(ServerLevel level, SessionState state, ServerPlayer player) {
        worldzero$clearFallingFluids(level, state);
        player.setDeltaMovement(0.0D, -1.2D, 0.0D);
        player.fallDistance = 0.0F;

        if (player.getY() > level.getMinBuildHeight() + 4.0D) {
            return;
        }

        player.teleportTo(state.worldzero$respawnX, state.worldzero$respawnY, state.worldzero$respawnZ);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        WorldZeroNetwork.sendFallClientAction(player, WorldZeroFallClientPacket.WORLDZERO_ACTION_SHOW_DEATH);
        state.worldzero$phase = Phase.WAITING_RESPAWN;
    }

    private static void worldzero$tickWaitingRespawnPhase(SessionState state, ServerPlayer player) {
        player.teleportTo(state.worldzero$respawnX, state.worldzero$respawnY, state.worldzero$respawnZ);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
    }

    private static void worldzero$applyFreeze(MinecraftServer server, SessionState state, ServerPlayer targetPlayer) {
        Entity blackEcho = worldzero$findEntity(server, state.worldzero$echoId);
        float yaw = state.worldzero$lockedYaw;
        float pitch = state.worldzero$lockedPitch;
        if (blackEcho != null) {
            double deltaX = blackEcho.getX() - state.worldzero$lockedX;
            double deltaY = blackEcho.getEyeY() - targetPlayer.getEyeHeight() - state.worldzero$lockedY;
            double deltaZ = blackEcho.getZ() - state.worldzero$lockedZ;
            double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            yaw = (float) (Mth.atan2(deltaZ, deltaX) * (180.0D / Math.PI)) - 90.0F;
            pitch = (float) (-(Mth.atan2(deltaY, horizontalDistance) * (180.0D / Math.PI)));
        }

        targetPlayer.setDeltaMovement(0.0D, 0.0D, 0.0D);
        targetPlayer.teleportTo(state.worldzero$lockedX, state.worldzero$lockedY, state.worldzero$lockedZ);
        targetPlayer.setYRot(yaw);
        targetPlayer.setYHeadRot(yaw);
        targetPlayer.setYBodyRot(yaw);
        targetPlayer.setXRot(pitch);
        targetPlayer.fallDistance = 0.0F;

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

    @Nullable
    private static ServerPlayer worldzero$pickRunningPlayer(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }

            if (!player.isSprinting() || !player.onGround()) {
                continue;
            }

            if (player.getDeltaMovement().horizontalDistanceSqr() < 0.01D) {
                continue;
            }

            return player;
        }
        return null;
    }

    @Nullable
    private static WorldZeroEchoEntity worldzero$spawnFrontBlackEcho(ServerLevel level, ServerPlayer player) {
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

    private static void worldzero$clearFallingFluids(ServerLevel level, SessionState state) {
        if (state.worldzero$holeCenter == null || state.worldzero$targetPlayerId == null) {
            return;
        }

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(state.worldzero$targetPlayerId);
        if (player == null) {
            return;
        }

        int minY = Math.max(level.getMinBuildHeight(), Mth.floor(player.getY()) - 2);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, Mth.floor(player.getY()) + 2);
        for (int x = state.worldzero$holeCenter.getX() - WORLDZERO_HOLE_RADIUS_BLOCKS;
             x <= state.worldzero$holeCenter.getX() + WORLDZERO_HOLE_RADIUS_BLOCKS;
             x++) {
            for (int z = state.worldzero$holeCenter.getZ() - WORLDZERO_HOLE_RADIUS_BLOCKS;
                 z <= state.worldzero$holeCenter.getZ() + WORLDZERO_HOLE_RADIUS_BLOCKS;
                 z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos blockPos = new BlockPos(x, y, z);
                    if (!level.getBlockState(blockPos).getFluidState().isEmpty()) {
                        level.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private static void worldzero$createHole(ServerLevel level, BlockPos holeCenter) {
        int minY = level.getMinBuildHeight();
        int topY = holeCenter.getY() - 1;

        for (int x = holeCenter.getX() - WORLDZERO_HOLE_RADIUS_BLOCKS; x <= holeCenter.getX() + WORLDZERO_HOLE_RADIUS_BLOCKS; x++) {
            for (int z = holeCenter.getZ() - WORLDZERO_HOLE_RADIUS_BLOCKS; z <= holeCenter.getZ() + WORLDZERO_HOLE_RADIUS_BLOCKS; z++) {
                for (int y = topY; y >= minY; y--) {
                    BlockPos blockPos = new BlockPos(x, y, z);
                    if (!level.getBlockState(blockPos).isAir()) {
                        level.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    @Nullable
    private static Vec3 worldzero$findRespawnPosition(ServerLevel level, BlockPos holeCenter, int referenceY) {
        for (int radius = WORLDZERO_RESPAWN_SEARCH_RADIUS_MIN; radius <= WORLDZERO_RESPAWN_SEARCH_RADIUS_MAX; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                Vec3 north = worldzero$findStandableRespawn(level, new BlockPos(holeCenter.getX() + dx, referenceY, holeCenter.getZ() - radius), referenceY);
                if (north != null) {
                    return north;
                }

                Vec3 south = worldzero$findStandableRespawn(level, new BlockPos(holeCenter.getX() + dx, referenceY, holeCenter.getZ() + radius), referenceY);
                if (south != null) {
                    return south;
                }
            }

            for (int dz = -radius + 1; dz <= radius - 1; dz++) {
                Vec3 west = worldzero$findStandableRespawn(level, new BlockPos(holeCenter.getX() - radius, referenceY, holeCenter.getZ() + dz), referenceY);
                if (west != null) {
                    return west;
                }

                Vec3 east = worldzero$findStandableRespawn(level, new BlockPos(holeCenter.getX() + radius, referenceY, holeCenter.getZ() + dz), referenceY);
                if (east != null) {
                    return east;
                }
            }
        }

        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, holeCenter.getX() + 3, holeCenter.getZ());
        return new Vec3(holeCenter.getX() + 3.5D, surfaceY, holeCenter.getZ() + 0.5D);
    }

    @Nullable
    private static Vec3 worldzero$findStandableRespawn(ServerLevel level, BlockPos base, int referenceY) {
        for (int y = referenceY + WORLDZERO_RESPAWN_SEARCH_VERTICAL_UP; y >= referenceY - WORLDZERO_RESPAWN_SEARCH_VERTICAL_DOWN; y--) {
            BlockPos candidate = new BlockPos(base.getX(), y, base.getZ());
            if (!level.getBlockState(candidate).isAir() || !level.getBlockState(candidate.above()).isAir()) {
                continue;
            }

            BlockPos belowPos = candidate.below();
            BlockState belowState = level.getBlockState(belowPos);
            if (!belowState.isFaceSturdy(level, belowPos, Direction.UP) || !belowState.getFluidState().isEmpty()) {
                continue;
            }

            return new Vec3(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
        }

        return null;
    }

    @Nullable
    private static Entity worldzero$findEntity(MinecraftServer server, @Nullable UUID entityId) {
        if (server == null || entityId == null) {
            return null;
        }

        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }

        return null;
    }

    private static boolean worldzero$isInteractionBlocked(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        SessionState state = WORLDZERO_SESSION_STATES.get(server);
        if (state == null || state.worldzero$phase == Phase.INACTIVE || state.worldzero$targetPlayerId == null) {
            return false;
        }

        return state.worldzero$targetPlayerId.equals(player.getUUID());
    }

    private static void worldzero$clearState(MinecraftServer server, SessionState state) {
        Entity blackEcho = worldzero$findEntity(server, state.worldzero$echoId);
        if (blackEcho != null) {
            blackEcho.discard();
        }

        state.worldzero$phase = Phase.INACTIVE;
        state.worldzero$targetPlayerId = null;
        state.worldzero$echoId = null;
        state.worldzero$phaseEndTick = -1L;
        state.worldzero$holeCenter = null;
        state.worldzero$debugForced = false;
    }

    private static FallSaveData worldzero$getSaveData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FallSaveData::load, FallSaveData::new, WORLDZERO_SAVE_ID);
    }

    private static long worldzero$randomDelayAfterFreeze(ServerLevel level) {
        long span = WORLDZERO_DELAY_AFTER_FREEZE_MAX_TICKS - WORLDZERO_DELAY_AFTER_FREEZE_MIN_TICKS + 1L;
        return WORLDZERO_DELAY_AFTER_FREEZE_MIN_TICKS + (long) (level.random.nextDouble() * span);
    }

    private enum Phase {
        INACTIVE,
        FREEZE,
        FALLING,
        WAITING_RESPAWN
    }

    private static final class SessionState {
        private Phase worldzero$phase = Phase.INACTIVE;
        private UUID worldzero$targetPlayerId;
        private UUID worldzero$echoId;
        private long worldzero$phaseEndTick = -1L;
        private double worldzero$lockedX;
        private double worldzero$lockedY;
        private double worldzero$lockedZ;
        private float worldzero$lockedYaw;
        private float worldzero$lockedPitch;
        private BlockPos worldzero$holeCenter;
        private double worldzero$respawnX;
        private double worldzero$respawnY;
        private double worldzero$respawnZ;
        private boolean worldzero$debugForced;
    }

    private static final class FallSaveData extends SavedData {
        private long worldzero$triggerTick = -1L;
        private boolean worldzero$completed;

        public static FallSaveData load(CompoundTag tag) {
            FallSaveData saveData = new FallSaveData();
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
