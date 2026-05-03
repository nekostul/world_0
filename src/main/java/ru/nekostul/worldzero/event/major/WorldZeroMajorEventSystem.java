package ru.nekostul.worldzero;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroMajorEventSystem {
    private static final long WORLDZERO_TICKS_PER_MINUTE = 60L * 20L;
    private static final long WORLDZERO_RETRY_COOLDOWN_TICKS = 30L * 20L;
    private static final String WORLDZERO_SAVE_ID = "worldzero_major_event_system";
    private static final Map<MinecraftServer, SessionState> WORLDZERO_SESSION_STATES = new WeakHashMap<>();

    private WorldZeroMajorEventSystem() {
    }

    @SubscribeEvent
    public static void worldzero$onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.level instanceof ServerLevel level) || level.isClientSide() || level.dimension() != Level.OVERWORLD) {
            return;
        }

        worldzero$tickEvents(level);

        MinecraftServer server = level.getServer();
        SessionState state = WORLDZERO_SESSION_STATES.computeIfAbsent(server, ignored -> new SessionState());
        if (worldzero$isMajorEventActive(server)) {
            return;
        }

        long worldTicks = WorldZeroHorrorEventSystem.worldzero$getWorldTicks(level);
        WorldZeroHorrorPhase phase = WorldZeroHorrorEventSystem.worldzero$resolvePhase(worldTicks);
        if (phase == WorldZeroHorrorPhase.FIRST || phase == WorldZeroHorrorPhase.END) {
            return;
        }

        if (state.worldzero$cooldownTicks < 0L) {
            state.worldzero$cooldownTicks = worldzero$randomCooldownTicks(level, phase);
        }
        if (state.worldzero$cooldownTicks > 0L) {
            state.worldzero$cooldownTicks--;
            return;
        }

        if (worldzero$hasConflictingEvent(level)) {
            state.worldzero$cooldownTicks = WORLDZERO_RETRY_COOLDOWN_TICKS;
            return;
        }

        MajorSaveData saveData = worldzero$getSaveData(level);
        WorldZeroMajorEventType[] candidates = worldzero$candidatesForPhase(phase);
        if (candidates.length == 0) {
            state.worldzero$cooldownTicks = WORLDZERO_RETRY_COOLDOWN_TICKS;
            return;
        }

        ServerPlayer player = worldzero$pickTargetPlayer(level);
        if (player == null) {
            state.worldzero$cooldownTicks = WORLDZERO_RETRY_COOLDOWN_TICKS;
            return;
        }

        int offset = level.random.nextInt(candidates.length);
        for (int index = 0; index < candidates.length; index++) {
            WorldZeroMajorEventType eventType = candidates[(offset + index) % candidates.length];
            if (saveData.worldzero$completedEvents.contains(eventType)) {
                continue;
            }
            if (worldzero$requiresClearEcho(eventType) && worldzero$hasActiveEcho(server)) {
                continue;
            }

            if (worldzero$triggerEvent(player, eventType)) {
                saveData.worldzero$completedEvents.add(eventType);
                saveData.setDirty();
                state.worldzero$cooldownTicks = worldzero$randomCooldownTicks(level, phase);
                return;
            }
        }

        state.worldzero$cooldownTicks = WORLDZERO_RETRY_COOLDOWN_TICKS;
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_SESSION_STATES.remove(event.getServer());
        WorldZeroEchoPresenceTracker.worldzero$clear(event.getServer());
        worldzero$stopAllEvents(event.getServer());
    }

    public static boolean worldzero$triggerMajorEventNow(ServerPlayer player, WorldZeroMajorEventType eventType) {
        if (player == null || eventType == null || !player.isAlive() || player.isSpectator()) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null
                || worldzero$isMajorEventActive(server)
                || worldzero$hasConflictingEvent(level)
                || (worldzero$requiresClearEcho(eventType) && worldzero$hasActiveEcho(server))) {
            return false;
        }

        boolean triggered = worldzero$triggerEvent(player, eventType);
        if (triggered) {
            WORLDZERO_SESSION_STATES.computeIfAbsent(server, ignored -> new SessionState()).worldzero$cooldownTicks = WORLDZERO_RETRY_COOLDOWN_TICKS;
        }
        return triggered;
    }

    public static boolean worldzero$isMajorEventActive(MinecraftServer server) {
        return WorldZeroWatchingEvent.worldzero$isActive(server)
                || WorldZeroStalkerEvent.worldzero$isActive(server)
                || WorldZeroVoidCallEvent.worldzero$isActive(server)
                || WorldZeroCorruptionEvent.worldzero$isActive(server)
                || WorldZeroGrowthEvent.worldzero$isActive(server)
                || WorldZeroSwarmEvent.worldzero$isActive(server)
                || WorldZeroTimeLoopEvent.worldzero$isActive(server)
                || WorldZeroGlitchRainEvent.worldzero$isActive(server);
    }

    public static boolean worldzero$stopAllEvents(MinecraftServer server) {
        if (server == null) {
            return false;
        }

        boolean changed = false;
        changed |= WorldZeroWatchingEvent.worldzero$stopNow(server);
        changed |= WorldZeroStalkerEvent.worldzero$stopNow(server);
        changed |= WorldZeroVoidCallEvent.worldzero$stopNow(server);
        changed |= WorldZeroCorruptionEvent.worldzero$stopNow(server);
        changed |= WorldZeroGrowthEvent.worldzero$stopNow(server);
        changed |= WorldZeroSwarmEvent.worldzero$stopNow(server);
        changed |= WorldZeroTimeLoopEvent.worldzero$stopNow(server);
        changed |= WorldZeroGlitchRainEvent.worldzero$stopNow(server);
        WORLDZERO_SESSION_STATES.remove(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            WorldZeroNetwork.sendMajorEvent(player, WorldZeroMajorEventPacket.WORLDZERO_ACTION_CLEAR_ALL, 1, 0);
        }
        return changed;
    }

    public static boolean worldzero$hasConflictingEvent(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return false;
        }

        MinecraftServer server = level.getServer();
        return WorldZeroFreezeEvent.worldzero$isFreezeActive(server)
                || WorldZeroFallEvent.worldzero$isFallActive(server)
                || WorldZeroFootstepsEvent.worldzero$isFootstepsActive(server)
                || WorldZeroHouseEvent.worldzero$isHouseActive(server)
                || WorldZeroSkyWatchEvent.worldzero$isActive(server)
                || WorldZeroParalysisEvent.worldzero$isParalysisActive(server)
                || WorldZeroHorrorFinale.worldzero$isActive(server)
                || WorldZeroHorrorEventSystem.worldzero$isMinorAnomalyActive(level);
    }

    private static void worldzero$tickEvents(ServerLevel level) {
        WorldZeroWatchingEvent.worldzero$tick(level);
        WorldZeroStalkerEvent.worldzero$tick(level);
        WorldZeroVoidCallEvent.worldzero$tick(level);
        WorldZeroCorruptionEvent.worldzero$tick(level);
        WorldZeroGrowthEvent.worldzero$tick(level);
        WorldZeroSwarmEvent.worldzero$tick(level);
        WorldZeroTimeLoopEvent.worldzero$tick(level);
        WorldZeroGlitchRainEvent.worldzero$tick(level);
    }

    private static boolean worldzero$triggerEvent(ServerPlayer player, WorldZeroMajorEventType eventType) {
        return switch (eventType) {
            case WATCHING -> WorldZeroWatchingEvent.worldzero$triggerNow(player);
            case STALKER -> WorldZeroStalkerEvent.worldzero$triggerNow(player);
            case VOID_CALL -> WorldZeroVoidCallEvent.worldzero$triggerNow(player);
            case CORRUPTION -> false;
            case GROWTH -> WorldZeroGrowthEvent.worldzero$triggerNow(player);
            case SWARM -> WorldZeroSwarmEvent.worldzero$triggerNow(player);
            case TIME_LOOP -> WorldZeroTimeLoopEvent.worldzero$triggerNow(player);
            case GLITCH_RAIN -> WorldZeroGlitchRainEvent.worldzero$triggerNow(player);
        };
    }

    private static WorldZeroMajorEventType[] worldzero$candidatesForPhase(WorldZeroHorrorPhase phase) {
        return switch (phase) {
            case EARLY -> new WorldZeroMajorEventType[]{
                    WorldZeroMajorEventType.WATCHING
            };
            case ACTIVE -> new WorldZeroMajorEventType[]{
                    WorldZeroMajorEventType.WATCHING
            };
            case RISING -> new WorldZeroMajorEventType[]{
                    WorldZeroMajorEventType.GROWTH,
                    WorldZeroMajorEventType.STALKER,
                    WorldZeroMajorEventType.GLITCH_RAIN
            };
            case PEAK -> new WorldZeroMajorEventType[]{
                    WorldZeroMajorEventType.GROWTH,
                    WorldZeroMajorEventType.STALKER,
                    WorldZeroMajorEventType.VOID_CALL,
                    WorldZeroMajorEventType.GLITCH_RAIN
            };
            case DECLINE -> new WorldZeroMajorEventType[]{
                    WorldZeroMajorEventType.STALKER,
                    WorldZeroMajorEventType.VOID_CALL,
                    WorldZeroMajorEventType.SWARM,
                    WorldZeroMajorEventType.TIME_LOOP
            };
            default -> new WorldZeroMajorEventType[0];
        };
    }

    private static boolean worldzero$requiresClearEcho(WorldZeroMajorEventType eventType) {
        return eventType == WorldZeroMajorEventType.STALKER;
    }

    private static ServerPlayer worldzero$pickTargetPlayer(ServerLevel level) {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (WorldZeroStoryTime.worldzero$canReceiveStoryEvent(player)) {
                players.add(player);
            }
        }

        if (players.isEmpty()) {
            return null;
        }

        return players.get(level.random.nextInt(players.size()));
    }

    private static boolean worldzero$hasActiveEcho(MinecraftServer server) {
        return WorldZeroEchoPresenceTracker.worldzero$hasAnyEcho(server);
    }

    private static long worldzero$randomCooldownTicks(ServerLevel level, WorldZeroHorrorPhase phase) {
        return switch (phase) {
            case EARLY -> worldzero$randomTicks(level, 24, 35);
            case ACTIVE -> worldzero$randomTicks(level, 22, 32);
            case RISING -> worldzero$randomTicks(level, 20, 28);
            case PEAK -> worldzero$randomTicks(level, 18, 24);
            case DECLINE -> worldzero$randomTicks(level, 16, 22);
            default -> worldzero$randomTicks(level, 25, 40);
        };
    }

    private static long worldzero$randomTicks(ServerLevel level, int minMinutes, int maxMinutes) {
        return Mth.nextInt(level.random, minMinutes, maxMinutes) * WORLDZERO_TICKS_PER_MINUTE;
    }

    private static MajorSaveData worldzero$getSaveData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(MajorSaveData::load, MajorSaveData::new, WORLDZERO_SAVE_ID);
    }

    private static final class SessionState {
        private long worldzero$cooldownTicks = -1L;
    }

    private static final class MajorSaveData extends SavedData {
        private final EnumSet<WorldZeroMajorEventType> worldzero$completedEvents = EnumSet.noneOf(WorldZeroMajorEventType.class);

        private static MajorSaveData load(CompoundTag tag) {
            MajorSaveData saveData = new MajorSaveData();
            for (WorldZeroMajorEventType eventType : WorldZeroMajorEventType.values()) {
                if (tag.getBoolean(eventType.worldzero$debugName())) {
                    saveData.worldzero$completedEvents.add(eventType);
                }
            }
            return saveData;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            for (WorldZeroMajorEventType eventType : WorldZeroMajorEventType.values()) {
                tag.putBoolean(eventType.worldzero$debugName(), this.worldzero$completedEvents.contains(eventType));
            }
            return tag;
        }
    }
}
