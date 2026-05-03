package ru.nekostul.worldzero.event.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;
import ru.nekostul.worldzero.event.WorldZeroStoryTime;
import ru.nekostul.worldzero.event.ambient.WorldZeroAmbientSoundEvent;
import ru.nekostul.worldzero.event.fall.WorldZeroFallEvent;
import ru.nekostul.worldzero.event.footsteps.WorldZeroFootstepsEvent;
import ru.nekostul.worldzero.event.freeze.WorldZeroFreezeEvent;
import ru.nekostul.worldzero.event.horror.WorldZeroHorrorFinale;
import ru.nekostul.worldzero.event.house.WorldZeroHouseDetector;
import ru.nekostul.worldzero.event.house.WorldZeroHouseEvent;
import ru.nekostul.worldzero.event.paralysis.WorldZeroParalysisEvent;
import ru.nekostul.worldzero.event.skywatch.WorldZeroSkyWatchEvent;
import ru.nekostul.worldzero.event.stalker.WorldZeroStalkerEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroMinePresenceEvent {
    private static final String WORLDZERO_SAVE_ID = "worldzero_mine_presence_event";
    private static final long WORLDZERO_UNDERGROUND_REQUIRED_TICKS = 5L * 60L * 20L;
    private static final int WORLDZERO_MIN_SOUND_SESSIONS = 2;
    private static final int WORLDZERO_MAX_SOUND_SESSIONS = 3;
    private static final int WORLDZERO_SOUND_TRIGGER_DELAY_MIN_TICKS = 25 * 20;
    private static final int WORLDZERO_SOUND_TRIGGER_DELAY_MAX_TICKS = 55 * 20;
    private static final int WORLDZERO_SOUND_SESSION_MIN_CUES = 4;
    private static final int WORLDZERO_SOUND_SESSION_MAX_CUES = 7;
    private static final int WORLDZERO_SOUND_CUE_DELAY_MIN_TICKS = 18;
    private static final int WORLDZERO_SOUND_CUE_DELAY_MAX_TICKS = 42;
    private static final int WORLDZERO_FOOTSTEP_BURST_MIN_SOUNDS = 2;
    private static final int WORLDZERO_FOOTSTEP_BURST_MAX_SOUNDS = 3;
    private static final int WORLDZERO_FOOTSTEP_BURST_DELAY_MIN_TICKS = 4;
    private static final int WORLDZERO_FOOTSTEP_BURST_DELAY_MAX_TICKS = 6;
    private static final int WORLDZERO_MINING_BURST_MIN_SOUNDS = 3;
    private static final int WORLDZERO_MINING_BURST_MAX_SOUNDS = 5;
    private static final int WORLDZERO_MINING_BURST_DELAY_MIN_TICKS = 3;
    private static final int WORLDZERO_MINING_BURST_DELAY_MAX_TICKS = 5;
    private static final int WORLDZERO_SOUND_STOP_STATIONARY_TICKS = 8;
    private static final double WORLDZERO_SOUND_STOP_MOVEMENT_DELTA_SQR = 0.03D * 0.03D;
    private static final double WORLDZERO_SOUND_MIN_DISTANCE = 4.0D;
    private static final double WORLDZERO_SOUND_MAX_DISTANCE = 8.5D;
    private static final double WORLDZERO_SOUND_MAX_VIEW_DOT = 0.35D;
    private static final double WORLDZERO_SOUND_INVESTIGATE_VIEW_DOT = 0.72D;
    private static final double WORLDZERO_SOUND_INVESTIGATE_CLOSE_DISTANCE_SQR = 3.25D * 3.25D;
    private static final double WORLDZERO_SOUND_INVESTIGATE_APPROACH_DELTA_SQR = 2.0D * 2.0D;
    private static final int WORLDZERO_MINE_SCAN_HORIZONTAL_RADIUS = 8;
    private static final int WORLDZERO_MINE_SCAN_VERTICAL_RADIUS = 3;
    private static final int WORLDZERO_MIN_SURFACE_DEPTH = 5;
    private static final int WORLDZERO_TORCH_FADE_MIN_DELAY_TICKS = 5 * 20;
    private static final int WORLDZERO_TORCH_FADE_MAX_DELAY_TICKS = 11 * 20;
    private static final double WORLDZERO_TORCH_FADE_DISTANCE_SQR = 14.0D * 14.0D;
    private static final int WORLDZERO_MIN_TORCH_CLUSTER_SIZE = 3;
    private static final int WORLDZERO_MAX_TRACKED_TORCHES = 24;
    private static final int WORLDZERO_TORCH_CLUSTER_RADIUS_BLOCKS = 18;
    private static final Map<MinecraftServer, SessionState> WORLDZERO_SESSION_STATES = new WeakHashMap<>();

    private WorldZeroMinePresenceEvent() {
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

        ServerLevel level = player.serverLevel();
        SessionState sessionState = WORLDZERO_SESSION_STATES.computeIfAbsent(server, ignored -> new SessionState());
        PlayerState playerState = sessionState.worldzero$playerStates.computeIfAbsent(
                player.getUUID(),
                ignored -> new PlayerState()
        );
        worldzero$loadPersistentState(level, player.getUUID(), playerState);
        worldzero$pruneTorches(level, playerState);

        if (!WorldZeroStoryTime.worldzero$countsTowardStoryTime(player)) {
            worldzero$stopActiveSession(playerState);
            return;
        }

        boolean underground = worldzero$isUnderground(level, player);
        boolean hasTool = worldzero$hasStonePickaxeOrBetter(player);
        if (!underground) {
            worldzero$handleMineExit(playerState);
            return;
        }

        if (!playerState.worldzero$inMineTrip) {
            playerState.worldzero$inMineTrip = true;
            playerState.worldzero$currentTripId++;
        }

        if (!hasTool) {
            playerState.worldzero$nextSoundAttemptTick = -1L;
            playerState.worldzero$torchFadeCheckTick = -1L;
            worldzero$stopActiveSession(playerState);
            return;
        }

        playerState.worldzero$undergroundTripTicks++;
        worldzero$tickTorchFade(level, player, playerState);

        if (playerState.worldzero$activeSoundSession != null) {
            worldzero$tickActiveSoundSession(level, player, playerState);
            return;
        }

        if (playerState.worldzero$tripSoundFinished
                || playerState.worldzero$completedSoundSessions >= playerState.worldzero$maxSoundSessions
                || playerState.worldzero$undergroundTripTicks < WORLDZERO_UNDERGROUND_REQUIRED_TICKS
                || !WorldZeroStoryTime.worldzero$canReceiveStoryEvent(player)
                || worldzero$hasConflictingEvent(level)) {
            return;
        }

        long gameTime = level.getGameTime();
        if (playerState.worldzero$nextSoundAttemptTick < 0L) {
            playerState.worldzero$nextSoundAttemptTick = gameTime + worldzero$randomBetween(
                    level,
                    WORLDZERO_SOUND_TRIGGER_DELAY_MIN_TICKS,
                    WORLDZERO_SOUND_TRIGGER_DELAY_MAX_TICKS
            );
            return;
        }

        if (gameTime < playerState.worldzero$nextSoundAttemptTick) {
            return;
        }

        if (worldzero$startSoundSession(level, player, playerState)) {
            playerState.worldzero$tripSoundFinished = true;
            playerState.worldzero$completedSoundSessions++;
            worldzero$savePersistentState(level, player.getUUID(), playerState);
            return;
        }

        playerState.worldzero$nextSoundAttemptTick = gameTime + worldzero$randomBetween(level, 10 * 20, 25 * 20);
    }

    @SubscribeEvent
    public static void worldzero$onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!(event.getLevel() instanceof ServerLevel level) || level.isClientSide() || level.dimension() != Level.OVERWORLD) {
            return;
        }

        BlockState placedState = event.getPlacedBlock();
        if (!worldzero$isTrackedTorch(placedState)) {
            return;
        }

        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }

        SessionState sessionState = WORLDZERO_SESSION_STATES.computeIfAbsent(server, ignored -> new SessionState());
        PlayerState playerState = sessionState.worldzero$playerStates.computeIfAbsent(
                player.getUUID(),
                ignored -> new PlayerState()
        );
        worldzero$loadPersistentState(level, player.getUUID(), playerState);

        if (!playerState.worldzero$inMineTrip
                || playerState.worldzero$undergroundTripTicks < WORLDZERO_UNDERGROUND_REQUIRED_TICKS
                || !worldzero$isUnderground(level, player)
                || !worldzero$hasStonePickaxeOrBetter(player)) {
            return;
        }

        playerState.worldzero$torchRecords.removeIf(record -> record.worldzero$pos.equals(event.getPos()));
        playerState.worldzero$torchRecords.add(new TorchRecord(
                event.getPos().immutable(),
                playerState.worldzero$currentTripId,
                level.getGameTime()
        ));
        playerState.worldzero$torchFadeCheckTick = -1L;
        while (playerState.worldzero$torchRecords.size() > WORLDZERO_MAX_TRACKED_TORCHES) {
            playerState.worldzero$torchRecords.remove(0);
        }
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_SESSION_STATES.remove(event.getServer());
    }

    public static boolean worldzero$triggerNow(ServerPlayer player) {
        if (worldzero$getDebugTriggerBlocker(player) != null) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        MinecraftServer server = level.getServer();
        if (server == null) {
            return false;
        }

        SessionState sessionState = WORLDZERO_SESSION_STATES.computeIfAbsent(server, ignored -> new SessionState());
        PlayerState playerState = sessionState.worldzero$playerStates.computeIfAbsent(
                player.getUUID(),
                ignored -> new PlayerState()
        );
        worldzero$loadPersistentState(level, player.getUUID(), playerState);
        worldzero$pruneTorches(level, playerState);

        playerState.worldzero$inMineTrip = true;
        playerState.worldzero$currentTripId++;
        playerState.worldzero$undergroundTripTicks = WORLDZERO_UNDERGROUND_REQUIRED_TICKS;
        playerState.worldzero$tripSoundFinished = false;
        playerState.worldzero$nextSoundAttemptTick = -1L;
        playerState.worldzero$torchFadeCheckTick = -1L;
        playerState.worldzero$activeSoundSession = null;
        return worldzero$startSoundSession(level, player, playerState);
    }

    public static boolean worldzero$stopNow(MinecraftServer server) {
        if (server == null) {
            return false;
        }

        SessionState sessionState = WORLDZERO_SESSION_STATES.get(server);
        if (sessionState == null) {
            return false;
        }

        boolean changed = false;
        for (PlayerState playerState : sessionState.worldzero$playerStates.values()) {
            if (playerState.worldzero$activeSoundSession != null
                    || playerState.worldzero$inMineTrip
                    || playerState.worldzero$nextSoundAttemptTick >= 0L
                    || playerState.worldzero$torchFadeCheckTick >= 0L) {
                changed = true;
            }
            playerState.worldzero$activeSoundSession = null;
            playerState.worldzero$inMineTrip = false;
            playerState.worldzero$undergroundTripTicks = 0L;
            playerState.worldzero$tripSoundFinished = false;
            playerState.worldzero$nextSoundAttemptTick = -1L;
            playerState.worldzero$torchFadeCheckTick = -1L;
        }
        return changed;
    }

    @Nullable
    public static String worldzero$getDebugTriggerBlocker(@Nullable ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return "player is not alive";
        }

        if (player.serverLevel().dimension() != Level.OVERWORLD) {
            return "player is not in overworld";
        }

        ServerLevel level = player.serverLevel();
        if (!worldzero$isUnderground(level, player)) {
            return "player is not underground";
        }

        if (!worldzero$hasStonePickaxeOrBetter(player)) {
            return "player has no stone pickaxe or better";
        }

        if (worldzero$hasConflictingEvent(level)) {
            return "another event is active";
        }

        HiddenSoundSource soundSource = worldzero$resolveHiddenSoundSource(level, player, false);
        if (soundSource == null) {
            return "no hidden sound source found nearby";
        }

        return null;
    }

    private static void worldzero$tickActiveSoundSession(
            ServerLevel level,
            ServerPlayer player,
            PlayerState playerState
    ) {
        ActiveSoundSession session = playerState.worldzero$activeSoundSession;
        if (session == null) {
            return;
        }

        if (!worldzero$isUnderground(level, player) || !worldzero$hasStonePickaxeOrBetter(player)) {
            playerState.worldzero$activeSoundSession = null;
            return;
        }

        if (worldzero$shouldStopBecausePlayerStopped(player, session)) {
            playerState.worldzero$activeSoundSession = null;
            return;
        }

        if (worldzero$isInvestigatingSource(player, session)) {
            playerState.worldzero$activeSoundSession = null;
            return;
        }

        if (level.getGameTime() < session.worldzero$nextCueTick) {
            return;
        }

        if (session.worldzero$remainingCues <= 0) {
            playerState.worldzero$activeSoundSession = null;
            return;
        }

        if (session.worldzero$burstRemainingSounds > 0) {
            if (!worldzero$playBurstSound(level, player, session)) {
                playerState.worldzero$activeSoundSession = null;
            }
            return;
        }

        SoundCueType cueType = worldzero$pickCueType(level, session);
        HiddenSoundSource soundSource = worldzero$resolveHiddenSoundSource(level, player, cueType == SoundCueType.MINING);
        if (soundSource == null) {
            playerState.worldzero$activeSoundSession = null;
            return;
        }

        MiningSoundTarget miningTarget = null;
        if (cueType == SoundCueType.MINING) {
            miningTarget = worldzero$resolveMiningSoundTarget(level, soundSource.worldzero$sourcePos());
            if (miningTarget == null) {
                cueType = SoundCueType.FOOTSTEP;
            }
        }

        worldzero$startBurst(level, player, session, cueType, soundSource, miningTarget);
        if (!worldzero$playBurstSound(level, player, session)) {
            playerState.worldzero$activeSoundSession = null;
            return;
        }
    }

    private static void worldzero$startBurst(
            ServerLevel level,
            ServerPlayer player,
            ActiveSoundSession session,
            SoundCueType cueType,
            HiddenSoundSource soundSource,
            @Nullable MiningSoundTarget miningTarget
    ) {
        session.worldzero$currentCueType = cueType;
        session.worldzero$currentSourcePos = soundSource.worldzero$sourcePos();
        session.worldzero$currentFloorPos = soundSource.worldzero$floorPos();
        session.worldzero$currentSourceX = soundSource.worldzero$source().x;
        session.worldzero$currentSourceY = soundSource.worldzero$source().y;
        session.worldzero$currentSourceZ = soundSource.worldzero$source().z;
        session.worldzero$currentMiningTarget = miningTarget;
        session.worldzero$burstRemainingSounds = cueType == SoundCueType.FOOTSTEP
                ? Mth.nextInt(level.random, WORLDZERO_FOOTSTEP_BURST_MIN_SOUNDS, WORLDZERO_FOOTSTEP_BURST_MAX_SOUNDS)
                : Mth.nextInt(level.random, WORLDZERO_MINING_BURST_MIN_SOUNDS, WORLDZERO_MINING_BURST_MAX_SOUNDS);
        session.worldzero$sourceX = session.worldzero$currentSourceX;
        session.worldzero$sourceY = session.worldzero$currentSourceY;
        session.worldzero$sourceZ = session.worldzero$currentSourceZ;
        session.worldzero$distanceAtCueSqr = player.distanceToSqr(
                session.worldzero$sourceX,
                session.worldzero$sourceY,
                session.worldzero$sourceZ
        );
        session.worldzero$yawAtCue = player.getYRot();
        session.worldzero$pitchAtCue = player.getXRot();
        session.worldzero$nextCueTick = level.getGameTime();
    }

    private static boolean worldzero$playBurstSound(
            ServerLevel level,
            ServerPlayer player,
            ActiveSoundSession session
    ) {
        if (session.worldzero$burstRemainingSounds <= 0) {
            return false;
        }

        if (session.worldzero$currentCueType == SoundCueType.MINING) {
            if (!worldzero$playMiningBurstSound(level, player, session)) {
                return false;
            }
        } else {
            worldzero$playFootstepBurstSound(level, player, session);
        }

        session.worldzero$burstRemainingSounds--;
        if (session.worldzero$burstRemainingSounds > 0) {
            session.worldzero$nextCueTick = level.getGameTime() + worldzero$randomBetween(
                    level,
                    session.worldzero$currentCueType == SoundCueType.FOOTSTEP
                            ? WORLDZERO_FOOTSTEP_BURST_DELAY_MIN_TICKS
                            : WORLDZERO_MINING_BURST_DELAY_MIN_TICKS,
                    session.worldzero$currentCueType == SoundCueType.FOOTSTEP
                            ? WORLDZERO_FOOTSTEP_BURST_DELAY_MAX_TICKS
                            : WORLDZERO_MINING_BURST_DELAY_MAX_TICKS
            );
            return true;
        }

        session.worldzero$lastCueType = session.worldzero$currentCueType;
        session.worldzero$remainingCues--;
        session.worldzero$currentMiningTarget = null;
        session.worldzero$currentSourcePos = null;
        session.worldzero$currentFloorPos = null;
        session.worldzero$nextCueTick = level.getGameTime() + worldzero$randomBetween(
                level,
                WORLDZERO_SOUND_CUE_DELAY_MIN_TICKS,
                WORLDZERO_SOUND_CUE_DELAY_MAX_TICKS
        );
        return true;
    }

    private static void worldzero$playFootstepBurstSound(
            ServerLevel level,
            ServerPlayer player,
            ActiveSoundSession session
    ) {
        if (session.worldzero$currentFloorPos == null) {
            return;
        }

        BlockPos supportPos = session.worldzero$currentFloorPos;
        BlockState supportState = level.getBlockState(supportPos);
        SoundType soundType = supportState.getSoundType(level, supportPos, player);
        worldzero$playCompensatedSound(
                level,
                player,
                new Vec3(session.worldzero$currentSourceX, session.worldzero$currentSourceY, session.worldzero$currentSourceZ),
                soundType.getStepSound(),
                0.94F + level.random.nextFloat() * 0.12F
        );
    }

    private static boolean worldzero$playMiningBurstSound(
            ServerLevel level,
            ServerPlayer player,
            ActiveSoundSession session
    ) {
        MiningSoundTarget target = session.worldzero$currentMiningTarget;
        if (target == null) {
            return false;
        }

        SoundType soundType = target.worldzero$state().getSoundType(level, target.worldzero$pos(), player);
        boolean lastSoundInBurst = session.worldzero$burstRemainingSounds == 1;
        SoundEvent soundEvent = lastSoundInBurst && level.random.nextDouble() < 0.38D
                ? soundType.getBreakSound()
                : soundType.getHitSound();
        worldzero$playCompensatedSound(
                level,
                player,
                new Vec3(session.worldzero$currentSourceX, session.worldzero$currentSourceY, session.worldzero$currentSourceZ),
                soundEvent,
                0.91F + level.random.nextFloat() * 0.14F
        );
        return true;
    }

    private static boolean worldzero$shouldStopBecausePlayerStopped(ServerPlayer player, ActiveSoundSession session) {
        double deltaX = player.getX() - session.worldzero$lastPlayerX;
        double deltaZ = player.getZ() - session.worldzero$lastPlayerZ;
        double horizontalDeltaSqr = deltaX * deltaX + deltaZ * deltaZ;
        session.worldzero$lastPlayerX = player.getX();
        session.worldzero$lastPlayerY = player.getY();
        session.worldzero$lastPlayerZ = player.getZ();
        if (horizontalDeltaSqr <= WORLDZERO_SOUND_STOP_MOVEMENT_DELTA_SQR) {
            session.worldzero$stationaryTicks++;
        } else {
            session.worldzero$stationaryTicks = 0;
        }
        return session.worldzero$stationaryTicks >= WORLDZERO_SOUND_STOP_STATIONARY_TICKS;
    }

    private static boolean worldzero$startSoundSession(
            ServerLevel level,
            ServerPlayer player,
            PlayerState playerState
    ) {
        HiddenSoundSource soundSource = worldzero$resolveHiddenSoundSource(level, player, false);
        if (soundSource == null) {
            return false;
        }

        ActiveSoundSession session = new ActiveSoundSession();
        session.worldzero$remainingCues = Mth.nextInt(
                level.random,
                WORLDZERO_SOUND_SESSION_MIN_CUES,
                WORLDZERO_SOUND_SESSION_MAX_CUES
        );
        session.worldzero$nextCueTick = level.getGameTime();
        session.worldzero$lastCueType = SoundCueType.MINING;
        session.worldzero$sourceX = soundSource.worldzero$source().x;
        session.worldzero$sourceY = soundSource.worldzero$source().y;
        session.worldzero$sourceZ = soundSource.worldzero$source().z;
        session.worldzero$distanceAtCueSqr = player.distanceToSqr(
                session.worldzero$sourceX,
                session.worldzero$sourceY,
                session.worldzero$sourceZ
        );
        session.worldzero$yawAtCue = player.getYRot();
        session.worldzero$pitchAtCue = player.getXRot();
        session.worldzero$lastPlayerX = player.getX();
        session.worldzero$lastPlayerY = player.getY();
        session.worldzero$lastPlayerZ = player.getZ();
        session.worldzero$stationaryTicks = 0;
        playerState.worldzero$activeSoundSession = session;
        playerState.worldzero$nextSoundAttemptTick = -1L;
        return true;
    }

    private static SoundCueType worldzero$pickCueType(ServerLevel level, ActiveSoundSession session) {
        if (session.worldzero$lastCueType == SoundCueType.FOOTSTEP) {
            return level.random.nextDouble() < 0.62D ? SoundCueType.MINING : SoundCueType.FOOTSTEP;
        }
        return level.random.nextDouble() < 0.58D ? SoundCueType.FOOTSTEP : SoundCueType.MINING;
    }

    private static void worldzero$playCompensatedSound(
            ServerLevel level,
            ServerPlayer player,
            Vec3 source,
            SoundEvent soundEvent,
            float pitch
    ) {
        double distance = Math.sqrt(player.distanceToSqr(source.x, source.y, source.z));
        float volume = (float) Mth.clamp(0.88D + Math.max(0.0D, distance - 4.0D) * 0.09D, 0.88D, 1.75D);
        level.playSound(
                null,
                source.x,
                source.y,
                source.z,
                soundEvent,
                SoundSource.BLOCKS,
                volume,
                pitch
        );
    }

    @Nullable
    private static HiddenSoundSource worldzero$resolveHiddenSoundSource(
            ServerLevel level,
            ServerPlayer player,
            boolean requireMiningSurface
    ) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();
        boolean closedMine = worldzero$isClosedMineArea(level, player.blockPosition());
        HiddenSoundSource best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        int playerY = player.blockPosition().getY();
        for (int dx = -WORLDZERO_MINE_SCAN_HORIZONTAL_RADIUS; dx <= WORLDZERO_MINE_SCAN_HORIZONTAL_RADIUS; dx++) {
            for (int dz = -WORLDZERO_MINE_SCAN_HORIZONTAL_RADIUS; dz <= WORLDZERO_MINE_SCAN_HORIZONTAL_RADIUS; dz++) {
                for (int dy = -WORLDZERO_MINE_SCAN_VERTICAL_RADIUS; dy <= WORLDZERO_MINE_SCAN_VERTICAL_RADIUS; dy++) {
                    BlockPos airPos = new BlockPos(
                            Mth.floor(player.getX()) + dx,
                            playerY + dy,
                            Mth.floor(player.getZ()) + dz
                    );
                    if (!worldzero$isWalkableHiddenSpot(level, airPos)) {
                        continue;
                    }

                    Vec3 source = new Vec3(airPos.getX() + 0.5D, airPos.getY(), airPos.getZ() + 0.5D);
                    double distance = Math.sqrt(source.distanceToSqr(player.position()));
                    if (distance < WORLDZERO_SOUND_MIN_DISTANCE || distance > WORLDZERO_SOUND_MAX_DISTANCE) {
                        continue;
                    }

                    Vec3 toSource = source.subtract(eyePos);
                    if (toSource.lengthSqr() < 1.0E-6D) {
                        continue;
                    }

                    double facingDot = look.dot(toSource.normalize());
                    if (facingDot > WORLDZERO_SOUND_MAX_VIEW_DOT) {
                        continue;
                    }

                    BlockHitResult hitResult = level.clip(new ClipContext(
                            eyePos,
                            source,
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            player
                    ));
                    if (hitResult.getType() != HitResult.Type.BLOCK) {
                        continue;
                    }

                    double hitDistanceSqr = hitResult.getLocation().distanceToSqr(eyePos);
                    double sourceDistanceSqr = source.distanceToSqr(eyePos);
                    if (hitDistanceSqr + 0.09D >= sourceDistanceSqr) {
                        continue;
                    }

                    if (requireMiningSurface && worldzero$resolveMiningSoundTarget(level, airPos) == null) {
                        continue;
                    }

                    double occlusionDepth = Math.sqrt(Math.max(0.0D, sourceDistanceSqr - hitDistanceSqr));
                    double sideBias = 1.0D - Math.abs(facingDot);
                    double closedBias = closedMine ? Math.min(occlusionDepth, 3.5D) : sideBias * 2.0D;
                    double score = closedBias + (WORLDZERO_SOUND_MAX_DISTANCE - distance) * 0.18D;
                    if (best != null && score <= bestScore) {
                        continue;
                    }

                    bestScore = score;
                    best = new HiddenSoundSource(airPos.immutable(), airPos.below().immutable(), source);
                }
            }
        }

        return best;
    }

    private static boolean worldzero$isWalkableHiddenSpot(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockState aboveState = level.getBlockState(pos.above());
        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);
        return state.isAir()
                && aboveState.isAir()
                && state.getFluidState().isEmpty()
                && aboveState.getFluidState().isEmpty()
                && belowState.isFaceSturdy(level, belowPos, Direction.UP)
                && belowState.getFluidState().isEmpty();
    }

    private static boolean worldzero$isInvestigatingSource(ServerPlayer player, ActiveSoundSession session) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 toSource = new Vec3(
                session.worldzero$sourceX - eyePos.x,
                session.worldzero$sourceY - eyePos.y,
                session.worldzero$sourceZ - eyePos.z
        );
        if (toSource.lengthSqr() < 1.0E-6D) {
            return false;
        }

        double currentDistanceSqr = player.distanceToSqr(
                session.worldzero$sourceX,
                session.worldzero$sourceY,
                session.worldzero$sourceZ
        );
        if (currentDistanceSqr <= WORLDZERO_SOUND_INVESTIGATE_CLOSE_DISTANCE_SQR) {
            return true;
        }

        double facingDot = player.getViewVector(1.0F).normalize().dot(toSource.normalize());
        boolean turnedToward = facingDot >= WORLDZERO_SOUND_INVESTIGATE_VIEW_DOT
                && (Math.abs(Mth.wrapDegrees(player.getYRot() - session.worldzero$yawAtCue)) >= 35.0F
                || Math.abs(player.getXRot() - session.worldzero$pitchAtCue) >= 12.0F);
        boolean movedCloser = currentDistanceSqr + WORLDZERO_SOUND_INVESTIGATE_APPROACH_DELTA_SQR < session.worldzero$distanceAtCueSqr;
        return turnedToward || movedCloser;
    }

    private static void worldzero$tickTorchFade(
            ServerLevel level,
            ServerPlayer player,
            PlayerState playerState
    ) {
        if (playerState.worldzero$torchFadeTripId == playerState.worldzero$currentTripId) {
            return;
        }

        List<TorchRecord> tripTorches = new ArrayList<>();
        for (TorchRecord record : playerState.worldzero$torchRecords) {
            if (record.worldzero$tripId != playerState.worldzero$currentTripId || record.worldzero$removed) {
                continue;
            }
            if (worldzero$isTrackedTorch(level.getBlockState(record.worldzero$pos))) {
                tripTorches.add(record);
            }
        }

        if (tripTorches.size() < WORLDZERO_MIN_TORCH_CLUSTER_SIZE) {
            playerState.worldzero$torchFadeCheckTick = -1L;
            return;
        }

        BlockPos center = worldzero$resolveTorchClusterCenter(tripTorches);
        if (center == null) {
            playerState.worldzero$torchFadeCheckTick = -1L;
            return;
        }

        if (player.blockPosition().distSqr(center) <= WORLDZERO_TORCH_FADE_DISTANCE_SQR) {
            playerState.worldzero$torchFadeCheckTick = -1L;
            return;
        }

        long gameTime = level.getGameTime();
        if (playerState.worldzero$torchFadeCheckTick < 0L) {
            playerState.worldzero$torchFadeCheckTick = gameTime + worldzero$randomBetween(
                    level,
                    WORLDZERO_TORCH_FADE_MIN_DELAY_TICKS,
                    WORLDZERO_TORCH_FADE_MAX_DELAY_TICKS
            );
            return;
        }

        if (gameTime < playerState.worldzero$torchFadeCheckTick) {
            return;
        }

        int keepCount = Mth.nextInt(level.random, 1, 2);
        tripTorches.sort(Comparator.comparingLong(record -> record.worldzero$placedAtTick));
        for (int index = 0; index < tripTorches.size() - keepCount; index++) {
            TorchRecord record = tripTorches.get(index);
            BlockState state = level.getBlockState(record.worldzero$pos);
            if (!worldzero$isTrackedTorch(state)) {
                record.worldzero$removed = true;
                continue;
            }
            level.setBlock(record.worldzero$pos, Blocks.AIR.defaultBlockState(), 18);
            level.sendBlockUpdated(record.worldzero$pos, state, Blocks.AIR.defaultBlockState(), 3);
            record.worldzero$removed = true;
        }

        playerState.worldzero$torchFadeTripId = playerState.worldzero$currentTripId;
        playerState.worldzero$torchFadeCheckTick = -1L;
    }

    @Nullable
    private static BlockPos worldzero$resolveTorchClusterCenter(List<TorchRecord> records) {
        if (records.isEmpty()) {
            return null;
        }

        long sumX = 0L;
        long sumY = 0L;
        long sumZ = 0L;
        for (TorchRecord record : records) {
            sumX += record.worldzero$pos.getX();
            sumY += record.worldzero$pos.getY();
            sumZ += record.worldzero$pos.getZ();
        }

        BlockPos center = new BlockPos(
                (int) Math.round((double) sumX / (double) records.size()),
                (int) Math.round((double) sumY / (double) records.size()),
                (int) Math.round((double) sumZ / (double) records.size())
        );

        int radiusSqr = WORLDZERO_TORCH_CLUSTER_RADIUS_BLOCKS * WORLDZERO_TORCH_CLUSTER_RADIUS_BLOCKS;
        for (TorchRecord record : records) {
            if (record.worldzero$pos.distSqr(center) > radiusSqr) {
                return null;
            }
        }
        return center;
    }

    private static void worldzero$pruneTorches(ServerLevel level, PlayerState playerState) {
        Iterator<TorchRecord> iterator = playerState.worldzero$torchRecords.iterator();
        while (iterator.hasNext()) {
            TorchRecord record = iterator.next();
            if (record.worldzero$removed || !worldzero$isTrackedTorch(level.getBlockState(record.worldzero$pos))) {
                iterator.remove();
            }
        }
    }

    private static void worldzero$handleMineExit(PlayerState playerState) {
        playerState.worldzero$inMineTrip = false;
        playerState.worldzero$undergroundTripTicks = 0L;
        playerState.worldzero$tripSoundFinished = false;
        playerState.worldzero$nextSoundAttemptTick = -1L;
        playerState.worldzero$torchFadeCheckTick = -1L;
        playerState.worldzero$activeSoundSession = null;
    }

    private static void worldzero$stopActiveSession(PlayerState playerState) {
        if (playerState != null) {
            playerState.worldzero$activeSoundSession = null;
        }
    }

    @Nullable
    private static MiningSoundTarget worldzero$resolveMiningSoundTarget(ServerLevel level, BlockPos center) {
        List<MiningSoundTarget> candidates = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!worldzero$isMineSoundBlock(state)) {
                        continue;
                    }

                    int weight = worldzero$mineSoundWeight(state);
                    for (int repeat = 0; repeat < weight; repeat++) {
                        candidates.add(new MiningSoundTarget(pos.immutable(), state));
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.get(level.random.nextInt(candidates.size()));
    }

    private static boolean worldzero$isMineSoundBlock(BlockState state) {
        if (state.is(Blocks.GRAVEL) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.COBBLED_DEEPSLATE)) {
            return true;
        }

        if (state.is(BlockTags.BASE_STONE_OVERWORLD)) {
            return true;
        }

        String blockPath = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return blockPath.contains("deepslate") || blockPath.equals("stone");
    }

    private static int worldzero$mineSoundWeight(BlockState state) {
        if (state.is(Blocks.GRAVEL)) {
            return 3;
        }
        if (state.is(Blocks.DEEPSLATE) || state.is(Blocks.COBBLED_DEEPSLATE)) {
            return 3;
        }
        return 2;
    }

    private static boolean worldzero$hasStonePickaxeOrBetter(ServerPlayer player) {
        int minimumLevel = Tiers.STONE.getLevel();
        for (ItemStack stack : player.getInventory().items) {
            if (!(stack.getItem() instanceof PickaxeItem pickaxeItem)) {
                continue;
            }

            Tier tier = pickaxeItem.getTier();
            if (tier != null && tier.getLevel() >= minimumLevel) {
                return true;
            }
        }
        return false;
    }

    private static boolean worldzero$isTrackedTorch(BlockState state) {
        return state.is(Blocks.TORCH)
                || state.is(Blocks.WALL_TORCH)
                || state.is(Blocks.SOUL_TORCH)
                || state.is(Blocks.SOUL_WALL_TORCH);
    }

    private static boolean worldzero$isUnderground(ServerLevel level, ServerPlayer player) {
        if (player == null || WorldZeroHouseDetector.worldzero$findContainingHouse(player) != null) {
            return false;
        }

        BlockPos feetPos = player.blockPosition();
        BlockPos eyePos = BlockPos.containing(player.getEyePosition());
        if (level.canSeeSky(feetPos) || level.canSeeSky(eyePos)) {
            return false;
        }

        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feetPos.getX(), feetPos.getZ());
        return surfaceY - feetPos.getY() >= WORLDZERO_MIN_SURFACE_DEPTH;
    }

    private static boolean worldzero$isClosedMineArea(ServerLevel level, BlockPos center) {
        int openColumns = 0;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (!worldzero$isWalkableHiddenSpot(level, center.offset(dx, dy, dz))) {
                        continue;
                    }
                    openColumns++;
                    if (openColumns > 16) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean worldzero$hasConflictingEvent(ServerLevel level) {
        return level == null
                || level.isClientSide()
                || WorldZeroAmbientSoundEvent.worldzero$isMajorEventStartBlocked(level)
                || WorldZeroFreezeEvent.worldzero$isFreezeActive(level.getServer())
                || WorldZeroFallEvent.worldzero$isFallActive(level.getServer())
                || WorldZeroFootstepsEvent.worldzero$isFootstepsActive(level.getServer())
                || WorldZeroHouseEvent.worldzero$isHouseActive(level.getServer())
                || WorldZeroSkyWatchEvent.worldzero$isActive(level.getServer())
                || WorldZeroParalysisEvent.worldzero$isParalysisActive(level.getServer())
                || WorldZeroHorrorFinale.worldzero$isActive(level.getServer())
                || WorldZeroStalkerEvent.worldzero$isActive(level.getServer());
    }

    private static int worldzero$randomBetween(ServerLevel level, int minValue, int maxValue) {
        if (minValue >= maxValue) {
            return minValue;
        }
        return Mth.nextInt(level.random, minValue, maxValue);
    }

    private static void worldzero$loadPersistentState(
            ServerLevel level,
            UUID playerId,
            PlayerState playerState
    ) {
        if (playerState.worldzero$persistentLoaded) {
            return;
        }

        PersistentPlayerState persistentPlayerState = worldzero$getSaveData(level).worldzero$playerStates.get(playerId);
        if (persistentPlayerState != null) {
            playerState.worldzero$completedSoundSessions = persistentPlayerState.worldzero$completedSoundSessions;
            playerState.worldzero$maxSoundSessions = persistentPlayerState.worldzero$maxSoundSessions;
        }

        if (playerState.worldzero$maxSoundSessions <= 0) {
            playerState.worldzero$maxSoundSessions = Mth.nextInt(
                    level.random,
                    WORLDZERO_MIN_SOUND_SESSIONS,
                    WORLDZERO_MAX_SOUND_SESSIONS
            );
        }

        playerState.worldzero$persistentLoaded = true;
    }

    private static void worldzero$savePersistentState(
            ServerLevel level,
            UUID playerId,
            PlayerState playerState
    ) {
        MinePresenceSaveData saveData = worldzero$getSaveData(level);
        PersistentPlayerState persistentPlayerState = saveData.worldzero$playerStates.computeIfAbsent(
                playerId,
                ignored -> new PersistentPlayerState()
        );
        persistentPlayerState.worldzero$completedSoundSessions = playerState.worldzero$completedSoundSessions;
        persistentPlayerState.worldzero$maxSoundSessions = playerState.worldzero$maxSoundSessions;
        saveData.setDirty();
    }

    private static MinePresenceSaveData worldzero$getSaveData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                MinePresenceSaveData::worldzero$load,
                MinePresenceSaveData::new,
                WORLDZERO_SAVE_ID
        );
    }

    private enum SoundCueType {
        FOOTSTEP,
        MINING
    }

    private static final class SessionState {
        private final Map<UUID, PlayerState> worldzero$playerStates = new HashMap<>();
    }

    private static final class PlayerState {
        private boolean worldzero$persistentLoaded;
        private boolean worldzero$inMineTrip;
        private boolean worldzero$tripSoundFinished;
        private int worldzero$completedSoundSessions;
        private int worldzero$maxSoundSessions;
        private long worldzero$currentTripId;
        private long worldzero$undergroundTripTicks;
        private long worldzero$nextSoundAttemptTick = -1L;
        private long worldzero$torchFadeCheckTick = -1L;
        private long worldzero$torchFadeTripId = -1L;
        @Nullable
        private ActiveSoundSession worldzero$activeSoundSession;
        private final List<TorchRecord> worldzero$torchRecords = new ArrayList<>();
    }

    private static final class ActiveSoundSession {
        private int worldzero$remainingCues;
        private int worldzero$burstRemainingSounds;
        private long worldzero$nextCueTick;
        private double worldzero$sourceX;
        private double worldzero$sourceY;
        private double worldzero$sourceZ;
        private double worldzero$distanceAtCueSqr;
        private float worldzero$yawAtCue;
        private float worldzero$pitchAtCue;
        private double worldzero$currentSourceX;
        private double worldzero$currentSourceY;
        private double worldzero$currentSourceZ;
        private double worldzero$lastPlayerX;
        private double worldzero$lastPlayerY;
        private double worldzero$lastPlayerZ;
        private int worldzero$stationaryTicks;
        @Nullable
        private BlockPos worldzero$currentSourcePos;
        @Nullable
        private BlockPos worldzero$currentFloorPos;
        @Nullable
        private MiningSoundTarget worldzero$currentMiningTarget;
        private SoundCueType worldzero$currentCueType = SoundCueType.MINING;
        private SoundCueType worldzero$lastCueType = SoundCueType.MINING;
    }

    private static final class TorchRecord {
        private final BlockPos worldzero$pos;
        private final long worldzero$tripId;
        private final long worldzero$placedAtTick;
        private boolean worldzero$removed;

        private TorchRecord(BlockPos pos, long tripId, long placedAtTick) {
            this.worldzero$pos = pos;
            this.worldzero$tripId = tripId;
            this.worldzero$placedAtTick = placedAtTick;
        }
    }

    private static final class HiddenSoundSource {
        private final BlockPos worldzero$sourcePos;
        private final BlockPos worldzero$floorPos;
        private final Vec3 worldzero$source;

        private HiddenSoundSource(BlockPos sourcePos, BlockPos floorPos, Vec3 source) {
            this.worldzero$sourcePos = sourcePos;
            this.worldzero$floorPos = floorPos;
            this.worldzero$source = source;
        }

        private BlockPos worldzero$sourcePos() {
            return this.worldzero$sourcePos;
        }

        private BlockPos worldzero$floorPos() {
            return this.worldzero$floorPos;
        }

        private Vec3 worldzero$source() {
            return this.worldzero$source;
        }
    }

    private static final class MiningSoundTarget {
        private final BlockPos worldzero$pos;
        private final BlockState worldzero$state;

        private MiningSoundTarget(BlockPos pos, BlockState state) {
            this.worldzero$pos = pos;
            this.worldzero$state = state;
        }

        private BlockPos worldzero$pos() {
            return this.worldzero$pos;
        }

        private BlockState worldzero$state() {
            return this.worldzero$state;
        }
    }

    private static final class PersistentPlayerState {
        private int worldzero$completedSoundSessions;
        private int worldzero$maxSoundSessions;
    }

    private static final class MinePresenceSaveData extends SavedData {
        private final Map<UUID, PersistentPlayerState> worldzero$playerStates = new HashMap<>();

        private static MinePresenceSaveData worldzero$load(CompoundTag tag) {
            MinePresenceSaveData saveData = new MinePresenceSaveData();
            ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
            for (int index = 0; index < players.size(); index++) {
                CompoundTag playerTag = players.getCompound(index);
                if (!playerTag.hasUUID("player_id")) {
                    continue;
                }

                PersistentPlayerState playerState = new PersistentPlayerState();
                playerState.worldzero$completedSoundSessions = Math.max(0, playerTag.getInt("completed_sound_sessions"));
                playerState.worldzero$maxSoundSessions = Math.max(0, playerTag.getInt("max_sound_sessions"));
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
                playerTag.putInt("completed_sound_sessions", entry.getValue().worldzero$completedSoundSessions);
                playerTag.putInt("max_sound_sessions", entry.getValue().worldzero$maxSoundSessions);
                players.add(playerTag);
            }
            tag.put("players", players);
            return tag;
        }
    }
}
