package ru.nekostul.worldzero.event.chat;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;
import ru.nekostul.worldzero.event.WorldZeroStoryTime;
import ru.nekostul.worldzero.event.fall.WorldZeroFallEvent;
import ru.nekostul.worldzero.event.freeze.WorldZeroFreezeEvent;
import ru.nekostul.worldzero.event.horror.WorldZeroHorrorFinale;
import ru.nekostul.worldzero.event.paralysis.WorldZeroParalysisEvent;
import ru.nekostul.worldzero.network.WorldZeroNetwork;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
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
    private static final String WORLDZERO_PLAYER_GREETING_TRIGGER = "\u043f\u0440\u0438\u0432\u0435\u0442";
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
    private static final int WORLDZERO_NEIGHBOR_BUILD_PROGRESS_INDEX = 5;
    private static final int WORLDZERO_PROGRESS_COAL_INDEX = 0;
    private static final int WORLDZERO_PROGRESS_WORKSTATION_INDEX = 2;
    private static final int WORLDZERO_PROGRESS_COAL_MIN_DISTANCE = 16;
    private static final int WORLDZERO_PROGRESS_COAL_MAX_DISTANCE = 34;
    private static final int WORLDZERO_PROGRESS_WORKSTATION_MIN_DISTANCE = 14;
    private static final int WORLDZERO_PROGRESS_WORKSTATION_MAX_DISTANCE = 28;
    private static final int WORLDZERO_PROGRESS_MAX_CANDIDATE_ATTEMPTS = 48;

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
        if (state.worldzero$joined && !state.worldzero$left) {
            worldzero$ensureNeighborTabPresence(player, state);
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
        worldzero$ensureNeighborTabPresence(player, state);
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

    public static String worldzero$getSpeakerName(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return "";
        }

        ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return "";
        }

        DoubleChatSaveData saveData = worldzero$getSaveData(overworld);
        PlayerState state = worldzero$getOrCreateState(
                saveData,
                player.getUUID(),
                WorldZeroStoryTime.worldzero$getStoryTicks(overworld)
        );
        if (worldzero$ensureScenarioData(state, player, overworld)) {
            saveData.setDirty();
        }
        return state.worldzero$fakeName;
    }

    public static String worldzero$getOriginalNeighborSpeakerName(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return "";
        }

        ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return "";
        }

        DoubleChatSaveData saveData = worldzero$getSaveData(overworld);
        PlayerState state = saveData.worldzero$states.get(player.getUUID());
        return state != null && state.worldzero$fakeName != null ? state.worldzero$fakeName : "";
    }

    public static boolean worldzero$sendSpeakerLineNow(ServerPlayer player, String messageKey) {
        if (player == null || messageKey == null || messageKey.isBlank()) {
            return false;
        }

        String speaker = worldzero$getSpeakerName(player);
        if (speaker.isBlank()) {
            return false;
        }

        return worldzero$sendSpeakerLineNow(player, speaker, messageKey);
    }

    public static boolean worldzero$sendSpeakerLineNow(ServerPlayer player, String speaker, String messageKey) {
        if (player == null || speaker == null || speaker.isBlank() || messageKey == null || messageKey.isBlank()) {
            return false;
        }

        worldzero$sendPlayerLine(player, speaker, messageKey);
        return true;
    }

    public static boolean worldzero$hasNeighborLeft(ServerLevel level, UUID playerId) {
        if (level == null || playerId == null) {
            return false;
        }

        DoubleChatSaveData saveData = worldzero$getSaveData(level);
        PlayerState state = saveData.worldzero$states.get(playerId);
        return state != null && state.worldzero$left;
    }

    public static boolean worldzero$hasNeighborLeft(ServerPlayer player) {
        return player != null && worldzero$hasNeighborLeft(player.serverLevel(), player.getUUID());
    }

    public static long worldzero$getNeighborLeaveStoryTick() {
        return WORLDZERO_PANIC_AT_TICKS;
    }

    public static boolean worldzero$sendAutoSelfLineNow(ServerPlayer player, String messageKey) {
        if (player == null || messageKey == null || messageKey.isBlank()) {
            return false;
        }

        WorldZeroNetwork.sendDoubleChatAutoSelfLine(player, messageKey);
        return true;
    }

    public static long worldzero$getProgress5SentStoryTick(ServerLevel level, UUID playerId) {
        if (level == null || playerId == null) {
            return -1L;
        }

        DoubleChatSaveData saveData = worldzero$getSaveData(level);
        PlayerState state = saveData.worldzero$states.get(playerId);
        if (state == null) {
            return -1L;
        }

        if (state.worldzero$progress5SentStoryTick >= 0L) {
            return state.worldzero$progress5SentStoryTick;
        }

        return state.worldzero$achievementIndex > WORLDZERO_NEIGHBOR_BUILD_PROGRESS_INDEX
                ? WORLDZERO_PROGRESS_TICKS[WORLDZERO_NEIGHBOR_BUILD_PROGRESS_INDEX]
                : -1L;
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
                worldzero$removeNeighborTabPresence(player);
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
            worldzero$ensureNeighborTabPresence(player, state);
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
            int progressIndex = state.worldzero$achievementIndex;
            worldzero$sendPlayerLine(
                    player,
                    state.worldzero$fakeName,
                    WORLDZERO_PROGRESS_KEYS[progressIndex]
            );
            worldzero$applyProgressWorldChange(level, player, state, progressIndex);
            if (progressIndex == WORLDZERO_NEIGHBOR_BUILD_PROGRESS_INDEX) {
                state.worldzero$progress5SentStoryTick = storyTicks;
            }
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

    private static void worldzero$ensureNeighborTabPresence(ServerPlayer player, PlayerState state) {
        if (player == null || player.connection == null || state == null || !state.worldzero$joined || state.worldzero$left) {
            return;
        }

        String fakeName = state.worldzero$fakeName;
        if (fakeName == null || fakeName.isBlank()) {
            return;
        }

        UUID neighborId = worldzero$getNeighborTabUuid(player.getUUID());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = EnumSet.of(
                    ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED
            );
            ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(
                    neighborId,
                    new GameProfile(neighborId, fakeName),
                    true,
                    0,
                    GameType.SURVIVAL,
                    null,
                    null
            );

            buffer.writeEnumSet(actions, ClientboundPlayerInfoUpdatePacket.Action.class);
            buffer.writeCollection(List.of(entry), (packetBuffer, packetEntry) -> {
                packetBuffer.writeUUID(packetEntry.profileId());
                packetBuffer.writeUtf(packetEntry.profile().getName(), 16);
                packetBuffer.writeGameProfileProperties(packetEntry.profile().getProperties());
                packetBuffer.writeBoolean(packetEntry.listed());
            });

            player.connection.send(new ClientboundPlayerInfoUpdatePacket(buffer));
        } finally {
            buffer.release();
        }
    }

    private static UUID worldzero$getNeighborTabUuid(UUID ownerId) {
        return UUID.nameUUIDFromBytes(("worldzero:double_chat:neighbor:" + ownerId).getBytes(StandardCharsets.UTF_8));
    }

    private static void worldzero$removeNeighborTabPresence(ServerPlayer player) {
        if (player == null || player.connection == null) {
            return;
        }

        player.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(worldzero$getNeighborTabUuid(player.getUUID()))));
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
        WorldZeroNetwork.sendDoubleChatAutoSelfLine(player, messageKey);
    }

    private static void worldzero$sendLocalPortLine(ServerPlayer player, String port) {
        WorldZeroNetwork.sendDoubleChatLocalPort(player, port);
    }

    private static void worldzero$applyProgressWorldChange(
            ServerLevel level,
            ServerPlayer player,
            PlayerState state,
            int progressIndex
    ) {
        if (level == null || player == null || state == null) {
            return;
        }

        if (progressIndex == WORLDZERO_PROGRESS_COAL_INDEX && state.worldzero$coalGiftPos == Long.MIN_VALUE) {
            BlockPos coalGiftPos = worldzero$findCoalGiftPosition(level, player);
            if (coalGiftPos != null && worldzero$placeCoalGift(level, coalGiftPos)) {
                state.worldzero$coalGiftPos = coalGiftPos.asLong();
            }
            return;
        }

        if (progressIndex == WORLDZERO_PROGRESS_WORKSTATION_INDEX && state.worldzero$workstationGiftPos == Long.MIN_VALUE) {
            BlockPos workstationPos = worldzero$findWorkstationGiftPosition(level, player, state);
            if (workstationPos != null && worldzero$placeWorkstationGift(level, workstationPos)) {
                state.worldzero$workstationGiftPos = workstationPos.asLong();
            }
        }
    }

    @Nullable
    private static BlockPos worldzero$findCoalGiftPosition(ServerLevel level, ServerPlayer player) {
        Vec3 look = player.getViewVector(1.0F);
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() < 0.0001D) {
            float radians = (float) Math.toRadians(player.getYRot());
            horizontalLook = new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
        }
        horizontalLook = horizontalLook.normalize();

        for (int attempt = 0; attempt < WORLDZERO_PROGRESS_MAX_CANDIDATE_ATTEMPTS; attempt++) {
            double distance = Mth.nextDouble(level.random, WORLDZERO_PROGRESS_COAL_MIN_DISTANCE, WORLDZERO_PROGRESS_COAL_MAX_DISTANCE);
            double angleOffset = Mth.nextDouble(level.random, -1.15D, 1.15D);
            Vec3 direction = horizontalLook.yRot((float) angleOffset);
            int x = Mth.floor(player.getX() + direction.x * distance);
            int z = Mth.floor(player.getZ() + direction.z * distance);
            int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (topY < level.getMinBuildHeight()) {
                continue;
            }

            for (int y = topY; y >= Math.max(level.getMinBuildHeight(), topY - 24); y--) {
                BlockPos floorPos = new BlockPos(x, y, z);
                BlockState floorState = level.getBlockState(floorPos);
                if (!floorState.isFaceSturdy(level, floorPos, net.minecraft.core.Direction.UP)
                        || !floorState.getFluidState().isEmpty()) {
                    continue;
                }

                BlockPos itemPos = floorPos.above();
                if (!level.getBlockState(itemPos).isAir() || !level.getBlockState(itemPos.above()).isAir()) {
                    continue;
                }

                int openFaces = 0;
                for (net.minecraft.core.Direction directionCheck : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    BlockPos sidePos = floorPos.relative(directionCheck);
                    if (!level.getBlockState(sidePos).canOcclude() || level.getBlockState(sidePos).isAir()) {
                        openFaces++;
                    }
                }
                if (openFaces < 2) {
                    continue;
                }

                return itemPos.immutable();
            }
        }

        return null;
    }

    private static boolean worldzero$placeCoalGift(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) {
            return false;
        }

        ItemEntity itemEntity = new ItemEntity(
                level,
                pos.getX() + 0.5D,
                pos.getY() + 0.15D,
                pos.getZ() + 0.5D,
                new ItemStack(Items.COAL, 6 + level.random.nextInt(9))
        );
        itemEntity.setDeltaMovement(Vec3.ZERO);
        itemEntity.setUnlimitedLifetime();
        level.addFreshEntity(itemEntity);
        return true;
    }

    @Nullable
    private static BlockPos worldzero$findWorkstationGiftPosition(
            ServerLevel level,
            ServerPlayer player,
            PlayerState state
    ) {
        Vec3 forward = worldzero$getForwardVector(player, state);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);

        for (int attempt = 0; attempt < WORLDZERO_PROGRESS_MAX_CANDIDATE_ATTEMPTS; attempt++) {
            double distance = Mth.nextDouble(level.random, WORLDZERO_PROGRESS_WORKSTATION_MIN_DISTANCE, WORLDZERO_PROGRESS_WORKSTATION_MAX_DISTANCE);
            double forwardScale = 0.8D + level.random.nextDouble() * 0.5D;
            double sideScale = Mth.nextDouble(level.random, -5.0D, 5.0D);
            Vec3 target = player.position()
                    .add(forward.scale(distance * forwardScale))
                    .add(right.scale(sideScale));

            int x = Mth.floor(target.x);
            int z = Mth.floor(target.z);
            int y = worldzero$getSimpleSurfaceY(level, x, z);
            if (y == Integer.MIN_VALUE) {
                continue;
            }

            BlockPos tablePos = new BlockPos(x, y, z);
            if (!worldzero$isWaterNearby(level, tablePos, 3)) {
                continue;
            }
            if (!worldzero$canPlaceSimpleBlockAt(level, tablePos)) {
                continue;
            }

            return tablePos.immutable();
        }

        return null;
    }

    private static boolean worldzero$placeWorkstationGift(ServerLevel level, BlockPos pos) {
        if (!worldzero$canPlaceSimpleBlockAt(level, pos)) {
            return false;
        }

        worldzero$clearSimplePlacementArea(level, pos);
        level.setBlock(
                pos,
                Blocks.CRAFTING_TABLE.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
        );
        return true;
    }

    private static Vec3 worldzero$getForwardVector(ServerPlayer player, PlayerState state) {
        Vec3 currentPos = player.position();
        if (state.worldzero$previousPosition != null) {
            Vec3 movement = currentPos.subtract(state.worldzero$previousPosition);
            Vec3 horizontalMovement = new Vec3(movement.x, 0.0D, movement.z);
            if (horizontalMovement.lengthSqr() > 0.25D) {
                return horizontalMovement.normalize();
            }
        }

        Vec3 look = player.getViewVector(1.0F);
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        return horizontalLook.lengthSqr() > 0.0001D ? horizontalLook.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
    }

    private static int worldzero$getSimpleSurfaceY(ServerLevel level, int x, int z) {
        int rawTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (rawTop < level.getMinBuildHeight()) {
            return Integer.MIN_VALUE;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, rawTop, z);
        while (cursor.getY() > level.getMinBuildHeight()) {
            BlockState state = level.getBlockState(cursor);
            if (state.isAir()) {
                cursor.move(net.minecraft.core.Direction.DOWN);
                continue;
            }
            if (!state.getFluidState().isEmpty()) {
                return Integer.MIN_VALUE;
            }
            return cursor.getY() + 1;
        }
        return Integer.MIN_VALUE;
    }

    private static boolean worldzero$canPlaceSimpleBlockAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockState above = level.getBlockState(pos.above());
        BlockState below = level.getBlockState(pos.below());
        return below.isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
                && below.getFluidState().isEmpty()
                && state.isAir()
                && above.isAir()
                && state.getFluidState().isEmpty()
                && above.getFluidState().isEmpty();
    }

    private static void worldzero$clearSimplePlacementArea(ServerLevel level, BlockPos pos) {
        for (int y = 0; y <= 1; y++) {
            BlockPos target = pos.above(y);
            if (!level.getBlockState(target).isAir()) {
                level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    private static boolean worldzero$isWaterNearby(ServerLevel level, BlockPos pos, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos checkPos = pos.offset(dx, -1, dz);
                if (level.getFluidState(checkPos).is(FluidTags.WATER) || level.getFluidState(checkPos.above()).is(FluidTags.WATER)) {
                    return true;
                }
            }
        }
        return false;
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
        private long worldzero$coalGiftPos = Long.MIN_VALUE;
        private long worldzero$workstationGiftPos = Long.MIN_VALUE;
        private long worldzero$progress5SentStoryTick = -1L;
        private long worldzero$nextStoryTick = -1L;
        private String worldzero$fakeName = "";
        @Nullable
        private Vec3 worldzero$previousPosition;

        private static PlayerState worldzero$load(CompoundTag tag) {
            PlayerState state = new PlayerState();
            state.worldzero$joined = tag.getBoolean("joined");
            state.worldzero$left = tag.getBoolean("left");
            state.worldzero$localPortSent = tag.getBoolean("local_port_sent");
            state.worldzero$stage = tag.getInt("stage");
            state.worldzero$dialogueIndex = Math.max(0, tag.getInt("dialogue_index"));
            state.worldzero$achievementIndex = Math.max(0, tag.getInt("achievement_index"));
            state.worldzero$localPort = tag.getInt("local_port");
            state.worldzero$coalGiftPos = tag.contains("coal_gift_pos") ? tag.getLong("coal_gift_pos") : Long.MIN_VALUE;
            state.worldzero$workstationGiftPos = tag.contains("workstation_gift_pos")
                    ? tag.getLong("workstation_gift_pos")
                    : Long.MIN_VALUE;
            state.worldzero$progress5SentStoryTick = tag.contains("progress5_sent_story_tick")
                    ? tag.getLong("progress5_sent_story_tick")
                    : -1L;
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
            if (this.worldzero$coalGiftPos != Long.MIN_VALUE) {
                tag.putLong("coal_gift_pos", this.worldzero$coalGiftPos);
            }
            if (this.worldzero$workstationGiftPos != Long.MIN_VALUE) {
                tag.putLong("workstation_gift_pos", this.worldzero$workstationGiftPos);
            }
            tag.putLong("progress5_sent_story_tick", this.worldzero$progress5SentStoryTick);
            tag.putLong("next_story_tick", this.worldzero$nextStoryTick);
            tag.putString("fake_name", this.worldzero$fakeName);
            return tag;
        }
    }
}
