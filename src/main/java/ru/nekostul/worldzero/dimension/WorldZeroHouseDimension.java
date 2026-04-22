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
import net.minecraft.world.Container;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroHouseDimension {
    public static final ResourceKey<Level> WORLDZERO_HOUSE_LEVEL = ResourceKey.create(
            Registries.DIMENSION,
            new ResourceLocation(WorldZeroMod.MOD_ID, "house")
    );

    private static final ResourceLocation WORLDZERO_HOUSE_STRUCTURE_ID = new ResourceLocation(
            WorldZeroMod.MOD_ID,
            "house"
    );
    private static final String WORLDZERO_SAVE_ID = "worldzero_house_dimension";
    private static final BlockPos WORLDZERO_BASE_ORIGIN = new BlockPos(-32, 64, -32);
    private static final int WORLDZERO_SLEEP_COUNT_BEFORE_TRIGGER = 2;
    private static final int WORLDZERO_SLEEP_FADE_TICKS = 3 * 20;
    private static final int WORLDZERO_SCAN_RADIUS_HORIZONTAL = 16;
    private static final int WORLDZERO_SCAN_RADIUS_VERTICAL = 8;
    private static final int WORLDZERO_BARRIER_PADDING = 1;
    private static final int WORLDZERO_BARRIER_HEIGHT_PADDING = 2;
    private static final ChestLootEntry[] WORLDZERO_PRIMARY_CHEST_LOOT = new ChestLootEntry[]{
            new ChestLootEntry(Items.DIAMOND, 27),
            new ChestLootEntry(Items.DIAMOND, 11),
            new ChestLootEntry(Items.DIAMOND_SWORD, 1, 18, 42),
            new ChestLootEntry(Items.DIAMOND_PICKAXE, 1, 22, 48),
            new ChestLootEntry(Items.DIAMOND_AXE, 1, 16, 40),
            new ChestLootEntry(Items.DIAMOND_CHESTPLATE, 1, 10, 28),
            new ChestLootEntry(Items.IRON_INGOT, 64),
            new ChestLootEntry(Items.IRON_INGOT, 29),
            new ChestLootEntry(Items.GOLD_INGOT, 31),
            new ChestLootEntry(Items.REDSTONE, 64),
            new ChestLootEntry(Items.REDSTONE, 38),
            new ChestLootEntry(Items.LAPIS_LAZULI, 13),
            new ChestLootEntry(Items.COOKED_BEEF, 18),
            new ChestLootEntry(Items.BREAD, 12),
            new ChestLootEntry(Items.TORCH, 64),
            new ChestLootEntry(Items.TORCH, 37),
            new ChestLootEntry(Items.COAL, 43),
            new ChestLootEntry(Items.WATER_BUCKET, 1),
            new ChestLootEntry(Items.IRON_PICKAXE, 1, 74, 92),
            new ChestLootEntry(Items.IRON_SHOVEL, 1, 68, 88),
            new ChestLootEntry(Items.GRAVEL, 17),
            new ChestLootEntry(Items.DIORITE, 21),
            new ChestLootEntry(Items.DIRT, 14)
    };
    private static final ChestLootEntry[] WORLDZERO_SECONDARY_CHEST_LOOT = new ChestLootEntry[]{
            new ChestLootEntry(Items.IRON_PICKAXE, 1, 24, 52),
            new ChestLootEntry(Items.IRON_PICKAXE, 1, 47, 76),
            new ChestLootEntry(Items.STONE_PICKAXE, 1, 34, 72),
            new ChestLootEntry(Items.STONE_SHOVEL, 1, 41, 79),
            new ChestLootEntry(Items.BOW, 1, 58, 86),
            new ChestLootEntry(Items.COBBLESTONE, 64),
            new ChestLootEntry(Items.COBBLESTONE, 51),
            new ChestLootEntry(Items.COBBLESTONE, 17),
            new ChestLootEntry(Items.OAK_PLANKS, 43),
            new ChestLootEntry(Items.OAK_LOG, 22),
            new ChestLootEntry(Items.SAND, 19),
            new ChestLootEntry(Items.GLASS, 13),
            new ChestLootEntry(Items.CLAY_BALL, 21),
            new ChestLootEntry(Items.POTATO, 17),
            new ChestLootEntry(Items.CARROT, 9),
            new ChestLootEntry(Items.BEEF, 6),
            new ChestLootEntry(Items.BREAD, 4),
            new ChestLootEntry(Items.SADDLE, 1),
            new ChestLootEntry(Items.LEAD, 1),
            new ChestLootEntry(Items.MAP, 1),
            new ChestLootEntry(Items.MAP, 1),
            new ChestLootEntry(Items.WRITABLE_BOOK, 1),
            new ChestLootEntry(Items.DIRT, 6)
    };
    private static final Map<MinecraftServer, SessionState> WORLDZERO_SERVER_STATES = new WeakHashMap<>();

    private WorldZeroHouseDimension() {
    }

    @SubscribeEvent
    public static void worldzero$onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide()) {
            return;
        }

        if (!(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }

        MinecraftServer server = level.getServer();
        if (server == null || WorldZeroParalysisEvent.worldzero$isParalysisActive(server)) {
            return;
        }

        HouseSaveData saveData = worldzero$getSaveData(server);
        SessionState sessionState = WORLDZERO_SERVER_STATES.computeIfAbsent(server, ignored -> new SessionState());

        for (ServerPlayer player : level.players()) {
            if (player.serverLevel() != level) {
                continue;
            }

            UUID playerId = player.getUUID();
            SleepTracker tracker = sessionState.worldzero$sleepTrackers.computeIfAbsent(
                    playerId,
                    ignored -> new SleepTracker()
            );

            if (!player.isAlive() || player.isSpectator() || saveData.worldzero$completedPlayers.contains(playerId)) {
                tracker.worldzero$reset();
                continue;
            }

            if (!player.isSleeping()) {
                tracker.worldzero$reset();
                continue;
            }

            int sleepCount = saveData.worldzero$sleepCounts.getOrDefault(playerId, 0);
            if (!tracker.worldzero$currentSleepTracked && player.getSleepTimer() > 0) {
                tracker.worldzero$currentSleepTracked = true;
                if (sleepCount >= WORLDZERO_SLEEP_COUNT_BEFORE_TRIGGER) {
                    tracker.worldzero$houseTeleportPending = true;
                } else {
                    saveData.worldzero$sleepCounts.put(playerId, sleepCount + 1);
                    saveData.setDirty();
                }
            }

            if (tracker.worldzero$houseTeleportPending
                    && player.getSleepTimer() >= WORLDZERO_SLEEP_FADE_TICKS
                    && worldzero$startSleepDream(player)) {
                saveData.worldzero$completedPlayers.add(playerId);
                saveData.worldzero$sleepCounts.remove(playerId);
                saveData.setDirty();
                tracker.worldzero$reset();
            }
        }
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_SERVER_STATES.remove(event.getServer());
    }

    @SubscribeEvent
    public static void worldzero$onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().dimension() != WORLDZERO_HOUSE_LEVEL) {
            return;
        }

        BlockState state = event.getLevel().getBlockState(event.getPos());
        BlockEntity blockEntity = event.getLevel().getBlockEntity(event.getPos());
        if (state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.BEDS) || blockEntity instanceof SignBlockEntity) {
            event.setCanceled(true);
        }
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

    public static boolean worldzero$startSleepDream(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator() || player.level().dimension() != Level.OVERWORLD) {
            return false;
        }

        return worldzero$teleportPlayerToHouseInternal(player, true);
    }

    public static boolean worldzero$teleportPlayerToHouse(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return false;
        }

        return worldzero$teleportPlayerToHouseInternal(player, false);
    }

    public static boolean worldzero$returnPlayerFromHouse(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        HouseSaveData saveData = worldzero$getSaveData(server);
        ReturnPoint returnPoint = saveData.worldzero$returnPoints.remove(player.getUUID());
        PlayerInventorySnapshot inventorySnapshot = saveData.worldzero$inventorySnapshots.remove(player.getUUID());
        if (returnPoint == null && inventorySnapshot == null && player.serverLevel().dimension() != WORLDZERO_HOUSE_LEVEL) {
            return false;
        }

        saveData.setDirty();

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

        if (player.isSleeping()) {
            player.stopSleepInBed(false, true);
        }

        player.teleportTo(targetLevel, targetX, targetY, targetZ, targetYaw, targetPitch);
        player.clearSleepingPos();
        player.setPose(Pose.STANDING);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;

        if (inventorySnapshot != null) {
            worldzero$restorePlayerInventory(player, inventorySnapshot);
        }

        if (returnPoint != null && returnPoint.worldzero$setMorningOnReturn && targetLevel.dimension() == Level.OVERWORLD) {
            worldzero$setMorning(targetLevel);
        }

        return true;
    }

    public static void worldzero$acknowledgeMusicFinished(ServerPlayer player) {
        if (player == null || player.serverLevel().dimension() != WORLDZERO_HOUSE_LEVEL) {
            return;
        }

        worldzero$returnPlayerFromHouse(player);
    }

    private static boolean worldzero$teleportPlayerToHouseInternal(ServerPlayer player, boolean fromSleep) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        HouseSaveData saveData = worldzero$getSaveData(server);
        if (saveData.worldzero$returnPoints.containsKey(player.getUUID())
                || saveData.worldzero$inventorySnapshots.containsKey(player.getUUID())) {
            return false;
        }

        ServerLevel houseLevel = server.getLevel(WORLDZERO_HOUSE_LEVEL);
        if (houseLevel == null) {
            return false;
        }

        TemplateInfo templateInfo = worldzero$getTemplateInfo(houseLevel);
        if (templateInfo == null || !worldzero$prepareHouseLevel(houseLevel, templateInfo)) {
            return false;
        }

        BlockPos bedPos = worldzero$findPrimaryBedPos(houseLevel, templateInfo);
        BlockPos spawnPos = worldzero$findSpawnPosNearBed(houseLevel, bedPos, templateInfo);
        float spawnYaw = worldzero$getHouseSpawnYaw(houseLevel, bedPos);

        ReturnPoint returnPoint = new ReturnPoint(
                player.serverLevel().dimension(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot(),
                fromSleep
        );
        PlayerInventorySnapshot inventorySnapshot = PlayerInventorySnapshot.worldzero$fromPlayer(player);

        if (fromSleep && player.isSleeping()) {
            player.stopSleepInBed(false, true);
        }

        saveData.worldzero$returnPoints.put(player.getUUID(), returnPoint);
        saveData.worldzero$inventorySnapshots.put(player.getUUID(), inventorySnapshot);
        saveData.setDirty();

        worldzero$clearPlayerInventory(player);

        houseLevel.getChunkAt(spawnPos);
        player.teleportTo(houseLevel, spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, spawnYaw, 0.0F);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        return true;
    }

    private static boolean worldzero$prepareHouseLevel(ServerLevel level, TemplateInfo templateInfo) {
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
        worldzero$lockSigns(level, templateInfo);
        worldzero$clearLooseItems(level, templateInfo);
        worldzero$populateChests(level, templateInfo);
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

    private static void worldzero$placeBarrierIfAir(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).isAir()) {
            level.setBlock(pos, Blocks.BARRIER.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
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
            worldzero$placeBarrierIfAir(level, wallBase);
            worldzero$placeBarrierIfAir(level, wallBase.above());
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

    private static void worldzero$lockSigns(ServerLevel level, TemplateInfo templateInfo) {
        BlockPos origin = WORLDZERO_BASE_ORIGIN;
        for (int x = origin.getX(); x < origin.getX() + templateInfo.worldzero$size.getX(); x++) {
            for (int y = origin.getY(); y < origin.getY() + templateInfo.worldzero$size.getY(); y++) {
                for (int z = origin.getZ(); z < origin.getZ() + templateInfo.worldzero$size.getZ(); z++) {
                    BlockEntity blockEntity = level.getBlockEntity(new BlockPos(x, y, z));
                    if (blockEntity instanceof SignBlockEntity signBlockEntity) {
                        signBlockEntity.setWaxed(true);
                        signBlockEntity.setChanged();
                    }
                }
            }
        }
    }

    private static void worldzero$clearLooseItems(ServerLevel level, TemplateInfo templateInfo) {
        AABB bounds = worldzero$getStructureBounds(templateInfo).inflate(4.0D, 4.0D, 4.0D);
        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, bounds)) {
            itemEntity.discard();
        }
    }

    private static void worldzero$populateChests(ServerLevel level, TemplateInfo templateInfo) {
        List<ChestGroup> chestGroups = worldzero$collectChestGroups(level, templateInfo);
        chestGroups.sort(Comparator
                .comparingInt((ChestGroup group) -> group.worldzero$anchorPos.getZ())
                .thenComparingInt(group -> group.worldzero$anchorPos.getX())
                .thenComparingInt(group -> group.worldzero$anchorPos.getY()));

        for (int groupIndex = 0; groupIndex < chestGroups.size(); groupIndex++) {
            ChestLootEntry[] lootProfile = groupIndex == 0
                    ? WORLDZERO_PRIMARY_CHEST_LOOT
                    : WORLDZERO_SECONDARY_CHEST_LOOT;
            worldzero$fillChestGroup(level, chestGroups.get(groupIndex), lootProfile);
        }
    }

    private static List<ChestGroup> worldzero$collectChestGroups(ServerLevel level, TemplateInfo templateInfo) {
        List<ChestGroup> chestGroups = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        BlockPos origin = WORLDZERO_BASE_ORIGIN;
        for (int x = origin.getX(); x < origin.getX() + templateInfo.worldzero$size.getX(); x++) {
            for (int y = origin.getY(); y < origin.getY() + templateInfo.worldzero$size.getY(); y++) {
                for (int z = origin.getZ(); z < origin.getZ() + templateInfo.worldzero$size.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (visited.contains(pos)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(pos);
                    if (!worldzero$isChestLikeBlock(state) || !(level.getBlockEntity(pos) instanceof Container)) {
                        continue;
                    }

                    List<BlockPos> chestPositions = new ArrayList<>();
                    chestPositions.add(pos.immutable());
                    visited.add(pos.immutable());

                    if (state.getBlock() instanceof ChestBlock
                            && state.hasProperty(ChestBlock.TYPE)
                            && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                        BlockPos connectedPos = pos.relative(ChestBlock.getConnectedDirection(state));
                        if (worldzero$isChestLikeBlock(level.getBlockState(connectedPos))
                                && level.getBlockEntity(connectedPos) instanceof Container) {
                            chestPositions.add(connectedPos.immutable());
                            visited.add(connectedPos.immutable());
                        }
                    }

                    chestPositions.sort(Comparator
                            .comparingInt((BlockPos blockPos) -> blockPos.getZ())
                            .thenComparingInt(blockPos -> blockPos.getX())
                            .thenComparingInt(blockPos -> blockPos.getY()));
                    chestGroups.add(new ChestGroup(chestPositions));
                }
            }
        }

        return chestGroups;
    }

    private static void worldzero$fillChestGroup(ServerLevel level, ChestGroup chestGroup, ChestLootEntry[] lootProfile) {
        List<Container> containers = new ArrayList<>();
        int totalSlots = 0;
        for (BlockPos pos : chestGroup.worldzero$positions) {
            if (!(level.getBlockEntity(pos) instanceof Container container)) {
                continue;
            }

            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                container.setItem(slot, ItemStack.EMPTY);
            }
            container.setChanged();
            containers.add(container);
            totalSlots += container.getContainerSize();
        }

        if (containers.isEmpty() || totalSlots <= 0) {
            return;
        }

        List<ItemStack> lootStacks = new ArrayList<>();
        for (ChestLootEntry entry : lootProfile) {
            lootStacks.add(worldzero$createLootStack(entry, level));
        }

        List<Integer> slotOrder = new ArrayList<>(totalSlots);
        for (int slot = 0; slot < totalSlots; slot++) {
            slotOrder.add(slot);
        }
        java.util.Collections.shuffle(slotOrder, new java.util.Random(level.getSeed() ^ chestGroup.worldzero$anchorPos.asLong()));
        slotOrder = slotOrder.subList(0, Math.min(slotOrder.size(), lootStacks.size()));
        slotOrder.sort(Integer::compareTo);

        for (int index = 0; index < lootStacks.size() && index < slotOrder.size(); index++) {
            int combinedSlot = slotOrder.get(index);
            int remainingSlot = combinedSlot;
            for (Container container : containers) {
                if (remainingSlot < container.getContainerSize()) {
                    container.setItem(remainingSlot, lootStacks.get(index));
                    break;
                }
                remainingSlot -= container.getContainerSize();
            }
        }

        for (Container container : containers) {
            container.setChanged();
        }
    }

    private static ItemStack worldzero$createLootStack(ChestLootEntry entry, ServerLevel level) {
        ItemStack stack = new ItemStack(entry.worldzero$item, entry.worldzero$count);
        if (entry.worldzero$randomDamage && stack.isDamageableItem()) {
            int maxDamage = stack.getMaxDamage();
            int minDamage = Math.max(1, (maxDamage * entry.worldzero$minDamagePercent) / 100);
            int maxAppliedDamage = Math.max(minDamage, (maxDamage * entry.worldzero$maxDamagePercent) / 100);
            stack.setDamageValue(minDamage + level.random.nextInt(maxAppliedDamage - minDamage + 1));
        }
        return stack;
    }

    private static boolean worldzero$isChestLikeBlock(BlockState state) {
        if (state.isAir()) {
            return false;
        }

        String blockPath = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return blockPath.contains("chest") && !blockPath.contains("ender_chest");
    }

    private static final class ChestGroup {
        private final BlockPos worldzero$anchorPos;
        private final List<BlockPos> worldzero$positions;

        private ChestGroup(List<BlockPos> positions) {
            this.worldzero$positions = positions;
            this.worldzero$anchorPos = positions.get(0);
        }
    }

    private static final class ChestLootEntry {
        private final Item worldzero$item;
        private final int worldzero$count;
        private final boolean worldzero$randomDamage;
        private final int worldzero$minDamagePercent;
        private final int worldzero$maxDamagePercent;

        private ChestLootEntry(Item item, int count) {
            this(item, count, false, 0, 0);
        }

        private ChestLootEntry(Item item, int count, boolean randomDamage) {
            this(item, count, randomDamage, 10, 66);
        }

        private ChestLootEntry(Item item, int count, int minDamagePercent, int maxDamagePercent) {
            this(item, count, true, minDamagePercent, maxDamagePercent);
        }

        private ChestLootEntry(
                Item item,
                int count,
                boolean randomDamage,
                int minDamagePercent,
                int maxDamagePercent
        ) {
            this.worldzero$item = item;
            this.worldzero$count = count;
            this.worldzero$randomDamage = randomDamage;
            this.worldzero$minDamagePercent = minDamagePercent;
            this.worldzero$maxDamagePercent = maxDamagePercent;
        }
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

            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos candidate = bedPos.offset(dx, 0, dz);
                    if (worldzero$isStandable(level, candidate)) {
                        return candidate.immutable();
                    }
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

    private static float worldzero$getHouseSpawnYaw(ServerLevel level, @Nullable BlockPos bedPos) {
        if (bedPos == null) {
            return 0.0F;
        }

        BlockState bedState = level.getBlockState(bedPos);
        if (bedState.hasProperty(BedBlock.FACING)) {
            return bedState.getValue(BedBlock.FACING).getOpposite().toYRot();
        }

        return 0.0F;
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

    private static void worldzero$setMorning(ServerLevel level) {
        long dayTime = level.getDayTime();
        long nextMorning = ((dayTime / 24000L) + 1L) * 24000L + 1000L;
        level.setDayTime(nextMorning);
    }

    @Nullable
    private static TemplateInfo worldzero$getTemplateInfo(ServerLevel level) {
        Optional<StructureTemplate> optionalTemplate = level.getStructureManager().get(WORLDZERO_HOUSE_STRUCTURE_ID);
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

    private static HouseSaveData worldzero$getSaveData(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                HouseSaveData::load,
                HouseSaveData::new,
                WORLDZERO_SAVE_ID
        );
    }

    private static boolean worldzero$isBuildRestricted(@Nullable Player player) {
        return player != null && player.level().dimension() == WORLDZERO_HOUSE_LEVEL;
    }

    private static final class SessionState {
        private final Map<UUID, SleepTracker> worldzero$sleepTrackers = new HashMap<>();
    }

    private static final class SleepTracker {
        private boolean worldzero$currentSleepTracked;
        private boolean worldzero$houseTeleportPending;

        private void worldzero$reset() {
            this.worldzero$currentSleepTracked = false;
            this.worldzero$houseTeleportPending = false;
        }
    }

    private static final class TemplateInfo {
        private final StructureTemplate worldzero$template;
        private final Vec3i worldzero$size;

        private TemplateInfo(StructureTemplate template, Vec3i size) {
            this.worldzero$template = template;
            this.worldzero$size = size;
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
        private final boolean worldzero$setMorningOnReturn;

        private ReturnPoint(
                ResourceKey<Level> dimension,
                double x,
                double y,
                double z,
                float yaw,
                float pitch,
                boolean setMorningOnReturn
        ) {
            this.worldzero$dimension = dimension;
            this.worldzero$x = x;
            this.worldzero$y = y;
            this.worldzero$z = z;
            this.worldzero$yaw = yaw;
            this.worldzero$pitch = pitch;
            this.worldzero$setMorningOnReturn = setMorningOnReturn;
        }

        private CompoundTag worldzero$save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dimension", this.worldzero$dimension.location().toString());
            tag.putDouble("X", this.worldzero$x);
            tag.putDouble("Y", this.worldzero$y);
            tag.putDouble("Z", this.worldzero$z);
            tag.putFloat("Yaw", this.worldzero$yaw);
            tag.putFloat("Pitch", this.worldzero$pitch);
            tag.putBoolean("SetMorningOnReturn", this.worldzero$setMorningOnReturn);
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
                    tag.getFloat("Pitch"),
                    tag.getBoolean("SetMorningOnReturn")
            );
        }
    }

    private static final class HouseSaveData extends SavedData {
        private final Map<UUID, Integer> worldzero$sleepCounts = new HashMap<>();
        private final Set<UUID> worldzero$completedPlayers = new java.util.HashSet<>();
        private final Map<UUID, ReturnPoint> worldzero$returnPoints = new HashMap<>();
        private final Map<UUID, PlayerInventorySnapshot> worldzero$inventorySnapshots = new HashMap<>();

        @Override
        public CompoundTag save(CompoundTag tag) {
            CompoundTag sleepCountsTag = new CompoundTag();
            for (Map.Entry<UUID, Integer> entry : this.worldzero$sleepCounts.entrySet()) {
                sleepCountsTag.putInt(entry.getKey().toString(), entry.getValue());
            }
            tag.put("SleepCounts", sleepCountsTag);

            CompoundTag completedPlayersTag = new CompoundTag();
            for (UUID playerId : this.worldzero$completedPlayers) {
                completedPlayersTag.putBoolean(playerId.toString(), true);
            }
            tag.put("CompletedPlayers", completedPlayersTag);

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
            return tag;
        }

        private static HouseSaveData load(CompoundTag tag) {
            HouseSaveData saveData = new HouseSaveData();

            CompoundTag sleepCountsTag = tag.getCompound("SleepCounts");
            for (String key : sleepCountsTag.getAllKeys()) {
                try {
                    saveData.worldzero$sleepCounts.put(UUID.fromString(key), sleepCountsTag.getInt(key));
                } catch (IllegalArgumentException ignored) {
                }
            }

            CompoundTag completedPlayersTag = tag.getCompound("CompletedPlayers");
            for (String key : completedPlayersTag.getAllKeys()) {
                try {
                    if (completedPlayersTag.getBoolean(key)) {
                        saveData.worldzero$completedPlayers.add(UUID.fromString(key));
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }

            CompoundTag returnPointsTag = tag.getCompound("ReturnPoints");
            for (String key : returnPointsTag.getAllKeys()) {
                try {
                    ReturnPoint returnPoint = ReturnPoint.worldzero$load(returnPointsTag.getCompound(key));
                    if (returnPoint != null) {
                        saveData.worldzero$returnPoints.put(UUID.fromString(key), returnPoint);
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

            return saveData;
        }
    }
}
