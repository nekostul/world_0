package ru.nekostul.worldzero.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;
import ru.nekostul.worldzero.achievement.WorldZeroAdvancementTriggers;
import ru.nekostul.worldzero.client.controller.WorldZeroHouseClientController;
import ru.nekostul.worldzero.entity.WorldZeroEntities;
import ru.nekostul.worldzero.entity.WorldZeroHouseEchoEntity;
import ru.nekostul.worldzero.event.paralysis.WorldZeroParalysisEvent;
import ru.nekostul.worldzero.network.WorldZeroNetwork;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
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
    private static final int WORLDZERO_POST_KORIDOR_SLEEP_COUNT_BEFORE_TRIGGER = 2;
    private static final int WORLDZERO_POST_KORIDOR_HOUSE_BAD_DELAY_TICKS = 65;
    private static final int WORLDZERO_SCAN_RADIUS_HORIZONTAL = 16;
    private static final int WORLDZERO_SCAN_RADIUS_VERTICAL = 8;
    private static final int WORLDZERO_BARRIER_PADDING = 1;
    private static final int WORLDZERO_BARRIER_HEIGHT_PADDING = 2;
    private static final int WORLDZERO_RESTORATION_SCENE_TIMEOUT_TICKS = 20 * 45;
    private static final int WORLDZERO_RESTORATION_CHAT_START_DELAY_TICKS = 30;
    private static final int WORLDZERO_RESTORATION_CHAT_DELAY_MIN_TICKS = 2 * 20;
    private static final int WORLDZERO_RESTORATION_CHAT_DELAY_MAX_TICKS = 4 * 20;
    private static final int WORLDZERO_RESTORATION_ECHO_START_DELAY_AFTER_CHAT_TICKS = 20;
    private static final String WORLDZERO_RESTORATION_CHAT_SANEK_NAME = "sanek0001";
    private static final String WORLDZERO_RESTORATION_CHAT_LINE_KEY_PREFIX = "message.worldzero.house.restoration.line.";
    private static final BlockPos WORLDZERO_RESTORATION_ROUTE_START = new BlockPos(-16, 73, -21);
    private static final List<BlockPos> WORLDZERO_RESTORATION_ROUTE_POINTS = List.of(
            new BlockPos(-16, 73, -19),
            new BlockPos(-19, 73, -19),
            new BlockPos(-19, 73, -4)
    );
    private static final List<BlockPos> WORLDZERO_RESTORATION_ROUTE_DOORS = List.of(
            new BlockPos(-17, 73, -20),
            new BlockPos(-17, 73, -19)
    );
    private static final int WORLDZERO_RESTORATION_ROUTE_DOOR_OPEN_STEP = 1;
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

    private enum HouseVisitMode {
        NORMAL((byte) 1, WorldZeroHouseClientController.WORLDZERO_MODE_MUSIC),
        RESTORATION((byte) 2, WorldZeroHouseClientController.WORLDZERO_MODE_SILENT);

        private final byte worldzero$saveId;
        private final byte worldzero$clientMode;

        HouseVisitMode(byte saveId, byte clientMode) {
            this.worldzero$saveId = saveId;
            this.worldzero$clientMode = clientMode;
        }

        @Nullable
        private static HouseVisitMode worldzero$fromSaveId(byte saveId) {
            for (HouseVisitMode value : values()) {
                if (value.worldzero$saveId == saveId) {
                    return value;
                }
            }
            return null;
        }
    }

    private WorldZeroHouseDimension() {
    }

    @SubscribeEvent
    public static void worldzero$onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide()) {
            return;
        }

        if (!(event.level instanceof ServerLevel level)) {
            return;
        }

        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }

        if (level.dimension() == WORLDZERO_HOUSE_LEVEL) {
            worldzero$tickRestorationScenes(level, server);
            return;
        }

        if (level.dimension() != Level.OVERWORLD) {
            return;
        }

        if (WorldZeroParalysisEvent.worldzero$isParalysisActive(server)) {
            return;
        }

        HouseSaveData saveData = worldzero$getSaveData(server);
        SessionState sessionState = WORLDZERO_SERVER_STATES.computeIfAbsent(server, ignored -> new SessionState());

        for (ServerPlayer player : new ArrayList<>(level.players())) {
            if (player.serverLevel() != level) {
                continue;
            }

            UUID playerId = player.getUUID();
            SleepTracker tracker = sessionState.worldzero$sleepTrackers.computeIfAbsent(
                    playerId,
                    ignored -> new SleepTracker()
            );
            boolean restorationReady = worldzero$hasRestorationDamage(saveData, playerId)
                    && saveData.worldzero$restorationPendingPlayers.contains(playerId);

            if (!player.isAlive() || player.isSpectator()) {
                tracker.worldzero$reset();
                continue;
            }

            int postKoridorSleepCount = saveData.worldzero$postKoridorSleepCounts.getOrDefault(playerId, -1);
            if (postKoridorSleepCount >= 0) {
                if (!player.isSleeping()) {
                    tracker.worldzero$reset();
                    continue;
                }

                if (!tracker.worldzero$currentSleepTracked && player.getSleepTimer() > 0) {
                    tracker.worldzero$currentSleepTracked = true;
                    if (postKoridorSleepCount >= WORLDZERO_POST_KORIDOR_SLEEP_COUNT_BEFORE_TRIGGER) {
                        tracker.worldzero$houseBadTeleportPending = true;
                    } else {
                        saveData.worldzero$postKoridorSleepCounts.put(playerId, postKoridorSleepCount + 1);
                        saveData.setDirty();
                    }
                }

                if (tracker.worldzero$houseBadTeleportPending
                        && player.getSleepTimer() >= WORLDZERO_POST_KORIDOR_HOUSE_BAD_DELAY_TICKS
                        && WorldZeroHouseBadDimension.worldzero$teleportPlayerToHouseBad(player)) {
                    saveData.worldzero$postKoridorSleepCounts.remove(playerId);
                    saveData.setDirty();
                    tracker.worldzero$reset();
                }
                continue;
            }

            if (!player.isSleeping()) {
                tracker.worldzero$reset();
                continue;
            }

            int sleepCount = saveData.worldzero$sleepCounts.getOrDefault(playerId, 0);
            if (!tracker.worldzero$currentSleepTracked && player.getSleepTimer() > 0) {
                tracker.worldzero$currentSleepTracked = true;
                if (restorationReady) {
                    tracker.worldzero$restorationTeleportPending = true;
                } else if (saveData.worldzero$completedPlayers.contains(playerId)) {
                    tracker.worldzero$houseTeleportPending = false;
                } else if (sleepCount >= WORLDZERO_SLEEP_COUNT_BEFORE_TRIGGER) {
                    tracker.worldzero$houseTeleportPending = true;
                } else {
                    saveData.worldzero$sleepCounts.put(playerId, sleepCount + 1);
                    saveData.setDirty();
                }
            }

            if (tracker.worldzero$restorationTeleportPending
                    && player.getSleepTimer() >= WORLDZERO_SLEEP_FADE_TICKS
                    && worldzero$startRestorationDream(player)) {
                saveData.worldzero$restorationPendingPlayers.remove(playerId);
                saveData.setDirty();
                tracker.worldzero$reset();
                continue;
            }

            if (saveData.worldzero$completedPlayers.contains(playerId)) {
                continue;
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

    @SubscribeEvent
    public static void worldzero$onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        if (player.level().dimension() != WORLDZERO_HOUSE_LEVEL) {
            return;
        }

        if (!worldzero$isFieldDamageAllowed(player, event.getPos(), event.getState())) {
            event.setCanceled(true);
            return;
        }

        worldzero$recordTrampleDamage(player, level, event.getPos(), event.getState());
        SessionState sessionState = WORLDZERO_SERVER_STATES.computeIfAbsent(player.getServer(), ignored -> new SessionState());
        sessionState.worldzero$lastTrampleMarkers.put(
                player.getUUID(),
                new TrampleMarker(level.getGameTime(), event.getPos())
        );
    }

    @SubscribeEvent
    public static void worldzero$onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getDistance() <= 0.5F) {
            return;
        }

        if (player.level().dimension() != WORLDZERO_HOUSE_LEVEL || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        SessionState sessionState = WORLDZERO_SERVER_STATES.computeIfAbsent(player.getServer(), ignored -> new SessionState());

        BlockPos[] candidates = new BlockPos[]{
                player.blockPosition(),
                player.blockPosition().below()
        };
        for (BlockPos candidate : candidates) {
            BlockState state = level.getBlockState(candidate);
            if (!(state.getBlock() instanceof FarmBlock)) {
                continue;
            }
            if (!worldzero$isFieldDamageAllowed(player, candidate, state)) {
                return;
            }
            TrampleMarker marker = sessionState.worldzero$lastTrampleMarkers.get(player.getUUID());
            if (marker != null
                    && marker.worldzero$gameTime == level.getGameTime()
                    && marker.worldzero$pos.equals(candidate)) {
                return;
            }
            worldzero$applyFallbackTrampleDamage(player, level, candidate, state);
            return;
        }
    }

    public static boolean worldzero$startSleepDream(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator() || player.level().dimension() != Level.OVERWORLD) {
            return false;
        }

        return worldzero$teleportPlayerToHouseInternal(player, true, HouseVisitMode.NORMAL);
    }

    public static boolean worldzero$startRestorationDream(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator() || player.level().dimension() != Level.OVERWORLD) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        HouseSaveData saveData = worldzero$getSaveData(server);
        if (!worldzero$hasRestorationDamage(saveData, player.getUUID())) {
            return false;
        }

        return worldzero$teleportPlayerToHouseInternal(player, true, HouseVisitMode.RESTORATION);
    }

    public static boolean worldzero$teleportPlayerToHouse(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return false;
        }

        return worldzero$teleportPlayerToHouseInternal(player, false, HouseVisitMode.NORMAL);
    }

    public static void worldzero$unlockHouseBadAfterKoridor(ServerPlayer player) {
        if (player == null) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        UUID playerId = player.getUUID();
        HouseSaveData saveData = worldzero$getSaveData(server);
        saveData.worldzero$postKoridorSleepCounts.put(playerId, 0);
        saveData.worldzero$sleepCounts.remove(playerId);
        saveData.setDirty();

        SessionState sessionState = WORLDZERO_SERVER_STATES.computeIfAbsent(server, ignored -> new SessionState());
        SleepTracker tracker = sessionState.worldzero$sleepTrackers.get(playerId);
        if (tracker != null) {
            tracker.worldzero$reset();
        }
    }

    public static boolean worldzero$triggerRestorationDreamNow(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator() || player.level().dimension() != Level.OVERWORLD) {
            return false;
        }

        return worldzero$startRestorationDream(player);
    }

    public static boolean worldzero$triggerRestorationDreamDemoNow(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator() || player.level().dimension() != Level.OVERWORLD) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        HouseSaveData saveData = worldzero$getSaveData(server);
        if (!worldzero$hasRestorationDamage(saveData, player.getUUID())
                && !worldzero$seedDebugRestorationDamage(server, saveData, player.getUUID())) {
            return false;
        }

        saveData.worldzero$restorationPendingPlayers.add(player.getUUID());
        saveData.setDirty();
        return worldzero$startRestorationDream(player);
    }

    public static boolean worldzero$returnPlayerFromHouse(ServerPlayer player) {
        return worldzero$returnPlayerFromHouseInternal(player, false);
    }

    private static boolean worldzero$finishRestorationDream(ServerPlayer player) {
        return worldzero$returnPlayerFromHouseInternal(player, true);
    }

    private static boolean worldzero$returnPlayerFromHouseInternal(ServerPlayer player, boolean clearRestorationDamage) {
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
        HouseVisitMode visitMode = saveData.worldzero$visitModes.remove(player.getUUID());
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

        ServerLevel currentLevel = player.serverLevel();
        SessionState sessionState = WORLDZERO_SERVER_STATES.get(server);
        if (sessionState != null) {
            RestorationSceneState sceneState = sessionState.worldzero$restorationScenes.remove(player.getUUID());
            if (sceneState != null && currentLevel.dimension() == WORLDZERO_HOUSE_LEVEL) {
                Entity echo = currentLevel.getEntity(sceneState.worldzero$echoEntityId);
                if (echo != null) {
                    echo.discard();
                }
            }
        }

        player.teleportTo(targetLevel, targetX, targetY, targetZ, targetYaw, targetPitch);
        player.clearSleepingPos();
        player.setPose(Pose.STANDING);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;

        if (inventorySnapshot != null) {
            worldzero$restorePlayerInventory(player, inventorySnapshot);
        }

        if (visitMode == HouseVisitMode.RESTORATION) {
            WorldZeroNetwork.sendFreezeEnd(player);
            if (clearRestorationDamage) {
                saveData.worldzero$restorationEntries.remove(player.getUUID());
                saveData.worldzero$restorationPendingPlayers.remove(player.getUUID());
                saveData.setDirty();
            } else if (worldzero$hasRestorationDamage(saveData, player.getUUID())) {
                saveData.worldzero$restorationPendingPlayers.add(player.getUUID());
                saveData.setDirty();
            }
        } else if (visitMode == HouseVisitMode.NORMAL && worldzero$hasRestorationDamage(saveData, player.getUUID())) {
            saveData.worldzero$restorationPendingPlayers.add(player.getUUID());
            saveData.setDirty();
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

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        HouseSaveData saveData = worldzero$getSaveData(server);
        if (saveData.worldzero$visitModes.get(player.getUUID()) != HouseVisitMode.NORMAL) {
            return;
        }

        worldzero$returnPlayerFromHouse(player);
    }

    private static boolean worldzero$teleportPlayerToHouseInternal(
            ServerPlayer player,
            boolean fromSleep,
            HouseVisitMode visitMode
    ) {
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

        List<RestorationEntry> restorationEntries = visitMode == HouseVisitMode.RESTORATION
                ? worldzero$getRestorationEntries(saveData, player.getUUID())
                : java.util.Collections.emptyList();
        RestorationDreamPlan restorationPlan = null;
        if (visitMode == HouseVisitMode.RESTORATION) {
            if (restorationEntries.isEmpty()) {
                return false;
            }
            worldzero$applyRestorationDamageState(houseLevel, restorationEntries);
            restorationPlan = worldzero$createRestorationDreamPlan(houseLevel, templateInfo, restorationEntries);
            if (restorationPlan == null) {
                return false;
            }
        }

        BlockPos bedPos = worldzero$findPrimaryBedPos(houseLevel, templateInfo);
        BlockPos spawnPos = restorationPlan != null
                ? restorationPlan.worldzero$playerPos
                : worldzero$findSpawnPosNearBed(houseLevel, bedPos, templateInfo);
        float spawnYaw = restorationPlan != null
                ? restorationPlan.worldzero$playerYaw
                : worldzero$getHouseSpawnYaw(houseLevel, bedPos);
        float spawnPitch = restorationPlan != null ? restorationPlan.worldzero$playerPitch : 0.0F;

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
        saveData.worldzero$visitModes.put(player.getUUID(), visitMode);
        saveData.setDirty();

        worldzero$clearPlayerInventory(player);
        WorldZeroNetwork.sendHouseClientMode(player, visitMode.worldzero$clientMode);

        houseLevel.getChunkAt(spawnPos);
        player.teleportTo(houseLevel, spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, spawnYaw, spawnPitch);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        if (restorationPlan != null && !worldzero$startRestorationScene(player, houseLevel, restorationPlan)) {
            return worldzero$returnPlayerFromHouseInternal(player, false);
        }
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

    private static void worldzero$tickRestorationScenes(ServerLevel level, MinecraftServer server) {
        SessionState sessionState = WORLDZERO_SERVER_STATES.get(server);
        if (sessionState == null || sessionState.worldzero$restorationScenes.isEmpty()) {
            return;
        }

        List<UUID> playersToWake = new ArrayList<>();
        Set<UUID> playersToCompleteRestoration = new HashSet<>();
        List<UUID> playersToDrop = new ArrayList<>();
        for (Map.Entry<UUID, RestorationSceneState> entry : sessionState.worldzero$restorationScenes.entrySet()) {
            UUID playerId = entry.getKey();
            RestorationSceneState sceneState = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || !player.isAlive() || player.isSpectator()) {
                playersToDrop.add(playerId);
                continue;
            }

            if (player.serverLevel() != level || player.level().dimension() != WORLDZERO_HOUSE_LEVEL) {
                playersToDrop.add(playerId);
                continue;
            }

            player.setDeltaMovement(0.0D, 0.0D, 0.0D);
            player.fallDistance = 0.0F;
            sceneState.worldzero$timeoutTicks--;
            if (sceneState.worldzero$timeoutTicks <= 0) {
                playersToWake.add(playerId);
                continue;
            }

            worldzero$tickRestorationChat(level, player, sceneState);

            Entity echo = level.getEntity(sceneState.worldzero$echoEntityId);
            if (echo == null || !echo.isAlive()) {
                if (sceneState.worldzero$spawnGraceTicks > 0) {
                    sceneState.worldzero$spawnGraceTicks--;
                }
                continue;
            }
            sceneState.worldzero$spawnGraceTicks = 0;
            if (echo instanceof WorldZeroHouseEchoEntity houseEcho
                    && houseEcho.worldzero$isFarmRestorationReadyToWake()) {
                playersToWake.add(playerId);
                playersToCompleteRestoration.add(playerId);
            }
        }

        for (UUID playerId : playersToDrop) {
            sessionState.worldzero$restorationScenes.remove(playerId);
        }

        for (UUID playerId : playersToWake) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                boolean finished = worldzero$finishRestorationDream(player);
                if (finished && playersToCompleteRestoration.contains(playerId)) {
                    WorldZeroAdvancementTriggers.grantNotOurFarm(player);
                }
            } else {
                sessionState.worldzero$restorationScenes.remove(playerId);
            }
        }
    }

    private static void worldzero$tickRestorationChat(
            ServerLevel level,
            ServerPlayer player,
            RestorationSceneState sceneState
    ) {
        if (sceneState.worldzero$chatMessageIndex >= sceneState.worldzero$chatTicks.length
                || level.getGameTime() < sceneState.worldzero$chatTicks[sceneState.worldzero$chatMessageIndex]) {
            return;
        }

        worldzero$sendRestorationChatLine(player, sceneState.worldzero$chatMessageIndex);
        sceneState.worldzero$chatMessageIndex++;
    }

    private static void worldzero$sendRestorationChatLine(ServerPlayer player, int messageIndex) {
        String playerName = player.getGameProfile().getName();
        switch (messageIndex) {
            case 0, 2 -> WorldZeroNetwork.sendLocalizedChatLine(
                    player,
                    playerName,
                    WORLDZERO_RESTORATION_CHAT_LINE_KEY_PREFIX + messageIndex
            );
            case 1, 3 -> WorldZeroNetwork.sendLocalizedChatLine(
                    player,
                    WORLDZERO_RESTORATION_CHAT_SANEK_NAME,
                    WORLDZERO_RESTORATION_CHAT_LINE_KEY_PREFIX + messageIndex
            );
            default -> {
            }
        }
    }

    private static boolean worldzero$startRestorationScene(
            ServerPlayer player,
            ServerLevel houseLevel,
            RestorationDreamPlan plan
    ) {
        WorldZeroHouseEchoEntity echo = WorldZeroEntities.WORLDZERO_HOUSE_ECHO.get().create(houseLevel);
        if (echo == null) {
            return false;
        }

        long[] chatTicks = worldzero$createRestorationChatSchedule(houseLevel);
        int farmStartDelayTicks = (int) Math.max(
                0L,
                chatTicks[chatTicks.length - 1] + WORLDZERO_RESTORATION_ECHO_START_DELAY_AFTER_CHAT_TICKS - houseLevel.getGameTime()
        );
        echo.moveTo(
                plan.worldzero$echoSpawnPos.getX() + 0.5D,
                plan.worldzero$echoSpawnPos.getY(),
                plan.worldzero$echoSpawnPos.getZ() + 0.5D,
                plan.worldzero$playerYaw,
                0.0F
        );
        echo.worldzero$configureFarmRestoration(
                player.getUUID(),
                plan.worldzero$tillTargets,
                plan.worldzero$plantTargets,
                plan.worldzero$approachTargets,
                plan.worldzero$doorTargets,
                plan.worldzero$doorOpenStep,
                farmStartDelayTicks
        );
        houseLevel.addFreshEntity(echo);

        int timeoutTicks = worldzero$getRestorationSceneTimeoutTicks(plan);
        SessionState sessionState = WORLDZERO_SERVER_STATES.computeIfAbsent(player.getServer(), ignored -> new SessionState());
        sessionState.worldzero$restorationScenes.put(
                player.getUUID(),
                new RestorationSceneState(
                        echo.getId(),
                        timeoutTicks,
                        chatTicks
                )
        );
        WorldZeroNetwork.sendFreezeStart(
                player,
                timeoutTicks,
                -1,
                plan.worldzero$playerYaw,
                plan.worldzero$playerPitch
        );
        return true;
    }

    private static int worldzero$getRestorationSceneTimeoutTicks(RestorationDreamPlan plan) {
        int actionCount = plan.worldzero$tillTargets.size() + plan.worldzero$plantTargets.size();
        return Math.max(WORLDZERO_RESTORATION_SCENE_TIMEOUT_TICKS, 20 * 10 + actionCount * 30);
    }

    private static long[] worldzero$createRestorationChatSchedule(ServerLevel level) {
        long[] chatTicks = new long[4];
        chatTicks[0] = level.getGameTime() + WORLDZERO_RESTORATION_CHAT_START_DELAY_TICKS;
        for (int index = 1; index < chatTicks.length; index++) {
            chatTicks[index] = chatTicks[index - 1] + level.random.nextInt(
                    WORLDZERO_RESTORATION_CHAT_DELAY_MAX_TICKS - WORLDZERO_RESTORATION_CHAT_DELAY_MIN_TICKS + 1
            ) + WORLDZERO_RESTORATION_CHAT_DELAY_MIN_TICKS;
        }
        return chatTicks;
    }

    private static void worldzero$applyRestorationDamageState(ServerLevel level, List<RestorationEntry> entries) {
        for (RestorationEntry entry : entries) {
            if (entry.worldzero$plantBroken) {
                level.setBlock(
                        entry.worldzero$getPlantPos(),
                        Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
                );
            }
            if (entry.worldzero$groundBroken) {
                level.setBlock(
                        entry.worldzero$groundPos,
                        Blocks.DIRT.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
                );
            }
        }
    }

    @Nullable
    private static RestorationDreamPlan worldzero$createRestorationDreamPlan(
            ServerLevel level,
            TemplateInfo templateInfo,
            List<RestorationEntry> entries
    ) {
        if (entries.isEmpty()) {
            return null;
        }

        BlockPos viewCenter = worldzero$getRestorationCenter(entries);
        BlockPos playerPos = worldzero$findRestorationViewPos(level, templateInfo, viewCenter);
        if (playerPos == null) {
            return null;
        }

        Vec3 lookTarget = Vec3.atCenterOf(viewCenter);
        float[] yawPitch = worldzero$computeLookRotation(playerPos, lookTarget);

        List<WorldZeroHouseEchoEntity.FarmTillTarget> tillTargets = new ArrayList<>();
        List<WorldZeroHouseEchoEntity.FarmPlantTarget> plantTargets = new ArrayList<>();
        worldzero$appendRestorationLineTargets(entries, playerPos, tillTargets, plantTargets);

        if (tillTargets.isEmpty() && plantTargets.isEmpty()) {
            return null;
        }

        BlockPos firstTarget = !tillTargets.isEmpty()
                ? tillTargets.get(0).worldzero$pos()
                : plantTargets.get(0).worldzero$soilPos();
        RestorationApproachScript approachScript = worldzero$createFixedRestorationApproach(level);
        BlockPos echoSpawnPos;
        List<BlockPos> approachTargets;
        List<BlockPos> doorTargets;
        int doorOpenStep;
        if (approachScript != null) {
            echoSpawnPos = approachScript.worldzero$spawnPos;
            approachTargets = approachScript.worldzero$waypoints;
            doorTargets = approachScript.worldzero$doorTargets;
            doorOpenStep = approachScript.worldzero$doorOpenStep;
        } else {
            echoSpawnPos = worldzero$findRestorationEchoSpawn(
                    level,
                    templateInfo,
                    worldzero$findPrimaryBedPos(level, templateInfo),
                    firstTarget
            );
            if (echoSpawnPos == null) {
                return null;
            }

            approachTargets = worldzero$findRestorationApproachPath(level, templateInfo, echoSpawnPos, firstTarget);
            if (approachTargets == null) {
                return null;
            }
            doorTargets = Collections.emptyList();
            doorOpenStep = -1;
        }

        return new RestorationDreamPlan(
                playerPos,
                echoSpawnPos,
                yawPitch[0],
                yawPitch[1],
                tillTargets,
                plantTargets,
                approachTargets,
                doorTargets,
                doorOpenStep
        );
    }

    private static void worldzero$appendRestorationLineTargets(
            List<RestorationEntry> entries,
            BlockPos playerPos,
            List<WorldZeroHouseEchoEntity.FarmTillTarget> tillTargets,
            List<WorldZeroHouseEchoEntity.FarmPlantTarget> plantTargets
    ) {
        if (entries.isEmpty()) {
            return;
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (RestorationEntry entry : entries) {
            minX = Math.min(minX, entry.worldzero$groundPos.getX());
            maxX = Math.max(maxX, entry.worldzero$groundPos.getX());
            minZ = Math.min(minZ, entry.worldzero$groundPos.getZ());
            maxZ = Math.max(maxZ, entry.worldzero$groundPos.getZ());
        }

        boolean rowsAlongX = (maxX - minX) >= (maxZ - minZ);
        Map<Integer, List<RestorationEntry>> lineEntries = new HashMap<>();
        for (RestorationEntry entry : entries) {
            int lineKey = rowsAlongX ? entry.worldzero$groundPos.getZ() : entry.worldzero$groundPos.getX();
            lineEntries.computeIfAbsent(lineKey, ignored -> new ArrayList<>()).add(entry);
        }

        List<Map.Entry<Integer, List<RestorationEntry>>> orderedLines = new ArrayList<>(lineEntries.entrySet());
        orderedLines.sort(Comparator
                .comparingDouble((Map.Entry<Integer, List<RestorationEntry>> lineEntry) -> worldzero$getClosestEntryDistanceSq(
                        lineEntry.getValue(),
                        playerPos
                ))
                .thenComparingInt(Map.Entry::getKey));

        for (int lineOrder = 0; lineOrder < orderedLines.size(); lineOrder++) {
            List<RestorationEntry> line = orderedLines.get(lineOrder).getValue();
            line.sort(worldzero$getRestorationLineComparator(playerPos, rowsAlongX, line));

            for (RestorationEntry entry : line) {
                if (entry.worldzero$groundBroken) {
                    tillTargets.add(new WorldZeroHouseEchoEntity.FarmTillTarget(
                            entry.worldzero$groundPos,
                            entry.worldzero$groundState,
                            lineOrder
                    ));
                }
            }

            for (RestorationEntry entry : line) {
                if (!entry.worldzero$plantBroken || entry.worldzero$plantState == null || entry.worldzero$plantState.isAir()) {
                    continue;
                }
                ItemStack heldItem = worldzero$getPlantItemForState(entry.worldzero$plantState);
                if (heldItem.isEmpty()) {
                    continue;
                }
                plantTargets.add(new WorldZeroHouseEchoEntity.FarmPlantTarget(
                        entry.worldzero$groundPos,
                        entry.worldzero$getPlantPos(),
                        entry.worldzero$groundState,
                        worldzero$getRestoredPlantState(entry.worldzero$plantState),
                        heldItem,
                        lineOrder
                ));
            }
        }
    }

    private static double worldzero$getClosestEntryDistanceSq(List<RestorationEntry> entries, BlockPos playerPos) {
        double closestDistanceSq = Double.MAX_VALUE;
        for (RestorationEntry entry : entries) {
            closestDistanceSq = Math.min(closestDistanceSq, entry.worldzero$groundPos.distSqr(playerPos));
        }
        return closestDistanceSq;
    }

    private static Comparator<RestorationEntry> worldzero$getRestorationLineComparator(
            BlockPos playerPos,
            boolean rowsAlongX,
            List<RestorationEntry> line
    ) {
        double averageAxis = 0.0D;
        for (RestorationEntry entry : line) {
            averageAxis += rowsAlongX ? entry.worldzero$groundPos.getX() : entry.worldzero$groundPos.getZ();
        }
        averageAxis /= Math.max(1, line.size());

        int playerAxis = rowsAlongX ? playerPos.getX() : playerPos.getZ();
        boolean descending = playerAxis > averageAxis;

        Comparator<RestorationEntry> comparator = Comparator.comparingInt(
                entry -> rowsAlongX ? entry.worldzero$groundPos.getX() : entry.worldzero$groundPos.getZ()
        );
        if (descending) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparingInt(entry -> rowsAlongX ? entry.worldzero$groundPos.getZ() : entry.worldzero$groundPos.getX());
    }

    private static BlockPos worldzero$getRestorationCenter(List<RestorationEntry> entries) {
        long sumX = 0L;
        long sumY = 0L;
        long sumZ = 0L;
        for (RestorationEntry entry : entries) {
            sumX += entry.worldzero$groundPos.getX();
            sumY += entry.worldzero$groundPos.getY();
            sumZ += entry.worldzero$groundPos.getZ();
        }
        int size = Math.max(1, entries.size());
        return new BlockPos(
                Math.round((float) sumX / size),
                Math.round((float) sumY / size),
                Math.round((float) sumZ / size)
        );
    }

    @Nullable
    private static BlockPos worldzero$findRestorationViewPos(
            ServerLevel level,
            TemplateInfo templateInfo,
            BlockPos center
    ) {
        BlockPos origin = WORLDZERO_BASE_ORIGIN;
        int minX = origin.getX();
        int maxX = origin.getX() + templateInfo.worldzero$size.getX() - 1;
        int minY = origin.getY();
        int maxY = origin.getY() + templateInfo.worldzero$size.getY() - 2;
        int minZ = origin.getZ();
        int maxZ = origin.getZ() + templateInfo.worldzero$size.getZ() - 1;
        Vec3 lookTarget = Vec3.atCenterOf(center);
        int[][] directions = new int[][]{
                {1, 0},
                {-1, 0},
                {0, 1},
                {0, -1},
                {1, 1},
                {1, -1},
                {-1, 1},
                {-1, -1}
        };

        for (int radius = 5; radius <= 12; radius++) {
            for (int[] direction : directions) {
                int candidateX = center.getX() + direction[0] * radius;
                int candidateZ = center.getZ() + direction[1] * radius;
                if (candidateX < minX || candidateX > maxX || candidateZ < minZ || candidateZ > maxZ) {
                    continue;
                }

                for (int y = center.getY() + 1; y >= minY; y--) {
                    if (y > maxY) {
                        continue;
                    }

                    BlockPos candidate = new BlockPos(candidateX, y, candidateZ);
                    if (!worldzero$isStandable(level, candidate)) {
                        continue;
                    }
                    if (!worldzero$hasClearView(level, candidate, lookTarget)) {
                        continue;
                    }
                    return candidate.immutable();
                }
            }
        }

        return worldzero$findSpawnPosNearBed(level, worldzero$findPrimaryBedPos(level, templateInfo), templateInfo);
    }

    private static boolean worldzero$hasClearView(ServerLevel level, BlockPos fromPos, Vec3 target) {
        Vec3 eyePos = new Vec3(fromPos.getX() + 0.5D, fromPos.getY() + 1.62D, fromPos.getZ() + 0.5D);
        HitResult hitResult = level.clip(new ClipContext(
                eyePos,
                target,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                null
        ));
        if (hitResult.getType() == HitResult.Type.MISS) {
            return true;
        }

        BlockPos targetPos = BlockPos.containing(target);
        return hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHitResult
                && blockHitResult.getBlockPos().closerThan(targetPos, 1.5D);
    }

    private static float[] worldzero$computeLookRotation(BlockPos fromPos, Vec3 target) {
        double deltaX = target.x - (fromPos.getX() + 0.5D);
        double deltaY = target.y - (fromPos.getY() + 1.62D);
        double deltaZ = target.z - (fromPos.getZ() + 0.5D);
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yaw = (float) (Math.atan2(deltaZ, deltaX) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) (-(Math.atan2(deltaY, horizontalDistance) * (180.0D / Math.PI)));
        return new float[]{yaw, pitch};
    }

    @Nullable
    private static BlockPos worldzero$findRestorationEchoSpawn(
            ServerLevel level,
            TemplateInfo templateInfo,
            @Nullable BlockPos bedPos,
            BlockPos firstTarget
    ) {
        if (bedPos != null) {
            BlockPos bedStart = worldzero$findSpawnPosNearBed(level, bedPos, templateInfo);
            if (worldzero$isStandable(level, bedStart)) {
                return bedStart.immutable();
            }
        }

        BlockPos interiorStart = worldzero$findRestorationHouseStart(level, templateInfo, firstTarget);
        if (interiorStart != null) {
            return interiorStart;
        }

        return worldzero$findRestorationEchoSpawnNearField(level, firstTarget);
    }

    @Nullable
    private static BlockPos worldzero$findRestorationHouseStart(
            ServerLevel level,
            TemplateInfo templateInfo,
            BlockPos firstTarget
    ) {
        BlockPos origin = WORLDZERO_BASE_ORIGIN;
        int minX = origin.getX();
        int maxX = origin.getX() + templateInfo.worldzero$size.getX() - 1;
        int minY = origin.getY();
        int maxY = origin.getY() + templateInfo.worldzero$size.getY() - 2;
        int minZ = origin.getZ();
        int maxZ = origin.getZ() + templateInfo.worldzero$size.getZ() - 1;
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        boolean preferX = Math.abs(firstTarget.getX() - centerX) >= Math.abs(firstTarget.getZ() - centerZ);
        int sideDirection = preferX
                ? Integer.compare(firstTarget.getX(), centerX)
                : Integer.compare(firstTarget.getZ(), centerZ);
        if (sideDirection == 0) {
            sideDirection = 1;
        }

        Vec3 lookTarget = Vec3.atCenterOf(firstTarget);
        for (boolean requireClearView : new boolean[]{true, false}) {
            BlockPos bestPos = null;
            double bestScore = Double.MAX_VALUE;

            for (int depth = 1; depth <= 5; depth++) {
                if (preferX) {
                    int x = sideDirection > 0 ? maxX - depth : minX + depth;
                    if (x <= minX || x >= maxX) {
                        continue;
                    }

                    for (int z = minZ + 1; z <= maxZ - 1; z++) {
                        for (int y = minY + 1; y <= maxY; y++) {
                            BlockPos candidate = new BlockPos(x, y, z);
                            if (!worldzero$isStandable(level, candidate)) {
                                continue;
                            }
                            if (requireClearView && !worldzero$hasClearView(level, candidate, lookTarget)) {
                                continue;
                            }

                            double score = candidate.distSqr(firstTarget) + depth * 4.0D + Math.abs(candidate.getY() - firstTarget.getY()) * 6.0D;
                            if (score < bestScore) {
                                bestScore = score;
                                bestPos = candidate.immutable();
                            }
                        }
                    }
                } else {
                    int z = sideDirection > 0 ? maxZ - depth : minZ + depth;
                    if (z <= minZ || z >= maxZ) {
                        continue;
                    }

                    for (int x = minX + 1; x <= maxX - 1; x++) {
                        for (int y = minY + 1; y <= maxY; y++) {
                            BlockPos candidate = new BlockPos(x, y, z);
                            if (!worldzero$isStandable(level, candidate)) {
                                continue;
                            }
                            if (requireClearView && !worldzero$hasClearView(level, candidate, lookTarget)) {
                                continue;
                            }

                            double score = candidate.distSqr(firstTarget) + depth * 4.0D + Math.abs(candidate.getY() - firstTarget.getY()) * 6.0D;
                            if (score < bestScore) {
                                bestScore = score;
                                bestPos = candidate.immutable();
                            }
                        }
                    }
                }
            }

            if (bestPos != null) {
                return bestPos;
            }
        }

        return null;
    }

    @Nullable
    private static BlockPos worldzero$findRestorationEchoSpawnNearField(ServerLevel level, BlockPos firstTarget) {
        int deltaX = 0;
        int deltaZ = 0;
        if (Math.abs(firstTarget.getX()) >= Math.abs(firstTarget.getZ())) {
            deltaX = Integer.compare(firstTarget.getX(), 0);
        } else {
            deltaZ = Integer.compare(firstTarget.getZ(), 0);
        }
        if (deltaX == 0 && deltaZ == 0) {
            deltaZ = 1;
        }

        for (int distance = 2; distance <= 5; distance++) {
            for (int lateral = -1; lateral <= 1; lateral++) {
                int x = firstTarget.getX() + deltaX * distance + (deltaZ == 0 ? lateral : 0);
                int z = firstTarget.getZ() + deltaZ * distance + (deltaX == 0 ? lateral : 0);
                BlockPos candidate = new BlockPos(x, firstTarget.getY() + 1, z);
                if (worldzero$isStandable(level, candidate)) {
                    return candidate.immutable();
                }
            }
        }

        for (int radius = 1; radius <= 4; radius++) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos candidate = firstTarget.relative(direction, radius).above();
                if (worldzero$isStandable(level, candidate)) {
                    return candidate.immutable();
                }
            }
        }

        return null;
    }

    @Nullable
    private static List<BlockPos> worldzero$findRestorationApproachPath(
            ServerLevel level,
            TemplateInfo templateInfo,
            BlockPos startPos,
            BlockPos firstTarget
    ) {
        BlockPos origin = WORLDZERO_BASE_ORIGIN;
        BlockPos targetPos = firstTarget.above();
        if (startPos.equals(targetPos)) {
            return Collections.emptyList();
        }

        int minX = origin.getX();
        int maxX = origin.getX() + templateInfo.worldzero$size.getX() - 1;
        int minY = Math.max(origin.getY() + 1, Math.min(startPos.getY(), targetPos.getY()) - 2);
        int maxY = Math.min(origin.getY() + templateInfo.worldzero$size.getY() - 1, Math.max(startPos.getY(), targetPos.getY()) + 2);
        int minZ = origin.getZ();
        int maxZ = origin.getZ() + templateInfo.worldzero$size.getZ() - 1;

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Map<Long, BlockPos> parents = new HashMap<>();
        Set<Long> visited = new HashSet<>();
        queue.add(startPos);
        visited.add(startPos.asLong());

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (current.equals(targetPos)) {
                break;
            }

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = worldzero$findRestorationPathNeighbor(level, current, direction, minY, maxY);
                if (next == null
                        || next.getX() < minX
                        || next.getX() > maxX
                        || next.getZ() < minZ
                        || next.getZ() > maxZ) {
                    continue;
                }

                long key = next.asLong();
                if (!visited.add(key)) {
                    continue;
                }

                parents.put(key, current);
                queue.addLast(next);
            }
        }

        if (!visited.contains(targetPos.asLong())) {
            return null;
        }

        List<BlockPos> path = new ArrayList<>();
        BlockPos cursor = targetPos;
        while (!cursor.equals(startPos)) {
            path.add(cursor.immutable());
            cursor = parents.get(cursor.asLong());
            if (cursor == null) {
                return null;
            }
        }
        Collections.reverse(path);
        return path;
    }

    @Nullable
    private static BlockPos worldzero$findRestorationPathNeighbor(
            ServerLevel level,
            BlockPos current,
            Direction direction,
            int minY,
            int maxY
    ) {
        int nextX = current.getX() + direction.getStepX();
        int nextZ = current.getZ() + direction.getStepZ();
        int[] offsets = new int[]{0, 1, -1, 2, -2};
        for (int offset : offsets) {
            int y = current.getY() + offset;
            if (y < minY || y > maxY) {
                continue;
            }

            BlockPos candidate = new BlockPos(nextX, y, nextZ);
            if (worldzero$isRestorationPathStandable(level, candidate)) {
                return candidate.immutable();
            }
        }

        return null;
    }

    private static boolean worldzero$isRestorationPathStandable(ServerLevel level, BlockPos pos) {
        if (worldzero$isStandable(level, pos)) {
            return true;
        }

        BlockState feetState = level.getBlockState(pos);
        BlockState headState = level.getBlockState(pos.above());
        BlockState floorState = level.getBlockState(pos.below());
        if (floorState.getCollisionShape(level, pos.below()).isEmpty()) {
            return false;
        }

        return worldzero$isRestorationOpenablePassage(feetState)
                && (headState.getCollisionShape(level, pos.above()).isEmpty()
                || worldzero$isRestorationOpenablePassage(headState));
    }

    private static boolean worldzero$isRestorationOpenablePassage(BlockState state) {
        return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock;
    }

    @Nullable
    private static RestorationApproachScript worldzero$createFixedRestorationApproach(ServerLevel level) {
        if (!worldzero$isStandable(level, WORLDZERO_RESTORATION_ROUTE_START)) {
            return null;
        }

        for (BlockPos waypoint : WORLDZERO_RESTORATION_ROUTE_POINTS) {
            if (!worldzero$isStandable(level, waypoint)) {
                return null;
            }
        }

        for (BlockPos doorTarget : WORLDZERO_RESTORATION_ROUTE_DOORS) {
            BlockState state = level.getBlockState(doorTarget);
            if (!(state.getBlock() instanceof DoorBlock)
                    || !state.hasProperty(DoorBlock.HALF)
                    || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
                return null;
            }
        }

        return new RestorationApproachScript(
                WORLDZERO_RESTORATION_ROUTE_START,
                WORLDZERO_RESTORATION_ROUTE_POINTS,
                WORLDZERO_RESTORATION_ROUTE_DOORS,
                WORLDZERO_RESTORATION_ROUTE_DOOR_OPEN_STEP
        );
    }

    private static ItemStack worldzero$getPlantItemForState(BlockState plantState) {
        Block block = plantState.getBlock();
        if (block == Blocks.WHEAT) {
            return new ItemStack(Items.WHEAT_SEEDS);
        }
        if (block == Blocks.CARROTS) {
            return new ItemStack(Items.CARROT);
        }
        if (block == Blocks.POTATOES) {
            return new ItemStack(Items.POTATO);
        }
        if (block == Blocks.BEETROOTS) {
            return new ItemStack(Items.BEETROOT_SEEDS);
        }
        if (block == Blocks.PUMPKIN_STEM || block == Blocks.ATTACHED_PUMPKIN_STEM) {
            return new ItemStack(Items.PUMPKIN_SEEDS);
        }
        if (block == Blocks.MELON_STEM || block == Blocks.ATTACHED_MELON_STEM) {
            return new ItemStack(Items.MELON_SEEDS);
        }

        Item item = block.asItem();
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static BlockState worldzero$getRestoredPlantState(BlockState originalPlantState) {
        if (originalPlantState.isAir()) {
            return originalPlantState;
        }
        return originalPlantState.getBlock().defaultBlockState();
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

    private static boolean worldzero$isFieldDamageAllowed(
            @Nullable Player player,
            @Nullable BlockPos pos,
            @Nullable BlockState state
    ) {
        if (!(player instanceof ServerPlayer serverPlayer) || pos == null || state == null) {
            return false;
        }

        MinecraftServer server = serverPlayer.getServer();
        if (server == null) {
            return false;
        }

        HouseSaveData saveData = worldzero$getSaveData(server);
        if (saveData.worldzero$visitModes.get(serverPlayer.getUUID()) != HouseVisitMode.NORMAL) {
            return false;
        }

        if (state.getBlock() instanceof FarmBlock) {
            return true;
        }

        if (worldzero$isRestorablePlantState(state)) {
            BlockState belowState = serverPlayer.serverLevel().getBlockState(pos.below());
            return belowState.getBlock() instanceof FarmBlock
                    || saveData.worldzero$restorationEntries.getOrDefault(serverPlayer.getUUID(), java.util.Collections.emptyMap())
                    .containsKey(pos.below().asLong());
        }

        return false;
    }

    private static boolean worldzero$isRestorablePlantState(BlockState state) {
        if (state.isAir()) {
            return false;
        }

        Block block = state.getBlock();
        return block instanceof CropBlock
                || block == Blocks.PUMPKIN_STEM
                || block == Blocks.ATTACHED_PUMPKIN_STEM
                || block == Blocks.MELON_STEM
                || block == Blocks.ATTACHED_MELON_STEM;
    }

    private static boolean worldzero$hasRestorationDamage(HouseSaveData saveData, UUID playerId) {
        return !worldzero$getRestorationEntries(saveData, playerId).isEmpty();
    }

    private static List<RestorationEntry> worldzero$getRestorationEntries(HouseSaveData saveData, UUID playerId) {
        Map<Long, RestorationEntry> entries = saveData.worldzero$restorationEntries.get(playerId);
        if (entries == null || entries.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        return new ArrayList<>(entries.values());
    }

    private static boolean worldzero$seedDebugRestorationDamage(
            MinecraftServer server,
            HouseSaveData saveData,
            UUID playerId
    ) {
        ServerLevel houseLevel = server.getLevel(WORLDZERO_HOUSE_LEVEL);
        if (houseLevel == null) {
            return false;
        }

        TemplateInfo templateInfo = worldzero$getTemplateInfo(houseLevel);
        if (templateInfo == null || !worldzero$prepareHouseLevel(houseLevel, templateInfo)) {
            return false;
        }

        List<RestorationEntry> debugEntries = worldzero$collectDebugRestorationEntries(houseLevel, templateInfo);
        if (debugEntries.isEmpty()) {
            return false;
        }

        Map<Long, RestorationEntry> playerEntries = new HashMap<>();
        for (RestorationEntry entry : debugEntries) {
            playerEntries.put(entry.worldzero$groundPos.asLong(), entry);
        }
        saveData.worldzero$restorationEntries.put(playerId, playerEntries);
        saveData.setDirty();
        return true;
    }

    private static List<RestorationEntry> worldzero$collectDebugRestorationEntries(
            ServerLevel level,
            TemplateInfo templateInfo
    ) {
        List<RestorationEntry> entries = new ArrayList<>();
        BlockPos origin = WORLDZERO_BASE_ORIGIN;
        for (int x = origin.getX(); x < origin.getX() + templateInfo.worldzero$size.getX(); x++) {
            for (int y = origin.getY(); y < origin.getY() + templateInfo.worldzero$size.getY(); y++) {
                for (int z = origin.getZ(); z < origin.getZ() + templateInfo.worldzero$size.getZ(); z++) {
                    BlockPos soilPos = new BlockPos(x, y, z);
                    BlockState soilState = level.getBlockState(soilPos);
                    if (!(soilState.getBlock() instanceof FarmBlock)) {
                        continue;
                    }

                    BlockState plantState = level.getBlockState(soilPos.above());
                    entries.add(new RestorationEntry(
                            soilPos,
                            soilState,
                            worldzero$isRestorablePlantState(plantState) ? plantState : null,
                            true,
                            worldzero$isRestorablePlantState(plantState)
                    ));
                }
            }
        }

        entries.sort(Comparator
                .comparingInt((RestorationEntry entry) -> entry.worldzero$groundPos.getZ())
                .thenComparingInt(entry -> entry.worldzero$groundPos.getX())
                .thenComparingInt(entry -> entry.worldzero$groundPos.getY()));
        return entries;
    }

    private static void worldzero$recordTrampleDamage(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos,
            BlockState originalSoilState
    ) {
        BlockState plantState = level.getBlockState(pos.above());
        worldzero$recordRestorationEntry(
                player,
                pos,
                originalSoilState,
                worldzero$isRestorablePlantState(plantState) ? plantState : null,
                true,
                worldzero$isRestorablePlantState(plantState)
        );
    }

    private static void worldzero$applyFallbackTrampleDamage(
            ServerPlayer player,
            ServerLevel level,
            BlockPos soilPos,
            BlockState originalSoilState
    ) {
        if (!(level.getBlockState(soilPos).getBlock() instanceof FarmBlock)) {
            return;
        }

        worldzero$recordTrampleDamage(player, level, soilPos, originalSoilState);
        BlockPos plantPos = soilPos.above();
        if (worldzero$isRestorablePlantState(level.getBlockState(plantPos))) {
            level.destroyBlock(plantPos, true, player);
        }
        level.levelEvent(2001, soilPos, Block.getId(originalSoilState));
        level.setBlock(soilPos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static void worldzero$recordRestorationEntry(
            ServerPlayer player,
            BlockPos soilPos,
            BlockState soilState,
            @Nullable BlockState plantState,
            boolean groundBroken,
            boolean plantBroken
    ) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        HouseSaveData saveData = worldzero$getSaveData(server);
        Map<Long, RestorationEntry> playerEntries = saveData.worldzero$restorationEntries.computeIfAbsent(
                player.getUUID(),
                ignored -> new HashMap<>()
        );
        RestorationEntry entry = playerEntries.get(soilPos.asLong());
        if (entry == null) {
            entry = new RestorationEntry(soilPos, soilState, plantState, groundBroken, plantBroken);
            playerEntries.put(soilPos.asLong(), entry);
            saveData.setDirty();
            return;
        }

        boolean changed = false;
        if (groundBroken && !entry.worldzero$groundBroken) {
            entry.worldzero$groundBroken = true;
            changed = true;
        }
        if (plantBroken && !entry.worldzero$plantBroken) {
            entry.worldzero$plantBroken = true;
            changed = true;
        }
        if (entry.worldzero$plantState == null && plantState != null && !plantState.isAir()) {
            entry.worldzero$plantState = plantState;
            changed = true;
        }
        if (changed) {
            saveData.setDirty();
        }
    }

    private static final class SessionState {
        private final Map<UUID, SleepTracker> worldzero$sleepTrackers = new HashMap<>();
        private final Map<UUID, RestorationSceneState> worldzero$restorationScenes = new HashMap<>();
        private final Map<UUID, TrampleMarker> worldzero$lastTrampleMarkers = new HashMap<>();
    }

    private static final class SleepTracker {
        private boolean worldzero$currentSleepTracked;
        private boolean worldzero$houseTeleportPending;
        private boolean worldzero$houseBadTeleportPending;
        private boolean worldzero$restorationTeleportPending;

        private void worldzero$reset() {
            this.worldzero$currentSleepTracked = false;
            this.worldzero$houseTeleportPending = false;
            this.worldzero$houseBadTeleportPending = false;
            this.worldzero$restorationTeleportPending = false;
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

    private static final class RestorationSceneState {
        private final int worldzero$echoEntityId;
        private final long[] worldzero$chatTicks;
        private int worldzero$timeoutTicks;
        private int worldzero$spawnGraceTicks;
        private int worldzero$chatMessageIndex;

        private RestorationSceneState(int echoEntityId, int timeoutTicks, long[] chatTicks) {
            this.worldzero$echoEntityId = echoEntityId;
            this.worldzero$chatTicks = chatTicks;
            this.worldzero$timeoutTicks = timeoutTicks;
            this.worldzero$spawnGraceTicks = 20;
            this.worldzero$chatMessageIndex = 0;
        }
    }

    private static final class TrampleMarker {
        private final long worldzero$gameTime;
        private final BlockPos worldzero$pos;

        private TrampleMarker(long gameTime, BlockPos pos) {
            this.worldzero$gameTime = gameTime;
            this.worldzero$pos = pos.immutable();
        }
    }

    private static final class RestorationDreamPlan {
        private final BlockPos worldzero$playerPos;
        private final BlockPos worldzero$echoSpawnPos;
        private final float worldzero$playerYaw;
        private final float worldzero$playerPitch;
        private final List<WorldZeroHouseEchoEntity.FarmTillTarget> worldzero$tillTargets;
        private final List<WorldZeroHouseEchoEntity.FarmPlantTarget> worldzero$plantTargets;
        private final List<BlockPos> worldzero$approachTargets;
        private final List<BlockPos> worldzero$doorTargets;
        private final int worldzero$doorOpenStep;

        private RestorationDreamPlan(
                BlockPos playerPos,
                BlockPos echoSpawnPos,
                float playerYaw,
                float playerPitch,
                List<WorldZeroHouseEchoEntity.FarmTillTarget> tillTargets,
                List<WorldZeroHouseEchoEntity.FarmPlantTarget> plantTargets,
                List<BlockPos> approachTargets,
                List<BlockPos> doorTargets,
                int doorOpenStep
        ) {
            this.worldzero$playerPos = playerPos.immutable();
            this.worldzero$echoSpawnPos = echoSpawnPos.immutable();
            this.worldzero$playerYaw = playerYaw;
            this.worldzero$playerPitch = playerPitch;
            this.worldzero$tillTargets = tillTargets;
            this.worldzero$plantTargets = plantTargets;
            this.worldzero$approachTargets = List.copyOf(approachTargets);
            this.worldzero$doorTargets = List.copyOf(doorTargets);
            this.worldzero$doorOpenStep = doorOpenStep;
        }
    }

    private record RestorationApproachScript(
            BlockPos worldzero$spawnPos,
            List<BlockPos> worldzero$waypoints,
            List<BlockPos> worldzero$doorTargets,
            int worldzero$doorOpenStep
    ) {
    }

    private static final class RestorationEntry {
        private final BlockPos worldzero$groundPos;
        private final BlockState worldzero$groundState;
        @Nullable
        private BlockState worldzero$plantState;
        private boolean worldzero$groundBroken;
        private boolean worldzero$plantBroken;

        private RestorationEntry(
                BlockPos groundPos,
                BlockState groundState,
                @Nullable BlockState plantState,
                boolean groundBroken,
                boolean plantBroken
        ) {
            this.worldzero$groundPos = groundPos.immutable();
            this.worldzero$groundState = groundState;
            this.worldzero$plantState = plantState;
            this.worldzero$groundBroken = groundBroken;
            this.worldzero$plantBroken = plantBroken;
        }

        private BlockPos worldzero$getPlantPos() {
            return this.worldzero$groundPos.above();
        }

        private CompoundTag worldzero$save() {
            CompoundTag tag = new CompoundTag();
            tag.put("GroundPos", NbtUtils.writeBlockPos(this.worldzero$groundPos));
            tag.put("GroundState", NbtUtils.writeBlockState(this.worldzero$groundState));
            if (this.worldzero$plantState != null && !this.worldzero$plantState.isAir()) {
                tag.put("PlantState", NbtUtils.writeBlockState(this.worldzero$plantState));
            }
            tag.putBoolean("GroundBroken", this.worldzero$groundBroken);
            tag.putBoolean("PlantBroken", this.worldzero$plantBroken);
            return tag;
        }

        @Nullable
        private static RestorationEntry worldzero$load(CompoundTag tag) {
            if (!tag.contains("GroundPos", Tag.TAG_COMPOUND) || !tag.contains("GroundState", Tag.TAG_COMPOUND)) {
                return null;
            }

            BlockPos groundPos = NbtUtils.readBlockPos(tag.getCompound("GroundPos"));
            BlockState groundState = NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("GroundState"));
            BlockState plantState = null;
            if (tag.contains("PlantState", Tag.TAG_COMPOUND)) {
                plantState = NbtUtils.readBlockState(
                        net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),
                        tag.getCompound("PlantState")
                );
            }
            return new RestorationEntry(
                    groundPos,
                    groundState,
                    plantState,
                    tag.getBoolean("GroundBroken"),
                    tag.getBoolean("PlantBroken")
            );
        }
    }

    private static final class HouseSaveData extends SavedData {
        private final Map<UUID, Integer> worldzero$sleepCounts = new HashMap<>();
        private final Map<UUID, Integer> worldzero$postKoridorSleepCounts = new HashMap<>();
        private final Set<UUID> worldzero$completedPlayers = new java.util.HashSet<>();
        private final Map<UUID, ReturnPoint> worldzero$returnPoints = new HashMap<>();
        private final Map<UUID, PlayerInventorySnapshot> worldzero$inventorySnapshots = new HashMap<>();
        private final Map<UUID, HouseVisitMode> worldzero$visitModes = new HashMap<>();
        private final Set<UUID> worldzero$restorationPendingPlayers = new HashSet<>();
        private final Map<UUID, Map<Long, RestorationEntry>> worldzero$restorationEntries = new HashMap<>();

        @Override
        public CompoundTag save(CompoundTag tag) {
            CompoundTag sleepCountsTag = new CompoundTag();
            for (Map.Entry<UUID, Integer> entry : this.worldzero$sleepCounts.entrySet()) {
                sleepCountsTag.putInt(entry.getKey().toString(), entry.getValue());
            }
            tag.put("SleepCounts", sleepCountsTag);

            CompoundTag postKoridorSleepCountsTag = new CompoundTag();
            for (Map.Entry<UUID, Integer> entry : this.worldzero$postKoridorSleepCounts.entrySet()) {
                postKoridorSleepCountsTag.putInt(entry.getKey().toString(), entry.getValue());
            }
            tag.put("PostKoridorSleepCounts", postKoridorSleepCountsTag);

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

            CompoundTag visitModesTag = new CompoundTag();
            for (Map.Entry<UUID, HouseVisitMode> entry : this.worldzero$visitModes.entrySet()) {
                visitModesTag.putByte(entry.getKey().toString(), entry.getValue().worldzero$saveId);
            }
            tag.put("VisitModes", visitModesTag);

            CompoundTag restorationPendingTag = new CompoundTag();
            for (UUID playerId : this.worldzero$restorationPendingPlayers) {
                restorationPendingTag.putBoolean(playerId.toString(), true);
            }
            tag.put("RestorationPending", restorationPendingTag);

            CompoundTag restorationEntriesTag = new CompoundTag();
            for (Map.Entry<UUID, Map<Long, RestorationEntry>> playerEntry : this.worldzero$restorationEntries.entrySet()) {
                ListTag listTag = new ListTag();
                for (RestorationEntry restorationEntry : playerEntry.getValue().values()) {
                    listTag.add(restorationEntry.worldzero$save());
                }
                restorationEntriesTag.put(playerEntry.getKey().toString(), listTag);
            }
            tag.put("RestorationEntries", restorationEntriesTag);
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

            CompoundTag postKoridorSleepCountsTag = tag.getCompound("PostKoridorSleepCounts");
            for (String key : postKoridorSleepCountsTag.getAllKeys()) {
                try {
                    saveData.worldzero$postKoridorSleepCounts.put(
                            UUID.fromString(key),
                            postKoridorSleepCountsTag.getInt(key)
                    );
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

            CompoundTag visitModesTag = tag.getCompound("VisitModes");
            for (String key : visitModesTag.getAllKeys()) {
                try {
                    HouseVisitMode visitMode = HouseVisitMode.worldzero$fromSaveId(visitModesTag.getByte(key));
                    if (visitMode != null) {
                        saveData.worldzero$visitModes.put(UUID.fromString(key), visitMode);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }

            CompoundTag restorationPendingTag = tag.getCompound("RestorationPending");
            for (String key : restorationPendingTag.getAllKeys()) {
                try {
                    if (restorationPendingTag.getBoolean(key)) {
                        saveData.worldzero$restorationPendingPlayers.add(UUID.fromString(key));
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }

            CompoundTag restorationEntriesTag = tag.getCompound("RestorationEntries");
            for (String key : restorationEntriesTag.getAllKeys()) {
                try {
                    UUID playerId = UUID.fromString(key);
                    ListTag listTag = restorationEntriesTag.getList(key, Tag.TAG_COMPOUND);
                    if (listTag.isEmpty()) {
                        continue;
                    }

                    Map<Long, RestorationEntry> playerEntries = new HashMap<>();
                    for (int index = 0; index < listTag.size(); index++) {
                        RestorationEntry restorationEntry = RestorationEntry.worldzero$load(listTag.getCompound(index));
                        if (restorationEntry != null) {
                            playerEntries.put(restorationEntry.worldzero$groundPos.asLong(), restorationEntry);
                        }
                    }
                    if (!playerEntries.isEmpty()) {
                        saveData.worldzero$restorationEntries.put(playerId, playerEntries);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }

            return saveData;
        }
    }
}
