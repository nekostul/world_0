package ru.nekostul.worldzero.event.horror;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;
import ru.nekostul.worldzero.dimension.WorldZeroVoidDimension;
import ru.nekostul.worldzero.event.fall.WorldZeroFallEvent;
import ru.nekostul.worldzero.event.footsteps.WorldZeroFootstepsEvent;
import ru.nekostul.worldzero.event.freeze.WorldZeroFreezeEvent;
import ru.nekostul.worldzero.event.house.WorldZeroHouseEvent;
import ru.nekostul.worldzero.event.major.WorldZeroMajorEventSystem;
import ru.nekostul.worldzero.event.memory.WorldZeroWorldMemoryEvent;
import ru.nekostul.worldzero.event.mining.WorldZeroLastBlockEvent;
import ru.nekostul.worldzero.event.paralysis.WorldZeroParalysisEvent;
import ru.nekostul.worldzero.network.WorldZeroFinalePacket;
import ru.nekostul.worldzero.network.WorldZeroMinorAnomalyPacket;
import ru.nekostul.worldzero.network.WorldZeroNetwork;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroHorrorFinale {
    public static final long WORLDZERO_END_TICKS = 180L * 60L * 20L;
    private static final long WORLDZERO_LIGHT_BREAK_TICKS = 100L;
    private static final long WORLDZERO_LIGHT_REPEAT_TICKS = 200L;
    private static final long WORLDZERO_STRONG_BREAK_TICKS = 300L;
    private static final long WORLDZERO_STRONG_REPEAT_TICKS = 400L;
    private static final long WORLDZERO_PEAK_TICKS = 500L;
    private static final long WORLDZERO_FREEZE_TICKS = 580L;
    private static final long WORLDZERO_EXIT_TICKS = 600L;
    private static final String WORLDZERO_SAVE_ID = "worldzero_horror_finale";
    private static final int WORLDZERO_STAGE_START = 1;
    private static final int WORLDZERO_STAGE_LIGHT_BREAK = 1 << 1;
    private static final int WORLDZERO_STAGE_LIGHT_REPEAT = 1 << 2;
    private static final int WORLDZERO_STAGE_STRONG_BREAK = 1 << 3;
    private static final int WORLDZERO_STAGE_STRONG_REPEAT = 1 << 4;
    private static final int WORLDZERO_STAGE_PEAK = 1 << 5;
    private static final int WORLDZERO_STAGE_FREEZE = 1 << 6;
    private static final int WORLDZERO_STAGE_EXIT = 1 << 7;
    private static final int WORLDZERO_POST_STAGE_NONE = 0;
    private static final int WORLDZERO_POST_STAGE_WAITING_VOID = 1;
    private static final int WORLDZERO_POST_STAGE_VOID_RUNNING = 2;
    private static final int WORLDZERO_POST_STAGE_FINAL_MENU = 3;
    private static final int WORLDZERO_POST_STAGE_ABSOLUTE_RUNNING = 4;
    private static final int WORLDZERO_POST_STAGE_ABSOLUTE_DONE = 5;
    private static final Component WORLDZERO_FINAL_WORLD_LOCK_MESSAGE = Component.literal(
            "Internal Exception: java.io.UTFDataFormatException: malformed input around byte 4100 in blak_echo.session"
    );
    private static final Map<MinecraftServer, SessionState> WORLDZERO_SESSION_STATES = new WeakHashMap<>();

    private WorldZeroHorrorFinale() {
    }

    public static boolean worldzero$isEndReached(long worldTicks) {
        return worldTicks >= WORLDZERO_END_TICKS;
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

        FinaleSaveData saveData = worldzero$getSaveData(overworld);
        if (saveData.worldzero$postFinaleStage == WORLDZERO_POST_STAGE_ABSOLUTE_DONE) {
            player.connection.disconnect(WORLDZERO_FINAL_WORLD_LOCK_MESSAGE);
            return;
        }

        if (saveData.worldzero$postFinaleStage == WORLDZERO_POST_STAGE_WAITING_VOID) {
            if (WorldZeroVoidDimension.worldzero$teleportPlayerToFinalVoid(player)) {
                saveData.worldzero$postFinaleStage = WORLDZERO_POST_STAGE_VOID_RUNNING;
                saveData.setDirty();
            }
            return;
        }

        if (saveData.worldzero$postFinaleStage == WORLDZERO_POST_STAGE_FINAL_MENU) {
            if (WorldZeroVoidDimension.worldzero$startAbsoluteFinal(player)) {
                saveData.worldzero$postFinaleStage = WORLDZERO_POST_STAGE_ABSOLUTE_RUNNING;
                saveData.setDirty();
            }
        }
    }

    public static boolean worldzero$isActive(MinecraftServer server) {
        SessionState state = WORLDZERO_SESSION_STATES.get(server);
        return state != null && state.worldzero$active;
    }

    public static void worldzero$tickEnd(ServerLevel level) {
        if (level == null || level.getServer() == null) {
            return;
        }

        FinaleSaveData saveData = worldzero$getSaveData(level);
        if (saveData.worldzero$completed) {
            WorldZeroMinorAnomalies.worldzero$cancelAll(level.getServer());
            return;
        }

        long worldTicks = WorldZeroHorrorEventSystem.worldzero$getWorldTicks(level);
        if (!saveData.worldzero$started) {
            worldzero$startFinale(level, saveData, WORLDZERO_END_TICKS);
        }

        MinecraftServer server = level.getServer();
        SessionState state = WORLDZERO_SESSION_STATES.computeIfAbsent(server, ignored -> new SessionState());
        state.worldzero$active = true;
        if (!state.worldzero$conflictsStopped) {
            worldzero$stopConflictingEvents(server);
            state.worldzero$conflictsStopped = true;
        }

        long elapsedTicks = Math.max(0L, worldTicks - saveData.worldzero$startWorldTick);
        worldzero$processBlockRestores(server, state, worldTicks);

        if (elapsedTicks >= WORLDZERO_EXIT_TICKS) {
            worldzero$sendStage(server, state, WORLDZERO_STAGE_EXIT, WorldZeroFinalePacket.WORLDZERO_ACTION_EXIT_TO_MENU, 1, 0);
            worldzero$restoreAllBlocks(server, state);
            saveData.worldzero$completed = true;
            saveData.worldzero$postFinaleStage = WORLDZERO_POST_STAGE_WAITING_VOID;
            saveData.setDirty();
            WORLDZERO_SESSION_STATES.remove(server);
            return;
        }

        if (elapsedTicks >= WORLDZERO_FREEZE_TICKS) {
            worldzero$sendStage(server, state, WORLDZERO_STAGE_FREEZE, WorldZeroFinalePacket.WORLDZERO_ACTION_FULL_FREEZE, (int) (WORLDZERO_EXIT_TICKS - elapsedTicks), 0);
            worldzero$restoreAllBlocks(server, state);
            return;
        }

        worldzero$sendStage(
                server,
                state,
                WORLDZERO_STAGE_START,
                WorldZeroFinalePacket.WORLDZERO_ACTION_START_SILENCE,
                (int) Math.max(1L, WORLDZERO_LIGHT_BREAK_TICKS - elapsedTicks),
                level.random.nextInt()
        );

        if (elapsedTicks >= WORLDZERO_LIGHT_BREAK_TICKS) {
            worldzero$sendStage(server, state, WORLDZERO_STAGE_LIGHT_BREAK, WorldZeroFinalePacket.WORLDZERO_ACTION_GLITCH, 100, 1);
        }
        if (elapsedTicks >= WORLDZERO_LIGHT_REPEAT_TICKS) {
            worldzero$sendStage(server, state, WORLDZERO_STAGE_LIGHT_REPEAT, WorldZeroFinalePacket.WORLDZERO_ACTION_GLITCH, 100, 1);
        }
        if (elapsedTicks >= WORLDZERO_STRONG_BREAK_TICKS) {
            worldzero$sendStrongBreakStage(server, state, elapsedTicks);
        }
        if (elapsedTicks >= WORLDZERO_STRONG_REPEAT_TICKS) {
            worldzero$sendStage(server, state, WORLDZERO_STAGE_STRONG_REPEAT, WorldZeroFinalePacket.WORLDZERO_ACTION_GLITCH, 100, 3);
        }
        if (elapsedTicks >= WORLDZERO_PEAK_TICKS) {
            worldzero$sendStage(server, state, WORLDZERO_STAGE_PEAK, WorldZeroFinalePacket.WORLDZERO_ACTION_GLITCH, 80, 4);
        }

        worldzero$tickBlockInstability(server, state, worldTicks, elapsedTicks);
    }

    public static boolean worldzero$triggerNow(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator() || player.getServer() == null) {
            return false;
        }

        ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return false;
        }

        long worldTicks = WorldZeroHorrorEventSystem.worldzero$getWorldTicks(overworld);
        FinaleSaveData saveData = worldzero$getSaveData(overworld);
        saveData.worldzero$completed = false;
        worldzero$startFinale(overworld, saveData, worldTicks);
        return true;
    }

    public static boolean worldzero$stopNow(MinecraftServer server) {
        if (server == null) {
            return false;
        }

        SessionState state = WORLDZERO_SESSION_STATES.remove(server);
        boolean changed = state != null && state.worldzero$active;
        if (state != null) {
            worldzero$restoreAllBlocks(server, state);
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null) {
            FinaleSaveData saveData = worldzero$getSaveData(overworld);
            if (saveData.worldzero$started && !saveData.worldzero$completed) {
                saveData.worldzero$completed = true;
                saveData.setDirty();
                changed = true;
            }
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            WorldZeroNetwork.sendFinale(player, WorldZeroFinalePacket.WORLDZERO_ACTION_CLEAR_ALL, 1, 0);
            WorldZeroNetwork.sendFreezeEnd(player);
        }
        return changed;
    }

    public static void worldzero$finishFinalVoid(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }

        ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            FinaleSaveData saveData = worldzero$getSaveData(overworld);
            saveData.worldzero$postFinaleStage = WORLDZERO_POST_STAGE_FINAL_MENU;
            saveData.setDirty();
        }

        WorldZeroNetwork.sendFreezeEnd(player);
        WorldZeroNetwork.sendKeyboardBlock(player, 0);
        WorldZeroNetwork.sendFinale(player, WorldZeroFinalePacket.WORLDZERO_ACTION_FINAL_BLACK_MENU, 1, 0);
    }

    public static void worldzero$prepareFinalMenuEntry(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }

        ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        FinaleSaveData saveData = worldzero$getSaveData(overworld);
        saveData.worldzero$postFinaleStage = WORLDZERO_POST_STAGE_FINAL_MENU;
        saveData.setDirty();
    }

    public static void worldzero$finishAbsoluteFinal(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }

        ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            FinaleSaveData saveData = worldzero$getSaveData(overworld);
            saveData.worldzero$postFinaleStage = WORLDZERO_POST_STAGE_ABSOLUTE_DONE;
            saveData.setDirty();
        }

        WorldZeroNetwork.sendFreezeEnd(player);
        WorldZeroNetwork.sendKeyboardBlock(player, 0);
        WorldZeroNetwork.sendFinale(player, WorldZeroFinalePacket.WORLDZERO_ACTION_FINAL_BLACK_MENU, 1, 1);
    }

    private static void worldzero$startFinale(ServerLevel level, FinaleSaveData saveData, long startWorldTick) {
        MinecraftServer server = level.getServer();
        SessionState oldState = WORLDZERO_SESSION_STATES.remove(server);
        if (oldState != null) {
            worldzero$restoreAllBlocks(server, oldState);
        }

        saveData.worldzero$started = true;
        saveData.worldzero$completed = false;
        saveData.worldzero$startWorldTick = Math.max(0L, startWorldTick);
        saveData.worldzero$postFinaleStage = WORLDZERO_POST_STAGE_NONE;
        saveData.setDirty();

        SessionState state = WORLDZERO_SESSION_STATES.computeIfAbsent(server, ignored -> new SessionState());
        state.worldzero$active = true;
        worldzero$stopConflictingEvents(server);
        state.worldzero$conflictsStopped = true;
        worldzero$sendFinaleToAll(server, WorldZeroFinalePacket.WORLDZERO_ACTION_START_SILENCE, (int) WORLDZERO_LIGHT_BREAK_TICKS, level.random.nextInt());
        state.worldzero$sentStages |= WORLDZERO_STAGE_START;
    }

    private static void worldzero$stopConflictingEvents(MinecraftServer server) {
        WorldZeroMinorAnomalies.worldzero$cancelAll(server);
        WorldZeroBlackEchoJumpscareEvent.worldzero$stopNow(server);
        WorldZeroMajorEventSystem.worldzero$stopAllEvents(server);
        WorldZeroFreezeEvent.worldzero$stopFreezeNow(server);
        WorldZeroFallEvent.worldzero$stopFallNow(server);
        WorldZeroParalysisEvent.worldzero$stopParalysisNow(server);
        WorldZeroFootstepsEvent.worldzero$stopFootstepsNow(server);
        WorldZeroHouseEvent.worldzero$stopHouseNow(server);
        WorldZeroWorldMemoryEvent.worldzero$stopMemoryNow(server);
        WorldZeroLastBlockEvent.worldzero$stopLastBlockNow(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            WorldZeroNetwork.sendMinorAnomaly(
                    player,
                    WorldZeroMinorAnomalyPacket.WORLDZERO_ACTION_CLEAR_ALL,
                    1,
                    0
            );
        }
    }

    private static void worldzero$sendStage(
            MinecraftServer server,
            SessionState state,
            int stage,
            byte action,
            int durationTicks,
            int variant
    ) {
        if ((state.worldzero$sentStages & stage) != 0) {
            return;
        }

        state.worldzero$sentStages |= stage;
        worldzero$sendFinaleToAll(server, action, Math.max(1, durationTicks), variant);
        if (action == WorldZeroFinalePacket.WORLDZERO_ACTION_FULL_FREEZE) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                WorldZeroNetwork.sendFreezeStart(player, Math.max(1, durationTicks));
            }
        }
    }

    private static void worldzero$sendStrongBreakStage(MinecraftServer server, SessionState state, long elapsedTicks) {
        if ((state.worldzero$sentStages & WORLDZERO_STAGE_STRONG_BREAK) != 0) {
            return;
        }

        state.worldzero$sentStages |= WORLDZERO_STAGE_STRONG_BREAK;
        worldzero$sendFinaleToAll(server, WorldZeroFinalePacket.WORLDZERO_ACTION_GLITCH, 100, 2);
        worldzero$sendFinaleToAll(
                server,
                WorldZeroFinalePacket.WORLDZERO_ACTION_SOUND_BREAK,
                (int) Math.max(1L, WORLDZERO_FREEZE_TICKS - elapsedTicks),
                2
        );
    }

    private static void worldzero$sendFinaleToAll(MinecraftServer server, byte action, int durationTicks, int variant) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            WorldZeroNetwork.sendFinale(player, action, durationTicks, variant);
        }
    }

    private static void worldzero$tickBlockInstability(
            MinecraftServer server,
            SessionState state,
            long worldTicks,
            long elapsedTicks
    ) {
        GlitchProfile profile = worldzero$glitchProfile(elapsedTicks);
        if (profile == null || worldTicks < state.worldzero$nextBlockBlinkWorldTick) {
            return;
        }

        state.worldzero$nextBlockBlinkWorldTick = worldTicks + profile.worldzero$intervalTicks
                + server.overworld().random.nextInt(Math.max(1, profile.worldzero$jitterTicks));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel playerLevel = player.serverLevel();
            if (player.isAlive() && !player.isSpectator()) {
                worldzero$blinkBlocksForPlayer(playerLevel, player, state, worldTicks, profile);
            }
        }
    }

    private static GlitchProfile worldzero$glitchProfile(long elapsedTicks) {
        if (elapsedTicks < WORLDZERO_LIGHT_BREAK_TICKS || elapsedTicks >= WORLDZERO_FREEZE_TICKS) {
            return null;
        }
        if (elapsedTicks >= WORLDZERO_PEAK_TICKS) {
            return new GlitchProfile(4, 8, 8, 9, true);
        }
        if (elapsedTicks >= WORLDZERO_STRONG_REPEAT_TICKS) {
            return new GlitchProfile(3, 14, 10, 6, true);
        }
        if (elapsedTicks >= WORLDZERO_STRONG_BREAK_TICKS) {
            return new GlitchProfile(2, 20, 14, 4, true);
        }
        return new GlitchProfile(1, 36, 28, 2, false);
    }

    private static void worldzero$blinkBlocksForPlayer(
            ServerLevel level,
            ServerPlayer player,
            SessionState state,
            long worldTicks,
            GlitchProfile profile
    ) {
        for (int index = 0; index < profile.worldzero$singleBlockCount; index++) {
            BlockPos pos = worldzero$findBlinkBlock(level, player, 5 + profile.worldzero$intensity * 3);
            if (pos != null) {
                worldzero$hideBlock(level, player, state, pos, worldTicks + 1L + level.random.nextInt(2));
            }
        }

        if (!profile.worldzero$allowHoles || level.random.nextInt(3) != 0) {
            return;
        }

        BlockPos center = worldzero$findBlinkBlock(level, player, 7 + profile.worldzero$intensity * 4);
        if (center == null) {
            return;
        }

        int radius = profile.worldzero$intensity >= 4 ? 2 : 1;
        int maxBlocks = 10 + profile.worldzero$intensity * 5;
        int sentBlocks = 0;
        for (int x = -radius; x <= radius && sentBlocks < maxBlocks; x++) {
            for (int y = -1; y <= radius && sentBlocks < maxBlocks; y++) {
                for (int z = -radius; z <= radius && sentBlocks < maxBlocks; z++) {
                    if (level.random.nextInt(4) == 0) {
                        continue;
                    }
                    BlockPos pos = center.offset(x, y, z);
                    if (worldzero$isBlinkableBlock(level, pos, level.getBlockState(pos))) {
                        worldzero$hideBlock(level, player, state, pos, worldTicks + 1L + level.random.nextInt(2));
                        sentBlocks++;
                    }
                }
            }
        }
    }

    private static BlockPos worldzero$findBlinkBlock(ServerLevel level, ServerPlayer player, int radius) {
        BlockPos origin = player.blockPosition();
        for (int attempt = 0; attempt < 30; attempt++) {
            BlockPos pos = origin.offset(
                    Mth.nextInt(level.random, -radius, radius),
                    Mth.nextInt(level.random, -3, 5),
                    Mth.nextInt(level.random, -radius, radius)
            );
            BlockState state = level.getBlockState(pos);
            if (worldzero$isBlinkableBlock(level, pos, state)) {
                return pos;
            }
        }
        return null;
    }

    private static boolean worldzero$isBlinkableBlock(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && !state.hasBlockEntity()
                && !state.is(Blocks.BEDROCK)
                && state.isCollisionShapeFullBlock(level, pos);
    }

    private static void worldzero$hideBlock(
            ServerLevel level,
            ServerPlayer player,
            SessionState state,
            BlockPos pos,
            long restoreWorldTick
    ) {
        player.connection.send(new ClientboundBlockUpdatePacket(pos, Blocks.AIR.defaultBlockState()));
        state.worldzero$pendingBlockRestores.add(new PendingBlockRestore(
                player.getUUID(),
                level.dimension(),
                pos.immutable(),
                restoreWorldTick,
                level.getBlockState(pos)
        ));
    }

    private static void worldzero$processBlockRestores(MinecraftServer server, SessionState state, long worldTicks) {
        Iterator<PendingBlockRestore> iterator = state.worldzero$pendingBlockRestores.iterator();
        while (iterator.hasNext()) {
            PendingBlockRestore restore = iterator.next();
            if (restore.worldzero$restoreWorldTick > worldTicks) {
                continue;
            }

            worldzero$restoreBlock(server, restore);
            iterator.remove();
        }
    }

    private static void worldzero$restoreAllBlocks(MinecraftServer server, SessionState state) {
        for (PendingBlockRestore restore : state.worldzero$pendingBlockRestores) {
            worldzero$restoreBlock(server, restore);
        }
        state.worldzero$pendingBlockRestores.clear();
    }

    private static void worldzero$restoreBlock(MinecraftServer server, PendingBlockRestore restore) {
        ServerLevel level = server.getLevel(restore.worldzero$dimension);
        ServerPlayer player = server.getPlayerList().getPlayer(restore.worldzero$playerId);
        if (level == null || player == null) {
            return;
        }

        BlockState currentState = level.hasChunkAt(restore.worldzero$pos)
                ? level.getBlockState(restore.worldzero$pos)
                : restore.worldzero$state;
        player.connection.send(new ClientboundBlockUpdatePacket(restore.worldzero$pos, currentState));
    }

    private static FinaleSaveData worldzero$getSaveData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FinaleSaveData::load, FinaleSaveData::new, WORLDZERO_SAVE_ID);
    }

    private static final class SessionState {
        private boolean worldzero$active;
        private boolean worldzero$conflictsStopped;
        private int worldzero$sentStages;
        private long worldzero$nextBlockBlinkWorldTick;
        private final List<PendingBlockRestore> worldzero$pendingBlockRestores = new ArrayList<>();
    }

    private record GlitchProfile(
            int worldzero$intensity,
            int worldzero$intervalTicks,
            int worldzero$jitterTicks,
            int worldzero$singleBlockCount,
            boolean worldzero$allowHoles
    ) {
    }

    private record PendingBlockRestore(
            UUID worldzero$playerId,
            ResourceKey<Level> worldzero$dimension,
            BlockPos worldzero$pos,
            long worldzero$restoreWorldTick,
            BlockState worldzero$state
    ) {
    }

    private static final class FinaleSaveData extends SavedData {
        private boolean worldzero$started;
        private boolean worldzero$completed;
        private long worldzero$startWorldTick = WORLDZERO_END_TICKS;
        private int worldzero$postFinaleStage = WORLDZERO_POST_STAGE_NONE;

        private static FinaleSaveData load(CompoundTag tag) {
            FinaleSaveData saveData = new FinaleSaveData();
            saveData.worldzero$started = tag.getBoolean("started");
            saveData.worldzero$completed = tag.getBoolean("completed");
            saveData.worldzero$startWorldTick = Math.max(0L, tag.getLong("start_world_tick"));
            saveData.worldzero$postFinaleStage = Math.max(WORLDZERO_POST_STAGE_NONE, tag.getInt("post_finale_stage"));
            return saveData;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean("started", this.worldzero$started);
            tag.putBoolean("completed", this.worldzero$completed);
            tag.putLong("start_world_tick", this.worldzero$startWorldTick);
            tag.putInt("post_finale_stage", this.worldzero$postFinaleStage);
            return tag;
        }
    }
}
