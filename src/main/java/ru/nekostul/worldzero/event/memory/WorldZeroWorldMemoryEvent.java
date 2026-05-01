package ru.nekostul.worldzero;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BlastFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.SmokerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroWorldMemoryEvent {
    private static final String WORLDZERO_SAVE_ID = "worldzero_world_memory";

    private static final long WORLDZERO_DORMANT_END_TICKS = 30L * 60L * 20L;
    private static final long WORLDZERO_FIRST_END_TICKS = 90L * 60L * 20L;
    private static final long WORLDZERO_ACTIVE_END_TICKS = 150L * 60L * 20L;
    private static final long WORLDZERO_PEAK_END_TICKS = 180L * 60L * 20L;

    private static final long WORLDZERO_FIRST_RETRY_MIN_TICKS = 4L * 60L * 20L;
    private static final long WORLDZERO_FIRST_RETRY_MAX_TICKS = 8L * 60L * 20L;
    private static final long WORLDZERO_ACTIVE_DELAY_MIN_TICKS = 12L * 60L * 20L;
    private static final long WORLDZERO_ACTIVE_DELAY_MAX_TICKS = 24L * 60L * 20L;
    private static final long WORLDZERO_ACTIVE_RETRY_MIN_TICKS = 4L * 60L * 20L;
    private static final long WORLDZERO_ACTIVE_RETRY_MAX_TICKS = 8L * 60L * 20L;
    private static final long WORLDZERO_PEAK_DELAY_MIN_TICKS = 20L * 60L * 20L;
    private static final long WORLDZERO_PEAK_DELAY_MAX_TICKS = 35L * 60L * 20L;
    private static final long WORLDZERO_PEAK_RETRY_MIN_TICKS = 8L * 60L * 20L;
    private static final long WORLDZERO_PEAK_RETRY_MAX_TICKS = 12L * 60L * 20L;
    private static final long WORLDZERO_DECLINE_DELAY_MIN_TICKS = 25L * 60L * 20L;
    private static final long WORLDZERO_DECLINE_DELAY_MAX_TICKS = 40L * 60L * 20L;
    private static final long WORLDZERO_DECLINE_RETRY_MIN_TICKS = 10L * 60L * 20L;
    private static final long WORLDZERO_DECLINE_RETRY_MAX_TICKS = 15L * 60L * 20L;

    private static final long WORLDZERO_HOUSE_SCAN_INTERVAL_TICKS = 5L * 20L;
    private static final long WORLDZERO_HOUSE_MEMORY_TICKS = 3L * 60L * 20L;
    private static final long WORLDZERO_PLAYTIME_SAVE_INTERVAL_TICKS = 30L * 20L;
    private static final long WORLDZERO_SILENT_CHEST_TICKS = 4L * 20L;

    private static final double WORLDZERO_HOUSE_PROXIMITY_BLOCKS = 12.0D;
    private static final double WORLDZERO_PLAYER_MIN_DISTANCE_TO_TARGET_BLOCKS = 1.75D;
    private static final double WORLDZERO_WATCH_DOT = 0.35D;
    private static final double WORLDZERO_ACTIVE_DOUBLE_CHANGE_CHANCE = 0.12D;

    private static final Map<MinecraftServer, SessionState> WORLDZERO_SERVER_STATES = new WeakHashMap<>();

    private WorldZeroWorldMemoryEvent() {
    }

    @SubscribeEvent
    public static void worldzero$onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.level instanceof ServerLevel level) || level.isClientSide() || level.dimension() != Level.OVERWORLD) {
            return;
        }

        SessionState sessionState = WORLDZERO_SERVER_STATES.get(level.getServer());
        if (sessionState == null) {
            return;
        }

        for (PlayerState playerState : sessionState.worldzero$playerStates.values()) {
            worldzero$closeSilentChestIfNeeded(level, playerState);
        }
    }

    @SubscribeEvent
    public static void worldzero$onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.player instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }

        if (!player.isAlive() || player.isSpectator() || player.serverLevel().dimension() != Level.OVERWORLD) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        SessionState sessionState = WORLDZERO_SERVER_STATES.computeIfAbsent(server, ignored -> new SessionState());
        PlayerState playerState = sessionState.worldzero$playerStates.computeIfAbsent(
                player.getUUID(),
                ignored -> new PlayerState()
        );
        worldzero$loadPersistentPlayerState(player.serverLevel(), player.getUUID(), playerState);
        worldzero$closeSilentChestIfNeeded(player.serverLevel(), playerState);

        long gameTime = player.serverLevel().getGameTime();
        boolean shouldPersistPlayTime = gameTime - playerState.worldzero$lastPlayTimeSaveGameTick
                >= WORLDZERO_PLAYTIME_SAVE_INTERVAL_TICKS;
        if (!WorldZeroStoryTime.worldzero$countsTowardStoryTime(player)) {
            if (shouldPersistPlayTime) {
                worldzero$savePersistentPlayerState(player.serverLevel(), player.getUUID(), playerState);
            }
            return;
        }

        long playTimeTicks = ++playerState.worldzero$elapsedPlayTicks;

        if (worldzero$hasConflictingEvent(server)) {
            if (shouldPersistPlayTime) {
                worldzero$savePersistentPlayerState(player.serverLevel(), player.getUUID(), playerState);
            }
            return;
        }

        if (!WorldZeroStoryTime.worldzero$canReceiveStoryEvent(player)) {
            if (shouldPersistPlayTime) {
                worldzero$savePersistentPlayerState(player.serverLevel(), player.getUUID(), playerState);
            }
            return;
        }

        Phase phase = worldzero$resolvePhase(playTimeTicks);
        if (phase == Phase.DORMANT) {
            if (shouldPersistPlayTime) {
                worldzero$savePersistentPlayerState(player.serverLevel(), player.getUUID(), playerState);
            }
            return;
        }

        if (playerState.worldzero$nextAnomalyTick < 0L) {
            if (phase == Phase.FIRST && !playerState.worldzero$firstManifestationDone) {
                playerState.worldzero$nextAnomalyTick = worldzero$scheduleFirstManifestationTick(
                        player.serverLevel(),
                        playTimeTicks
                );
            } else if (phase != Phase.FIRST) {
                playerState.worldzero$nextAnomalyTick = playTimeTicks
                        + worldzero$randomDelayForPhase(player.serverLevel(), phase);
            }
            worldzero$savePersistentPlayerState(player.serverLevel(), player.getUUID(), playerState);
        }

        if (playerState.worldzero$nextAnomalyTick < 0L || playTimeTicks < playerState.worldzero$nextAnomalyTick) {
            if (shouldPersistPlayTime) {
                worldzero$savePersistentPlayerState(player.serverLevel(), player.getUUID(), playerState);
            }
            return;
        }

        WorldZeroHouseDetector.DetectedHouse house = worldzero$getEligibleHouse(player, playerState, gameTime);
        if (house == null) {
            playerState.worldzero$nextAnomalyTick = playTimeTicks + worldzero$randomRetryDelay(player.serverLevel(), phase);
            worldzero$savePersistentPlayerState(player.serverLevel(), player.getUUID(), playerState);
            return;
        }

        int maxChanges = phase == Phase.ACTIVE && player.serverLevel().random.nextDouble() < WORLDZERO_ACTIVE_DOUBLE_CHANGE_CHANCE
                ? 2
                : 1;
        int changes = worldzero$triggerAnomalies(player.serverLevel(), player, playerState, house, phase, maxChanges);
        if (changes > 0) {
            if (phase == Phase.FIRST) {
                playerState.worldzero$firstManifestationDone = true;
                playerState.worldzero$nextAnomalyTick = -1L;
            } else {
                playerState.worldzero$nextAnomalyTick = playTimeTicks
                        + worldzero$randomDelayForPhase(player.serverLevel(), phase);
            }
        } else {
            playerState.worldzero$nextAnomalyTick = playTimeTicks + worldzero$randomRetryDelay(player.serverLevel(), phase);
        }

        worldzero$savePersistentPlayerState(player.serverLevel(), player.getUUID(), playerState);
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_SERVER_STATES.remove(event.getServer());
    }

    public static boolean worldzero$triggerMemoryNow(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator() || player.serverLevel().dimension() != Level.OVERWORLD) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null || worldzero$hasConflictingEvent(server)) {
            return false;
        }

        SessionState sessionState = WORLDZERO_SERVER_STATES.computeIfAbsent(server, ignored -> new SessionState());
        PlayerState playerState = sessionState.worldzero$playerStates.computeIfAbsent(
                player.getUUID(),
                ignored -> new PlayerState()
        );
        worldzero$loadPersistentPlayerState(player.serverLevel(), player.getUUID(), playerState);
        worldzero$closeSilentChestIfNeeded(player.serverLevel(), playerState);

        WorldZeroHouseDetector.DetectedHouse house = WorldZeroHouseDetector.worldzero$findContainingHouse(player);
        if (house == null) {
            house = WorldZeroHouseDetector.worldzero$findNearbyHouseForDebug(player);
        }

        if (house == null || !worldzero$isPlayerNearHouse(player, house)) {
            return false;
        }

        int changes = worldzero$triggerAnomalies(player.serverLevel(), player, playerState, house, Phase.ACTIVE, 1);
        if (changes > 0) {
            worldzero$savePersistentPlayerState(player.serverLevel(), player.getUUID(), playerState);
            return true;
        }

        return false;
    }

    public static boolean worldzero$stopMemoryNow(MinecraftServer server) {
        SessionState sessionState = WORLDZERO_SERVER_STATES.remove(server);
        if (server == null || sessionState == null) {
            return false;
        }

        ServerLevel level = server.getLevel(Level.OVERWORLD);
        boolean changed = false;
        if (level != null) {
            for (PlayerState playerState : sessionState.worldzero$playerStates.values()) {
                if (playerState.worldzero$openedChestPos != null) {
                    playerState.worldzero$openedChestCloseTick = 0L;
                    worldzero$closeSilentChestIfNeeded(level, playerState);
                    changed = true;
                }
            }
        }
        return changed || !sessionState.worldzero$playerStates.isEmpty();
    }

    private static boolean worldzero$hasConflictingEvent(MinecraftServer server) {
        return WorldZeroFreezeEvent.worldzero$isFreezeActive(server)
                || WorldZeroFallEvent.worldzero$isFallActive(server)
                || WorldZeroFootstepsEvent.worldzero$isFootstepsActive(server)
                || WorldZeroHouseEvent.worldzero$isHouseActive(server)
                || WorldZeroParalysisEvent.worldzero$isParalysisActive(server);
    }

    private static Phase worldzero$resolvePhase(long playTimeTicks) {
        if (playTimeTicks < WORLDZERO_DORMANT_END_TICKS) {
            return Phase.DORMANT;
        }
        if (playTimeTicks < WORLDZERO_FIRST_END_TICKS) {
            return Phase.FIRST;
        }
        if (playTimeTicks < WORLDZERO_ACTIVE_END_TICKS) {
            return Phase.ACTIVE;
        }
        if (playTimeTicks < WORLDZERO_PEAK_END_TICKS) {
            return Phase.PEAK;
        }
        return Phase.DECLINE;
    }

    private static long worldzero$scheduleFirstManifestationTick(ServerLevel level, long playTimeTicks) {
        long earliest = Math.max(playTimeTicks + 4L * 60L * 20L, WORLDZERO_DORMANT_END_TICKS + 4L * 60L * 20L);
        long latest = WORLDZERO_FIRST_END_TICKS - 2L * 60L * 20L;
        if (earliest >= latest) {
            return earliest;
        }
        return worldzero$randomBetween(level, earliest, latest);
    }

    private static long worldzero$randomDelayForPhase(ServerLevel level, Phase phase) {
        return switch (phase) {
            case ACTIVE -> worldzero$randomBetween(level, WORLDZERO_ACTIVE_DELAY_MIN_TICKS, WORLDZERO_ACTIVE_DELAY_MAX_TICKS);
            case PEAK -> worldzero$randomBetween(level, WORLDZERO_PEAK_DELAY_MIN_TICKS, WORLDZERO_PEAK_DELAY_MAX_TICKS);
            case DECLINE -> worldzero$randomBetween(level, WORLDZERO_DECLINE_DELAY_MIN_TICKS, WORLDZERO_DECLINE_DELAY_MAX_TICKS);
            default -> -1L;
        };
    }

    private static long worldzero$randomRetryDelay(ServerLevel level, Phase phase) {
        return switch (phase) {
            case FIRST -> worldzero$randomBetween(level, WORLDZERO_FIRST_RETRY_MIN_TICKS, WORLDZERO_FIRST_RETRY_MAX_TICKS);
            case ACTIVE -> worldzero$randomBetween(level, WORLDZERO_ACTIVE_RETRY_MIN_TICKS, WORLDZERO_ACTIVE_RETRY_MAX_TICKS);
            case PEAK -> worldzero$randomBetween(level, WORLDZERO_PEAK_RETRY_MIN_TICKS, WORLDZERO_PEAK_RETRY_MAX_TICKS);
            case DECLINE -> worldzero$randomBetween(level, WORLDZERO_DECLINE_RETRY_MIN_TICKS, WORLDZERO_DECLINE_RETRY_MAX_TICKS);
            default -> 20L * 20L;
        };
    }

    private static long worldzero$randomBetween(ServerLevel level, long minValue, long maxValue) {
        if (maxValue <= minValue) {
            return minValue;
        }

        long span = maxValue - minValue;
        return minValue + (long) Math.floor(level.random.nextDouble() * (double) (span + 1L));
    }

    @Nullable
    private static WorldZeroHouseDetector.DetectedHouse worldzero$getEligibleHouse(
            ServerPlayer player,
            PlayerState playerState,
            long gameTime
    ) {
        WorldZeroHouseDetector.DetectedHouse rememberedHouse = playerState.worldzero$rememberedHouse;
        if (rememberedHouse != null
                && gameTime <= playerState.worldzero$rememberedHouseUntilTick
                && worldzero$isPlayerNearHouse(player, rememberedHouse)) {
            return rememberedHouse;
        }

        if (gameTime - playerState.worldzero$lastHouseScanTick < WORLDZERO_HOUSE_SCAN_INTERVAL_TICKS) {
            return null;
        }

        playerState.worldzero$lastHouseScanTick = gameTime;

        WorldZeroHouseDetector.DetectedHouse detectedHouse = WorldZeroHouseDetector.worldzero$findContainingHouse(player);
        if (detectedHouse == null) {
            detectedHouse = WorldZeroHouseDetector.worldzero$findNearbyHouseForDebug(player);
        }

        if (detectedHouse == null || !worldzero$isPlayerNearHouse(player, detectedHouse)) {
            playerState.worldzero$rememberedHouse = null;
            playerState.worldzero$rememberedHouseUntilTick = -1L;
            return null;
        }

        playerState.worldzero$rememberedHouse = detectedHouse;
        playerState.worldzero$rememberedHouseUntilTick = gameTime + WORLDZERO_HOUSE_MEMORY_TICKS;
        return detectedHouse;
    }

    private static boolean worldzero$isPlayerNearHouse(ServerPlayer player, WorldZeroHouseDetector.DetectedHouse house) {
        if (WorldZeroHouseDetector.worldzero$isPlayerInsideHouse(player, house)) {
            return true;
        }

        double distanceSqr = house.worldzero$horizontalDistanceToBoundsSqr(player.getX(), player.getZ());
        return distanceSqr <= WORLDZERO_HOUSE_PROXIMITY_BLOCKS * WORLDZERO_HOUSE_PROXIMITY_BLOCKS;
    }

    private static int worldzero$triggerAnomalies(
            ServerLevel level,
            ServerPlayer player,
            PlayerState playerState,
            WorldZeroHouseDetector.DetectedHouse house,
            Phase phase,
            int maxChanges
    ) {
        List<AnomalyKind> availableKinds = new ArrayList<>();
        if (phase == Phase.FIRST) {
            availableKinds.add(AnomalyKind.DOOR_OPEN);
        } else if (phase == Phase.PEAK) {
            availableKinds.add(AnomalyKind.DOOR_OPEN);
            availableKinds.add(AnomalyKind.CHEST_OPEN);
        } else {
            availableKinds.add(AnomalyKind.DOOR_OPEN);
            availableKinds.add(AnomalyKind.CHEST_OPEN);
            availableKinds.add(AnomalyKind.FURNITURE_SHIFT);
        }

        if (playerState.worldzero$lastAnomalyKind != null && availableKinds.size() > 1) {
            availableKinds.remove(playerState.worldzero$lastAnomalyKind);
        }

        int changes = 0;
        AnomalyKind lastAppliedKind = null;
        while (changes < maxChanges && !availableKinds.isEmpty()) {
            AnomalyKind selectedKind = availableKinds.remove(level.random.nextInt(availableKinds.size()));
            if (!worldzero$applyAnomaly(level, player, playerState, house, selectedKind)) {
                continue;
            }

            changes++;
            lastAppliedKind = selectedKind;
        }

        if (lastAppliedKind != null) {
            playerState.worldzero$lastAnomalyKind = lastAppliedKind;
        }
        return changes;
    }

    private static boolean worldzero$applyAnomaly(
            ServerLevel level,
            ServerPlayer player,
            PlayerState playerState,
            WorldZeroHouseDetector.DetectedHouse house,
            AnomalyKind kind
    ) {
        return switch (kind) {
            case DOOR_OPEN -> worldzero$tryOpenDoor(level, player, house);
            case CHEST_OPEN -> worldzero$tryOpenChest(level, player, playerState, house);
            case FURNITURE_SHIFT -> worldzero$tryShiftFurniture(level, player, house);
        };
    }

    private static boolean worldzero$tryOpenDoor(
            ServerLevel level,
            ServerPlayer player,
            WorldZeroHouseDetector.DetectedHouse house
    ) {
        List<BlockPos> closedDoors = worldzero$collectClosedDoors(level, player, house);
        if (closedDoors.isEmpty()) {
            return false;
        }

        BlockPos doorPos = closedDoors.get(level.random.nextInt(closedDoors.size()));
        return worldzero$openSilentDoor(level, doorPos);
    }

    private static boolean worldzero$tryOpenChest(
            ServerLevel level,
            ServerPlayer player,
            PlayerState playerState,
            WorldZeroHouseDetector.DetectedHouse house
    ) {
        if (playerState.worldzero$openedChestPos != null && level.getGameTime() < playerState.worldzero$openedChestCloseTick) {
            return false;
        }

        List<BlockPos> chests = worldzero$collectSingleChests(level, player, playerState, house);
        if (chests.isEmpty()) {
            return false;
        }

        BlockPos chestPos = chests.get(level.random.nextInt(chests.size()));
        return worldzero$openSilentChest(level, chestPos, playerState);
    }

    private static boolean worldzero$tryShiftFurniture(
            ServerLevel level,
            ServerPlayer player,
            WorldZeroHouseDetector.DetectedHouse house
    ) {
        List<BlockPos> sources = worldzero$collectMovableFurniture(level, player, house);
        if (sources.isEmpty()) {
            return false;
        }

        while (!sources.isEmpty()) {
            BlockPos sourcePos = sources.remove(level.random.nextInt(sources.size()));
            BlockState sourceState = level.getBlockState(sourcePos);
            List<BlockPos> targets = worldzero$collectFurnitureTargets(level, player, house, sourcePos, sourceState);
            if (targets.isEmpty()) {
                continue;
            }

            BlockPos targetPos = targets.get(level.random.nextInt(targets.size()));
            if (worldzero$moveFurniture(level, sourcePos, targetPos, sourceState)) {
                return true;
            }
        }

        return false;
    }

    private static List<BlockPos> worldzero$collectClosedDoors(
            ServerLevel level,
            ServerPlayer player,
            WorldZeroHouseDetector.DetectedHouse house
    ) {
        List<BlockPos> positions = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        for (int x = house.interiorMin().getX() - 1; x <= house.interiorMax().getX() + 1; x++) {
            for (int y = house.interiorMin().getY() - 1; y <= house.interiorMax().getY() + 1; y++) {
                for (int z = house.interiorMin().getZ() - 1; z <= house.interiorMax().getZ() + 1; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.is(BlockTags.DOORS)
                            || !state.hasProperty(DoorBlock.HALF)
                            || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER
                            || !state.hasProperty(DoorBlock.OPEN)
                            || state.getValue(DoorBlock.OPEN)) {
                        continue;
                    }

                    if (!visited.add(pos.asLong())) {
                        continue;
                    }

                    if (worldzero$isTargetWatched(level, player, house, pos, state)) {
                        continue;
                    }

                    positions.add(pos.immutable());
                }
            }
        }
        return positions;
    }

    private static List<BlockPos> worldzero$collectSingleChests(
            ServerLevel level,
            ServerPlayer player,
            PlayerState playerState,
            WorldZeroHouseDetector.DetectedHouse house
    ) {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = house.interiorMin().getX(); x <= house.interiorMax().getX(); x++) {
            for (int y = house.interiorMin().getY(); y <= house.interiorMax().getY(); y++) {
                for (int z = house.interiorMin().getZ(); z <= house.interiorMax().getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof ChestBlock)
                            || !state.hasProperty(ChestBlock.TYPE)
                            || state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                        continue;
                    }

                    if (playerState.worldzero$openedChestPos != null && playerState.worldzero$openedChestPos.equals(pos)) {
                        continue;
                    }

                    if (worldzero$isTargetWatched(level, player, house, pos, state)) {
                        continue;
                    }

                    positions.add(pos.immutable());
                }
            }
        }
        return positions;
    }

    private static List<BlockPos> worldzero$collectMovableFurniture(
            ServerLevel level,
            ServerPlayer player,
            WorldZeroHouseDetector.DetectedHouse house
    ) {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = house.interiorMin().getX(); x <= house.interiorMax().getX(); x++) {
            for (int y = house.interiorMin().getY(); y <= house.interiorMax().getY(); y++) {
                for (int z = house.interiorMin().getZ(); z <= house.interiorMax().getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!worldzero$isMovableFurniture(state)) {
                        continue;
                    }

                    if (worldzero$isPlayerTooClose(player, pos)) {
                        continue;
                    }

                    if (worldzero$isTargetWatched(level, player, house, pos, state)) {
                        continue;
                    }

                    if (state.getBlock() instanceof ChestBlock
                            && (!state.hasProperty(ChestBlock.TYPE) || state.getValue(ChestBlock.TYPE) != ChestType.SINGLE)) {
                        continue;
                    }

                    positions.add(pos.immutable());
                }
            }
        }
        return positions;
    }

    private static List<BlockPos> worldzero$collectFurnitureTargets(
            ServerLevel level,
            ServerPlayer player,
            WorldZeroHouseDetector.DetectedHouse house,
            BlockPos sourcePos,
            BlockState sourceState
    ) {
        List<BlockPos> targets = new ArrayList<>();
        int y = sourcePos.getY();
        for (int x = house.interiorMin().getX(); x <= house.interiorMax().getX(); x++) {
            for (int z = house.interiorMin().getZ(); z <= house.interiorMax().getZ(); z++) {
                BlockPos targetPos = new BlockPos(x, y, z);
                if (targetPos.equals(sourcePos) || worldzero$isPlayerTooClose(player, targetPos)) {
                    continue;
                }

                if (!worldzero$isValidFurnitureTarget(level, targetPos, sourceState)) {
                    continue;
                }

                if (worldzero$isTargetWatched(level, player, house, targetPos, sourceState)) {
                    continue;
                }

                targets.add(targetPos.immutable());
            }
        }
        return targets;
    }

    private static boolean worldzero$isValidFurnitureTarget(ServerLevel level, BlockPos pos, BlockState sourceState) {
        BlockState targetState = level.getBlockState(pos);
        BlockState aboveState = level.getBlockState(pos.above());
        BlockState belowState = level.getBlockState(pos.below());
        if (!targetState.isAir() || !aboveState.isAir() || !belowState.isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) {
            return false;
        }

        if (!(sourceState.getBlock() instanceof ChestBlock)) {
            return true;
        }

        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockState neighborState = level.getBlockState(pos.relative(direction));
            if (neighborState.getBlock() instanceof ChestBlock) {
                return false;
            }
        }
        return true;
    }

    private static boolean worldzero$isMovableFurniture(BlockState state) {
        return state.getBlock() instanceof CraftingTableBlock
                || state.getBlock() instanceof FurnaceBlock
                || state.getBlock() instanceof BlastFurnaceBlock
                || state.getBlock() instanceof SmokerBlock
                || state.getBlock() instanceof BarrelBlock
                || state.getBlock() instanceof ChestBlock;
    }

    private static boolean worldzero$moveFurniture(
            ServerLevel level,
            BlockPos sourcePos,
            BlockPos targetPos,
            BlockState sourceState
    ) {
        if (!level.getBlockState(targetPos).isAir()) {
            return false;
        }

        CompoundTag blockEntityTag = null;
        BlockEntity blockEntity = level.getBlockEntity(sourcePos);
        if (blockEntity != null) {
            blockEntityTag = blockEntity.saveWithFullMetadata();
        }

        level.removeBlockEntity(sourcePos);
        level.setBlock(sourcePos, Blocks.AIR.defaultBlockState(), 18);
        level.setBlock(targetPos, sourceState, 18);

        if (blockEntityTag != null) {
            BlockEntity movedBlockEntity = BlockEntity.loadStatic(targetPos, sourceState, blockEntityTag);
            if (movedBlockEntity != null) {
                level.setBlockEntity(movedBlockEntity);
                movedBlockEntity.setChanged();
            }
        }

        level.sendBlockUpdated(sourcePos, sourceState, Blocks.AIR.defaultBlockState(), 3);
        level.sendBlockUpdated(targetPos, Blocks.AIR.defaultBlockState(), sourceState, 3);
        return true;
    }

    private static boolean worldzero$isPlayerTooClose(ServerPlayer player, BlockPos pos) {
        double dx = (pos.getX() + 0.5D) - player.getX();
        double dy = (pos.getY() + 0.5D) - player.getY();
        double dz = (pos.getZ() + 0.5D) - player.getZ();
        return dx * dx + dy * dy + dz * dz
                <= WORLDZERO_PLAYER_MIN_DISTANCE_TO_TARGET_BLOCKS * WORLDZERO_PLAYER_MIN_DISTANCE_TO_TARGET_BLOCKS;
    }

    private static boolean worldzero$isTargetWatched(
            ServerLevel level,
            ServerPlayer player,
            WorldZeroHouseDetector.DetectedHouse house,
            BlockPos pos,
            BlockState state
    ) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 target = worldzero$getObservationPoint(pos, state);
        Vec3 toTarget = target.subtract(eyePos);
        if (toTarget.lengthSqr() < 1.0E-6D) {
            return true;
        }

        double facingDot = player.getViewVector(1.0F).normalize().dot(toTarget.normalize());
        if (facingDot < WORLDZERO_WATCH_DOT) {
            return false;
        }

        BlockHitResult hitResult = level.clip(new ClipContext(
                eyePos,
                target,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        if (hitResult.getType() == HitResult.Type.MISS) {
            return true;
        }

        BlockPos hitPos = hitResult.getBlockPos();
        return hitPos.equals(pos)
                || (state.is(BlockTags.DOORS) && (hitPos.equals(pos.above()) || hitPos.equals(pos.below())))
                || worldzero$isInsideHouseShell(house, hitPos);
    }

    private static Vec3 worldzero$getObservationPoint(BlockPos pos, BlockState state) {
        double yOffset = state.is(BlockTags.DOORS) ? 0.9D : 0.5D;
        return new Vec3(pos.getX() + 0.5D, pos.getY() + yOffset, pos.getZ() + 0.5D);
    }

    private static boolean worldzero$isInsideHouseShell(WorldZeroHouseDetector.DetectedHouse house, BlockPos pos) {
        return pos.getX() >= house.interiorMin().getX() - 1
                && pos.getX() <= house.interiorMax().getX() + 1
                && pos.getY() >= house.interiorMin().getY() - 1
                && pos.getY() <= house.interiorMax().getY() + 1
                && pos.getZ() >= house.interiorMin().getZ() - 1
                && pos.getZ() <= house.interiorMax().getZ() + 1;
    }

    private static boolean worldzero$openSilentDoor(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.DOORS) || !state.hasProperty(DoorBlock.HALF) || !state.hasProperty(DoorBlock.OPEN)) {
            return false;
        }

        BlockPos lowerPos = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        BlockPos upperPos = lowerPos.above();
        BlockState lowerState = level.getBlockState(lowerPos);
        BlockState upperState = level.getBlockState(upperPos);
        if (!lowerState.is(BlockTags.DOORS)
                || !upperState.is(BlockTags.DOORS)
                || !lowerState.hasProperty(DoorBlock.OPEN)
                || !upperState.hasProperty(DoorBlock.OPEN)
                || lowerState.getValue(DoorBlock.OPEN)) {
            return false;
        }

        level.setBlock(lowerPos, lowerState.setValue(DoorBlock.OPEN, true), 18);
        level.setBlock(upperPos, upperState.setValue(DoorBlock.OPEN, true), 18);
        return true;
    }

    private static boolean worldzero$openSilentChest(ServerLevel level, BlockPos pos, PlayerState playerState) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) {
            return false;
        }

        level.blockEvent(pos, state.getBlock(), 1, 1);
        level.sendBlockUpdated(pos, state, state, 3);
        playerState.worldzero$openedChestPos = pos.immutable();
        playerState.worldzero$openedChestCloseTick = level.getGameTime() + WORLDZERO_SILENT_CHEST_TICKS;
        return true;
    }

    private static void worldzero$closeSilentChestIfNeeded(ServerLevel level, PlayerState playerState) {
        if (playerState.worldzero$openedChestPos == null || playerState.worldzero$openedChestCloseTick < 0L) {
            return;
        }

        if (level.getGameTime() < playerState.worldzero$openedChestCloseTick) {
            return;
        }

        BlockState chestState = level.getBlockState(playerState.worldzero$openedChestPos);
        if (chestState.getBlock() instanceof ChestBlock) {
            level.blockEvent(playerState.worldzero$openedChestPos, chestState.getBlock(), 1, 0);
            level.sendBlockUpdated(playerState.worldzero$openedChestPos, chestState, chestState, 3);
        }

        playerState.worldzero$openedChestPos = null;
        playerState.worldzero$openedChestCloseTick = -1L;
    }

    private static void worldzero$loadPersistentPlayerState(
            ServerLevel level,
            UUID playerId,
            PlayerState playerState
    ) {
        if (playerState.worldzero$persistentLoaded) {
            return;
        }

        PersistentPlayerState persistentPlayerState = worldzero$getSaveData(level).worldzero$playerStates.get(playerId);
        if (persistentPlayerState != null) {
            playerState.worldzero$elapsedPlayTicks = persistentPlayerState.worldzero$elapsedPlayTicks;
            playerState.worldzero$nextAnomalyTick = persistentPlayerState.worldzero$nextAnomalyTick;
            playerState.worldzero$firstManifestationDone = persistentPlayerState.worldzero$firstManifestationDone;
            playerState.worldzero$lastAnomalyKind = persistentPlayerState.worldzero$lastAnomalyKind;
        }

        playerState.worldzero$lastPlayTimeSaveGameTick = level.getGameTime();
        playerState.worldzero$persistentLoaded = true;
    }

    private static void worldzero$savePersistentPlayerState(
            ServerLevel level,
            UUID playerId,
            PlayerState playerState
    ) {
        WorldMemorySaveData saveData = worldzero$getSaveData(level);
        PersistentPlayerState persistentPlayerState = saveData.worldzero$playerStates.computeIfAbsent(
                playerId,
                ignored -> new PersistentPlayerState()
        );
        persistentPlayerState.worldzero$elapsedPlayTicks = playerState.worldzero$elapsedPlayTicks;
        persistentPlayerState.worldzero$nextAnomalyTick = playerState.worldzero$nextAnomalyTick;
        persistentPlayerState.worldzero$firstManifestationDone = playerState.worldzero$firstManifestationDone;
        persistentPlayerState.worldzero$lastAnomalyKind = playerState.worldzero$lastAnomalyKind;
        playerState.worldzero$lastPlayTimeSaveGameTick = level.getGameTime();
        saveData.setDirty();
    }

    private static WorldMemorySaveData worldzero$getSaveData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(WorldMemorySaveData::load, WorldMemorySaveData::new, WORLDZERO_SAVE_ID);
    }

    private enum Phase {
        DORMANT,
        FIRST,
        ACTIVE,
        PEAK,
        DECLINE
    }

    private enum AnomalyKind {
        DOOR_OPEN,
        CHEST_OPEN,
        FURNITURE_SHIFT
    }

    private static final class SessionState {
        private final Map<UUID, PlayerState> worldzero$playerStates = new HashMap<>();
    }

    private static final class PlayerState {
        private boolean worldzero$persistentLoaded;
        private long worldzero$lastHouseScanTick = -1L;
        private long worldzero$elapsedPlayTicks;
        private long worldzero$lastPlayTimeSaveGameTick = -1L;
        private long worldzero$nextAnomalyTick = -1L;
        private boolean worldzero$firstManifestationDone;
        @Nullable
        private AnomalyKind worldzero$lastAnomalyKind;

        @Nullable
        private WorldZeroHouseDetector.DetectedHouse worldzero$rememberedHouse;
        private long worldzero$rememberedHouseUntilTick = -1L;

        @Nullable
        private BlockPos worldzero$openedChestPos;
        private long worldzero$openedChestCloseTick = -1L;
    }

    private static final class PersistentPlayerState {
        private long worldzero$elapsedPlayTicks;
        private long worldzero$nextAnomalyTick = -1L;
        private boolean worldzero$firstManifestationDone;
        @Nullable
        private AnomalyKind worldzero$lastAnomalyKind;
    }

    private static final class WorldMemorySaveData extends SavedData {
        private final Map<UUID, PersistentPlayerState> worldzero$playerStates = new HashMap<>();

        private static WorldMemorySaveData load(CompoundTag tag) {
            WorldMemorySaveData saveData = new WorldMemorySaveData();
            ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
            for (int index = 0; index < players.size(); index++) {
                CompoundTag playerTag = players.getCompound(index);
                if (!playerTag.hasUUID("player_id")) {
                    continue;
                }

                PersistentPlayerState playerState = new PersistentPlayerState();
                playerState.worldzero$elapsedPlayTicks = playerTag.getLong("play_time_ticks");
                playerState.worldzero$nextAnomalyTick = playerTag.getLong("next_anomaly_tick");
                playerState.worldzero$firstManifestationDone = playerTag.getBoolean("first_manifestation_done");
                if (playerTag.contains("last_anomaly_kind", Tag.TAG_STRING)) {
                    try {
                        playerState.worldzero$lastAnomalyKind = AnomalyKind.valueOf(playerTag.getString("last_anomaly_kind"));
                    } catch (IllegalArgumentException ignored) {
                        playerState.worldzero$lastAnomalyKind = null;
                    }
                }
                saveData.worldzero$playerStates.put(playerTag.getUUID("player_id"), playerState);
            }
            return saveData;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag players = new ListTag();
            for (Map.Entry<UUID, PersistentPlayerState> entry : this.worldzero$playerStates.entrySet()) {
                CompoundTag playerTag = new CompoundTag();
                playerTag.putUUID("player_id", entry.getKey());
                playerTag.putLong("play_time_ticks", entry.getValue().worldzero$elapsedPlayTicks);
                playerTag.putLong("next_anomaly_tick", entry.getValue().worldzero$nextAnomalyTick);
                playerTag.putBoolean("first_manifestation_done", entry.getValue().worldzero$firstManifestationDone);
                if (entry.getValue().worldzero$lastAnomalyKind != null) {
                    playerTag.putString("last_anomaly_kind", entry.getValue().worldzero$lastAnomalyKind.name());
                }
                players.add(playerTag);
            }
            tag.put("players", players);
            return tag;
        }
    }
}
