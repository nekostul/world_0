package ru.nekostul.worldzero;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
    private static final Map<MinecraftServer, SessionState> WORLDZERO_SERVER_STATES = new WeakHashMap<>();

    private WorldZeroHouseBadDimension() {
    }

    @SubscribeEvent
    public static void worldzero$onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != WORLDZERO_HOUSE_BAD_LEVEL
                || !(event.getEntity() instanceof ServerPlayer player)
                || player.isSpectator()) {
            return;
        }

        HouseBadSaveData saveData = worldzero$getSaveData(level);
        if (saveData.worldzero$doorEchoConsumed.contains(player.getUUID())) {
            return;
        }

        BlockPos doorPos = worldzero$resolveDoorLowerPos(level, event.getPos());
        if (doorPos == null) {
            return;
        }

        BlockState doorState = level.getBlockState(doorPos);
        if (!doorState.hasProperty(DoorBlock.OPEN) || doorState.getValue(DoorBlock.OPEN)) {
            return;
        }

        worldzero$trySpawnDoorBlackEcho(level, player, doorPos);
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
    public static void worldzero$onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.level instanceof ServerLevel level)
                || level.dimension() != WORLDZERO_HOUSE_BAD_LEVEL) {
            return;
        }

        SessionState sessionState = WORLDZERO_SERVER_STATES.get(level.getServer());
        if (sessionState == null || sessionState.worldzero$activeAppearances.isEmpty()) {
            return;
        }

        long gameTime = level.getGameTime();
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
                || saveData.worldzero$inventorySnapshots.containsKey(player.getUUID())) {
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
        saveData.worldzero$doorEchoConsumed.remove(player.getUUID());
        saveData.setDirty();

        worldzero$clearPlayerInventory(player);
        BlockPos bedPos = worldzero$findPrimaryBedPos(houseBadLevel, templateInfo);
        BlockPos spawnPos = worldzero$findSpawnPosNearBed(houseBadLevel, bedPos, templateInfo);
        float spawnYaw = worldzero$getSpawnYaw(houseBadLevel, bedPos);
        houseBadLevel.getChunkAt(spawnPos);
        player.teleportTo(houseBadLevel, spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, spawnYaw, 0.0F);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
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
        saveData.worldzero$doorEchoConsumed.remove(player.getUUID());
        saveData.setDirty();

        if (returnPoint == null
                && inventorySnapshot == null
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

    private static void worldzero$lookEntityAtPlayer(Entity entity, ServerPlayer player) {
        double deltaX = player.getX() - entity.getX();
        double deltaY = player.getEyeY() - entity.getEyeY();
        double deltaZ = player.getZ() - entity.getZ();
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yaw = (float) (Mth.atan2(deltaZ, deltaX) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(deltaY, horizontalDistance) * (180.0D / Math.PI)));
        entity.setYRot(yaw);
        entity.setXRot(pitch);
        entity.setYHeadRot(yaw);
        entity.setYBodyRot(yaw);
        entity.yRotO = yaw;
        entity.xRotO = pitch;
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
        private final Set<ActiveAppearance> worldzero$activeAppearances = new HashSet<>();
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

    private static final class StairAnchor {
        private final BlockPos worldzero$stairPos;
        private final int worldzero$distance;

        private StairAnchor(BlockPos stairPos, int distance) {
            this.worldzero$stairPos = stairPos;
            this.worldzero$distance = distance;
        }
    }

    private static final class HouseBadSaveData extends SavedData {
        private final Map<UUID, ReturnPoint> worldzero$returnPoints = new HashMap<>();
        private final Map<UUID, PlayerInventorySnapshot> worldzero$inventorySnapshots = new HashMap<>();
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
