package ru.nekostul.worldzero;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.achievement.WorldZeroAdvancementTriggers;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroFootstepsEvent {
    private static final long WORLDZERO_TRIGGER_MIN_TICKS = 60L * 60L * 20L;
    private static final long WORLDZERO_TRIGGER_MAX_TICKS = 90L * 60L * 20L;
    private static final int WORLDZERO_SECOND_STEP_MIN_TICKS = 20 * 20;
    private static final int WORLDZERO_SECOND_STEP_MAX_TICKS = 40 * 20;
    private static final int WORLDZERO_IGNORE_MIN_TICKS = 2 * 60 * 20;
    private static final int WORLDZERO_IGNORE_MAX_TICKS = 4 * 60 * 20;
    private static final int WORLDZERO_WINDOW_ECHO_TICKS = 10 * 20;
    private static final int WORLDZERO_POST_REACTION_TICKS = 2 * 20;
    private static final int WORLDZERO_SILENT_CHEST_TICKS = 3 * 20;
    private static final double WORLDZERO_STEP_DISTANCE_BLOCKS = 2.75D;
    private static final double WORLDZERO_STEP_VOLUME = 0.65D;
    private static final double WORLDZERO_VIEW_TO_SOURCE_DOT = 0.8D;
    private static final float WORLDZERO_MIN_TURN_YAW_DEGREES = 80.0F;
    private static final float WORLDZERO_MIN_TURN_PITCH_DEGREES = 20.0F;
    private static final double WORLDZERO_ENVIRONMENT_CHANGE_CHANCE = 0.75D;
    private static final int WORLDZERO_INTERACTION_RADIUS_BLOCKS = 8;
    private static final int WORLDZERO_WINDOW_SEARCH_RADIUS_BLOCKS = 2;
    private static final AABB WORLDZERO_ENTITY_SCAN_AABB = new AABB(
            -30_000_000.0D,
            -2_048.0D,
            -30_000_000.0D,
            30_000_000.0D,
            4_096.0D,
            30_000_000.0D
    );
    private static final String WORLDZERO_SAVE_ID = "worldzero_footsteps_event";
    private static final Map<MinecraftServer, SessionState> WORLDZERO_SESSION_STATES = new WeakHashMap<>();
    private static final Map<MinecraftServer, BlankDiscPlaybackState> WORLDZERO_BLANK_DISC_PLAYBACKS = new WeakHashMap<>();
    private static final Set<UUID> WORLDZERO_BLANK_DISC_ACHIEVEMENT_PLAYERS = new HashSet<>();

    private WorldZeroFootstepsEvent() {
    }

    @SubscribeEvent
    public static void worldzero$onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.level instanceof ServerLevel level) || level.isClientSide() || level.dimension() != Level.OVERWORLD) {
            return;
        }

        worldzero$checkBlankDiscInJukeboxes(level);

        MinecraftServer server = level.getServer();
        SessionState sessionState = WORLDZERO_SESSION_STATES.computeIfAbsent(server, ignored -> new SessionState());
        if (sessionState.worldzero$phase != Phase.INACTIVE) {
            worldzero$tickActiveEvent(level, sessionState);
            return;
        }

        if (WorldZeroFreezeEvent.worldzero$isFreezeActive(server) || WorldZeroFallEvent.worldzero$isFallActive(server)) {
            return;
        }

        if (WorldZeroAmbientSoundEvent.worldzero$isMajorEventStartBlocked(level)) {
            return;
        }

        FootstepsSaveData saveData = worldzero$getSaveData(level);
        if (saveData.worldzero$completed) {
            return;
        }

        if (saveData.worldzero$triggerTick < 0L) {
            long span = WORLDZERO_TRIGGER_MAX_TICKS - WORLDZERO_TRIGGER_MIN_TICKS + 1L;
            saveData.worldzero$triggerTick = WORLDZERO_TRIGGER_MIN_TICKS + (long) (level.random.nextDouble() * span);
            saveData.setDirty();
        }

        if (level.getGameTime() < saveData.worldzero$triggerTick || !level.isNight()) {
            return;
        }

        StartTarget target = worldzero$pickEligiblePlayer(level);
        if (target == null) {
            return;
        }

        worldzero$startEvent(level, sessionState, saveData, target.player(), target.house());
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_SESSION_STATES.remove(event.getServer());
        WORLDZERO_BLANK_DISC_PLAYBACKS.remove(event.getServer());
        WORLDZERO_BLANK_DISC_ACHIEVEMENT_PLAYERS.clear();
    }

    @SubscribeEvent
    public static void worldzero$onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!(state.getBlock() instanceof JukeboxBlock)) {
            return;
        }

        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof JukeboxBlockEntity jukeboxBlockEntity)) {
            return;
        }

        if (worldzero$hasBlankDisc(jukeboxBlockEntity.getItem(0))) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
            return;
        }

        if (!worldzero$hasBlankDisc(event.getItemStack()) || state.getValue(JukeboxBlock.HAS_RECORD)) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        worldzero$insertBlankDisc(player.serverLevel(), event.getPos(), state, jukeboxBlockEntity, player, event.getHand());
    }

    public static void worldzero$acknowledgeBlankDiscPlaybackFinished(ServerPlayer player) {
        if (player == null) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        BlankDiscPlaybackState playbackState = worldzero$getBlankDiscPlaybackState(server);
        if (playbackState == null || !playbackState.worldzero$playerId.equals(player.getUUID())) {
            return;
        }

        ServerLevel level = server.getLevel(playbackState.worldzero$dimension);
        if (level != null && level.getBlockEntity(playbackState.worldzero$jukeboxPos) instanceof JukeboxBlockEntity jukeboxBlockEntity) {
            if (worldzero$hasBlankDisc(jukeboxBlockEntity.getItem(0))) {
                jukeboxBlockEntity.popOutRecord();
                WorldZeroNetwork.sendBlankDiscPlaybackError(player);
            }
        }

        FootstepsSaveData saveData = worldzero$getPersistentSaveData(server);
        saveData.worldzero$blankDiscPlaybackDone = true;
        saveData.setDirty();
        WORLDZERO_BLANK_DISC_PLAYBACKS.remove(server);
    }

    public static boolean worldzero$resetBlankDiscPlayback(MinecraftServer server) {
        if (server == null) {
            return false;
        }

        boolean changed = false;
        BlankDiscPlaybackState playbackState = WORLDZERO_BLANK_DISC_PLAYBACKS.remove(server);
        if (playbackState != null) {
            ServerLevel level = server.getLevel(playbackState.worldzero$dimension);
            if (level != null && level.getBlockEntity(playbackState.worldzero$jukeboxPos) instanceof JukeboxBlockEntity jukeboxBlockEntity) {
                if (worldzero$hasBlankDisc(jukeboxBlockEntity.getItem(0))) {
                    jukeboxBlockEntity.popOutRecord();
                }
            }
            changed = true;
        }

        FootstepsSaveData saveData = worldzero$getPersistentSaveData(server);
        if (saveData.worldzero$blankDiscPlaybackDone) {
            saveData.worldzero$blankDiscPlaybackDone = false;
            saveData.setDirty();
            changed = true;
        }

        return changed;
    }

    public static boolean worldzero$triggerFootstepsNow(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator() || player.serverLevel().dimension() != Level.OVERWORLD) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null || WorldZeroFreezeEvent.worldzero$isFreezeActive(server) || WorldZeroFallEvent.worldzero$isFallActive(server)) {
            return false;
        }

        SessionState sessionState = WORLDZERO_SESSION_STATES.computeIfAbsent(server, ignored -> new SessionState());
        if (sessionState.worldzero$phase != Phase.INACTIVE) {
            return false;
        }

        WorldZeroHouseDetector.DetectedHouse detectedHouse = WorldZeroHouseDetector.worldzero$findContainingHouse(player);
        if (detectedHouse == null) {
            detectedHouse = WorldZeroHouseDetector.worldzero$findNearbyHouseForDebug(player);
        }

        return worldzero$startEvent(player.serverLevel(), sessionState, null, player, detectedHouse);
    }

    public static boolean worldzero$isFootstepsActive(MinecraftServer server) {
        SessionState state = WORLDZERO_SESSION_STATES.get(server);
        return state != null && state.worldzero$phase != Phase.INACTIVE;
    }

    private static boolean worldzero$startEvent(
            ServerLevel level,
            SessionState state,
            @Nullable FootstepsSaveData saveData,
            ServerPlayer player,
            @Nullable WorldZeroHouseDetector.DetectedHouse detectedHouse
    ) {
        state.worldzero$phase = Phase.WAIT_SECOND_STEP;
        state.worldzero$targetPlayerId = player.getUUID();
        state.worldzero$phaseEndTick = level.getGameTime() + Mth.nextInt(
                level.random,
                WORLDZERO_SECOND_STEP_MIN_TICKS,
                WORLDZERO_SECOND_STEP_MAX_TICKS
        );
        state.worldzero$house = detectedHouse;
        state.worldzero$echoId = null;
        state.worldzero$sourceX = player.getX();
        state.worldzero$sourceY = player.getEyeY();
        state.worldzero$sourceZ = player.getZ();
        state.worldzero$reactionStartYaw = player.getYRot();
        state.worldzero$reactionStartPitch = player.getXRot();
        WorldZeroAmbientSoundEvent.worldzero$notifyMajorEventStarted(level);

        if (saveData != null) {
            saveData.worldzero$completed = true;
            saveData.setDirty();
        }

        worldzero$playFootstepBehind(level, player, null);
        return true;
    }

    private static void worldzero$tickActiveEvent(ServerLevel level, SessionState state) {
        worldzero$closeSilentChestIfNeeded(level, state);

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(state.worldzero$targetPlayerId);
        if (player == null || !player.isAlive() || player.isSpectator() || player.serverLevel() != level) {
            worldzero$clearState(level.getServer(), state);
            return;
        }

        switch (state.worldzero$phase) {
            case WAIT_SECOND_STEP -> worldzero$tickSecondStepPhase(level, state, player);
            case WAIT_REACTION -> worldzero$tickReactionPhase(level, state, player);
            case POST_REACTION -> worldzero$tickPostReactionPhase(level, state, player);
            case WINDOW_ECHO -> worldzero$tickWindowEchoPhase(level, state, player);
            default -> {
            }
        }
    }

    private static void worldzero$tickSecondStepPhase(ServerLevel level, SessionState state, ServerPlayer player) {
        if (level.getGameTime() < state.worldzero$phaseEndTick) {
            return;
        }

        Vec3 source = worldzero$playFootstepBehind(level, player, state);
        state.worldzero$sourceX = source.x;
        state.worldzero$sourceY = source.y;
        state.worldzero$sourceZ = source.z;
        state.worldzero$reactionStartYaw = player.getYRot();
        state.worldzero$reactionStartPitch = player.getXRot();
        state.worldzero$phase = Phase.WAIT_REACTION;
        state.worldzero$phaseEndTick = level.getGameTime() + Mth.nextInt(
                level.random,
                WORLDZERO_IGNORE_MIN_TICKS,
                WORLDZERO_IGNORE_MAX_TICKS
        );
    }

    private static void worldzero$tickReactionPhase(ServerLevel level, SessionState state, ServerPlayer player) {
        if (worldzero$hasTurnedTowardSource(player, state)) {
            worldzero$triggerSubtleEnvironmentChange(level, player, state);
            state.worldzero$phase = Phase.POST_REACTION;
            state.worldzero$phaseEndTick = level.getGameTime() + WORLDZERO_POST_REACTION_TICKS;
            return;
        }

        if (level.getGameTime() < state.worldzero$phaseEndTick) {
            return;
        }

        WorldZeroEchoEntity echo = worldzero$spawnWindowEcho(level, player, state.worldzero$house);
        if (echo == null) {
            worldzero$finishEvent(level, state, player);
            return;
        }

        state.worldzero$echoId = echo.getUUID();
        state.worldzero$phase = Phase.WINDOW_ECHO;
        state.worldzero$phaseEndTick = level.getGameTime() + WORLDZERO_WINDOW_ECHO_TICKS;
    }

    private static void worldzero$tickPostReactionPhase(ServerLevel level, SessionState state, ServerPlayer player) {
        if (level.getGameTime() < state.worldzero$phaseEndTick) {
            return;
        }

        worldzero$finishEvent(level, state, player);
    }

    private static void worldzero$tickWindowEchoPhase(ServerLevel level, SessionState state, ServerPlayer player) {
        Entity echo = worldzero$findEntity(level.getServer(), state.worldzero$echoId);
        if (echo == null || level.getGameTime() >= state.worldzero$phaseEndTick) {
            worldzero$finishEvent(level, state, player);
        }
    }

    private static void worldzero$finishEvent(ServerLevel level, SessionState state, ServerPlayer player) {
        ItemStack blankDisc = new ItemStack(WorldZeroItems.WORLDZERO_BLANK_DISC.get());
        if (!player.getInventory().add(blankDisc)) {
            player.drop(blankDisc, false);
        }

        worldzero$clearState(level.getServer(), state);
    }

    @Nullable
    private static StartTarget worldzero$pickEligiblePlayer(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }

            WorldZeroHouseDetector.DetectedHouse detectedHouse = WorldZeroHouseDetector.worldzero$findContainingHouse(player);
            if (detectedHouse == null) {
                continue;
            }

            return new StartTarget(player, detectedHouse);
        }

        return null;
    }

    private static Vec3 worldzero$playFootstepBehind(
            ServerLevel level,
            ServerPlayer player,
            @Nullable SessionState state
    ) {
        Vec3 source = worldzero$resolveFootstepSource(level, player);
        BlockPos belowPos = BlockPos.containing(source.x, source.y - 0.2D, source.z).below();
        BlockState belowState = level.getBlockState(belowPos);
        SoundType soundType = belowState.getSoundType(level, belowPos, player);
        level.playSound(
                null,
                source.x,
                source.y,
                source.z,
                soundType.getStepSound(),
                SoundSource.BLOCKS,
                (float) WORLDZERO_STEP_VOLUME,
                0.92F + level.random.nextFloat() * 0.12F
        );

        if (state != null) {
            state.worldzero$sourceX = source.x;
            state.worldzero$sourceY = source.y;
            state.worldzero$sourceZ = source.z;
        }

        return source;
    }

    private static Vec3 worldzero$resolveFootstepSource(ServerLevel level, ServerPlayer player) {
        Vec3 look = player.getViewVector(1.0F);
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() < 1.0E-6D) {
            float radians = (float) Math.toRadians(player.getYRot());
            horizontalLook = new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
        }
        horizontalLook = horizontalLook.normalize();

        Vec3 base = player.position().subtract(horizontalLook.scale(WORLDZERO_STEP_DISTANCE_BLOCKS));
        int baseX = Mth.floor(base.x);
        int baseZ = Mth.floor(base.z);
        int playerY = Mth.floor(player.getY());

        for (int dy = 1; dy >= -3; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos candidate = new BlockPos(baseX + dx, playerY + dy, baseZ + dz);
                    if (!worldzero$isWalkableStepSpot(level, candidate)) {
                        continue;
                    }
                    return new Vec3(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
                }
            }
        }

        return new Vec3(base.x, player.getY(), base.z);
    }

    private static boolean worldzero$isWalkableStepSpot(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockState aboveState = level.getBlockState(pos.above());
        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);
        FluidState fluidState = state.getFluidState();
        return state.isAir()
                && aboveState.isAir()
                && fluidState.isEmpty()
                && belowState.isFaceSturdy(level, belowPos, Direction.UP)
                && belowState.getFluidState().isEmpty();
    }

    private static boolean worldzero$hasTurnedTowardSource(ServerPlayer player, SessionState state) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 toSource = new Vec3(
                state.worldzero$sourceX - eyePos.x,
                state.worldzero$sourceY - eyePos.y,
                state.worldzero$sourceZ - eyePos.z
        );
        if (toSource.lengthSqr() < 1.0E-6D) {
            return false;
        }

        double facingDot = player.getViewVector(1.0F).normalize().dot(toSource.normalize());
        float yawDelta = Math.abs(Mth.wrapDegrees(player.getYRot() - state.worldzero$reactionStartYaw));
        float pitchDelta = Math.abs(player.getXRot() - state.worldzero$reactionStartPitch);
        return facingDot >= WORLDZERO_VIEW_TO_SOURCE_DOT
                && (yawDelta >= WORLDZERO_MIN_TURN_YAW_DEGREES || pitchDelta >= WORLDZERO_MIN_TURN_PITCH_DEGREES);
    }

    private static void worldzero$triggerSubtleEnvironmentChange(ServerLevel level, ServerPlayer player, SessionState state) {
        if (level.random.nextDouble() > WORLDZERO_ENVIRONMENT_CHANGE_CHANCE) {
            return;
        }

        BlockPos doorPos = worldzero$findNearestDoor(level, player.blockPosition(), state.worldzero$house);
        BlockPos chestPos = worldzero$findNearestChest(level, player.blockPosition(), state.worldzero$house);
        if (doorPos == null && chestPos == null) {
            return;
        }

        if (doorPos != null && chestPos != null) {
            if (level.random.nextBoolean()) {
                if (worldzero$openSilentDoor(level, doorPos)) {
                    return;
                }
                worldzero$openSilentChest(level, chestPos, state);
                return;
            }

            if (worldzero$openSilentChest(level, chestPos, state)) {
                return;
            }
            worldzero$openSilentDoor(level, doorPos);
            return;
        }

        if (doorPos != null) {
            worldzero$openSilentDoor(level, doorPos);
            return;
        }

        if (chestPos != null) {
            worldzero$openSilentChest(level, chestPos, state);
        }
    }

    @Nullable
    private static BlockPos worldzero$findNearestDoor(
            ServerLevel level,
            BlockPos center,
            @Nullable WorldZeroHouseDetector.DetectedHouse house
    ) {
        BlockPos bestPos = null;
        double bestDistanceSqr = Double.MAX_VALUE;
        Bounds bounds = worldzero$resolveSearchBounds(center, house);
        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int y = bounds.minY; y <= bounds.maxY; y++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (center.distSqr(pos) > (double) (WORLDZERO_INTERACTION_RADIUS_BLOCKS * WORLDZERO_INTERACTION_RADIUS_BLOCKS)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(pos);
                    if (!state.is(BlockTags.DOORS)) {
                        continue;
                    }

                    double distanceSqr = pos.distSqr(center);
                    if (distanceSqr < bestDistanceSqr) {
                        bestDistanceSqr = distanceSqr;
                        bestPos = pos.immutable();
                    }
                }
            }
        }
        return bestPos;
    }

    @Nullable
    private static BlockPos worldzero$findNearestChest(
            ServerLevel level,
            BlockPos center,
            @Nullable WorldZeroHouseDetector.DetectedHouse house
    ) {
        BlockPos bestPos = null;
        double bestDistanceSqr = Double.MAX_VALUE;
        Bounds bounds = worldzero$resolveSearchBounds(center, house);
        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int y = bounds.minY; y <= bounds.maxY; y++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (center.distSqr(pos) > (double) (WORLDZERO_INTERACTION_RADIUS_BLOCKS * WORLDZERO_INTERACTION_RADIUS_BLOCKS)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof ChestBlock)) {
                        continue;
                    }

                    double distanceSqr = pos.distSqr(center);
                    if (distanceSqr < bestDistanceSqr) {
                        bestDistanceSqr = distanceSqr;
                        bestPos = pos.immutable();
                    }
                }
            }
        }
        return bestPos;
    }

    private static Bounds worldzero$resolveSearchBounds(
            BlockPos center,
            @Nullable WorldZeroHouseDetector.DetectedHouse house
    ) {
        if (house == null) {
            return new Bounds(
                    center.getX() - WORLDZERO_INTERACTION_RADIUS_BLOCKS,
                    center.getY() - 4,
                    center.getZ() - WORLDZERO_INTERACTION_RADIUS_BLOCKS,
                    center.getX() + WORLDZERO_INTERACTION_RADIUS_BLOCKS,
                    center.getY() + 4,
                    center.getZ() + WORLDZERO_INTERACTION_RADIUS_BLOCKS
            );
        }

        return new Bounds(
                Math.max(center.getX() - WORLDZERO_INTERACTION_RADIUS_BLOCKS, house.interiorMin().getX() - 2),
                Math.max(center.getY() - 4, house.interiorMin().getY() - 1),
                Math.max(center.getZ() - WORLDZERO_INTERACTION_RADIUS_BLOCKS, house.interiorMin().getZ() - 2),
                Math.min(center.getX() + WORLDZERO_INTERACTION_RADIUS_BLOCKS, house.interiorMax().getX() + 2),
                Math.min(center.getY() + 4, house.interiorMax().getY() + 2),
                Math.min(center.getZ() + WORLDZERO_INTERACTION_RADIUS_BLOCKS, house.interiorMax().getZ() + 2)
        );
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
                || !upperState.hasProperty(DoorBlock.OPEN)) {
            return false;
        }

        if (lowerState.getValue(DoorBlock.OPEN)) {
            return true;
        }

        level.setBlock(lowerPos, lowerState.setValue(DoorBlock.OPEN, true), 18);
        level.setBlock(upperPos, upperState.setValue(DoorBlock.OPEN, true), 18);
        return true;
    }

    private static boolean worldzero$openSilentChest(ServerLevel level, BlockPos pos, SessionState state) {
        BlockState chestState = level.getBlockState(pos);
        if (!(chestState.getBlock() instanceof ChestBlock)) {
            return false;
        }

        level.blockEvent(pos, chestState.getBlock(), 1, 1);
        level.sendBlockUpdated(pos, chestState, chestState, 3);
        state.worldzero$openedChestPos = pos.immutable();
        state.worldzero$openedChestCloseTick = level.getGameTime() + WORLDZERO_SILENT_CHEST_TICKS;
        return true;
    }

    private static void worldzero$closeSilentChestIfNeeded(ServerLevel level, SessionState state) {
        if (state.worldzero$openedChestPos == null || state.worldzero$openedChestCloseTick < 0L) {
            return;
        }

        if (level.getGameTime() < state.worldzero$openedChestCloseTick) {
            return;
        }

        BlockState chestState = level.getBlockState(state.worldzero$openedChestPos);
        if (chestState.getBlock() instanceof ChestBlock) {
            level.blockEvent(state.worldzero$openedChestPos, chestState.getBlock(), 1, 0);
            level.sendBlockUpdated(state.worldzero$openedChestPos, chestState, chestState, 3);
        }

        state.worldzero$openedChestPos = null;
        state.worldzero$openedChestCloseTick = -1L;
    }

    @Nullable
    private static WorldZeroEchoEntity worldzero$spawnWindowEcho(
            ServerLevel level,
            ServerPlayer player,
            @Nullable WorldZeroHouseDetector.DetectedHouse house
    ) {
        if (worldzero$hasActiveEcho(level.getServer())) {
            return null;
        }

        Vec3 spawnPos = house != null ? worldzero$findWindowSpawn(level, player, house) : null;
        if (spawnPos == null) {
            spawnPos = worldzero$findFallbackEchoSpawn(level, player);
        }
        if (spawnPos == null) {
            return null;
        }

        WorldZeroEchoEntity echo = WorldZeroEntities.WORLDZERO_ECHO.get().create(level);
        if (echo == null) {
            return null;
        }

        double deltaX = player.getX() - spawnPos.x;
        double deltaZ = player.getZ() - spawnPos.z;
        float yaw = (float) (Mth.atan2(deltaZ, deltaX) * (180.0D / Math.PI)) - 90.0F;
        echo.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, yaw, 0.0F);
        echo.setCustomName(Component.literal(player.getGameProfile().getName()));
        echo.setCustomNameVisible(false);
        echo.worldzero$configureWindowWatch(player.getUUID(), WORLDZERO_WINDOW_ECHO_TICKS);
        level.addFreshEntity(echo);
        return echo;
    }

    @Nullable
    private static Vec3 worldzero$findWindowSpawn(
            ServerLevel level,
            ServerPlayer player,
            WorldZeroHouseDetector.DetectedHouse house
    ) {
        Vec3 fallback = null;
        int minShellX = house.interiorMin().getX() - 1;
        int maxShellX = house.interiorMax().getX() + 1;
        int minShellY = house.interiorMin().getY();
        int maxShellY = house.interiorMax().getY() + 1;
        int minShellZ = house.interiorMin().getZ() - 1;
        int maxShellZ = house.interiorMax().getZ() + 1;

        for (int y = minShellY; y <= maxShellY; y++) {
            for (int x = minShellX; x <= maxShellX; x++) {
                Vec3 northGlass = worldzero$evaluateWindowCandidate(level, player, new BlockPos(x, y, minShellZ), Direction.NORTH, true);
                if (northGlass != null) {
                    return northGlass;
                }
                Vec3 southGlass = worldzero$evaluateWindowCandidate(level, player, new BlockPos(x, y, maxShellZ), Direction.SOUTH, true);
                if (southGlass != null) {
                    return southGlass;
                }
                if (fallback == null) {
                    fallback = worldzero$evaluateWindowCandidate(level, player, new BlockPos(x, y, minShellZ), Direction.NORTH, false);
                }
                if (fallback == null) {
                    fallback = worldzero$evaluateWindowCandidate(level, player, new BlockPos(x, y, maxShellZ), Direction.SOUTH, false);
                }
            }

            for (int z = minShellZ + 1; z <= maxShellZ - 1; z++) {
                Vec3 westGlass = worldzero$evaluateWindowCandidate(level, player, new BlockPos(minShellX, y, z), Direction.WEST, true);
                if (westGlass != null) {
                    return westGlass;
                }
                Vec3 eastGlass = worldzero$evaluateWindowCandidate(level, player, new BlockPos(maxShellX, y, z), Direction.EAST, true);
                if (eastGlass != null) {
                    return eastGlass;
                }
                if (fallback == null) {
                    fallback = worldzero$evaluateWindowCandidate(level, player, new BlockPos(minShellX, y, z), Direction.WEST, false);
                }
                if (fallback == null) {
                    fallback = worldzero$evaluateWindowCandidate(level, player, new BlockPos(maxShellX, y, z), Direction.EAST, false);
                }
            }
        }

        return fallback;
    }

    @Nullable
    private static Vec3 worldzero$evaluateWindowCandidate(
            ServerLevel level,
            ServerPlayer player,
            BlockPos wallPos,
            Direction outwardDirection,
            boolean requireGlass
    ) {
        BlockState wallState = level.getBlockState(wallPos);
        if (requireGlass) {
            if (!worldzero$isGlassLike(wallState)) {
                return null;
            }
        } else if (wallState.isAir()) {
            return null;
        }

        Vec3 fallback = null;
        for (int distance = 1; distance <= WORLDZERO_WINDOW_SEARCH_RADIUS_BLOCKS; distance++) {
            BlockPos base = wallPos.relative(outwardDirection, distance);
            Vec3 spawnPos = worldzero$findStandingEchoSpawn(level, base, wallPos.getY());
            if (spawnPos == null) {
                continue;
            }

            if (worldzero$isWindowVisible(level, player, spawnPos, wallPos)) {
                return spawnPos;
            }

            if (fallback == null) {
                fallback = spawnPos;
            }
        }
        return fallback;
    }

    private static boolean worldzero$isWindowVisible(
            ServerLevel level,
            ServerPlayer player,
            Vec3 spawnPos,
            BlockPos windowPos
    ) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 target = new Vec3(spawnPos.x, spawnPos.y + 1.55D, spawnPos.z);
        BlockHitResult hitResult = level.clip(new net.minecraft.world.level.ClipContext(
                eyePos,
                target,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player
        ));
        if (hitResult.getType() == HitResult.Type.MISS) {
            return true;
        }

        BlockPos hitPos = hitResult.getBlockPos();
        return hitPos.equals(windowPos) || worldzero$isGlassLike(level.getBlockState(hitPos));
    }

    @Nullable
    private static Vec3 worldzero$findStandingEchoSpawn(ServerLevel level, BlockPos base, int referenceY) {
        for (int dy = 2; dy >= -5; dy--) {
            BlockPos candidate = new BlockPos(base.getX(), referenceY + dy, base.getZ());
            if (!worldzero$isValidEchoSpawn(level, candidate)) {
                continue;
            }

            return new Vec3(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
        }
        return null;
    }

    @Nullable
    private static Vec3 worldzero$findFallbackEchoSpawn(ServerLevel level, ServerPlayer player) {
        Vec3 look = player.getViewVector(1.0F);
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() < 1.0E-6D) {
            float radians = (float) Math.toRadians(player.getYRot());
            horizontalLook = new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
        }
        horizontalLook = horizontalLook.normalize();

        Vec3 base = player.position().add(horizontalLook.scale(5.0D));
        int baseX = Mth.floor(base.x);
        int baseZ = Mth.floor(base.z);
        int baseY = Mth.floor(player.getY());
        for (int dy = 2; dy >= -4; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos candidate = new BlockPos(baseX + dx, baseY + dy, baseZ + dz);
                    if (!worldzero$isValidEchoSpawn(level, candidate)) {
                        continue;
                    }

                    return new Vec3(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
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

        AABB spawnBox = WorldZeroEntities.WORLDZERO_ECHO.get().getDimensions().makeBoundingBox(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D
        );
        return level.noCollision(spawnBox) && !level.containsAnyLiquid(spawnBox);
    }

    private static boolean worldzero$isGlassLike(BlockState state) {
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.contains("glass") || path.contains("pane");
    }

    private static boolean worldzero$hasActiveEcho(MinecraftServer server) {
        for (ServerLevel serverLevel : server.getAllLevels()) {
            if (!serverLevel.getEntitiesOfClass(
                    WorldZeroEchoEntity.class,
                    WORLDZERO_ENTITY_SCAN_AABB,
                    entity -> entity.getType() == WorldZeroEntities.WORLDZERO_ECHO.get()
            ).isEmpty()) {
                return true;
            }
        }

        return false;
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

    private static void worldzero$clearState(MinecraftServer server, SessionState state) {
        Entity echo = worldzero$findEntity(server, state.worldzero$echoId);
        if (echo != null) {
            echo.discard();
        }

        ServerLevel level = server.overworld();
        if (level != null && state.worldzero$openedChestPos != null) {
            BlockState chestState = level.getBlockState(state.worldzero$openedChestPos);
            if (chestState.getBlock() instanceof ChestBlock) {
                level.blockEvent(state.worldzero$openedChestPos, chestState.getBlock(), 1, 0);
                level.sendBlockUpdated(state.worldzero$openedChestPos, chestState, chestState, 3);
            }
        }

        state.worldzero$phase = Phase.INACTIVE;
        state.worldzero$targetPlayerId = null;
        state.worldzero$phaseEndTick = -1L;
        state.worldzero$house = null;
        state.worldzero$echoId = null;
        state.worldzero$openedChestPos = null;
        state.worldzero$openedChestCloseTick = -1L;
    }

    private static FootstepsSaveData worldzero$getSaveData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FootstepsSaveData::load, FootstepsSaveData::new, WORLDZERO_SAVE_ID);
    }

    private static FootstepsSaveData worldzero$getPersistentSaveData(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return worldzero$getSaveData(overworld != null ? overworld : server.getAllLevels().iterator().next());
    }

    private static void worldzero$insertBlankDisc(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            JukeboxBlockEntity jukeboxBlockEntity,
            ServerPlayer player,
            net.minecraft.world.InteractionHand hand
    ) {
        ItemStack heldStack = player.getItemInHand(hand);
        if (!worldzero$hasBlankDisc(heldStack)) {
            return;
        }

        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }

        worldzero$placeBlankDiscIntoJukebox(level, pos, state, jukeboxBlockEntity, heldStack, player);

        FootstepsSaveData saveData = worldzero$getPersistentSaveData(server);
        if (saveData.worldzero$blankDiscPlaybackDone) {
            jukeboxBlockEntity.popOutRecord();
            return;
        }

        BlankDiscPlaybackState activePlayback = worldzero$getBlankDiscPlaybackState(server);
        if (activePlayback != null) {
            jukeboxBlockEntity.popOutRecord();
            return;
        }

        WORLDZERO_BLANK_DISC_PLAYBACKS.put(
                server,
                new BlankDiscPlaybackState(player.getUUID(), level.dimension(), pos.immutable())
        );
        WorldZeroNetwork.sendBlankDiscPlayback(player);
    }

    private static void worldzero$placeBlankDiscIntoJukebox(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            JukeboxBlockEntity jukeboxBlockEntity,
            ItemStack heldStack,
            ServerPlayer player
    ) {
        ItemStack insertedStack = heldStack.copy();
        insertedStack.setCount(1);
        jukeboxBlockEntity.setRecordWithoutPlaying(insertedStack);
        jukeboxBlockEntity.setChanged();
        level.setBlock(pos, state.setValue(JukeboxBlock.HAS_RECORD, true), 3);

        if (!player.getAbilities().instabuild) {
            heldStack.shrink(1);
        }
    }

    @Nullable
    private static BlankDiscPlaybackState worldzero$getBlankDiscPlaybackState(MinecraftServer server) {
        BlankDiscPlaybackState playbackState = WORLDZERO_BLANK_DISC_PLAYBACKS.get(server);
        if (playbackState == null) {
            return null;
        }

        if (server.getPlayerList().getPlayer(playbackState.worldzero$playerId) == null) {
            WORLDZERO_BLANK_DISC_PLAYBACKS.remove(server);
            return null;
        }

        return playbackState;
    }

    private static boolean worldzero$hasBlankDisc(ItemStack stack) {
        return !stack.isEmpty() && stack.is(WorldZeroItems.WORLDZERO_BLANK_DISC.get());
    }

    private static void worldzero$checkBlankDiscInJukeboxes(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            BlockPos playerPos = player.blockPosition();
            int minChunkX = (playerPos.getX() >> 4) - 10;
            int maxChunkX = (playerPos.getX() >> 4) + 10;
            int minChunkZ = (playerPos.getZ() >> 4) - 10;
            int maxChunkZ = (playerPos.getZ() >> 4) + 10;

            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    var chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                    if (chunk instanceof net.minecraft.world.level.chunk.LevelChunk levelChunk) {
                        levelChunk.getBlockEntities().forEach((pos, blockEntity) -> {
                            if (blockEntity instanceof JukeboxBlockEntity) {
                                var nbt = blockEntity.saveWithFullMetadata();
                                if (nbt.contains("RecordItem")) {
                                    var recordTag = nbt.getCompound("RecordItem");
                                    if (recordTag.contains("id") && recordTag.getString("id").contains("blank_disc")) {
                                        UUID playerId = player.getUUID();
                                        if (!WORLDZERO_BLANK_DISC_ACHIEVEMENT_PLAYERS.contains(playerId)) {
                                            WORLDZERO_BLANK_DISC_ACHIEVEMENT_PLAYERS.add(playerId);
                                            WorldZeroAdvancementTriggers.grantForgottenDisc(player);
                                        }
                                    }
                                }
                            }
                        });
                    }
                }
            }
        }
    }

    private enum Phase {
        INACTIVE,
        WAIT_SECOND_STEP,
        WAIT_REACTION,
        POST_REACTION,
        WINDOW_ECHO
    }

    private static final class SessionState {
        private Phase worldzero$phase = Phase.INACTIVE;
        private UUID worldzero$targetPlayerId;
        private long worldzero$phaseEndTick = -1L;
        private WorldZeroHouseDetector.DetectedHouse worldzero$house;
        private double worldzero$sourceX;
        private double worldzero$sourceY;
        private double worldzero$sourceZ;
        private float worldzero$reactionStartYaw;
        private float worldzero$reactionStartPitch;
        private UUID worldzero$echoId;
        private BlockPos worldzero$openedChestPos;
        private long worldzero$openedChestCloseTick = -1L;
    }

    private static final class BlankDiscPlaybackState {
        private final UUID worldzero$playerId;
        private final ResourceKey<Level> worldzero$dimension;
        private final BlockPos worldzero$jukeboxPos;

        private BlankDiscPlaybackState(UUID playerId, ResourceKey<Level> dimension, BlockPos jukeboxPos) {
            this.worldzero$playerId = playerId;
            this.worldzero$dimension = dimension;
            this.worldzero$jukeboxPos = jukeboxPos;
        }
    }

    private static final class FootstepsSaveData extends SavedData {
        private long worldzero$triggerTick = -1L;
        private boolean worldzero$completed;
        private boolean worldzero$blankDiscPlaybackDone;

        public static FootstepsSaveData load(CompoundTag tag) {
            FootstepsSaveData saveData = new FootstepsSaveData();
            saveData.worldzero$triggerTick = tag.getLong("trigger_tick");
            saveData.worldzero$completed = tag.getBoolean("completed");
            saveData.worldzero$blankDiscPlaybackDone = tag.getBoolean("blank_disc_playback_done");
            return saveData;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putLong("trigger_tick", this.worldzero$triggerTick);
            tag.putBoolean("completed", this.worldzero$completed);
            tag.putBoolean("blank_disc_playback_done", this.worldzero$blankDiscPlaybackDone);
            return tag;
        }
    }

    private record StartTarget(ServerPlayer player, WorldZeroHouseDetector.DetectedHouse house) {
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }
}
