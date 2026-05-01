package ru.nekostul.worldzero;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroLastBlockEvent {
    private static final String WORLDZERO_SAVE_ID = "worldzero_last_block_event";
    private static final int WORLDZERO_UNDERGROUND_MAX_Y = 40;
    private static final double WORLDZERO_TRIGGER_CHANCE = 0.02D;
    private static final double WORLDZERO_TWO_TICK_CHANCE = 0.08D;
    private static final long WORLDZERO_BLACK_ECHO_LIFETIME_TICKS = 6L;
    private static final long WORLDZERO_ACTIVATION_PLAYTIME_TICKS = 90L * 60L * 20L;
    private static final long WORLDZERO_PLAYTIME_SAVE_INTERVAL_TICKS = 30L * 20L;
    private static final long WORLDZERO_COOLDOWN_MIN_TICKS = 5L * 60L * 20L;
    private static final long WORLDZERO_COOLDOWN_MAX_TICKS = 12L * 60L * 20L;
    private static final double WORLDZERO_DEBUG_TRACE_DISTANCE_BLOCKS = 6.0D;
    private static final int WORLDZERO_CLOSED_MINE_SCAN_RADIUS_BLOCKS = 6;
    private static final int WORLDZERO_CLOSED_MINE_MAX_OPEN_COLUMNS = 42;
    private static final AABB WORLDZERO_ENTITY_SCAN_AABB = new AABB(
            -30_000_000.0D,
            -2_048.0D,
            -30_000_000.0D,
            30_000_000.0D,
            4_096.0D,
            30_000_000.0D
    );
    private static final Map<MinecraftServer, SessionState> WORLDZERO_SERVER_STATES = new WeakHashMap<>();

    private WorldZeroLastBlockEvent() {
    }

    @SubscribeEvent
    public static void worldzero$onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.player instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }

        if (!player.isAlive() || player.isSpectator() || player.serverLevel().dimension() != Level.OVERWORLD) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        SessionState sessionState = WORLDZERO_SERVER_STATES.computeIfAbsent(server, ignored -> new SessionState());
        PlayerState playerState = sessionState.worldzero$playerStates.computeIfAbsent(
                player.getUUID(),
                ignored -> new PlayerState()
        );
        worldzero$loadPersistentPlayerState(player.serverLevel(), player.getUUID(), playerState);

        long gameTime = player.serverLevel().getGameTime();
        if (!WorldZeroStoryTime.worldzero$countsTowardStoryTime(player)) {
            if (gameTime - playerState.worldzero$lastPlayTimeSaveGameTick >= WORLDZERO_PLAYTIME_SAVE_INTERVAL_TICKS) {
                worldzero$savePersistentPlayerState(player.serverLevel(), player.getUUID(), playerState);
            }
            return;
        }

        playerState.worldzero$elapsedPlayTicks++;

        if (gameTime - playerState.worldzero$lastPlayTimeSaveGameTick < WORLDZERO_PLAYTIME_SAVE_INTERVAL_TICKS) {
            return;
        }

        worldzero$savePersistentPlayerState(player.serverLevel(), player.getUUID(), playerState);
    }

    @SubscribeEvent
    public static void worldzero$onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        if (!(event.getLevel() instanceof ServerLevel level) || level.isClientSide() || level.dimension() != Level.OVERWORLD) {
            return;
        }

        if (!WorldZeroStoryTime.worldzero$canReceiveStoryEvent(player) || event.getPos().getY() >= WORLDZERO_UNDERGROUND_MAX_Y) {
            return;
        }

        if (!worldzero$isMineBlock(event.getState())) {
            return;
        }

        List<Direction> directions = worldzero$buildBreakDirectionOrder(player, event.getPos());
        if (worldzero$findSpawnPlan(level, player, event.getPos(), directions) == null) {
            return;
        }

        SessionState sessionState = WORLDZERO_SERVER_STATES.computeIfAbsent(level.getServer(), ignored -> new SessionState());
        PlayerState playerState = sessionState.worldzero$playerStates.computeIfAbsent(
                player.getUUID(),
                ignored -> new PlayerState()
        );
        worldzero$loadPersistentPlayerState(level, player.getUUID(), playerState);
        if (playerState.worldzero$elapsedPlayTicks < WORLDZERO_ACTIVATION_PLAYTIME_TICKS) {
            return;
        }

        long gameTime = level.getGameTime();
        Long nextAllowedTick = sessionState.worldzero$nextAllowedByPlayer.get(player.getUUID());
        if (nextAllowedTick != null && gameTime < nextAllowedTick) {
            return;
        }

        if (level.random.nextDouble() >= WORLDZERO_TRIGGER_CHANCE) {
            return;
        }

        if (worldzero$hasConflictingEvent(level.getServer()) || worldzero$hasActiveEcho(level.getServer())) {
            return;
        }

        sessionState.worldzero$nextAllowedByPlayer.put(player.getUUID(), gameTime + worldzero$randomCooldown(level));
        sessionState.worldzero$pendingAppearances.add(new PendingAppearance(
                level.dimension(),
                player.getUUID(),
                event.getPos().immutable(),
                directions,
                gameTime + 1L,
                worldzero$visibleTicks(level)
        ));
    }

    @SubscribeEvent
    public static void worldzero$onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.level instanceof ServerLevel level) || level.isClientSide() || level.dimension() != Level.OVERWORLD) {
            return;
        }

        SessionState sessionState = WORLDZERO_SERVER_STATES.get(level.getServer());
        if (sessionState == null) {
            return;
        }

        long gameTime = level.getGameTime();
        worldzero$spawnPendingAppearances(level, sessionState, gameTime);
        worldzero$discardExpiredAppearances(level, sessionState, gameTime);
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_SERVER_STATES.remove(event.getServer());
    }

    public static boolean worldzero$triggerLastBlockNow(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator() || player.serverLevel().dimension() != Level.OVERWORLD) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null || worldzero$hasConflictingEvent(server) || worldzero$hasActiveEcho(server)) {
            return false;
        }

        BlockHitResult hitResult = player.level().clip(new ClipContext(
                player.getEyePosition(),
                player.getEyePosition().add(player.getLookAngle().scale(WORLDZERO_DEBUG_TRACE_DISTANCE_BLOCKS)),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return false;
        }

        return worldzero$spawnAppearanceNow(
                player.serverLevel(),
                player,
                hitResult.getBlockPos(),
                worldzero$buildDebugDirectionOrder(player, hitResult),
                1,
                false
        );
    }

    public static boolean worldzero$stopLastBlockNow(MinecraftServer server) {
        SessionState sessionState = WORLDZERO_SERVER_STATES.remove(server);
        if (server == null || sessionState == null) {
            return false;
        }

        boolean changed = !sessionState.worldzero$pendingAppearances.isEmpty()
                || !sessionState.worldzero$activeAppearances.isEmpty();
        for (ActiveAppearance activeAppearance : sessionState.worldzero$activeAppearances) {
            Entity entity = worldzero$findEntity(server, activeAppearance.worldzero$entityId);
            if (entity != null) {
                entity.discard();
                changed = true;
            }
        }
        return changed;
    }

    private static void worldzero$spawnPendingAppearances(
            ServerLevel level,
            SessionState sessionState,
            long gameTime
    ) {
        Iterator<PendingAppearance> iterator = sessionState.worldzero$pendingAppearances.iterator();
        while (iterator.hasNext()) {
            PendingAppearance pendingAppearance = iterator.next();
            if (pendingAppearance.worldzero$spawnTick > gameTime
                    || pendingAppearance.worldzero$dimension != level.dimension()) {
                continue;
            }

            iterator.remove();

            if (worldzero$hasConflictingEvent(level.getServer()) || worldzero$hasActiveEcho(level.getServer())) {
                continue;
            }

            ServerPlayer player = level.getServer().getPlayerList().getPlayer(pendingAppearance.worldzero$playerId);
            if (player == null || !player.isAlive() || player.isSpectator() || player.serverLevel() != level) {
                continue;
            }

            worldzero$spawnAppearanceNow(
                    level,
                    player,
                    pendingAppearance.worldzero$brokenPos,
                    pendingAppearance.worldzero$directions,
                    pendingAppearance.worldzero$visibleTicks,
                    true
            );
        }
    }

    private static void worldzero$discardExpiredAppearances(
            ServerLevel level,
            SessionState sessionState,
            long gameTime
    ) {
        Iterator<ActiveAppearance> iterator = sessionState.worldzero$activeAppearances.iterator();
        while (iterator.hasNext()) {
            ActiveAppearance activeAppearance = iterator.next();
            if (activeAppearance.worldzero$dimension != level.dimension()) {
                continue;
            }

            Entity entity = level.getEntity(activeAppearance.worldzero$entityId);
            if (!(entity instanceof WorldZeroEchoEntity echo)) {
                iterator.remove();
                continue;
            }

            ServerPlayer player = level.getServer().getPlayerList().getPlayer(activeAppearance.worldzero$playerId);
            if (player == null || !player.isAlive() || player.isSpectator() || player.serverLevel() != level) {
                iterator.remove();
                entity.discard();
                continue;
            }

            if (activeAppearance.worldzero$seenTick < 0L) {
                if (worldzero$isSeenByPlayer(echo, player)) {
                    activeAppearance.worldzero$seenTick = gameTime;
                    activeAppearance.worldzero$discardTick = gameTime + WORLDZERO_BLACK_ECHO_LIFETIME_TICKS;
                }
                continue;
            }

            if (gameTime < activeAppearance.worldzero$discardTick) {
                continue;
            }

            iterator.remove();
            entity.discard();
        }
    }

    private static boolean worldzero$spawnAppearanceNow(
            ServerLevel level,
            ServerPlayer player,
            BlockPos referencePos,
            List<Direction> directions,
            int visibleTicks,
            boolean requireOpenedBlock
    ) {
        if (requireOpenedBlock && !level.getBlockState(referencePos).isAir()) {
            return false;
        }

        SpawnPlan spawnPlan = worldzero$findSpawnPlan(level, player, referencePos, directions);
        if (spawnPlan == null) {
            return false;
        }

        if (!worldzero$isClosedMine(level, spawnPlan.worldzero$spawnPos, spawnPlan.worldzero$requiresCarve)) {
            return false;
        }

        if (spawnPlan.worldzero$requiresCarve && !worldzero$carveSpawnColumn(level, spawnPlan.worldzero$spawnPos)) {
            return false;
        }

        BlockPos spawnPos = spawnPlan.worldzero$spawnPos;
        if (!worldzero$isValidSpawnBlock(level, spawnPos)) {
            return false;
        }

        AABB spawnBox = WorldZeroEntities.WORLDZERO_BLACK_ECHO.get().getDimensions().makeBoundingBox(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D
        );
        if (!level.noCollision(spawnBox)
                || level.containsAnyLiquid(spawnBox)
                || spawnBox.intersects(player.getBoundingBox())) {
            return false;
        }

        WorldZeroEchoEntity blackEcho = WorldZeroEntities.WORLDZERO_BLACK_ECHO.get().create(level);
        if (blackEcho == null) {
            return false;
        }

        float yaw = worldzero$yawTowardPlayer(spawnPos, player);
        blackEcho.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, yaw, 0.0F);
        blackEcho.setNoGravity(true);
        blackEcho.setDeltaMovement(0.0D, 0.0D, 0.0D);
        blackEcho.setSilent(true);
        level.addFreshEntity(blackEcho);

        SessionState sessionState = WORLDZERO_SERVER_STATES.computeIfAbsent(level.getServer(), ignored -> new SessionState());
        sessionState.worldzero$activeAppearances.add(new ActiveAppearance(
                level.dimension(),
                player.getUUID(),
                blackEcho.getUUID(),
                -1L,
                -1L
        ));
        return true;
    }

    @Nullable
    private static SpawnPlan worldzero$findSpawnPlan(
            ServerLevel level,
            ServerPlayer player,
            BlockPos brokenPos,
            List<Direction> directions
    ) {
        int tunnelBaseY = worldzero$resolveTunnelBaseY(player, brokenPos);
        for (Direction direction : directions) {
            BlockPos basePos = new BlockPos(
                    brokenPos.getX() + direction.getStepX(),
                    tunnelBaseY,
                    brokenPos.getZ() + direction.getStepZ()
            );
            if (worldzero$isValidSpawnBlock(level, basePos)) {
                return new SpawnPlan(basePos.immutable(), false);
            }
            if (worldzero$canCarveSpawnColumn(level, basePos)) {
                return new SpawnPlan(basePos.immutable(), true);
            }
        }
        return null;
    }

    private static int worldzero$resolveTunnelBaseY(ServerPlayer player, BlockPos brokenPos) {
        return Math.min(brokenPos.getY(), player.blockPosition().getY());
    }

    private static boolean worldzero$canCarveSpawnColumn(ServerLevel level, BlockPos pos) {
        BlockState lowerState = level.getBlockState(pos);
        BlockState upperState = level.getBlockState(pos.above());
        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);

        if (!belowState.isFaceSturdy(level, belowPos, Direction.UP) || !belowState.getFluidState().isEmpty()) {
            return false;
        }

        return worldzero$isCarvableMineBlock(lowerState) && worldzero$isCarvableMineBlock(upperState);
    }

    private static boolean worldzero$carveSpawnColumn(ServerLevel level, BlockPos pos) {
        if (!worldzero$canCarveSpawnColumn(level, pos)) {
            return false;
        }

        worldzero$clearBlockSilently(level, pos);
        worldzero$clearBlockSilently(level, pos.above());
        return true;
    }

    private static void worldzero$clearBlockSilently(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
        level.sendBlockUpdated(pos, state, Blocks.AIR.defaultBlockState(), 3);
    }

    private static boolean worldzero$isValidSpawnBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockState aboveState = level.getBlockState(pos.above());
        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);
        return state.isAir()
                && aboveState.isAir()
                && state.getFluidState().isEmpty()
                && aboveState.getFluidState().isEmpty()
                && !belowState.isAir()
                && belowState.getFluidState().isEmpty()
                && belowState.isFaceSturdy(level, belowPos, Direction.UP);
    }

    private static boolean worldzero$isCarvableMineBlock(BlockState state) {
        return state.isAir() || (state.getFluidState().isEmpty() && worldzero$isMineBlock(state));
    }

    private static boolean worldzero$isClosedMine(ServerLevel level, BlockPos origin, boolean simulateOriginCarve) {
        if (!worldzero$isOpenMineColumn(level, origin, origin, simulateOriginCarve)) {
            return false;
        }

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(origin);
        visited.add(origin.asLong());
        int openColumns = 0;

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            openColumns++;
            if (openColumns > WORLDZERO_CLOSED_MINE_MAX_OPEN_COLUMNS) {
                return false;
            }

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.relative(direction);
                if (Math.abs(next.getX() - origin.getX()) > WORLDZERO_CLOSED_MINE_SCAN_RADIUS_BLOCKS
                        || Math.abs(next.getZ() - origin.getZ()) > WORLDZERO_CLOSED_MINE_SCAN_RADIUS_BLOCKS) {
                    continue;
                }

                if (!visited.add(next.asLong())
                        || !worldzero$isOpenMineColumn(level, next, origin, simulateOriginCarve)) {
                    continue;
                }

                queue.addLast(next);
            }
        }

        return true;
    }

    private static boolean worldzero$isOpenMineColumn(
            ServerLevel level,
            BlockPos pos,
            BlockPos simulatedOrigin,
            boolean simulateOriginCarve
    ) {
        return worldzero$isMineColumnPassable(level, pos, simulatedOrigin, simulateOriginCarve)
                && worldzero$isMineColumnPassable(level, pos.above(), simulatedOrigin, simulateOriginCarve);
    }

    private static boolean worldzero$isMineColumnPassable(
            ServerLevel level,
            BlockPos pos,
            BlockPos simulatedOrigin,
            boolean simulateOriginCarve
    ) {
        if (simulateOriginCarve && (pos.equals(simulatedOrigin) || pos.equals(simulatedOrigin.above()))) {
            return true;
        }

        BlockState state = level.getBlockState(pos);
        return state.isAir() && state.getFluidState().isEmpty();
    }

    private static List<Direction> worldzero$buildBreakDirectionOrder(ServerPlayer player, BlockPos brokenPos) {
        Vec3 toBlock = Vec3.atCenterOf(brokenPos).subtract(player.getEyePosition());
        Direction primaryDirection = worldzero$horizontalDirection(toBlock.x, toBlock.z);
        Direction lookDirection = worldzero$horizontalLookDirection(player);
        return worldzero$buildDirectionOrder(primaryDirection, lookDirection);
    }

    private static List<Direction> worldzero$buildDebugDirectionOrder(
            ServerPlayer player,
            BlockHitResult hitResult
    ) {
        Direction hitDirection = hitResult.getDirection().getOpposite();
        Direction breakDirection = worldzero$buildBreakDirectionOrder(player, hitResult.getBlockPos()).get(0);
        return worldzero$buildDirectionOrder(hitDirection, breakDirection);
    }

    private static List<Direction> worldzero$buildDirectionOrder(
            @Nullable Direction primaryDirection,
            @Nullable Direction secondaryDirection
    ) {
        List<Direction> directions = new ArrayList<>(4);
        worldzero$addHorizontalDirection(directions, primaryDirection);
        worldzero$addHorizontalDirection(directions, secondaryDirection);
        worldzero$addHorizontalDirection(directions, secondaryDirection != null ? secondaryDirection.getOpposite() : null);
        worldzero$addHorizontalDirection(directions, Direction.NORTH);
        worldzero$addHorizontalDirection(directions, Direction.SOUTH);
        worldzero$addHorizontalDirection(directions, Direction.EAST);
        worldzero$addHorizontalDirection(directions, Direction.WEST);
        return directions;
    }

    private static void worldzero$addHorizontalDirection(List<Direction> directions, @Nullable Direction direction) {
        if (direction == null || direction.getAxis().isVertical() || directions.contains(direction)) {
            return;
        }

        directions.add(direction);
    }

    private static Direction worldzero$horizontalLookDirection(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        return worldzero$horizontalDirection(look.x, look.z);
    }

    private static Direction worldzero$horizontalDirection(double x, double z) {
        if (Math.abs(x) >= Math.abs(z)) {
            return x >= 0.0D ? Direction.EAST : Direction.WEST;
        }

        return z >= 0.0D ? Direction.SOUTH : Direction.NORTH;
    }

    private static float worldzero$yawTowardPlayer(BlockPos spawnPos, ServerPlayer player) {
        double deltaX = player.getX() - (spawnPos.getX() + 0.5D);
        double deltaZ = player.getZ() - (spawnPos.getZ() + 0.5D);
        return (float) (Math.atan2(deltaZ, deltaX) * (180.0D / Math.PI)) - 90.0F;
    }

    private static boolean worldzero$isSeenByPlayer(WorldZeroEchoEntity echo, ServerPlayer player) {
        if (!player.hasLineOfSight(echo)) {
            return false;
        }

        Vec3 eyePosition = player.getEyePosition();
        Vec3 directionToEcho = echo.getBoundingBox().getCenter().subtract(eyePosition);
        if (directionToEcho.lengthSqr() < 0.0001D) {
            return false;
        }

        Vec3 lookVector = player.getViewVector(1.0F).normalize();
        return lookVector.dot(directionToEcho.normalize()) >= 0.8D;
    }

    private static int worldzero$visibleTicks(ServerLevel level) {
        return level.random.nextDouble() < WORLDZERO_TWO_TICK_CHANCE ? 2 : 1;
    }

    private static long worldzero$randomCooldown(ServerLevel level) {
        long span = WORLDZERO_COOLDOWN_MAX_TICKS - WORLDZERO_COOLDOWN_MIN_TICKS + 1L;
        return WORLDZERO_COOLDOWN_MIN_TICKS + (long) (level.random.nextDouble() * span);
    }

    private static boolean worldzero$isMineBlock(BlockState state) {
        if (state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.COBBLED_DEEPSLATE)
                || state.is(Blocks.TUFF)) {
            return true;
        }

        String blockPath = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return blockPath.endsWith("_ore");
    }

    private static boolean worldzero$hasConflictingEvent(MinecraftServer server) {
        return WorldZeroFreezeEvent.worldzero$isFreezeActive(server)
                || WorldZeroFallEvent.worldzero$isFallActive(server)
                || WorldZeroFootstepsEvent.worldzero$isFootstepsActive(server)
                || WorldZeroHouseEvent.worldzero$isHouseActive(server)
                || WorldZeroParalysisEvent.worldzero$isParalysisActive(server);
    }

    private static boolean worldzero$hasActiveEcho(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() == WorldZeroVoidPortalDimension.WORLDZERO_VOIDPORTAL_LEVEL) {
                continue;
            }

            if (!level.getEntitiesOfClass(
                    WorldZeroEchoEntity.class,
                    WORLDZERO_ENTITY_SCAN_AABB,
                    entity -> entity.getType() == WorldZeroEntities.WORLDZERO_ECHO.get()
                            || entity.getType() == WorldZeroEntities.WORLDZERO_BLACK_ECHO.get()
            ).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void worldzero$loadPersistentPlayerState(
            ServerLevel level,
            UUID playerId,
            PlayerState playerState
    ) {
        if (playerState.worldzero$persistentLoaded) {
            return;
        }

        PersistentPlayerState persistentPlayerState = worldzero$getSaveData(level).worldzero$playerStates.get(playerId);
        if (persistentPlayerState != null) {
            playerState.worldzero$elapsedPlayTicks = persistentPlayerState.worldzero$elapsedPlayTicks;
        }

        playerState.worldzero$lastPlayTimeSaveGameTick = level.getGameTime();
        playerState.worldzero$persistentLoaded = true;
    }

    private static void worldzero$savePersistentPlayerState(
            ServerLevel level,
            UUID playerId,
            PlayerState playerState
    ) {
        LastBlockSaveData saveData = worldzero$getSaveData(level);
        PersistentPlayerState persistentPlayerState = saveData.worldzero$playerStates.computeIfAbsent(
                playerId,
                ignored -> new PersistentPlayerState()
        );
        persistentPlayerState.worldzero$elapsedPlayTicks = playerState.worldzero$elapsedPlayTicks;
        playerState.worldzero$lastPlayTimeSaveGameTick = level.getGameTime();
        saveData.setDirty();
    }

    private static LastBlockSaveData worldzero$getSaveData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(LastBlockSaveData::load, LastBlockSaveData::new, WORLDZERO_SAVE_ID);
    }

    @Nullable
    private static Entity worldzero$findEntity(MinecraftServer server, @Nullable UUID entityId) {
        if (server == null || entityId == null) {
            return null;
        }

        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }

        return null;
    }

    private static final class SessionState {
        private final Map<UUID, PlayerState> worldzero$playerStates = new HashMap<>();
        private final Map<UUID, Long> worldzero$nextAllowedByPlayer = new HashMap<>();
        private final List<PendingAppearance> worldzero$pendingAppearances = new ArrayList<>();
        private final List<ActiveAppearance> worldzero$activeAppearances = new ArrayList<>();
    }

    private static final class PlayerState {
        private boolean worldzero$persistentLoaded;
        private long worldzero$elapsedPlayTicks;
        private long worldzero$lastPlayTimeSaveGameTick = -1L;
    }

    private static final class PersistentPlayerState {
        private long worldzero$elapsedPlayTicks;
    }

    private static final class LastBlockSaveData extends SavedData {
        private final Map<UUID, PersistentPlayerState> worldzero$playerStates = new HashMap<>();

        private static LastBlockSaveData load(CompoundTag tag) {
            LastBlockSaveData saveData = new LastBlockSaveData();
            ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
            for (int index = 0; index < players.size(); index++) {
                CompoundTag playerTag = players.getCompound(index);
                if (!playerTag.hasUUID("player_id")) {
                    continue;
                }

                PersistentPlayerState playerState = new PersistentPlayerState();
                playerState.worldzero$elapsedPlayTicks = playerTag.getLong("play_time_ticks");
                saveData.worldzero$playerStates.put(playerTag.getUUID("player_id"), playerState);
            }
            return saveData;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag players = new ListTag();
            for (Map.Entry<UUID, PersistentPlayerState> entry : this.worldzero$playerStates.entrySet()) {
                CompoundTag playerTag = new CompoundTag();
                playerTag.putUUID("player_id", entry.getKey());
                playerTag.putLong("play_time_ticks", entry.getValue().worldzero$elapsedPlayTicks);
                players.add(playerTag);
            }
            tag.put("players", players);
            return tag;
        }
    }

    private static final class PendingAppearance {
        private final ResourceKey<Level> worldzero$dimension;
        private final UUID worldzero$playerId;
        private final BlockPos worldzero$brokenPos;
        private final List<Direction> worldzero$directions;
        private final long worldzero$spawnTick;
        private final int worldzero$visibleTicks;

        private PendingAppearance(
                ResourceKey<Level> dimension,
                UUID playerId,
                BlockPos brokenPos,
                List<Direction> directions,
                long spawnTick,
                int visibleTicks
        ) {
            this.worldzero$dimension = dimension;
            this.worldzero$playerId = playerId;
            this.worldzero$brokenPos = brokenPos;
            this.worldzero$directions = List.copyOf(directions);
            this.worldzero$spawnTick = spawnTick;
            this.worldzero$visibleTicks = visibleTicks;
        }
    }

    private static final class ActiveAppearance {
        private final ResourceKey<Level> worldzero$dimension;
        private final UUID worldzero$playerId;
        private final UUID worldzero$entityId;
        private long worldzero$seenTick;
        private long worldzero$discardTick;

        private ActiveAppearance(
                ResourceKey<Level> dimension,
                UUID playerId,
                UUID entityId,
                long seenTick,
                long discardTick
        ) {
            this.worldzero$dimension = dimension;
            this.worldzero$playerId = playerId;
            this.worldzero$entityId = entityId;
            this.worldzero$seenTick = seenTick;
            this.worldzero$discardTick = discardTick;
        }
    }

    private static final class SpawnPlan {
        private final BlockPos worldzero$spawnPos;
        private final boolean worldzero$requiresCarve;

        private SpawnPlan(BlockPos spawnPos, boolean requiresCarve) {
            this.worldzero$spawnPos = spawnPos;
            this.worldzero$requiresCarve = requiresCarve;
        }
    }
}
