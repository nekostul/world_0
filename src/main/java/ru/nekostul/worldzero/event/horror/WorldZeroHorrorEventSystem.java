package ru.nekostul.worldzero;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroHorrorEventSystem {
    private static final long WORLDZERO_TICKS_PER_MINUTE = 60L * 20L;
    private static final long WORLDZERO_FIRST_END_TICKS = 30L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_ACTIVE_END_TICKS = 90L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_PEAK_END_TICKS = 150L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_RETRY_COOLDOWN_TICKS = 20L * 20L;
    private static final long WORLDZERO_SAVE_DIRTY_INTERVAL_TICKS = 20L * 20L;
    private static final String WORLDZERO_SAVE_ID = "worldzero_horror_event_system";
    private static final AABB WORLDZERO_ENTITY_SCAN_AABB = new AABB(
            -30_000_000.0D,
            -2_048.0D,
            -30_000_000.0D,
            30_000_000.0D,
            4_096.0D,
            30_000_000.0D
    );
    private static final Map<MinecraftServer, SessionState> WORLDZERO_SESSION_STATES = new WeakHashMap<>();

    private WorldZeroHorrorEventSystem() {
    }

    @SubscribeEvent
    public static void worldzero$onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.level instanceof ServerLevel level) || level.isClientSide() || level.dimension() != Level.OVERWORLD) {
            return;
        }

        HorrorSaveData saveData = worldzero$getSaveData(level);
        saveData.worldzero$worldTicks++;
        if (saveData.worldzero$worldTicks % WORLDZERO_SAVE_DIRTY_INTERVAL_TICKS == 0L) {
            saveData.setDirty();
        }

        long worldTicks = saveData.worldzero$worldTicks;
        WorldZeroMinorAnomalies.worldzero$tick(level, worldTicks);

        MinecraftServer server = level.getServer();
        SessionState state = WORLDZERO_SESSION_STATES.computeIfAbsent(server, ignored -> new SessionState());
        if (state.worldzero$activeUntilWorldTick >= 0L && state.worldzero$activeUntilWorldTick <= worldTicks) {
            state.worldzero$activeUntilWorldTick = -1L;
            state.worldzero$debugForcedActive = false;
        }

        if (WorldZeroHorrorFinale.worldzero$isEndReached(worldTicks)) {
            if (!state.worldzero$debugForcedActive) {
                WorldZeroHorrorFinale.worldzero$tickEnd(level);
            }
            return;
        }

        WorldZeroHorrorPhase phase = worldzero$resolvePhase(worldTicks);
        if (phase == WorldZeroHorrorPhase.FIRST || phase == WorldZeroHorrorPhase.END) {
            return;
        }

        if (state.worldzero$activeUntilWorldTick > worldTicks) {
            return;
        }
        state.worldzero$activeUntilWorldTick = -1L;

        if (state.worldzero$cooldownTicks > 0L) {
            state.worldzero$cooldownTicks--;
            return;
        }

        if (worldzero$hasConflictingEvent(server) || worldzero$hasActiveEcho(server)) {
            state.worldzero$cooldownTicks = WORLDZERO_RETRY_COOLDOWN_TICKS;
            return;
        }

        WorldZeroMinorAnomalies.TriggerResult result = WorldZeroMinorAnomalies.worldzero$tryTriggerRandom(
                level,
                phase,
                worldTicks,
                !saveData.worldzero$wrongWindTriggered
        );
        if (!result.worldzero$triggered()) {
            state.worldzero$cooldownTicks = WORLDZERO_RETRY_COOLDOWN_TICKS;
            return;
        }

        if (result.worldzero$type() == WorldZeroMinorAnomalies.MinorAnomalyType.WRONG_WIND) {
            saveData.worldzero$wrongWindTriggered = true;
            saveData.setDirty();
        }
        state.worldzero$activeUntilWorldTick = worldTicks + result.worldzero$durationTicks();
        state.worldzero$cooldownTicks = worldzero$randomCooldownTicks(level, phase);
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_SESSION_STATES.remove(event.getServer());
        WorldZeroMinorAnomalies.worldzero$cancelAll(event.getServer());
    }

    public static boolean worldzero$triggerMinorAnomalyNow(
            ServerPlayer player,
            WorldZeroMinorAnomalies.MinorAnomalyType anomalyType
    ) {
        if (player == null || anomalyType == null || !player.isAlive() || player.isSpectator()) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD) {
            return false;
        }

        HorrorSaveData saveData = worldzero$getSaveData(level);
        long worldTicks = saveData.worldzero$worldTicks;

        MinecraftServer server = level.getServer();
        SessionState state = WORLDZERO_SESSION_STATES.computeIfAbsent(server, ignored -> new SessionState());
        if (state.worldzero$activeUntilWorldTick > worldTicks
                || worldzero$hasConflictingEvent(server)
                || worldzero$hasActiveEcho(server)) {
            return false;
        }

        WorldZeroMinorAnomalies.TriggerResult result = WorldZeroMinorAnomalies.worldzero$trigger(
                level,
                player,
                anomalyType,
                worldTicks
        );
        if (!result.worldzero$triggered()) {
            return false;
        }

        state.worldzero$activeUntilWorldTick = worldTicks + result.worldzero$durationTicks();
        state.worldzero$debugForcedActive = true;
        state.worldzero$cooldownTicks = Math.max(state.worldzero$cooldownTicks, WORLDZERO_RETRY_COOLDOWN_TICKS);
        return true;
    }

    public static long worldzero$getWorldTicks(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return 0L;
        }

        return worldzero$getSaveData(level).worldzero$worldTicks;
    }

    public static boolean worldzero$isMinorAnomalyActive(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return false;
        }

        SessionState state = WORLDZERO_SESSION_STATES.get(level.getServer());
        if (state == null || state.worldzero$activeUntilWorldTick < 0L) {
            return false;
        }

        return state.worldzero$activeUntilWorldTick > worldzero$getSaveData(level).worldzero$worldTicks;
    }

    public static boolean worldzero$stopAllEvents(MinecraftServer server) {
        if (server == null) {
            return false;
        }

        boolean hadSystemState = WORLDZERO_SESSION_STATES.remove(server) != null;
        WorldZeroMinorAnomalies.worldzero$cancelAll(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            WorldZeroNetwork.sendMinorAnomaly(
                    player,
                    WorldZeroMinorAnomalyPacket.WORLDZERO_ACTION_CLEAR_ALL,
                    1,
                    0
            );
        }
        return hadSystemState;
    }

    public static WorldZeroHorrorPhase worldzero$resolvePhase(long worldTicks) {
        if (worldTicks < WORLDZERO_FIRST_END_TICKS) {
            return WorldZeroHorrorPhase.FIRST;
        }
        if (worldTicks < WORLDZERO_ACTIVE_END_TICKS) {
            return WorldZeroHorrorPhase.ACTIVE;
        }
        if (worldTicks < WORLDZERO_PEAK_END_TICKS) {
            return WorldZeroHorrorPhase.PEAK;
        }
        if (worldTicks < WorldZeroHorrorFinale.WORLDZERO_END_TICKS) {
            return WorldZeroHorrorPhase.DECLINE;
        }
        return WorldZeroHorrorPhase.END;
    }

    private static boolean worldzero$hasConflictingEvent(MinecraftServer server) {
        return WorldZeroFreezeEvent.worldzero$isFreezeActive(server)
                || WorldZeroFallEvent.worldzero$isFallActive(server)
                || WorldZeroFootstepsEvent.worldzero$isFootstepsActive(server)
                || WorldZeroHouseEvent.worldzero$isHouseActive(server)
                || WorldZeroParalysisEvent.worldzero$isParalysisActive(server);
    }

    private static boolean worldzero$hasActiveEcho(MinecraftServer server) {
        if (server == null) {
            return false;
        }

        for (ServerLevel level : server.getAllLevels()) {
            if (!level.getEntitiesOfClass(
                    WorldZeroEchoEntity.class,
                    WORLDZERO_ENTITY_SCAN_AABB,
                    entity -> entity.getType() == WorldZeroEntities.WORLDZERO_ECHO.get()
                            || entity.getType() == WorldZeroEntities.WORLDZERO_BLACK_ECHO.get()
            ).isEmpty()) {
                return true;
            }

            if (!level.getEntitiesOfClass(
                    WorldZeroHouseEchoEntity.class,
                    WORLDZERO_ENTITY_SCAN_AABB
            ).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private static long worldzero$randomCooldownTicks(ServerLevel level, WorldZeroHorrorPhase phase) {
        return switch (phase) {
            case ACTIVE -> worldzero$randomTicks(level, 5, 9);
            case PEAK -> worldzero$randomTicks(level, 5, 7);
            case DECLINE -> worldzero$randomTicks(level, 8, 15);
            default -> worldzero$randomTicks(level, 10, 15);
        };
    }

    private static long worldzero$randomTicks(ServerLevel level, int minMinutes, int maxMinutes) {
        int minutes = Mth.nextInt(level.random, minMinutes, maxMinutes);
        return minutes * WORLDZERO_TICKS_PER_MINUTE;
    }

    private static HorrorSaveData worldzero$getSaveData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(HorrorSaveData::load, HorrorSaveData::new, WORLDZERO_SAVE_ID);
    }

    private static final class SessionState {
        private long worldzero$cooldownTicks;
        private long worldzero$activeUntilWorldTick = -1L;
        private boolean worldzero$debugForcedActive;
    }

    private static final class HorrorSaveData extends SavedData {
        private long worldzero$worldTicks;
        private boolean worldzero$wrongWindTriggered;

        private static HorrorSaveData load(CompoundTag tag) {
            HorrorSaveData saveData = new HorrorSaveData();
            saveData.worldzero$worldTicks = Math.max(0L, tag.getLong("world_ticks"));
            saveData.worldzero$wrongWindTriggered = tag.getBoolean("wrong_wind_triggered");
            return saveData;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putLong("world_ticks", this.worldzero$worldTicks);
            tag.putBoolean("wrong_wind_triggered", this.worldzero$wrongWindTriggered);
            return tag;
        }
    }
}
