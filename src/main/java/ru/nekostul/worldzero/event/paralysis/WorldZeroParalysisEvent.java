package ru.nekostul.worldzero;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.achievement.WorldZeroAdvancementTriggers;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
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
    private static final int WORLDZERO_RETURN_TO_BED_KNOCK_TICKS = 65;
    private static final int WORLDZERO_RETURN_TO_BED_DOOR_DELAY_TICKS = 20;
    private static final int WORLDZERO_RETURN_TO_BED_FOOTSTEP_INTERVAL_TICKS = 8;
    private static final int WORLDZERO_RETURN_TO_BED_FOOTSTEP_COUNT = 5;
    private static final int WORLDZERO_RETURN_TO_BED_CHEST_CLOSE_DELAY_TICKS = 20;
    private static final int WORLDZERO_KORIDOR_SLEEP_FADE_TICKS = 3 * 20;
    private static final int WORLDZERO_RETURN_TO_BED_GLASS_BREAK_DELAY_TICKS = 4;
    private static final int WORLDZERO_RETURN_TO_BED_GLASS_BREAK_DURATION_TICKS = 10;
    private static final int WORLDZERO_RETURN_TO_BED_FOOTSTEP_SPAN_TICKS =
            WORLDZERO_RETURN_TO_BED_FOOTSTEP_INTERVAL_TICKS * (WORLDZERO_RETURN_TO_BED_FOOTSTEP_COUNT - 1);
    private static final int WORLDZERO_RETURN_TO_BED_DOOR_APPROACH_START_TICKS = WORLDZERO_RETURN_TO_BED_KNOCK_TICKS
            + WORLDZERO_RETURN_TO_BED_DOOR_DELAY_TICKS;
    private static final int WORLDZERO_RETURN_TO_BED_DOOR_OPEN_TICKS = WORLDZERO_RETURN_TO_BED_DOOR_APPROACH_START_TICKS
            + WORLDZERO_RETURN_TO_BED_FOOTSTEP_SPAN_TICKS;
    private static final int WORLDZERO_RETURN_TO_BED_CHEST_REACHED_TICKS = WORLDZERO_RETURN_TO_BED_DOOR_OPEN_TICKS
            + WORLDZERO_RETURN_TO_BED_FOOTSTEP_SPAN_TICKS;
    private static final int WORLDZERO_RETURN_TO_BED_CHEST_CLOSE_TICKS = WORLDZERO_RETURN_TO_BED_CHEST_REACHED_TICKS
            + WORLDZERO_RETURN_TO_BED_CHEST_CLOSE_DELAY_TICKS;
    private static final int WORLDZERO_RETURN_TO_BED_GLASS_BREAK_START_TICKS = WORLDZERO_RETURN_TO_BED_CHEST_CLOSE_TICKS
            + WORLDZERO_RETURN_TO_BED_FOOTSTEP_SPAN_TICKS
            + WORLDZERO_RETURN_TO_BED_GLASS_BREAK_DELAY_TICKS;
    private static final int WORLDZERO_RETURN_TO_BED_EXIT_START_TICKS = WORLDZERO_RETURN_TO_BED_GLASS_BREAK_START_TICKS
            + WORLDZERO_RETURN_TO_BED_GLASS_BREAK_DURATION_TICKS;
    private static final int WORLDZERO_RETURN_TO_BED_DOOR_CLOSE_TICKS = WORLDZERO_RETURN_TO_BED_EXIT_START_TICKS
            + WORLDZERO_RETURN_TO_BED_FOOTSTEP_SPAN_TICKS;
    private static final int WORLDZERO_RETURN_TO_BED_SLEEP_START_TICKS = WORLDZERO_RETURN_TO_BED_DOOR_CLOSE_TICKS + 4;
    private static final int WORLDZERO_RETURN_TO_BED_TICKS = WORLDZERO_RETURN_TO_BED_SLEEP_START_TICKS + WORLDZERO_KORIDOR_SLEEP_FADE_TICKS;
    private static final int WORLDZERO_RETURN_TO_BED_GLASS_BREAKER_ID = 193204001;
    private static final int WORLDZERO_CAMERA_ALIGN_TIMEOUT_TICKS = 15 * 20;
    private static final int WORLDZERO_SLEEP_START_MAX_TICKS = 40;
    private static final int WORLDZERO_KORIDOR_SLEEP_COUNT_BEFORE_TRIGGER = 0;
    private static final int WORLDZERO_BED_SEARCH_RADIUS = 12;
    private static final double WORLDZERO_BLACK_ECHO_FRONT_DISTANCE_BLOCKS = 3.0D;
    private static final double WORLDZERO_BLACK_ECHO_WATCH_MIN_DISTANCE_BLOCKS = 10.0D;
    private static final double WORLDZERO_BLACK_ECHO_WATCH_MAX_DISTANCE_BLOCKS = 14.0D;
    private static final double WORLDZERO_BED_ECHO_LENGTH_OFFSET_BLOCKS = 0.80D;
    private static final double WORLDZERO_BED_ECHO_HEIGHT_BLOCKS = 0.76D;
    private static final int WORLDZERO_BUSY_BED_MESSAGE_COOLDOWN_TICKS = 5 * 20;
    private static final Component WORLDZERO_BUSY_BED_MESSAGE = Component.literal(
            "Ты не можешь лечь. В кровати уже кто-то есть."
    ).withStyle(ChatFormatting.GRAY);
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

        boolean conflictingEvent = worldzero$hasConflictingEvent(server);
        ParalysisSaveData saveData = worldzero$getSaveData(level);
        long storyTicks = WorldZeroStoryTime.worldzero$getStoryTicks(level);
        if (!conflictingEvent
                && !saveData.worldzero$completed
                && saveData.worldzero$scheduledTick >= 0L
                && storyTicks >= saveData.worldzero$scheduledTick
                && level.isNight()
                && !WorldZeroAmbientSoundEvent.worldzero$isMajorEventStartBlocked(level)) {
            StartTarget target = worldzero$pickSleepingTarget(level);
            if (target != null && worldzero$startEvent(level, state, saveData, target.player(), target.bedPos())) {
                saveData.worldzero$completed = true;
                saveData.setDirty();
            }
        }

        if (state.worldzero$phase == Phase.INACTIVE) {
            worldzero$tickKoridorSleepProgress(level, state, saveData);
        }
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_SESSION_STATES.remove(event.getServer());
    }

    @SubscribeEvent
    public static void worldzero$onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        SessionState state = worldzero$getRestrictedState(player);
        if (state == null) {
            return;
        }

        event.setCanceled(true);
        BlockPos bedPos = worldzero$resolveBedBase(player.serverLevel(), event.getPos());
        if (bedPos != null && bedPos.equals(state.worldzero$bedPos)) {
            worldzero$sendBusyBedMessage(player, state);
        }
    }

    @SubscribeEvent
    public static void worldzero$onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && worldzero$getRestrictedState(player) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onEntityInteract(EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        SessionState state = worldzero$getRestrictedState(player);
        if (state == null) {
            return;
        }

        event.setCanceled(true);
        if (worldzero$isBedEchoTarget(state, event.getTarget())) {
            worldzero$sendBusyBedMessage(player, state);
        }
    }

    @SubscribeEvent
    public static void worldzero$onEntityInteractSpecific(EntityInteractSpecific event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        SessionState state = worldzero$getRestrictedState(player);
        if (state == null) {
            return;
        }

        event.setCanceled(true);
        if (worldzero$isBedEchoTarget(state, event.getTarget())) {
            worldzero$sendBusyBedMessage(player, state);
        }
    }

    @SubscribeEvent
    public static void worldzero$onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && worldzero$getRestrictedState(player) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && worldzero$getRestrictedState(player) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && worldzero$getRestrictedState(player) != null) {
            event.setCanceled(true);
        }
    }

    public static void worldzero$scheduleAfterFall(ServerPlayer player) {
        if (player == null || player.level().dimension() != Level.OVERWORLD) {
            return;
        }

        ParalysisSaveData saveData = worldzero$getSaveData(player.serverLevel());
        if (saveData.worldzero$completed) {
            return;
        }

        saveData.worldzero$scheduledTick = WorldZeroStoryTime.worldzero$getStoryTicks(player.serverLevel())
                + WORLDZERO_DELAY_AFTER_FALL_TICKS;
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

    public static boolean worldzero$stopParalysisNow(MinecraftServer server) {
        if (server == null) {
            return false;
        }

        SessionState state = WORLDZERO_SESSION_STATES.get(server);
        if (state == null || state.worldzero$phase == Phase.INACTIVE) {
            return false;
        }

        ServerPlayer targetPlayer = server.getPlayerList().getPlayer(state.worldzero$targetPlayerId);
        worldzero$clearState(server, state, targetPlayer);
        return true;
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
        WorldZeroHouseDetector.DetectedHouse containingHouse = WorldZeroHouseDetector.worldzero$findContainingHouse(player);
        if (containingHouse != null && containingHouse.doorPos() != null) {
            BlockPos openedDoorPos = worldzero$openSilentDoor(level, containingHouse.doorPos());
            if (openedDoorPos != null) {
                state.worldzero$openedDoorPos = openedDoorPos;
            }
        }
        boolean sleepingAtStart = player.isSleeping();
        if (sleepingAtStart) {
            player.stopSleepInBed(false, true);
            float wakeYaw = worldzero$wakeAwayFromBedYaw(level, bedPos, player);
            float wakePitch = 0.0F;
            player.setYRot(wakeYaw);
            player.setYHeadRot(wakeYaw);
            player.setYBodyRot(wakeYaw);
            player.setXRot(wakePitch);
            player.yRotO = wakeYaw;
            player.xRotO = wakePitch;
        }

        state.worldzero$lockedX = player.getX();
        state.worldzero$lockedY = player.getY();
        state.worldzero$lockedZ = player.getZ();
        state.worldzero$lockedYaw = player.getYRot();
        state.worldzero$lockedPitch = player.getXRot();
        state.worldzero$phase = Phase.INITIAL_FREEZE;
        state.worldzero$phaseStartTick = level.getGameTime();
        state.worldzero$phaseEndTick = level.getGameTime() + WORLDZERO_INITIAL_FREEZE_TICKS;
        state.worldzero$echoId = null;
        state.worldzero$blackEchoId = null;
        state.worldzero$watchBlackEchoId = null;
        WorldZeroAmbientSoundEvent.worldzero$notifyMajorEventStarted(level);
        WorldZeroNetwork.sendFreezeStart(
                player,
                WORLDZERO_INITIAL_FREEZE_TICKS,
                -1,
                state.worldzero$lockedYaw,
                state.worldzero$lockedPitch
        );
        WorldZeroNetwork.sendParalysisClientAction(
                player,
                WorldZeroParalysisClientPacket.WORLDZERO_ACTION_PLAY_CREAK,
                BlockPos.ZERO,
                -1,
                0
        );

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
        Entity distantBlackEcho = worldzero$spawnDistantBlackEcho(level, player);
        state.worldzero$watchBlackEchoId = distantBlackEcho != null ? distantBlackEcho.getUUID() : null;
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

        Entity watchBlackEcho = worldzero$findEntity(level.getServer(), state.worldzero$watchBlackEchoId);
        if (watchBlackEcho != null) {
            watchBlackEcho.discard();
        }
        state.worldzero$watchBlackEchoId = null;
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
        if (state.worldzero$openedDoorPos != null) {
            worldzero$closeSilentDoor(level, state.worldzero$openedDoorPos);
            state.worldzero$returnDoorOpened = false;
        }
        state.worldzero$returnGlassBroken = false;
        state.worldzero$returnSleepStarted = false;
        state.worldzero$returnDiamondPlaced = false;
        state.worldzero$returnChestPos = worldzero$findNearestChest(level, state.worldzero$bedPos);
        state.worldzero$returnGlassPos = worldzero$findNearestGlass(level, state.worldzero$bedPos);
        worldzero$holdPlayerInBedPose(level, state, player);
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
        long elapsedTicks = level.getGameTime() - state.worldzero$phaseStartTick;
        if (elapsedTicks < WORLDZERO_RETURN_TO_BED_SLEEP_START_TICKS) {
            worldzero$holdPlayerInBedPose(level, state, player);
        } else {
            worldzero$tryReturnPlayerToBed(level, state, player);
            state.worldzero$returnSleepStarted = state.worldzero$returnSleepStarted || player.isSleeping();
        }

        if (!state.worldzero$returnDoorOpened
                && elapsedTicks >= WORLDZERO_RETURN_TO_BED_DOOR_OPEN_TICKS
                && state.worldzero$openedDoorPos != null
                && worldzero$openSilentDoor(level, state.worldzero$openedDoorPos) != null) {
            state.worldzero$returnDoorOpened = true;
        }

        if (!state.worldzero$returnDiamondPlaced
                && elapsedTicks >= WORLDZERO_RETURN_TO_BED_CHEST_REACHED_TICKS
                && worldzero$insertDiamondIntoChest(level, state.worldzero$returnChestPos)) {
            state.worldzero$returnDiamondPlaced = true;
        }

        if (state.worldzero$returnDoorOpened
                && elapsedTicks >= WORLDZERO_RETURN_TO_BED_DOOR_CLOSE_TICKS
                && state.worldzero$openedDoorPos != null) {
            worldzero$closeSilentDoor(level, state.worldzero$openedDoorPos);
            state.worldzero$openedDoorPos = null;
            state.worldzero$returnDoorOpened = false;
        }

        worldzero$updateReturnToBedGlassVisual(level, state, elapsedTicks);

        if (level.getGameTime() < state.worldzero$phaseEndTick) {
            return;
        }

        if (state.worldzero$openedDoorPos != null) {
            worldzero$closeSilentDoor(level, state.worldzero$openedDoorPos);
            state.worldzero$openedDoorPos = null;
        }
        worldzero$setMorning(level);
        if (player.isSleeping()) {
            player.stopSleepInBed(false, true);
        }

        WorldZeroAdvancementTriggers.grantParalysis(player);
        worldzero$unlockKoridorDream(level, player);

        worldzero$clearState(level.getServer(), state, player);
    }

    private static void worldzero$updateReturnToBedGlassVisual(ServerLevel level, SessionState state, long elapsedTicks) {
        BlockPos glassPos = state.worldzero$returnGlassPos;
        if (glassPos == null) {
            return;
        }

        if (!worldzero$isGlassLikeBlock(level.getBlockState(glassPos))) {
            level.destroyBlockProgress(WORLDZERO_RETURN_TO_BED_GLASS_BREAKER_ID, glassPos, -1);
            state.worldzero$returnGlassPos = null;
            state.worldzero$returnGlassBroken = true;
            return;
        }

        long crackTicks = elapsedTicks - WORLDZERO_RETURN_TO_BED_GLASS_BREAK_START_TICKS;
        if (crackTicks < 0L) {
            return;
        }

        if (crackTicks >= WORLDZERO_RETURN_TO_BED_GLASS_BREAK_DURATION_TICKS) {
            level.destroyBlockProgress(WORLDZERO_RETURN_TO_BED_GLASS_BREAKER_ID, glassPos, -1);
            state.worldzero$returnGlassBroken = true;
            return;
        }

        int stage = Mth.clamp(
                (int) (((crackTicks + 1L) * 10L) / WORLDZERO_RETURN_TO_BED_GLASS_BREAK_DURATION_TICKS) - 1,
                0,
                9
        );
        level.destroyBlockProgress(WORLDZERO_RETURN_TO_BED_GLASS_BREAKER_ID, glassPos, stage);
    }

    private static void worldzero$tickKoridorSleepProgress(
            ServerLevel level,
            SessionState state,
            ParalysisSaveData saveData
    ) {
        if (worldzero$hasConflictingEvent(level.getServer())) {
            return;
        }

        if (saveData.worldzero$postParalysisSleepCounts.isEmpty()) {
            state.worldzero$koridorSleepTrackers.clear();
            return;
        }

        for (ServerPlayer player : new ArrayList<>(level.players())) {
            UUID playerId = player.getUUID();
            int sleepCount = saveData.worldzero$postParalysisSleepCounts.getOrDefault(playerId, -1);
            KoridorSleepTracker tracker = state.worldzero$koridorSleepTrackers.computeIfAbsent(
                    playerId,
                    ignored -> new KoridorSleepTracker()
            );

            if (sleepCount < 0 || !player.isAlive() || player.isSpectator()) {
                tracker.worldzero$reset();
                continue;
            }

            if (!player.isSleeping()) {
                tracker.worldzero$reset();
                continue;
            }

            if (!tracker.worldzero$currentSleepTracked && player.getSleepTimer() > 0) {
                tracker.worldzero$currentSleepTracked = true;
                if (sleepCount >= WORLDZERO_KORIDOR_SLEEP_COUNT_BEFORE_TRIGGER) {
                    tracker.worldzero$koridorTeleportPending = true;
                } else {
                    saveData.worldzero$postParalysisSleepCounts.put(playerId, sleepCount + 1);
                    saveData.setDirty();
                }
            }

            if (tracker.worldzero$koridorTeleportPending
                    && player.getSleepTimer() >= WORLDZERO_KORIDOR_SLEEP_FADE_TICKS
                    && WorldZeroKoridorDimension.worldzero$startSleepDream(player)) {
                saveData.worldzero$postParalysisSleepCounts.remove(playerId);
                saveData.setDirty();
                tracker.worldzero$reset();
            }
        }

        state.worldzero$koridorSleepTrackers.entrySet().removeIf(
                entry -> !saveData.worldzero$postParalysisSleepCounts.containsKey(entry.getKey())
        );
    }

    private static void worldzero$unlockKoridorDream(ServerLevel level, ServerPlayer player) {
        if (player == null) {
            return;
        }

        ParalysisSaveData saveData = worldzero$getSaveData(level);
        saveData.worldzero$postParalysisSleepCounts.put(player.getUUID(), 0);
        saveData.setDirty();
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

    private static void worldzero$holdPlayerInBedPose(ServerLevel level, SessionState state, ServerPlayer player) {
        Vec3 bedPos = worldzero$bedEchoBasePos(level, state.worldzero$bedPos);
        BlockState bedState = level.getBlockState(state.worldzero$bedPos);
        Direction bedFacing = bedState.hasProperty(BedBlock.FACING) ? bedState.getValue(BedBlock.FACING) : Direction.SOUTH;
        float bedYaw = bedFacing.toYRot();

        player.teleportTo(bedPos.x, Math.max(level.getMinBuildHeight(), state.worldzero$bedPos.getY() + 0.2D), bedPos.z);
        player.setSleepingPos(state.worldzero$bedPos);
        player.setPose(Pose.SLEEPING);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.setYRot(bedYaw);
        player.setYHeadRot(bedYaw);
        player.setYBodyRot(bedYaw);
        player.setXRot(0.0F);
        player.fallDistance = 0.0F;
    }

    private static float worldzero$wakeAwayFromBedYaw(ServerLevel level, BlockPos bedPos, ServerPlayer player) {
        Vec3 bedCenter = Vec3.atCenterOf(bedPos);
        double deltaX = player.getX() - bedCenter.x;
        double deltaZ = player.getZ() - bedCenter.z;
        if (deltaX * deltaX + deltaZ * deltaZ < 0.0001D) {
            BlockState bedState = level.getBlockState(bedPos);
            Direction bedFacing = bedState.hasProperty(BedBlock.FACING)
                    ? bedState.getValue(BedBlock.FACING)
                    : Direction.SOUTH;
            return bedFacing.getOpposite().toYRot();
        }

        return (float) (Mth.atan2(deltaZ, deltaX) * (180.0D / Math.PI)) - 90.0F;
    }

    @Nullable
    private static SessionState worldzero$getRestrictedState(ServerPlayer player) {
        if (player == null || player.level().isClientSide()) {
            return null;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return null;
        }

        SessionState state = WORLDZERO_SESSION_STATES.get(server);
        if (state == null
                || state.worldzero$targetPlayerId == null
                || !state.worldzero$targetPlayerId.equals(player.getUUID())) {
            return null;
        }

        return switch (state.worldzero$phase) {
            case CAMERA_ALIGN, BED_WATCH, BREATH_ONLY, RETURN_TO_BED -> state;
            default -> null;
        };
    }

    private static void worldzero$sendBusyBedMessage(ServerPlayer player, SessionState state) {
        long gameTime = player.serverLevel().getGameTime();
        if (gameTime - state.worldzero$lastBusyBedMessageTick < WORLDZERO_BUSY_BED_MESSAGE_COOLDOWN_TICKS) {
            return;
        }

        state.worldzero$lastBusyBedMessageTick = gameTime;
        player.displayClientMessage(worldzero$busyBedMessage(), true);
    }

    private static Component worldzero$busyBedMessage() {
        return Component.translatable("message.worldzero.paralysis.bed_occupied");
    }

    private static boolean worldzero$isBedEchoTarget(SessionState state, Entity target) {
        return state.worldzero$echoId != null
                && target != null
                && state.worldzero$echoId.equals(target.getUUID());
    }

    @Nullable
    private static BlockPos worldzero$openSilentDoor(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.DOORS) || !state.hasProperty(DoorBlock.HALF) || !state.hasProperty(DoorBlock.OPEN)) {
            return null;
        }

        BlockPos lowerPos = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        BlockPos upperPos = lowerPos.above();
        BlockState lowerState = level.getBlockState(lowerPos);
        BlockState upperState = level.getBlockState(upperPos);
        if (!lowerState.is(BlockTags.DOORS)
                || !upperState.is(BlockTags.DOORS)
                || !lowerState.hasProperty(DoorBlock.OPEN)
                || !upperState.hasProperty(DoorBlock.OPEN)) {
            return null;
        }

        if (!lowerState.getValue(DoorBlock.OPEN)) {
            level.setBlock(lowerPos, lowerState.setValue(DoorBlock.OPEN, true), 18);
            level.setBlock(upperPos, upperState.setValue(DoorBlock.OPEN, true), 18);
        }

        return lowerPos.immutable();
    }

    private static void worldzero$closeSilentDoor(ServerLevel level, BlockPos lowerPos) {
        BlockState lowerState = level.getBlockState(lowerPos);
        BlockState upperState = level.getBlockState(lowerPos.above());
        if (!lowerState.is(BlockTags.DOORS)
                || !upperState.is(BlockTags.DOORS)
                || !lowerState.hasProperty(DoorBlock.OPEN)
                || !upperState.hasProperty(DoorBlock.OPEN)) {
            return;
        }

        if (lowerState.getValue(DoorBlock.OPEN)) {
            level.setBlock(lowerPos, lowerState.setValue(DoorBlock.OPEN, false), 18);
            level.setBlock(lowerPos.above(), upperState.setValue(DoorBlock.OPEN, false), 18);
        }
    }

    @Nullable
    private static BlockPos worldzero$findNearestGlass(ServerLevel level, BlockPos center) {
        if (center == null) {
            return null;
        }

        BlockPos bestPos = null;
        double bestDistanceSqr = Double.MAX_VALUE;
        for (int x = center.getX() - WORLDZERO_BED_SEARCH_RADIUS; x <= center.getX() + WORLDZERO_BED_SEARCH_RADIUS; x++) {
            for (int y = center.getY() - 4; y <= center.getY() + 4; y++) {
                for (int z = center.getZ() - WORLDZERO_BED_SEARCH_RADIUS; z <= center.getZ() + WORLDZERO_BED_SEARCH_RADIUS; z++) {
                    BlockPos candidatePos = new BlockPos(x, y, z);
                    BlockState candidateState = level.getBlockState(candidatePos);
                    if (!worldzero$isGlassLikeBlock(candidateState)) {
                        continue;
                    }

                    double distanceSqr = center.distSqr(candidatePos);
                    if (distanceSqr < bestDistanceSqr) {
                        bestDistanceSqr = distanceSqr;
                        bestPos = candidatePos.immutable();
                    }
                }
            }
        }

        return bestPos;
    }

    @Nullable
    private static BlockPos worldzero$findNearestChest(ServerLevel level, @Nullable BlockPos center) {
        if (center == null) {
            return null;
        }

        BlockPos bestPos = null;
        double bestDistanceSqr = Double.MAX_VALUE;
        for (int x = center.getX() - WORLDZERO_BED_SEARCH_RADIUS; x <= center.getX() + WORLDZERO_BED_SEARCH_RADIUS; x++) {
            for (int y = center.getY() - 4; y <= center.getY() + 4; y++) {
                for (int z = center.getZ() - WORLDZERO_BED_SEARCH_RADIUS; z <= center.getZ() + WORLDZERO_BED_SEARCH_RADIUS; z++) {
                    BlockPos candidatePos = new BlockPos(x, y, z);
                    BlockState candidateState = level.getBlockState(candidatePos);
                    if (!worldzero$isChestLikeBlock(candidateState)) {
                        continue;
                    }

                    double distanceSqr = center.distSqr(candidatePos);
                    if (distanceSqr < bestDistanceSqr) {
                        bestDistanceSqr = distanceSqr;
                        bestPos = candidatePos.immutable();
                    }
                }
            }
        }

        return bestPos;
    }

    private static boolean worldzero$isGlassLikeBlock(BlockState state) {
        if (state.isAir()) {
            return false;
        }

        String blockPath = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return blockPath.contains("glass") || blockPath.contains("pane");
    }

    private static boolean worldzero$isChestLikeBlock(BlockState state) {
        if (state.isAir()) {
            return false;
        }

        String blockPath = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return blockPath.contains("chest") && !blockPath.contains("ender_chest");
    }

    private static boolean worldzero$insertDiamondIntoChest(ServerLevel level, @Nullable BlockPos chestPos) {
        if (chestPos == null) {
            return false;
        }

        if (!(level.getBlockEntity(chestPos) instanceof Container container)) {
            return false;
        }

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.is(Items.DIAMOND)) {
                continue;
            }

            int maxCount = Math.min(stack.getMaxStackSize(), container.getMaxStackSize());
            if (stack.getCount() >= maxCount) {
                continue;
            }

            stack.grow(1);
            container.setChanged();
            return true;
        }

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (!container.getItem(slot).isEmpty()) {
                continue;
            }

            container.setItem(slot, new ItemStack(Items.DIAMOND, 1));
            container.setChanged();
            return true;
        }

        return false;
    }

    @Nullable
    private static Entity worldzero$spawnDistantBlackEcho(ServerLevel level, ServerPlayer player) {
        Vec3 center = player.position();
        int playerFeetY = Mth.floor(player.getY());
        double startAngle = level.random.nextDouble() * (Math.PI * 2.0D);

        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = startAngle + ((Math.PI * 2.0D) / 24.0D) * attempt;
            double distance = Mth.nextDouble(
                    level.random,
                    WORLDZERO_BLACK_ECHO_WATCH_MIN_DISTANCE_BLOCKS,
                    WORLDZERO_BLACK_ECHO_WATCH_MAX_DISTANCE_BLOCKS
            );
            double candidateX = center.x + Math.cos(angle) * distance;
            double candidateZ = center.z + Math.sin(angle) * distance;
            int baseX = Mth.floor(candidateX);
            int baseZ = Mth.floor(candidateZ);

            for (int y = playerFeetY + 2; y >= playerFeetY - 6; y--) {
                BlockPos spawnPos = new BlockPos(baseX, y, baseZ);
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
                blackEcho.setDeltaMovement(0.0D, 0.0D, 0.0D);
                blackEcho.worldzero$setParalysisBedActive(true);
                level.addFreshEntity(blackEcho);
                return blackEcho;
            }
        }

        return null;
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

        Entity watchBlackEcho = worldzero$findEntity(server, state.worldzero$watchBlackEchoId);
        if (watchBlackEcho != null) {
            watchBlackEcho.discard();
        }

        if (state.worldzero$openedDoorPos != null) {
            ServerLevel level = server.getLevel(Level.OVERWORLD);
            if (level != null) {
                worldzero$closeSilentDoor(level, state.worldzero$openedDoorPos);
            }
        }

        if (player != null) {
            if (!player.isSleeping()) {
                player.clearSleepingPos();
                player.setPose(Pose.STANDING);
            }
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
        state.worldzero$watchBlackEchoId = null;
        state.worldzero$openedDoorPos = null;
        state.worldzero$phaseStartTick = -1L;
        state.worldzero$phaseEndTick = -1L;
        state.worldzero$lastBusyBedMessageTick = Long.MIN_VALUE;
        state.worldzero$returnDoorOpened = false;
        state.worldzero$returnGlassBroken = false;
        state.worldzero$returnSleepStarted = false;
        state.worldzero$returnDiamondPlaced = false;
        state.worldzero$returnChestPos = null;
        if (state.worldzero$returnGlassPos != null) {
            ServerLevel level = server.getLevel(Level.OVERWORLD);
            if (level != null) {
                level.destroyBlockProgress(WORLDZERO_RETURN_TO_BED_GLASS_BREAKER_ID, state.worldzero$returnGlassPos, -1);
            }
        }
        state.worldzero$returnGlassPos = null;
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
        private UUID worldzero$watchBlackEchoId;
        private BlockPos worldzero$openedDoorPos;
        private long worldzero$phaseStartTick = -1L;
        private long worldzero$phaseEndTick = -1L;
        private long worldzero$lastBusyBedMessageTick = Long.MIN_VALUE;
        private boolean worldzero$returnDoorOpened;
        private boolean worldzero$returnGlassBroken;
        private boolean worldzero$returnSleepStarted;
        private boolean worldzero$returnDiamondPlaced;
        private BlockPos worldzero$returnChestPos;
        private BlockPos worldzero$returnGlassPos;
        private double worldzero$lockedX;
        private double worldzero$lockedY;
        private double worldzero$lockedZ;
        private float worldzero$lockedYaw;
        private float worldzero$lockedPitch;
        private final Map<UUID, KoridorSleepTracker> worldzero$koridorSleepTrackers = new HashMap<>();
    }

    private static final class KoridorSleepTracker {
        private boolean worldzero$currentSleepTracked;
        private boolean worldzero$koridorTeleportPending;

        private void worldzero$reset() {
            this.worldzero$currentSleepTracked = false;
            this.worldzero$koridorTeleportPending = false;
        }
    }

    private static final class ParalysisSaveData extends SavedData {
        private long worldzero$scheduledTick = -1L;
        private boolean worldzero$completed;
        private final Map<UUID, Integer> worldzero$postParalysisSleepCounts = new HashMap<>();

        public static ParalysisSaveData load(CompoundTag tag) {
            ParalysisSaveData saveData = new ParalysisSaveData();
            saveData.worldzero$scheduledTick = tag.getLong("scheduled_tick");
            saveData.worldzero$completed = tag.getBoolean("completed");
            CompoundTag sleepCountsTag = tag.getCompound("post_paralysis_sleep_counts");
            for (String key : sleepCountsTag.getAllKeys()) {
                try {
                    saveData.worldzero$postParalysisSleepCounts.put(
                            UUID.fromString(key),
                            sleepCountsTag.getInt(key)
                    );
                } catch (IllegalArgumentException ignored) {
                }
            }
            return saveData;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putLong("scheduled_tick", this.worldzero$scheduledTick);
            tag.putBoolean("completed", this.worldzero$completed);
            CompoundTag sleepCountsTag = new CompoundTag();
            for (Map.Entry<UUID, Integer> entry : this.worldzero$postParalysisSleepCounts.entrySet()) {
                sleepCountsTag.putInt(entry.getKey().toString(), entry.getValue());
            }
            tag.put("post_paralysis_sleep_counts", sleepCountsTag);
            return tag;
        }
    }

    private record StartTarget(ServerPlayer player, BlockPos bedPos) {
    }
}
