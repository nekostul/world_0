package ru.nekostul.worldzero.event.horror;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;
import ru.nekostul.worldzero.entity.WorldZeroEchoEntity;
import ru.nekostul.worldzero.entity.WorldZeroEchoPresenceTracker;
import ru.nekostul.worldzero.entity.WorldZeroEntities;
import ru.nekostul.worldzero.event.WorldZeroStoryTime;
import ru.nekostul.worldzero.event.ambient.WorldZeroAmbientSoundEvent;
import ru.nekostul.worldzero.event.fall.WorldZeroFallEvent;
import ru.nekostul.worldzero.event.footsteps.WorldZeroFootstepsEvent;
import ru.nekostul.worldzero.event.freeze.WorldZeroFreezeEvent;
import ru.nekostul.worldzero.event.house.WorldZeroHouseEvent;
import ru.nekostul.worldzero.event.major.WorldZeroMajorEventSystem;
import ru.nekostul.worldzero.event.paralysis.WorldZeroParalysisEvent;
import ru.nekostul.worldzero.event.skywatch.WorldZeroSkyWatchEvent;
import ru.nekostul.worldzero.network.WorldZeroNetwork;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroTrapEvent {
    private static final long WORLDZERO_TICKS_PER_MINUTE = 60L * 20L;
    private static final long WORLDZERO_WINDOW_START_TICKS = 50L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_WINDOW_END_TICKS = 80L * WORLDZERO_TICKS_PER_MINUTE;
    private static final int WORLDZERO_BOX_HALF_SIZE = 3;
    private static final int WORLDZERO_BOX_HEIGHT = 7;
    private static final int WORLDZERO_BOX_INTERIOR_FLOOR_Y_OFFSET = 1;
    private static final int WORLDZERO_REDSTONE_TORCH_COUNT = 3;
    private static final int WORLDZERO_SOUND_END_DROP_DELAY_TICKS = 20;
    private static final int WORLDZERO_DROP_PHASE_TIMEOUT_TICKS = 20 * 18;
    private static final int WORLDZERO_TRAP_FREEZE_DURATION_TICKS = 20 * 60 * 5;
    private static final int WORLDZERO_CHAT_DELAY_MIN_TICKS = 14;
    private static final int WORLDZERO_CHAT_DELAY_MAX_TICKS = 28;
    private static final int WORLDZERO_BOX_RESTORE_BELOW_FLOOR_DISTANCE = 6;
    private static final int WORLDZERO_SKY_OFFSET_BLOCKS = 68;
    private static final double WORLDZERO_ORBIT_RADIUS_MIN = 1.45D;
    private static final double WORLDZERO_ORBIT_RADIUS_MAX = 2.15D;
    private static final double WORLDZERO_ORBIT_SPEED_RADIANS = 0.28D;
    private static final double WORLDZERO_PRE_DROP_FRONT_DISTANCE = 1.6D;
    private static final double WORLDZERO_INTERIOR_CLAMP = 2.1D;
    private static final String WORLDZERO_SAVE_ID = "worldzero_trap_event";
    private static final String[] WORLDZERO_TRAP_WORDS = {
            "glassroot",
            "teethmap",
            "ashmilk",
            "wirefruit",
            "mossburn",
            "nailhive",
            "saltlung",
            "copperveil",
            "hushseed",
            "skinroom",
            "dustprayer",
            "thornmilk",
            "inkstone",
            "suturebird",
            "coldrelay"
    };
    private static final String[] WORLDZERO_TRAP_SYMBOL_WORDS = {
            "////",
            "[::]",
            "<<0>>",
            "##//##",
            "[]{}",
            "||_|",
            "0x0x0",
            "::##::"
    };
    private static final net.minecraft.resources.ResourceLocation WORLDZERO_BEKOROBKA_SOUND_ID = new net.minecraft.resources.ResourceLocation(
            WorldZeroMod.MOD_ID,
            "bekorobka"
    );
    private static final net.minecraft.resources.ResourceLocation WORLDZERO_BEJS3_SOUND_ID = new net.minecraft.resources.ResourceLocation(
            WorldZeroMod.MOD_ID,
            "bejs3"
    );
    private static final Map<MinecraftServer, SessionState> WORLDZERO_SESSION_STATES = new WeakHashMap<>();

    private WorldZeroTrapEvent() {
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
        if (state.worldzero$phase != Phase.INACTIVE) {
            worldzero$tickActiveTrap(level, state);
            return;
        }

        TrapSaveData saveData = worldzero$getSaveData(level);
        if (saveData.worldzero$triggered) {
            return;
        }

        long storyTicks = WorldZeroStoryTime.worldzero$getStoryTicks(level);
        if (saveData.worldzero$triggerTick < 0L) {
            if (storyTicks > WORLDZERO_WINDOW_END_TICKS) {
                saveData.worldzero$triggered = true;
            } else {
                saveData.worldzero$triggerTick = worldzero$randomRange(
                        level,
                        Math.max(storyTicks, WORLDZERO_WINDOW_START_TICKS),
                        WORLDZERO_WINDOW_END_TICKS
                );
            }
            saveData.setDirty();
            return;
        }

        if (storyTicks < saveData.worldzero$triggerTick) {
            return;
        }

        if (storyTicks > WORLDZERO_WINDOW_END_TICKS) {
            saveData.worldzero$triggered = true;
            saveData.setDirty();
            return;
        }

        if (level.isNight() || level.isThundering()) {
            return;
        }

        if (worldzero$hasConflictingEvent(level) || WorldZeroEchoPresenceTracker.worldzero$hasAnyEcho(server)) {
            return;
        }

        ServerPlayer player = worldzero$pickTargetPlayer(level);
        if (player == null) {
            return;
        }

        if (worldzero$startTrap(level, state, player)) {
            saveData.worldzero$triggered = true;
            saveData.setDirty();
        }
    }

    @SubscribeEvent
    public static void worldzero$onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        SessionState state = WORLDZERO_SESSION_STATES.get(server);
        if (state == null
                || state.worldzero$phase != Phase.DROPPING
                || state.worldzero$targetPlayerId == null
                || !state.worldzero$targetPlayerId.equals(player.getUUID())) {
            return;
        }

        event.setDistance(0.0F);
        event.setDamageMultiplier(0.0F);
    }

    @SubscribeEvent
    public static void worldzero$onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && worldzero$isInteractionBlocked(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && worldzero$isInteractionBlocked(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && worldzero$isInteractionBlocked(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && worldzero$isInteractionBlocked(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        SessionState state = WORLDZERO_SESSION_STATES.remove(event.getServer());
        if (state == null || state.worldzero$phase == Phase.INACTIVE) {
            return;
        }
    }

    public static boolean worldzero$isActive(@Nullable MinecraftServer server) {
        if (server == null) {
            return false;
        }

        SessionState state = WORLDZERO_SESSION_STATES.get(server);
        return state != null && state.worldzero$phase != Phase.INACTIVE;
    }

    public static boolean worldzero$stopNow(@Nullable MinecraftServer server) {
        if (server == null) {
            return false;
        }

        SessionState state = WORLDZERO_SESSION_STATES.get(server);
        if (state == null || state.worldzero$phase == Phase.INACTIVE) {
            return false;
        }

        worldzero$clearState(server, state, true);
        return true;
    }

    public static void worldzero$acknowledgeAmbientSoundFinished(ServerPlayer player) {
        if (player == null) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        SessionState state = WORLDZERO_SESSION_STATES.get(server);
        if (state == null
                || state.worldzero$phase != Phase.TRAPPED
                || state.worldzero$targetPlayerId == null
                || !state.worldzero$targetPlayerId.equals(player.getUUID())) {
            return;
        }

        ServerLevel level = player.serverLevel();
        state.worldzero$phase = Phase.PRE_DROP;
        state.worldzero$phaseEndGameTick = level.getGameTime() + WORLDZERO_SOUND_END_DROP_DELAY_TICKS;
    }

    private static void worldzero$tickActiveTrap(ServerLevel level, SessionState state) {
        MinecraftServer server = level.getServer();
        ServerPlayer player = server.getPlayerList().getPlayer(state.worldzero$targetPlayerId);
        if (player == null
                || !player.isAlive()
                || player.isSpectator()
                || player.serverLevel() != level) {
            worldzero$clearState(server, state, true);
            return;
        }

        WorldZeroEchoEntity blakEcho = worldzero$findBlakEcho(server, state.worldzero$echoId);
        if (state.worldzero$phase != Phase.DROPPING && blakEcho == null) {
            worldzero$clearState(server, state, true);
            return;
        }

        switch (state.worldzero$phase) {
            case TRAPPED -> worldzero$tickTrappedPhase(level, state, player, blakEcho);
            case PRE_DROP -> worldzero$tickPreDropPhase(level, state, player, blakEcho);
            case DROPPING -> worldzero$tickDroppingPhase(level, state, player, blakEcho);
            default -> {
            }
        }
    }

    private static void worldzero$tickTrappedPhase(
            ServerLevel level,
            SessionState state,
            ServerPlayer player,
            @Nullable WorldZeroEchoEntity blakEcho
    ) {
        player.fallDistance = 0.0F;
        if (blakEcho != null) {
            state.worldzero$orbitAngle += WORLDZERO_ORBIT_SPEED_RADIANS;
            double pulse = (Math.sin((level.getGameTime() + state.worldzero$orbitAngle) * 0.11D) + 1.0D) * 0.5D;
            double radius = Mth.lerp(pulse, WORLDZERO_ORBIT_RADIUS_MIN, WORLDZERO_ORBIT_RADIUS_MAX);
            double targetX = state.worldzero$boxCenter.getX() + 0.5D + Math.cos(state.worldzero$orbitAngle) * radius;
            double targetZ = state.worldzero$boxCenter.getZ() + 0.5D + Math.sin(state.worldzero$orbitAngle) * radius;
            double targetY = state.worldzero$boxCenter.getY() + WORLDZERO_BOX_INTERIOR_FLOOR_Y_OFFSET;
            worldzero$moveEcho(blakEcho, targetX, targetY, targetZ, player);
        }

        if (state.worldzero$chatCooldownTicks > 0) {
            state.worldzero$chatCooldownTicks--;
        }
        if (state.worldzero$chatCooldownTicks <= 0) {
            player.sendSystemMessage(worldzero$buildTrapChatLine(level.random));
            state.worldzero$chatCooldownTicks = worldzero$randomChatDelay(level);
        }
    }

    private static void worldzero$tickPreDropPhase(
            ServerLevel level,
            SessionState state,
            ServerPlayer player,
            @Nullable WorldZeroEchoEntity blakEcho
    ) {
        player.fallDistance = 0.0F;
        if (blakEcho != null) {
            Vec3 look = worldzero$horizontalLook(player);
            double targetX = state.worldzero$boxCenter.getX() + 0.5D + Mth.clamp(
                    look.x * WORLDZERO_PRE_DROP_FRONT_DISTANCE,
                    -WORLDZERO_INTERIOR_CLAMP,
                    WORLDZERO_INTERIOR_CLAMP
            );
            double targetZ = state.worldzero$boxCenter.getZ() + 0.5D + Mth.clamp(
                    look.z * WORLDZERO_PRE_DROP_FRONT_DISTANCE,
                    -WORLDZERO_INTERIOR_CLAMP,
                    WORLDZERO_INTERIOR_CLAMP
            );
            double targetY = state.worldzero$boxCenter.getY() + WORLDZERO_BOX_INTERIOR_FLOOR_Y_OFFSET;
            worldzero$moveEcho(blakEcho, targetX, targetY, targetZ, player);
        }

        if (level.getGameTime() < state.worldzero$phaseEndGameTick) {
            return;
        }

        worldzero$breakBlockUnderPlayer(level, state, player);
        state.worldzero$phase = Phase.DROPPING;
        state.worldzero$phaseEndGameTick = level.getGameTime() + WORLDZERO_DROP_PHASE_TIMEOUT_TICKS;
    }

    private static void worldzero$tickDroppingPhase(
            ServerLevel level,
            SessionState state,
            ServerPlayer player,
            @Nullable WorldZeroEchoEntity blakEcho
    ) {
        player.fallDistance = 0.0F;
        if (blakEcho != null
                && player.getY() < state.worldzero$boxCenter.getY() - WORLDZERO_BOX_RESTORE_BELOW_FLOOR_DISTANCE) {
            blakEcho.discard();
            state.worldzero$echoId = null;
        }

        if (player.onGround() || player.isInWaterOrBubble() || level.getGameTime() >= state.worldzero$phaseEndGameTick) {
            worldzero$clearState(level.getServer(), state, false);
        }
    }

    private static boolean worldzero$startTrap(ServerLevel level, SessionState state, ServerPlayer player) {
        BlockPos boxCenter = worldzero$resolveBoxCenter(level, player);
        Map<BlockPos, BlockState> replacedStates = new HashMap<>();
        worldzero$buildBox(level, boxCenter, replacedStates);
        worldzero$placeRedstoneTorches(level, boxCenter, replacedStates);

        WorldZeroEchoEntity blakEcho = worldzero$spawnBlakEcho(level, boxCenter, player);
        if (blakEcho == null) {
            worldzero$restoreReplacedBlocks(level, replacedStates);
            return false;
        }

        player.teleportTo(
                boxCenter.getX() + 0.5D,
                boxCenter.getY() + WORLDZERO_BOX_INTERIOR_FLOOR_Y_OFFSET,
                boxCenter.getZ() + 0.5D
        );
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        WorldZeroNetwork.sendFreezeStart(player, WORLDZERO_TRAP_FREEZE_DURATION_TICKS, false);

        state.worldzero$phase = Phase.TRAPPED;
        state.worldzero$targetPlayerId = player.getUUID();
        state.worldzero$echoId = blakEcho.getUUID();
        state.worldzero$boxCenter = boxCenter.immutable();
        state.worldzero$boxBlocks.clear();
        state.worldzero$boxBlocks.putAll(replacedStates);
        state.worldzero$boxRestored = false;
        state.worldzero$phaseEndGameTick = -1L;
        state.worldzero$chatCooldownTicks = worldzero$randomChatDelay(level);
        state.worldzero$orbitAngle = level.random.nextDouble() * (Math.PI * 2.0D);
        WorldZeroNetwork.sendHorrorSound(player, WORLDZERO_BEKOROBKA_SOUND_ID, 1.0F, true);
        return true;
    }

    private static void worldzero$buildBox(ServerLevel level, BlockPos center, Map<BlockPos, BlockState> replacedStates) {
        for (int x = center.getX() - WORLDZERO_BOX_HALF_SIZE; x <= center.getX() + WORLDZERO_BOX_HALF_SIZE; x++) {
            for (int z = center.getZ() - WORLDZERO_BOX_HALF_SIZE; z <= center.getZ() + WORLDZERO_BOX_HALF_SIZE; z++) {
                for (int y = center.getY(); y < center.getY() + WORLDZERO_BOX_HEIGHT; y++) {
                    BlockPos blockPos = new BlockPos(x, y, z);
                    boolean shell = x == center.getX() - WORLDZERO_BOX_HALF_SIZE
                            || x == center.getX() + WORLDZERO_BOX_HALF_SIZE
                            || z == center.getZ() - WORLDZERO_BOX_HALF_SIZE
                            || z == center.getZ() + WORLDZERO_BOX_HALF_SIZE
                            || y == center.getY()
                            || y == center.getY() + WORLDZERO_BOX_HEIGHT - 1;
                    BlockState newState = shell ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    if (blockPos.equals(center)) {
                        newState = Blocks.DIRT.defaultBlockState();
                    }
                    worldzero$storeAndSet(level, replacedStates, blockPos, newState);
                }
            }
        }
    }

    private static void worldzero$placeRedstoneTorches(ServerLevel level, BlockPos center, Map<BlockPos, BlockState> replacedStates) {
        List<BlockPos> candidates = new ArrayList<>();
        for (int x = center.getX() - WORLDZERO_BOX_HALF_SIZE + 1; x <= center.getX() + WORLDZERO_BOX_HALF_SIZE - 1; x++) {
            for (int z = center.getZ() - WORLDZERO_BOX_HALF_SIZE + 1; z <= center.getZ() + WORLDZERO_BOX_HALF_SIZE - 1; z++) {
                if (x == center.getX() && z == center.getZ()) {
                    continue;
                }
                candidates.add(new BlockPos(x, center.getY() + 1, z));
            }
        }

        for (int index = candidates.size() - 1; index > 0; index--) {
            int swapIndex = level.random.nextInt(index + 1);
            BlockPos temp = candidates.get(index);
            candidates.set(index, candidates.get(swapIndex));
            candidates.set(swapIndex, temp);
        }

        int torchCount = Math.min(WORLDZERO_REDSTONE_TORCH_COUNT, candidates.size());
        for (int index = 0; index < torchCount; index++) {
            worldzero$storeAndSet(
                    level,
                    replacedStates,
                    candidates.get(index),
                    Blocks.REDSTONE_TORCH.defaultBlockState()
            );
        }
    }

    private static void worldzero$breakBlockUnderPlayer(ServerLevel level, SessionState state, ServerPlayer player) {
        double centerX = state.worldzero$boxCenter.getX() + 0.5D;
        double centerY = state.worldzero$boxCenter.getY() + WORLDZERO_BOX_INTERIOR_FLOOR_Y_OFFSET;
        double centerZ = state.worldzero$boxCenter.getZ() + 0.5D;
        player.teleportTo(centerX, centerY, centerZ);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        WorldZeroNetwork.sendFreezeEnd(player);
        BlockPos below = state.worldzero$boxCenter;
        BlockState blockState = level.getBlockState(below);
        if (!blockState.isAir()) {
            level.levelEvent(2001, below, Block.getId(blockState));
            level.setBlock(below, Blocks.AIR.defaultBlockState(), 3);
        }
        player.setDeltaMovement(player.getDeltaMovement().x, -1.0D, player.getDeltaMovement().z);
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        WorldZeroNetwork.sendHorrorSound(player, WORLDZERO_BEJS3_SOUND_ID, 1.0F, false);
    }

    private static void worldzero$clearState(MinecraftServer server, SessionState state, boolean abort) {
        if (server == null) {
            state.worldzero$reset();
            return;
        }

        Entity echo = worldzero$findEntity(server, state.worldzero$echoId);
        if (echo != null) {
            echo.discard();
        }

        ServerPlayer player = server.getPlayerList().getPlayer(state.worldzero$targetPlayerId);
        if (player != null) {
            WorldZeroNetwork.sendFreezeEnd(player);
            if (abort) {
                WorldZeroNetwork.sendStopHorrorSounds(player);
            }
        }

        state.worldzero$reset();
    }

    private static void worldzero$restoreBox(ServerLevel level, SessionState state) {
        if (state.worldzero$boxRestored) {
            return;
        }

        worldzero$restoreReplacedBlocks(level, state.worldzero$boxBlocks);
        state.worldzero$boxRestored = true;
        state.worldzero$boxBlocks.clear();
    }

    private static void worldzero$restoreReplacedBlocks(ServerLevel level, Map<BlockPos, BlockState> replacedStates) {
        for (Map.Entry<BlockPos, BlockState> entry : replacedStates.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
    }

    private static void worldzero$storeAndSet(
            ServerLevel level,
            Map<BlockPos, BlockState> replacedStates,
            BlockPos blockPos,
            BlockState newState
    ) {
        replacedStates.putIfAbsent(blockPos.immutable(), level.getBlockState(blockPos));
        level.setBlock(blockPos, newState, 3);
    }

    @Nullable
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

    private static BlockPos worldzero$resolveBoxCenter(ServerLevel level, ServerPlayer player) {
        int x = player.getBlockX();
        int z = player.getBlockZ();
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int referenceY = Math.max(surfaceY, player.getBlockY());
        int minY = level.getMinBuildHeight() + 16;
        int maxY = level.getMaxBuildHeight() - WORLDZERO_BOX_HEIGHT - 1;
        int floorY = Mth.clamp(referenceY + WORLDZERO_SKY_OFFSET_BLOCKS, minY, maxY);
        return new BlockPos(x, floorY, z);
    }

    @Nullable
    private static WorldZeroEchoEntity worldzero$spawnBlakEcho(ServerLevel level, BlockPos boxCenter, ServerPlayer player) {
        WorldZeroEchoEntity blakEcho = WorldZeroEntities.WORLDZERO_BLAK_ECHO.get().create(level);
        if (blakEcho == null) {
            return null;
        }

        double spawnX = boxCenter.getX() + 0.5D + WORLDZERO_ORBIT_RADIUS_MAX;
        double spawnY = boxCenter.getY() + WORLDZERO_BOX_INTERIOR_FLOOR_Y_OFFSET;
        double spawnZ = boxCenter.getZ() + 0.5D;
        blakEcho.moveTo(spawnX, spawnY, spawnZ, 0.0F, 0.0F);
        blakEcho.setSilent(true);
        blakEcho.setDeltaMovement(0.0D, 0.0D, 0.0D);
        level.addFreshEntity(blakEcho);
        worldzero$faceEcho(blakEcho, player);
        return blakEcho;
    }

    private static void worldzero$moveEcho(WorldZeroEchoEntity blakEcho, double x, double y, double z, ServerPlayer player) {
        blakEcho.setPos(x, y, z);
        blakEcho.setDeltaMovement(0.0D, 0.0D, 0.0D);
        worldzero$faceEcho(blakEcho, player);
    }

    private static void worldzero$faceEcho(WorldZeroEchoEntity blakEcho, ServerPlayer player) {
        double deltaX = player.getX() - blakEcho.getX();
        double deltaZ = player.getZ() - blakEcho.getZ();
        float yaw = (float) (Mth.atan2(deltaZ, deltaX) * (180.0D / Math.PI)) - 90.0F;
        blakEcho.setYRot(yaw);
        blakEcho.setYHeadRot(yaw);
        blakEcho.setYBodyRot(yaw);
    }

    private static net.minecraft.network.chat.Component worldzero$buildTrapChatLine(net.minecraft.util.RandomSource random) {
        boolean obfuscated = random.nextFloat() < 0.35F;
        String word = obfuscated
                ? WORLDZERO_TRAP_SYMBOL_WORDS[random.nextInt(WORLDZERO_TRAP_SYMBOL_WORDS.length)]
                : WORLDZERO_TRAP_WORDS[random.nextInt(WORLDZERO_TRAP_WORDS.length)];
        net.minecraft.network.chat.MutableComponent wordComponent = net.minecraft.network.chat.Component.literal(word)
                .withStyle(ChatFormatting.RED);
        if (obfuscated) {
            wordComponent.withStyle(ChatFormatting.OBFUSCATED);
        }
        return net.minecraft.network.chat.Component.literal("<> ")
                .withStyle(ChatFormatting.RED)
                .append(wordComponent);
    }

    private static int worldzero$randomChatDelay(ServerLevel level) {
        return level.random.nextInt(WORLDZERO_CHAT_DELAY_MAX_TICKS - WORLDZERO_CHAT_DELAY_MIN_TICKS + 1)
                + WORLDZERO_CHAT_DELAY_MIN_TICKS;
    }

    private static boolean worldzero$isInteractionBlocked(@Nullable ServerPlayer player) {
        if (player == null) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        SessionState state = WORLDZERO_SESSION_STATES.get(server);
        return state != null
                && state.worldzero$targetPlayerId != null
                && state.worldzero$targetPlayerId.equals(player.getUUID())
                && (state.worldzero$phase == Phase.TRAPPED || state.worldzero$phase == Phase.PRE_DROP);
    }

    @Nullable
    private static WorldZeroEchoEntity worldzero$findBlakEcho(MinecraftServer server, UUID entityId) {
        Entity entity = worldzero$findEntity(server, entityId);
        return entity instanceof WorldZeroEchoEntity echoEntity ? echoEntity : null;
    }

    @Nullable
    private static Entity worldzero$findEntity(MinecraftServer server, UUID entityId) {
        if (server == null || entityId == null) {
            return null;
        }

        for (ServerLevel serverLevel : server.getAllLevels()) {
            Entity entity = serverLevel.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }

        return null;
    }

    private static Vec3 worldzero$horizontalLook(ServerPlayer player) {
        Vec3 look = player.getViewVector(1.0F);
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() < 0.0001D) {
            float radians = (float) Math.toRadians(player.getYRot());
            horizontalLook = new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
        }
        return horizontalLook.normalize();
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
                || WorldZeroHorrorFinale.worldzero$isActive(server)
                || WorldZeroHorrorEventSystem.worldzero$isMinorAnomalyActive(level)
                || WorldZeroMajorEventSystem.worldzero$isMajorEventActive(server)
                || WorldZeroAmbientSoundEvent.worldzero$isMajorEventStartBlocked(level);
    }

    private static long worldzero$randomRange(ServerLevel level, long minValue, long maxValue) {
        if (minValue >= maxValue) {
            return minValue;
        }

        long bound = maxValue - minValue + 1L;
        return minValue + Math.floorMod(level.random.nextLong(), bound);
    }

    private static TrapSaveData worldzero$getSaveData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                TrapSaveData::worldzero$load,
                TrapSaveData::new,
                WORLDZERO_SAVE_ID
        );
    }

    private enum Phase {
        INACTIVE,
        TRAPPED,
        PRE_DROP,
        DROPPING
    }

    private static final class SessionState {
        private Phase worldzero$phase = Phase.INACTIVE;
        private UUID worldzero$targetPlayerId;
        private UUID worldzero$echoId;
        private BlockPos worldzero$boxCenter;
        private final Map<BlockPos, BlockState> worldzero$boxBlocks = new HashMap<>();
        private boolean worldzero$boxRestored;
        private long worldzero$phaseEndGameTick = -1L;
        private int worldzero$chatCooldownTicks;
        private double worldzero$orbitAngle;

        private void worldzero$reset() {
            this.worldzero$phase = Phase.INACTIVE;
            this.worldzero$targetPlayerId = null;
            this.worldzero$echoId = null;
            this.worldzero$boxCenter = null;
            this.worldzero$boxBlocks.clear();
            this.worldzero$boxRestored = false;
            this.worldzero$phaseEndGameTick = -1L;
            this.worldzero$chatCooldownTicks = 0;
            this.worldzero$orbitAngle = 0.0D;
        }
    }

    private static final class TrapSaveData extends SavedData {
        private long worldzero$triggerTick = -1L;
        private boolean worldzero$triggered;

        private static TrapSaveData worldzero$load(CompoundTag tag) {
            TrapSaveData saveData = new TrapSaveData();
            saveData.worldzero$triggerTick = tag.contains("trigger_tick") ? tag.getLong("trigger_tick") : -1L;
            saveData.worldzero$triggered = tag.contains("triggered")
                    ? tag.getBoolean("triggered")
                    : tag.getBoolean("completed");
            return saveData;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putLong("trigger_tick", this.worldzero$triggerTick);
            tag.putBoolean("triggered", this.worldzero$triggered);
            tag.putBoolean("completed", this.worldzero$triggered);
            return tag;
        }
    }
}
