package ru.nekostul.worldzero.event.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;
import ru.nekostul.worldzero.event.WorldZeroStoryTime;
import ru.nekostul.worldzero.event.chat.WorldZeroDoubleChatEvent;
import ru.nekostul.worldzero.event.house.WorldZeroHouseDetector;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
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
public final class WorldZeroOverworldStructureEvent {
    private static final String WORLDZERO_SAVE_ID = "worldzero_overworld_structures";
    private static final long WORLDZERO_TICKS_PER_MINUTE = 20L * 60L;
    private static final long WORLDZERO_WORKSTATION_AT_TICKS = 15L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_PORTAL_AT_TICKS = 5L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_PORTAL2_AT_TICKS = 30L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_NEIGHBOR_MIN_DELAY_TICKS = 15L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_NEIGHBOR_MAX_DELAY_TICKS = 20L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_DOM2_MIN_DELAY_TICKS = 20L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_DOM2_MAX_DELAY_TICKS = 30L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_DOM2_REACTION_DELAY_TICKS = 2L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_JOIN_SAFE_TICKS = 16L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_BUILD_MEMORY_TICKS = 20L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_HOUSE_CHECK_INTERVAL_TICKS = 15L * 20L;
    private static final long WORLDZERO_STRUCTURE_RETRY_TICKS = 20L * 20L;
    private static final int WORLDZERO_REQUIRED_BUILD_BLOCKS = 16;
    private static final int WORLDZERO_PORTAL_MIN_DISTANCE = 18;
    private static final int WORLDZERO_PORTAL_MAX_DISTANCE = 34;
    private static final int WORLDZERO_PORTAL2_SEPARATION = 48;
    private static final int WORLDZERO_WORKSTATION_MIN_DISTANCE = 14;
    private static final int WORLDZERO_WORKSTATION_MAX_DISTANCE = 28;
    private static final int WORLDZERO_DOM_MIN_DISTANCE = 24;
    private static final int WORLDZERO_DOM_MAX_DISTANCE = 38;
    private static final int WORLDZERO_DOM_PLAYER_MIN_DISTANCE = 18;
    private static final int WORLDZERO_DEBUG_PORTAL_MIN_DISTANCE = 10;
    private static final int WORLDZERO_DEBUG_PORTAL_MAX_DISTANCE = 18;
    private static final int WORLDZERO_DEBUG_DOM_MIN_DISTANCE = 14;
    private static final int WORLDZERO_DEBUG_DOM_MAX_DISTANCE = 24;
    private static final int WORLDZERO_PATH_SURFACE_VARIANCE = 2;
    private static final int WORLDZERO_DOM_SURFACE_VARIANCE = 3;
    private static final int WORLDZERO_DEBUG_DOM_SURFACE_VARIANCE = 5;
    private static final int WORLDZERO_MAX_CANDIDATE_ATTEMPTS = 48;
    private static final int WORLDZERO_DEBUG_DOM_ATTEMPTS = 96;
    private static final int WORLDZERO_FOUNDATION_DEPTH = 6;
    private static final int WORLDZERO_STRUCTURE_CLEAR_HEADROOM = 4;
    private static final int WORLDZERO_DOM_CLEAR_MARGIN = 2;
    private static final int WORLDZERO_GENERIC_CLEAR_MARGIN = 1;
    private static final int WORLDZERO_DOOR_PATH_LENGTH = 3;
    private static final int WORLDZERO_DOOR_PATH_HALF_WIDTH = 1;
    private static final int WORLDZERO_DEBUG_DOM_UPDATE_RADIUS = 96;
    private static final int WORLDZERO_DOM_LAVA_CHECK_RADIUS = 8;
    private static final int WORLDZERO_DOM_LAVA_VERTICAL_BELOW = 2;
    private static final int WORLDZERO_DOM_LAVA_VERTICAL_ABOVE = 3;
    private static final ResourceLocation WORLDZERO_PORTAL_ID = new ResourceLocation(WorldZeroMod.MOD_ID, "portal");
    private static final ResourceLocation WORLDZERO_PORTAL2_ID = new ResourceLocation(WorldZeroMod.MOD_ID, "portal2");
    private static final ResourceLocation WORLDZERO_DOM_ID = new ResourceLocation(WorldZeroMod.MOD_ID, "dom");
    private static final ResourceLocation WORLDZERO_DOM2_ID = new ResourceLocation(WorldZeroMod.MOD_ID, "dom2");
    private static final String WORLDZERO_NEIGHBOR_BUILT_KEY = "message.worldzero.double_chat.neighbor_built";
    private static final String WORLDZERO_DOM_CHANGED_KEY = "message.worldzero.double_chat.dom_changed";
    private static final Map<MinecraftServer, SessionState> WORLDZERO_SESSION_STATES = new WeakHashMap<>();
    private static final DomLootEntry[] WORLDZERO_DOM_LOOT_POOL = new DomLootEntry[]{
            new DomLootEntry(Items.DIRT, 8, 48, 14),
            new DomLootEntry(Items.COBBLESTONE, 8, 40, 13),
            new DomLootEntry(Items.GRAVEL, 4, 24, 8),
            new DomLootEntry(Items.OAK_LOG, 4, 18, 7),
            new DomLootEntry(Items.SPRUCE_LOG, 4, 18, 6),
            new DomLootEntry(Items.OAK_PLANKS, 6, 32, 10),
            new DomLootEntry(Items.STICK, 2, 18, 8),
            new DomLootEntry(Items.ROTTEN_FLESH, 1, 8, 5),
            new DomLootEntry(Items.BONE, 1, 8, 5),
            new DomLootEntry(Items.STRING, 1, 8, 5),
            new DomLootEntry(Items.WHEAT_SEEDS, 1, 8, 5),
            new DomLootEntry(Items.BEETROOT_SEEDS, 1, 6, 4),
            new DomLootEntry(Items.WHEAT, 1, 8, 4),
            new DomLootEntry(Items.POTATO, 1, 6, 4),
            new DomLootEntry(Items.CARROT, 1, 6, 4),
            new DomLootEntry(Items.APPLE, 1, 4, 3),
            new DomLootEntry(Items.COAL, 1, 10, 4),
            new DomLootEntry(Items.LEATHER, 1, 3, 3),
            new DomLootEntry(Items.FEATHER, 1, 8, 4),
            new DomLootEntry(Items.GLASS, 2, 12, 4),
            new DomLootEntry(Items.GLASS_PANE, 4, 16, 4),
            new DomLootEntry(Items.TORCH, 2, 10, 4),
            new DomLootEntry(Items.PAPER, 1, 8, 4),
            new DomLootEntry(Items.MAP, 1, 1, 2),
            new DomLootEntry(Items.WOODEN_PICKAXE, 1, 1, 2, 84, 97),
            new DomLootEntry(Items.STONE_PICKAXE, 1, 1, 2, 70, 95),
            new DomLootEntry(Items.STONE_AXE, 1, 1, 1, 70, 96),
            new DomLootEntry(Items.STONE_SHOVEL, 1, 1, 1, 70, 96),
            new DomLootEntry(Items.IRON_INGOT, 1, 3, 1)
    };

    private WorldZeroOverworldStructureEvent() {
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
        StructureSaveData saveData = worldzero$getSaveData(level);
        SessionState sessionState = WORLDZERO_SESSION_STATES.computeIfAbsent(level.getServer(), ignored -> new SessionState());

        for (ServerPlayer player : level.players()) {
            if (!worldzero$isValidPlayer(player)) {
                continue;
            }

            if (!saveData.worldzero$houseDetected) {
                worldzero$tryDetectPlayerHouse(level, saveData, sessionState, player, storyTicks);
            }
        }

        if (storyTicks >= WORLDZERO_WORKSTATION_AT_TICKS
                && !saveData.worldzero$workstationPlaced
                && storyTicks >= saveData.worldzero$workstationRetryAfterTick) {
            if (!worldzero$tryPlaceWorkstation(level, sessionState)) {
                saveData.worldzero$workstationRetryAfterTick = storyTicks + WORLDZERO_STRUCTURE_RETRY_TICKS;
                saveData.setDirty();
            }
        }

        if (storyTicks >= WORLDZERO_PORTAL_AT_TICKS && !saveData.worldzero$portalPlaced && storyTicks >= saveData.worldzero$portalRetryAfterTick) {
            if (!worldzero$tryPlacePortal(level, saveData, sessionState, false)) {
                saveData.worldzero$portalRetryAfterTick = storyTicks + WORLDZERO_STRUCTURE_RETRY_TICKS;
                saveData.setDirty();
            }
        }

        if (storyTicks >= WORLDZERO_PORTAL2_AT_TICKS && !saveData.worldzero$portal2Placed && storyTicks >= saveData.worldzero$portal2RetryAfterTick) {
            if (!worldzero$tryPlacePortal(level, saveData, sessionState, true)) {
                saveData.worldzero$portal2RetryAfterTick = storyTicks + WORLDZERO_STRUCTURE_RETRY_TICKS;
                saveData.setDirty();
            }
        }

        if (saveData.worldzero$houseDetected) {
            if (!saveData.worldzero$domPlaced && storyTicks >= saveData.worldzero$domRetryAfterTick) {
                if (!worldzero$tryPlaceDom(level, saveData, sessionState)) {
                    saveData.worldzero$domRetryAfterTick = storyTicks + WORLDZERO_STRUCTURE_RETRY_TICKS;
                    saveData.setDirty();
                }
            }

            if (saveData.worldzero$domPlaced
                    && !saveData.worldzero$dom2Placed
                    && !saveData.worldzero$domSeenSoundPlayed
                    && worldzero$tryPlayDomSeenSound(level, saveData)) {
                saveData.worldzero$domSeenSoundPlayed = true;
                saveData.setDirty();
            }

            if (saveData.worldzero$domPlaced
                    && !saveData.worldzero$neighborBuiltMessageSent
                    && saveData.worldzero$neighborBuiltMessageTick >= 0L
                    && storyTicks >= saveData.worldzero$neighborBuiltMessageTick) {
                if (worldzero$sendHouseOwnerLine(level, saveData, WORLDZERO_NEIGHBOR_BUILT_KEY)) {
                    saveData.worldzero$neighborBuiltMessageSent = true;
                    saveData.setDirty();
                }
            }

            if (saveData.worldzero$domPlaced
                    && !saveData.worldzero$dom2Placed
                    && saveData.worldzero$dom2UpgradeTick >= 0L
                    && storyTicks >= saveData.worldzero$dom2UpgradeTick) {
                if (worldzero$tryUpgradeDom(level, saveData)) {
                    saveData.setDirty();
                }
            }

            if (saveData.worldzero$dom2Placed
                    && !saveData.worldzero$dom2ReactionSent
                    && saveData.worldzero$dom2ReactionTick >= 0L
                    && storyTicks >= saveData.worldzero$dom2ReactionTick) {
                if (worldzero$sendHouseOwnerLine(level, saveData, WORLDZERO_DOM_CHANGED_KEY)) {
                    saveData.worldzero$dom2ReactionSent = true;
                    saveData.setDirty();
                }
            }
        }

        for (ServerPlayer player : level.players()) {
            if (worldzero$isValidPlayer(player)) {
                worldzero$updateTrackedPlayer(sessionState, player);
            }
        }
    }

    @SubscribeEvent
    public static void worldzero$onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.level().isClientSide()
                || player.serverLevel().dimension() != Level.OVERWORLD) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        SessionState sessionState = WORLDZERO_SESSION_STATES.computeIfAbsent(server, ignored -> new SessionState());
        TrackedPlayerState trackedPlayer = sessionState.worldzero$players.computeIfAbsent(
                player.getUUID(),
                ignored -> new TrackedPlayerState()
        );
        long storyTicks = WorldZeroStoryTime.worldzero$getStoryTicks(player.serverLevel());
        if (trackedPlayer.worldzero$lastBuildStoryTick < 0L
                || storyTicks - trackedPlayer.worldzero$lastBuildStoryTick > WORLDZERO_BUILD_MEMORY_TICKS) {
            trackedPlayer.worldzero$recentBuildBlocks = 0;
            trackedPlayer.worldzero$firstBuildPos = event.getPos().immutable();
        }

        trackedPlayer.worldzero$recentBuildBlocks++;
        trackedPlayer.worldzero$lastBuildStoryTick = storyTicks;
        trackedPlayer.worldzero$lastBuildPos = event.getPos().immutable();
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_SESSION_STATES.remove(event.getServer());
    }

    public static boolean worldzero$spawnDebugStructure(ServerPlayer player, String structureName) {
        if (player == null
                || player.level().isClientSide()
                || player.serverLevel().dimension() != Level.OVERWORLD) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        SessionState sessionState = WORLDZERO_SESSION_STATES.computeIfAbsent(level.getServer(), ignored -> new SessionState());
        TrackedPlayerState trackedPlayer = sessionState.worldzero$players.computeIfAbsent(
                player.getUUID(),
                ignored -> new TrackedPlayerState()
        );

        if ("portal".equals(structureName)) {
            return worldzero$spawnDebugPathStructure(level, player, trackedPlayer, WORLDZERO_PORTAL_ID, false);
        }
        if ("portal2".equals(structureName)) {
            return worldzero$spawnDebugPathStructure(level, player, trackedPlayer, WORLDZERO_PORTAL2_ID, false);
        }
        if ("dom".equals(structureName)) {
            return worldzero$spawnDebugPathStructure(level, player, trackedPlayer, WORLDZERO_DOM_ID, true);
        }
        if ("dom2".equals(structureName)) {
            return worldzero$spawnDebugPathStructure(level, player, trackedPlayer, WORLDZERO_DOM2_ID, true);
        }

        return false;
    }

    public static boolean worldzero$updateNearestDomDebug(ServerPlayer player) {
        if (player == null
                || player.level().isClientSide()
                || player.serverLevel().dimension() != Level.OVERWORLD) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        TemplateInfo oldTemplate = worldzero$getTemplateInfo(level, WORLDZERO_DOM_ID);
        TemplateInfo newTemplate = worldzero$getTemplateInfo(level, WORLDZERO_DOM2_ID);
        if (oldTemplate == null || newTemplate == null) {
            return false;
        }

        BlockPos origin = worldzero$findNearestDomOrigin(level, player.blockPosition());
        if (origin == null) {
            return false;
        }

        List<ChestGroupSnapshot> chestSnapshots = worldzero$captureChestSnapshots(level, origin, oldTemplate.worldzero$size);
        worldzero$clearContainerContents(level, origin, oldTemplate.worldzero$size);
        Vec3i clearSize = new Vec3i(
                Math.max(oldTemplate.worldzero$size.getX(), newTemplate.worldzero$size.getX()),
                Math.max(oldTemplate.worldzero$size.getY(), newTemplate.worldzero$size.getY()),
                Math.max(oldTemplate.worldzero$size.getZ(), newTemplate.worldzero$size.getZ())
        );
        worldzero$clearArea(level, origin, clearSize);
        if (!worldzero$placeTemplate(level, newTemplate, origin, true, false)) {
            return false;
        }

        worldzero$restoreDom2ChestLoot(level, origin, newTemplate.worldzero$size, chestSnapshots);
        return true;
    }

    private static boolean worldzero$tryPlaceWorkstation(
            ServerLevel level,
            SessionState sessionState
    ) {
        ServerPlayer player = worldzero$pickTargetPlayer(level);
        if (player == null) {
            return false;
        }

        TrackedPlayerState trackedPlayer = sessionState.worldzero$players.computeIfAbsent(
                player.getUUID(),
                ignored -> new TrackedPlayerState()
        );
        WorkstationPlacement placement = worldzero$findWorkstationPlacement(level, player, trackedPlayer);
        if (placement == null) {
            return false;
        }

        worldzero$clearSimplePlacementArea(level, placement.worldzero$craftingTablePos);
        worldzero$clearSimplePlacementArea(level, placement.worldzero$furnacePos);
        level.setBlock(
                placement.worldzero$craftingTablePos,
                Blocks.CRAFTING_TABLE.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
        );
        level.setBlock(
                placement.worldzero$furnacePos,
                Blocks.FURNACE.defaultBlockState()
                        .setValue(FurnaceBlock.FACING, placement.worldzero$furnaceFacing)
                        .setValue(FurnaceBlock.LIT, true),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
        );
        worldzero$setupWorkstationFurnace(level, placement.worldzero$furnacePos);

        StructureSaveData saveData = worldzero$getSaveData(level);
        saveData.worldzero$workstationPlaced = true;
        saveData.worldzero$workstationOrigin = placement.worldzero$craftingTablePos.asLong();
        saveData.setDirty();
        return true;
    }

    private static boolean worldzero$spawnDebugPathStructure(
            ServerLevel level,
            ServerPlayer player,
            TrackedPlayerState trackedPlayer,
            ResourceLocation structureId,
            boolean domStructure
    ) {
        TemplateInfo templateInfo = worldzero$getTemplateInfo(level, structureId);
        if (templateInfo == null) {
            return false;
        }

        BlockPos origin = domStructure
                ? worldzero$findDebugDomPlacement(level, player, templateInfo.worldzero$size)
                : worldzero$findPathPlacement(
                level,
                player,
                trackedPlayer,
                templateInfo.worldzero$size,
                WORLDZERO_DEBUG_PORTAL_MIN_DISTANCE,
                WORLDZERO_DEBUG_PORTAL_MAX_DISTANCE,
                null,
                0,
                false
        );
        if (origin == null) {
            return false;
        }

        if (!worldzero$placeTemplate(level, templateInfo, origin, domStructure, structureId.equals(WORLDZERO_DOM_ID))) {
            return false;
        }

        if (structureId.equals(WORLDZERO_DOM_ID)) {
            worldzero$populateDomChests(level, origin, templateInfo.worldzero$size);
        } else if (structureId.equals(WORLDZERO_DOM2_ID)) {
            worldzero$populateDomChests(level, origin, templateInfo.worldzero$size);
            List<ChestGroupSnapshot> snapshots = worldzero$captureChestSnapshots(level, origin, templateInfo.worldzero$size);
            worldzero$restoreDom2ChestLoot(level, origin, templateInfo.worldzero$size, snapshots);
        }
        return true;
    }

    private static boolean worldzero$tryDetectPlayerHouse(
            ServerLevel level,
            StructureSaveData saveData,
            SessionState sessionState,
            ServerPlayer player,
            long storyTicks
    ) {
        TrackedPlayerState trackedPlayer = sessionState.worldzero$players.computeIfAbsent(
                player.getUUID(),
                ignored -> new TrackedPlayerState()
        );
        if (trackedPlayer.worldzero$recentBuildBlocks < WORLDZERO_REQUIRED_BUILD_BLOCKS
                || trackedPlayer.worldzero$lastBuildStoryTick < 0L
                || storyTicks - trackedPlayer.worldzero$lastBuildStoryTick > WORLDZERO_BUILD_MEMORY_TICKS
                || storyTicks < trackedPlayer.worldzero$nextHouseCheckStoryTick) {
            return false;
        }

        trackedPlayer.worldzero$nextHouseCheckStoryTick = storyTicks + WORLDZERO_HOUSE_CHECK_INTERVAL_TICKS;
        WorldZeroHouseDetector.DetectedHouse detectedHouse = WorldZeroHouseDetector.worldzero$findContainingHouse(player);
        if (detectedHouse == null) {
            detectedHouse = WorldZeroHouseDetector.worldzero$findNearbyHouseForDebug(player);
        }
        if (detectedHouse == null || !worldzero$isBuildLinkedToHouse(trackedPlayer, detectedHouse)) {
            return false;
        }

        saveData.worldzero$houseDetected = true;
        saveData.worldzero$houseOwnerId = player.getUUID().toString();
        saveData.worldzero$houseCenter = detectedHouse.center().asLong();
        saveData.worldzero$houseInteriorMin = detectedHouse.interiorMin().asLong();
        saveData.worldzero$houseInteriorMax = detectedHouse.interiorMax().asLong();
        saveData.worldzero$houseDoorPos = detectedHouse.doorPos() != null ? detectedHouse.doorPos().asLong() : Long.MIN_VALUE;
        saveData.setDirty();
        return true;
    }

    private static boolean worldzero$isBuildLinkedToHouse(
            TrackedPlayerState trackedPlayer,
            WorldZeroHouseDetector.DetectedHouse detectedHouse
    ) {
        if (trackedPlayer.worldzero$lastBuildPos == null) {
            return false;
        }

        return worldzero$isNearHouseBounds(trackedPlayer.worldzero$lastBuildPos, detectedHouse, 12)
                || (trackedPlayer.worldzero$firstBuildPos != null
                && worldzero$isNearHouseBounds(trackedPlayer.worldzero$firstBuildPos, detectedHouse, 12));
    }

    private static boolean worldzero$isNearHouseBounds(
            BlockPos pos,
            WorldZeroHouseDetector.DetectedHouse detectedHouse,
            int margin
    ) {
        return pos.getX() >= detectedHouse.interiorMin().getX() - margin
                && pos.getX() <= detectedHouse.interiorMax().getX() + margin
                && pos.getZ() >= detectedHouse.interiorMin().getZ() - margin
                && pos.getZ() <= detectedHouse.interiorMax().getZ() + margin;
    }

    private static boolean worldzero$tryPlacePortal(
            ServerLevel level,
            StructureSaveData saveData,
            SessionState sessionState,
            boolean secondPortal
    ) {
        TemplateInfo templateInfo = worldzero$getTemplateInfo(level, secondPortal ? WORLDZERO_PORTAL2_ID : WORLDZERO_PORTAL_ID);
        if (templateInfo == null) {
            return false;
        }

        ServerPlayer player = worldzero$pickTargetPlayer(level);
        if (player == null) {
            return false;
        }

        TrackedPlayerState trackedPlayer = sessionState.worldzero$players.computeIfAbsent(
                player.getUUID(),
                ignored -> new TrackedPlayerState()
        );
        BlockPos avoidPos = secondPortal && saveData.worldzero$portalPlaced ? BlockPos.of(saveData.worldzero$portalOrigin) : null;
        BlockPos origin = worldzero$findPathPlacement(
                level,
                player,
                trackedPlayer,
                templateInfo.worldzero$size,
                WORLDZERO_PORTAL_MIN_DISTANCE,
                WORLDZERO_PORTAL_MAX_DISTANCE,
                avoidPos,
                secondPortal ? WORLDZERO_PORTAL2_SEPARATION : 0,
                false
        );
        if (origin == null || !worldzero$placeTemplate(level, templateInfo, origin, false, false)) {
            return false;
        }

        if (secondPortal) {
            saveData.worldzero$portal2Placed = true;
            saveData.worldzero$portal2Origin = origin.asLong();
        } else {
            saveData.worldzero$portalPlaced = true;
            saveData.worldzero$portalOrigin = origin.asLong();
        }
        saveData.setDirty();
        return true;
    }

    private static boolean worldzero$tryPlaceDom(
            ServerLevel level,
            StructureSaveData saveData,
            SessionState sessionState
    ) {
        TemplateInfo templateInfo = worldzero$getTemplateInfo(level, WORLDZERO_DOM_ID);
        if (templateInfo == null) {
            return false;
        }

        ServerPlayer owner = worldzero$getHouseOwner(level, saveData);
        BlockPos origin = worldzero$findDomPlacement(level, saveData, owner, templateInfo.worldzero$size);
        if (origin == null || !worldzero$placeTemplate(level, templateInfo, origin, true, true)) {
            return false;
        }

        worldzero$populateDomChests(level, origin, templateInfo.worldzero$size);

        long storyTicks = WorldZeroStoryTime.worldzero$getStoryTicks(level);
        saveData.worldzero$domPlaced = true;
        saveData.worldzero$domOrigin = origin.asLong();
        saveData.worldzero$domSeenSoundPlayed = false;
        saveData.worldzero$neighborBuiltMessageTick = Math.max(
                WORLDZERO_JOIN_SAFE_TICKS,
                storyTicks + worldzero$randomTicks(level, WORLDZERO_NEIGHBOR_MIN_DELAY_TICKS, WORLDZERO_NEIGHBOR_MAX_DELAY_TICKS)
        );
        saveData.worldzero$dom2UpgradeTick = storyTicks + worldzero$randomTicks(level, WORLDZERO_DOM2_MIN_DELAY_TICKS, WORLDZERO_DOM2_MAX_DELAY_TICKS);
        saveData.setDirty();
        return true;
    }

    private static boolean worldzero$tryUpgradeDom(ServerLevel level, StructureSaveData saveData) {
        if (!saveData.worldzero$domPlaced) {
            return false;
        }

        TemplateInfo oldTemplate = worldzero$getTemplateInfo(level, WORLDZERO_DOM_ID);
        TemplateInfo newTemplate = worldzero$getTemplateInfo(level, WORLDZERO_DOM2_ID);
        if (oldTemplate == null || newTemplate == null) {
            return false;
        }

        BlockPos origin = BlockPos.of(saveData.worldzero$domOrigin);
        List<ChestGroupSnapshot> chestSnapshots = worldzero$captureChestSnapshots(level, origin, oldTemplate.worldzero$size);
        worldzero$clearContainerContents(level, origin, oldTemplate.worldzero$size);
        Vec3i clearSize = new Vec3i(
                Math.max(oldTemplate.worldzero$size.getX(), newTemplate.worldzero$size.getX()),
                Math.max(oldTemplate.worldzero$size.getY(), newTemplate.worldzero$size.getY()),
                Math.max(oldTemplate.worldzero$size.getZ(), newTemplate.worldzero$size.getZ())
        );
        worldzero$clearArea(level, origin, clearSize);
        if (!worldzero$placeTemplate(level, newTemplate, origin, true, false)) {
            return false;
        }

        worldzero$restoreDom2ChestLoot(level, origin, newTemplate.worldzero$size, chestSnapshots);

        long storyTicks = WorldZeroStoryTime.worldzero$getStoryTicks(level);
        saveData.worldzero$dom2Placed = true;
        saveData.worldzero$dom2ReactionTick = storyTicks + WORLDZERO_DOM2_REACTION_DELAY_TICKS;
        saveData.setDirty();
        return true;
    }

    @Nullable
    private static BlockPos worldzero$findNearestDomOrigin(ServerLevel level, BlockPos playerPos) {
        StructureSaveData saveData = worldzero$getSaveData(level);
        BlockPos bestOrigin = null;
        double bestDistanceSqr = Double.MAX_VALUE;

        if (saveData.worldzero$domOrigin != Long.MIN_VALUE) {
            BlockPos savedOrigin = BlockPos.of(saveData.worldzero$domOrigin);
            if (worldzero$looksLikeDomAt(level, savedOrigin)) {
                double distanceSqr = worldzero$horizontalDistanceSqr(savedOrigin, playerPos);
                if (distanceSqr <= (double) (WORLDZERO_DEBUG_DOM_UPDATE_RADIUS * WORLDZERO_DEBUG_DOM_UPDATE_RADIUS)) {
                    bestOrigin = savedOrigin;
                    bestDistanceSqr = distanceSqr;
                }
            }
        }

        int minX = playerPos.getX() - WORLDZERO_DEBUG_DOM_UPDATE_RADIUS;
        int maxX = playerPos.getX() + WORLDZERO_DEBUG_DOM_UPDATE_RADIUS;
        int minZ = playerPos.getZ() - WORLDZERO_DEBUG_DOM_UPDATE_RADIUS;
        int maxZ = playerPos.getZ() + WORLDZERO_DEBUG_DOM_UPDATE_RADIUS;
        int minY = Math.max(level.getMinBuildHeight(), playerPos.getY() - 24);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, playerPos.getY() + 24);

        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof DoorBlock)
                    || !state.hasProperty(DoorBlock.HALF)
                    || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
                continue;
            }

            BlockPos candidateOrigin = pos.offset(-8, -1, -12);
            if (!worldzero$looksLikeDomAt(level, candidateOrigin)) {
                continue;
            }

            double distanceSqr = worldzero$horizontalDistanceSqr(candidateOrigin, playerPos);
            if (distanceSqr < bestDistanceSqr) {
                bestDistanceSqr = distanceSqr;
                bestOrigin = candidateOrigin.immutable();
            }
        }

        return bestOrigin;
    }

    private static boolean worldzero$looksLikeDomAt(ServerLevel level, BlockPos origin) {
        return level.getBlockState(origin.offset(8, 1, 12)).getBlock() instanceof DoorBlock
                && level.getBlockState(origin.offset(7, 1, 13)).getBlock() instanceof ChestBlock
                && level.getBlockState(origin.offset(7, 1, 14)).getBlock() instanceof ChestBlock;
    }

    private static boolean worldzero$sendHouseOwnerLine(ServerLevel level, StructureSaveData saveData, String messageKey) {
        ServerPlayer owner = worldzero$getHouseOwner(level, saveData);
        return owner != null
                && WorldZeroStoryTime.worldzero$canReceiveStoryEvent(owner)
                && WorldZeroDoubleChatEvent.worldzero$sendSpeakerLineNow(owner, messageKey);
    }

    private static boolean worldzero$tryPlayDomSeenSound(ServerLevel level, StructureSaveData saveData) {
        if (saveData.worldzero$domOrigin == Long.MIN_VALUE) {
            return false;
        }

        TemplateInfo templateInfo = worldzero$getTemplateInfo(level, WORLDZERO_DOM_ID);
        if (templateInfo == null) {
            return false;
        }

        BlockPos origin = BlockPos.of(saveData.worldzero$domOrigin);
        ServerPlayer owner = worldzero$getHouseOwner(level, saveData);
        if (worldzero$tryPlayDomSeenSoundForPlayer(level, owner, origin, templateInfo.worldzero$size)) {
            return true;
        }

        for (ServerPlayer player : level.players()) {
            if (player == owner) {
                continue;
            }
            if (worldzero$tryPlayDomSeenSoundForPlayer(level, player, origin, templateInfo.worldzero$size)) {
                return true;
            }
        }

        return false;
    }

    private static boolean worldzero$tryPlayDomSeenSoundForPlayer(
            ServerLevel level,
            @Nullable ServerPlayer player,
            BlockPos origin,
            Vec3i size
    ) {
        if (player == null
                || !worldzero$isValidPlayer(player)
                || !WorldZeroStoryTime.worldzero$canReceiveStoryEvent(player)
                || !worldzero$isDomVisibleToPlayer(level, player, origin, size)) {
            return false;
        }

        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.AMBIENT_CAVE.value(),
                SoundSource.AMBIENT,
                0.55F,
                0.42F
        );
        return true;
    }

    private static void worldzero$updateTrackedPlayer(SessionState sessionState, ServerPlayer player) {
        TrackedPlayerState trackedPlayer = sessionState.worldzero$players.computeIfAbsent(
                player.getUUID(),
                ignored -> new TrackedPlayerState()
        );
        trackedPlayer.worldzero$previousPosition = player.position();
    }

    @Nullable
    private static ServerPlayer worldzero$pickTargetPlayer(ServerLevel level) {
        ServerPlayer fallback = null;
        for (ServerPlayer player : level.players()) {
            if (!worldzero$isValidPlayer(player)) {
                continue;
            }
            if (WorldZeroStoryTime.worldzero$canReceiveStoryEvent(player)) {
                return player;
            }
            if (fallback == null) {
                fallback = player;
            }
        }
        return fallback;
    }

    @Nullable
    private static ServerPlayer worldzero$getHouseOwner(ServerLevel level, StructureSaveData saveData) {
        if (saveData.worldzero$houseOwnerId.isBlank()) {
            return null;
        }

        try {
            return level.getServer().getPlayerList().getPlayer(UUID.fromString(saveData.worldzero$houseOwnerId));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nullable
    private static BlockPos worldzero$findPathPlacement(
            ServerLevel level,
            ServerPlayer player,
            TrackedPlayerState trackedPlayer,
            Vec3i size,
            int minDistance,
            int maxDistance,
            @Nullable BlockPos avoidPos,
            int avoidDistance,
            boolean domStructure
    ) {
        Vec3 forward = worldzero$getForwardVector(player, trackedPlayer);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);

        for (int attempt = 0; attempt < WORLDZERO_MAX_CANDIDATE_ATTEMPTS; attempt++) {
            double distance = Mth.nextDouble(level.random, minDistance, maxDistance);
            double forwardScale = 0.8D + level.random.nextDouble() * 0.5D;
            double sideScale = Mth.nextDouble(level.random, -8.0D, 8.0D);
            Vec3 target = player.position()
                    .add(forward.scale(distance * forwardScale))
                    .add(right.scale(sideScale));
            BlockPos origin = worldzero$centerOriginOnSurface(
                    level,
                    target,
                    size,
                    -1,
                    domStructure ? WORLDZERO_DOM_SURFACE_VARIANCE : WORLDZERO_PATH_SURFACE_VARIANCE
            );
            if (origin == null) {
                continue;
            }
            if (worldzero$getStructureCenter(origin, size).distanceToSqr(player.position()) < (double) (minDistance * minDistance)) {
                continue;
            }
            if (avoidPos != null && worldzero$horizontalDistanceSqr(origin, avoidPos) < (double) (avoidDistance * avoidDistance)) {
                continue;
            }
            return origin;
        }

        return null;
    }

    @Nullable
    private static BlockPos worldzero$findDebugDomPlacement(
            ServerLevel level,
            ServerPlayer player,
            Vec3i size
    ) {
        for (int attempt = 0; attempt < WORLDZERO_DEBUG_DOM_ATTEMPTS; attempt++) {
            double angle = level.random.nextDouble() * (Math.PI * 2.0D);
            double distance = Mth.nextDouble(level.random, WORLDZERO_DEBUG_DOM_MIN_DISTANCE, WORLDZERO_DEBUG_DOM_MAX_DISTANCE);
            Vec3 target = player.position().add(
                    Math.cos(angle) * distance,
                    0.0D,
                    Math.sin(angle) * distance
            );
            BlockPos origin = worldzero$centerOriginOnSurface(
                    level,
                    target,
                    size,
                    -1,
                    WORLDZERO_DEBUG_DOM_SURFACE_VARIANCE
            );
            if (origin != null
                    && !worldzero$hasNearbyLava(level, origin, size)
                    && worldzero$horizontalDistanceSqr(origin, player.blockPosition())
                    >= (double) (WORLDZERO_DEBUG_DOM_MIN_DISTANCE * WORLDZERO_DEBUG_DOM_MIN_DISTANCE)) {
                return origin;
            }
        }
        return null;
    }

    @Nullable
    private static WorkstationPlacement worldzero$findWorkstationPlacement(
            ServerLevel level,
            ServerPlayer player,
            TrackedPlayerState trackedPlayer
    ) {
        Vec3 forward = worldzero$getForwardVector(player, trackedPlayer);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        Direction sideDirection = Math.abs(right.x) >= Math.abs(right.z)
                ? (right.x >= 0.0D ? Direction.EAST : Direction.WEST)
                : (right.z >= 0.0D ? Direction.SOUTH : Direction.NORTH);
        Direction furnaceFacing = sideDirection;

        for (int attempt = 0; attempt < WORLDZERO_MAX_CANDIDATE_ATTEMPTS; attempt++) {
            double distance = Mth.nextDouble(level.random, WORLDZERO_WORKSTATION_MIN_DISTANCE, WORLDZERO_WORKSTATION_MAX_DISTANCE);
            double forwardScale = 0.8D + level.random.nextDouble() * 0.5D;
            double sideScale = Mth.nextDouble(level.random, -5.0D, 5.0D);
            Vec3 target = player.position()
                    .add(forward.scale(distance * forwardScale))
                    .add(right.scale(sideScale));

            int craftingX = Mth.floor(target.x);
            int craftingZ = Mth.floor(target.z);
            int furnaceX = craftingX + sideDirection.getStepX();
            int furnaceZ = craftingZ + sideDirection.getStepZ();
            int craftingY = worldzero$getSurfaceY(level, craftingX, craftingZ);
            int furnaceY = worldzero$getSurfaceY(level, furnaceX, furnaceZ);
            if (craftingY == Integer.MIN_VALUE
                    || furnaceY == Integer.MIN_VALUE
                    || Math.abs(craftingY - furnaceY) > 1) {
                continue;
            }

            BlockPos craftingPos = new BlockPos(craftingX, craftingY, craftingZ);
            BlockPos furnacePos = new BlockPos(furnaceX, furnaceY, furnaceZ);
            if (worldzero$getStructureCenter(craftingPos, new Vec3i(2, 1, 1)).distanceToSqr(player.position())
                    < (double) (WORLDZERO_WORKSTATION_MIN_DISTANCE * WORLDZERO_WORKSTATION_MIN_DISTANCE)) {
                continue;
            }
            if (!worldzero$canPlaceSimpleBlockAt(level, craftingPos) || !worldzero$canPlaceSimpleBlockAt(level, furnacePos)) {
                continue;
            }

            return new WorkstationPlacement(craftingPos, furnacePos, furnaceFacing);
        }

        return null;
    }

    @Nullable
    private static BlockPos worldzero$findDomPlacement(
            ServerLevel level,
            StructureSaveData saveData,
            @Nullable ServerPlayer owner,
            Vec3i size
    ) {
        BlockPos houseCenter = BlockPos.of(saveData.worldzero$houseCenter);
        for (int attempt = 0; attempt < WORLDZERO_MAX_CANDIDATE_ATTEMPTS; attempt++) {
            double angle = level.random.nextDouble() * (Math.PI * 2.0D);
            double distance = Mth.nextDouble(level.random, WORLDZERO_DOM_MIN_DISTANCE, WORLDZERO_DOM_MAX_DISTANCE);
            Vec3 target = new Vec3(
                    houseCenter.getX() + 0.5D + Math.cos(angle) * distance,
                    houseCenter.getY(),
                    houseCenter.getZ() + 0.5D + Math.sin(angle) * distance
            );
            BlockPos origin = worldzero$centerOriginOnSurface(
                    level,
                    target,
                    size,
                    -1,
                    WORLDZERO_DOM_SURFACE_VARIANCE
            );
            if (origin == null) {
                continue;
            }
            if (worldzero$hasNearbyLava(level, origin, size)) {
                continue;
            }
            if (worldzero$horizontalDistanceSqr(origin, houseCenter) < (double) (WORLDZERO_DOM_MIN_DISTANCE * WORLDZERO_DOM_MIN_DISTANCE)) {
                continue;
            }
            if (owner != null) {
                if (worldzero$horizontalDistanceSqr(origin, owner.blockPosition()) < (double) (WORLDZERO_DOM_PLAYER_MIN_DISTANCE * WORLDZERO_DOM_PLAYER_MIN_DISTANCE)) {
                    continue;
                }
                if (worldzero$isInViewCone(owner, worldzero$getStructureCenter(origin, size))) {
                    continue;
                }
            }
            return origin;
        }
        return null;
    }

    private static Vec3 worldzero$getForwardVector(ServerPlayer player, TrackedPlayerState trackedPlayer) {
        Vec3 currentPos = player.position();
        if (trackedPlayer.worldzero$previousPosition != null) {
            Vec3 movement = currentPos.subtract(trackedPlayer.worldzero$previousPosition);
            Vec3 horizontalMovement = new Vec3(movement.x, 0.0D, movement.z);
            if (horizontalMovement.lengthSqr() > 0.25D) {
                return horizontalMovement.normalize();
            }
        }

        Vec3 look = player.getViewVector(1.0F);
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        return horizontalLook.lengthSqr() > 0.0001D ? horizontalLook.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
    }

    @Nullable
    private static BlockPos worldzero$centerOriginOnSurface(
            ServerLevel level,
            Vec3 target,
            Vec3i size,
            int placementYOffset,
            int maxVariance
    ) {
        int centerX = Mth.floor(target.x);
        int centerZ = Mth.floor(target.z);
        int originX = centerX - Math.max(0, (size.getX() - 1) / 2);
        int originZ = centerZ - Math.max(0, (size.getZ() - 1) / 2);
        FootprintSurface surface = worldzero$measureFootprintSurface(level, originX, originZ, size, maxVariance);
        if (surface == null) {
            return null;
        }

        return new BlockPos(originX, surface.worldzero$surfaceY + placementYOffset, originZ);
    }

    private static boolean worldzero$hasNearbyLava(ServerLevel level, BlockPos origin, Vec3i size) {
        int minX = origin.getX() - WORLDZERO_DOM_LAVA_CHECK_RADIUS;
        int maxX = origin.getX() + size.getX() - 1 + WORLDZERO_DOM_LAVA_CHECK_RADIUS;
        int minZ = origin.getZ() - WORLDZERO_DOM_LAVA_CHECK_RADIUS;
        int maxZ = origin.getZ() + size.getZ() - 1 + WORLDZERO_DOM_LAVA_CHECK_RADIUS;
        int minY = Math.max(level.getMinBuildHeight(), origin.getY() - WORLDZERO_DOM_LAVA_VERTICAL_BELOW);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, origin.getY() + WORLDZERO_DOM_LAVA_VERTICAL_ABOVE);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getFluidState(pos).is(FluidTags.LAVA) || level.getBlockState(pos).is(Blocks.LAVA)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Nullable
    private static FootprintSurface worldzero$measureFootprintSurface(
            ServerLevel level,
            int originX,
            int originZ,
            Vec3i size,
            int maxVariance
    ) {
        int minSurface = Integer.MAX_VALUE;
        int maxSurface = Integer.MIN_VALUE;
        int[] samples = new int[size.getX() * size.getZ()];
        int sampleIndex = 0;

        for (int x = 0; x < size.getX(); x++) {
            for (int z = 0; z < size.getZ(); z++) {
                int worldX = originX + x;
                int worldZ = originZ + z;
                int surfaceY = worldzero$getSurfaceY(level, worldX, worldZ);
                if (surfaceY == Integer.MIN_VALUE) {
                    return null;
                }
                samples[sampleIndex++] = surfaceY;
                minSurface = Math.min(minSurface, surfaceY);
                maxSurface = Math.max(maxSurface, surfaceY);
            }
        }

        if (sampleIndex == 0) {
            return null;
        }

        if (maxSurface - minSurface > maxVariance) {
            return null;
        }

        java.util.Arrays.sort(samples, 0, sampleIndex);
        int medianSurface = samples[sampleIndex / 2];
        return new FootprintSurface(medianSurface);
    }

    private static int worldzero$getSurfaceY(ServerLevel level, int x, int z) {
        int rawTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (rawTop < level.getMinBuildHeight()) {
            return Integer.MIN_VALUE;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, rawTop, z);
        while (cursor.getY() > level.getMinBuildHeight()) {
            BlockState state = level.getBlockState(cursor);
            if (state.isAir()) {
                cursor.move(Direction.DOWN);
                continue;
            }
            if (worldzero$isVegetationOrTree(state)) {
                cursor.move(Direction.DOWN);
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
        return below.isFaceSturdy(level, pos.below(), Direction.UP)
                && below.getFluidState().isEmpty()
                && (state.isAir() || worldzero$isVegetationOrTree(state))
                && (above.isAir() || worldzero$isVegetationOrTree(above))
                && state.getFluidState().isEmpty()
                && above.getFluidState().isEmpty();
    }

    private static void worldzero$clearSimplePlacementArea(ServerLevel level, BlockPos pos) {
        for (int y = 0; y <= 1; y++) {
            BlockPos target = pos.above(y);
            BlockState state = level.getBlockState(target);
            if (!state.isAir()) {
                level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    private static void worldzero$setupWorkstationFurnace(ServerLevel level, BlockPos furnacePos) {
        if (!(level.getBlockEntity(furnacePos) instanceof FurnaceBlockEntity furnace)) {
            return;
        }

        CompoundTag tag = furnace.saveWithoutMetadata();
        tag.putShort("BurnTime", (short) 1200);
        tag.putShort("CookTime", (short) 80);
        tag.putShort("CookTimeTotal", (short) 200);
        furnace.load(tag);
        furnace.setItem(0, new ItemStack(Items.PORKCHOP, 9));
        furnace.setItem(1, new ItemStack(Items.COAL, 22));
        furnace.setItem(2, new ItemStack(Items.COOKED_PORKCHOP, 3));
        furnace.setChanged();
        level.sendBlockUpdated(
                furnacePos,
                level.getBlockState(furnacePos),
                level.getBlockState(furnacePos),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
        );
    }

    private static boolean worldzero$placeTemplate(
            ServerLevel level,
            TemplateInfo templateInfo,
            BlockPos origin,
            boolean domStructure,
            boolean ensureDoorAccess
    ) {
        int minChunkX = origin.getX() >> 4;
        int maxChunkX = (origin.getX() + templateInfo.worldzero$size.getX() - 1) >> 4;
        int minChunkZ = origin.getZ() >> 4;
        int maxChunkZ = (origin.getZ() + templateInfo.worldzero$size.getZ() - 1) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }

        worldzero$preparePlacementArea(level, origin, templateInfo.worldzero$size, domStructure);

        boolean placed = templateInfo.worldzero$template.placeInWorld(
                level,
                origin,
                origin,
                new StructurePlaceSettings().setIgnoreEntities(true),
                level.random,
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
        );
        if (!placed) {
            return false;
        }

        worldzero$fillFoundation(level, origin, templateInfo.worldzero$size);
        if (ensureDoorAccess) {
            worldzero$clearDomDoorAccess(level, origin, templateInfo.worldzero$size);
        }
        return true;
    }

    private static void worldzero$preparePlacementArea(ServerLevel level, BlockPos origin, Vec3i size, boolean domStructure) {
        worldzero$clearStructureVolume(level, origin, size);
        worldzero$clearSurfaceVegetation(level, origin, size, domStructure ? WORLDZERO_DOM_CLEAR_MARGIN : WORLDZERO_GENERIC_CLEAR_MARGIN);
    }

    private static void worldzero$clearStructureVolume(ServerLevel level, BlockPos origin, Vec3i size) {
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY() + WORLDZERO_STRUCTURE_CLEAR_HEADROOM; y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }
    }

    private static void worldzero$clearSurfaceVegetation(ServerLevel level, BlockPos origin, Vec3i size, int margin) {
        int minX = origin.getX() - margin;
        int maxX = origin.getX() + size.getX() - 1 + margin;
        int minZ = origin.getZ() - margin;
        int maxZ = origin.getZ() + size.getZ() - 1 + margin;
        int minY = origin.getY() - 1;
        int maxY = origin.getY() + size.getY() + WORLDZERO_STRUCTURE_CLEAR_HEADROOM;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (worldzero$isVegetationOrTree(state)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }
    }

    private static void worldzero$fillFoundation(ServerLevel level, BlockPos origin, Vec3i size) {
        for (int x = 0; x < size.getX(); x++) {
            for (int z = 0; z < size.getZ(); z++) {
                BlockPos surfacePos = origin.offset(x, -1, z);
                BlockState fillState = worldzero$getFoundationState(level, surfacePos);
                BlockPos.MutableBlockPos cursor = surfacePos.mutable();
                for (int depth = 0; depth < WORLDZERO_FOUNDATION_DEPTH; depth++) {
                    BlockState state = level.getBlockState(cursor);
                    if (state.isFaceSturdy(level, cursor, Direction.UP)
                            && !worldzero$isVegetationOrTree(state)
                            && state.getFluidState().isEmpty()) {
                        break;
                    }
                    level.setBlock(cursor, fillState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    cursor.move(Direction.DOWN);
                }
            }
        }
    }

    private static BlockState worldzero$getFoundationState(ServerLevel level, BlockPos pos) {
        for (int depth = 0; depth < WORLDZERO_FOUNDATION_DEPTH + 3; depth++) {
            BlockPos checkPos = pos.below(depth);
            BlockState state = level.getBlockState(checkPos);
            if (state.isAir() || worldzero$isVegetationOrTree(state) || !state.getFluidState().isEmpty()) {
                continue;
            }

            if (state.is(Blocks.SAND)
                    || state.is(Blocks.RED_SAND)
                    || state.is(Blocks.GRAVEL)
                    || state.is(Blocks.DIRT)
                    || state.is(Blocks.COARSE_DIRT)
                    || state.is(Blocks.PODZOL)
                    || state.is(Blocks.GRASS_BLOCK)) {
                return state;
            }
        }
        return Blocks.DIRT.defaultBlockState();
    }

    private static boolean worldzero$isVegetationOrTree(BlockState state) {
        if (state.isAir()) {
            return false;
        }

        if (state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.FARMLAND)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.STONE)
                || state.is(Blocks.ANDESITE)
                || state.is(Blocks.DIORITE)
                || state.is(Blocks.GRANITE)) {
            return false;
        }

        if (state.getFluidState().isEmpty()) {
            String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            return path.contains("leaf")
                    || path.contains("log")
                    || path.contains("sapling")
                    || path.equals("grass")
                    || path.endsWith("_grass")
                    || path.contains("fern")
                    || path.contains("flower")
                    || path.contains("bush")
                    || path.contains("vine")
                    || path.contains("mushroom")
                    || path.contains("crop")
                    || path.contains("roots")
                    || path.contains("bamboo")
                    || path.contains("cane");
        }
        return false;
    }

    private static void worldzero$clearDomDoorAccess(ServerLevel level, BlockPos origin, Vec3i size) {
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    BlockPos doorPos = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(doorPos);
                    if (!(state.getBlock() instanceof DoorBlock)
                            || !state.hasProperty(DoorBlock.HALF)
                            || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
                        continue;
                    }

                    Direction outward = worldzero$getNearestEdgeDirection(x, z, size);
                    worldzero$clearDoorPath(level, doorPos, outward);
                }
            }
        }
    }

    private static Direction worldzero$getNearestEdgeDirection(int relativeX, int relativeZ, Vec3i size) {
        int westDistance = relativeX;
        int eastDistance = Math.max(0, size.getX() - 1 - relativeX);
        int northDistance = relativeZ;
        int southDistance = Math.max(0, size.getZ() - 1 - relativeZ);
        int minDistance = Math.min(Math.min(westDistance, eastDistance), Math.min(northDistance, southDistance));

        if (minDistance == westDistance) {
            return Direction.WEST;
        }
        if (minDistance == eastDistance) {
            return Direction.EAST;
        }
        if (minDistance == northDistance) {
            return Direction.NORTH;
        }
        return Direction.SOUTH;
    }

    private static void worldzero$clearDoorPath(ServerLevel level, BlockPos doorPos, Direction outward) {
        Direction sideways = outward.getClockWise();
        for (int step = 1; step <= WORLDZERO_DOOR_PATH_LENGTH; step++) {
            for (int side = -WORLDZERO_DOOR_PATH_HALF_WIDTH; side <= WORLDZERO_DOOR_PATH_HALF_WIDTH; side++) {
                BlockPos groundPos = doorPos.relative(outward, step).relative(sideways, side);
                BlockState fillState = worldzero$getFoundationState(level, groundPos.below());
                level.setBlock(groundPos.below(), fillState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                for (int height = 0; height <= 2; height++) {
                    BlockPos clearPos = groundPos.above(height);
                    BlockState state = level.getBlockState(clearPos);
                    if (!state.isAir()) {
                        level.setBlock(clearPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }
    }

    private static void worldzero$clearArea(ServerLevel level, BlockPos origin, Vec3i size) {
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY() + WORLDZERO_STRUCTURE_CLEAR_HEADROOM; y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }
    }

    @Nullable
    private static TemplateInfo worldzero$getTemplateInfo(ServerLevel level, ResourceLocation structureId) {
        Optional<StructureTemplate> optionalTemplate = level.getStructureManager().get(structureId);
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

    private static void worldzero$populateDomChests(ServerLevel level, BlockPos origin, Vec3i size) {
        List<ChestGroup> chestGroups = worldzero$collectChestGroups(level, origin, size);
        for (ChestGroup chestGroup : chestGroups) {
            worldzero$fillDomChestGroup(level, chestGroup);
        }
    }

    private static void worldzero$fillDomChestGroup(ServerLevel level, ChestGroup chestGroup) {
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

        int fillPercent = Mth.nextInt(level.random, 60, 80);
        int targetStacks = Math.max(1, (totalSlots * fillPercent) / 100);
        List<ItemStack> lootStacks = new ArrayList<>();
        for (int index = 0; index < targetStacks; index++) {
            lootStacks.add(worldzero$createRandomDomLootStack(level));
        }
        worldzero$applyStacksToContainers(containers, lootStacks, chestGroup.worldzero$anchorPos.asLong() ^ level.getGameTime());
    }

    private static List<ChestGroupSnapshot> worldzero$captureChestSnapshots(ServerLevel level, BlockPos origin, Vec3i size) {
        List<ChestGroupSnapshot> snapshots = new ArrayList<>();
        for (ChestGroup chestGroup : worldzero$collectChestGroups(level, origin, size)) {
            List<ItemStack> stacks = new ArrayList<>();
            for (BlockPos pos : chestGroup.worldzero$positions) {
                if (!(level.getBlockEntity(pos) instanceof Container container)) {
                    continue;
                }

                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    ItemStack stack = container.getItem(slot);
                    if (!stack.isEmpty()) {
                        stacks.add(stack.copy());
                    }
                }
            }
            snapshots.add(new ChestGroupSnapshot(chestGroup.worldzero$anchorPos, stacks));
        }
        return snapshots;
    }

    private static void worldzero$clearContainerContents(ServerLevel level, BlockPos origin, Vec3i size) {
        for (int x = origin.getX(); x < origin.getX() + size.getX(); x++) {
            for (int y = origin.getY(); y < origin.getY() + size.getY(); y++) {
                for (int z = origin.getZ(); z < origin.getZ() + size.getZ(); z++) {
                    if (!(level.getBlockEntity(new BlockPos(x, y, z)) instanceof Container container)) {
                        continue;
                    }

                    for (int slot = 0; slot < container.getContainerSize(); slot++) {
                        container.setItem(slot, ItemStack.EMPTY);
                    }
                    container.setChanged();
                }
            }
        }
    }

    private static void worldzero$restoreDom2ChestLoot(
            ServerLevel level,
            BlockPos origin,
            Vec3i size,
            List<ChestGroupSnapshot> snapshots
    ) {
        List<ChestGroup> chestGroups = worldzero$collectChestGroups(level, origin, size);
        if (chestGroups.isEmpty()) {
            return;
        }

        Set<Integer> disturbedGroups = new HashSet<>();
        int disturbCount = chestGroups.size() <= 1 ? 0 : Mth.nextInt(level.random, 1, Math.max(1, chestGroups.size() - 1));
        while (disturbedGroups.size() < disturbCount) {
            disturbedGroups.add(level.random.nextInt(chestGroups.size()));
        }

        List<ChestGroupSnapshot> remainingSnapshots = new ArrayList<>(snapshots);
        for (int index = 0; index < chestGroups.size(); index++) {
            ChestGroup chestGroup = chestGroups.get(index);
            int bestSnapshotIndex = worldzero$findBestSnapshotIndex(chestGroup, remainingSnapshots);
            List<ItemStack> stacks = new ArrayList<>();
            if (bestSnapshotIndex >= 0) {
                ChestGroupSnapshot snapshot = remainingSnapshots.remove(bestSnapshotIndex);
                for (ItemStack stack : snapshot.worldzero$stacks) {
                    stacks.add(stack.copy());
                }
            }

            if (stacks.isEmpty()) {
                int fallbackStacks = Math.max(1, chestGroup.worldzero$totalSlots / 3);
                for (int count = 0; count < fallbackStacks; count++) {
                    stacks.add(worldzero$createRandomDomLootStack(level));
                }
            }

            Collections.shuffle(stacks, new java.util.Random(level.random.nextLong()));
            if (disturbedGroups.contains(index)) {
                worldzero$disturbLootStacks(level, stacks);
            }
            worldzero$applyStacksToContainers(worldzero$getContainers(level, chestGroup), stacks, chestGroup.worldzero$anchorPos.asLong() ^ level.getGameTime());
        }
    }

    private static int worldzero$findBestSnapshotIndex(ChestGroup chestGroup, List<ChestGroupSnapshot> snapshots) {
        int bestIndex = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int index = 0; index < snapshots.size(); index++) {
            ChestGroupSnapshot snapshot = snapshots.get(index);
            double distance = worldzero$horizontalDistanceSqr(chestGroup.worldzero$anchorPos, snapshot.worldzero$anchorPos);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private static void worldzero$disturbLootStacks(ServerLevel level, List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return;
        }

        int removePercent = Mth.nextInt(level.random, 10, 40);
        int removeCount = Math.max(1, (stacks.size() * removePercent) / 100);
        for (int removed = 0; removed < removeCount && !stacks.isEmpty(); removed++) {
            stacks.remove(level.random.nextInt(stacks.size()));
        }

        for (ItemStack stack : stacks) {
            if (stack.getCount() > 1 && level.random.nextFloat() < 0.35F) {
                int shrink = Math.max(1, (stack.getCount() * Mth.nextInt(level.random, 15, 45)) / 100);
                stack.shrink(shrink);
            }
        }
        stacks.removeIf(ItemStack::isEmpty);
    }

    private static ItemStack worldzero$createRandomDomLootStack(ServerLevel level) {
        int totalWeight = 0;
        for (DomLootEntry entry : WORLDZERO_DOM_LOOT_POOL) {
            totalWeight += entry.worldzero$weight;
        }

        int target = level.random.nextInt(Math.max(1, totalWeight));
        DomLootEntry selected = WORLDZERO_DOM_LOOT_POOL[0];
        for (DomLootEntry entry : WORLDZERO_DOM_LOOT_POOL) {
            target -= entry.worldzero$weight;
            if (target < 0) {
                selected = entry;
                break;
            }
        }

        int count = selected.worldzero$minCount;
        if (selected.worldzero$maxCount > selected.worldzero$minCount) {
            count += level.random.nextInt(selected.worldzero$maxCount - selected.worldzero$minCount + 1);
        }
        ItemStack stack = new ItemStack(selected.worldzero$item, count);
        if (selected.worldzero$damageable) {
            int maxDamage = stack.getMaxDamage();
            int minAppliedDamage = Math.max(1, (maxDamage * selected.worldzero$minDamagePercent) / 100);
            int maxAppliedDamage = Math.max(minAppliedDamage, (maxDamage * selected.worldzero$maxDamagePercent) / 100);
            stack.setDamageValue(minAppliedDamage + level.random.nextInt(maxAppliedDamage - minAppliedDamage + 1));
        }
        return stack;
    }

    private static void worldzero$applyStacksToContainers(List<Container> containers, List<ItemStack> stacks, long shuffleSeed) {
        int totalSlots = 0;
        for (Container container : containers) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                container.setItem(slot, ItemStack.EMPTY);
            }
            container.setChanged();
            totalSlots += container.getContainerSize();
        }
        if (containers.isEmpty() || totalSlots <= 0) {
            return;
        }

        List<Integer> slotOrder = new ArrayList<>(totalSlots);
        for (int slot = 0; slot < totalSlots; slot++) {
            slotOrder.add(slot);
        }
        Collections.shuffle(slotOrder, new java.util.Random(shuffleSeed));

        int limit = Math.min(slotOrder.size(), stacks.size());
        for (int index = 0; index < limit; index++) {
            int combinedSlot = slotOrder.get(index);
            ItemStack stack = stacks.get(index);
            int remainingSlot = combinedSlot;
            for (Container container : containers) {
                if (remainingSlot < container.getContainerSize()) {
                    container.setItem(remainingSlot, stack.copy());
                    break;
                }
                remainingSlot -= container.getContainerSize();
            }
        }

        for (Container container : containers) {
            container.setChanged();
        }
    }

    private static List<ChestGroup> worldzero$collectChestGroups(ServerLevel level, BlockPos origin, Vec3i size) {
        List<ChestGroup> chestGroups = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        for (int x = origin.getX(); x < origin.getX() + size.getX(); x++) {
            for (int y = origin.getY(); y < origin.getY() + size.getY(); y++) {
                for (int z = origin.getZ(); z < origin.getZ() + size.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (visited.contains(pos)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof ChestBlock) || !(level.getBlockEntity(pos) instanceof Container)) {
                        continue;
                    }

                    List<BlockPos> positions = new ArrayList<>();
                    positions.add(pos.immutable());
                    visited.add(pos.immutable());

                    if (state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                        BlockPos otherPos = pos.relative(ChestBlock.getConnectedDirection(state));
                        if (level.getBlockState(otherPos).getBlock() instanceof ChestBlock
                                && level.getBlockEntity(otherPos) instanceof Container) {
                            positions.add(otherPos.immutable());
                            visited.add(otherPos.immutable());
                        }
                    }

                    positions.sort(Comparator
                            .comparingInt((BlockPos blockPos) -> blockPos.getZ())
                            .thenComparingInt(BlockPos::getX)
                            .thenComparingInt(BlockPos::getY));
                    int totalSlots = 0;
                    for (BlockPos chestPos : positions) {
                        if (level.getBlockEntity(chestPos) instanceof Container container) {
                            totalSlots += container.getContainerSize();
                        }
                    }
                    chestGroups.add(new ChestGroup(positions, totalSlots));
                }
            }
        }

        chestGroups.sort(Comparator
                .comparingInt((ChestGroup group) -> group.worldzero$anchorPos.getZ())
                .thenComparingInt(group -> group.worldzero$anchorPos.getX())
                .thenComparingInt(group -> group.worldzero$anchorPos.getY()));
        return chestGroups;
    }

    private static List<Container> worldzero$getContainers(ServerLevel level, ChestGroup chestGroup) {
        List<Container> containers = new ArrayList<>();
        for (BlockPos pos : chestGroup.worldzero$positions) {
            if (level.getBlockEntity(pos) instanceof Container container) {
                containers.add(container);
            }
        }
        return containers;
    }

    private static long worldzero$randomTicks(ServerLevel level, long minTicks, long maxTicks) {
        if (minTicks >= maxTicks) {
            return minTicks;
        }

        long bound = maxTicks - minTicks + 1L;
        return minTicks + Math.floorMod(level.random.nextLong(), bound);
    }

    private static double worldzero$horizontalDistanceSqr(BlockPos first, BlockPos second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private static boolean worldzero$isDomVisibleToPlayer(ServerLevel level, ServerPlayer player, BlockPos origin, Vec3i size) {
        if (worldzero$horizontalDistanceSqr(origin, player.blockPosition()) > (double) (72 * 72)) {
            return false;
        }

        double[] xFractions = {0.2D, 0.5D, 0.8D};
        double[] yFractions = {0.32D, 0.58D};
        double[] zFractions = {0.2D, 0.5D, 0.8D};
        for (double yFraction : yFractions) {
            for (double xFraction : xFractions) {
                for (double zFraction : zFractions) {
                    Vec3 sample = worldzero$sampleStructurePoint(origin, size, xFraction, yFraction, zFraction);
                    if (!worldzero$isInViewCone(player, sample)) {
                        continue;
                    }
                    if (worldzero$hasClearSightToStructure(level, player, origin, size, sample)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static Vec3 worldzero$sampleStructurePoint(
            BlockPos origin,
            Vec3i size,
            double xFraction,
            double yFraction,
            double zFraction
    ) {
        double x = origin.getX() + Math.max(0.5D, (size.getX() - 1) * xFraction + 0.5D);
        double y = origin.getY() + Math.max(0.25D, (size.getY() - 1) * yFraction + 0.25D);
        double z = origin.getZ() + Math.max(0.5D, (size.getZ() - 1) * zFraction + 0.5D);
        return new Vec3(x, y, z);
    }

    private static boolean worldzero$hasClearSightToStructure(
            ServerLevel level,
            ServerPlayer player,
            BlockPos origin,
            Vec3i size,
            Vec3 target
    ) {
        HitResult hitResult = level.clip(new ClipContext(
                player.getEyePosition(),
                target,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        if (hitResult.getType() == HitResult.Type.MISS) {
            return true;
        }
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return false;
        }

        BlockPos hitPos = ((BlockHitResult) hitResult).getBlockPos();
        return hitPos.getX() >= origin.getX()
                && hitPos.getX() < origin.getX() + size.getX()
                && hitPos.getY() >= origin.getY()
                && hitPos.getY() < origin.getY() + size.getY()
                && hitPos.getZ() >= origin.getZ()
                && hitPos.getZ() < origin.getZ() + size.getZ();
    }

    private static boolean worldzero$isInViewCone(ServerPlayer player, Vec3 target) {
        Vec3 toTarget = target.subtract(player.getEyePosition());
        Vec3 look = player.getViewVector(1.0F);
        return toTarget.lengthSqr() > 0.0001D && look.normalize().dot(toTarget.normalize()) >= 0.2D;
    }

    private static Vec3 worldzero$getStructureCenter(BlockPos origin, Vec3i size) {
        return new Vec3(
                origin.getX() + size.getX() * 0.5D,
                origin.getY() + size.getY() * 0.5D,
                origin.getZ() + size.getZ() * 0.5D
        );
    }

    private static boolean worldzero$isValidPlayer(ServerPlayer player) {
        return player != null && player.isAlive() && !player.isSpectator();
    }

    private static StructureSaveData worldzero$getSaveData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                StructureSaveData::worldzero$load,
                StructureSaveData::new,
                WORLDZERO_SAVE_ID
        );
    }

    private static final class SessionState {
        private final Map<UUID, TrackedPlayerState> worldzero$players = new HashMap<>();
    }

    private static final class TrackedPlayerState {
        @Nullable
        private Vec3 worldzero$previousPosition;
        @Nullable
        private BlockPos worldzero$firstBuildPos;
        @Nullable
        private BlockPos worldzero$lastBuildPos;
        private int worldzero$recentBuildBlocks;
        private long worldzero$lastBuildStoryTick = -1L;
        private long worldzero$nextHouseCheckStoryTick;
    }

    private static final class FootprintSurface {
        private final int worldzero$surfaceY;

        private FootprintSurface(int surfaceY) {
            this.worldzero$surfaceY = surfaceY;
        }
    }

    private static final class WorkstationPlacement {
        private final BlockPos worldzero$craftingTablePos;
        private final BlockPos worldzero$furnacePos;
        private final Direction worldzero$furnaceFacing;

        private WorkstationPlacement(BlockPos craftingTablePos, BlockPos furnacePos, Direction furnaceFacing) {
            this.worldzero$craftingTablePos = craftingTablePos.immutable();
            this.worldzero$furnacePos = furnacePos.immutable();
            this.worldzero$furnaceFacing = furnaceFacing;
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

    private static final class ChestGroup {
        private final BlockPos worldzero$anchorPos;
        private final List<BlockPos> worldzero$positions;
        private final int worldzero$totalSlots;

        private ChestGroup(List<BlockPos> positions, int totalSlots) {
            this.worldzero$positions = positions;
            this.worldzero$anchorPos = positions.get(0);
            this.worldzero$totalSlots = totalSlots;
        }
    }

    private static final class ChestGroupSnapshot {
        private final BlockPos worldzero$anchorPos;
        private final List<ItemStack> worldzero$stacks;

        private ChestGroupSnapshot(BlockPos anchorPos, List<ItemStack> stacks) {
            this.worldzero$anchorPos = anchorPos.immutable();
            this.worldzero$stacks = stacks;
        }
    }

    private static final class DomLootEntry {
        private final Item worldzero$item;
        private final int worldzero$minCount;
        private final int worldzero$maxCount;
        private final int worldzero$weight;
        private final boolean worldzero$damageable;
        private final int worldzero$minDamagePercent;
        private final int worldzero$maxDamagePercent;

        private DomLootEntry(Item item, int minCount, int maxCount, int weight) {
            this(item, minCount, maxCount, weight, 0, 0);
        }

        private DomLootEntry(
                Item item,
                int minCount,
                int maxCount,
                int weight,
                int minDamagePercent,
                int maxDamagePercent
        ) {
            this.worldzero$item = item;
            this.worldzero$minCount = minCount;
            this.worldzero$maxCount = maxCount;
            this.worldzero$weight = weight;
            this.worldzero$damageable = maxDamagePercent > 0;
            this.worldzero$minDamagePercent = minDamagePercent;
            this.worldzero$maxDamagePercent = maxDamagePercent;
        }
    }

    private static final class StructureSaveData extends SavedData {
        private boolean worldzero$workstationPlaced;
        private boolean worldzero$portalPlaced;
        private boolean worldzero$portal2Placed;
        private boolean worldzero$houseDetected;
        private boolean worldzero$domPlaced;
        private boolean worldzero$dom2Placed;
        private boolean worldzero$domSeenSoundPlayed;
        private boolean worldzero$neighborBuiltMessageSent;
        private boolean worldzero$dom2ReactionSent;
        private long worldzero$workstationOrigin = Long.MIN_VALUE;
        private long worldzero$portalOrigin = Long.MIN_VALUE;
        private long worldzero$portal2Origin = Long.MIN_VALUE;
        private long worldzero$houseCenter = Long.MIN_VALUE;
        private long worldzero$houseInteriorMin = Long.MIN_VALUE;
        private long worldzero$houseInteriorMax = Long.MIN_VALUE;
        private long worldzero$houseDoorPos = Long.MIN_VALUE;
        private long worldzero$domOrigin = Long.MIN_VALUE;
        private long worldzero$neighborBuiltMessageTick = -1L;
        private long worldzero$dom2UpgradeTick = -1L;
        private long worldzero$dom2ReactionTick = -1L;
        private long worldzero$workstationRetryAfterTick;
        private long worldzero$portalRetryAfterTick;
        private long worldzero$portal2RetryAfterTick;
        private long worldzero$domRetryAfterTick;
        private String worldzero$houseOwnerId = "";

        private static StructureSaveData worldzero$load(CompoundTag tag) {
            StructureSaveData saveData = new StructureSaveData();
            saveData.worldzero$workstationPlaced = tag.getBoolean("workstation_placed");
            saveData.worldzero$portalPlaced = tag.getBoolean("portal_placed");
            saveData.worldzero$portal2Placed = tag.getBoolean("portal2_placed");
            saveData.worldzero$houseDetected = tag.getBoolean("house_detected");
            saveData.worldzero$domPlaced = tag.getBoolean("dom_placed");
            saveData.worldzero$dom2Placed = tag.getBoolean("dom2_placed");
            saveData.worldzero$domSeenSoundPlayed = tag.getBoolean("dom_seen_sound_played");
            saveData.worldzero$neighborBuiltMessageSent = tag.getBoolean("neighbor_built_message_sent");
            saveData.worldzero$dom2ReactionSent = tag.getBoolean("dom2_reaction_sent");
            saveData.worldzero$workstationOrigin = tag.contains("workstation_origin") ? tag.getLong("workstation_origin") : Long.MIN_VALUE;
            saveData.worldzero$portalOrigin = tag.contains("portal_origin") ? tag.getLong("portal_origin") : Long.MIN_VALUE;
            saveData.worldzero$portal2Origin = tag.contains("portal2_origin") ? tag.getLong("portal2_origin") : Long.MIN_VALUE;
            saveData.worldzero$houseCenter = tag.contains("house_center") ? tag.getLong("house_center") : Long.MIN_VALUE;
            saveData.worldzero$houseInteriorMin = tag.contains("house_interior_min") ? tag.getLong("house_interior_min") : Long.MIN_VALUE;
            saveData.worldzero$houseInteriorMax = tag.contains("house_interior_max") ? tag.getLong("house_interior_max") : Long.MIN_VALUE;
            saveData.worldzero$houseDoorPos = tag.contains("house_door_pos") ? tag.getLong("house_door_pos") : Long.MIN_VALUE;
            saveData.worldzero$domOrigin = tag.contains("dom_origin") ? tag.getLong("dom_origin") : Long.MIN_VALUE;
            saveData.worldzero$neighborBuiltMessageTick = tag.contains("neighbor_built_message_tick")
                    ? tag.getLong("neighbor_built_message_tick")
                    : -1L;
            saveData.worldzero$dom2UpgradeTick = tag.contains("dom2_upgrade_tick")
                    ? tag.getLong("dom2_upgrade_tick")
                    : -1L;
            saveData.worldzero$dom2ReactionTick = tag.contains("dom2_reaction_tick")
                    ? tag.getLong("dom2_reaction_tick")
                    : -1L;
            saveData.worldzero$workstationRetryAfterTick = tag.getLong("workstation_retry_after_tick");
            saveData.worldzero$portalRetryAfterTick = tag.getLong("portal_retry_after_tick");
            saveData.worldzero$portal2RetryAfterTick = tag.getLong("portal2_retry_after_tick");
            saveData.worldzero$domRetryAfterTick = tag.getLong("dom_retry_after_tick");
            saveData.worldzero$houseOwnerId = tag.getString("house_owner_id");
            return saveData;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean("workstation_placed", this.worldzero$workstationPlaced);
            tag.putBoolean("portal_placed", this.worldzero$portalPlaced);
            tag.putBoolean("portal2_placed", this.worldzero$portal2Placed);
            tag.putBoolean("house_detected", this.worldzero$houseDetected);
            tag.putBoolean("dom_placed", this.worldzero$domPlaced);
            tag.putBoolean("dom2_placed", this.worldzero$dom2Placed);
            tag.putBoolean("dom_seen_sound_played", this.worldzero$domSeenSoundPlayed);
            tag.putBoolean("neighbor_built_message_sent", this.worldzero$neighborBuiltMessageSent);
            tag.putBoolean("dom2_reaction_sent", this.worldzero$dom2ReactionSent);
            if (this.worldzero$workstationOrigin != Long.MIN_VALUE) {
                tag.putLong("workstation_origin", this.worldzero$workstationOrigin);
            }
            if (this.worldzero$portalOrigin != Long.MIN_VALUE) {
                tag.putLong("portal_origin", this.worldzero$portalOrigin);
            }
            if (this.worldzero$portal2Origin != Long.MIN_VALUE) {
                tag.putLong("portal2_origin", this.worldzero$portal2Origin);
            }
            if (this.worldzero$houseCenter != Long.MIN_VALUE) {
                tag.putLong("house_center", this.worldzero$houseCenter);
            }
            if (this.worldzero$houseInteriorMin != Long.MIN_VALUE) {
                tag.putLong("house_interior_min", this.worldzero$houseInteriorMin);
            }
            if (this.worldzero$houseInteriorMax != Long.MIN_VALUE) {
                tag.putLong("house_interior_max", this.worldzero$houseInteriorMax);
            }
            if (this.worldzero$houseDoorPos != Long.MIN_VALUE) {
                tag.putLong("house_door_pos", this.worldzero$houseDoorPos);
            }
            if (this.worldzero$domOrigin != Long.MIN_VALUE) {
                tag.putLong("dom_origin", this.worldzero$domOrigin);
            }
            tag.putLong("neighbor_built_message_tick", this.worldzero$neighborBuiltMessageTick);
            tag.putLong("dom2_upgrade_tick", this.worldzero$dom2UpgradeTick);
            tag.putLong("dom2_reaction_tick", this.worldzero$dom2ReactionTick);
            tag.putLong("workstation_retry_after_tick", this.worldzero$workstationRetryAfterTick);
            tag.putLong("portal_retry_after_tick", this.worldzero$portalRetryAfterTick);
            tag.putLong("portal2_retry_after_tick", this.worldzero$portal2RetryAfterTick);
            tag.putLong("dom_retry_after_tick", this.worldzero$domRetryAfterTick);
            tag.putString("house_owner_id", this.worldzero$houseOwnerId);
            return tag;
        }
    }
}
