package ru.nekostul.worldzero;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroDoubleChatEvent {
    private static final String WORLDZERO_SAVE_ID = "worldzero_double_chat_event";
    private static final long WORLDZERO_TICKS_PER_MINUTE = 20L * 60L;
    private static final long WORLDZERO_LOCAL_PORT_AT_TICKS = WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_JOIN_AT_TICKS = 15L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_PANIC_AT_TICKS = 55L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_FIRST_HELLO_DELAY_TICKS = 45L;
    private static final long WORLDZERO_IGNORE_DELAY_TICKS = WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_AUTO_REPLY_WAIT_TICKS = 30L * 20L;
    private static final long WORLDZERO_DIALOGUE_FAST_REPLY_DELAY_TICKS = 45L;
    private static final long WORLDZERO_DIALOGUE_LINE_DELAY_TICKS = 30L * 20L;
    private static final int WORLDZERO_LOCAL_PORT_MIN = 50000;
    private static final int WORLDZERO_LOCAL_PORT_MAX_EXCLUSIVE = 60000;
    private static final String WORLDZERO_MUTATED_NAME_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789_";
    private static final String WORLDZERO_MUTATED_NAME_CHARS_UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
    private static final String WORLDZERO_PLAYER_GREETING_TRIGGER = "привет";
    private static final int WORLDZERO_STAGE_WAITING_JOIN = 0;
    private static final int WORLDZERO_STAGE_WAITING_FIRST_HELLO = 1;
    private static final int WORLDZERO_STAGE_WAITING_FIRST_REPLY = 2;
    private static final int WORLDZERO_STAGE_WAITING_SECOND_REPLY = 3;
    private static final int WORLDZERO_STAGE_WAITING_AUTO_REPLY = 4;
    private static final int WORLDZERO_STAGE_WAITING_OPENING_REPLY = 5;
    private static final int WORLDZERO_STAGE_DIALOGUE_RUNNING = 6;
    private static final int WORLDZERO_STAGE_DIALOGUE_COMPLETE = 7;
    private static final int WORLDZERO_STAGE_COMPLETED = 8;
    private static final String WORLDZERO_JOINED_KEY = "message.worldzero.double_chat.joined";
    private static final String WORLDZERO_LEFT_KEY = "message.worldzero.double_chat.left";
    private static final String WORLDZERO_GREETING_KEY = "message.worldzero.double_chat.greeting";
    private static final String WORLDZERO_SECOND_GREETING_KEY = "message.worldzero.double_chat.second_greeting";
    private static final String WORLDZERO_AUTO_GREETING_KEY = "message.worldzero.double_chat.auto_greeting";
    private static final String WORLDZERO_FINAL_PANIC_KEY = "message.worldzero.double_chat.panic";
    private static final String[] WORLDZERO_DIALOGUE_KEYS = {
            "message.worldzero.double_chat.dialogue.0",
            "message.worldzero.double_chat.dialogue.1",
            "message.worldzero.double_chat.dialogue.2",
            "message.worldzero.double_chat.dialogue.3",
            "message.worldzero.double_chat.dialogue.4",
            "message.worldzero.double_chat.dialogue.5",
            "message.worldzero.double_chat.dialogue.6",
            "message.worldzero.double_chat.dialogue.7",
            "message.worldzero.double_chat.dialogue.8",
            "message.worldzero.double_chat.dialogue.9"
    };
    private static final long[] WORLDZERO_PROGRESS_TICKS = {
            24L * WORLDZERO_TICKS_PER_MINUTE,
            30L * WORLDZERO_TICKS_PER_MINUTE,
            36L * WORLDZERO_TICKS_PER_MINUTE,
            42L * WORLDZERO_TICKS_PER_MINUTE,
            47L * WORLDZERO_TICKS_PER_MINUTE,
            52L * WORLDZERO_TICKS_PER_MINUTE,
            54L * WORLDZERO_TICKS_PER_MINUTE
    };
    private static final String[] WORLDZERO_PROGRESS_KEYS = {
            "message.worldzero.double_chat.progress.0",
            "message.worldzero.double_chat.progress.1",
            "message.worldzero.double_chat.progress.2",
            "message.worldzero.double_chat.progress.3",
            "message.worldzero.double_chat.progress.4",
            "message.worldzero.double_chat.progress.5",
            "message.worldzero.double_chat.progress.6"
    };

    private WorldZeroDoubleChatEvent() {
    }

    @SubscribeEvent
    public static void worldzero$onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.level instanceof ServerLevel level) || level.isClientSide() || level.dimension() != Level.OVERWORLD) {
            return;
        }

        DoubleChatSaveData saveData = worldzero$getSaveData(level);
        long storyTicks = WorldZeroStoryTime.worldzero$getStoryTicks(level);
        for (ServerPlayer player : level.players()) {
            worldzero$tickPlayer(level, saveData, player, storyTicks);
        }
    }

    @SubscribeEvent
    public static void worldzero$onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) {
            return;
        }

        ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        DoubleChatSaveData saveData = worldzero$getSaveData(overworld);
        PlayerState state = worldzero$getOrCreateState(saveData, player.getUUID(), WorldZeroStoryTime.worldzero$getStoryTicks(overworld));
        if (worldzero$ensureScenarioData(state, player, overworld)) {
            saveData.setDirty();
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

        if (!WORLDZERO_PLAYER_GREETING_TRIGGER.equals(message.trim().toLowerCase())) {
            return;
        }

        ServerLevel level = player.serverLevel();
        DoubleChatSaveData saveData = worldzero$getSaveData(level);
        PlayerState state = saveData.worldzero$states.get(player.getUUID());
        if (state == null || state.worldzero$stage == WORLDZERO_STAGE_COMPLETED || state.worldzero$left) {
            return;
        }

        long storyTicks = WorldZeroStoryTime.worldzero$getStoryTicks(level);
        switch (state.worldzero$stage) {
            case WORLDZERO_STAGE_WAITING_FIRST_REPLY,
                    WORLDZERO_STAGE_WAITING_SECOND_REPLY,
                    WORLDZERO_STAGE_WAITING_AUTO_REPLY -> {
                state.worldzero$dialogueIndex = 0;
                state.worldzero$stage = WORLDZERO_STAGE_WAITING_OPENING_REPLY;
                state.worldzero$nextStoryTick = storyTicks + WORLDZERO_DIALOGUE_FAST_REPLY_DELAY_TICKS;
                saveData.setDirty();
            }
            default -> {
            }
        }
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
    }

    public static boolean worldzero$triggerNow(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator() || player.serverLevel().dimension() != Level.OVERWORLD) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        DoubleChatSaveData saveData = worldzero$getSaveData(level);
        PlayerState state = new PlayerState();
        worldzero$ensureScenarioData(state, player, level);
        state.worldzero$joined = true;
        state.worldzero$stage = WORLDZERO_STAGE_WAITING_FIRST_HELLO;
        state.worldzero$nextStoryTick = WorldZeroStoryTime.worldzero$getStoryTicks(level) + WORLDZERO_FIRST_HELLO_DELAY_TICKS;
        saveData.worldzero$states.put(player.getUUID(), state);
        saveData.setDirty();
        worldzero$sendSystemLine(player, state.worldzero$fakeName, WORLDZERO_JOINED_KEY);
        return true;
    }

    public static boolean worldzero$resetNow(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return false;
        }

        ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return false;
        }

        DoubleChatSaveData saveData = worldzero$getSaveData(overworld);
        boolean removed = saveData.worldzero$states.remove(player.getUUID()) != null;
        if (removed) {
            saveData.setDirty();
        }
        return removed;
    }

    private static void worldzero$tickPlayer(
            ServerLevel level,
            DoubleChatSaveData saveData,
            ServerPlayer player,
            long storyTicks
    ) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return;
        }

        PlayerState state = worldzero$getOrCreateState(saveData, player.getUUID(), storyTicks);
        if (worldzero$ensureScenarioData(state, player, level)) {
            saveData.setDirty();
        }
        if (state.worldzero$stage == WORLDZERO_STAGE_COMPLETED || state.worldzero$left) {
            return;
        }

        if (!state.worldzero$localPortSent && storyTicks >= WORLDZERO_LOCAL_PORT_AT_TICKS) {
            worldzero$sendLocalPortLine(player, Integer.toString(state.worldzero$localPort));
            state.worldzero$localPortSent = true;
            saveData.setDirty();
        }

        if (storyTicks >= WORLDZERO_PANIC_AT_TICKS) {
            if (state.worldzero$joined) {
                worldzero$sendPlayerLine(player, state.worldzero$fakeName, WORLDZERO_FINAL_PANIC_KEY);
                worldzero$sendSystemLine(player, state.worldzero$fakeName, WORLDZERO_LEFT_KEY);
            }
            state.worldzero$left = true;
            state.worldzero$stage = WORLDZERO_STAGE_COMPLETED;
            saveData.setDirty();
            return;
        }

        if (!state.worldzero$joined) {
            if (storyTicks < WORLDZERO_JOIN_AT_TICKS) {
                return;
            }

            state.worldzero$joined = true;
            state.worldzero$stage = WORLDZERO_STAGE_WAITING_FIRST_HELLO;
            state.worldzero$nextStoryTick = storyTicks + WORLDZERO_FIRST_HELLO_DELAY_TICKS;
            worldzero$sendSystemLine(player, state.worldzero$fakeName, WORLDZERO_JOINED_KEY);
            saveData.setDirty();
            return;
        }

        switch (state.worldzero$stage) {
            case WORLDZERO_STAGE_WAITING_FIRST_HELLO -> {
                if (storyTicks < state.worldzero$nextStoryTick) {
                    return;
                }

                worldzero$sendPlayerLine(player, state.worldzero$fakeName, WORLDZERO_GREETING_KEY);
                state.worldzero$stage = WORLDZERO_STAGE_WAITING_FIRST_REPLY;
                state.worldzero$nextStoryTick = storyTicks + WORLDZERO_IGNORE_DELAY_TICKS;
                saveData.setDirty();
                return;
            }
            case WORLDZERO_STAGE_WAITING_FIRST_REPLY -> {
                if (storyTicks < state.worldzero$nextStoryTick || !worldzero$canPlayerReply(player)) {
                    return;
                }

                worldzero$sendPlayerLine(player, state.worldzero$fakeName, WORLDZERO_SECOND_GREETING_KEY);
                state.worldzero$stage = WORLDZERO_STAGE_WAITING_SECOND_REPLY;
                state.worldzero$nextStoryTick = storyTicks + WORLDZERO_IGNORE_DELAY_TICKS;
                saveData.setDirty();
                return;
            }
            case WORLDZERO_STAGE_WAITING_SECOND_REPLY -> {
                if (storyTicks < state.worldzero$nextStoryTick || !worldzero$canPlayerReply(player)) {
                    return;
                }

                worldzero$sendAutoPlayerLine(player, state.worldzero$fakeName, WORLDZERO_AUTO_GREETING_KEY);
                state.worldzero$stage = WORLDZERO_STAGE_WAITING_AUTO_REPLY;
                state.worldzero$nextStoryTick = storyTicks + WORLDZERO_AUTO_REPLY_WAIT_TICKS;
                saveData.setDirty();
                return;
            }
            case WORLDZERO_STAGE_WAITING_AUTO_REPLY -> {
                if (storyTicks < state.worldzero$nextStoryTick || !worldzero$canPlayerReply(player)) {
                    return;
                }

                state.worldzero$dialogueIndex = 0;
                state.worldzero$stage = WORLDZERO_STAGE_WAITING_OPENING_REPLY;
                state.worldzero$nextStoryTick = storyTicks + WORLDZERO_DIALOGUE_FAST_REPLY_DELAY_TICKS;
                saveData.setDirty();
                return;
            }
            case WORLDZERO_STAGE_WAITING_OPENING_REPLY -> {
                if (storyTicks < state.worldzero$nextStoryTick) {
                    return;
                }

                worldzero$sendPlayerLine(player, state.worldzero$fakeName, WORLDZERO_DIALOGUE_KEYS[0]);
                state.worldzero$dialogueIndex = 1;
                state.worldzero$stage = WORLDZERO_STAGE_DIALOGUE_RUNNING;
                state.worldzero$nextStoryTick = storyTicks + WORLDZERO_DIALOGUE_LINE_DELAY_TICKS;
                saveData.setDirty();
                return;
            }
            case WORLDZERO_STAGE_DIALOGUE_RUNNING -> {
                if (storyTicks < state.worldzero$nextStoryTick) {
                    return;
                }

                if (state.worldzero$dialogueIndex >= WORLDZERO_DIALOGUE_KEYS.length) {
                    state.worldzero$stage = WORLDZERO_STAGE_DIALOGUE_COMPLETE;
                    saveData.setDirty();
                    break;
                }

                worldzero$sendPlayerLine(
                        player,
                        state.worldzero$fakeName,
                        WORLDZERO_DIALOGUE_KEYS[state.worldzero$dialogueIndex]
                );
                state.worldzero$dialogueIndex++;
                state.worldzero$nextStoryTick = storyTicks + WORLDZERO_DIALOGUE_LINE_DELAY_TICKS;
                if (state.worldzero$dialogueIndex >= WORLDZERO_DIALOGUE_KEYS.length) {
                    state.worldzero$stage = WORLDZERO_STAGE_DIALOGUE_COMPLETE;
                }
                saveData.setDirty();
                return;
            }
            default -> {
            }
        }

        if (state.worldzero$achievementIndex < WORLDZERO_PROGRESS_TICKS.length
                && storyTicks >= WORLDZERO_PROGRESS_TICKS[state.worldzero$achievementIndex]) {
            worldzero$sendPlayerLine(
                    player,
                    state.worldzero$fakeName,
                    WORLDZERO_PROGRESS_KEYS[state.worldzero$achievementIndex]
            );
            state.worldzero$achievementIndex++;
            saveData.setDirty();
        }
    }

    private static PlayerState worldzero$getOrCreateState(DoubleChatSaveData saveData, UUID playerId, long storyTicks) {
        PlayerState state = saveData.worldzero$states.get(playerId);
        if (state != null) {
            return state;
        }

        state = new PlayerState();
        if (storyTicks >= WORLDZERO_PANIC_AT_TICKS) {
            state.worldzero$left = true;
            state.worldzero$stage = WORLDZERO_STAGE_COMPLETED;
            state.worldzero$joined = true;
            state.worldzero$achievementIndex = WORLDZERO_PROGRESS_TICKS.length;
        }
        saveData.worldzero$states.put(playerId, state);
        saveData.setDirty();
        return state;
    }

    private static boolean worldzero$ensureScenarioData(PlayerState state, ServerPlayer player, ServerLevel level) {
        boolean changed = false;
        if (state.worldzero$fakeName == null || state.worldzero$fakeName.isBlank()) {
            state.worldzero$fakeName = worldzero$mutatePlayerName(player.getGameProfile().getName(), level);
            changed = true;
        }
        if (state.worldzero$localPort < WORLDZERO_LOCAL_PORT_MIN || state.worldzero$localPort >= WORLDZERO_LOCAL_PORT_MAX_EXCLUSIVE) {
            state.worldzero$localPort = worldzero$generateLocalPort(level);
            changed = true;
        }
        return changed;
    }

    private static boolean worldzero$canPlayerReply(ServerPlayer player) {
        if (!WorldZeroStoryTime.worldzero$canReceiveStoryEvent(player)) {
            return false;
        }

        MinecraftServer server = player.getServer();
        return server != null
                && !WorldZeroFreezeEvent.worldzero$isFreezeActive(server)
                && !WorldZeroFallEvent.worldzero$isFallActive(server)
                && !WorldZeroParalysisEvent.worldzero$isParalysisActive(server)
                && !WorldZeroHorrorFinale.worldzero$isActive(server);
    }

    private static int worldzero$generateLocalPort(ServerLevel level) {
        if (level == null) {
            return WORLDZERO_LOCAL_PORT_MIN;
        }

        return WORLDZERO_LOCAL_PORT_MIN + level.random.nextInt(WORLDZERO_LOCAL_PORT_MAX_EXCLUSIVE - WORLDZERO_LOCAL_PORT_MIN);
    }

    private static String worldzero$mutatePlayerName(String name, ServerLevel level) {
        if (name == null || name.isBlank()) {
            return "playe5";
        }

        char lastChar = name.charAt(name.length() - 1);
        String variants = Character.isUpperCase(lastChar)
                ? WORLDZERO_MUTATED_NAME_CHARS_UPPER
                : WORLDZERO_MUTATED_NAME_CHARS;
        int originalIndex = variants.indexOf(lastChar);
        char replacement = variants.charAt(0);
        if (level != null) {
            for (int attempt = 0; attempt < 8; attempt++) {
                char candidate = variants.charAt(level.random.nextInt(variants.length()));
                if (candidate != lastChar) {
                    replacement = candidate;
                    break;
                }
            }
        }

        if (replacement == lastChar) {
            int fallbackIndex = originalIndex >= 0 ? (originalIndex + 1) % variants.length() : 1;
            replacement = variants.charAt(fallbackIndex);
        }

        return name.substring(0, name.length() - 1) + replacement;
    }

    private static void worldzero$sendPlayerLine(ServerPlayer player, String speaker, String messageKey) {
        WorldZeroNetwork.sendDoubleChatPlayerLine(player, speaker, messageKey);
    }

    private static void worldzero$sendSystemLine(ServerPlayer player, String speaker, String messageKey) {
        WorldZeroNetwork.sendDoubleChatSystemLine(player, speaker, messageKey);
    }

    private static void worldzero$sendAutoPlayerLine(ServerPlayer player, String speaker, String messageKey) {
        WorldZeroNetwork.sendDoubleChatAutoLine(player, speaker, messageKey);
    }

    private static void worldzero$sendLocalPortLine(ServerPlayer player, String port) {
        WorldZeroNetwork.sendDoubleChatLocalPort(player, port);
    }

    private static DoubleChatSaveData worldzero$getSaveData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                DoubleChatSaveData::worldzero$load,
                DoubleChatSaveData::new,
                WORLDZERO_SAVE_ID
        );
    }

    private static final class DoubleChatSaveData extends SavedData {
        private final Map<UUID, PlayerState> worldzero$states = new HashMap<>();

        private static DoubleChatSaveData worldzero$load(CompoundTag tag) {
            DoubleChatSaveData saveData = new DoubleChatSaveData();
            CompoundTag playersTag = tag.getCompound("players");
            for (String key : playersTag.getAllKeys()) {
                try {
                    saveData.worldzero$states.put(UUID.fromString(key), PlayerState.worldzero$load(playersTag.getCompound(key)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return saveData;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            CompoundTag playersTag = new CompoundTag();
            for (Map.Entry<UUID, PlayerState> entry : this.worldzero$states.entrySet()) {
                playersTag.put(entry.getKey().toString(), entry.getValue().worldzero$save());
            }
            tag.put("players", playersTag);
            return tag;
        }
    }

    private static final class PlayerState {
        private boolean worldzero$joined;
        private boolean worldzero$left;
        private boolean worldzero$localPortSent;
        private int worldzero$stage = WORLDZERO_STAGE_WAITING_JOIN;
        private int worldzero$dialogueIndex;
        private int worldzero$achievementIndex;
        private int worldzero$localPort;
        private long worldzero$nextStoryTick = -1L;
        private String worldzero$fakeName = "";

        private static PlayerState worldzero$load(CompoundTag tag) {
            PlayerState state = new PlayerState();
            state.worldzero$joined = tag.getBoolean("joined");
            state.worldzero$left = tag.getBoolean("left");
            state.worldzero$localPortSent = tag.getBoolean("local_port_sent");
            state.worldzero$stage = tag.getInt("stage");
            state.worldzero$dialogueIndex = Math.max(0, tag.getInt("dialogue_index"));
            state.worldzero$achievementIndex = Math.max(0, tag.getInt("achievement_index"));
            state.worldzero$localPort = tag.getInt("local_port");
            state.worldzero$nextStoryTick = tag.contains("next_story_tick") ? tag.getLong("next_story_tick") : -1L;
            state.worldzero$fakeName = tag.getString("fake_name");
            return state;
        }

        private CompoundTag worldzero$save() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("joined", this.worldzero$joined);
            tag.putBoolean("left", this.worldzero$left);
            tag.putBoolean("local_port_sent", this.worldzero$localPortSent);
            tag.putInt("stage", this.worldzero$stage);
            tag.putInt("dialogue_index", this.worldzero$dialogueIndex);
            tag.putInt("achievement_index", this.worldzero$achievementIndex);
            tag.putInt("local_port", this.worldzero$localPort);
            tag.putLong("next_story_tick", this.worldzero$nextStoryTick);
            tag.putString("fake_name", this.worldzero$fakeName);
            return tag;
        }
    }
}
