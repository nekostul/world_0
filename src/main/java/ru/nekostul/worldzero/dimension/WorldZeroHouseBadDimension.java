package ru.nekostul.worldzero;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroHouseBadDimension {
    public static final ResourceKey<Level> WORLDZERO_HOUSE_BAD_LEVEL = ResourceKey.create(
            Registries.DIMENSION,
            new ResourceLocation(WorldZeroMod.MOD_ID, "house_bad")
    );

    private static final ResourceLocation WORLDZERO_HOUSE_BAD_STRUCTURE_ID = new ResourceLocation(
            WorldZeroMod.MOD_ID,
            "house_bad"
    );
    private static final String WORLDZERO_SAVE_ID = "worldzero_house_bad_dimension";
    private static final BlockPos WORLDZERO_BASE_ORIGIN = new BlockPos(-32, 64, -32);
    private static final int WORLDZERO_SCAN_RADIUS_HORIZONTAL = 16;
    private static final int WORLDZERO_BARRIER_PADDING = 1;
    private static final int WORLDZERO_BARRIER_HEIGHT_PADDING = 8;
    private static final int WORLDZERO_DROP_SAFETY_WALL_HEIGHT = 6;
    private static final int WORLDZERO_DOOR_BLACK_ECHO_LIFETIME_TICKS = 3;
    private static final int WORLDZERO_DOOR_BLACK_ECHO_SCAN_DISTANCE = 8;
    private static final int WORLDZERO_DOOR_BLACK_ECHO_Y_SCAN_DOWN = 8;
    private static final int WORLDZERO_DOOR_BLACK_ECHO_Y_SCAN_UP = 2;
    private static final double WORLDZERO_DOOR_BLACK_ECHO_VIEW_DOT_THRESHOLD = 0.8D;
    private static final Component WORLDZERO_BED_OCCUPIED_MESSAGE = Component.translatable("block.minecraft.bed.occupied");
    private static final int WORLDZERO_NAVIGATION_TRIGGER_DELAY_TICKS = 20 * 4;
    private static final int WORLDZERO_DELAYED_DOOR_MIN_TICKS = 30;
    private static final int WORLDZERO_DELAYED_DOOR_MAX_TICKS = 65;
    private static final int WORLDZERO_DELAYED_CHEST_MIN_TICKS = 24;
    private static final int WORLDZERO_DELAYED_CHEST_MAX_TICKS = 52;
    private static final int WORLDZERO_OUTSIDE_PASS_REQUIRED_TICKS = 45;
    private static final double WORLDZERO_OUTSIDE_PASS_MAX_WINDOW_DISTANCE_SQR = 16.0D * 16.0D;
    private static final double WORLDZERO_OUTSIDE_PASS_SPEED_BLOCKS_PER_TICK = 0.12D;
    private static final double WORLDZERO_OUTSIDE_PASS_STEP_DISTANCE = 1.15D;
    private static final double WORLDZERO_RETURN_LOCK_TRIGGER_X = -15.443D;
    private static final int WORLDZERO_RETURN_LOCK_EVENT_DELAY_TICKS = 60;
    private static final int WORLDZERO_RETURN_LOCK_CHEST_CLOSE_DELAY_TICKS = 60;
    private static final double WORLDZERO_RETURN_LOCK_BARRIER_START_Y = 65.0D;
    private static final double WORLDZERO_RETURN_LOCK_BARRIER_END_Y = 67.0D;
    private static final double WORLDZERO_RETURN_LOCK_BARRIER_START_Z = -31.695D;
    private static final double WORLDZERO_RETURN_LOCK_BARRIER_END_Z = 15.700D;
    private static final int WORLDZERO_RETURN_LOCK_BARRIER_MIN_X = -16;
    private static final int WORLDZERO_RETURN_LOCK_BARRIER_MAX_X = -15;
    private static final int WORLDZERO_SCRIPTED_BLOCK_SEARCH_HORIZONTAL = 4;
    private static final int WORLDZERO_SCRIPTED_BLOCK_SEARCH_VERTICAL = 3;
    private static final double WORLDZERO_SCRIPTED_BLACK_ECHO_SPEED_BLOCKS_PER_TICK = 0.10D;
    private static final double WORLDZERO_FINAL_AMBUSH_DISTANCE_BLOCKS = 2.0D;
    private static final int WORLDZERO_FINAL_AMBUSH_FREEZE_TICKS = 15;
    private static final Vec3 WORLDZERO_SCRIPTED_POINT_1 = new Vec3(-4.413D, 67.0D, -6.436D);
    private static final Vec3 WORLDZERO_SCRIPTED_POINT_2 = new Vec3(-7.640D, 68.0D, -6.456D);
    private static final Vec3 WORLDZERO_SCRIPTED_POINT_3 = new Vec3(-11.806D, 68.0D, -2.390D);
    private static final Vec3 WORLDZERO_SCRIPTED_POINT_4 = new Vec3(-12.252D, 68.0D, -10.488D);
    private static final Map<MinecraftServer, SessionState> WORLDZERO_SERVER_STATES = new WeakHashMap<>();

    private WorldZeroHouseBadDimension() {
    }

    @SubscribeEvent
    public static void worldzero$onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != WORLDZERO_HOUSE_BAD_LEVEL
                || !(event.getEntity() instanceof ServerPlayer player)
                || player.isSpectator()
                || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        BlockState clickedState = level.getBlockState(event.getPos());
        if (clickedState.is(BlockTags.BEDS)) {
            event.setCanceled(true);
            player.displayClientMessage(WORLDZERO_BED_OCCUPIED_MESSAGE, true);
            return;
        }

        SessionState sessionState = WORLDZERO_SERVER_STATES.computeIfAbsent(level.getServer(), ignored -> new SessionState());
        worldzero$tryArmFinalUpperChestAmbush(level, sessionState, player, event.getPos());

        HouseBadSaveData saveData = worldzero$getSaveData(level);
        BlockPos doorPos = worldzero$resolveDoorLowerPos(level, event.getPos());
        if (doorPos == null) {
            return;
        }

        BlockState doorState = level.getBlockState(doorPos);
        if (!doorState.hasProperty(DoorBlock.OPEN)) {
            return;
        }

        if (!doorState.getValue(DoorBlock.OPEN)) {
            VisitState visitState = sessionState.worldzero$visits.computeIfAbsent(
                    player.getUUID(),
                    ignored -> new VisitState(level.getGameTime(), player.blockPosition())
            );
            if (!saveData.worldzero$doorEchoConsumed.contains(player.getUUID())) {
                worldzero$trySpawnDoorBlackEcho(level, player, doorPos);
            }
            if (!visitState.worldzero$doorCloseTriggered) {
                worldzero$scheduleDoorClose(level, player, doorPos);
                visitState.worldzero$doorCloseTriggered = true;
            }
        }
    }

    @SubscribeEvent
    public static void worldzero$onPlayerContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.serverLevel().dimension() != WORLDZERO_HOUSE_BAD_LEVEL
                || player.isSpectator()) {
            return;
        }

        SessionState sessionState = WORLDZERO_SERVER_STATES.get(player.getServer());
        if (sessionState == null) {
            return;
        }

        worldzero$trySpawnFinalUpperChestAmbush(player.serverLevel(), sessionState, player);
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

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_SERVER_STATES.remove(event.getServer());
    }

    @SubscribeEvent
    public static void worldzero$onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)
                || player.serverLevel().dimension() != WORLDZERO_HOUSE_BAD_LEVEL
                || !player.isAlive()
                || player.isSpectator()) {
            return;
        }

        worldzero$applyHouseBadVitals(player);
        player.setSprinting(false);
    }

    @SubscribeEvent
    public static void worldzero$onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.level instanceof ServerLevel level)
                || level.dimension() != WORLDZERO_HOUSE_BAD_LEVEL) {
            return;
        }

        SessionState sessionState = WORLDZERO_SERVER_STATES.computeIfAbsent(level.getServer(), ignored -> new SessionState());
        StructureRuntime structureRuntime = worldzero$getOrCreateStructureRuntime(level, sessionState);
        if (structureRuntime == null) {
            return;
        }

        worldzero$syncVisitStates(level, sessionState);
        long gameTime = level.getGameTime();
        worldzero$tickDoorAppearances(level, sessionState, gameTime);
        worldzero$tickDelayedActions(level, sessionState, gameTime);
        worldzero$tickScriptedReturnLockEvent(level, sessionState, gameTime);
        worldzero$tickVisitStates(level, sessionState, structureRuntime, gameTime);
    }

    public static boolean worldzero$teleportPlayerToHouseBad(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        ServerLevel houseBadLevel = server.getLevel(WORLDZERO_HOUSE_BAD_LEVEL);
        if (houseBadLevel == null) {
            return false;
        }

        HouseBadSaveData saveData = worldzero$getSaveData(houseBadLevel);
        if (saveData.worldzero$returnPoints.containsKey(player.getUUID())
                || saveData.worldzero$inventorySnapshots.containsKey(player.getUUID())
                || saveData.worldzero$vitalSnapshots.containsKey(player.getUUID())) {
            return false;
        }

        TemplateInfo templateInfo = worldzero$getTemplateInfo(houseBadLevel);
        if (templateInfo == null || !worldzero$prepareHouseBadLevel(houseBadLevel, templateInfo)) {
            return false;
        }

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
        saveData.worldzero$inventorySnapshots.put(
                player.getUUID(),
                PlayerInventorySnapshot.worldzero$fromPlayer(player)
        );
        saveData.worldzero$vitalSnapshots.put(
                player.getUUID(),
                PlayerVitalsSnapshot.worldzero$fromPlayer(player)
        );
        saveData.worldzero$doorEchoConsumed.remove(player.getUUID());
        saveData.setDirty();

        if (player.isSleeping()) {
            player.stopSleepInBed(false, true);
        }
        worldzero$clearPlayerInventory(player);
        BlockPos bedPos = worldzero$findPrimaryBedPos(houseBadLevel, templateInfo);
        BlockPos spawnPos = worldzero$findSpawnPosNearBed(houseBadLevel, bedPos, templateInfo);
        float spawnYaw = worldzero$getSpawnYaw(houseBadLevel, bedPos);
        SessionState sessionState = WORLDZERO_SERVER_STATES.computeIfAbsent(server, ignored -> new SessionState());
        worldzero$clearPlayerSessionState(server, sessionState, player.getUUID());
        sessionState.worldzero$visits.put(player.getUUID(), new VisitState(houseBadLevel.getGameTime(), spawnPos));
        houseBadLevel.getChunkAt(spawnPos);
        player.teleportTo(houseBadLevel, spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, spawnYaw, 0.0F);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        worldzero$applyHouseBadVitals(player);
        return true;
    }

    public static boolean worldzero$returnPlayerFromHouseBad(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        ServerLevel houseBadLevel = server.getLevel(WORLDZERO_HOUSE_BAD_LEVEL);
        if (houseBadLevel == null) {
            return false;
        }

        HouseBadSaveData saveData = worldzero$getSaveData(houseBadLevel);
        ReturnPoint returnPoint = saveData.worldzero$returnPoints.remove(player.getUUID());
        PlayerInventorySnapshot inventorySnapshot = saveData.worldzero$inventorySnapshots.remove(player.getUUID());
        PlayerVitalsSnapshot vitalSnapshot = saveData.worldzero$vitalSnapshots.remove(player.getUUID());
        saveData.worldzero$doorEchoConsumed.remove(player.getUUID());
        saveData.setDirty();
        SessionState sessionState = WORLDZERO_SERVER_STATES.get(server);
        if (sessionState != null) {
            worldzero$clearPlayerSessionState(server, sessionState, player.getUUID());
        }

        if (returnPoint == null
                && inventorySnapshot == null
                && vitalSnapshot == null
                && player.serverLevel().dimension() != WORLDZERO_HOUSE_BAD_LEVEL) {
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

            BlockPos respawnPos = player.getRespawnPosition();
            ServerLevel respawnLevel = server.getLevel(player.getRespawnDimension());
            if (respawnPos != null && respawnLevel != null) {
                targetLevel = respawnLevel;
                targetX = respawnPos.getX() + 0.5D;
                targetY = respawnPos.getY();
                targetZ = respawnPos.getZ() + 0.5D;
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
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        if (inventorySnapshot != null) {
            worldzero$restorePlayerInventory(player, inventorySnapshot);
        }
        if (vitalSnapshot != null) {
            vitalSnapshot.worldzero$apply(player);
        }
        return true;
    }

    private static boolean worldzero$prepareHouseBadLevel(ServerLevel level, TemplateInfo templateInfo) {
        BlockPos origin = WORLDZERO_BASE_ORIGIN;
        int minChunkX = origin.getX() >> 4;
        int maxChunkX = (origin.getX() + templateInfo.worldzero$size.getX() - 1) >> 4;
        int minChunkZ = origin.getZ() >> 4;
        int maxChunkZ = (origin.getZ() + templateInfo.worldzero$size.getZ() - 1) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }

        if (!templateInfo.worldzero$template.placeInWorld(
                level,
                origin,
                origin,
                new StructurePlaceSettings().setIgnoreEntities(true),
                level.random,
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
        )) {
            return false;
        }

        worldzero$placePerimeterBarriers(level, templateInfo);
        worldzero$placeDropSafetyWalls(level, templateInfo);
        worldzero$clearLooseItems(level, templateInfo);
        return true;
    }

    private static void worldzero$placePerimeterBarriers(ServerLevel level, TemplateInfo templateInfo) {
        BlockPos origin = WORLDZERO_BASE_ORIGIN;
        int minX = origin.getX() - WORLDZERO_BARRIER_PADDING;
        int maxX = origin.getX() + templateInfo.worldzero$size.getX() - 1 + WORLDZERO_BARRIER_PADDING;
        int minY = origin.getY() - 1;
        int maxY = origin.getY() + templateInfo.worldzero$size.getY() - 1 + WORLDZERO_BARRIER_HEIGHT_PADDING;
        int minZ = origin.getZ() - WORLDZERO_BARRIER_PADDING;
        int maxZ = origin.getZ() + templateInfo.worldzero$size.getZ() - 1 + WORLDZERO_BARRIER_PADDING;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                worldzero$placeBarrierIfAir(level, new BlockPos(x, y, minZ));
                worldzero$placeBarrierIfAir(level, new BlockPos(x, y, maxZ));
            }
        }

        for (int z = minZ; z <= maxZ; z++) {
            for (int y = minY; y <= maxY; y++) {
                worldzero$placeBarrierIfAir(level, new BlockPos(minX, y, z));
                worldzero$placeBarrierIfAir(level, new BlockPos(maxX, y, z));
            }
        }
    }

    private static void worldzero$placeDropSafetyWalls(ServerLevel level, TemplateInfo templateInfo) {
        BlockPos origin = WORLDZERO_BASE_ORIGIN;
        int minX = origin.getX() - WORLDZERO_BARRIER_PADDING;
        int maxX = origin.getX() + templateInfo.worldzero$size.getX() - 1 + WORLDZERO_BARRIER_PADDING;
        int minY = origin.getY();
        int maxY = origin.getY() + templateInfo.worldzero$size.getY() - 2;
        int minZ = origin.getZ() - WORLDZERO_BARRIER_PADDING;
        int maxZ = origin.getZ() + templateInfo.worldzero$size.getZ() - 1 + WORLDZERO_BARRIER_PADDING;
        Set<BlockPos> wallBases = new HashSet<>();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos standPos = new BlockPos(x, y, z);
                    if (!worldzero$isStandable(level, standPos)) {
                        continue;
                    }

                    for (Direction direction : Direction.Plane.HORIZONTAL) {
                        BlockPos wallBasePos = standPos.relative(direction);
                        BlockPos wallHeadPos = wallBasePos.above();
                        if (!level.getBlockState(wallBasePos).getCollisionShape(level, wallBasePos).isEmpty()
                                || !level.getBlockState(wallHeadPos).getCollisionShape(level, wallHeadPos).isEmpty()) {
                            continue;
                        }

                        BlockPos dropBasePos = wallBasePos.below();
                        if (!level.getBlockState(dropBasePos).isAir()) {
                            continue;
                        }

                        if (!worldzero$isUnsafeDrop(level, dropBasePos, level.getMinBuildHeight())) {
                            continue;
                        }

                        wallBases.add(wallBasePos.immutable());
                    }
                }
            }
        }

        for (BlockPos wallBase : wallBases) {
            for (int offset = 0; offset < WORLDZERO_DROP_SAFETY_WALL_HEIGHT; offset++) {
                worldzero$placeBarrierIfAir(level, wallBase.above(offset));
            }
        }
    }

    private static void worldzero$placeBarrierIfAir(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).isAir()) {
            level.setBlock(pos, Blocks.BARRIER.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private static boolean worldzero$isUnsafeDrop(ServerLevel level, BlockPos startPos, int minY) {
        int airDepth = 0;
        for (int y = startPos.getY(); y >= minY; y--) {
            BlockPos pos = new BlockPos(startPos.getX(), y, startPos.getZ());
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                break;
            }
            airDepth++;
        }
        return airDepth >= 4;
    }

    @Nullable
    private static BlockPos worldzero$findPrimaryBedPos(ServerLevel level, TemplateInfo templateInfo) {
        BlockPos origin = WORLDZERO_BASE_ORIGIN;
        BlockPos fallback = null;
        for (int x = origin.getX(); x < origin.getX() + templateInfo.worldzero$size.getX(); x++) {
            for (int y = origin.getY(); y < origin.getY() + templateInfo.worldzero$size.getY(); y++) {
                for (int z = origin.getZ(); z < origin.getZ() + templateInfo.worldzero$size.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.is(BlockTags.BEDS) || !(state.getBlock() instanceof BedBlock)) {
                        continue;
                    }

                    if (fallback == null) {
                        fallback = pos.immutable();
                    }

                    if (state.hasProperty(BedBlock.PART) && state.getValue(BedBlock.PART) == BedPart.FOOT) {
                        return pos.immutable();
                    }
                }
            }
        }

        return fallback;
    }

    private static BlockPos worldzero$findSpawnPosNearBed(
            ServerLevel level,
            @Nullable BlockPos bedPos,
            TemplateInfo templateInfo
    ) {
        if (bedPos != null) {
            BlockState bedState = level.getBlockState(bedPos);
            Direction facing = bedState.hasProperty(BedBlock.FACING) ? bedState.getValue(BedBlock.FACING) : Direction.SOUTH;
            BlockPos[] candidates = new BlockPos[]{
                    bedPos.relative(facing.getOpposite()),
                    bedPos.relative(facing.getClockWise()),
                    bedPos.relative(facing.getCounterClockWise()),
                    bedPos.relative(facing),
                    bedPos.above()
            };

            for (BlockPos candidate : candidates) {
                if (worldzero$isStandable(level, candidate)) {
                    return candidate.immutable();
                }
            }
        }

        BlockPos origin = WORLDZERO_BASE_ORIGIN;
        int centerX = origin.getX() + templateInfo.worldzero$size.getX() / 2;
        int centerZ = origin.getZ() + templateInfo.worldzero$size.getZ() / 2;
        for (int y = origin.getY() + templateInfo.worldzero$size.getY(); y >= origin.getY(); y--) {
            for (int dx = -WORLDZERO_SCAN_RADIUS_HORIZONTAL; dx <= WORLDZERO_SCAN_RADIUS_HORIZONTAL; dx++) {
                for (int dz = -WORLDZERO_SCAN_RADIUS_HORIZONTAL; dz <= WORLDZERO_SCAN_RADIUS_HORIZONTAL; dz++) {
                    BlockPos candidate = new BlockPos(centerX + dx, y, centerZ + dz);
                    if (worldzero$isStandable(level, candidate)) {
                        return candidate.immutable();
                    }
                }
            }
        }

        return origin.offset(
                Math.max(0, (templateInfo.worldzero$size.getX() - 1) / 2),
                1,
                Math.max(0, (templateInfo.worldzero$size.getZ() - 1) / 2)
        );
    }

    private static boolean worldzero$isStandable(ServerLevel level, BlockPos pos) {
        BlockState feetState = level.getBlockState(pos);
        BlockState headState = level.getBlockState(pos.above());
        BlockState floorState = level.getBlockState(pos.below());
        return feetState.getCollisionShape(level, pos).isEmpty()
                && headState.getCollisionShape(level, pos.above()).isEmpty()
                && !floorState.getCollisionShape(level, pos.below()).isEmpty();
    }

    private static float worldzero$getSpawnYaw(ServerLevel level, @Nullable BlockPos bedPos) {
        if (bedPos == null) {
            return 0.0F;
        }

        BlockState bedState = level.getBlockState(bedPos);
        if (bedState.hasProperty(BedBlock.FACING)) {
            return bedState.getValue(BedBlock.FACING).getOpposite().toYRot();
        }

        return 0.0F;
    }

    private static void worldzero$clearLooseItems(ServerLevel level, TemplateInfo templateInfo) {
        AABB bounds = worldzero$getStructureBounds(templateInfo).inflate(4.0D, 4.0D, 4.0D);
        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, bounds)
                .forEach(net.minecraft.world.entity.item.ItemEntity::discard);
    }

    @Nullable
    private static StructureRuntime worldzero$getOrCreateStructureRuntime(ServerLevel level, SessionState sessionState) {
        if (sessionState.worldzero$structureRuntime != null) {
            return sessionState.worldzero$structureRuntime;
        }

        TemplateInfo templateInfo = worldzero$getTemplateInfo(level);
        if (templateInfo == null) {
            return null;
        }

        sessionState.worldzero$structureRuntime = worldzero$createStructureRuntime(level, templateInfo);
        return sessionState.worldzero$structureRuntime;
    }

    private static StructureRuntime worldzero$createStructureRuntime(ServerLevel level, TemplateInfo templateInfo) {
        BlockPos origin = WORLDZERO_BASE_ORIGIN;
        int minX = origin.getX();
        int maxX = origin.getX() + templateInfo.worldzero$size.getX() - 1;
        int minY = origin.getY();
        int maxY = origin.getY() + templateInfo.worldzero$size.getY() - 1;
        int minZ = origin.getZ();
        int maxZ = origin.getZ() + templateInfo.worldzero$size.getZ() - 1;
        int coreMinX = Math.min(maxX, minX + 1);
        int coreMaxX = Math.max(minX, maxX - 1);
        int coreMinZ = Math.min(maxZ, minZ + 1);
        int coreMaxZ = Math.max(minZ, maxZ - 1);
        int coreMaxY = Math.max(minY, maxY - 1);
        boolean navigationAxisX = (coreMaxX - coreMinX) >= (coreMaxZ - coreMinZ);
        int navigationCenter = navigationAxisX ? (coreMinX + coreMaxX) / 2 : (coreMinZ + coreMaxZ) / 2;
        List<BlockPos> windowBlocks = new ArrayList<>();
        List<BlockPos> insideStandablePositions = new ArrayList<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (worldzero$isGlassLikeBlock(state)
                            && (x <= minX + 1 || x >= maxX - 1 || z <= minZ + 1 || z >= maxZ - 1)) {
                        windowBlocks.add(pos.immutable());
                    }
                }
            }
        }

        for (int x = coreMinX; x <= coreMaxX; x++) {
            for (int y = minY; y <= coreMaxY; y++) {
                for (int z = coreMinZ; z <= coreMaxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (worldzero$isStandable(level, pos)) {
                        insideStandablePositions.add(pos.immutable());
                    }
                }
            }
        }

        return new StructureRuntime(
                templateInfo,
                minX,
                maxX,
                minY,
                maxY,
                minZ,
                maxZ,
                coreMinX,
                coreMaxX,
                coreMinZ,
                coreMaxZ,
                navigationAxisX,
                navigationCenter,
                windowBlocks,
                insideStandablePositions
        );
    }

    private static void worldzero$syncVisitStates(ServerLevel level, SessionState sessionState) {
        Set<UUID> activePlayers = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }

            activePlayers.add(player.getUUID());
            sessionState.worldzero$visits.computeIfAbsent(
                    player.getUUID(),
                    ignored -> new VisitState(level.getGameTime(), player.blockPosition())
            );
        }

        List<UUID> stalePlayers = new ArrayList<>();
        for (UUID playerId : sessionState.worldzero$visits.keySet()) {
            if (!activePlayers.contains(playerId)) {
                stalePlayers.add(playerId);
            }
        }

        for (UUID playerId : stalePlayers) {
            worldzero$clearPlayerSessionState(level.getServer(), sessionState, playerId);
        }
    }

    private static void worldzero$tickDoorAppearances(ServerLevel level, SessionState sessionState, long gameTime) {
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

            worldzero$lookEntityAtPlayer(echo, player);

            if (activeAppearance.worldzero$seenTick < 0L) {
                if (worldzero$isSeenByPlayer(echo, player)) {
                    activeAppearance.worldzero$seenTick = gameTime;
                    activeAppearance.worldzero$discardTick = gameTime + WORLDZERO_DOOR_BLACK_ECHO_LIFETIME_TICKS;
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

    private static void worldzero$tickDelayedActions(ServerLevel level, SessionState sessionState, long gameTime) {
        Iterator<DelayedAction> iterator = sessionState.worldzero$delayedActions.iterator();
        while (iterator.hasNext()) {
            DelayedAction delayedAction = iterator.next();
            if (delayedAction.worldzero$dimension != level.dimension() || gameTime < delayedAction.worldzero$dueTick) {
                continue;
            }

            if (delayedAction.worldzero$type == DelayedActionType.DOOR_CLOSE) {
                if (worldzero$setDoorOpenSilent(level, delayedAction.worldzero$pos, false)) {
                    worldzero$playDoorSound(level, delayedAction.worldzero$pos, false);
                }
            } else if (delayedAction.worldzero$type == DelayedActionType.CHEST_CLOSE) {
                worldzero$playChestCloseSound(level, delayedAction.worldzero$pos);
            }

            iterator.remove();
        }
    }

    private static void worldzero$tickOutsidePasses(ServerLevel level, SessionState sessionState) {
        Iterator<ActiveOutsidePass> iterator = sessionState.worldzero$outsidePasses.iterator();
        while (iterator.hasNext()) {
            ActiveOutsidePass activePass = iterator.next();
            if (activePass.worldzero$dimension != level.dimension()) {
                continue;
            }

            Entity entity = level.getEntity(activePass.worldzero$entityId);
            if (!(entity instanceof WorldZeroEchoEntity echo)) {
                iterator.remove();
                continue;
            }

            ServerPlayer player = level.getServer().getPlayerList().getPlayer(activePass.worldzero$playerId);
            if (player == null || !player.isAlive() || player.isSpectator() || player.serverLevel() != level) {
                iterator.remove();
                entity.discard();
                continue;
            }

            double step = Math.min(activePass.worldzero$speed, activePass.worldzero$distance - activePass.worldzero$progress);
            activePass.worldzero$progress += step;
            Vec3 position = activePass.worldzero$startPos.add(activePass.worldzero$direction.scale(activePass.worldzero$progress));
            echo.setPos(position.x, position.y, position.z);
            echo.setDeltaMovement(Vec3.ZERO);
            echo.setSprinting(false);
            worldzero$setEntityYawPitch(echo, activePass.worldzero$yaw, 0.0F);

            activePass.worldzero$stepDistanceAccumulator += step;
            while (activePass.worldzero$stepDistanceAccumulator >= WORLDZERO_OUTSIDE_PASS_STEP_DISTANCE) {
                activePass.worldzero$stepDistanceAccumulator -= WORLDZERO_OUTSIDE_PASS_STEP_DISTANCE;
                worldzero$playFootstep(level, echo.blockPosition(), echo);
            }

            if (activePass.worldzero$progress + 0.0001D < activePass.worldzero$distance) {
                continue;
            }

            iterator.remove();
            echo.discard();
        }
    }

    private static void worldzero$tickVisitStates(
            ServerLevel level,
            SessionState sessionState,
            StructureRuntime structureRuntime,
            long gameTime
    ) {
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }

            VisitState visitState = sessionState.worldzero$visits.computeIfAbsent(
                    player.getUUID(),
                    ignored -> new VisitState(gameTime, player.blockPosition())
            );
            boolean insideCore = structureRuntime.worldzero$isInsideCore(player.blockPosition());
            if (insideCore) {
                visitState.worldzero$lastInsidePos = player.blockPosition().immutable();
            }

            if (!visitState.worldzero$returnLockTriggered
                    && visitState.worldzero$lastKnownX > WORLDZERO_RETURN_LOCK_TRIGGER_X
                    && player.getX() <= WORLDZERO_RETURN_LOCK_TRIGGER_X
                    && worldzero$tryStartScriptedReturnLockEvent(level, sessionState, player, gameTime)) {
                visitState.worldzero$returnLockTriggered = true;
            }
            visitState.worldzero$lastKnownX = player.getX();
        }
    }

    private static boolean worldzero$tryStartScriptedReturnLockEvent(
            ServerLevel level,
            SessionState sessionState,
            ServerPlayer player,
            long gameTime
    ) {
        if (sessionState.worldzero$scriptedReturnLockEvent != null) {
            return false;
        }

        List<BlockPos> barrierPositions = worldzero$placeTemporaryReturnBarriers(level, player.blockPosition());
        BlockPos doorPos = worldzero$findNearestDoorAlongPath(level, WORLDZERO_SCRIPTED_POINT_1, WORLDZERO_SCRIPTED_POINT_2);
        BlockPos chestPos = worldzero$findNearestChestLikeBlock(level, WORLDZERO_SCRIPTED_POINT_3);
        BlockPos upperChestPos = worldzero$findUpperChestLikeBlock(level, chestPos);
        BlockPos bedPos = worldzero$findNearestBedBlock(level, WORLDZERO_SCRIPTED_POINT_4);
        sessionState.worldzero$scriptedReturnLockEvent = new ScriptedReturnLockEvent(
                level.dimension(),
                player.getUUID(),
                doorPos,
                chestPos,
                upperChestPos,
                bedPos,
                barrierPositions,
                gameTime + WORLDZERO_RETURN_LOCK_EVENT_DELAY_TICKS
        );
        return true;
    }

    private static void worldzero$tickScriptedReturnLockEvent(ServerLevel level, SessionState sessionState, long gameTime) {
        ScriptedReturnLockEvent scriptedEvent = sessionState.worldzero$scriptedReturnLockEvent;
        if (scriptedEvent == null || scriptedEvent.worldzero$dimension != level.dimension()) {
            return;
        }

        ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(scriptedEvent.worldzero$playerId);
        if (ownerPlayer == null || !ownerPlayer.isAlive() || ownerPlayer.isSpectator() || ownerPlayer.serverLevel() != level) {
            worldzero$clearScriptedReturnLockEvent(level.getServer(), sessionState);
            return;
        }

        WorldZeroEchoEntity echo = null;
        if (scriptedEvent.worldzero$entityId != null) {
            Entity entity = level.getEntity(scriptedEvent.worldzero$entityId);
            if (entity instanceof WorldZeroEchoEntity worldZeroEchoEntity) {
                echo = worldZeroEchoEntity;
            } else if (scriptedEvent.worldzero$phase != ScriptedReturnLockPhase.WAITING_TO_START) {
                worldzero$clearScriptedReturnLockEvent(level.getServer(), sessionState);
                return;
            }
        }

        switch (scriptedEvent.worldzero$phase) {
            case WAITING_TO_START -> {
                if (gameTime < scriptedEvent.worldzero$dueTick) {
                    return;
                }

                WorldZeroEchoEntity blackEcho = WorldZeroEntities.WORLDZERO_BLACK_ECHO.get().create(level);
                if (blackEcho == null) {
                    worldzero$clearScriptedReturnLockEvent(level.getServer(), sessionState);
                    return;
                }

                blackEcho.moveTo(
                        WORLDZERO_SCRIPTED_POINT_1.x,
                        WORLDZERO_SCRIPTED_POINT_1.y,
                        WORLDZERO_SCRIPTED_POINT_1.z,
                        0.0F,
                        0.0F
                );
                blackEcho.setNoGravity(true);
                blackEcho.setSilent(true);
                blackEcho.setDeltaMovement(Vec3.ZERO);
                blackEcho.setSprinting(false);
                level.addFreshEntity(blackEcho);
                scriptedEvent.worldzero$entityId = blackEcho.getUUID();
                scriptedEvent.worldzero$phase = ScriptedReturnLockPhase.WALK_TO_POINT_2;
            }
            case WALK_TO_POINT_2 -> {
                if (echo == null) {
                    worldzero$clearScriptedReturnLockEvent(level.getServer(), sessionState);
                    return;
                }

                if (!scriptedEvent.worldzero$doorOpened
                        && scriptedEvent.worldzero$doorPos != null
                        && echo.position().distanceToSqr(worldzero$getBlockCenter(scriptedEvent.worldzero$doorPos)) <= 2.25D) {
                    worldzero$setDoorStateWithSound(level, scriptedEvent.worldzero$doorPos, true);
                    scriptedEvent.worldzero$doorOpened = true;
                }

                if (worldzero$moveSilentBlackEchoTowards(echo, WORLDZERO_SCRIPTED_POINT_2, WORLDZERO_SCRIPTED_BLACK_ECHO_SPEED_BLOCKS_PER_TICK)) {
                    if (scriptedEvent.worldzero$doorOpened && scriptedEvent.worldzero$doorPos != null) {
                        worldzero$setDoorStateWithSound(level, scriptedEvent.worldzero$doorPos, false);
                    }
                    scriptedEvent.worldzero$phase = ScriptedReturnLockPhase.WALK_TO_POINT_3;
                }
            }
            case WALK_TO_POINT_3 -> {
                if (echo == null) {
                    worldzero$clearScriptedReturnLockEvent(level.getServer(), sessionState);
                    return;
                }

                if (worldzero$moveSilentBlackEchoTowards(echo, WORLDZERO_SCRIPTED_POINT_3, WORLDZERO_SCRIPTED_BLACK_ECHO_SPEED_BLOCKS_PER_TICK)) {
                    if (scriptedEvent.worldzero$chestPos != null) {
                        worldzero$openChestLike(level, scriptedEvent.worldzero$chestPos, ownerPlayer);
                    }
                    scriptedEvent.worldzero$dueTick = gameTime + WORLDZERO_RETURN_LOCK_CHEST_CLOSE_DELAY_TICKS;
                    scriptedEvent.worldzero$phase = ScriptedReturnLockPhase.WAITING_CHEST_CLOSE;
                }
            }
            case WAITING_CHEST_CLOSE -> {
                if (echo == null) {
                    worldzero$clearScriptedReturnLockEvent(level.getServer(), sessionState);
                    return;
                }

                worldzero$moveSilentBlackEchoTowards(echo, WORLDZERO_SCRIPTED_POINT_3, WORLDZERO_SCRIPTED_BLACK_ECHO_SPEED_BLOCKS_PER_TICK);
                if (gameTime < scriptedEvent.worldzero$dueTick) {
                    return;
                }

                if (scriptedEvent.worldzero$chestPos != null) {
                    worldzero$closeChestLike(level, scriptedEvent.worldzero$chestPos, ownerPlayer);
                }
                if (scriptedEvent.worldzero$upperChestPos != null) {
                    worldzero$clearContainer(level, scriptedEvent.worldzero$upperChestPos, ownerPlayer);
                }
                scriptedEvent.worldzero$phase = ScriptedReturnLockPhase.WALK_TO_POINT_4;
            }
            case WALK_TO_POINT_4 -> {
                if (echo == null) {
                    worldzero$clearScriptedReturnLockEvent(level.getServer(), sessionState);
                    return;
                }

                if (worldzero$moveSilentBlackEchoTowards(echo, WORLDZERO_SCRIPTED_POINT_4, WORLDZERO_SCRIPTED_BLACK_ECHO_SPEED_BLOCKS_PER_TICK)) {
                    if (scriptedEvent.worldzero$bedPos != null) {
                        worldzero$putBlackEchoToBed(level, echo, scriptedEvent.worldzero$bedPos);
                    }
                    if (!scriptedEvent.worldzero$barriersReleased) {
                        worldzero$removeTemporaryReturnBarriers(level, scriptedEvent.worldzero$barrierPositions);
                        scriptedEvent.worldzero$barriersReleased = true;
                    }
                    scriptedEvent.worldzero$phase = ScriptedReturnLockPhase.COMPLETED;
                }
            }
            case COMPLETED -> {
            }
            case WAITING_PLAYER_UPPER_CHEST_CLOSE -> {
            }
            case FINAL_AMBUSH_WAIT_LOOK -> {
                if (echo == null) {
                    worldzero$clearScriptedReturnLockEvent(level.getServer(), sessionState);
                    return;
                }

                worldzero$lookEntityAtPlayer(echo, ownerPlayer);
                if (!worldzero$isSeenByPlayer(echo, ownerPlayer)) {
                    return;
                }

                ownerPlayer.setDeltaMovement(0.0D, 0.0D, 0.0D);
                ownerPlayer.fallDistance = 0.0F;
                scriptedEvent.worldzero$dueTick = gameTime + WORLDZERO_FINAL_AMBUSH_FREEZE_TICKS;
                WorldZeroNetwork.sendFreezeStart(ownerPlayer, WORLDZERO_FINAL_AMBUSH_FREEZE_TICKS, echo.getId());
                scriptedEvent.worldzero$phase = ScriptedReturnLockPhase.FINAL_AMBUSH_FREEZE;
            }
            case FINAL_AMBUSH_FREEZE -> {
                ownerPlayer.setDeltaMovement(0.0D, 0.0D, 0.0D);
                ownerPlayer.fallDistance = 0.0F;
                if (echo != null) {
                    worldzero$lookEntityAtPlayer(echo, ownerPlayer);
                }
                if (gameTime < scriptedEvent.worldzero$dueTick) {
                    return;
                }

                WorldZeroNetwork.sendFreezeEnd(ownerPlayer);
                worldzero$returnPlayerFromHouseBad(ownerPlayer);
                return;
            }
        }
    }

    private static void worldzero$clearScriptedReturnLockEvent(MinecraftServer server, SessionState sessionState) {
        ScriptedReturnLockEvent scriptedEvent = sessionState.worldzero$scriptedReturnLockEvent;
        if (scriptedEvent == null) {
            return;
        }

        if (scriptedEvent.worldzero$phase == ScriptedReturnLockPhase.FINAL_AMBUSH_FREEZE) {
            ServerPlayer player = server.getPlayerList().getPlayer(scriptedEvent.worldzero$playerId);
            if (player != null) {
                WorldZeroNetwork.sendFreezeEnd(player);
            }
        }

        if (!scriptedEvent.worldzero$barriersReleased) {
            ServerLevel level = server.getLevel(scriptedEvent.worldzero$dimension);
            if (level != null) {
                worldzero$removeTemporaryReturnBarriers(level, scriptedEvent.worldzero$barrierPositions);
            }
        }

        Entity entity = worldzero$findEntity(server, scriptedEvent.worldzero$entityId);
        if (entity != null) {
            entity.discard();
        }
        sessionState.worldzero$scriptedReturnLockEvent = null;
    }

    private static List<BlockPos> worldzero$placeTemporaryReturnBarriers(ServerLevel level, BlockPos centerPos) {
        List<BlockPos> barrierPositions = new ArrayList<>();
        int minZ = Mth.floor(Math.min(WORLDZERO_RETURN_LOCK_BARRIER_START_Z, WORLDZERO_RETURN_LOCK_BARRIER_END_Z));
        int maxZ = Mth.ceil(Math.max(WORLDZERO_RETURN_LOCK_BARRIER_START_Z, WORLDZERO_RETURN_LOCK_BARRIER_END_Z));
        double zSpan = WORLDZERO_RETURN_LOCK_BARRIER_END_Z - WORLDZERO_RETURN_LOCK_BARRIER_START_Z;
        for (int x = WORLDZERO_RETURN_LOCK_BARRIER_MIN_X; x <= WORLDZERO_RETURN_LOCK_BARRIER_MAX_X; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double sampledZ = z + 0.5D;
                double progress = zSpan == 0.0D
                        ? 0.0D
                        : Mth.clamp((sampledZ - WORLDZERO_RETURN_LOCK_BARRIER_START_Z) / zSpan, 0.0D, 1.0D);
                double sampledY = Mth.lerp(progress, WORLDZERO_RETURN_LOCK_BARRIER_START_Y, WORLDZERO_RETURN_LOCK_BARRIER_END_Y);
                int minY = Mth.floor(sampledY);
                int maxY = Mth.ceil(sampledY) + 1;
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && !state.getCollisionShape(level, pos).isEmpty()) {
                        continue;
                    }

                    level.setBlock(pos, Blocks.BARRIER.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    barrierPositions.add(pos.immutable());
                }
            }
        }
        return barrierPositions;
    }

    private static void worldzero$removeTemporaryReturnBarriers(ServerLevel level, List<BlockPos> barrierPositions) {
        for (BlockPos pos : barrierPositions) {
            if (level.getBlockState(pos).is(Blocks.BARRIER)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    private static boolean worldzero$moveSilentBlackEchoTowards(WorldZeroEchoEntity echo, Vec3 target, double speed) {
        Vec3 currentPos = echo.position();
        Vec3 delta = target.subtract(currentPos);
        double distance = delta.length();
        if (distance <= speed + 0.0001D) {
            echo.setPos(target.x, target.y, target.z);
            echo.setDeltaMovement(Vec3.ZERO);
            echo.setSprinting(false);
            if (distance > 0.0001D) {
                float yaw = (float) (Math.atan2(delta.z, delta.x) * (180.0D / Math.PI)) - 90.0F;
                worldzero$setEntityYawPitch(echo, yaw, 0.0F);
            }
            return true;
        }

        Vec3 direction = delta.scale(1.0D / distance);
        Vec3 nextPos = currentPos.add(direction.scale(speed));
        echo.setPos(nextPos.x, nextPos.y, nextPos.z);
        echo.setDeltaMovement(Vec3.ZERO);
        echo.setSprinting(false);
        float yaw = (float) (Math.atan2(direction.z, direction.x) * (180.0D / Math.PI)) - 90.0F;
        worldzero$setEntityYawPitch(echo, yaw, 0.0F);
        return false;
    }

    private static void worldzero$setDoorStateWithSound(ServerLevel level, BlockPos doorPos, boolean open) {
        if (worldzero$setDoorOpenSilent(level, doorPos, open)) {
            worldzero$playDoorSound(level, doorPos, open);
        }
    }

    @Nullable
    private static BlockPos worldzero$findNearestDoorAlongPath(ServerLevel level, Vec3 start, Vec3 end) {
        int minX = Mth.floor(Math.min(start.x, end.x)) - WORLDZERO_SCRIPTED_BLOCK_SEARCH_HORIZONTAL;
        int maxX = Mth.floor(Math.max(start.x, end.x)) + WORLDZERO_SCRIPTED_BLOCK_SEARCH_HORIZONTAL;
        int minY = Mth.floor(Math.min(start.y, end.y)) - WORLDZERO_SCRIPTED_BLOCK_SEARCH_VERTICAL;
        int maxY = Mth.floor(Math.max(start.y, end.y)) + WORLDZERO_SCRIPTED_BLOCK_SEARCH_VERTICAL;
        int minZ = Mth.floor(Math.min(start.z, end.z)) - WORLDZERO_SCRIPTED_BLOCK_SEARCH_HORIZONTAL;
        int maxZ = Mth.floor(Math.max(start.z, end.z)) + WORLDZERO_SCRIPTED_BLOCK_SEARCH_HORIZONTAL;
        BlockPos bestPos = null;
        double bestDistanceSqr = Double.MAX_VALUE;
        Set<BlockPos> visited = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    BlockPos doorPos = worldzero$resolveDoorLowerPos(level, candidate);
                    if (doorPos == null || !visited.add(doorPos)) {
                        continue;
                    }

                    double distanceSqr = worldzero$distanceToSegmentSqr(worldzero$getBlockCenter(doorPos), start, end);
                    if (distanceSqr < bestDistanceSqr) {
                        bestDistanceSqr = distanceSqr;
                        bestPos = doorPos.immutable();
                    }
                }
            }
        }
        return bestPos;
    }

    @Nullable
    private static BlockPos worldzero$findNearestChestLikeBlock(ServerLevel level, Vec3 target) {
        return worldzero$findNearestBlock(level, target, WorldZeroHouseBadDimension::worldzero$isChestLikeBlock);
    }

    @Nullable
    private static BlockPos worldzero$findUpperChestLikeBlock(ServerLevel level, @Nullable BlockPos baseChestPos) {
        if (baseChestPos == null) {
            return null;
        }

        BlockPos bestPos = null;
        for (int offset = 0; offset <= 2; offset++) {
            BlockPos candidate = baseChestPos.above(offset);
            if (worldzero$isChestLikeBlock(level.getBlockState(candidate))) {
                bestPos = candidate.immutable();
            }
        }
        return bestPos != null ? bestPos : baseChestPos.immutable();
    }

    @Nullable
    private static BlockPos worldzero$findNearestBedBlock(ServerLevel level, Vec3 target) {
        return worldzero$findNearestBlock(level, target, state -> state.is(BlockTags.BEDS));
    }

    @Nullable
    private static BlockPos worldzero$findNearestBlock(
            ServerLevel level,
            Vec3 target,
            java.util.function.Predicate<BlockState> predicate
    ) {
        BlockPos centerPos = BlockPos.containing(target);
        BlockPos bestPos = null;
        double bestDistanceSqr = Double.MAX_VALUE;
        for (int x = centerPos.getX() - WORLDZERO_SCRIPTED_BLOCK_SEARCH_HORIZONTAL;
             x <= centerPos.getX() + WORLDZERO_SCRIPTED_BLOCK_SEARCH_HORIZONTAL;
             x++) {
            for (int y = centerPos.getY() - WORLDZERO_SCRIPTED_BLOCK_SEARCH_VERTICAL;
                 y <= centerPos.getY() + WORLDZERO_SCRIPTED_BLOCK_SEARCH_VERTICAL;
                 y++) {
                for (int z = centerPos.getZ() - WORLDZERO_SCRIPTED_BLOCK_SEARCH_HORIZONTAL;
                     z <= centerPos.getZ() + WORLDZERO_SCRIPTED_BLOCK_SEARCH_HORIZONTAL;
                     z++) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(candidate);
                    if (!predicate.test(state)) {
                        continue;
                    }

                    double distanceSqr = target.distanceToSqr(worldzero$getBlockCenter(candidate));
                    if (distanceSqr < bestDistanceSqr) {
                        bestDistanceSqr = distanceSqr;
                        bestPos = candidate.immutable();
                    }
                }
            }
        }
        return bestPos;
    }

    private static double worldzero$distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double segmentLengthSqr = segment.lengthSqr();
        if (segmentLengthSqr < 0.0001D) {
            return point.distanceToSqr(start);
        }

        double progress = Mth.clamp(point.subtract(start).dot(segment) / segmentLengthSqr, 0.0D, 1.0D);
        Vec3 projectedPoint = start.add(segment.scale(progress));
        return point.distanceToSqr(projectedPoint);
    }

    private static Vec3 worldzero$getBlockCenter(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    @Nullable
    private static BlockPos worldzero$findWrongNavigationTarget(
            StructureRuntime structureRuntime,
            BlockPos currentPos,
            int targetSide
    ) {
        BlockPos bestPos = null;
        double bestDistanceSqr = -1.0D;
        for (BlockPos candidate : structureRuntime.worldzero$insideStandablePositions) {
            if (worldzero$getNavigationSide(structureRuntime, candidate) != targetSide) {
                continue;
            }

            double distanceSqr = candidate.distSqr(currentPos);
            if (distanceSqr < 16.0D || distanceSqr > 24.0D * 24.0D) {
                continue;
            }

            if (distanceSqr > bestDistanceSqr) {
                bestDistanceSqr = distanceSqr;
                bestPos = candidate.immutable();
            }
        }

        return bestPos;
    }

    private static int worldzero$getNavigationSide(StructureRuntime structureRuntime, BlockPos pos) {
        int delta = structureRuntime.worldzero$navigationAxisX
                ? pos.getX() - structureRuntime.worldzero$navigationCenter
                : pos.getZ() - structureRuntime.worldzero$navigationCenter;
        if (Math.abs(delta) <= 1) {
            return 0;
        }
        return delta < 0 ? -1 : 1;
    }

    private static boolean worldzero$tryStartOutsideWindowPass(
            ServerLevel level,
            SessionState sessionState,
            StructureRuntime structureRuntime,
            ServerPlayer player
    ) {
        if (!sessionState.worldzero$outsidePasses.isEmpty()) {
            return false;
        }

        OutsidePassPlan passPlan = worldzero$createOutsidePassPlan(level, structureRuntime, player);
        if (passPlan == null) {
            return false;
        }

        WorldZeroEchoEntity blackEcho = WorldZeroEntities.WORLDZERO_BLACK_ECHO.get().create(level);
        if (blackEcho == null) {
            return false;
        }

        blackEcho.moveTo(passPlan.worldzero$startPos.x, passPlan.worldzero$startPos.y, passPlan.worldzero$startPos.z, passPlan.worldzero$yaw, 0.0F);
        blackEcho.setNoGravity(true);
        blackEcho.setSilent(true);
        blackEcho.setDeltaMovement(Vec3.ZERO);
        blackEcho.setSprinting(false);
        level.addFreshEntity(blackEcho);

        sessionState.worldzero$outsidePasses.add(new ActiveOutsidePass(
                level.dimension(),
                player.getUUID(),
                blackEcho.getUUID(),
                passPlan.worldzero$startPos,
                passPlan.worldzero$direction,
                passPlan.worldzero$distance,
                passPlan.worldzero$speed,
                passPlan.worldzero$yaw
        ));
        return true;
    }

    @Nullable
    private static OutsidePassPlan worldzero$createOutsidePassPlan(
            ServerLevel level,
            StructureRuntime structureRuntime,
            ServerPlayer player
    ) {
        BlockPos bestWindow = null;
        Direction bestWall = null;
        double bestDistanceSqr = Double.MAX_VALUE;
        for (BlockPos windowPos : structureRuntime.worldzero$windowBlocks) {
            Direction wall = worldzero$getWindowWall(structureRuntime, windowPos);
            if (wall == null) {
                continue;
            }

            double distanceSqr = windowPos.distSqr(player.blockPosition());
            if (distanceSqr > WORLDZERO_OUTSIDE_PASS_MAX_WINDOW_DISTANCE_SQR || distanceSqr >= bestDistanceSqr) {
                continue;
            }

            bestDistanceSqr = distanceSqr;
            bestWindow = windowPos;
            bestWall = wall;
        }

        if (bestWindow == null || bestWall == null) {
            return null;
        }

        Direction insideDirection = bestWall.getOpposite();
        Direction lineDirection = bestWall.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        BlockPos interiorBase = bestWindow.relative(insideDirection);
        BlockPos startPos = worldzero$findPassEndpoint(level, interiorBase, lineDirection.getOpposite());
        BlockPos endPos = worldzero$findPassEndpoint(level, interiorBase, lineDirection);
        if (startPos == null || endPos == null) {
            return null;
        }

        Vec3 startVec = new Vec3(startPos.getX() + 0.5D, startPos.getY(), startPos.getZ() + 0.5D);
        Vec3 endVec = new Vec3(endPos.getX() + 0.5D, endPos.getY(), endPos.getZ() + 0.5D);
        Vec3 movement = endVec.subtract(startVec);
        double distance = movement.length();
        if (distance < 4.0D) {
            return null;
        }

        Vec3 direction = movement.scale(1.0D / distance);
        float yaw = (float) (Math.atan2(direction.z, direction.x) * (180.0D / Math.PI)) - 90.0F;
        return new OutsidePassPlan(startVec, direction, distance, WORLDZERO_OUTSIDE_PASS_SPEED_BLOCKS_PER_TICK, yaw);
    }

    @Nullable
    private static BlockPos worldzero$findPassEndpoint(ServerLevel level, BlockPos interiorBase, Direction direction) {
        BlockPos bestPos = null;
        for (int distance = 1; distance <= 5; distance++) {
            BlockPos candidate = worldzero$findStandableFeetPos(level, interiorBase.relative(direction, distance));
            if (candidate == null) {
                continue;
            }
            bestPos = candidate.immutable();
        }
        return bestPos;
    }

    @Nullable
    private static Direction worldzero$getWindowWall(StructureRuntime structureRuntime, BlockPos windowPos) {
        int westDistance = Math.abs(windowPos.getX() - structureRuntime.worldzero$minX);
        int eastDistance = Math.abs(windowPos.getX() - structureRuntime.worldzero$maxX);
        int northDistance = Math.abs(windowPos.getZ() - structureRuntime.worldzero$minZ);
        int southDistance = Math.abs(windowPos.getZ() - structureRuntime.worldzero$maxZ);
        int bestDistance = Math.min(Math.min(westDistance, eastDistance), Math.min(northDistance, southDistance));
        if (bestDistance > 1) {
            return null;
        }

        if (bestDistance == westDistance) {
            return Direction.WEST;
        }
        if (bestDistance == eastDistance) {
            return Direction.EAST;
        }
        if (bestDistance == northDistance) {
            return Direction.NORTH;
        }
        return Direction.SOUTH;
    }

    private static void worldzero$scheduleDoorClose(ServerLevel level, ServerPlayer player, BlockPos doorPos) {
        SessionState sessionState = WORLDZERO_SERVER_STATES.computeIfAbsent(level.getServer(), ignored -> new SessionState());
        for (DelayedAction delayedAction : sessionState.worldzero$delayedActions) {
            if (delayedAction.worldzero$type == DelayedActionType.DOOR_CLOSE
                    && delayedAction.worldzero$playerId.equals(player.getUUID())
                    && delayedAction.worldzero$pos.equals(doorPos)
                    && delayedAction.worldzero$dimension == level.dimension()) {
                return;
            }
        }

        sessionState.worldzero$delayedActions.add(new DelayedAction(
                level.dimension(),
                player.getUUID(),
                DelayedActionType.DOOR_CLOSE,
                doorPos.immutable(),
                level.getGameTime() + worldzero$randomDelay(level, WORLDZERO_DELAYED_DOOR_MIN_TICKS, WORLDZERO_DELAYED_DOOR_MAX_TICKS)
        ));
    }

    private static void worldzero$scheduleChestClose(ServerLevel level, ServerPlayer player, BlockPos chestPos) {
        SessionState sessionState = WORLDZERO_SERVER_STATES.computeIfAbsent(level.getServer(), ignored -> new SessionState());
        for (DelayedAction delayedAction : sessionState.worldzero$delayedActions) {
            if (delayedAction.worldzero$type == DelayedActionType.CHEST_CLOSE
                    && delayedAction.worldzero$playerId.equals(player.getUUID())
                    && delayedAction.worldzero$pos.equals(chestPos)
                    && delayedAction.worldzero$dimension == level.dimension()) {
                return;
            }
        }

        sessionState.worldzero$delayedActions.add(new DelayedAction(
                level.dimension(),
                player.getUUID(),
                DelayedActionType.CHEST_CLOSE,
                chestPos.immutable(),
                level.getGameTime() + worldzero$randomDelay(level, WORLDZERO_DELAYED_CHEST_MIN_TICKS, WORLDZERO_DELAYED_CHEST_MAX_TICKS)
        ));
    }

    private static int worldzero$randomDelay(ServerLevel level, int minTicks, int maxTicks) {
        return Mth.nextInt(level.random, minTicks, maxTicks);
    }

    private static void worldzero$clearPlayerSessionState(
            MinecraftServer server,
            SessionState sessionState,
            UUID playerId
    ) {
        if (sessionState.worldzero$scriptedReturnLockEvent != null
                && sessionState.worldzero$scriptedReturnLockEvent.worldzero$playerId.equals(playerId)) {
            worldzero$clearScriptedReturnLockEvent(server, sessionState);
        }

        sessionState.worldzero$visits.remove(playerId);

        Iterator<ActiveAppearance> appearanceIterator = sessionState.worldzero$activeAppearances.iterator();
        while (appearanceIterator.hasNext()) {
            ActiveAppearance activeAppearance = appearanceIterator.next();
            if (!activeAppearance.worldzero$playerId.equals(playerId)) {
                continue;
            }

            Entity entity = worldzero$findEntity(server, activeAppearance.worldzero$entityId);
            if (entity != null) {
                entity.discard();
            }
            appearanceIterator.remove();
        }

        Iterator<ActiveOutsidePass> passIterator = sessionState.worldzero$outsidePasses.iterator();
        while (passIterator.hasNext()) {
            ActiveOutsidePass activePass = passIterator.next();
            if (!activePass.worldzero$playerId.equals(playerId)) {
                continue;
            }

            Entity entity = worldzero$findEntity(server, activePass.worldzero$entityId);
            if (entity != null) {
                entity.discard();
            }
            passIterator.remove();
        }

        sessionState.worldzero$delayedActions.removeIf(delayedAction -> delayedAction.worldzero$playerId.equals(playerId));
    }

    @Nullable
    private static Entity worldzero$findEntity(MinecraftServer server, @Nullable UUID entityId) {
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

    private static void worldzero$playDoorSound(ServerLevel level, BlockPos doorPos, boolean open) {
        BlockState state = level.getBlockState(doorPos);
        SoundEvent soundEvent = state.is(Blocks.IRON_DOOR)
                ? (open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE)
                : (open ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE);
        level.playSound(
                null,
                doorPos,
                soundEvent,
                SoundSource.BLOCKS,
                3.0F,
                0.96F + level.random.nextFloat() * 0.08F
        );
    }

    private static void worldzero$playChestCloseSound(ServerLevel level, BlockPos chestPos) {
        List<BlockPos> chestPositions = worldzero$getConnectedChestPositions(level, chestPos);
        BlockState state = level.getBlockState(chestPos);
        Vec3 soundPos = worldzero$getAverageBlockCenter(chestPositions);
        SoundEvent soundEvent = state.is(Blocks.ENDER_CHEST) ? SoundEvents.ENDER_CHEST_CLOSE : SoundEvents.CHEST_CLOSE;
        level.playSound(
                null,
                soundPos.x,
                soundPos.y,
                soundPos.z,
                soundEvent,
                SoundSource.BLOCKS,
                3.0F,
                0.95F + level.random.nextFloat() * 0.08F
        );
    }

    private static void worldzero$playChestOpenSound(ServerLevel level, BlockPos chestPos) {
        List<BlockPos> chestPositions = worldzero$getConnectedChestPositions(level, chestPos);
        BlockState state = level.getBlockState(chestPos);
        Vec3 soundPos = worldzero$getAverageBlockCenter(chestPositions);
        SoundEvent soundEvent = state.is(Blocks.ENDER_CHEST) ? SoundEvents.ENDER_CHEST_OPEN : SoundEvents.CHEST_OPEN;
        level.playSound(
                null,
                soundPos.x,
                soundPos.y,
                soundPos.z,
                soundEvent,
                SoundSource.BLOCKS,
                3.0F,
                0.95F + level.random.nextFloat() * 0.08F
        );
    }

    private static void worldzero$openChestLike(ServerLevel level, BlockPos chestPos, @Nullable ServerPlayer player) {
        List<BlockPos> chestPositions = worldzero$getConnectedChestPositions(level, chestPos);
        for (BlockPos pos : chestPositions) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof ChestBlock)) {
                continue;
            }

            level.blockEvent(pos, state.getBlock(), 1, 1);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }

        worldzero$playChestOpenSound(level, chestPos);
    }

    private static void worldzero$closeChestLike(ServerLevel level, BlockPos chestPos, @Nullable ServerPlayer player) {
        List<BlockPos> chestPositions = worldzero$getConnectedChestPositions(level, chestPos);
        for (BlockPos pos : chestPositions) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof ChestBlock)) {
                continue;
            }

            level.blockEvent(pos, state.getBlock(), 1, 0);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }

        worldzero$playChestCloseSound(level, chestPos);
    }

    private static void worldzero$clearContainer(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player) {
        for (BlockPos containerPos : worldzero$getConnectedChestPositions(level, pos)) {
            BlockEntity blockEntity = level.getBlockEntity(containerPos);
            if (!(blockEntity instanceof RandomizableContainerBlockEntity container)) {
                continue;
            }

            if (player != null) {
                container.unpackLootTable(player);
            }
            container.clearContent();
            container.setChanged();
            BlockState state = level.getBlockState(containerPos);
            level.sendBlockUpdated(containerPos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private static void worldzero$putBlackEchoToBed(ServerLevel level, WorldZeroEchoEntity echo, BlockPos bedPos) {
        BlockState bedState = level.getBlockState(bedPos);
        if (!bedState.is(BlockTags.BEDS) || !(bedState.getBlock() instanceof BedBlock)) {
            return;
        }

        BlockPos headPos = bedPos.immutable();
        if (bedState.hasProperty(BedBlock.PART)
                && bedState.getValue(BedBlock.PART) == BedPart.FOOT
                && bedState.hasProperty(BedBlock.FACING)) {
            headPos = bedPos.relative(bedState.getValue(BedBlock.FACING)).immutable();
        }

        BlockState headState = level.getBlockState(headPos);
        if (!headState.is(BlockTags.BEDS)) {
            headPos = bedPos.immutable();
            headState = bedState;
        }

        echo.setPos(headPos.getX() + 0.5D, headPos.getY() + 0.5625D, headPos.getZ() + 0.5D);
        echo.setDeltaMovement(Vec3.ZERO);
        echo.setSprinting(false);
        echo.startSleeping(headPos);
        if (headState.hasProperty(BedBlock.FACING)) {
            worldzero$setEntityYawPitch(echo, headState.getValue(BedBlock.FACING).toYRot(), 0.0F);
        }
    }

    private static void worldzero$tryArmFinalUpperChestAmbush(
            ServerLevel level,
            SessionState sessionState,
            ServerPlayer player,
            BlockPos clickedPos
    ) {
        ScriptedReturnLockEvent scriptedEvent = sessionState.worldzero$scriptedReturnLockEvent;
        if (scriptedEvent == null
                || scriptedEvent.worldzero$dimension != level.dimension()
                || !scriptedEvent.worldzero$playerId.equals(player.getUUID())
                || scriptedEvent.worldzero$phase != ScriptedReturnLockPhase.COMPLETED
                || !worldzero$isScriptedUpperChest(level, scriptedEvent, clickedPos)
                || !worldzero$isContainerEmpty(level, scriptedEvent.worldzero$upperChestPos, player)) {
            return;
        }

        Entity entity = worldzero$findEntity(level.getServer(), scriptedEvent.worldzero$entityId);
        if (entity != null) {
            entity.discard();
        }
        scriptedEvent.worldzero$entityId = null;
        scriptedEvent.worldzero$phase = ScriptedReturnLockPhase.WAITING_PLAYER_UPPER_CHEST_CLOSE;
    }

    private static void worldzero$trySpawnFinalUpperChestAmbush(
            ServerLevel level,
            SessionState sessionState,
            ServerPlayer player
    ) {
        ScriptedReturnLockEvent scriptedEvent = sessionState.worldzero$scriptedReturnLockEvent;
        if (scriptedEvent == null
                || scriptedEvent.worldzero$dimension != level.dimension()
                || !scriptedEvent.worldzero$playerId.equals(player.getUUID())
                || scriptedEvent.worldzero$phase != ScriptedReturnLockPhase.WAITING_PLAYER_UPPER_CHEST_CLOSE) {
            return;
        }

        WorldZeroEchoEntity blackEcho = worldzero$spawnFinalAmbushEcho(level, player);
        if (blackEcho == null) {
            return;
        }

        scriptedEvent.worldzero$entityId = blackEcho.getUUID();
        scriptedEvent.worldzero$phase = ScriptedReturnLockPhase.FINAL_AMBUSH_WAIT_LOOK;
    }

    private static boolean worldzero$isScriptedUpperChest(
            ServerLevel level,
            ScriptedReturnLockEvent scriptedEvent,
            BlockPos pos
    ) {
        if (scriptedEvent.worldzero$upperChestPos == null) {
            return false;
        }

        for (BlockPos chestPos : worldzero$getConnectedChestPositions(level, scriptedEvent.worldzero$upperChestPos)) {
            if (chestPos.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean worldzero$isContainerEmpty(
            ServerLevel level,
            @Nullable BlockPos pos,
            @Nullable ServerPlayer player
    ) {
        if (pos == null) {
            return false;
        }

        for (BlockPos containerPos : worldzero$getConnectedChestPositions(level, pos)) {
            BlockEntity blockEntity = level.getBlockEntity(containerPos);
            if (blockEntity instanceof RandomizableContainerBlockEntity randomizableContainer && player != null) {
                randomizableContainer.unpackLootTable(player);
            }
            if (!(blockEntity instanceof Container container)) {
                continue;
            }

            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (!container.getItem(slot).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    @Nullable
    private static WorldZeroEchoEntity worldzero$spawnFinalAmbushEcho(ServerLevel level, ServerPlayer player) {
        Vec3 spawnPos = worldzero$findFinalAmbushSpawnPos(level, player);
        if (spawnPos == null) {
            return null;
        }

        WorldZeroEchoEntity blackEcho = WorldZeroEntities.WORLDZERO_BLACK_ECHO.get().create(level);
        if (blackEcho == null) {
            return null;
        }

        blackEcho.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, 0.0F, 0.0F);
        blackEcho.setNoGravity(true);
        blackEcho.setSilent(true);
        blackEcho.setDeltaMovement(Vec3.ZERO);
        blackEcho.setSprinting(false);
        worldzero$lookEntityAtPlayer(blackEcho, player);
        level.addFreshEntity(blackEcho);
        return blackEcho;
    }

    @Nullable
    private static Vec3 worldzero$findFinalAmbushSpawnPos(ServerLevel level, ServerPlayer player) {
        Vec3 lookVector = player.getViewVector(1.0F);
        Vec3 horizontalLook = new Vec3(lookVector.x, 0.0D, lookVector.z);
        if (horizontalLook.lengthSqr() < 0.0001D) {
            float yawRadians = player.getYRot() * ((float) Math.PI / 180.0F);
            horizontalLook = new Vec3(-Mth.sin(yawRadians), 0.0D, Mth.cos(yawRadians));
        } else {
            horizontalLook = horizontalLook.normalize();
        }

        Vec3 idealPos = player.position().subtract(horizontalLook.scale(WORLDZERO_FINAL_AMBUSH_DISTANCE_BLOCKS));
        BlockPos basePos = BlockPos.containing(idealPos.x, player.getY(), idealPos.z);
        int[][] offsets = new int[][]{
                {0, 0},
                {1, 0},
                {-1, 0},
                {0, 1},
                {0, -1},
                {1, 1},
                {1, -1},
                {-1, 1},
                {-1, -1}
        };
        for (int[] offset : offsets) {
            BlockPos standPos = worldzero$findStandableFeetPos(level, basePos.offset(offset[0], -1, offset[1]));
            if (standPos == null) {
                continue;
            }

            AABB spawnBox = WorldZeroEntities.WORLDZERO_BLACK_ECHO.get().getDimensions().makeBoundingBox(
                    standPos.getX() + 0.5D,
                    standPos.getY(),
                    standPos.getZ() + 0.5D
            );
            if (!level.noCollision(spawnBox)
                    || level.containsAnyLiquid(spawnBox)
                    || spawnBox.intersects(player.getBoundingBox())) {
                continue;
            }

            return new Vec3(standPos.getX() + 0.5D, standPos.getY(), standPos.getZ() + 0.5D);
        }
        return null;
    }

    private static List<BlockPos> worldzero$getConnectedChestPositions(ServerLevel level, BlockPos chestPos) {
        List<BlockPos> chestPositions = new ArrayList<>();
        chestPositions.add(chestPos.immutable());
        BlockState state = level.getBlockState(chestPos);
        if (!(state.getBlock() instanceof ChestBlock)
                || !state.hasProperty(ChestBlock.TYPE)
                || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return chestPositions;
        }

        BlockPos connectedPos = chestPos.relative(ChestBlock.getConnectedDirection(state));
        BlockState connectedState = level.getBlockState(connectedPos);
        if (connectedState.getBlock() instanceof ChestBlock) {
            chestPositions.add(connectedPos.immutable());
        }
        return chestPositions;
    }

    private static Vec3 worldzero$getAverageBlockCenter(List<BlockPos> positions) {
        if (positions.isEmpty()) {
            return Vec3.ZERO;
        }

        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        for (BlockPos pos : positions) {
            x += pos.getX() + 0.5D;
            y += pos.getY() + 0.5D;
            z += pos.getZ() + 0.5D;
        }

        double scale = 1.0D / positions.size();
        return new Vec3(x * scale, y * scale, z * scale);
    }

    private static void worldzero$playFootstep(ServerLevel level, BlockPos pos, Entity entity) {
        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);
        net.minecraft.world.level.block.SoundType soundType = belowState.getSoundType(level, belowPos, entity);
        level.playSound(
                null,
                pos.getX() + 0.5D,
                pos.getY(),
                pos.getZ() + 0.5D,
                soundType.getStepSound(),
                SoundSource.PLAYERS,
                0.32F,
                0.95F + level.random.nextFloat() * 0.08F
        );
    }

    private static void worldzero$trySpawnDoorBlackEcho(ServerLevel level, ServerPlayer player, BlockPos doorPos) {
        SessionState sessionState = WORLDZERO_SERVER_STATES.computeIfAbsent(level.getServer(), ignored -> new SessionState());
        HouseBadSaveData saveData = worldzero$getSaveData(level);
        if (worldzero$hasActiveAppearance(sessionState, player.getUUID(), level.dimension())) {
            return;
        }

        BlockPos spawnPos = worldzero$findDoorBlackEchoSpawnPos(level, doorPos);
        if (spawnPos == null) {
            return;
        }

        AABB spawnBox = WorldZeroEntities.WORLDZERO_BLACK_ECHO.get().getDimensions().makeBoundingBox(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D
        );
        if (!level.noCollision(spawnBox)
                || level.containsAnyLiquid(spawnBox)
                || spawnBox.intersects(player.getBoundingBox())) {
            return;
        }

        WorldZeroEchoEntity blackEcho = WorldZeroEntities.WORLDZERO_BLACK_ECHO.get().create(level);
        if (blackEcho == null) {
            return;
        }

        blackEcho.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
        blackEcho.setNoGravity(true);
        blackEcho.setDeltaMovement(0.0D, 0.0D, 0.0D);
        blackEcho.setSilent(true);
        worldzero$lookEntityAtPlayer(blackEcho, player);
        level.addFreshEntity(blackEcho);

        sessionState.worldzero$activeAppearances.add(new ActiveAppearance(
                level.dimension(),
                player.getUUID(),
                blackEcho.getUUID(),
                -1L,
                -1L
        ));
        saveData.worldzero$doorEchoConsumed.add(player.getUUID());
        saveData.setDirty();
    }

    private static boolean worldzero$hasActiveAppearance(
            SessionState sessionState,
            UUID playerId,
            ResourceKey<Level> dimension
    ) {
        for (ActiveAppearance activeAppearance : sessionState.worldzero$activeAppearances) {
            if (activeAppearance.worldzero$playerId.equals(playerId)
                    && activeAppearance.worldzero$dimension == dimension) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static BlockPos worldzero$findDoorBlackEchoSpawnPos(ServerLevel level, BlockPos doorPos) {
        BlockState doorState = level.getBlockState(doorPos);
        Direction facing = doorState.hasProperty(DoorBlock.FACING) ? doorState.getValue(DoorBlock.FACING) : Direction.SOUTH;
        BlockPos bestPos = null;
        int bestDistance = -1;

        for (Direction outward : new Direction[]{facing, facing.getOpposite()}) {
            StairAnchor stairAnchor = worldzero$findFurthestStairAnchor(level, doorPos, outward);
            if (stairAnchor == null) {
                continue;
            }

            BlockPos candidatePos = worldzero$findStandableFeetPos(level, stairAnchor.worldzero$stairPos.relative(outward));
            if (candidatePos == null) {
                continue;
            }

            if (stairAnchor.worldzero$distance > bestDistance) {
                bestDistance = stairAnchor.worldzero$distance;
                bestPos = candidatePos.immutable();
            }
        }

        return bestPos;
    }

    @Nullable
    private static StairAnchor worldzero$findFurthestStairAnchor(ServerLevel level, BlockPos doorPos, Direction outward) {
        StairAnchor best = null;
        for (int distance = 1; distance <= WORLDZERO_DOOR_BLACK_ECHO_SCAN_DISTANCE; distance++) {
            BlockPos linePos = doorPos.relative(outward, distance);
            for (int yOffset = -WORLDZERO_DOOR_BLACK_ECHO_Y_SCAN_DOWN; yOffset <= WORLDZERO_DOOR_BLACK_ECHO_Y_SCAN_UP; yOffset++) {
                BlockPos candidatePos = linePos.offset(0, yOffset, 0);
                if (!level.getBlockState(candidatePos).is(BlockTags.STAIRS)) {
                    continue;
                }

                if (best == null
                        || distance > best.worldzero$distance
                        || (distance == best.worldzero$distance && candidatePos.getY() < best.worldzero$stairPos.getY())) {
                    best = new StairAnchor(candidatePos.immutable(), distance);
                }
            }
        }
        return best;
    }

    @Nullable
    private static BlockPos worldzero$findStandableFeetPos(ServerLevel level, BlockPos floorBasePos) {
        BlockPos[] candidates = new BlockPos[]{
                floorBasePos.above(),
                floorBasePos,
                floorBasePos.below(),
                floorBasePos.above(2)
        };
        for (BlockPos candidate : candidates) {
            if (worldzero$isStandable(level, candidate)) {
                return candidate.immutable();
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos worldzero$resolveDoorLowerPos(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.DOORS) || !state.hasProperty(DoorBlock.HALF)) {
            return null;
        }

        return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.immutable() : pos.below().immutable();
    }

    private static boolean worldzero$isGlassLikeBlock(BlockState state) {
        if (state.isAir()) {
            return false;
        }

        String blockPath = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return blockPath.contains("glass") || blockPath.contains("pane");
    }

    private static boolean worldzero$isChestLikeBlock(BlockState state) {
        return state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.ENDER_CHEST);
    }

    private static void worldzero$setEntityYawPitch(Entity entity, float yaw, float pitch) {
        entity.setYRot(yaw);
        entity.setXRot(pitch);
        entity.setYHeadRot(yaw);
        entity.setYBodyRot(yaw);
        entity.yRotO = yaw;
        entity.xRotO = pitch;
    }

    private static void worldzero$lookEntityAtPlayer(Entity entity, ServerPlayer player) {
        double deltaX = player.getX() - entity.getX();
        double deltaY = player.getEyeY() - entity.getEyeY();
        double deltaZ = player.getZ() - entity.getZ();
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yaw = (float) (Mth.atan2(deltaZ, deltaX) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(deltaY, horizontalDistance) * (180.0D / Math.PI)));
        worldzero$setEntityYawPitch(entity, yaw, pitch);
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
        return lookVector.dot(directionToEcho.normalize()) >= WORLDZERO_DOOR_BLACK_ECHO_VIEW_DOT_THRESHOLD;
    }

    private static void worldzero$clearPlayerInventory(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        player.containerMenu.setCarried(ItemStack.EMPTY);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }

    private static void worldzero$restorePlayerInventory(ServerPlayer player, PlayerInventorySnapshot snapshot) {
        player.getInventory().clearContent();
        snapshot.worldzero$apply(player);
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        player.containerMenu.setCarried(ItemStack.EMPTY);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }

    private static void worldzero$applyHouseBadVitals(ServerPlayer player) {
        if (player == null) {
            return;
        }

        player.setHealth(player.getMaxHealth());
        player.setAbsorptionAmount(0.0F);
        FoodData foodData = player.getFoodData();
        foodData.setFoodLevel(20);
        foodData.setSaturation(20.0F);
        foodData.setExhaustion(0.0F);
    }

    private static AABB worldzero$getStructureBounds(TemplateInfo templateInfo) {
        BlockPos origin = WORLDZERO_BASE_ORIGIN;
        BlockPos max = origin.offset(
                templateInfo.worldzero$size.getX(),
                templateInfo.worldzero$size.getY(),
                templateInfo.worldzero$size.getZ()
        );
        return new AABB(origin, max);
    }

    @Nullable
    private static TemplateInfo worldzero$getTemplateInfo(ServerLevel level) {
        Optional<StructureTemplate> optionalTemplate = level.getStructureManager().get(WORLDZERO_HOUSE_BAD_STRUCTURE_ID);
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

    private static HouseBadSaveData worldzero$getSaveData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(HouseBadSaveData::load, HouseBadSaveData::new, WORLDZERO_SAVE_ID);
    }

    private static boolean worldzero$isBuildRestricted(@Nullable Player player) {
        return player != null && player.level().dimension() == WORLDZERO_HOUSE_BAD_LEVEL;
    }

    private static final class TemplateInfo {
        private final StructureTemplate worldzero$template;
        private final Vec3i worldzero$size;

        private TemplateInfo(StructureTemplate template, Vec3i size) {
            this.worldzero$template = template;
            this.worldzero$size = size;
        }
    }

    private static final class SessionState {
        @Nullable
        private StructureRuntime worldzero$structureRuntime;
        private final Set<ActiveAppearance> worldzero$activeAppearances = new HashSet<>();
        private final Map<UUID, VisitState> worldzero$visits = new HashMap<>();
        private final List<DelayedAction> worldzero$delayedActions = new ArrayList<>();
        private final List<ActiveOutsidePass> worldzero$outsidePasses = new ArrayList<>();
        @Nullable
        private ScriptedReturnLockEvent worldzero$scriptedReturnLockEvent;
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

    private static final class VisitState {
        private final long worldzero$enterTick;
        private boolean worldzero$doorCloseTriggered;
        private boolean worldzero$navigationTriggered;
        private int worldzero$previousNavigationSide;
        private long worldzero$outsideTicks;
        private boolean worldzero$outsideReached;
        private boolean worldzero$outsidePassTriggered;
        private BlockPos worldzero$lastInsidePos;
        private double worldzero$lastKnownX;
        private boolean worldzero$returnLockTriggered;

        private VisitState(long enterTick, BlockPos initialPos) {
            this.worldzero$enterTick = enterTick;
            this.worldzero$lastInsidePos = initialPos.immutable();
            this.worldzero$lastKnownX = initialPos.getX() + 0.5D;
        }
    }

    private static final class StructureRuntime {
        private final TemplateInfo worldzero$templateInfo;
        private final int worldzero$minX;
        private final int worldzero$maxX;
        private final int worldzero$minY;
        private final int worldzero$maxY;
        private final int worldzero$minZ;
        private final int worldzero$maxZ;
        private final int worldzero$coreMinX;
        private final int worldzero$coreMaxX;
        private final int worldzero$coreMinZ;
        private final int worldzero$coreMaxZ;
        private final boolean worldzero$navigationAxisX;
        private final int worldzero$navigationCenter;
        private final List<BlockPos> worldzero$windowBlocks;
        private final List<BlockPos> worldzero$insideStandablePositions;

        private StructureRuntime(
                TemplateInfo templateInfo,
                int minX,
                int maxX,
                int minY,
                int maxY,
                int minZ,
                int maxZ,
                int coreMinX,
                int coreMaxX,
                int coreMinZ,
                int coreMaxZ,
                boolean navigationAxisX,
                int navigationCenter,
                List<BlockPos> windowBlocks,
                List<BlockPos> insideStandablePositions
        ) {
            this.worldzero$templateInfo = templateInfo;
            this.worldzero$minX = minX;
            this.worldzero$maxX = maxX;
            this.worldzero$minY = minY;
            this.worldzero$maxY = maxY;
            this.worldzero$minZ = minZ;
            this.worldzero$maxZ = maxZ;
            this.worldzero$coreMinX = coreMinX;
            this.worldzero$coreMaxX = coreMaxX;
            this.worldzero$coreMinZ = coreMinZ;
            this.worldzero$coreMaxZ = coreMaxZ;
            this.worldzero$navigationAxisX = navigationAxisX;
            this.worldzero$navigationCenter = navigationCenter;
            this.worldzero$windowBlocks = windowBlocks;
            this.worldzero$insideStandablePositions = insideStandablePositions;
        }

        private boolean worldzero$isInsideCore(BlockPos pos) {
            return pos.getX() >= this.worldzero$coreMinX
                    && pos.getX() <= this.worldzero$coreMaxX
                    && pos.getY() >= this.worldzero$minY
                    && pos.getY() <= this.worldzero$maxY
                    && pos.getZ() >= this.worldzero$coreMinZ
                    && pos.getZ() <= this.worldzero$coreMaxZ;
        }
    }

    private enum DelayedActionType {
        DOOR_CLOSE,
        CHEST_CLOSE
    }

    private static final class DelayedAction {
        private final ResourceKey<Level> worldzero$dimension;
        private final UUID worldzero$playerId;
        private final DelayedActionType worldzero$type;
        private final BlockPos worldzero$pos;
        private final long worldzero$dueTick;

        private DelayedAction(
                ResourceKey<Level> dimension,
                UUID playerId,
                DelayedActionType type,
                BlockPos pos,
                long dueTick
        ) {
            this.worldzero$dimension = dimension;
            this.worldzero$playerId = playerId;
            this.worldzero$type = type;
            this.worldzero$pos = pos;
            this.worldzero$dueTick = dueTick;
        }
    }

    private static final class OutsidePassPlan {
        private final Vec3 worldzero$startPos;
        private final Vec3 worldzero$direction;
        private final double worldzero$distance;
        private final double worldzero$speed;
        private final float worldzero$yaw;

        private OutsidePassPlan(Vec3 startPos, Vec3 direction, double distance, double speed, float yaw) {
            this.worldzero$startPos = startPos;
            this.worldzero$direction = direction;
            this.worldzero$distance = distance;
            this.worldzero$speed = speed;
            this.worldzero$yaw = yaw;
        }
    }

    private static final class ActiveOutsidePass {
        private final ResourceKey<Level> worldzero$dimension;
        private final UUID worldzero$playerId;
        private final UUID worldzero$entityId;
        private final Vec3 worldzero$startPos;
        private final Vec3 worldzero$direction;
        private final double worldzero$distance;
        private final double worldzero$speed;
        private final float worldzero$yaw;
        private double worldzero$progress;
        private double worldzero$stepDistanceAccumulator;

        private ActiveOutsidePass(
                ResourceKey<Level> dimension,
                UUID playerId,
                UUID entityId,
                Vec3 startPos,
                Vec3 direction,
                double distance,
                double speed,
                float yaw
        ) {
            this.worldzero$dimension = dimension;
            this.worldzero$playerId = playerId;
            this.worldzero$entityId = entityId;
            this.worldzero$startPos = startPos;
            this.worldzero$direction = direction;
            this.worldzero$distance = distance;
            this.worldzero$speed = speed;
            this.worldzero$yaw = yaw;
        }
    }

    private static final class StairAnchor {
        private final BlockPos worldzero$stairPos;
        private final int worldzero$distance;

        private StairAnchor(BlockPos stairPos, int distance) {
            this.worldzero$stairPos = stairPos;
            this.worldzero$distance = distance;
        }
    }

    private enum ScriptedReturnLockPhase {
        WAITING_TO_START,
        WALK_TO_POINT_2,
        WALK_TO_POINT_3,
        WAITING_CHEST_CLOSE,
        WALK_TO_POINT_4,
        COMPLETED,
        WAITING_PLAYER_UPPER_CHEST_CLOSE,
        FINAL_AMBUSH_WAIT_LOOK,
        FINAL_AMBUSH_FREEZE
    }

    private static final class ScriptedReturnLockEvent {
        private final ResourceKey<Level> worldzero$dimension;
        private final UUID worldzero$playerId;
        @Nullable
        private final BlockPos worldzero$doorPos;
        @Nullable
        private final BlockPos worldzero$chestPos;
        @Nullable
        private final BlockPos worldzero$upperChestPos;
        @Nullable
        private final BlockPos worldzero$bedPos;
        private final List<BlockPos> worldzero$barrierPositions;
        private long worldzero$dueTick;
        @Nullable
        private UUID worldzero$entityId;
        private boolean worldzero$doorOpened;
        private boolean worldzero$barriersReleased;
        private ScriptedReturnLockPhase worldzero$phase = ScriptedReturnLockPhase.WAITING_TO_START;

        private ScriptedReturnLockEvent(
                ResourceKey<Level> dimension,
                UUID playerId,
                @Nullable BlockPos doorPos,
                @Nullable BlockPos chestPos,
                @Nullable BlockPos upperChestPos,
                @Nullable BlockPos bedPos,
                List<BlockPos> barrierPositions,
                long dueTick
        ) {
            this.worldzero$dimension = dimension;
            this.worldzero$playerId = playerId;
            this.worldzero$doorPos = doorPos;
            this.worldzero$chestPos = chestPos;
            this.worldzero$upperChestPos = upperChestPos;
            this.worldzero$bedPos = bedPos;
            this.worldzero$barrierPositions = barrierPositions;
            this.worldzero$dueTick = dueTick;
        }
    }

    private static final class HouseBadSaveData extends SavedData {
        private final Map<UUID, ReturnPoint> worldzero$returnPoints = new HashMap<>();
        private final Map<UUID, PlayerInventorySnapshot> worldzero$inventorySnapshots = new HashMap<>();
        private final Map<UUID, PlayerVitalsSnapshot> worldzero$vitalSnapshots = new HashMap<>();
        private final Set<UUID> worldzero$doorEchoConsumed = new HashSet<>();

        @Override
        public CompoundTag save(CompoundTag tag) {
            CompoundTag returnPointsTag = new CompoundTag();
            for (Map.Entry<UUID, ReturnPoint> entry : this.worldzero$returnPoints.entrySet()) {
                returnPointsTag.put(entry.getKey().toString(), entry.getValue().worldzero$save());
            }
            tag.put("ReturnPoints", returnPointsTag);

            CompoundTag inventorySnapshotsTag = new CompoundTag();
            for (Map.Entry<UUID, PlayerInventorySnapshot> entry : this.worldzero$inventorySnapshots.entrySet()) {
                inventorySnapshotsTag.put(entry.getKey().toString(), entry.getValue().worldzero$save());
            }
            tag.put("InventorySnapshots", inventorySnapshotsTag);

            CompoundTag vitalSnapshotsTag = new CompoundTag();
            for (Map.Entry<UUID, PlayerVitalsSnapshot> entry : this.worldzero$vitalSnapshots.entrySet()) {
                vitalSnapshotsTag.put(entry.getKey().toString(), entry.getValue().worldzero$save());
            }
            tag.put("VitalSnapshots", vitalSnapshotsTag);

            ListTag doorEchoConsumedTag = new ListTag();
            for (UUID playerId : this.worldzero$doorEchoConsumed) {
                CompoundTag playerTag = new CompoundTag();
                playerTag.putUUID("Player", playerId);
                doorEchoConsumedTag.add(playerTag);
            }
            tag.put("DoorEchoConsumed", doorEchoConsumedTag);
            return tag;
        }

        public static HouseBadSaveData load(CompoundTag tag) {
            HouseBadSaveData saveData = new HouseBadSaveData();
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

            CompoundTag inventorySnapshotsTag = tag.getCompound("InventorySnapshots");
            for (String key : inventorySnapshotsTag.getAllKeys()) {
                try {
                    PlayerInventorySnapshot snapshot = PlayerInventorySnapshot.worldzero$load(
                            inventorySnapshotsTag.getCompound(key)
                    );
                    if (snapshot != null) {
                        saveData.worldzero$inventorySnapshots.put(UUID.fromString(key), snapshot);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }

            CompoundTag vitalSnapshotsTag = tag.getCompound("VitalSnapshots");
            for (String key : vitalSnapshotsTag.getAllKeys()) {
                try {
                    PlayerVitalsSnapshot snapshot = PlayerVitalsSnapshot.worldzero$load(
                            vitalSnapshotsTag.getCompound(key)
                    );
                    if (snapshot != null) {
                        saveData.worldzero$vitalSnapshots.put(UUID.fromString(key), snapshot);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }

            ListTag doorEchoConsumedTag = tag.getList("DoorEchoConsumed", Tag.TAG_COMPOUND);
            for (int index = 0; index < doorEchoConsumedTag.size(); index++) {
                CompoundTag playerTag = doorEchoConsumedTag.getCompound(index);
                if (playerTag.hasUUID("Player")) {
                    saveData.worldzero$doorEchoConsumed.add(playerTag.getUUID("Player"));
                }
            }
            return saveData;
        }
    }

    private static final class PlayerVitalsSnapshot {
        private final float worldzero$health;
        private final float worldzero$absorptionAmount;
        private final int worldzero$foodLevel;
        private final float worldzero$saturationLevel;
        private final float worldzero$exhaustionLevel;

        private PlayerVitalsSnapshot(
                float health,
                float absorptionAmount,
                int foodLevel,
                float saturationLevel,
                float exhaustionLevel
        ) {
            this.worldzero$health = health;
            this.worldzero$absorptionAmount = absorptionAmount;
            this.worldzero$foodLevel = foodLevel;
            this.worldzero$saturationLevel = saturationLevel;
            this.worldzero$exhaustionLevel = exhaustionLevel;
        }

        private static PlayerVitalsSnapshot worldzero$fromPlayer(ServerPlayer player) {
            FoodData foodData = player.getFoodData();
            return new PlayerVitalsSnapshot(
                    player.getHealth(),
                    player.getAbsorptionAmount(),
                    foodData.getFoodLevel(),
                    foodData.getSaturationLevel(),
                    foodData.getExhaustionLevel()
            );
        }

        private CompoundTag worldzero$save() {
            CompoundTag tag = new CompoundTag();
            tag.putFloat("Health", this.worldzero$health);
            tag.putFloat("AbsorptionAmount", this.worldzero$absorptionAmount);
            tag.putInt("FoodLevel", this.worldzero$foodLevel);
            tag.putFloat("SaturationLevel", this.worldzero$saturationLevel);
            tag.putFloat("ExhaustionLevel", this.worldzero$exhaustionLevel);
            return tag;
        }

        private void worldzero$apply(ServerPlayer player) {
            int foodLevel = Mth.clamp(this.worldzero$foodLevel, 0, 20);
            float saturationLevel = Mth.clamp(this.worldzero$saturationLevel, 0.0F, (float) foodLevel);
            player.setAbsorptionAmount(Math.max(0.0F, this.worldzero$absorptionAmount));
            player.setHealth(Mth.clamp(this.worldzero$health, 0.0F, player.getMaxHealth()));
            FoodData foodData = player.getFoodData();
            foodData.setFoodLevel(foodLevel);
            foodData.setSaturation(saturationLevel);
            foodData.setExhaustion(Math.max(0.0F, this.worldzero$exhaustionLevel));
        }

        @Nullable
        private static PlayerVitalsSnapshot worldzero$load(CompoundTag tag) {
            if (!tag.contains("Health", Tag.TAG_FLOAT)
                    || !tag.contains("FoodLevel", Tag.TAG_INT)) {
                return null;
            }

            return new PlayerVitalsSnapshot(
                    tag.getFloat("Health"),
                    tag.getFloat("AbsorptionAmount"),
                    tag.getInt("FoodLevel"),
                    tag.getFloat("SaturationLevel"),
                    tag.getFloat("ExhaustionLevel")
            );
        }
    }

    private static final class PlayerInventorySnapshot {
        private final CompoundTag worldzero$tag;

        private PlayerInventorySnapshot(CompoundTag tag) {
            this.worldzero$tag = tag;
        }

        private static PlayerInventorySnapshot worldzero$fromPlayer(ServerPlayer player) {
            CompoundTag tag = new CompoundTag();
            tag.put("Inventory", player.getInventory().save(new ListTag()));
            tag.putInt("SelectedSlot", player.getInventory().selected);
            return new PlayerInventorySnapshot(tag);
        }

        private CompoundTag worldzero$save() {
            return this.worldzero$tag.copy();
        }

        private void worldzero$apply(ServerPlayer player) {
            ListTag inventoryTag = this.worldzero$tag.getList("Inventory", Tag.TAG_COMPOUND);
            player.getInventory().load(inventoryTag);
            player.getInventory().selected = Math.max(0, Math.min(8, this.worldzero$tag.getInt("SelectedSlot")));
        }

        @Nullable
        private static PlayerInventorySnapshot worldzero$load(CompoundTag tag) {
            if (!tag.contains("Inventory", Tag.TAG_LIST)) {
                return null;
            }

            return new PlayerInventorySnapshot(tag.copy());
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
            String dimensionId = tag.getString("Dimension");
            ResourceLocation location = ResourceLocation.tryParse(dimensionId);
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
