package ru.nekostul.worldzero;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroLateChatEvent {
    private static final String WORLDZERO_SAVE_ID = "worldzero_late_chat_event";
    private static final long WORLDZERO_TICKS_PER_MINUTE = 20L * 60L;
    private static final long WORLDZERO_START_TICKS = 55L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_IDLE_MIN_TICKS = 56L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_IDLE_MAX_TICKS = 60L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_IDLE_REPLY_WAIT_TICKS = 30L * 20L;
    private static final long WORLDZERO_LATE_END_TICKS = 180L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_NORMAL_DELAY_MIN_TICKS = 30L * 20L;
    private static final long WORLDZERO_NORMAL_DELAY_MAX_TICKS = 120L * 20L;
    private static final long WORLDZERO_LONG_DELAY_MIN_TICKS = 2L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_LONG_DELAY_MAX_TICKS = 5L * WORLDZERO_TICKS_PER_MINUTE;
    private static final String WORLDZERO_IDLE_KEY = "message.worldzero.double_chat.idle";
    private static final String WORLDZERO_IDLE_AUTO_REPLY_KEY = "message.worldzero.double_chat.idle_auto_reply";
    private static final String[] WORLDZERO_LATE_MESSAGE_KEYS = {
            "message.worldzero.double_chat.late.0",
            "message.worldzero.double_chat.late.1",
            "message.worldzero.double_chat.late.2",
            "message.worldzero.double_chat.late.3",
            "message.worldzero.double_chat.late.4",
            "message.worldzero.double_chat.late.5",
            "message.worldzero.double_chat.late.6",
            "message.worldzero.double_chat.late.7",
            "message.worldzero.double_chat.late.8",
            "message.worldzero.double_chat.late.9",
            "message.worldzero.double_chat.late.10",
            "message.worldzero.double_chat.late.11",
            "message.worldzero.double_chat.late.12",
            "message.worldzero.double_chat.late.13",
            "message.worldzero.double_chat.late.14",
            "message.worldzero.double_chat.late.15",
            "message.worldzero.double_chat.late.16",
            "message.worldzero.double_chat.late.17",
            "message.worldzero.double_chat.late.18",
            "message.worldzero.double_chat.late.19",
            "message.worldzero.double_chat.late.20",
            "message.worldzero.double_chat.late.21",
            "message.worldzero.double_chat.late.22",
            "message.worldzero.double_chat.late.23",
            "message.worldzero.double_chat.late.24",
            "message.worldzero.double_chat.late.25",
            "message.worldzero.double_chat.late.26",
            "message.worldzero.double_chat.late.27"
    };
    private static final int[] WORLDZERO_LATE_START_MINUTES = {
            61, 64, 67, 69,
            72, 77, 83, 88,
            91, 96, 102, 108,
            111, 117, 123, 128,
            131, 137, 142, 148,
            151, 157, 163, 168,
            171, 174, 177, 179
    };
    private static final int[] WORLDZERO_LATE_END_MINUTES = {
            63, 66, 69, 71,
            74, 79, 85, 90,
            93, 98, 104, 110,
            113, 119, 125, 130,
            133, 139, 144, 150,
            153, 159, 165, 170,
            173, 176, 179, 180
    };

    private WorldZeroLateChatEvent() {
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
        if (storyTicks < WORLDZERO_START_TICKS) {
            return;
        }

        LateChatSaveData saveData = worldzero$getSaveData(level);
        for (ServerPlayer player : level.players()) {
            if (player == null || !player.isAlive() || player.isSpectator()) {
                continue;
            }

            worldzero$tickPlayer(level, saveData, player, storyTicks);
        }
    }

    @SubscribeEvent
    public static void worldzero$onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player == null || player.serverLevel().dimension() != Level.OVERWORLD) {
            return;
        }

        String message = event.getRawText();
        if (message == null || message.isBlank()) {
            return;
        }

        ServerLevel level = player.serverLevel();
        LateChatSaveData saveData = worldzero$getSaveData(level);
        PlayerState state = saveData.worldzero$players.get(player.getUUID());
        if (state == null || !state.worldzero$idleWaitingReply) {
            return;
        }

        long storyTicks = WorldZeroStoryTime.worldzero$getStoryTicks(level);
        state.worldzero$idleWaitingReply = false;
        state.worldzero$idleResolved = true;
        state.worldzero$nextLateMessageTick = storyTicks + worldzero$randomDelayTicks(level);
        saveData.setDirty();
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
    }

    private static void worldzero$tickPlayer(
            ServerLevel level,
            LateChatSaveData saveData,
            ServerPlayer player,
            long storyTicks
    ) {
        if (WorldZeroSkyWatchEvent.worldzero$isActive(level.getServer())) {
            return;
        }

        PlayerState state = worldzero$getOrCreateState(saveData, player, storyTicks);
        if (!state.worldzero$idleSent) {
            if (storyTicks < state.worldzero$idleTriggerTick || !WorldZeroStoryTime.worldzero$canReceiveStoryEvent(player)) {
                return;
            }

            if (WorldZeroDoubleChatEvent.worldzero$sendSpeakerLineNow(player, WORLDZERO_IDLE_KEY)) {
                state.worldzero$idleSent = true;
                state.worldzero$idleWaitingReply = true;
                state.worldzero$idleReplyDeadlineTick = storyTicks + WORLDZERO_IDLE_REPLY_WAIT_TICKS;
                saveData.setDirty();
            }
            return;
        }

        if (state.worldzero$idleWaitingReply) {
            if (storyTicks < state.worldzero$idleReplyDeadlineTick || !WorldZeroStoryTime.worldzero$canReceiveStoryEvent(player)) {
                return;
            }

            if (WorldZeroDoubleChatEvent.worldzero$sendAutoSelfLineNow(player, WORLDZERO_IDLE_AUTO_REPLY_KEY)) {
                state.worldzero$idleWaitingReply = false;
                state.worldzero$idleResolved = true;
                state.worldzero$nextLateMessageTick = storyTicks + worldzero$randomDelayTicks(level);
                saveData.setDirty();
            }
            return;
        }

        if (!state.worldzero$idleResolved || storyTicks > WORLDZERO_LATE_END_TICKS) {
            return;
        }

        if (state.worldzero$nextLateMessageTick < 0L) {
            state.worldzero$nextLateMessageTick = Math.max(
                    60L * WORLDZERO_TICKS_PER_MINUTE,
                    storyTicks + worldzero$randomDelayTicks(level)
            );
            saveData.setDirty();
            return;
        }

        if (storyTicks < state.worldzero$nextLateMessageTick || !WorldZeroStoryTime.worldzero$canReceiveStoryEvent(player)) {
            return;
        }

        int messageIndex = worldzero$pickLateMessage(level, state, storyTicks);
        if (messageIndex < 0) {
            long nextTick = worldzero$getNextAvailableTick(state, storyTicks);
            if (nextTick >= 0L) {
                state.worldzero$nextLateMessageTick = nextTick;
                saveData.setDirty();
            }
            return;
        }

        if (WorldZeroDoubleChatEvent.worldzero$sendSpeakerLineNow(player, WORLDZERO_LATE_MESSAGE_KEYS[messageIndex])) {
            state.worldzero$usedLateMessageMask |= (1L << messageIndex);
            state.worldzero$lastLateMessageIndex = messageIndex;
            state.worldzero$nextLateMessageTick = storyTicks + worldzero$randomDelayTicks(level);
            saveData.setDirty();
        }
    }

    private static int worldzero$pickLateMessage(ServerLevel level, PlayerState state, long storyTicks) {
        int[] candidates = new int[WORLDZERO_LATE_MESSAGE_KEYS.length];
        int count = 0;
        for (int index = 0; index < WORLDZERO_LATE_MESSAGE_KEYS.length; index++) {
            if ((state.worldzero$usedLateMessageMask & (1L << index)) != 0L) {
                continue;
            }

            long startTick = WORLDZERO_LATE_START_MINUTES[index] * WORLDZERO_TICKS_PER_MINUTE;
            long endTick = WORLDZERO_LATE_END_MINUTES[index] * WORLDZERO_TICKS_PER_MINUTE;
            if (storyTicks < startTick || storyTicks > endTick) {
                continue;
            }
            if (index == state.worldzero$lastLateMessageIndex) {
                continue;
            }

            candidates[count++] = index;
        }

        if (count == 0) {
            return -1;
        }

        return candidates[level.random.nextInt(count)];
    }

    private static long worldzero$getNextAvailableTick(PlayerState state, long storyTicks) {
        long nextTick = -1L;
        for (int index = 0; index < WORLDZERO_LATE_MESSAGE_KEYS.length; index++) {
            if ((state.worldzero$usedLateMessageMask & (1L << index)) != 0L) {
                continue;
            }

            long startTick = WORLDZERO_LATE_START_MINUTES[index] * WORLDZERO_TICKS_PER_MINUTE;
            if (startTick <= storyTicks) {
                continue;
            }
            if (nextTick < 0L || startTick < nextTick) {
                nextTick = startTick;
            }
        }
        return nextTick;
    }

    private static long worldzero$randomDelayTicks(ServerLevel level) {
        return level.random.nextInt(5) == 0
                ? worldzero$randomRange(level, WORLDZERO_LONG_DELAY_MIN_TICKS, WORLDZERO_LONG_DELAY_MAX_TICKS)
                : worldzero$randomRange(level, WORLDZERO_NORMAL_DELAY_MIN_TICKS, WORLDZERO_NORMAL_DELAY_MAX_TICKS);
    }

    private static PlayerState worldzero$getOrCreateState(LateChatSaveData saveData, ServerPlayer player, long storyTicks) {
        PlayerState state = saveData.worldzero$players.get(player.getUUID());
        if (state != null) {
            return state;
        }

        state = new PlayerState();
        state.worldzero$idleTriggerTick = worldzero$randomRange(player.serverLevel(), WORLDZERO_IDLE_MIN_TICKS, WORLDZERO_IDLE_MAX_TICKS);
        if (storyTicks > state.worldzero$idleTriggerTick) {
            state.worldzero$idleTriggerTick = storyTicks + 20L;
        }
        saveData.worldzero$players.put(player.getUUID(), state);
        saveData.setDirty();
        return state;
    }

    private static long worldzero$randomRange(ServerLevel level, long minValue, long maxValue) {
        if (minValue >= maxValue) {
            return minValue;
        }

        long bound = maxValue - minValue + 1L;
        return minValue + Math.floorMod(level.random.nextLong(), bound);
    }

    private static LateChatSaveData worldzero$getSaveData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                LateChatSaveData::worldzero$load,
                LateChatSaveData::new,
                WORLDZERO_SAVE_ID
        );
    }

    private static final class LateChatSaveData extends SavedData {
        private final Map<UUID, PlayerState> worldzero$players = new HashMap<>();

        private static LateChatSaveData worldzero$load(CompoundTag tag) {
            LateChatSaveData saveData = new LateChatSaveData();
            CompoundTag playersTag = tag.getCompound("players");
            for (String key : playersTag.getAllKeys()) {
                try {
                    saveData.worldzero$players.put(UUID.fromString(key), PlayerState.worldzero$load(playersTag.getCompound(key)));
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

    private static final class PlayerState {
        private boolean worldzero$idleSent;
        private boolean worldzero$idleWaitingReply;
        private boolean worldzero$idleResolved;
        private int worldzero$lastLateMessageIndex = -1;
        private long worldzero$idleTriggerTick = -1L;
        private long worldzero$idleReplyDeadlineTick = -1L;
        private long worldzero$nextLateMessageTick = -1L;
        private long worldzero$usedLateMessageMask;

        private static PlayerState worldzero$load(CompoundTag tag) {
            PlayerState state = new PlayerState();
            state.worldzero$idleSent = tag.getBoolean("idle_sent");
            state.worldzero$idleWaitingReply = tag.getBoolean("idle_waiting_reply");
            state.worldzero$idleResolved = tag.getBoolean("idle_resolved");
            state.worldzero$lastLateMessageIndex = tag.getInt("last_late_message_index");
            state.worldzero$idleTriggerTick = tag.getLong("idle_trigger_tick");
            state.worldzero$idleReplyDeadlineTick = tag.getLong("idle_reply_deadline_tick");
            state.worldzero$nextLateMessageTick = tag.getLong("next_late_message_tick");
            state.worldzero$usedLateMessageMask = tag.getLong("used_late_message_mask");
            return state;
        }

        private CompoundTag worldzero$save() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("idle_sent", this.worldzero$idleSent);
            tag.putBoolean("idle_waiting_reply", this.worldzero$idleWaitingReply);
            tag.putBoolean("idle_resolved", this.worldzero$idleResolved);
            tag.putInt("last_late_message_index", this.worldzero$lastLateMessageIndex);
            tag.putLong("idle_trigger_tick", this.worldzero$idleTriggerTick);
            tag.putLong("idle_reply_deadline_tick", this.worldzero$idleReplyDeadlineTick);
            tag.putLong("next_late_message_tick", this.worldzero$nextLateMessageTick);
            tag.putLong("used_late_message_mask", this.worldzero$usedLateMessageMask);
            return tag;
        }
    }
}
