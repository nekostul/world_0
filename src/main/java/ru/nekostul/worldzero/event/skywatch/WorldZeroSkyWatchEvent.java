package ru.nekostul.worldzero.event.skywatch;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;
import ru.nekostul.worldzero.event.WorldZeroStoryTime;
import ru.nekostul.worldzero.event.chat.WorldZeroDoubleChatEvent;
import ru.nekostul.worldzero.event.fall.WorldZeroFallEvent;
import ru.nekostul.worldzero.event.footsteps.WorldZeroFootstepsEvent;
import ru.nekostul.worldzero.event.freeze.WorldZeroFreezeEvent;
import ru.nekostul.worldzero.event.horror.WorldZeroHorrorEventSystem;
import ru.nekostul.worldzero.event.horror.WorldZeroHorrorFinale;
import ru.nekostul.worldzero.event.house.WorldZeroHouseDetector;
import ru.nekostul.worldzero.event.house.WorldZeroHouseEvent;
import ru.nekostul.worldzero.event.major.WorldZeroMajorEventSystem;
import ru.nekostul.worldzero.event.paralysis.WorldZeroParalysisEvent;
import ru.nekostul.worldzero.event.stalker.WorldZeroStalkerEvent;
import ru.nekostul.worldzero.network.WorldZeroNetwork;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroSkyWatchEvent {
    private static final String WORLDZERO_SAVE_ID = "worldzero_sky_watch_event";
    private static final long WORLDZERO_TICKS_PER_MINUTE = 20L * 60L;
    private static final long WORLDZERO_WINDOW_START_TICKS = 50L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_PRIMARY_WINDOW_END_TICKS = 90L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_ABSOLUTE_WINDOW_END_TICKS = 180L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_PRELUDE_DELAY_TICKS = 40L;
    private static final int WORLDZERO_SOUND_STAGE_TICKS = 28 * 20;
    private static final int WORLDZERO_RESULT_DELAY_TICKS = 20;
    private static final int WORLDZERO_DURATION_TICKS = WORLDZERO_SOUND_STAGE_TICKS + WORLDZERO_RESULT_DELAY_TICKS;
    private static final long WORLDZERO_RETRY_MIN_TICKS = 45L * 20L;
    private static final long WORLDZERO_RETRY_MAX_TICKS = 90L * 20L;
    private static final long WORLDZERO_LATE_RETRY_MIN_TICKS = 2L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_LATE_RETRY_MAX_TICKS = 4L * WORLDZERO_TICKS_PER_MINUTE;
    private static final double WORLDZERO_ESCAPE_DISTANCE_SQR = 18.0D * 18.0D;
    private static final int WORLDZERO_MIN_SURFACE_DEPTH = 5;
    private static final String WORLDZERO_NOTICE_KEY = "message.worldzero.sky_watch.notice";
    private static final String WORLDZERO_SUCCESS_KEY = "message.worldzero.sky_watch.success";
    private static final String WORLDZERO_FAILURE_KEY = "message.worldzero.sky_watch.failure";
    private static final int WORLDZERO_STAGE_IDLE = 0;
    private static final int WORLDZERO_STAGE_PRELUDE = 1;
    private static final int WORLDZERO_STAGE_RUNNING = 2;
    private static final int WORLDZERO_STAGE_DONE = 3;

    private WorldZeroSkyWatchEvent() {
    }

    @SubscribeEvent
    public static void worldzero$onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.level instanceof ServerLevel level) || level.isClientSide() || level.dimension() != Level.OVERWORLD) {
            return;
        }

        long storyTicks = WorldZeroStoryTime.worldzero$getStoryTicks(level);
        SkyWatchSaveData saveData = worldzero$getSaveData(level);
        MinecraftServer server = level.getServer();
        boolean active = false;

        for (Map.Entry<UUID, PlayerState> entry : saveData.worldzero$players.entrySet()) {
            PlayerState state = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (worldzero$tickPlayer(level, saveData, player, state, storyTicks)) {
                active = true;
            }
        }

        for (ServerPlayer player : level.players()) {
            if (!saveData.worldzero$players.containsKey(player.getUUID())) {
                PlayerState state = new PlayerState();
                state.worldzero$nextAttemptTick = worldzero$getInitialAttemptTick(level, storyTicks);
                saveData.worldzero$players.put(player.getUUID(), state);
                saveData.setDirty();
                if (worldzero$tickPlayer(level, saveData, player, state, storyTicks)) {
                    active = true;
                }
            }
        }

        saveData.worldzero$active = active;
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
    }

    public static boolean worldzero$isActive(MinecraftServer server) {
        if (server == null) {
            return false;
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return false;
        }

        return worldzero$hasActiveState(worldzero$getSaveData(overworld));
    }

    public static boolean worldzero$stopNow(MinecraftServer server) {
        if (server == null) {
            return false;
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return false;
        }

        SkyWatchSaveData saveData = worldzero$getSaveData(overworld);
        long storyTicks = WorldZeroStoryTime.worldzero$getStoryTicks(overworld);
        boolean changed = false;
        for (Map.Entry<UUID, PlayerState> entry : saveData.worldzero$players.entrySet()) {
            PlayerState state = entry.getValue();
            if (state.worldzero$stage != WORLDZERO_STAGE_PRELUDE && state.worldzero$stage != WORLDZERO_STAGE_RUNNING) {
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                WorldZeroNetwork.sendSkyWatch(player, WorldZeroSkyWatchPacket.WORLDZERO_ACTION_CLEAR, 0, 0);
            }
            state.worldzero$stage = WORLDZERO_STAGE_IDLE;
            state.worldzero$nextAttemptTick = worldzero$scheduleNextAttemptTick(overworld, storyTicks);
            state.worldzero$preludeEndTick = -1L;
            state.worldzero$clearTick = -1L;
            state.worldzero$eventEndTick = -1L;
            changed = true;
        }

        if (changed) {
            saveData.worldzero$active = false;
            saveData.setDirty();
        }
        return changed;
    }

    public static boolean worldzero$triggerNow(ServerPlayer player) {
        if (worldzero$getDebugTriggerBlocker(player) != null) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        SkyWatchSaveData saveData = worldzero$getSaveData(level);

        if (!worldzero$sendChatLineNow(player, WORLDZERO_NOTICE_KEY)) {
            return false;
        }

        PlayerState state = new PlayerState();
        state.worldzero$stage = WORLDZERO_STAGE_PRELUDE;
        state.worldzero$completed = false;
        state.worldzero$seed = level.random.nextInt();
        state.worldzero$nextAttemptTick = -1L;
        state.worldzero$preludeEndTick = level.getGameTime() + WORLDZERO_PRELUDE_DELAY_TICKS;
        state.worldzero$eventEndTick = -1L;
        saveData.worldzero$players.put(player.getUUID(), state);
        saveData.worldzero$active = true;
        saveData.setDirty();
        return true;
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
            return "player is not considered underground";
        }

        if (worldzero$isInsideHouse(player)) {
            return "player is inside a house";
        }

        if (worldzero$hasConflictingEvent(level)) {
            return "another event is active";
        }

        if (worldzero$hasActiveState(worldzero$getSaveData(level))) {
            return "sky-watch is already active";
        }

        return null;
    }

    private static boolean worldzero$tickPlayer(
            ServerLevel level,
            SkyWatchSaveData saveData,
            @Nullable ServerPlayer player,
            PlayerState state,
            long storyTicks
    ) {
        long gameTicks = level.getGameTime();
        if (state.worldzero$stage == WORLDZERO_STAGE_DONE || state.worldzero$completed) {
            return false;
        }

        if (player == null || !player.isAlive() || player.isSpectator() || player.serverLevel().dimension() != Level.OVERWORLD) {
            if (state.worldzero$stage == WORLDZERO_STAGE_PRELUDE || state.worldzero$stage == WORLDZERO_STAGE_RUNNING) {
                state.worldzero$stage = WORLDZERO_STAGE_IDLE;
                state.worldzero$preludeEndTick = -1L;
                state.worldzero$clearTick = -1L;
                state.worldzero$eventEndTick = -1L;
                saveData.setDirty();
            }
            return state.worldzero$stage == WORLDZERO_STAGE_PRELUDE || state.worldzero$stage == WORLDZERO_STAGE_RUNNING;
        }

        if (state.worldzero$stage == WORLDZERO_STAGE_PRELUDE) {
            if (gameTicks < state.worldzero$preludeEndTick) {
                return true;
            }

            state.worldzero$stage = WORLDZERO_STAGE_RUNNING;
            state.worldzero$clearTick = gameTicks + WORLDZERO_SOUND_STAGE_TICKS;
            state.worldzero$eventEndTick = gameTicks + WORLDZERO_DURATION_TICKS;
            state.worldzero$startX = player.getX();
            state.worldzero$startY = player.getY();
            state.worldzero$startZ = player.getZ();
            state.worldzero$seed = level.random.nextInt();
            WorldZeroNetwork.sendSkyWatch(
                    player,
                    WorldZeroSkyWatchPacket.WORLDZERO_ACTION_START,
                    WORLDZERO_SOUND_STAGE_TICKS,
                    state.worldzero$seed
            );
            saveData.setDirty();
            return true;
        }

        if (state.worldzero$stage == WORLDZERO_STAGE_RUNNING) {
            if (state.worldzero$clearTick >= 0L && gameTicks < state.worldzero$clearTick) {
                return true;
            }

            if (state.worldzero$clearTick >= 0L) {
                WorldZeroNetwork.sendSkyWatch(player, WorldZeroSkyWatchPacket.WORLDZERO_ACTION_CLEAR, 0, 0);
                state.worldzero$clearTick = -1L;
                saveData.setDirty();
            }

            if (state.worldzero$eventEndTick < 0L || gameTicks < state.worldzero$eventEndTick) {
                return true;
            }

            boolean success = worldzero$isSheltered(player.serverLevel(), player)
                    || player.position().distanceToSqr(state.worldzero$startX, state.worldzero$startY, state.worldzero$startZ)
                    >= WORLDZERO_ESCAPE_DISTANCE_SQR;
            if (!worldzero$sendChatLineNow(player, success ? WORLDZERO_SUCCESS_KEY : WORLDZERO_FAILURE_KEY)) {
                state.worldzero$eventEndTick = gameTicks + 20L;
                saveData.setDirty();
                return true;
            }
            state.worldzero$stage = WORLDZERO_STAGE_DONE;
            state.worldzero$completed = true;
            state.worldzero$clearTick = -1L;
            state.worldzero$eventEndTick = -1L;
            saveData.setDirty();
            return false;
        }

        if (storyTicks < WORLDZERO_WINDOW_START_TICKS || storyTicks >= WORLDZERO_ABSOLUTE_WINDOW_END_TICKS) {
            if (storyTicks >= WORLDZERO_ABSOLUTE_WINDOW_END_TICKS) {
                state.worldzero$stage = WORLDZERO_STAGE_DONE;
                state.worldzero$completed = true;
                saveData.setDirty();
            }
            return false;
        }

        if (storyTicks >= WORLDZERO_PRIMARY_WINDOW_END_TICKS
                && state.worldzero$nextAttemptTick >= 0L
                && state.worldzero$nextAttemptTick < WORLDZERO_PRIMARY_WINDOW_END_TICKS) {
            state.worldzero$nextAttemptTick = worldzero$scheduleLateAttemptTick(level, storyTicks);
            saveData.setDirty();
            return false;
        }

        if (state.worldzero$nextAttemptTick < 0L) {
            state.worldzero$nextAttemptTick = worldzero$getInitialAttemptTick(level, storyTicks);
            saveData.setDirty();
            return false;
        }

        if (storyTicks < state.worldzero$nextAttemptTick) {
            return false;
        }

        if (!WorldZeroStoryTime.worldzero$canReceiveStoryEvent(player)
                || !worldzero$isUnderground(level, player)
                || worldzero$isInsideHouse(player)
                || worldzero$hasConflictingEvent(level)) {
            state.worldzero$nextAttemptTick = worldzero$scheduleNextAttemptTick(level, storyTicks);
            saveData.setDirty();
            return false;
        }

        if (worldzero$sendChatLineNow(player, WORLDZERO_NOTICE_KEY)) {
            state.worldzero$stage = WORLDZERO_STAGE_PRELUDE;
            state.worldzero$preludeEndTick = gameTicks + WORLDZERO_PRELUDE_DELAY_TICKS;
            saveData.setDirty();
            return true;
        }

        state.worldzero$nextAttemptTick = worldzero$scheduleNextAttemptTick(level, storyTicks);
        saveData.setDirty();
        return false;
    }

    private static boolean worldzero$sendChatLineNow(ServerPlayer player, String messageKey) {
        if (player == null || messageKey == null || messageKey.isBlank()) {
            return false;
        }

        String speaker = WorldZeroDoubleChatEvent.worldzero$getSpeakerName(player);
        if (speaker == null || speaker.isBlank()) {
            return false;
        }

        WorldZeroNetwork.sendLocalizedChatLine(player, speaker, messageKey);
        return true;
    }

    private static boolean worldzero$hasConflictingEvent(ServerLevel level) {
        return level == null
                || level.isClientSide()
                || WorldZeroFreezeEvent.worldzero$isFreezeActive(level.getServer())
                || WorldZeroFallEvent.worldzero$isFallActive(level.getServer())
                || WorldZeroFootstepsEvent.worldzero$isFootstepsActive(level.getServer())
                || WorldZeroHouseEvent.worldzero$isHouseActive(level.getServer())
                || WorldZeroParalysisEvent.worldzero$isParalysisActive(level.getServer())
                || WorldZeroHorrorFinale.worldzero$isActive(level.getServer())
                || WorldZeroMajorEventSystem.worldzero$isMajorEventActive(level.getServer())
                || WorldZeroHorrorEventSystem.worldzero$isMinorAnomalyActive(level)
                || WorldZeroStalkerEvent.worldzero$isActive(level.getServer());
    }

    private static boolean worldzero$isUnderground(ServerLevel level, ServerPlayer player) {
        BlockPos feetPos = player.blockPosition();
        BlockPos eyePos = BlockPos.containing(player.getEyePosition());
        if (level.canSeeSky(feetPos) || level.canSeeSky(eyePos)) {
            return false;
        }

        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feetPos.getX(), feetPos.getZ());
        return surfaceY - feetPos.getY() >= WORLDZERO_MIN_SURFACE_DEPTH;
    }

    private static boolean worldzero$isInsideHouse(ServerPlayer player) {
        return player != null && WorldZeroHouseDetector.worldzero$findContainingHouse(player) != null;
    }

    private static boolean worldzero$isSheltered(ServerLevel level, ServerPlayer player) {
        BlockPos eyePos = BlockPos.containing(player.getEyePosition());
        return !level.canSeeSky(eyePos) && worldzero$hasLowCeiling(level, eyePos);
    }

    private static boolean worldzero$hasLowCeiling(ServerLevel level, BlockPos eyePos) {
        for (int offset = 1; offset <= 3; offset++) {
            if (level.getBlockState(eyePos.above(offset)).canOcclude()) {
                return true;
            }
        }
        return false;
    }

    private static long worldzero$randomBetween(ServerLevel level, long minValue, long maxValue) {
        if (minValue >= maxValue) {
            return minValue;
        }

        long bound = maxValue - minValue + 1L;
        return minValue + Math.floorMod(level.random.nextLong(), bound);
    }

    private static long worldzero$getInitialAttemptTick(ServerLevel level, long storyTicks) {
        long latestPrimaryTick = WORLDZERO_PRIMARY_WINDOW_END_TICKS - WORLDZERO_TICKS_PER_MINUTE;
        if (storyTicks < WORLDZERO_WINDOW_START_TICKS) {
            return worldzero$randomBetween(level, WORLDZERO_WINDOW_START_TICKS, latestPrimaryTick);
        }

        if (storyTicks < latestPrimaryTick) {
            return worldzero$randomBetween(level, storyTicks, latestPrimaryTick);
        }

        return worldzero$scheduleLateAttemptTick(level, Math.max(storyTicks, WORLDZERO_PRIMARY_WINDOW_END_TICKS));
    }

    private static long worldzero$scheduleNextAttemptTick(ServerLevel level, long storyTicks) {
        if (storyTicks < WORLDZERO_PRIMARY_WINDOW_END_TICKS) {
            long nextAttemptTick = storyTicks + worldzero$randomBetween(level, WORLDZERO_RETRY_MIN_TICKS, WORLDZERO_RETRY_MAX_TICKS);
            if (nextAttemptTick < WORLDZERO_PRIMARY_WINDOW_END_TICKS) {
                return nextAttemptTick;
            }
            return worldzero$scheduleLateAttemptTick(level, WORLDZERO_PRIMARY_WINDOW_END_TICKS);
        }

        return worldzero$scheduleLateAttemptTick(level, storyTicks);
    }

    private static long worldzero$scheduleLateAttemptTick(ServerLevel level, long storyTicks) {
        return Math.min(
                WORLDZERO_ABSOLUTE_WINDOW_END_TICKS,
                storyTicks + worldzero$randomBetween(level, WORLDZERO_LATE_RETRY_MIN_TICKS, WORLDZERO_LATE_RETRY_MAX_TICKS)
        );
    }

    private static SkyWatchSaveData worldzero$getSaveData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                SkyWatchSaveData::worldzero$load,
                SkyWatchSaveData::new,
                WORLDZERO_SAVE_ID
        );
    }

    private static boolean worldzero$hasActiveState(SkyWatchSaveData saveData) {
        for (PlayerState state : saveData.worldzero$players.values()) {
            if (state.worldzero$stage == WORLDZERO_STAGE_PRELUDE || state.worldzero$stage == WORLDZERO_STAGE_RUNNING) {
                return true;
            }
        }
        return false;
    }

    private static final class SkyWatchSaveData extends SavedData {
        private final Map<UUID, PlayerState> worldzero$players = new HashMap<>();
        private boolean worldzero$active;

        private static SkyWatchSaveData worldzero$load(CompoundTag tag) {
            SkyWatchSaveData saveData = new SkyWatchSaveData();
            CompoundTag playersTag = tag.getCompound("players");
            for (String key : playersTag.getAllKeys()) {
                try {
                    saveData.worldzero$players.put(UUID.fromString(key), PlayerState.worldzero$load(playersTag.getCompound(key)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            saveData.worldzero$active = false;
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

    private static final class PlayerState {
        private boolean worldzero$completed;
        private int worldzero$stage;
        private int worldzero$seed;
        private long worldzero$nextAttemptTick = -1L;
        private long worldzero$preludeEndTick = -1L;
        private long worldzero$clearTick = -1L;
        private long worldzero$eventEndTick = -1L;
        private double worldzero$startX;
        private double worldzero$startY;
        private double worldzero$startZ;

        private static PlayerState worldzero$load(CompoundTag tag) {
            PlayerState state = new PlayerState();
            state.worldzero$completed = tag.getBoolean("completed");
            state.worldzero$stage = tag.getInt("stage");
            state.worldzero$seed = tag.getInt("seed");
            state.worldzero$nextAttemptTick = tag.contains("next_attempt_tick") ? tag.getLong("next_attempt_tick") : -1L;
            state.worldzero$preludeEndTick = tag.contains("prelude_end_tick") ? tag.getLong("prelude_end_tick") : -1L;
            state.worldzero$clearTick = tag.contains("clear_tick") ? tag.getLong("clear_tick") : -1L;
            state.worldzero$eventEndTick = tag.contains("event_end_tick") ? tag.getLong("event_end_tick") : -1L;
            state.worldzero$startX = tag.getDouble("start_x");
            state.worldzero$startY = tag.getDouble("start_y");
            state.worldzero$startZ = tag.getDouble("start_z");
            if (state.worldzero$completed) {
                state.worldzero$stage = WORLDZERO_STAGE_DONE;
            }
            return state;
        }

        private CompoundTag worldzero$save() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("completed", this.worldzero$completed);
            tag.putInt("stage", this.worldzero$stage);
            tag.putInt("seed", this.worldzero$seed);
            tag.putLong("next_attempt_tick", this.worldzero$nextAttemptTick);
            tag.putLong("prelude_end_tick", this.worldzero$preludeEndTick);
            tag.putLong("clear_tick", this.worldzero$clearTick);
            tag.putLong("event_end_tick", this.worldzero$eventEndTick);
            tag.putDouble("start_x", this.worldzero$startX);
            tag.putDouble("start_y", this.worldzero$startY);
            tag.putDouble("start_z", this.worldzero$startZ);
            return tag;
        }
    }
}
