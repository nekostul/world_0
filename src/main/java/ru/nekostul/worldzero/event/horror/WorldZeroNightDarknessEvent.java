package ru.nekostul.worldzero.event.horror;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;
import ru.nekostul.worldzero.entity.WorldZeroEchoPresenceTracker;
import ru.nekostul.worldzero.event.WorldZeroStoryTime;
import ru.nekostul.worldzero.event.ambient.WorldZeroAmbientSoundEvent;
import ru.nekostul.worldzero.event.fall.WorldZeroFallEvent;
import ru.nekostul.worldzero.event.footsteps.WorldZeroFootstepsEvent;
import ru.nekostul.worldzero.event.freeze.WorldZeroFreezeEvent;
import ru.nekostul.worldzero.event.house.WorldZeroHouseEvent;
import ru.nekostul.worldzero.event.major.WorldZeroMajorEventSystem;
import ru.nekostul.worldzero.event.paralysis.WorldZeroParalysisEvent;
import ru.nekostul.worldzero.event.skywatch.WorldZeroSkyWatchEvent;
import ru.nekostul.worldzero.event.sleep.WorldZeroSleepControlEvent;
import ru.nekostul.worldzero.network.WorldZeroNetwork;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroNightDarknessEvent {
    private static final long WORLDZERO_RETRY_DELAY_MIN_TICKS = 2L * 60L * 20L;
    private static final long WORLDZERO_RETRY_DELAY_MAX_TICKS = 4L * 60L * 20L;
    private static final long WORLDZERO_NIGHT_START_TIME = 14000L;
    private static final long WORLDZERO_NIGHT_LAST_TRIGGER_TIME = 22000L;
    private static final int WORLDZERO_DARKNESS_DURATION_TICKS = 20 * 60 * 10;
    private static final String WORLDZERO_SAVE_ID = "worldzero_night_darkness";
    private static final net.minecraft.resources.ResourceLocation WORLDZERO_W0_SOUND_ID = new net.minecraft.resources.ResourceLocation(
            WorldZeroMod.MOD_ID,
            "w0"
    );
    private static final Map<MinecraftServer, SessionState> WORLDZERO_SESSION_STATES = new WeakHashMap<>();

    private WorldZeroNightDarknessEvent() {
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
        if (state.worldzero$active) {
            worldzero$tickActiveEvent(server, state);
            return;
        }

        NightDarknessSaveData saveData = worldzero$getSaveData(level);
        if (saveData.worldzero$triggered || !level.isNight()) {
            return;
        }

        long dayIndex = Math.floorDiv(level.getDayTime(), 24000L);
        long nightTime = Math.floorMod(level.getDayTime(), 24000L);
        if (saveData.worldzero$scheduledNightDay != dayIndex) {
            saveData.worldzero$scheduledNightDay = dayIndex;
            saveData.worldzero$scheduledTriggerTime = worldzero$pickNightTriggerTime(level, nightTime);
            saveData.worldzero$retryNotBeforeTime = -1L;
            saveData.setDirty();
        }

        if (nightTime < saveData.worldzero$scheduledTriggerTime) {
            return;
        }

        if (saveData.worldzero$retryNotBeforeTime >= 0L && nightTime < saveData.worldzero$retryNotBeforeTime) {
            return;
        }

        if (worldzero$hasConflictingEvent(level) || WorldZeroEchoPresenceTracker.worldzero$hasAnyEcho(server)) {
            long retryDelay = worldzero$randomRange(level, WORLDZERO_RETRY_DELAY_MIN_TICKS, WORLDZERO_RETRY_DELAY_MAX_TICKS);
            saveData.worldzero$retryNotBeforeTime = nightTime + retryDelay;
            saveData.setDirty();
            return;
        }

        ServerPlayer player = worldzero$pickTargetPlayer(level);
        if (player == null) {
            return;
        }

        if (!worldzero$startEvent(player, state)) {
            return;
        }

        saveData.worldzero$triggered = true;
        saveData.setDirty();
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_SESSION_STATES.remove(event.getServer());
    }

    public static boolean worldzero$isActive(@Nullable MinecraftServer server) {
        if (server == null) {
            return false;
        }

        SessionState state = WORLDZERO_SESSION_STATES.get(server);
        return state != null && state.worldzero$active;
    }

    public static void worldzero$acknowledgeSoundFinished(ServerPlayer player) {
        if (player == null) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        SessionState state = WORLDZERO_SESSION_STATES.get(server);
        if (state == null || !state.worldzero$active || !player.getUUID().equals(state.worldzero$targetPlayerId)) {
            return;
        }

        worldzero$finishEvent(player, state, false);
    }

    public static boolean worldzero$stopNow(@Nullable MinecraftServer server) {
        if (server == null) {
            return false;
        }

        SessionState state = WORLDZERO_SESSION_STATES.get(server);
        if (state == null || !state.worldzero$active) {
            return false;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(state.worldzero$targetPlayerId);
        if (player != null) {
            worldzero$finishEvent(player, state, true);
        } else {
            state.worldzero$reset();
        }
        return true;
    }

    public static boolean worldzero$triggerNow(ServerPlayer player) {
        if (player == null
                || !player.isAlive()
                || player.isSpectator()
                || player.serverLevel().dimension() != Level.OVERWORLD) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null || worldzero$hasConflictingEvent(player.serverLevel()) || WorldZeroEchoPresenceTracker.worldzero$hasAnyEcho(server)) {
            return false;
        }

        SessionState state = WORLDZERO_SESSION_STATES.computeIfAbsent(server, ignored -> new SessionState());
        if (state.worldzero$active) {
            return false;
        }

        return worldzero$startEvent(player, state);
    }

    private static void worldzero$tickActiveEvent(MinecraftServer server, SessionState state) {
        ServerPlayer player = server.getPlayerList().getPlayer(state.worldzero$targetPlayerId);
        if (player == null || !player.isAlive() || player.isSpectator() || player.serverLevel().dimension() != Level.OVERWORLD) {
            if (player != null) {
                worldzero$finishEvent(player, state, true);
            } else {
                state.worldzero$reset();
            }
        }
    }

    private static boolean worldzero$startEvent(ServerPlayer player, SessionState state) {
        if (!player.isAlive() || player.isSpectator() || player.serverLevel().dimension() != Level.OVERWORLD) {
            return false;
        }

        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, WORLDZERO_DARKNESS_DURATION_TICKS, 0, false, false, false));
        WorldZeroNetwork.sendNightDarkness(player, true);
        WorldZeroNetwork.sendHorrorSound(player, WORLDZERO_W0_SOUND_ID, 1.0F, true);
        state.worldzero$active = true;
        state.worldzero$targetPlayerId = player.getUUID();
        return true;
    }

    private static void worldzero$finishEvent(ServerPlayer player, SessionState state, boolean stopSound) {
        player.removeEffect(MobEffects.DARKNESS);
        WorldZeroNetwork.sendNightDarkness(player, false);
        if (stopSound) {
            WorldZeroNetwork.sendStopHorrorSounds(player);
        }
        state.worldzero$reset();
    }

    @Nullable
    private static ServerPlayer worldzero$pickTargetPlayer(ServerLevel level) {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!WorldZeroStoryTime.worldzero$canReceiveStoryEvent(player)) {
                continue;
            }
            if (!WorldZeroSleepControlEvent.worldzero$canPlayerSleepNow(level, player)) {
                players.add(player);
            }
        }

        if (players.isEmpty()) {
            return null;
        }

        return players.get(level.random.nextInt(players.size()));
    }

    private static boolean worldzero$hasConflictingEvent(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return true;
        }

        MinecraftServer server = level.getServer();
        return WorldZeroFreezeEvent.worldzero$isFreezeActive(server)
                || WorldZeroFallEvent.worldzero$isFallActive(server)
                || WorldZeroFootstepsEvent.worldzero$isFootstepsActive(server)
                || WorldZeroHouseEvent.worldzero$isHouseActive(server)
                || WorldZeroSkyWatchEvent.worldzero$isActive(server)
                || WorldZeroParalysisEvent.worldzero$isParalysisActive(server)
                || WorldZeroBlackEchoJumpscareEvent.worldzero$isActive(server)
                || WorldZeroTrapEvent.worldzero$isActive(server)
                || WorldZeroHorrorFinale.worldzero$isActive(server)
                || WorldZeroHorrorEventSystem.worldzero$isMinorAnomalyActive(level)
                || WorldZeroMajorEventSystem.worldzero$isMajorEventActive(server)
                || WorldZeroAmbientSoundEvent.worldzero$isMajorEventStartBlocked(level);
    }

    private static long worldzero$pickNightTriggerTime(ServerLevel level, long currentNightTime) {
        long minTime = Math.max(WORLDZERO_NIGHT_START_TIME, currentNightTime);
        long maxTime = WORLDZERO_NIGHT_LAST_TRIGGER_TIME;
        if (minTime >= maxTime) {
            return minTime;
        }
        return worldzero$randomRange(level, minTime, maxTime);
    }

    private static long worldzero$randomRange(ServerLevel level, long minValue, long maxValue) {
        if (minValue >= maxValue) {
            return minValue;
        }

        long bound = maxValue - minValue + 1L;
        return minValue + Math.floorMod(level.random.nextLong(), bound);
    }

    private static NightDarknessSaveData worldzero$getSaveData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                NightDarknessSaveData::worldzero$load,
                NightDarknessSaveData::new,
                WORLDZERO_SAVE_ID
        );
    }

    private static final class SessionState {
        private boolean worldzero$active;
        private UUID worldzero$targetPlayerId;

        private void worldzero$reset() {
            this.worldzero$active = false;
            this.worldzero$targetPlayerId = null;
        }
    }

    private static final class NightDarknessSaveData extends SavedData {
        private boolean worldzero$triggered;
        private long worldzero$scheduledNightDay = Long.MIN_VALUE;
        private long worldzero$scheduledTriggerTime = WORLDZERO_NIGHT_START_TIME;
        private long worldzero$retryNotBeforeTime = -1L;

        private static NightDarknessSaveData worldzero$load(CompoundTag tag) {
            NightDarknessSaveData saveData = new NightDarknessSaveData();
            saveData.worldzero$triggered = tag.contains("triggered")
                    ? tag.getBoolean("triggered")
                    : tag.getBoolean("completed");
            saveData.worldzero$scheduledNightDay = tag.contains("scheduled_night_day")
                    ? tag.getLong("scheduled_night_day")
                    : Long.MIN_VALUE;
            saveData.worldzero$scheduledTriggerTime = tag.contains("scheduled_trigger_time")
                    ? tag.getLong("scheduled_trigger_time")
                    : WORLDZERO_NIGHT_START_TIME;
            saveData.worldzero$retryNotBeforeTime = tag.contains("retry_not_before_time")
                    ? tag.getLong("retry_not_before_time")
                    : -1L;
            return saveData;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean("triggered", this.worldzero$triggered);
            tag.putBoolean("completed", this.worldzero$triggered);
            tag.putLong("scheduled_night_day", this.worldzero$scheduledNightDay);
            tag.putLong("scheduled_trigger_time", this.worldzero$scheduledTriggerTime);
            tag.putLong("retry_not_before_time", this.worldzero$retryNotBeforeTime);
            return tag;
        }
    }
}
