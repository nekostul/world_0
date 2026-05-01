package ru.nekostul.worldzero;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroHouseEvent {
    private static final String WORLDZERO_HOUSE_BLACK_ECHO_TAG = "worldzero_house_black_echo";
    private static final String WORLDZERO_SAVE_ID = "worldzero_house_event";
    private static final AABB WORLDZERO_ENTITY_SCAN_AABB = new AABB(
            -30_000_000.0D,
            -2_048.0D,
            -30_000_000.0D,
            30_000_000.0D,
            4_096.0D,
            30_000_000.0D
    );
    private static final double WORLDZERO_TRIGGER_REARM_MARGIN_BLOCKS = 4.0D;
    private static final long WORLDZERO_HOUSE_MEMORY_MULTIPLIER = 12L;
    private static final Map<MinecraftServer, SessionState> WORLDZERO_SERVER_STATES = new WeakHashMap<>();

    private WorldZeroHouseEvent() {
    }

    @SubscribeEvent
    public static void worldzero$onServerStarted(ServerStartedEvent event) {
        worldzero$cleanupOrphanDisplays(event.getServer());
    }

    @SubscribeEvent
    public static void worldzero$onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.player instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }

        if (!WorldZeroConfig.worldzero$isHouseEventEnabled()) {
            return;
        }

        if (!player.isAlive() || player.isSpectator() || player.level().dimension() != Level.OVERWORLD) {
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

        long gameTime = player.serverLevel().getGameTime();
        long storyTicks = WorldZeroStoryTime.worldzero$getStoryTicks(player.serverLevel());
        worldzero$ensureHouseTimeline(player.serverLevel(), player.getUUID(), playerState);
        if (playerState.worldzero$realFireTriggered) {
            if (playerState.worldzero$bedrockActive) {
                worldzero$deactivateBedrockScene(server, player, playerState);
            }
            return;
        }

        if (storyTicks < WorldZeroConfig.worldzero$houseActiveStartTick()) {
            return;
        }

        if (playerState.worldzero$bedrockActive) {
            if (playerState.worldzero$activeHouse == null
                    || playerState.worldzero$activeVisualBlocks.isEmpty()) {
                worldzero$deactivateBedrockScene(server, player, playerState);
                return;
            }

            if (storyTicks >= playerState.worldzero$finalRealFireTick
                    && worldzero$igniteRealHouseFire(player, playerState, playerState.worldzero$activeHouse)) {
                return;
            }

            double distanceToHouse = Math.sqrt(playerState.worldzero$activeHouse.worldzero$horizontalDistanceToBoundsSqr(
                    player.getX(),
                    player.getZ()
            ));
            if (distanceToHouse <= WorldZeroConfig.worldzero$houseDisappearDistanceBlocks()) {
                if (!playerState.worldzero$blackEchoDismissed && playerState.worldzero$activeBlackEchoId != null) {
                    Entity blackEcho = worldzero$findEntity(server, playerState.worldzero$activeBlackEchoId);
                    if (blackEcho != null) {
                        blackEcho.discard();
                    }
                    playerState.worldzero$activeBlackEchoId = null;
                    playerState.worldzero$blackEchoDismissed = true;
                }
            } else if (!playerState.worldzero$blackEchoDismissed
                    && !worldzero$hasActiveHouseBlackEcho(server, playerState.worldzero$activeBlackEchoId)) {
                playerState.worldzero$activeBlackEchoId = worldzero$spawnHouseBlackEcho(
                        player.serverLevel(),
                        player,
                        playerState.worldzero$activeHouse
                );
            }

            if (distanceToHouse <= playerState.worldzero$restoreDistanceThreshold) {
                worldzero$deactivateBedrockScene(server, player, playerState);
            }
            return;
        }

        WorldZeroHouseDetector.DetectedHouse detectedHouse = worldzero$getDetectedOrRememberedHouse(
                player,
                playerState,
                gameTime,
                false
        );
        if (detectedHouse == null) {
            return;
        }

        if (WorldZeroAmbientSoundEvent.worldzero$isMajorEventStartBlocked(player.serverLevel())) {
            return;
        }

        double distanceToHouse = Math.sqrt(detectedHouse.worldzero$horizontalDistanceToBoundsSqr(
                player.getX(),
                player.getZ()
        ));
        double triggerMin = WorldZeroConfig.worldzero$houseTriggerDistanceMinBlocks();
        double triggerMax = WorldZeroConfig.worldzero$houseTriggerDistanceMaxBlocks();

        if (distanceToHouse > triggerMax + WORLDZERO_TRIGGER_REARM_MARGIN_BLOCKS) {
            if (!playerState.worldzero$armedForApproach) {
                playerState.worldzero$armedForApproach = true;
                playerState.worldzero$wasInsideTriggerBand = false;
                worldzero$rollTriggerBand(player.serverLevel(), playerState);
            }
            return;
        }

        if (!playerState.worldzero$armedForApproach) {
            return;
        }

        if (playerState.worldzero$triggerBandMin < triggerMin
                || playerState.worldzero$triggerBandMax > triggerMax
                || playerState.worldzero$triggerBandMin < 0.0D
                || playerState.worldzero$triggerBandMax < 0.0D) {
            worldzero$rollTriggerBand(player.serverLevel(), playerState);
        }

        boolean insideTriggerBand = distanceToHouse >= playerState.worldzero$triggerBandMin
                && distanceToHouse <= playerState.worldzero$triggerBandMax;

        if (storyTicks >= playerState.worldzero$finalRealFireTick) {
            if (insideTriggerBand && WorldZeroStoryTime.worldzero$canReceiveStoryEvent(player)) {
                worldzero$igniteRealHouseFire(player, playerState, detectedHouse);
                playerState.worldzero$armedForApproach = false;
            }
            playerState.worldzero$wasInsideTriggerBand = insideTriggerBand;
            return;
        }

        if (!playerState.worldzero$wasInsideTriggerBand
                && insideTriggerBand
                && storyTicks >= playerState.worldzero$nextScheduledVisualTick
                && WorldZeroStoryTime.worldzero$canReceiveStoryEvent(player)) {
            playerState.worldzero$armedForApproach = false;
            if (worldzero$activateBedrockScene(player, playerState, detectedHouse, false)) {
                playerState.worldzero$nextScheduledVisualTick = storyTicks
                        + worldzero$randomVisualRepeatTicks(player.serverLevel());
                worldzero$savePersistentPlayerState(player.serverLevel(), player.getUUID(), playerState);
            }
        }
        playerState.worldzero$wasInsideTriggerBand = insideTriggerBand;
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        worldzero$cleanupOrphanDisplays(event.getServer());
        WORLDZERO_SERVER_STATES.remove(event.getServer());
    }

    public static boolean worldzero$triggerHouseNow(ServerPlayer player) {
        return worldzero$triggerHouseNowInternal(player, true);
    }

    public static boolean worldzero$triggerHouseNowDebug(ServerPlayer player) {
        return worldzero$triggerHouseNowInternal(player, false);
    }

    public static boolean worldzero$isHouseActive(MinecraftServer server) {
        SessionState sessionState = WORLDZERO_SERVER_STATES.get(server);
        if (sessionState == null) {
            return false;
        }

        for (PlayerState playerState : sessionState.worldzero$playerStates.values()) {
            if (playerState.worldzero$bedrockActive) {
                return true;
            }
        }
        return false;
    }

    public static boolean worldzero$stopHouseNow(MinecraftServer server) {
        SessionState sessionState = WORLDZERO_SERVER_STATES.get(server);
        if (server == null || sessionState == null) {
            return false;
        }

        boolean changed = false;
        for (Map.Entry<UUID, PlayerState> entry : sessionState.worldzero$playerStates.entrySet()) {
            PlayerState playerState = entry.getValue();
            if (!playerState.worldzero$bedrockActive
                    && playerState.worldzero$activeVisualBlocks.isEmpty()
                    && playerState.worldzero$activeBlackEchoId == null) {
                continue;
            }

            worldzero$deactivateBedrockScene(server, server.getPlayerList().getPlayer(entry.getKey()), playerState);
            changed = true;
        }
        return changed;
    }

    private static boolean worldzero$triggerHouseNowInternal(ServerPlayer player, boolean enforceRealConditions) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return false;
        }

        if (player.level().dimension() != Level.OVERWORLD) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        SessionState sessionState = WORLDZERO_SERVER_STATES.computeIfAbsent(server, ignored -> new SessionState());
        PlayerState playerState = sessionState.worldzero$playerStates.computeIfAbsent(
                player.getUUID(),
                ignored -> new PlayerState()
        );
        worldzero$loadPersistentPlayerState(player.serverLevel(), player.getUUID(), playerState);
        if (playerState.worldzero$bedrockActive) {
            return false;
        }

        long gameTime = player.serverLevel().getGameTime();
        WorldZeroHouseDetector.DetectedHouse detectedHouse = worldzero$getDetectedOrRememberedHouse(
                player,
                playerState,
                gameTime,
                true
        );
        boolean useSyntheticFallback = false;
        if (detectedHouse == null && !enforceRealConditions) {
            detectedHouse = worldzero$createDebugFallbackHouse(player);
            useSyntheticFallback = true;
        }
        if (detectedHouse == null) {
            return false;
        }

        if (enforceRealConditions) {
            double distanceToHouse = Math.sqrt(detectedHouse.worldzero$horizontalDistanceToBoundsSqr(
                    player.getX(),
                    player.getZ()
            ));
            if (distanceToHouse < WorldZeroConfig.worldzero$houseTriggerDistanceMinBlocks()
                    || distanceToHouse > WorldZeroConfig.worldzero$houseTriggerDistanceMaxBlocks()) {
                return false;
            }
        }

        return worldzero$activateBedrockScene(player, playerState, detectedHouse, useSyntheticFallback);
    }

    private static boolean worldzero$activateBedrockScene(
            ServerPlayer player,
            PlayerState playerState,
            WorldZeroHouseDetector.DetectedHouse detectedHouse,
            boolean syntheticFallback
    ) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        worldzero$deactivateBedrockScene(server, player, playerState);

        ServerLevel level = player.serverLevel();
        int minX = detectedHouse.interiorMin().getX() - 1;
        int minY = detectedHouse.interiorMin().getY() - 1;
        int minZ = detectedHouse.interiorMin().getZ() - 1;
        int maxX = detectedHouse.interiorMax().getX() + 1;
        int maxY = detectedHouse.interiorMax().getY() + 1;
        int maxZ = detectedHouse.interiorMax().getZ() + 1;
        List<BlockPos> visualBlocks = worldzero$collectVisualHouseFireBlocks(
                level,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                syntheticFallback
        );
        if (visualBlocks.isEmpty()) {
            return false;
        }

        List<BlockPos> hiddenBurningBlocks = worldzero$collectVisualBurnoutBlocks(
                level,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                syntheticFallback
        );

        Set<Long> activeKeys = new HashSet<>();
        for (BlockPos blockPos : hiddenBurningBlocks) {
            player.connection.send(new ClientboundBlockUpdatePacket(blockPos, Blocks.AIR.defaultBlockState()));
            if (activeKeys.add(blockPos.asLong())) {
                playerState.worldzero$activeVisualBlocks.add(blockPos);
            }
        }

        for (BlockPos blockPos : visualBlocks) {
            BlockState fireState = worldzero$createVisualFireState(level, blockPos);
            if (fireState.isAir()) {
                continue;
            }
            player.connection.send(new ClientboundBlockUpdatePacket(blockPos, fireState));
            if (activeKeys.add(blockPos.asLong())) {
                playerState.worldzero$activeVisualBlocks.add(blockPos);
            }
        }
        if (playerState.worldzero$activeVisualBlocks.isEmpty()) {
            return false;
        }

        playerState.worldzero$bedrockActive = true;
        playerState.worldzero$activeHouse = detectedHouse;
        playerState.worldzero$restoreDistanceThreshold = WorldZeroConfig.worldzero$houseFireClearDistanceBlocks();
        playerState.worldzero$activeBlackEchoId = worldzero$spawnHouseBlackEcho(level, player, detectedHouse);
        playerState.worldzero$blackEchoDismissed = false;
        playerState.worldzero$armedForApproach = false;
        playerState.worldzero$wasInsideTriggerBand = false;
        WorldZeroAmbientSoundEvent.worldzero$notifyMajorEventStarted(level);
        return true;
    }

    private static boolean worldzero$igniteRealHouseFire(
            ServerPlayer player,
            PlayerState playerState,
            @Nullable WorldZeroHouseDetector.DetectedHouse detectedHouse
    ) {
        if (detectedHouse == null) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        int minX = detectedHouse.interiorMin().getX() - 1;
        int minY = detectedHouse.interiorMin().getY() - 1;
        int minZ = detectedHouse.interiorMin().getZ() - 1;
        int maxX = detectedHouse.interiorMax().getX() + 1;
        int maxY = detectedHouse.interiorMax().getY() + 1;
        int maxZ = detectedHouse.interiorMax().getZ() + 1;

        List<BlockPos> fireBlocks = worldzero$collectVisualHouseFireBlocks(
                level,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                false
        );
        List<BlockPos> burnoutBlocks = worldzero$collectVisualBurnoutBlocks(
                level,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                false
        );
        if (fireBlocks.isEmpty() && burnoutBlocks.isEmpty()) {
            return false;
        }

        worldzero$deactivateBedrockScene(server, player, playerState);

        boolean changed = false;
        for (BlockPos blockPos : burnoutBlocks) {
            BlockState currentState = level.getBlockState(blockPos);
            if (!currentState.isAir() && currentState.getFluidState().isEmpty()) {
                changed = true;
                level.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 3);
            }
        }

        for (BlockPos blockPos : fireBlocks) {
            BlockState fireState = worldzero$createVisualFireState(level, blockPos);
            if (fireState.isAir()) {
                continue;
            }
            BlockState currentState = level.getBlockState(blockPos);
            if (!currentState.isAir() || !currentState.getFluidState().isEmpty()) {
                continue;
            }
            changed = true;
            level.setBlock(blockPos, fireState, 3);
        }

        if (!changed) {
            return false;
        }

        playerState.worldzero$realFireTriggered = true;
        playerState.worldzero$armedForApproach = false;
        playerState.worldzero$wasInsideTriggerBand = false;
        playerState.worldzero$rememberedHouse = null;
        playerState.worldzero$rememberedHouseUntilTick = 0L;
        WorldZeroAmbientSoundEvent.worldzero$notifyMajorEventStarted(level);
        worldzero$savePersistentPlayerState(level, player.getUUID(), playerState);
        return true;
    }

    private static void worldzero$deactivateBedrockScene(
            MinecraftServer server,
            @Nullable ServerPlayer player,
            PlayerState playerState
    ) {
        if (player != null && !playerState.worldzero$activeVisualBlocks.isEmpty()) {
            ServerLevel level = player.serverLevel();
            for (BlockPos blockPos : playerState.worldzero$activeVisualBlocks) {
                player.connection.send(new ClientboundBlockUpdatePacket(blockPos, level.getBlockState(blockPos)));
            }
        }
        playerState.worldzero$activeVisualBlocks.clear();

        playerState.worldzero$bedrockActive = false;
        playerState.worldzero$activeHouse = null;
        playerState.worldzero$restoreDistanceThreshold = -1.0D;
        playerState.worldzero$blackEchoDismissed = false;
        if (playerState.worldzero$activeBlackEchoId != null) {
            Entity blackEcho = worldzero$findEntity(server, playerState.worldzero$activeBlackEchoId);
            if (blackEcho != null) {
                blackEcho.discard();
            }
            playerState.worldzero$activeBlackEchoId = null;
        }
    }

    @Nullable
    private static WorldZeroHouseDetector.DetectedHouse worldzero$getDetectedOrRememberedHouse(
            ServerPlayer player,
            PlayerState playerState,
            long gameTime,
            boolean forceScan
    ) {
        WorldZeroHouseDetector.DetectedHouse detectedHouse = null;
        if (forceScan || gameTime - playerState.worldzero$lastScanTick >= WorldZeroConfig.worldzero$houseScanIntervalTicks()) {
            playerState.worldzero$lastScanTick = gameTime;
            detectedHouse = WorldZeroHouseDetector.worldzero$findNearbyHouseForDebug(player);
            if (detectedHouse != null) {
                playerState.worldzero$rememberedHouse = detectedHouse;
                playerState.worldzero$rememberedHouseUntilTick = gameTime + worldzero$houseMemoryTicks();
            }
        }

        if (detectedHouse != null) {
            return detectedHouse;
        }

        if (playerState.worldzero$rememberedHouse != null
                && gameTime <= playerState.worldzero$rememberedHouseUntilTick) {
            return playerState.worldzero$rememberedHouse;
        }
        return null;
    }

    private static long worldzero$houseMemoryTicks() {
        return Math.max(
                200L,
                (long) WorldZeroConfig.worldzero$houseScanIntervalTicks() * WORLDZERO_HOUSE_MEMORY_MULTIPLIER
        );
    }

    private static void worldzero$loadPersistentPlayerState(
            ServerLevel level,
            UUID playerId,
            PlayerState playerState
    ) {
        if (playerState.worldzero$persistentLoaded) {
            return;
        }

        HouseSaveData saveData = worldzero$getSaveData(level);
        PersistentPlayerState persistentState = saveData.worldzero$playerStates.get(playerId);
        if (persistentState != null) {
            playerState.worldzero$realFireTriggered = persistentState.worldzero$realFireTriggered;
            playerState.worldzero$nextScheduledVisualTick = persistentState.worldzero$nextScheduledVisualTick;
            playerState.worldzero$finalRealFireTick = persistentState.worldzero$finalRealFireTick;
        }
        playerState.worldzero$persistentLoaded = true;
    }

    private static void worldzero$savePersistentPlayerState(
            ServerLevel level,
            UUID playerId,
            PlayerState playerState
    ) {
        HouseSaveData saveData = worldzero$getSaveData(level);
        PersistentPlayerState persistentState = saveData.worldzero$playerStates.computeIfAbsent(
                playerId,
                ignored -> new PersistentPlayerState()
        );

        boolean changed = false;
        if (persistentState.worldzero$realFireTriggered != playerState.worldzero$realFireTriggered) {
            persistentState.worldzero$realFireTriggered = playerState.worldzero$realFireTriggered;
            changed = true;
        }
        if (persistentState.worldzero$nextScheduledVisualTick != playerState.worldzero$nextScheduledVisualTick) {
            persistentState.worldzero$nextScheduledVisualTick = playerState.worldzero$nextScheduledVisualTick;
            changed = true;
        }
        if (persistentState.worldzero$finalRealFireTick != playerState.worldzero$finalRealFireTick) {
            persistentState.worldzero$finalRealFireTick = playerState.worldzero$finalRealFireTick;
            changed = true;
        }

        if (changed) {
            saveData.setDirty();
        }
    }

    private static HouseSaveData worldzero$getSaveData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                HouseSaveData::worldzero$load,
                HouseSaveData::new,
                WORLDZERO_SAVE_ID
        );
    }

    private static void worldzero$ensureHouseTimeline(ServerLevel level, UUID playerId, PlayerState playerState) {
        boolean changed = false;
        long activeStartTick = WorldZeroConfig.worldzero$houseActiveStartTick();
        if (playerState.worldzero$nextScheduledVisualTick < activeStartTick) {
            playerState.worldzero$nextScheduledVisualTick = activeStartTick;
            changed = true;
        }

        long minTick = WorldZeroConfig.worldzero$houseRealFireMinTick();
        long maxTick = WorldZeroConfig.worldzero$houseRealFireMaxTick();
        if (playerState.worldzero$finalRealFireTick < minTick
                || playerState.worldzero$finalRealFireTick > maxTick) {
            long span = Math.max(0L, maxTick - minTick);
            playerState.worldzero$finalRealFireTick = minTick
                    + (long) Math.floor(level.random.nextDouble() * (double) (span + 1L));
            changed = true;
        }

        if (changed) {
            worldzero$savePersistentPlayerState(level, playerId, playerState);
        }
    }

    private static long worldzero$randomVisualRepeatTicks(ServerLevel level) {
        long minTicks = WorldZeroConfig.worldzero$houseRepeatMinTicks();
        long maxTicks = Math.max(minTicks, (long) WorldZeroConfig.worldzero$houseRepeatMaxTicks());
        long span = Math.max(0L, maxTicks - minTicks);
        return minTicks + (long) Math.floor(level.random.nextDouble() * (double) (span + 1L));
    }

    private static void worldzero$rollTriggerBand(ServerLevel level, PlayerState playerState) {
        double triggerMin = WorldZeroConfig.worldzero$houseTriggerDistanceMinBlocks();
        double triggerMax = WorldZeroConfig.worldzero$houseTriggerDistanceMaxBlocks();
        double center = Mth.nextDouble(level.random, triggerMin, triggerMax);
        double halfBandWidth = Mth.nextDouble(level.random, 0.8D, 1.8D);

        double bandMin = Math.max(triggerMin, center - halfBandWidth);
        double bandMax = Math.min(triggerMax, center + halfBandWidth);
        if (bandMax <= bandMin) {
            bandMax = Math.min(triggerMax, bandMin + 0.5D);
        }

        playerState.worldzero$triggerBandMin = bandMin;
        playerState.worldzero$triggerBandMax = bandMax;
    }

    private static List<BlockPos> worldzero$collectVisualHouseFireBlocks(
            ServerLevel level,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            boolean syntheticFallback
    ) {
        List<BlockPos> result = new ArrayList<>();
        Set<Long> added = new HashSet<>();
        int maxBlocks = WorldZeroConfig.worldzero$houseBedrockMaxBlocks();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos shellPos = new BlockPos(x, y, z);
                    if (!worldzero$isShellPosition(shellPos, minX, minY, minZ, maxX, maxY, maxZ)) {
                        continue;
                    }

                    BlockState worldState = level.getBlockState(shellPos);
                    if (worldState.isAir() || !worldState.getFluidState().isEmpty()) {
                        continue;
                    }

                    if (x == minX) {
                        worldzero$tryAddFireCandidate(level, shellPos.west(), added, result, maxBlocks);
                    }
                    if (x == maxX) {
                        worldzero$tryAddFireCandidate(level, shellPos.east(), added, result, maxBlocks);
                    }
                    if (z == minZ) {
                        worldzero$tryAddFireCandidate(level, shellPos.north(), added, result, maxBlocks);
                    }
                    if (z == maxZ) {
                        worldzero$tryAddFireCandidate(level, shellPos.south(), added, result, maxBlocks);
                    }
                    if (y == maxY) {
                        worldzero$tryAddFireCandidate(level, shellPos.above(), added, result, maxBlocks);
                    }
                }
            }
        }

        if (syntheticFallback) {
            return result;
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (result.size() >= maxBlocks) {
                        return result;
                    }

                    BlockPos blockPos = new BlockPos(x, y, z);
                    BlockState worldState = level.getBlockState(blockPos);
                    if (worldState.isAir() || !worldState.getFluidState().isEmpty()) {
                        continue;
                    }

                    worldzero$tryAddFireCandidate(level, blockPos.above(), added, result, maxBlocks);
                    worldzero$tryAddFireCandidate(level, blockPos.north(), added, result, maxBlocks);
                    worldzero$tryAddFireCandidate(level, blockPos.south(), added, result, maxBlocks);
                    worldzero$tryAddFireCandidate(level, blockPos.east(), added, result, maxBlocks);
                    worldzero$tryAddFireCandidate(level, blockPos.west(), added, result, maxBlocks);
                }
            }
        }

        return result;
    }

    private static List<BlockPos> worldzero$collectVisualBurnoutBlocks(
            ServerLevel level,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            boolean syntheticFallback
    ) {
        List<BlockPos> result = new ArrayList<>();
        if (syntheticFallback) {
            return result;
        }

        int maxHiddenBlocks = Math.max(8, WorldZeroConfig.worldzero$houseBedrockMaxBlocks() / 6);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (result.size() >= maxHiddenBlocks) {
                        return result;
                    }

                    BlockPos shellPos = new BlockPos(x, y, z);
                    if (!worldzero$isShellPosition(shellPos, minX, minY, minZ, maxX, maxY, maxZ)) {
                        continue;
                    }

                    BlockState worldState = level.getBlockState(shellPos);
                    if (worldState.isAir() || !worldState.getFluidState().isEmpty()) {
                        continue;
                    }

                    if (!worldzero$shouldVisuallyBurnAway(shellPos) || !worldzero$hasPotentialFireAdjacent(level, shellPos)) {
                        continue;
                    }

                    result.add(shellPos.immutable());
                }
            }
        }

        return result;
    }

    private static void worldzero$tryAddFireCandidate(
            ServerLevel level,
            BlockPos candidatePos,
            Set<Long> added,
            List<BlockPos> result,
            int maxBlocks
    ) {
        if (result.size() >= maxBlocks) {
            return;
        }

        BlockState worldState = level.getBlockState(candidatePos);
        if (!worldState.isAir() || !worldState.getFluidState().isEmpty()) {
            return;
        }

        BlockState fireState = worldzero$createVisualFireState(level, candidatePos);
        if (fireState.isAir()) {
            return;
        }

        long key = candidatePos.asLong();
        if (added.add(key)) {
            result.add(candidatePos.immutable());
        }
    }

    private static boolean worldzero$hasPotentialFireAdjacent(ServerLevel level, BlockPos blockPos) {
        BlockPos[] candidates = new BlockPos[] {
                blockPos.above(),
                blockPos.north(),
                blockPos.south(),
                blockPos.east(),
                blockPos.west()
        };
        for (BlockPos candidate : candidates) {
            BlockState candidateState = level.getBlockState(candidate);
            if (!candidateState.isAir() || !candidateState.getFluidState().isEmpty()) {
                continue;
            }

            if (!worldzero$createVisualFireState(level, candidate).isAir()) {
                return true;
            }
        }
        return false;
    }

    private static boolean worldzero$shouldVisuallyBurnAway(BlockPos blockPos) {
        int hash = blockPos.getX() * 31 + blockPos.getY() * 17 + blockPos.getZ() * 13;
        return Math.floorMod(hash, 5) == 0;
    }

    private static BlockState worldzero$createVisualFireState(ServerLevel level, BlockPos blockPos) {
        BlockState fireState = Blocks.FIRE.defaultBlockState();
        boolean hasSupport = false;

        BlockPos belowPos = blockPos.below();
        BlockState belowState = level.getBlockState(belowPos);
        if (!belowState.isAir()
                && belowState.getFluidState().isEmpty()
                && belowState.isFaceSturdy(level, belowPos, Direction.UP)) {
            hasSupport = true;
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos supportPos = blockPos.relative(direction);
            BlockState supportState = level.getBlockState(supportPos);
            if (supportState.isAir() || !supportState.getFluidState().isEmpty()) {
                continue;
            }

            hasSupport = true;
            fireState = switch (direction) {
                case NORTH -> fireState.setValue(FireBlock.NORTH, true);
                case SOUTH -> fireState.setValue(FireBlock.SOUTH, true);
                case WEST -> fireState.setValue(FireBlock.WEST, true);
                case EAST -> fireState.setValue(FireBlock.EAST, true);
                default -> fireState;
            };
        }

        if (!hasSupport) {
            return Blocks.AIR.defaultBlockState();
        }

        return fireState;
    }

    @Nullable
    private static UUID worldzero$spawnHouseBlackEcho(
            ServerLevel level,
            ServerPlayer player,
            WorldZeroHouseDetector.DetectedHouse detectedHouse
    ) {
        BlockPos spawnPos = worldzero$findBlackEchoSpawnPosition(level, player, detectedHouse);
        if (spawnPos == null) {
            return null;
        }

        WorldZeroEchoEntity blackEcho = WorldZeroEntities.WORLDZERO_BLACK_ECHO.get().create(level);
        if (blackEcho == null) {
            return null;
        }

        blackEcho.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, player.getYRot() + 180.0F, 0.0F);
        blackEcho.addTag(WORLDZERO_HOUSE_BLACK_ECHO_TAG);
        level.addFreshEntity(blackEcho);
        return blackEcho.getUUID();
    }

    @Nullable
    private static BlockPos worldzero$findBlackEchoSpawnPosition(
            ServerLevel level,
            ServerPlayer player,
            WorldZeroHouseDetector.DetectedHouse detectedHouse
    ) {
        int minX = detectedHouse.interiorMin().getX() - 1;
        int minZ = detectedHouse.interiorMin().getZ() - 1;
        int maxX = detectedHouse.interiorMax().getX() + 1;
        int maxZ = detectedHouse.interiorMax().getZ() + 1;
        int referenceY = detectedHouse.worldzero$floorY() + 1;

        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = minX - 3; x <= maxX + 3; x++) {
            bestPos = worldzero$chooseCloserBlackEchoPosition(level, player, bestPos, bestDistance, new BlockPos(x, referenceY, minZ - 2));
            if (bestPos != null) {
                bestDistance = player.distanceToSqr(bestPos.getX() + 0.5D, bestPos.getY(), bestPos.getZ() + 0.5D);
            }
            bestPos = worldzero$chooseCloserBlackEchoPosition(level, player, bestPos, bestDistance, new BlockPos(x, referenceY, maxZ + 2));
            if (bestPos != null) {
                bestDistance = player.distanceToSqr(bestPos.getX() + 0.5D, bestPos.getY(), bestPos.getZ() + 0.5D);
            }
        }

        for (int z = minZ - 3; z <= maxZ + 3; z++) {
            bestPos = worldzero$chooseCloserBlackEchoPosition(level, player, bestPos, bestDistance, new BlockPos(minX - 2, referenceY, z));
            if (bestPos != null) {
                bestDistance = player.distanceToSqr(bestPos.getX() + 0.5D, bestPos.getY(), bestPos.getZ() + 0.5D);
            }
            bestPos = worldzero$chooseCloserBlackEchoPosition(level, player, bestPos, bestDistance, new BlockPos(maxX + 2, referenceY, z));
            if (bestPos != null) {
                bestDistance = player.distanceToSqr(bestPos.getX() + 0.5D, bestPos.getY(), bestPos.getZ() + 0.5D);
            }
        }

        if (bestPos != null) {
            return bestPos;
        }

        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        BlockPos[] fallbackBases = new BlockPos[] {
                new BlockPos(centerX, referenceY, minZ - 2),
                new BlockPos(centerX, referenceY, maxZ + 2),
                new BlockPos(minX - 2, referenceY, centerZ),
                new BlockPos(maxX + 2, referenceY, centerZ)
        };
        for (BlockPos fallbackBase : fallbackBases) {
            BlockPos standable = worldzero$findStandablePosition(level, fallbackBase, referenceY);
            if (standable != null) {
                return standable;
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos worldzero$chooseCloserBlackEchoPosition(
            ServerLevel level,
            ServerPlayer player,
            @Nullable BlockPos currentBestPos,
            double currentBestDistance,
            BlockPos base
    ) {
        BlockPos standable = worldzero$findStandablePosition(level, base, base.getY());
        if (standable == null) {
            return currentBestPos;
        }

        double playerDistance = player.distanceToSqr(standable.getX() + 0.5D, standable.getY(), standable.getZ() + 0.5D);
        if (playerDistance >= currentBestDistance) {
            return currentBestPos;
        }
        return standable;
    }

    @Nullable
    private static BlockPos worldzero$findStandablePosition(ServerLevel level, BlockPos base, int referenceY) {
        for (int y = referenceY + 4; y >= referenceY - 8; y--) {
            BlockPos candidate = new BlockPos(base.getX(), y, base.getZ());
            if (!level.getBlockState(candidate).isAir()) {
                continue;
            }
            if (!level.getBlockState(candidate.above()).isAir()) {
                continue;
            }

            BlockPos belowPos = candidate.below();
            BlockState belowState = level.getBlockState(belowPos);
            if (!belowState.isFaceSturdy(level, belowPos, Direction.UP)) {
                continue;
            }
            if (!belowState.getFluidState().isEmpty()) {
                continue;
            }

            AABB spawnBox = WorldZeroEntities.WORLDZERO_BLACK_ECHO.get().getDimensions().makeBoundingBox(
                    candidate.getX() + 0.5D,
                    candidate.getY(),
                    candidate.getZ() + 0.5D
            );
            if (!level.noCollision(spawnBox) || level.containsAnyLiquid(spawnBox)) {
                continue;
            }
            return candidate;
        }

        int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.getX(), base.getZ());
        BlockPos surfacePos = new BlockPos(base.getX(), topY, base.getZ());
        if (level.getBlockState(surfacePos).isAir()
                && level.getBlockState(surfacePos.above()).isAir()
                && level.getBlockState(surfacePos.below()).isFaceSturdy(level, surfacePos.below(), Direction.UP)
                && level.getBlockState(surfacePos.below()).getFluidState().isEmpty()) {
            AABB spawnBox = WorldZeroEntities.WORLDZERO_BLACK_ECHO.get().getDimensions().makeBoundingBox(
                    surfacePos.getX() + 0.5D,
                    surfacePos.getY(),
                    surfacePos.getZ() + 0.5D
            );
            if (level.noCollision(spawnBox) && !level.containsAnyLiquid(spawnBox)) {
                return surfacePos;
            }
        }
        return null;
    }

    private static boolean worldzero$hasActiveHouseBlackEcho(MinecraftServer server, @Nullable UUID entityId) {
        if (entityId == null) {
            return false;
        }
        Entity entity = worldzero$findEntity(server, entityId);
        return entity instanceof WorldZeroEchoEntity
                && entity.getType() == WorldZeroEntities.WORLDZERO_BLACK_ECHO.get()
                && entity.getTags().contains(WORLDZERO_HOUSE_BLACK_ECHO_TAG);
    }

    private static boolean worldzero$isShellPosition(
            BlockPos blockPos,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    ) {
        return blockPos.getX() == minX
                || blockPos.getX() == maxX
                || blockPos.getY() == minY
                || blockPos.getY() == maxY
                || blockPos.getZ() == minZ
                || blockPos.getZ() == maxZ;
    }

    @Nullable
    private static Entity worldzero$findEntity(MinecraftServer server, UUID entityId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static void worldzero$cleanupOrphanDisplays(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (WorldZeroEchoEntity blackEcho : level.getEntitiesOfClass(
                    WorldZeroEchoEntity.class,
                    WORLDZERO_ENTITY_SCAN_AABB,
                    entity -> entity.getType() == WorldZeroEntities.WORLDZERO_BLACK_ECHO.get()
                            && entity.getTags().contains(WORLDZERO_HOUSE_BLACK_ECHO_TAG)
            )) {
                blackEcho.discard();
            }
        }
    }

    private static WorldZeroHouseDetector.DetectedHouse worldzero$createDebugFallbackHouse(ServerPlayer player) {
        BlockPos center = player.blockPosition();
        BlockPos interiorMin = center.offset(-3, 0, -3);
        BlockPos interiorMax = center.offset(3, 3, 3);
        return new WorldZeroHouseDetector.DetectedHouse(
                center,
                interiorMin,
                interiorMax,
                0,
                0,
                null
        );
    }

    private static final class SessionState {
        private final Map<UUID, PlayerState> worldzero$playerStates = new HashMap<>();
    }

    private static final class PlayerState {
        private boolean worldzero$persistentLoaded;
        private long worldzero$lastScanTick;
        private boolean worldzero$bedrockActive;
        private boolean worldzero$realFireTriggered;
        private WorldZeroHouseDetector.DetectedHouse worldzero$activeHouse;
        private double worldzero$restoreDistanceThreshold = -1.0D;
        private final List<BlockPos> worldzero$activeVisualBlocks = new ArrayList<>();
        private UUID worldzero$activeBlackEchoId;
        private boolean worldzero$blackEchoDismissed;

        private boolean worldzero$armedForApproach = true;
        private boolean worldzero$wasInsideTriggerBand;
        private double worldzero$triggerBandMin = -1.0D;
        private double worldzero$triggerBandMax = -1.0D;
        private long worldzero$nextScheduledVisualTick = -1L;
        private long worldzero$finalRealFireTick = -1L;

        private WorldZeroHouseDetector.DetectedHouse worldzero$rememberedHouse;
        private long worldzero$rememberedHouseUntilTick;
    }

    private static final class PersistentPlayerState {
        private boolean worldzero$realFireTriggered;
        private long worldzero$nextScheduledVisualTick = -1L;
        private long worldzero$finalRealFireTick = -1L;
    }

    private static final class HouseSaveData extends SavedData {
        private final Map<UUID, PersistentPlayerState> worldzero$playerStates = new HashMap<>();

        private static HouseSaveData worldzero$load(CompoundTag tag) {
            HouseSaveData saveData = new HouseSaveData();
            ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
            for (int index = 0; index < players.size(); index++) {
                CompoundTag playerTag = players.getCompound(index);
                if (!playerTag.hasUUID("player_id")) {
                    continue;
                }

                PersistentPlayerState playerState = new PersistentPlayerState();
                playerState.worldzero$realFireTriggered = playerTag.getBoolean("real_fire_triggered");
                playerState.worldzero$nextScheduledVisualTick = playerTag.getLong("next_visual_tick");
                playerState.worldzero$finalRealFireTick = playerTag.getLong("final_fire_tick");
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
                playerTag.putBoolean("real_fire_triggered", entry.getValue().worldzero$realFireTriggered);
                playerTag.putLong("next_visual_tick", entry.getValue().worldzero$nextScheduledVisualTick);
                playerTag.putLong("final_fire_tick", entry.getValue().worldzero$finalRealFireTick);
                players.add(playerTag);
            }
            tag.put("players", players);
            return tag;
        }
    }
}
