package ru.nekostul.worldzero;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroKoridorDimension {
    public static final ResourceKey<Level> WORLDZERO_KORIDOR_LEVEL = ResourceKey.create(
            Registries.DIMENSION,
            new ResourceLocation(WorldZeroMod.MOD_ID, "koridor")
    );

    private static final ResourceLocation WORLDZERO_KORIDOR_STRUCTURE_ID = new ResourceLocation(
            WorldZeroMod.MOD_ID,
            "koridor"
    );
    private static final ResourceLocation WORLDZERO_WOODEN_DOOR_OPEN_SOUND_ID = new ResourceLocation(
            "minecraft",
            "block.wooden_door.open"
    );
    private static final ResourceLocation WORLDZERO_WOODEN_DOOR_CLOSE_SOUND_ID = new ResourceLocation(
            "minecraft",
            "block.wooden_door.close"
    );
    private static final ResourceLocation WORLDZERO_IRON_DOOR_OPEN_SOUND_ID = new ResourceLocation(
            "minecraft",
            "block.iron_door.open"
    );
    private static final ResourceLocation WORLDZERO_IRON_DOOR_CLOSE_SOUND_ID = new ResourceLocation(
            "minecraft",
            "block.iron_door.close"
    );

    private static final String WORLDZERO_SAVE_ID = "worldzero_koridor_dimension";
    private static final BlockPos WORLDZERO_BASE_ORIGIN = new BlockPos(-8, 64, 0);
    private static final int WORLDZERO_GENERATION_RADIUS_SEGMENTS = 2;
    private static final int WORLDZERO_ECHO_RUN_DELAY_MIN_TICKS = 35 * 20;
    private static final int WORLDZERO_ECHO_RUN_DELAY_MAX_TICKS = 40 * 20;
    private static final int WORLDZERO_ECHO_RUN_MIN_FORWARD_BLOCKS = 12;
    private static final int WORLDZERO_ECHO_RUN_MAX_FORWARD_BLOCKS = 28;
    private static final int WORLDZERO_ECHO_RUN_DOOR_SCAN_HALF_WIDTH = 12;
    private static final double WORLDZERO_ECHO_RUN_LOOK_DOT_THRESHOLD = 0.88D;
    private static final double WORLDZERO_ECHO_RUN_SPEED_BLOCKS_PER_TICK = 0.46D;
    private static final double WORLDZERO_ECHO_RUN_DOOR_OPEN_DISTANCE_SQR = 1.75D * 1.75D;
    private static final double WORLDZERO_ECHO_RUN_DOOR_CLOSE_DISTANCE_SQR = 1.05D * 1.05D;
    private static final Map<MinecraftServer, SessionState> WORLDZERO_SERVER_STATES = new WeakHashMap<>();

    private WorldZeroKoridorDimension() {
    }

    @SubscribeEvent
    public static void worldzero$onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.player instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }

        ServerLevel level = player.serverLevel();
        if (level.dimension() != WORLDZERO_KORIDOR_LEVEL) {
            return;
        }

        TemplateInfo templateInfo = worldzero$getTemplateInfo(level);
        if (templateInfo == null) {
            return;
        }

        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }

        SessionState sessionState = WORLDZERO_SERVER_STATES.computeIfAbsent(server, ignored -> new SessionState());
        long segmentIndex = worldzero$getSegmentIndex(player.blockPosition().getZ(), templateInfo.worldzero$segmentLength);
        Long previousSegmentIndex = sessionState.worldzero$lastEnsuredSegmentByPlayer.put(player.getUUID(), segmentIndex);
        if (previousSegmentIndex == null || previousSegmentIndex != segmentIndex) {
            worldzero$ensureSegmentsAround(level, segmentIndex, templateInfo);
        }

        worldzero$initializeEchoRunSchedule(level);
        worldzero$tryStartEchoRun(level, player, sessionState);
    }

    @SubscribeEvent
    public static void worldzero$onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide()) {
            return;
        }

        if (!(event.level instanceof ServerLevel level) || level.dimension() != WORLDZERO_KORIDOR_LEVEL) {
            return;
        }

        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }

        SessionState sessionState = WORLDZERO_SERVER_STATES.get(server);
        if (sessionState == null || sessionState.worldzero$activeEchoRun == null) {
            return;
        }

        worldzero$tickActiveEchoRun(level, sessionState);
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_SERVER_STATES.remove(event.getServer());
    }

    @SubscribeEvent
    public static void worldzero$onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (worldzero$isBuildRestricted(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (worldzero$isBuildRestricted(event.getEntity())) {
            event.setNewSpeed(0.0F);
        }
    }

    @SubscribeEvent
    public static void worldzero$onBlockBreak(BlockEvent.BreakEvent event) {
        if (worldzero$isBuildRestricted(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && worldzero$isBuildRestricted(player)) {
            event.setCanceled(true);
        }
    }

    public static boolean worldzero$teleportPlayerToKoridor(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        ServerLevel level = server.getLevel(WORLDZERO_KORIDOR_LEVEL);
        if (level == null) {
            return false;
        }

        TemplateInfo templateInfo = worldzero$getTemplateInfo(level);
        if (templateInfo == null) {
            return false;
        }

        KoridorSaveData saveData = worldzero$getSaveData(level);
        saveData.worldzero$returnPoints.put(
                player.getUUID(),
                new ReturnPoint(
                        player.serverLevel().dimension(),
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        player.getYRot(),
                        player.getXRot()
                )
        );
        saveData.setDirty();

        if (!worldzero$ensureSegmentsAround(level, 0L, templateInfo)) {
            return false;
        }

        BlockPos spawnPos = worldzero$getSpawnBlockPos(templateInfo);
        level.getChunkAt(spawnPos);
        player.teleportTo(level, spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
        return true;
    }

    public static boolean worldzero$returnPlayerFromKoridor(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        ServerLevel koridorLevel = server.getLevel(WORLDZERO_KORIDOR_LEVEL);
        if (koridorLevel == null) {
            return false;
        }

        KoridorSaveData saveData = worldzero$getSaveData(koridorLevel);
        ReturnPoint returnPoint = saveData.worldzero$returnPoints.remove(player.getUUID());
        saveData.setDirty();

        if (returnPoint == null && player.serverLevel().dimension() != WORLDZERO_KORIDOR_LEVEL) {
            return false;
        }

        ServerLevel targetLevel = null;
        double targetX;
        double targetY;
        double targetZ;
        float targetYaw;
        float targetPitch;
        if (returnPoint != null) {
            targetLevel = server.getLevel(returnPoint.worldzero$dimension);
        }

        if (targetLevel != null) {
            targetX = returnPoint.worldzero$x;
            targetY = returnPoint.worldzero$y;
            targetZ = returnPoint.worldzero$z;
            targetYaw = returnPoint.worldzero$yaw;
            targetPitch = returnPoint.worldzero$pitch;
        } else {
            targetLevel = server.overworld();
            if (targetLevel == null) {
                return false;
            }

            BlockPos fallbackPos = player.getRespawnPosition();
            ResourceKey<Level> respawnDimension = player.getRespawnDimension();
            ServerLevel respawnLevel = respawnDimension != null ? server.getLevel(respawnDimension) : null;
            if (fallbackPos != null && respawnLevel != null) {
                targetLevel = respawnLevel;
                targetX = fallbackPos.getX() + 0.5D;
                targetY = fallbackPos.getY();
                targetZ = fallbackPos.getZ() + 0.5D;
                targetYaw = player.getRespawnAngle();
                targetPitch = 0.0F;
            } else {
                BlockPos sharedSpawn = targetLevel.getSharedSpawnPos();
                targetX = sharedSpawn.getX() + 0.5D;
                targetY = sharedSpawn.getY();
                targetZ = sharedSpawn.getZ() + 0.5D;
                targetYaw = targetLevel.getSharedSpawnAngle();
                targetPitch = 0.0F;
            }
        }

        player.teleportTo(targetLevel, targetX, targetY, targetZ, targetYaw, targetPitch);
        return true;
    }

    public static boolean worldzero$triggerEchoRunNow(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        if (level.dimension() != WORLDZERO_KORIDOR_LEVEL) {
            return false;
        }

        MinecraftServer server = level.getServer();
        if (server == null) {
            return false;
        }

        TemplateInfo templateInfo = worldzero$getTemplateInfo(level);
        if (templateInfo == null) {
            return false;
        }

        SessionState sessionState = WORLDZERO_SERVER_STATES.computeIfAbsent(server, ignored -> new SessionState());
        long segmentIndex = worldzero$getSegmentIndex(player.blockPosition().getZ(), templateInfo.worldzero$segmentLength);
        if (!worldzero$ensureSegmentsAround(level, segmentIndex, templateInfo)) {
            return false;
        }

        if (sessionState.worldzero$activeEchoRun != null) {
            return false;
        }

        EchoRunCandidate candidate = worldzero$findEchoRunCandidate(level, player);
        return candidate != null && worldzero$startEchoRun(level, sessionState, candidate, false);
    }

    private static void worldzero$initializeEchoRunSchedule(ServerLevel level) {
        KoridorSaveData saveData = worldzero$getSaveData(level);
        if (saveData.worldzero$echoRunCompleted || saveData.worldzero$echoRunTriggerTick >= 0L) {
            return;
        }

        saveData.worldzero$echoRunTriggerTick = level.getGameTime()
                + (long) Mth.nextInt(level.random, WORLDZERO_ECHO_RUN_DELAY_MIN_TICKS, WORLDZERO_ECHO_RUN_DELAY_MAX_TICKS);
        saveData.setDirty();
    }

    private static void worldzero$tryStartEchoRun(ServerLevel level, ServerPlayer player, SessionState sessionState) {
        if (!player.isAlive() || player.isSpectator() || sessionState.worldzero$activeEchoRun != null) {
            return;
        }

        KoridorSaveData saveData = worldzero$getSaveData(level);
        if (saveData.worldzero$echoRunCompleted
                || saveData.worldzero$echoRunTriggerTick < 0L
                || level.getGameTime() < saveData.worldzero$echoRunTriggerTick) {
            return;
        }

        if (!worldzero$isEchoRunPlayerReady(player)) {
            return;
        }

        EchoRunCandidate candidate = worldzero$findEchoRunCandidate(level, player);
        if (candidate == null) {
            return;
        }

        worldzero$startEchoRun(level, sessionState, candidate, true);
    }

    private static void worldzero$tickActiveEchoRun(ServerLevel level, SessionState sessionState) {
        ActiveEchoRun activeRun = sessionState.worldzero$activeEchoRun;
        if (activeRun == null) {
            return;
        }

        Entity entity = level.getEntity(activeRun.worldzero$echoId);
        if (!(entity instanceof WorldZeroEchoEntity echo) || entity.isRemoved()) {
            worldzero$finishActiveEchoRun(level, sessionState, activeRun);
            return;
        }

        Vec3 echoPos = echo.position();
        if (!activeRun.worldzero$startDoorClosed
                && echoPos.distanceToSqr(activeRun.worldzero$startDoorCloseTriggerPos) <= WORLDZERO_ECHO_RUN_DOOR_CLOSE_DISTANCE_SQR
                && worldzero$setDoorOpenSilent(level, activeRun.worldzero$startDoorPos, false)) {
            activeRun.worldzero$startDoorClosed = true;
            worldzero$playVanillaDoorSoundToKoridorPlayers(level, activeRun.worldzero$startDoorPos, false);
        }

        if (!activeRun.worldzero$secondDoorOpened
                && echoPos.distanceToSqr(activeRun.worldzero$endDoorOpenTriggerPos) <= WORLDZERO_ECHO_RUN_DOOR_OPEN_DISTANCE_SQR
                && worldzero$setDoorOpenSilent(level, activeRun.worldzero$endDoorPos, true)) {
            activeRun.worldzero$secondDoorOpened = true;
            worldzero$playVanillaDoorSoundToKoridorPlayers(level, activeRun.worldzero$endDoorPos, true);
        }

        if (activeRun.worldzero$secondDoorOpened
                && !activeRun.worldzero$endDoorClosed
                && echoPos.distanceToSqr(activeRun.worldzero$endDoorCloseTriggerPos) <= WORLDZERO_ECHO_RUN_DOOR_CLOSE_DISTANCE_SQR
                && worldzero$setDoorOpenSilent(level, activeRun.worldzero$endDoorPos, false)) {
            activeRun.worldzero$endDoorClosed = true;
            worldzero$playVanillaDoorSoundToKoridorPlayers(level, activeRun.worldzero$endDoorPos, false);
        }
    }

    @Nullable
    private static EchoRunCandidate worldzero$findEchoRunCandidate(ServerLevel level, ServerPlayer player) {
        int stepZ = player.getLookAngle().z >= 0.0D ? 1 : -1;
        BlockPos playerPos = player.blockPosition();
        for (int forward = WORLDZERO_ECHO_RUN_MAX_FORWARD_BLOCKS; forward >= WORLDZERO_ECHO_RUN_MIN_FORWARD_BLOCKS; forward--) {
            int targetZ = playerPos.getZ() + stepZ * forward;
            DoorPair doorPair = worldzero$findClosedDoorPairAtZ(level, playerPos, targetZ);
            if (doorPair == null) {
                continue;
            }

            Vec3 center = new Vec3(
                    (doorPair.worldzero$leftDoorPos.getX() + doorPair.worldzero$rightDoorPos.getX() + 1.0D) * 0.5D,
                    doorPair.worldzero$leftDoorPos.getY() + 1.0D,
                    targetZ + 0.5D
            );
            if (!worldzero$isEchoRunCenterVisible(level, player, center)) {
                continue;
            }

            EchoRunCandidate candidate = worldzero$buildEchoRunCandidate(level, doorPair);
            if (candidate != null) {
                return candidate;
            }
        }

        return null;
    }

    @Nullable
    private static DoorPair worldzero$findClosedDoorPairAtZ(ServerLevel level, BlockPos playerPos, int targetZ) {
        for (int y = playerPos.getY() - 1; y <= playerPos.getY() + 1; y++) {
            BlockPos leftDoorPos = null;
            BlockPos rightDoorPos = null;
            for (int x = playerPos.getX() - WORLDZERO_ECHO_RUN_DOOR_SCAN_HALF_WIDTH;
                 x <= playerPos.getX() + WORLDZERO_ECHO_RUN_DOOR_SCAN_HALF_WIDTH;
                 x++) {
                BlockPos candidatePos = new BlockPos(x, y, targetZ);
                if (!worldzero$isClosedLowerDoor(level.getBlockState(candidatePos))) {
                    continue;
                }

                if (leftDoorPos == null) {
                    leftDoorPos = candidatePos.immutable();
                }
                rightDoorPos = candidatePos.immutable();
            }

            if (leftDoorPos != null
                    && rightDoorPos != null
                    && rightDoorPos.getX() - leftDoorPos.getX() >= 4
                    && worldzero$isDoorPairPassageValid(level, leftDoorPos, rightDoorPos)) {
                return new DoorPair(leftDoorPos, rightDoorPos);
            }
        }

        return null;
    }

    @Nullable
    private static EchoRunCandidate worldzero$buildEchoRunCandidate(ServerLevel level, DoorPair doorPair) {
        double centerX = (doorPair.worldzero$leftDoorPos.getX() + doorPair.worldzero$rightDoorPos.getX() + 1.0D) * 0.5D;
        boolean startFromLeft = level.random.nextBoolean();
        BlockPos startDoorPos = startFromLeft ? doorPair.worldzero$leftDoorPos : doorPair.worldzero$rightDoorPos;
        BlockPos endDoorPos = startFromLeft ? doorPair.worldzero$rightDoorPos : doorPair.worldzero$leftDoorPos;

        Vec3 startPos = worldzero$findRoomInteriorPosition(level, startDoorPos, startDoorPos.getX() < centerX ? -1 : 1);
        Vec3 endPos = worldzero$findRoomInteriorPosition(level, endDoorPos, endDoorPos.getX() < centerX ? -1 : 1);
        if (startPos == null || endPos == null) {
            return null;
        }

        double directionX = endPos.x - startPos.x;
        double directionZ = endPos.z - startPos.z;
        double distance = Math.sqrt(directionX * directionX + directionZ * directionZ);
        if (distance < 2.0D) {
            return null;
        }

        Vec3 normalizedDirection = new Vec3(directionX / distance, 0.0D, directionZ / distance);
        Vec3 startDoorCenter = new Vec3(startDoorPos.getX() + 0.5D, startDoorPos.getY(), startDoorPos.getZ() + 0.5D);
        Vec3 endDoorCenter = new Vec3(endDoorPos.getX() + 0.5D, endDoorPos.getY(), endDoorPos.getZ() + 0.5D);
        int durationTicks = Mth.clamp(Mth.ceil(distance / WORLDZERO_ECHO_RUN_SPEED_BLOCKS_PER_TICK), 12, 30);
        double speed = distance / (double) durationTicks;
        float yaw = (float) (Mth.atan2(directionZ, directionX) * (180.0D / Math.PI)) - 90.0F;
        return new EchoRunCandidate(
                startDoorPos,
                endDoorPos,
                startPos,
                startDoorCenter.add(normalizedDirection.scale(1.15D)),
                endDoorCenter.subtract(normalizedDirection.scale(1.00D)),
                endDoorCenter.add(normalizedDirection.scale(1.15D)),
                directionX,
                directionZ,
                speed,
                durationTicks,
                yaw
        );
    }

    @Nullable
    private static Vec3 worldzero$findRoomInteriorPosition(ServerLevel level, BlockPos doorPos, int roomDirectionX) {
        double baseX = doorPos.getX() + 0.5D;
        double baseY = doorPos.getY();
        double baseZ = doorPos.getZ() + 0.5D;
        double[] offsets = {2.85D, 2.45D, 2.05D, 1.65D, 1.25D};
        for (double offset : offsets) {
            Vec3 candidate = new Vec3(baseX + roomDirectionX * offset, baseY, baseZ);
            if (worldzero$isEchoSpawnPositionValid(level, candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private static boolean worldzero$isEchoSpawnPositionValid(ServerLevel level, Vec3 candidate) {
        AABB spawnBox = WorldZeroEntities.WORLDZERO_ECHO.get().getDimensions().makeBoundingBox(
                candidate.x,
                candidate.y,
                candidate.z
        );
        return level.noCollision(spawnBox) && !level.containsAnyLiquid(spawnBox);
    }

    private static boolean worldzero$isEchoRunCenterVisible(ServerLevel level, ServerPlayer player, Vec3 center) {
        Vec3 eyePosition = player.getEyePosition();
        Vec3 directionToCenter = center.subtract(eyePosition);
        double distanceSqr = directionToCenter.lengthSqr();
        if (distanceSqr < WORLDZERO_ECHO_RUN_MIN_FORWARD_BLOCKS * WORLDZERO_ECHO_RUN_MIN_FORWARD_BLOCKS
                || distanceSqr > WORLDZERO_ECHO_RUN_MAX_FORWARD_BLOCKS * WORLDZERO_ECHO_RUN_MAX_FORWARD_BLOCKS) {
            return false;
        }

        Vec3 lookVector = player.getViewVector(1.0F).normalize();
        if (lookVector.dot(directionToCenter.normalize()) < WORLDZERO_ECHO_RUN_LOOK_DOT_THRESHOLD) {
            return false;
        }

        HitResult hitResult = level.clip(new ClipContext(
                eyePosition,
                center,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        return hitResult.getType() == HitResult.Type.MISS;
    }

    private static boolean worldzero$isEchoRunPlayerReady(ServerPlayer player) {
        Vec3 lookVector = player.getViewVector(1.0F);
        return Math.abs(lookVector.z) >= 0.75D && Math.abs(lookVector.z) >= Math.abs(lookVector.x);
    }

    private static boolean worldzero$isClosedLowerDoor(BlockState state) {
        return state.is(BlockTags.DOORS)
                && state.hasProperty(DoorBlock.HALF)
                && state.hasProperty(DoorBlock.OPEN)
                && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && !state.getValue(DoorBlock.OPEN);
    }

    private static boolean worldzero$isDoorPairPassageValid(ServerLevel level, BlockPos leftDoorPos, BlockPos rightDoorPos) {
        if (leftDoorPos.getY() != rightDoorPos.getY() || leftDoorPos.getZ() != rightDoorPos.getZ()) {
            return false;
        }

        for (int x = leftDoorPos.getX() + 1; x <= rightDoorPos.getX() - 1; x++) {
            BlockPos lowerPos = new BlockPos(x, leftDoorPos.getY(), leftDoorPos.getZ());
            BlockPos upperPos = lowerPos.above();
            if (!level.getBlockState(lowerPos).getCollisionShape(level, lowerPos).isEmpty()
                    || !level.getBlockState(upperPos).getCollisionShape(level, upperPos).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private static boolean worldzero$setDoorOpenSilent(ServerLevel level, BlockPos pos, boolean open) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.DOORS) || !state.hasProperty(DoorBlock.HALF) || !state.hasProperty(DoorBlock.OPEN)) {
            return false;
        }

        BlockPos lowerPos = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        BlockPos upperPos = lowerPos.above();
        BlockState lowerState = level.getBlockState(lowerPos);
        BlockState upperState = level.getBlockState(upperPos);
        if (!lowerState.is(BlockTags.DOORS)
                || !upperState.is(BlockTags.DOORS)
                || !lowerState.hasProperty(DoorBlock.OPEN)
                || !upperState.hasProperty(DoorBlock.OPEN)
                || lowerState.getValue(DoorBlock.OPEN) == open) {
            return false;
        }

        level.setBlock(lowerPos, lowerState.setValue(DoorBlock.OPEN, open), 18);
        level.setBlock(upperPos, upperState.setValue(DoorBlock.OPEN, open), 18);
        return true;
    }

    private static boolean worldzero$startEchoRun(
            ServerLevel level,
            SessionState sessionState,
            EchoRunCandidate candidate,
            boolean markCompleted
    ) {
        if (!worldzero$setDoorOpenSilent(level, candidate.worldzero$startDoorPos, true)) {
            return false;
        }

        WorldZeroEchoEntity echo = WorldZeroEntities.WORLDZERO_ECHO.get().create(level);
        if (echo == null) {
            return false;
        }

        worldzero$playVanillaDoorSoundToKoridorPlayers(level, candidate.worldzero$startDoorPos, true);

        echo.moveTo(
                candidate.worldzero$startPos.x,
                candidate.worldzero$startPos.y,
                candidate.worldzero$startPos.z,
                candidate.worldzero$yaw,
                0.0F
        );
        echo.setDeltaMovement(0.0D, 0.0D, 0.0D);
        echo.worldzero$configureKoridorPass(
                candidate.worldzero$directionX,
                candidate.worldzero$directionZ,
                candidate.worldzero$speed,
                candidate.worldzero$durationTicks
        );
        level.addFreshEntity(echo);

        sessionState.worldzero$activeEchoRun = new ActiveEchoRun(
                echo.getUUID(),
                candidate.worldzero$startDoorPos,
                candidate.worldzero$startDoorCloseTriggerPos,
                candidate.worldzero$endDoorPos,
                candidate.worldzero$endDoorOpenTriggerPos,
                candidate.worldzero$endDoorCloseTriggerPos
        );

        if (markCompleted) {
            KoridorSaveData saveData = worldzero$getSaveData(level);
            saveData.worldzero$echoRunCompleted = true;
            saveData.worldzero$echoRunTriggerTick = -1L;
            saveData.setDirty();
        }

        return true;
    }

    private static void worldzero$finishActiveEchoRun(ServerLevel level, SessionState sessionState, ActiveEchoRun activeRun) {
        if (!activeRun.worldzero$startDoorClosed && worldzero$setDoorOpenSilent(level, activeRun.worldzero$startDoorPos, false)) {
            worldzero$playVanillaDoorSoundToKoridorPlayers(level, activeRun.worldzero$startDoorPos, false);
        }

        if (activeRun.worldzero$secondDoorOpened
                && !activeRun.worldzero$endDoorClosed
                && worldzero$setDoorOpenSilent(level, activeRun.worldzero$endDoorPos, false)) {
            worldzero$playVanillaDoorSoundToKoridorPlayers(level, activeRun.worldzero$endDoorPos, false);
        }

        sessionState.worldzero$activeEchoRun = null;
    }

    private static void worldzero$playVanillaDoorSoundToKoridorPlayers(ServerLevel level, BlockPos doorPos, boolean open) {
        ResourceLocation soundId = worldzero$getVanillaDoorSoundId(level.getBlockState(doorPos), open);
        if (soundId == null) {
            return;
        }

        double soundX = doorPos.getX() + 0.5D;
        double soundY = doorPos.getY() + 0.5D;
        double soundZ = doorPos.getZ() + 0.5D;
        for (ServerPlayer player : level.players()) {
            WorldZeroNetwork.sendKoridorDoorSound(player, soundId, soundX, soundY, soundZ);
        }
    }

    @Nullable
    private static ResourceLocation worldzero$getVanillaDoorSoundId(BlockState state, boolean open) {
        if (!state.is(BlockTags.DOORS)) {
            return null;
        }

        boolean ironDoor = state.is(Blocks.IRON_DOOR);
        if (ironDoor) {
            return open ? WORLDZERO_IRON_DOOR_OPEN_SOUND_ID : WORLDZERO_IRON_DOOR_CLOSE_SOUND_ID;
        }

        return open ? WORLDZERO_WOODEN_DOOR_OPEN_SOUND_ID : WORLDZERO_WOODEN_DOOR_CLOSE_SOUND_ID;
    }

    private static boolean worldzero$ensureSegmentsAround(
            ServerLevel level,
            long centerSegmentIndex,
            TemplateInfo templateInfo
    ) {
        KoridorSaveData saveData = worldzero$getSaveData(level);
        for (long segmentIndex = centerSegmentIndex - WORLDZERO_GENERATION_RADIUS_SEGMENTS;
             segmentIndex <= centerSegmentIndex + WORLDZERO_GENERATION_RADIUS_SEGMENTS;
             segmentIndex++) {
            if (saveData.worldzero$generatedSegments.contains(segmentIndex)) {
                continue;
            }

            if (!worldzero$placeSegment(level, templateInfo, segmentIndex)) {
                return false;
            }

            saveData.worldzero$generatedSegments.add(segmentIndex);
            saveData.setDirty();
        }

        return true;
    }

    private static boolean worldzero$placeSegment(ServerLevel level, TemplateInfo templateInfo, long segmentIndex) {
        BlockPos origin = worldzero$getSegmentOrigin(segmentIndex, templateInfo.worldzero$size.getZ());
        int minChunkX = origin.getX() >> 4;
        int maxChunkX = (origin.getX() + templateInfo.worldzero$size.getX() - 1) >> 4;
        int minChunkZ = origin.getZ() >> 4;
        int maxChunkZ = (origin.getZ() + templateInfo.worldzero$size.getZ() - 1) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }

        return templateInfo.worldzero$template.placeInWorld(
                level,
                origin,
                origin,
                new StructurePlaceSettings().setIgnoreEntities(true),
                level.random,
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
        );
    }

    private static long worldzero$getSegmentIndex(int worldZ, int segmentLength) {
        return Math.floorDiv(worldZ - WORLDZERO_BASE_ORIGIN.getZ(), segmentLength);
    }

    private static BlockPos worldzero$getSegmentOrigin(long segmentIndex, int segmentLength) {
        return WORLDZERO_BASE_ORIGIN.offset(0, 0, Math.toIntExact(segmentIndex * (long) segmentLength));
    }

    private static BlockPos worldzero$getSpawnBlockPos(TemplateInfo templateInfo) {
        return new BlockPos(
                WORLDZERO_BASE_ORIGIN.getX() + Math.max(0, (templateInfo.worldzero$size.getX() - 1) / 2),
                WORLDZERO_BASE_ORIGIN.getY() + 1,
                WORLDZERO_BASE_ORIGIN.getZ() + Math.max(0, (templateInfo.worldzero$size.getZ() - 1) / 2)
        );
    }

    @Nullable
    private static TemplateInfo worldzero$getTemplateInfo(ServerLevel level) {
        Optional<StructureTemplate> optionalTemplate = level.getStructureManager().get(WORLDZERO_KORIDOR_STRUCTURE_ID);
        if (optionalTemplate.isEmpty()) {
            return null;
        }

        StructureTemplate template = optionalTemplate.get();
        Vec3i size = template.getSize();
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
            return null;
        }

        return new TemplateInfo(template, size);
    }

    private static KoridorSaveData worldzero$getSaveData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(KoridorSaveData::load, KoridorSaveData::new, WORLDZERO_SAVE_ID);
    }

    private static boolean worldzero$isBuildRestricted(Player player) {
        return player != null
                && player.level().dimension() == WORLDZERO_KORIDOR_LEVEL;
    }

    private static final class SessionState {
        private final Map<UUID, Long> worldzero$lastEnsuredSegmentByPlayer = new HashMap<>();
        private ActiveEchoRun worldzero$activeEchoRun;
    }

    private static final class ActiveEchoRun {
        private final UUID worldzero$echoId;
        private final BlockPos worldzero$startDoorPos;
        private final Vec3 worldzero$startDoorCloseTriggerPos;
        private final BlockPos worldzero$endDoorPos;
        private final Vec3 worldzero$endDoorOpenTriggerPos;
        private final Vec3 worldzero$endDoorCloseTriggerPos;
        private boolean worldzero$startDoorClosed;
        private boolean worldzero$secondDoorOpened;
        private boolean worldzero$endDoorClosed;

        private ActiveEchoRun(
                UUID echoId,
                BlockPos startDoorPos,
                Vec3 startDoorCloseTriggerPos,
                BlockPos endDoorPos,
                Vec3 endDoorOpenTriggerPos,
                Vec3 endDoorCloseTriggerPos
        ) {
            this.worldzero$echoId = echoId;
            this.worldzero$startDoorPos = startDoorPos;
            this.worldzero$startDoorCloseTriggerPos = startDoorCloseTriggerPos;
            this.worldzero$endDoorPos = endDoorPos;
            this.worldzero$endDoorOpenTriggerPos = endDoorOpenTriggerPos;
            this.worldzero$endDoorCloseTriggerPos = endDoorCloseTriggerPos;
        }
    }

    private static final class EchoRunCandidate {
        private final BlockPos worldzero$startDoorPos;
        private final BlockPos worldzero$endDoorPos;
        private final Vec3 worldzero$startPos;
        private final Vec3 worldzero$startDoorCloseTriggerPos;
        private final Vec3 worldzero$endDoorOpenTriggerPos;
        private final Vec3 worldzero$endDoorCloseTriggerPos;
        private final double worldzero$directionX;
        private final double worldzero$directionZ;
        private final double worldzero$speed;
        private final int worldzero$durationTicks;
        private final float worldzero$yaw;

        private EchoRunCandidate(
                BlockPos startDoorPos,
                BlockPos endDoorPos,
                Vec3 startPos,
                Vec3 startDoorCloseTriggerPos,
                Vec3 endDoorOpenTriggerPos,
                Vec3 endDoorCloseTriggerPos,
                double directionX,
                double directionZ,
                double speed,
                int durationTicks,
                float yaw
        ) {
            this.worldzero$startDoorPos = startDoorPos;
            this.worldzero$endDoorPos = endDoorPos;
            this.worldzero$startPos = startPos;
            this.worldzero$startDoorCloseTriggerPos = startDoorCloseTriggerPos;
            this.worldzero$endDoorOpenTriggerPos = endDoorOpenTriggerPos;
            this.worldzero$endDoorCloseTriggerPos = endDoorCloseTriggerPos;
            this.worldzero$directionX = directionX;
            this.worldzero$directionZ = directionZ;
            this.worldzero$speed = speed;
            this.worldzero$durationTicks = durationTicks;
            this.worldzero$yaw = yaw;
        }
    }

    private static final class DoorPair {
        private final BlockPos worldzero$leftDoorPos;
        private final BlockPos worldzero$rightDoorPos;

        private DoorPair(BlockPos leftDoorPos, BlockPos rightDoorPos) {
            this.worldzero$leftDoorPos = leftDoorPos;
            this.worldzero$rightDoorPos = rightDoorPos;
        }
    }

    private static final class TemplateInfo {
        private final StructureTemplate worldzero$template;
        private final Vec3i worldzero$size;
        private final int worldzero$segmentLength;

        private TemplateInfo(StructureTemplate template, Vec3i size) {
            this.worldzero$template = template;
            this.worldzero$size = size;
            this.worldzero$segmentLength = size.getZ();
        }
    }

    private static final class KoridorSaveData extends SavedData {
        private final LongOpenHashSet worldzero$generatedSegments = new LongOpenHashSet();
        private final Map<UUID, ReturnPoint> worldzero$returnPoints = new HashMap<>();
        private long worldzero$echoRunTriggerTick = -1L;
        private boolean worldzero$echoRunCompleted;

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putLongArray("GeneratedSegments", this.worldzero$generatedSegments.toLongArray());

            CompoundTag returnPointsTag = new CompoundTag();
            for (Map.Entry<UUID, ReturnPoint> entry : this.worldzero$returnPoints.entrySet()) {
                returnPointsTag.put(entry.getKey().toString(), entry.getValue().worldzero$save());
            }
            tag.put("ReturnPoints", returnPointsTag);
            tag.putLong("EchoRunTriggerTick", this.worldzero$echoRunTriggerTick);
            tag.putBoolean("EchoRunCompleted", this.worldzero$echoRunCompleted);
            return tag;
        }

        private static KoridorSaveData load(CompoundTag tag) {
            KoridorSaveData saveData = new KoridorSaveData();
            for (long segmentIndex : tag.getLongArray("GeneratedSegments")) {
                saveData.worldzero$generatedSegments.add(segmentIndex);
            }

            CompoundTag returnPointsTag = tag.getCompound("ReturnPoints");
            for (String key : returnPointsTag.getAllKeys()) {
                try {
                    UUID playerId = UUID.fromString(key);
                    ReturnPoint returnPoint = ReturnPoint.worldzero$load(returnPointsTag.getCompound(key));
                    if (returnPoint != null) {
                        saveData.worldzero$returnPoints.put(playerId, returnPoint);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }

            saveData.worldzero$echoRunTriggerTick = tag.getLong("EchoRunTriggerTick");
            saveData.worldzero$echoRunCompleted = tag.getBoolean("EchoRunCompleted");
            return saveData;
        }
    }

    private static final class ReturnPoint {
        private final ResourceKey<Level> worldzero$dimension;
        private final double worldzero$x;
        private final double worldzero$y;
        private final double worldzero$z;
        private final float worldzero$yaw;
        private final float worldzero$pitch;

        private ReturnPoint(
                ResourceKey<Level> dimension,
                double x,
                double y,
                double z,
                float yaw,
                float pitch
        ) {
            this.worldzero$dimension = dimension;
            this.worldzero$x = x;
            this.worldzero$y = y;
            this.worldzero$z = z;
            this.worldzero$yaw = yaw;
            this.worldzero$pitch = pitch;
        }

        private CompoundTag worldzero$save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dimension", this.worldzero$dimension.location().toString());
            tag.putDouble("X", this.worldzero$x);
            tag.putDouble("Y", this.worldzero$y);
            tag.putDouble("Z", this.worldzero$z);
            tag.putFloat("Yaw", this.worldzero$yaw);
            tag.putFloat("Pitch", this.worldzero$pitch);
            return tag;
        }

        @Nullable
        private static ReturnPoint worldzero$load(CompoundTag tag) {
            ResourceLocation location = ResourceLocation.tryParse(tag.getString("Dimension"));
            if (location == null) {
                return null;
            }

            return new ReturnPoint(
                    ResourceKey.create(Registries.DIMENSION, location),
                    tag.getDouble("X"),
                    tag.getDouble("Y"),
                    tag.getDouble("Z"),
                    tag.getFloat("Yaw"),
                    tag.getFloat("Pitch")
            );
        }
    }
}
