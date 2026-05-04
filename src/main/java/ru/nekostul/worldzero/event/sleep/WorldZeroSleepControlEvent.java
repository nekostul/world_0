package ru.nekostul.worldzero.event.sleep;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;
import ru.nekostul.worldzero.dimension.WorldZeroHouseBadDimension;
import ru.nekostul.worldzero.dimension.WorldZeroHouseDimension;
import ru.nekostul.worldzero.dimension.WorldZeroKoridorDimension;
import ru.nekostul.worldzero.event.WorldZeroStoryTime;
import ru.nekostul.worldzero.event.chat.WorldZeroDoubleChatEvent;
import ru.nekostul.worldzero.event.fall.WorldZeroFallEvent;
import ru.nekostul.worldzero.event.footsteps.WorldZeroFootstepsEvent;
import ru.nekostul.worldzero.event.freeze.WorldZeroFreezeEvent;
import ru.nekostul.worldzero.event.house.WorldZeroHouseEvent;
import ru.nekostul.worldzero.event.paralysis.WorldZeroParalysisEvent;
import ru.nekostul.worldzero.network.WorldZeroNetwork;
import ru.nekostul.worldzero.event.skywatch.WorldZeroSkyWatchPacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroSleepControlEvent {
    private static final String WORLDZERO_SAVE_ID = "worldzero_sleep_control_event";
    private static final long WORLDZERO_TICKS_PER_MINUTE = 20L * 60L;
    private static final long WORLDZERO_CONTROL_START_TICKS = 30L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_SPECIAL_PRIORITY_TICKS = 120L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_SPECIAL_FORCE_TICKS = 150L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_CAMPAIGN_END_TICKS = 180L * WORLDZERO_TICKS_PER_MINUTE;
    private static final int WORLDZERO_MAX_SLEEPS = 6;
    private static final int WORLDZERO_PRESSURE_KEYBOARD_BLOCK_TICKS = 30;
    private static final int WORLDZERO_PRESSURE_SKYWATCH_DURATION_TICKS = 20 * 60 * 20;
    private static final long WORLDZERO_CANNOT_SLEEP_LINE_COOLDOWN = 5L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_PRESSURE_LINE_COOLDOWN = 2L * WORLDZERO_TICKS_PER_MINUTE;

    private static final String WORLDZERO_CAN_SLEEP_KEY = "message.worldzero.sleep_control.can_sleep";
    private static final String WORLDZERO_CANNOT_SLEEP_KEY = "message.worldzero.sleep_control.cannot_sleep";
    private static final String WORLDZERO_PRESSURE_KEY = "message.worldzero.sleep_control.pressure";
    private static final String WORLDZERO_BED_BLOCKED_KEY = "message.worldzero.paralysis.bed_occupied";

    private static final Map<MinecraftServer, SessionState> WORLDZERO_SESSION_STATES = new WeakHashMap<>();

    private WorldZeroSleepControlEvent() {
    }

    public static boolean worldzero$isManagingSleep(@Nullable MinecraftServer server) {
        return server != null;
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_SESSION_STATES.remove(event.getServer());
    }

    @SubscribeEvent
    public static void worldzero$onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.level instanceof ServerLevel level)
                || level.isClientSide()
                || level.dimension() != Level.OVERWORLD) {
            return;
        }

        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }

        SleepControlSaveData saveData = worldzero$getSaveData(level);
        SessionState sessionState = WORLDZERO_SESSION_STATES.computeIfAbsent(server, ignored -> new SessionState());
        long storyTicks = WorldZeroStoryTime.worldzero$getStoryTicks(level);

        for (ServerPlayer player : new ArrayList<>(level.players())) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }

            PlayerState state = saveData.worldzero$players.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
            if (worldzero$tickPlayer(level, player, state, sessionState, storyTicks)) {
                saveData.setDirty();
            }
        }
    }

    @SubscribeEvent
    public static void worldzero$onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD
                || player.isSpectator()) {
            return;
        }

        BlockPos bedPos = worldzero$resolveBedBase(level, event.getPos());
        if (bedPos == null) {
            return;
        }

        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }

        SleepControlSaveData saveData = worldzero$getSaveData(level);
        SessionState sessionState = WORLDZERO_SESSION_STATES.computeIfAbsent(server, ignored -> new SessionState());
        PlayerState state = saveData.worldzero$players.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
        long gameTime = level.getGameTime();
        long storyTicks = WorldZeroStoryTime.worldzero$getStoryTicks(level);

        if (state.worldzero$lastBedAttemptGameTime == gameTime) {
            event.setCanceled(true);
            return;
        }
        state.worldzero$lastBedAttemptGameTime = gameTime;

        boolean forceSleep = state.worldzero$skippedAvailableNights >= 3;
        boolean canSleepNow = worldzero$canSleepNow(level, player, state, storyTicks, true) || forceSleep;
        if (!canSleepNow) {
            event.setCanceled(true);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(WORLDZERO_BED_BLOCKED_KEY), true);
            if (storyTicks - state.worldzero$lastCannotSleepLineTick >= WORLDZERO_CANNOT_SLEEP_LINE_COOLDOWN
                    && level.random.nextInt(8) == 0) {
                WorldZeroDoubleChatEvent.worldzero$sendSpeakerLineNow(player, WORLDZERO_CANNOT_SLEEP_KEY);
                state.worldzero$lastCannotSleepLineTick = storyTicks;
            }
            saveData.setDirty();
            return;
        }

        SleepAction action = worldzero$pickSleepAction(level, player, state, storyTicks);
        boolean success = worldzero$executeSleepAction(level, player, bedPos, state, action, storyTicks, forceSleep);
        event.setCanceled(true);

        if (!success) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(WORLDZERO_BED_BLOCKED_KEY), true);
            return;
        }

        if (action == SleepAction.PARALYSIS) {
            sessionState.worldzero$paralysisByPlayer.put(player.getUUID(), true);
        }
        saveData.setDirty();
    }

    private static boolean worldzero$tickPlayer(
            ServerLevel level,
            ServerPlayer player,
            PlayerState state,
            SessionState sessionState,
            long storyTicks
    ) {
        boolean changed = false;
        long dayIndex = Math.floorDiv(level.getDayTime(), 24000L);
        boolean isNight = level.isNight();

        if (state.worldzero$nightInitialized && state.worldzero$nightActive && !isNight) {
            if (!state.worldzero$sleptThisNight) {
                state.worldzero$nightsSinceLastSleep++;
            } else {
                state.worldzero$skippedAvailableNights = 0;
            }
            if (state.worldzero$nightWasAvailable && !state.worldzero$sleptThisNight) {
                state.worldzero$skippedAvailableNights++;
            }
            state.worldzero$nightActive = false;
            state.worldzero$canSleepAnnouncedThisNight = false;
            state.worldzero$nightWasAvailable = false;
            changed = true;
        }

        if (!state.worldzero$nightInitialized || (!state.worldzero$nightActive && isNight && state.worldzero$nightDayIndex != dayIndex)) {
            state.worldzero$nightInitialized = true;
            state.worldzero$nightActive = true;
            state.worldzero$nightDayIndex = dayIndex;
            state.worldzero$sleptThisNight = false;
            state.worldzero$nightWasAvailable = worldzero$canSleepNow(level, player, state, storyTicks, false);
            state.worldzero$canSleepAnnouncedThisNight = false;
            changed = true;
        }

        if (state.worldzero$awaitingHouseReturn && player.serverLevel().dimension() == Level.OVERWORLD) {
            if (!WorldZeroHouseDimension.worldzero$hasPendingRestorationDream(player)) {
                WorldZeroHouseDimension.worldzero$prepareRestorationDream(player);
            }
            if (WorldZeroHouseDimension.worldzero$hasPendingRestorationDream(player)) {
                state.worldzero$restorationSleepPending = true;
                state.worldzero$awaitingHouseReturn = false;
                changed = true;
            }
        }

        if (state.worldzero$restorationSleepPending && !WorldZeroHouseDimension.worldzero$hasPendingRestorationDream(player)) {
            state.worldzero$restorationSleepPending = false;
            changed = true;
        }

        if (state.worldzero$nightActive) {
            boolean canSleepNow = worldzero$canSleepNow(level, player, state, storyTicks, false);
            if (canSleepNow && !state.worldzero$canSleepAnnouncedThisNight) {
                if (WorldZeroDoubleChatEvent.worldzero$sendSpeakerLineNow(player, WORLDZERO_CAN_SLEEP_KEY)) {
                    state.worldzero$canSleepAnnouncedThisNight = true;
                    state.worldzero$nightWasAvailable = true;
                    changed = true;
                }
            }
        }

        if (state.worldzero$skippedAvailableNights >= 2
                && storyTicks - state.worldzero$lastPressureLineTick >= WORLDZERO_PRESSURE_LINE_COOLDOWN) {
            if (WorldZeroDoubleChatEvent.worldzero$sendSpeakerLineNow(player, WORLDZERO_PRESSURE_KEY)) {
                state.worldzero$lastPressureLineTick = storyTicks;
                changed = true;
            }
        }

        boolean shouldKeepPressureSkyWatch = state.worldzero$skippedAvailableNights >= 2 && level.isNight();
        if (player.isSleeping()) {
            shouldKeepPressureSkyWatch = false;
        }

        if (shouldKeepPressureSkyWatch && !state.worldzero$pressureSkyWatchActive) {
            worldzero$startPressureSkyWatch(player, level);
            state.worldzero$pressureSkyWatchActive = true;
            changed = true;
        } else if (!shouldKeepPressureSkyWatch && state.worldzero$pressureSkyWatchActive) {
            worldzero$clearPressureSkyWatch(player);
            state.worldzero$pressureSkyWatchActive = false;
            changed = true;
        }

        if (state.worldzero$skippedAvailableNights >= 3 && level.isNight() && storyTicks >= state.worldzero$nextPressureControlTick) {
            WorldZeroNetwork.sendKeyboardBlock(player, WORLDZERO_PRESSURE_KEYBOARD_BLOCK_TICKS);
            state.worldzero$nextPressureControlTick = storyTicks + 20L * 25L;
            changed = true;
        }

        boolean paralysisActive = WorldZeroParalysisEvent.worldzero$isParalysisActive(level.getServer());
        boolean trackedParalysis = sessionState.worldzero$paralysisByPlayer.getOrDefault(player.getUUID(), false);
        if (trackedParalysis && !paralysisActive) {
            sessionState.worldzero$paralysisByPlayer.remove(player.getUUID());
            changed = true;
        }

        return changed;
    }

    private static boolean worldzero$canSleepNow(
            ServerLevel level,
            ServerPlayer player,
            PlayerState state,
            long storyTicks,
            boolean ignoreNightCheck
    ) {
        if (state.worldzero$sleepsUsed >= WORLDZERO_MAX_SLEEPS) {
            return false;
        }

        if (!ignoreNightCheck && !level.isNight()) {
            return false;
        }

        if (worldzero$hasConflictingEvent(level.getServer())) {
            return false;
        }

        if (state.worldzero$restorationSleepPending || WorldZeroHouseDimension.worldzero$hasPendingRestorationDream(player)) {
            return true;
        }

        if (storyTicks >= WORLDZERO_CAMPAIGN_END_TICKS
                && (state.worldzero$specialSleepIndex < 3 || !state.worldzero$paralysisTriggered)) {
            return true;
        }

        if (storyTicks >= WORLDZERO_SPECIAL_FORCE_TICKS && state.worldzero$specialSleepIndex < 3) {
            return true;
        }

        if (!worldzero$isControlEnabled(storyTicks, state)) {
            return true;
        }

        return state.worldzero$nightsSinceLastSleep >= state.worldzero$requiredNightsBeforeNextSleep;
    }

    private static boolean worldzero$isControlEnabled(long storyTicks, PlayerState state) {
        return storyTicks >= WORLDZERO_CONTROL_START_TICKS || state.worldzero$sleepsUsed >= 2;
    }

    private static SleepAction worldzero$pickSleepAction(
            ServerLevel level,
            ServerPlayer player,
            PlayerState state,
            long storyTicks
    ) {
        if (state.worldzero$restorationSleepPending || WorldZeroHouseDimension.worldzero$hasPendingRestorationDream(player)) {
            return SleepAction.RESTORATION;
        }

        boolean specialRemaining = state.worldzero$specialSleepIndex < 3;
        boolean specialHardForce = specialRemaining && storyTicks >= WORLDZERO_SPECIAL_FORCE_TICKS;
        boolean specialPriority = specialRemaining && storyTicks >= WORLDZERO_SPECIAL_PRIORITY_TICKS;
        boolean campaignHardForce = storyTicks >= WORLDZERO_CAMPAIGN_END_TICKS;
        boolean paralysisMustForce = !state.worldzero$paralysisTriggered
                && state.worldzero$specialSleepIndex > 0
                && storyTicks >= WORLDZERO_SPECIAL_PRIORITY_TICKS;

        if (campaignHardForce && !state.worldzero$paralysisTriggered && state.worldzero$specialSleepIndex > 0) {
            return SleepAction.PARALYSIS;
        }

        if (campaignHardForce) {
            SleepAction forcedSpecial = worldzero$getNextSpecialAction(state, true);
            if (forcedSpecial != SleepAction.NONE) {
                return forcedSpecial;
            }
        }

        if (state.worldzero$specialSleepIndex >= 2 && !state.worldzero$paralysisTriggered) {
            return SleepAction.PARALYSIS;
        }

        SleepAction specialAction = worldzero$getNextSpecialAction(state, specialHardForce);
        if (specialAction != SleepAction.NONE && (specialHardForce || specialPriority)) {
            return specialAction;
        }

        if (paralysisMustForce) {
            return SleepAction.PARALYSIS;
        }

        if (specialAction != SleepAction.NONE) {
            return specialAction;
        }

        if (!state.worldzero$paralysisTriggered && state.worldzero$specialSleepIndex > 0 && level.random.nextBoolean()) {
            return SleepAction.PARALYSIS;
        }

        return SleepAction.ORDINARY;
    }

    private static SleepAction worldzero$getNextSpecialAction(PlayerState state, boolean ignoreOrdinaryGate) {
        return switch (state.worldzero$specialSleepIndex) {
            case 0 -> state.worldzero$sleepsUsed >= 1 ? SleepAction.SPECIAL_HOUSE : SleepAction.NONE;
            case 1 -> (ignoreOrdinaryGate || state.worldzero$ordinarySinceLastSpecial >= 1)
                    ? SleepAction.SPECIAL_KORIDOR
                    : SleepAction.NONE;
            case 2 -> (ignoreOrdinaryGate || state.worldzero$ordinarySinceLastSpecial >= 1)
                    ? SleepAction.SPECIAL_HOUSE_BAD
                    : SleepAction.NONE;
            default -> SleepAction.NONE;
        };
    }

    private static boolean worldzero$executeSleepAction(
            ServerLevel level,
            ServerPlayer player,
            BlockPos bedPos,
            PlayerState state,
            SleepAction action,
            long storyTicks,
            boolean forceSleep
    ) {
        boolean success = switch (action) {
            case SPECIAL_HOUSE -> WorldZeroHouseDimension.worldzero$startSleepDream(player);
            case SPECIAL_KORIDOR -> WorldZeroKoridorDimension.worldzero$startSleepDream(player);
            case SPECIAL_HOUSE_BAD -> WorldZeroHouseBadDimension.worldzero$teleportPlayerToHouseBad(player);
            case RESTORATION -> WorldZeroHouseDimension.worldzero$startRestorationDream(player);
            case PARALYSIS -> WorldZeroParalysisEvent.worldzero$triggerParalysisNow(player);
            case ORDINARY -> worldzero$startOrdinarySleep(player, bedPos, forceSleep);
            default -> false;
        };
        if (!success) {
            return false;
        }

        if (action == SleepAction.RESTORATION) {
            WorldZeroHouseDimension.worldzero$consumePendingRestorationDream(player);
            state.worldzero$restorationSleepPending = false;
            state.worldzero$restorationCompleted = true;
        }

        if (action == SleepAction.SPECIAL_HOUSE) {
            state.worldzero$awaitingHouseReturn = true;
        }

        if (action == SleepAction.PARALYSIS) {
            state.worldzero$paralysisTriggered = true;
        }

        if (state.worldzero$pressureSkyWatchActive) {
            worldzero$clearPressureSkyWatch(player);
            state.worldzero$pressureSkyWatchActive = false;
        }

        worldzero$markSleepUsed(level, state, action, storyTicks);
        return true;
    }

    private static void worldzero$startPressureSkyWatch(ServerPlayer player, ServerLevel level) {
        WorldZeroNetwork.sendSkyWatch(
                player,
                WorldZeroSkyWatchPacket.WORLDZERO_ACTION_START,
                WORLDZERO_PRESSURE_SKYWATCH_DURATION_TICKS,
                level.random.nextInt()
        );
    }

    private static void worldzero$clearPressureSkyWatch(ServerPlayer player) {
        WorldZeroNetwork.sendSkyWatch(player, WorldZeroSkyWatchPacket.WORLDZERO_ACTION_CLEAR, 0, 0);
    }

    private static boolean worldzero$startOrdinarySleep(ServerPlayer player, BlockPos bedPos, boolean forceSleep) {
        player.startSleepInBed(bedPos);
        if (player.isSleeping()) {
            return true;
        }

        if (!forceSleep) {
            return false;
        }

        player.teleportTo(bedPos.getX() + 0.5D, bedPos.getY(), bedPos.getZ() + 0.5D);
        player.startSleepInBed(bedPos);
        return player.isSleeping();
    }

    private static void worldzero$markSleepUsed(
            ServerLevel level,
            PlayerState state,
            SleepAction action,
            long storyTicks
    ) {
        state.worldzero$sleepsUsed++;
        state.worldzero$sleptThisNight = true;
        state.worldzero$nightsSinceLastSleep = 0;
        state.worldzero$skippedAvailableNights = 0;
        state.worldzero$canSleepAnnouncedThisNight = true;

        if (action == SleepAction.SPECIAL_HOUSE || action == SleepAction.SPECIAL_KORIDOR || action == SleepAction.SPECIAL_HOUSE_BAD) {
            state.worldzero$specialSleepIndex = Math.min(3, state.worldzero$specialSleepIndex + 1);
            state.worldzero$ordinarySinceLastSpecial = 0;
        } else {
            state.worldzero$ordinarySinceLastSpecial++;
        }

        if (state.worldzero$sleepsUsed >= WORLDZERO_MAX_SLEEPS) {
            state.worldzero$requiredNightsBeforeNextSleep = Integer.MAX_VALUE;
            return;
        }

        if (worldzero$isControlEnabled(storyTicks, state)) {
            state.worldzero$requiredNightsBeforeNextSleep = level.random.nextInt(4) == 0 ? 3 : 2;
        } else {
            state.worldzero$requiredNightsBeforeNextSleep = 0;
        }
    }

    private static boolean worldzero$hasConflictingEvent(@Nullable MinecraftServer server) {
        if (server == null) {
            return true;
        }

        return WorldZeroFreezeEvent.worldzero$isFreezeActive(server)
                || WorldZeroFallEvent.worldzero$isFallActive(server)
                || WorldZeroFootstepsEvent.worldzero$isFootstepsActive(server)
                || WorldZeroHouseEvent.worldzero$isHouseActive(server)
                || WorldZeroParalysisEvent.worldzero$isParalysisActive(server);
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

    private static SleepControlSaveData worldzero$getSaveData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                SleepControlSaveData::worldzero$load,
                SleepControlSaveData::new,
                WORLDZERO_SAVE_ID
        );
    }

    private enum SleepAction {
        NONE,
        ORDINARY,
        RESTORATION,
        PARALYSIS,
        SPECIAL_HOUSE,
        SPECIAL_KORIDOR,
        SPECIAL_HOUSE_BAD
    }

    private static final class SessionState {
        private final Map<UUID, Boolean> worldzero$paralysisByPlayer = new HashMap<>();
    }

    private static final class PlayerState {
        private int worldzero$sleepsUsed;
        private int worldzero$specialSleepIndex;
        private int worldzero$ordinarySinceLastSpecial;
        private int worldzero$requiredNightsBeforeNextSleep;
        private int worldzero$nightsSinceLastSleep = 100;
        private int worldzero$skippedAvailableNights;
        private long worldzero$nightDayIndex = Long.MIN_VALUE;
        private boolean worldzero$nightInitialized;
        private boolean worldzero$nightActive;
        private boolean worldzero$sleptThisNight;
        private boolean worldzero$nightWasAvailable;
        private boolean worldzero$canSleepAnnouncedThisNight;
        private boolean worldzero$paralysisTriggered;
        private boolean worldzero$awaitingHouseReturn;
        private boolean worldzero$restorationSleepPending;
        private boolean worldzero$restorationCompleted;
        private boolean worldzero$pressureSkyWatchActive;
        private long worldzero$nextPressureControlTick;
        private long worldzero$lastCannotSleepLineTick = Long.MIN_VALUE;
        private long worldzero$lastPressureLineTick = Long.MIN_VALUE;
        private long worldzero$lastBedAttemptGameTime = Long.MIN_VALUE;

        private CompoundTag worldzero$save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("sleeps_used", this.worldzero$sleepsUsed);
            tag.putInt("special_sleep_index", this.worldzero$specialSleepIndex);
            tag.putInt("ordinary_since_last_special", this.worldzero$ordinarySinceLastSpecial);
            tag.putInt("required_nights_before_next_sleep", this.worldzero$requiredNightsBeforeNextSleep);
            tag.putInt("nights_since_last_sleep", this.worldzero$nightsSinceLastSleep);
            tag.putInt("skipped_available_nights", this.worldzero$skippedAvailableNights);
            tag.putLong("night_day_index", this.worldzero$nightDayIndex);
            tag.putBoolean("night_initialized", this.worldzero$nightInitialized);
            tag.putBoolean("night_active", this.worldzero$nightActive);
            tag.putBoolean("slept_this_night", this.worldzero$sleptThisNight);
            tag.putBoolean("night_was_available", this.worldzero$nightWasAvailable);
            tag.putBoolean("can_sleep_announced_this_night", this.worldzero$canSleepAnnouncedThisNight);
            tag.putBoolean("paralysis_triggered", this.worldzero$paralysisTriggered);
            tag.putBoolean("awaiting_house_return", this.worldzero$awaitingHouseReturn);
            tag.putBoolean("restoration_sleep_pending", this.worldzero$restorationSleepPending);
            tag.putBoolean("restoration_completed", this.worldzero$restorationCompleted);
            tag.putBoolean("pressure_sky_watch_active", this.worldzero$pressureSkyWatchActive);
            tag.putLong("next_pressure_control_tick", this.worldzero$nextPressureControlTick);
            tag.putLong("last_cannot_sleep_line_tick", this.worldzero$lastCannotSleepLineTick);
            tag.putLong("last_pressure_line_tick", this.worldzero$lastPressureLineTick);
            tag.putLong("last_bed_attempt_game_time", this.worldzero$lastBedAttemptGameTime);
            return tag;
        }

        private static PlayerState worldzero$load(CompoundTag tag) {
            PlayerState state = new PlayerState();
            state.worldzero$sleepsUsed = tag.getInt("sleeps_used");
            state.worldzero$specialSleepIndex = tag.getInt("special_sleep_index");
            state.worldzero$ordinarySinceLastSpecial = tag.getInt("ordinary_since_last_special");
            state.worldzero$requiredNightsBeforeNextSleep = tag.getInt("required_nights_before_next_sleep");
            state.worldzero$nightsSinceLastSleep = tag.contains("nights_since_last_sleep")
                    ? tag.getInt("nights_since_last_sleep")
                    : 100;
            state.worldzero$skippedAvailableNights = tag.getInt("skipped_available_nights");
            state.worldzero$nightDayIndex = tag.contains("night_day_index") ? tag.getLong("night_day_index") : Long.MIN_VALUE;
            state.worldzero$nightInitialized = tag.getBoolean("night_initialized");
            state.worldzero$nightActive = tag.getBoolean("night_active");
            state.worldzero$sleptThisNight = tag.getBoolean("slept_this_night");
            state.worldzero$nightWasAvailable = tag.getBoolean("night_was_available");
            state.worldzero$canSleepAnnouncedThisNight = tag.getBoolean("can_sleep_announced_this_night");
            state.worldzero$paralysisTriggered = tag.getBoolean("paralysis_triggered");
            state.worldzero$awaitingHouseReturn = tag.getBoolean("awaiting_house_return");
            state.worldzero$restorationSleepPending = tag.getBoolean("restoration_sleep_pending");
            state.worldzero$restorationCompleted = tag.getBoolean("restoration_completed");
            state.worldzero$pressureSkyWatchActive = tag.getBoolean("pressure_sky_watch_active");
            state.worldzero$nextPressureControlTick = tag.getLong("next_pressure_control_tick");
            state.worldzero$lastCannotSleepLineTick = tag.contains("last_cannot_sleep_line_tick")
                    ? tag.getLong("last_cannot_sleep_line_tick")
                    : Long.MIN_VALUE;
            state.worldzero$lastPressureLineTick = tag.contains("last_pressure_line_tick")
                    ? tag.getLong("last_pressure_line_tick")
                    : Long.MIN_VALUE;
            state.worldzero$lastBedAttemptGameTime = tag.contains("last_bed_attempt_game_time")
                    ? tag.getLong("last_bed_attempt_game_time")
                    : Long.MIN_VALUE;
            return state;
        }
    }

    private static final class SleepControlSaveData extends SavedData {
        private final Map<UUID, PlayerState> worldzero$players = new HashMap<>();

        private static SleepControlSaveData worldzero$load(CompoundTag tag) {
            SleepControlSaveData saveData = new SleepControlSaveData();
            CompoundTag playersTag = tag.getCompound("players");
            for (String key : playersTag.getAllKeys()) {
                try {
                    saveData.worldzero$players.put(
                            UUID.fromString(key),
                            PlayerState.worldzero$load(playersTag.getCompound(key))
                    );
                } catch (IllegalArgumentException ignored) {
                }
            }
            return saveData;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            CompoundTag playersTag = new CompoundTag();
            for (Map.Entry<UUID, PlayerState> entry : this.worldzero$players.entrySet()) {
                playersTag.put(entry.getKey().toString(), entry.getValue().worldzero$save());
            }
            tag.put("players", playersTag);
            return tag;
        }
    }
}
