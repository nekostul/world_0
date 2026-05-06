package ru.nekostul.worldzero.event.chat;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;
import ru.nekostul.worldzero.event.WorldZeroStoryTime;
import ru.nekostul.worldzero.event.skywatch.WorldZeroSkyWatchEvent;

import java.util.HashMap;
import java.util.List;
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
    private static final long WORLDZERO_REPLY_DELAY_MIN_TICKS = 40L;
    private static final long WORLDZERO_REPLY_DELAY_MAX_TICKS = 180L;
    private static final long WORLDZERO_REPLY_DELAY_LONG_MIN_TICKS = 8L * 20L;
    private static final long WORLDZERO_REPLY_DELAY_LONG_MAX_TICKS = 16L * 20L;
    private static final long WORLDZERO_HINT_DELAY_MIN_TICKS = 2L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_HINT_DELAY_MAX_TICKS = 5L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_HINT_SILENCE_REQUIRED_TICKS = 75L * 20L;
    private static final long WORLDZERO_SOUND_COOLDOWN_TICKS = 3L * WORLDZERO_TICKS_PER_MINUTE;
    private static final String WORLDZERO_IDLE_KEY = "message.worldzero.double_chat.idle";
    private static final String WORLDZERO_IDLE_AUTO_REPLY_KEY = "message.worldzero.double_chat.idle_auto_reply";
    private static final String WORLDZERO_FAKE_QUESTION_KEY_PREFIX = "message.worldzero.double_chat.fake.question.";
    private static final String WORLDZERO_FAKE_ANSWER_KEY_PREFIX = "message.worldzero.double_chat.fake.answer.";
    private static final String WORLDZERO_FAKE_HINT_KEY_PREFIX = "message.worldzero.double_chat.fake.hint.";
    private static final String[] WORLDZERO_FAKE_VISIBLE_NAME_CHARS = {
            "々", "〆", "ゞ", "ヌ", "ム", "乂", "人", "口", "尸", "屮",
            "爪", "丂", "卂", "匚", "丄", "丨", "乙", "卄", "仄", "龴",
            "Λ", "Σ", "Ψ", "Ω", "Ж", "҂", "Ѫ", "Ѭ", "Ӿ", "Я",
            "Ф", "Ю", "Ϟ", "ϟ", "☍", "☌", "☰", "☲", "☳", "☷",
            "⟟", "⌁", "░", "▒", "▓", "⠿", "⡇", "⢼", "⣷", "⧖"
    };
    private static final String[] WORLDZERO_FAKE_COMBINING_NAME_CHARS = {
            "\u0307", "\u0301", "\u0323", "\u0335", "\u0337"
    };
    private static final List<Holder.Reference<SoundEvent>> WORLDZERO_OMINOUS_SOUNDS = List.of(
            SoundEvents.AMBIENT_CAVE,
            SoundEvents.AMBIENT_BASALT_DELTAS_MOOD,
            SoundEvents.AMBIENT_CRIMSON_FOREST_MOOD,
            SoundEvents.AMBIENT_NETHER_WASTES_MOOD,
            SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD,
            SoundEvents.AMBIENT_WARPED_FOREST_MOOD
    );
    private static final QuestionAnswerEntry[] WORLDZERO_FAKE_QA = {
            worldzero$qa(0, false, "ты кто", "кто ты", "who are you"),
            worldzero$qa(1, false, "где ты", "ты где", "where are you", "where are u"),
            worldzero$qa(2, false, "это ты", "is it you"),
            worldzero$qa(3, false, "ты настоящий", "are you real"),
            worldzero$qa(4, true, "что произошло", "what happened"),
            worldzero$qa(5, true, "кто стоял у дома", "who was standing by the house"),
            worldzero$qa(6, false, "ты меня видишь", "can you see me"),
            worldzero$qa(7, true, "что тебе нужно", "what do you want"),
            worldzero$qa(8, false, "почему ты пишешь", "why are you typing", "why are you writing"),
            worldzero$qa(9, true, "сосед вышел", "did the neighbor leave"),
            worldzero$qa(10, true, "что это был за звук", "what was that sound"),
            worldzero$qa(11, true, "кто сейчас в мире", "who is in the world now"),
            worldzero$qa(12, true, "ты рядом", "are you near"),
            worldzero$qa(13, true, "что в лесу", "what is in the forest", "what is in the woods"),
            worldzero$qa(14, true, "кто он", "who is it", "who is he"),
            worldzero$qa(15, true, "ты следишь за мной", "are you watching me"),
            worldzero$qa(16, true, "почему двери открываются", "why are the doors opening"),
            worldzero$qa(17, true, "почему ты молчал", "why were you quiet"),
            worldzero$qa(18, false, "это мод", "is this a mod"),
            worldzero$qa(19, false, "ты игрок", "are you a player"),
            worldzero$qa(20, true, "что с твоим ником", "what happened to your name"),
            worldzero$qa(21, true, "почему ты не выходишь", "why dont you leave", "why don't you leave"),
            worldzero$qa(22, true, "что ты сейчас видишь", "what do you see right now"),
            worldzero$qa(23, true, "мне выйти", "should i leave"),
            worldzero$qa(24, true, "ты врешь", "are you lying"),
            worldzero$qa(25, true, "что мне делать", "what should i do"),
            worldzero$qa(26, true, "кто пишет вместо тебя", "who is typing instead of you")
    };
    private static final HintEntry[] WORLDZERO_FAKE_HINTS = {
            worldzero$hint(0, true),
            worldzero$hint(1, false),
            worldzero$hint(2, false),
            worldzero$hint(3, true),
            worldzero$hint(4, false),
            worldzero$hint(5, true),
            worldzero$hint(6, true),
            worldzero$hint(7, true),
            worldzero$hint(8, false),
            worldzero$hint(9, true)
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
        if (!WorldZeroDoubleChatEvent.worldzero$hasNeighborLeft(level, player.getUUID())) {
            return;
        }

        long storyTicks = WorldZeroStoryTime.worldzero$getStoryTicks(level);
        if (storyTicks > WORLDZERO_LATE_END_TICKS) {
            return;
        }

        LateChatSaveData saveData = worldzero$getSaveData(level);
        PlayerState state = worldzero$getOrCreateState(saveData, player, storyTicks);
        if (worldzero$ensureStateData(state, player, level)) {
            saveData.setDirty();
        }

        if (state.worldzero$idleWaitingReply) {
            state.worldzero$idleWaitingReply = false;
            state.worldzero$idleResolved = true;
            state.worldzero$nextHintTick = storyTicks + worldzero$randomHintDelay(level);
        } else if (!state.worldzero$idleResolved) {
            return;
        }

        worldzero$handlePlayerMessage(level, saveData, state, message, storyTicks);
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
        if (!WorldZeroDoubleChatEvent.worldzero$hasNeighborLeft(level, player.getUUID())
                || WorldZeroSkyWatchEvent.worldzero$isActive(level.getServer())) {
            return;
        }

        PlayerState state = worldzero$getOrCreateState(saveData, player, storyTicks);
        if (worldzero$ensureStateData(state, player, level)) {
            saveData.setDirty();
        }

        if (!state.worldzero$idleSent) {
            if (storyTicks < state.worldzero$idleTriggerTick || !WorldZeroStoryTime.worldzero$canReceiveStoryEvent(player)) {
                return;
            }

            if (WorldZeroDoubleChatEvent.worldzero$sendSpeakerLineNow(player, state.worldzero$fakeSpeakerName, WORLDZERO_IDLE_KEY)) {
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
                state.worldzero$nextHintTick = storyTicks + worldzero$randomHintDelay(level);
                saveData.setDirty();
            }
            return;
        }

        if (!state.worldzero$idleResolved || storyTicks > WORLDZERO_LATE_END_TICKS) {
            return;
        }

        if (state.worldzero$pendingReplyTick >= 0L) {
            if (storyTicks < state.worldzero$pendingReplyTick || !WorldZeroStoryTime.worldzero$canReceiveStoryEvent(player)) {
                return;
            }

            if (worldzero$emitPendingReply(level, player, state, storyTicks)) {
                saveData.setDirty();
            }
            return;
        }

        if (state.worldzero$nextHintTick < 0L) {
            state.worldzero$nextHintTick = storyTicks + worldzero$randomHintDelay(level);
            saveData.setDirty();
            return;
        }

        if (storyTicks < state.worldzero$nextHintTick
                || !WorldZeroStoryTime.worldzero$canReceiveStoryEvent(player)
                || storyTicks - state.worldzero$lastPlayerMessageTick < WORLDZERO_HINT_SILENCE_REQUIRED_TICKS) {
            return;
        }

        int hintIndex = worldzero$pickHintIndex(level, state);
        if (hintIndex < 0) {
            state.worldzero$nextHintTick = storyTicks + worldzero$randomHintDelay(level);
            saveData.setDirty();
            return;
        }

        HintEntry hint = WORLDZERO_FAKE_HINTS[hintIndex];
        if (WorldZeroDoubleChatEvent.worldzero$sendSpeakerLineNow(player, state.worldzero$fakeSpeakerName, hint.worldzero$messageKey())) {
            state.worldzero$lastHintIndex = hintIndex;
            state.worldzero$nextHintTick = storyTicks + worldzero$randomHintDelay(level);
            worldzero$maybePlayOminousSound(level, player, state, hint.worldzero$ominous(), storyTicks);
            saveData.setDirty();
        }
    }

    private static boolean worldzero$emitPendingReply(
            ServerLevel level,
            ServerPlayer player,
            PlayerState state,
            long storyTicks
    ) {
        boolean sent = false;
        boolean ominous = false;
        if (state.worldzero$pendingAnswerIndex >= 0) {
            QuestionAnswerEntry entry = WORLDZERO_FAKE_QA[state.worldzero$pendingAnswerIndex];
            sent = WorldZeroDoubleChatEvent.worldzero$sendSpeakerLineNow(
                    player,
                    state.worldzero$fakeSpeakerName,
                    entry.worldzero$answerKey()
            );
            ominous = entry.worldzero$ominous();
            if (sent) {
                state.worldzero$lastAnswerIndex = state.worldzero$pendingAnswerIndex;
            }
        } else if (state.worldzero$pendingHintIndex >= 0) {
            HintEntry hint = WORLDZERO_FAKE_HINTS[state.worldzero$pendingHintIndex];
            sent = WorldZeroDoubleChatEvent.worldzero$sendSpeakerLineNow(
                    player,
                    state.worldzero$fakeSpeakerName,
                    hint.worldzero$messageKey()
            );
            ominous = hint.worldzero$ominous();
            if (sent) {
                state.worldzero$lastHintIndex = state.worldzero$pendingHintIndex;
            }
        }

        if (!sent) {
            return false;
        }

        state.worldzero$pendingReplyTick = -1L;
        state.worldzero$pendingAnswerIndex = -1;
        state.worldzero$pendingHintIndex = -1;
        state.worldzero$pendingBurstCount = 0;
        state.worldzero$nextHintTick = storyTicks + worldzero$randomHintDelay(level);
        worldzero$maybePlayOminousSound(level, player, state, ominous, storyTicks);
        return true;
    }

    private static void worldzero$handlePlayerMessage(
            ServerLevel level,
            LateChatSaveData saveData,
            PlayerState state,
            String rawMessage,
            long storyTicks
    ) {
        String normalizedMessage = worldzero$normalizeMessage(rawMessage);
        if (normalizedMessage.isBlank()) {
            return;
        }

        state.worldzero$lastPlayerMessageTick = storyTicks;
        state.worldzero$nextHintTick = storyTicks + worldzero$randomHintDelay(level);

        int questionIndex = worldzero$findQuestionIndex(normalizedMessage);
        if (state.worldzero$pendingReplyTick >= 0L) {
            state.worldzero$pendingBurstCount++;
            if (questionIndex >= 0 && level.random.nextInt(5) == 0) {
                state.worldzero$pendingAnswerIndex = worldzero$pickAnswerIndex(level, questionIndex, state);
                state.worldzero$pendingHintIndex = -1;
                state.worldzero$pendingReplyTick = storyTicks + worldzero$randomReplyDelay(level);
            } else if (questionIndex >= 0 && state.worldzero$pendingHintIndex >= 0 && level.random.nextBoolean()) {
                state.worldzero$pendingAnswerIndex = worldzero$pickAnswerIndex(level, questionIndex, state);
                state.worldzero$pendingHintIndex = -1;
                state.worldzero$pendingReplyTick = storyTicks + worldzero$randomReplyDelay(level);
            }
            saveData.setDirty();
            return;
        }

        boolean scheduled = false;
        if (questionIndex >= 0) {
            int roll = level.random.nextInt(100);
            if (roll < 68) {
                state.worldzero$pendingAnswerIndex = worldzero$pickAnswerIndex(level, questionIndex, state);
                state.worldzero$pendingHintIndex = -1;
                state.worldzero$pendingReplyTick = storyTicks + worldzero$randomReplyDelay(level);
                state.worldzero$pendingBurstCount = 1;
                scheduled = true;
            } else if (roll < 84) {
                state.worldzero$pendingAnswerIndex = -1;
                state.worldzero$pendingHintIndex = worldzero$pickHintIndex(level, state);
                state.worldzero$pendingReplyTick = storyTicks + worldzero$randomReplyDelay(level);
                state.worldzero$pendingBurstCount = 1;
                scheduled = state.worldzero$pendingHintIndex >= 0;
            }
        } else {
            int roll = level.random.nextInt(100);
            if (roll < 18) {
                state.worldzero$pendingAnswerIndex = worldzero$pickUnrelatedAnswerIndex(level, state);
                state.worldzero$pendingHintIndex = -1;
                state.worldzero$pendingReplyTick = storyTicks + worldzero$randomReplyDelay(level);
                state.worldzero$pendingBurstCount = 1;
                scheduled = state.worldzero$pendingAnswerIndex >= 0;
            } else if (roll < 33) {
                state.worldzero$pendingAnswerIndex = -1;
                state.worldzero$pendingHintIndex = worldzero$pickHintIndex(level, state);
                state.worldzero$pendingReplyTick = storyTicks + worldzero$randomReplyDelay(level);
                state.worldzero$pendingBurstCount = 1;
                scheduled = state.worldzero$pendingHintIndex >= 0;
            }
        }

        if (scheduled) {
            saveData.setDirty();
        }
    }

    private static int worldzero$findQuestionIndex(String normalizedMessage) {
        for (int index = 0; index < WORLDZERO_FAKE_QA.length; index++) {
            QuestionAnswerEntry entry = WORLDZERO_FAKE_QA[index];
            for (String trigger : entry.worldzero$triggers()) {
                if (trigger.equals(normalizedMessage)) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static int worldzero$pickAnswerIndex(ServerLevel level, int questionIndex, PlayerState state) {
        if (questionIndex < 0 || questionIndex >= WORLDZERO_FAKE_QA.length) {
            return worldzero$pickUnrelatedAnswerIndex(level, state);
        }

        int mismatchChance = state.worldzero$pendingBurstCount > 1 ? 35 : 23;
        if (level.random.nextInt(100) < mismatchChance) {
            return worldzero$pickUnrelatedAnswerIndex(level, state);
        }
        return questionIndex;
    }

    private static int worldzero$pickUnrelatedAnswerIndex(ServerLevel level, PlayerState state) {
        if (WORLDZERO_FAKE_QA.length == 0) {
            return -1;
        }

        int[] candidates = new int[WORLDZERO_FAKE_QA.length];
        int count = 0;
        for (int index = 0; index < WORLDZERO_FAKE_QA.length; index++) {
            if (index == state.worldzero$lastAnswerIndex) {
                continue;
            }
            candidates[count++] = index;
        }

        if (count == 0) {
            return level.random.nextInt(WORLDZERO_FAKE_QA.length);
        }
        return candidates[level.random.nextInt(count)];
    }

    private static int worldzero$pickHintIndex(ServerLevel level, PlayerState state) {
        if (WORLDZERO_FAKE_HINTS.length == 0) {
            return -1;
        }

        int[] candidates = new int[WORLDZERO_FAKE_HINTS.length];
        int count = 0;
        for (int index = 0; index < WORLDZERO_FAKE_HINTS.length; index++) {
            if (index == state.worldzero$lastHintIndex) {
                continue;
            }
            candidates[count++] = index;
        }

        if (count == 0) {
            return level.random.nextInt(WORLDZERO_FAKE_HINTS.length);
        }
        return candidates[level.random.nextInt(count)];
    }

    private static long worldzero$randomReplyDelay(ServerLevel level) {
        return level.random.nextInt(4) == 0
                ? worldzero$randomRange(level, WORLDZERO_REPLY_DELAY_LONG_MIN_TICKS, WORLDZERO_REPLY_DELAY_LONG_MAX_TICKS)
                : worldzero$randomRange(level, WORLDZERO_REPLY_DELAY_MIN_TICKS, WORLDZERO_REPLY_DELAY_MAX_TICKS);
    }

    private static long worldzero$randomHintDelay(ServerLevel level) {
        return worldzero$randomRange(level, WORLDZERO_HINT_DELAY_MIN_TICKS, WORLDZERO_HINT_DELAY_MAX_TICKS);
    }

    private static void worldzero$maybePlayOminousSound(
            ServerLevel level,
            ServerPlayer player,
            PlayerState state,
            boolean ominous,
            long storyTicks
    ) {
        if (!ominous
                || player == null
                || storyTicks < state.worldzero$nextSoundAllowedTick
                || level.random.nextInt(6) != 0) {
            return;
        }

        Holder.Reference<SoundEvent> sound = WORLDZERO_OMINOUS_SOUNDS.get(level.random.nextInt(WORLDZERO_OMINOUS_SOUNDS.size()));
        level.playSound(
                null,
                player.blockPosition(),
                sound.value(),
                SoundSource.AMBIENT,
                0.75F,
                0.35F + level.random.nextFloat() * 0.3F
        );
        state.worldzero$nextSoundAllowedTick = storyTicks + WORLDZERO_SOUND_COOLDOWN_TICKS;
    }

    private static boolean worldzero$ensureStateData(PlayerState state, ServerPlayer player, ServerLevel level) {
        if (state.worldzero$fakeSpeakerName != null && !state.worldzero$fakeSpeakerName.isBlank()) {
            return false;
        }

        state.worldzero$fakeSpeakerName = worldzero$generateFakeSpeakerName(player.getGameProfile().getName(), level);
        return true;
    }

    private static String worldzero$generateFakeSpeakerName(String playerName, ServerLevel level) {
        int targetLength = Math.max(1, playerName == null ? 0 : playerName.length());
        StringBuilder builder = new StringBuilder(targetLength);
        for (int index = 0; index < targetLength; index++) {
            if (index > 0 && targetLength > 3 && level.random.nextInt(7) == 0) {
                builder.append(WORLDZERO_FAKE_COMBINING_NAME_CHARS[level.random.nextInt(WORLDZERO_FAKE_COMBINING_NAME_CHARS.length)]);
                continue;
            }

            builder.append(WORLDZERO_FAKE_VISIBLE_NAME_CHARS[level.random.nextInt(WORLDZERO_FAKE_VISIBLE_NAME_CHARS.length)]);
        }
        return builder.toString();
    }

    private static String worldzero$normalizeMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(rawMessage.length());
        for (int index = 0; index < rawMessage.length(); index++) {
            char character = Character.toLowerCase(rawMessage.charAt(index));
            if (Character.isLetterOrDigit(character)) {
                builder.append(character);
            }
        }
        return builder.toString();
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
        state.worldzero$lastPlayerMessageTick = storyTicks - WORLDZERO_HINT_SILENCE_REQUIRED_TICKS;
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

    private static QuestionAnswerEntry worldzero$qa(int index, boolean ominous, String... triggers) {
        return new QuestionAnswerEntry(
                WORLDZERO_FAKE_QUESTION_KEY_PREFIX + index,
                WORLDZERO_FAKE_ANSWER_KEY_PREFIX + index,
                ominous,
                worldzero$normalizeTriggers(triggers)
        );
    }

    private static HintEntry worldzero$hint(int index, boolean ominous) {
        return new HintEntry(WORLDZERO_FAKE_HINT_KEY_PREFIX + index, ominous);
    }

    private static String[] worldzero$normalizeTriggers(String[] triggers) {
        String[] normalized = new String[triggers.length];
        for (int index = 0; index < triggers.length; index++) {
            normalized[index] = worldzero$normalizeMessage(triggers[index]);
        }
        return normalized;
    }

    private record QuestionAnswerEntry(
            String worldzero$questionKey,
            String worldzero$answerKey,
            boolean worldzero$ominous,
            String[] worldzero$triggers
    ) {
    }

    private record HintEntry(String worldzero$messageKey, boolean worldzero$ominous) {
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
        private int worldzero$lastAnswerIndex = -1;
        private int worldzero$lastHintIndex = -1;
        private int worldzero$pendingAnswerIndex = -1;
        private int worldzero$pendingHintIndex = -1;
        private int worldzero$pendingBurstCount;
        private long worldzero$idleTriggerTick = -1L;
        private long worldzero$idleReplyDeadlineTick = -1L;
        private long worldzero$lastPlayerMessageTick = Long.MIN_VALUE;
        private long worldzero$nextHintTick = -1L;
        private long worldzero$pendingReplyTick = -1L;
        private long worldzero$nextSoundAllowedTick = -1L;
        private String worldzero$fakeSpeakerName = "";

        private static PlayerState worldzero$load(CompoundTag tag) {
            PlayerState state = new PlayerState();
            state.worldzero$idleSent = tag.getBoolean("idle_sent");
            state.worldzero$idleWaitingReply = tag.getBoolean("idle_waiting_reply");
            state.worldzero$idleResolved = tag.getBoolean("idle_resolved");
            state.worldzero$lastAnswerIndex = tag.contains("last_answer_index") ? tag.getInt("last_answer_index") : -1;
            state.worldzero$lastHintIndex = tag.contains("last_hint_index") ? tag.getInt("last_hint_index") : -1;
            state.worldzero$pendingAnswerIndex = tag.contains("pending_answer_index") ? tag.getInt("pending_answer_index") : -1;
            state.worldzero$pendingHintIndex = tag.contains("pending_hint_index") ? tag.getInt("pending_hint_index") : -1;
            state.worldzero$pendingBurstCount = tag.getInt("pending_burst_count");
            state.worldzero$idleTriggerTick = tag.contains("idle_trigger_tick") ? tag.getLong("idle_trigger_tick") : -1L;
            state.worldzero$idleReplyDeadlineTick = tag.contains("idle_reply_deadline_tick")
                    ? tag.getLong("idle_reply_deadline_tick")
                    : -1L;
            state.worldzero$lastPlayerMessageTick = tag.contains("last_player_message_tick")
                    ? tag.getLong("last_player_message_tick")
                    : Long.MIN_VALUE;
            state.worldzero$nextHintTick = tag.contains("next_hint_tick") ? tag.getLong("next_hint_tick") : -1L;
            state.worldzero$pendingReplyTick = tag.contains("pending_reply_tick") ? tag.getLong("pending_reply_tick") : -1L;
            state.worldzero$nextSoundAllowedTick = tag.contains("next_sound_allowed_tick")
                    ? tag.getLong("next_sound_allowed_tick")
                    : -1L;
            state.worldzero$fakeSpeakerName = tag.getString("fake_speaker_name");
            return state;
        }

        private CompoundTag worldzero$save() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("idle_sent", this.worldzero$idleSent);
            tag.putBoolean("idle_waiting_reply", this.worldzero$idleWaitingReply);
            tag.putBoolean("idle_resolved", this.worldzero$idleResolved);
            tag.putInt("last_answer_index", this.worldzero$lastAnswerIndex);
            tag.putInt("last_hint_index", this.worldzero$lastHintIndex);
            tag.putInt("pending_answer_index", this.worldzero$pendingAnswerIndex);
            tag.putInt("pending_hint_index", this.worldzero$pendingHintIndex);
            tag.putInt("pending_burst_count", this.worldzero$pendingBurstCount);
            tag.putLong("idle_trigger_tick", this.worldzero$idleTriggerTick);
            tag.putLong("idle_reply_deadline_tick", this.worldzero$idleReplyDeadlineTick);
            tag.putLong("last_player_message_tick", this.worldzero$lastPlayerMessageTick);
            tag.putLong("next_hint_tick", this.worldzero$nextHintTick);
            tag.putLong("pending_reply_tick", this.worldzero$pendingReplyTick);
            tag.putLong("next_sound_allowed_tick", this.worldzero$nextSoundAllowedTick);
            tag.putString("fake_speaker_name", this.worldzero$fakeSpeakerName);
            return tag;
        }
    }
}
