package ru.nekostul.worldzero;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroStoryTime {
    private static final long WORLDZERO_WAKE_GRACE_TICKS = 60L * 20L;
    private static final long WORLDZERO_SAVE_DIRTY_INTERVAL_TICKS = 20L * 20L;
    private static final String WORLDZERO_SAVE_ID = "worldzero_story_time";
    private static final Map<MinecraftServer, SessionState> WORLDZERO_SESSION_STATES = new WeakHashMap<>();

    private WorldZeroStoryTime() {
    }

    @SubscribeEvent
    public static void worldzero$onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.level instanceof ServerLevel level) || level.isClientSide() || level.dimension() != Level.OVERWORLD) {
            return;
        }

        worldzero$sync(level);
    }

    @SubscribeEvent
    public static void worldzero$onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.player instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        SessionState state = WORLDZERO_SESSION_STATES.computeIfAbsent(server, ignored -> new SessionState());
        boolean sleeping = worldzero$isSleepingInOverworld(player);
        Boolean previousSleeping = state.worldzero$sleepingByPlayer.put(player.getUUID(), sleeping);
        if (Boolean.TRUE.equals(previousSleeping) && !sleeping) {
            state.worldzero$wakeBlockedUntilStoryTick.put(
                    player.getUUID(),
                    worldzero$getStoryTicks(server) + WORLDZERO_WAKE_GRACE_TICKS
            );
        }
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_SESSION_STATES.remove(event.getServer());
    }

    public static long worldzero$getStoryTicks(ServerLevel level) {
        return level == null ? 0L : worldzero$getStoryTicks(level.getServer());
    }

    public static long worldzero$getStoryTicks(MinecraftServer server) {
        ServerLevel overworld = worldzero$getOverworld(server);
        if (overworld == null) {
            return 0L;
        }

        StoryTimeSaveData saveData = worldzero$sync(overworld);
        return Math.max(0L, overworld.getGameTime() - saveData.worldzero$pausedTicks);
    }

    public static boolean worldzero$countsTowardStoryTime(ServerPlayer player) {
        return player != null
                && player.isAlive()
                && !player.isSpectator()
                && player.serverLevel().dimension() == Level.OVERWORLD
                && !player.isSleeping();
    }

    public static boolean worldzero$canReceiveStoryEvent(ServerPlayer player) {
        if (!worldzero$countsTowardStoryTime(player)) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        SessionState state = WORLDZERO_SESSION_STATES.get(server);
        if (state == null) {
            return true;
        }

        long blockedUntilStoryTick = state.worldzero$wakeBlockedUntilStoryTick.getOrDefault(player.getUUID(), -1L);
        return blockedUntilStoryTick < 0L || worldzero$getStoryTicks(server) >= blockedUntilStoryTick;
    }

    private static StoryTimeSaveData worldzero$sync(ServerLevel overworld) {
        StoryTimeSaveData saveData = worldzero$getSaveData(overworld);
        long gameTime = overworld.getGameTime();
        if (!saveData.worldzero$initialized) {
            saveData.worldzero$initialized = true;
            saveData.worldzero$lastObservedGameTime = gameTime;
            saveData.setDirty();
            return saveData;
        }

        if (gameTime <= saveData.worldzero$lastObservedGameTime) {
            return saveData;
        }

        long deltaTicks = gameTime - saveData.worldzero$lastObservedGameTime;
        if (!worldzero$hasAwakeOverworldPlayer(overworld)) {
            saveData.worldzero$pausedTicks += deltaTicks;
        }
        saveData.worldzero$lastObservedGameTime = gameTime;
        if (gameTime % WORLDZERO_SAVE_DIRTY_INTERVAL_TICKS == 0L) {
            saveData.setDirty();
        }
        return saveData;
    }

    private static boolean worldzero$hasAwakeOverworldPlayer(ServerLevel overworld) {
        for (ServerPlayer player : overworld.players()) {
            if (worldzero$countsTowardStoryTime(player)) {
                return true;
            }
        }
        return false;
    }

    private static boolean worldzero$isSleepingInOverworld(ServerPlayer player) {
        return player != null
                && player.isAlive()
                && !player.isSpectator()
                && player.serverLevel().dimension() == Level.OVERWORLD
                && player.isSleeping();
    }

    private static ServerLevel worldzero$getOverworld(MinecraftServer server) {
        return server == null ? null : server.getLevel(Level.OVERWORLD);
    }

    private static StoryTimeSaveData worldzero$getSaveData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                StoryTimeSaveData::worldzero$load,
                StoryTimeSaveData::new,
                WORLDZERO_SAVE_ID
        );
    }

    private static final class SessionState {
        private final Map<UUID, Boolean> worldzero$sleepingByPlayer = new HashMap<>();
        private final Map<UUID, Long> worldzero$wakeBlockedUntilStoryTick = new HashMap<>();
    }

    private static final class StoryTimeSaveData extends SavedData {
        private long worldzero$pausedTicks;
        private long worldzero$lastObservedGameTime = -1L;
        private boolean worldzero$initialized;

        private static StoryTimeSaveData worldzero$load(CompoundTag tag) {
            StoryTimeSaveData saveData = new StoryTimeSaveData();
            saveData.worldzero$pausedTicks = Math.max(0L, tag.getLong("paused_ticks"));
            saveData.worldzero$lastObservedGameTime = tag.contains("last_observed_game_time")
                    ? tag.getLong("last_observed_game_time")
                    : -1L;
            saveData.worldzero$initialized = tag.getBoolean("initialized");
            return saveData;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putLong("paused_ticks", this.worldzero$pausedTicks);
            tag.putLong("last_observed_game_time", this.worldzero$lastObservedGameTime);
            tag.putBoolean("initialized", this.worldzero$initialized);
            return tag;
        }
    }
}
