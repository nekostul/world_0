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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
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
    private static final String WORLDZERO_SAVE_ID = "worldzero_koridor_dimension";
    private static final BlockPos WORLDZERO_BASE_ORIGIN = new BlockPos(-8, 64, 0);
    private static final int WORLDZERO_GENERATION_RADIUS_SEGMENTS = 2;
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
        if (previousSegmentIndex != null && previousSegmentIndex == segmentIndex) {
            return;
        }

        worldzero$ensureSegmentsAround(level, segmentIndex, templateInfo);
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_SERVER_STATES.remove(event.getServer());
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

    private static final class SessionState {
        private final Map<UUID, Long> worldzero$lastEnsuredSegmentByPlayer = new HashMap<>();
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

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putLongArray("GeneratedSegments", this.worldzero$generatedSegments.toLongArray());

            CompoundTag returnPointsTag = new CompoundTag();
            for (Map.Entry<UUID, ReturnPoint> entry : this.worldzero$returnPoints.entrySet()) {
                returnPointsTag.put(entry.getKey().toString(), entry.getValue().worldzero$save());
            }
            tag.put("ReturnPoints", returnPointsTag);
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
