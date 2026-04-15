package ru.nekostul.worldzero;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

public final class WorldZeroHouseDetector {
    private static final int WORLDZERO_MIN_HOUSE_WIDTH = 4;
    private static final int WORLDZERO_MIN_HOUSE_LENGTH = 4;
    private static final int WORLDZERO_MIN_HOUSE_HEIGHT = 3;

    private WorldZeroHouseDetector() {
    }

    @Nullable
    public static DetectedHouse worldzero$findNearbyHouse(ServerPlayer player) {
        return worldzero$findNearbyHouse(player, true);
    }

    @Nullable
    public static DetectedHouse worldzero$findNearbyHouseForDebug(ServerPlayer player) {
        return worldzero$findNearbyHouse(player, false);
    }

    @Nullable
    public static DetectedHouse worldzero$findContainingHouse(ServerPlayer player) {
        DetectedHouse detectedHouse = worldzero$findNearbyHouse(player, false);
        if (detectedHouse == null) {
            return null;
        }

        return worldzero$isPlayerInsideHouse(player, detectedHouse) ? detectedHouse : null;
    }

    public static boolean worldzero$isPlayerInsideHouse(ServerPlayer player, DetectedHouse detectedHouse) {
        AABB playerBox = player.getBoundingBox();
        double minX = detectedHouse.interiorMin().getX();
        double maxX = detectedHouse.interiorMax().getX() + 1.0D;
        double minY = detectedHouse.interiorMin().getY();
        double maxY = detectedHouse.interiorMax().getY() + 1.95D;
        double minZ = detectedHouse.interiorMin().getZ();
        double maxZ = detectedHouse.interiorMax().getZ() + 1.0D;

        boolean intersectsInterior = playerBox.maxX > minX
                && playerBox.minX < maxX
                && playerBox.maxY > minY
                && playerBox.minY < maxY
                && playerBox.maxZ > minZ
                && playerBox.minZ < maxZ;
        if (!intersectsInterior) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        BlockPos feetPos = player.blockPosition();
        BlockPos eyePos = BlockPos.containing(player.getEyePosition());
        return !level.canSeeSky(feetPos) || !level.canSeeSky(eyePos);
    }

    @Nullable
    private static DetectedHouse worldzero$findNearbyHouse(ServerPlayer player, boolean enforceTriggerDistance) {
        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();
        int searchRadius = Math.max(
                WorldZeroConfig.worldzero$houseSearchRadiusBlocks(),
                (int) Math.ceil(WorldZeroConfig.worldzero$houseTriggerDistanceMaxBlocks()) + 10
        );
        int verticalRange = WorldZeroConfig.worldzero$houseSearchVerticalRangeBlocks();
        LongSet visited = new LongOpenHashSet();
        DetectedHouse bestHouse = null;

        for (int y = playerPos.getY() - verticalRange; y <= playerPos.getY() + verticalRange; y++) {
            for (int x = playerPos.getX() - searchRadius; x <= playerPos.getX() + searchRadius; x++) {
                for (int z = playerPos.getZ() - searchRadius; z <= playerPos.getZ() + searchRadius; z++) {
                    double dx = player.getX() - (x + 0.5D);
                    double dz = player.getZ() - (z + 0.5D);
                    if (dx * dx + dz * dz > (double) (searchRadius * searchRadius)) {
                        continue;
                    }

                    BlockPos candidate = new BlockPos(x, y, z);
                    long candidateKey = candidate.asLong();
                    if (visited.contains(candidateKey)) {
                        continue;
                    }

                    if (!worldzero$isCandidateInterior(level, candidate)) {
                        continue;
                    }

                    EvaluatedRoom room = worldzero$scanRoom(level, candidate, visited);
                    if (room == null || room.detectedHouse == null) {
                        continue;
                    }

                    DetectedHouse detectedHouse = room.detectedHouse;
                    if (enforceTriggerDistance
                            && !detectedHouse.worldzero$isWithinTriggerDistanceRange(player.getX(), player.getZ())) {
                        continue;
                    }

                    if (bestHouse == null
                            || detectedHouse.score() > bestHouse.score()
                            || (detectedHouse.score() == bestHouse.score()
                            && detectedHouse.worldzero$horizontalDistanceToBoundsSqr(player.getX(), player.getZ())
                            < bestHouse.worldzero$horizontalDistanceToBoundsSqr(player.getX(), player.getZ()))) {
                        bestHouse = detectedHouse;
                    }
                }
            }
        }

        return bestHouse;
    }

    private static boolean worldzero$isCandidateInterior(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir()) {
            return false;
        }

        if (!level.getBlockState(pos.above()).isAir()) {
            return false;
        }

        BlockState belowState = level.getBlockState(pos.below());
        return !belowState.isAir() && belowState.getFluidState().isEmpty();
    }

    @Nullable
    private static EvaluatedRoom worldzero$scanRoom(ServerLevel level, BlockPos start, LongSet globalVisited) {
        int maxHorizontalRadius = WorldZeroConfig.worldzero$houseRoomMaxHorizontalRadiusBlocks();
        int maxHeight = WorldZeroConfig.worldzero$houseRoomMaxHeightBlocks();
        int maxVolume = (maxHorizontalRadius * 2 + 1) * (maxHorizontalRadius * 2 + 1) * (maxHeight + 2);
        LongSet localVisited = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        localVisited.add(start.asLong());

        int minX = start.getX();
        int maxX = start.getX();
        int minY = start.getY();
        int maxY = start.getY();
        int minZ = start.getZ();
        int maxZ = start.getZ();

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            minX = Math.min(minX, current.getX());
            maxX = Math.max(maxX, current.getX());
            minY = Math.min(minY, current.getY());
            maxY = Math.max(maxY, current.getY());
            minZ = Math.min(minZ, current.getZ());
            maxZ = Math.max(maxZ, current.getZ());

            if (maxX - minX + 1 > maxHorizontalRadius * 2 + 1
                    || maxZ - minZ + 1 > maxHorizontalRadius * 2 + 1
                    || maxY - minY + 1 > maxHeight) {
                globalVisited.addAll(localVisited);
                return null;
            }

            if (localVisited.size() > maxVolume) {
                globalVisited.addAll(localVisited);
                return null;
            }

            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (Math.abs(next.getX() - start.getX()) > maxHorizontalRadius
                        || Math.abs(next.getZ() - start.getZ()) > maxHorizontalRadius
                        || next.getY() < start.getY() - 2
                        || next.getY() > start.getY() + maxHeight) {
                    continue;
                }

                long nextKey = next.asLong();
                if (localVisited.contains(nextKey)) {
                    continue;
                }

                if (!level.getBlockState(next).isAir()) {
                    continue;
                }

                localVisited.add(nextKey);
                queue.addLast(next);
            }
        }

        globalVisited.addAll(localVisited);

        int width = maxX - minX + 1;
        int length = maxZ - minZ + 1;
        int height = maxY - minY + 1;
        int structureWidth = width + 2;
        int structureLength = length + 2;
        int structureHeight = height + 2;
        if (structureWidth < WORLDZERO_MIN_HOUSE_WIDTH
                || structureLength < WORLDZERO_MIN_HOUSE_LENGTH
                || structureHeight < WORLDZERO_MIN_HOUSE_HEIGHT) {
            return new EvaluatedRoom(null);
        }

        int wallChecks = 0;
        int wallClosed = 0;
        int roofChecks = 0;
        int roofClosed = 0;
        Map<Long, Integer> minYByColumn = new HashMap<>();
        Map<Long, Integer> maxYByColumn = new HashMap<>();

        for (long packedPos : localVisited) {
            BlockPos interiorPos = BlockPos.of(packedPos);
            long columnKey = worldzero$columnKey(interiorPos.getX(), interiorPos.getZ());
            minYByColumn.merge(columnKey, interiorPos.getY(), Math::min);
            maxYByColumn.merge(columnKey, interiorPos.getY(), Math::max);

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos neighborPos = interiorPos.relative(direction);
                if (localVisited.contains(neighborPos.asLong())) {
                    continue;
                }

                wallChecks++;
                if (worldzero$isClosedBoundary(level, neighborPos)) {
                    wallClosed++;
                }
            }
        }

        for (Map.Entry<Long, Integer> entry : minYByColumn.entrySet()) {
            int x = worldzero$columnX(entry.getKey());
            int z = worldzero$columnZ(entry.getKey());

            BlockPos floorPos = new BlockPos(x, entry.getValue() - 1, z);
            wallChecks++;
            if (worldzero$isClosedBoundary(level, floorPos)) {
                wallClosed++;
            }
        }

        for (Map.Entry<Long, Integer> entry : maxYByColumn.entrySet()) {
            int x = worldzero$columnX(entry.getKey());
            int z = worldzero$columnZ(entry.getKey());

            BlockPos roofPos = new BlockPos(x, entry.getValue() + 1, z);
            roofChecks++;
            if (worldzero$isClosedBoundary(level, roofPos)) {
                roofClosed++;
            }
        }

        if (wallChecks == 0 || roofChecks == 0) {
            return new EvaluatedRoom(null);
        }

        double wallCoverage = (double) wallClosed / (double) wallChecks;
        double roofCoverage = (double) roofClosed / (double) roofChecks;
        if (wallCoverage < WorldZeroConfig.worldzero$houseMinWallCoverage()
                || roofCoverage < WorldZeroConfig.worldzero$houseMinRoofCoverage()) {
            return new EvaluatedRoom(null);
        }

        int score = 3;
        score += 3;
        score += 2;

        boolean hasDoor = false;
        boolean hasGlass = false;
        boolean hasFurniture = false;
        boolean hasLight = false;
        BlockPos foundDoor = null;

        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int y = minY - 1; y <= maxY + 1; y++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    BlockPos scanPos = new BlockPos(x, y, z);
                    BlockState scanState = level.getBlockState(scanPos);
                    if (scanState.isAir()) {
                        continue;
                    }

                    if (!hasDoor && scanState.is(BlockTags.DOORS)) {
                        hasDoor = true;
                        foundDoor = scanPos;
                        score += 2;
                    }

                    if (!hasGlass && worldzero$isGlassLike(scanState)) {
                        hasGlass = true;
                        score += 1;
                    }

                    if (!hasFurniture && worldzero$isFurniture(scanState)) {
                        hasFurniture = true;
                        score += 2;
                    }

                    if (!hasLight && scanState.getLightEmission(level, scanPos) > 0) {
                        hasLight = true;
                        score += 1;
                    }
                }
            }
        }

        int featureCategories = 0;
        if (hasDoor) {
            featureCategories++;
        }
        if (hasGlass) {
            featureCategories++;
        }
        if (hasFurniture) {
            featureCategories++;
        }
        if (hasLight) {
            featureCategories++;
        }

        if (score < WorldZeroConfig.worldzero$houseScoreThreshold()
                || featureCategories < WorldZeroConfig.worldzero$houseFeatureCategoryThreshold()) {
            return new EvaluatedRoom(null);
        }

        BlockPos center = new BlockPos((minX + maxX) / 2, minY, (minZ + maxZ) / 2);
        return new EvaluatedRoom(new DetectedHouse(
                center,
                new BlockPos(minX, minY, minZ),
                new BlockPos(maxX, maxY, maxZ),
                score,
                featureCategories,
                foundDoor
        ));
    }

    private static boolean worldzero$isClosedBoundary(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }

        FluidState fluidState = state.getFluidState();
        return fluidState.isEmpty();
    }

    private static boolean worldzero$isGlassLike(BlockState state) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String path = blockId.getPath();
        return path.contains("glass") || path.contains("pane");
    }

    private static boolean worldzero$isFurniture(BlockState state) {
        return state.is(BlockTags.BEDS)
                || state.is(Blocks.CHEST)
                || state.is(Blocks.TRAPPED_CHEST)
                || state.is(Blocks.FURNACE)
                || state.is(Blocks.BLAST_FURNACE)
                || state.is(Blocks.SMOKER)
                || state.is(Blocks.BARREL);
    }

    private static double worldzero$square(double value) {
        return value * value;
    }

    private static long worldzero$columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int worldzero$columnX(long key) {
        return (int) (key >> 32);
    }

    private static int worldzero$columnZ(long key) {
        return (int) key;
    }

    public record DetectedHouse(
            BlockPos center,
            BlockPos interiorMin,
            BlockPos interiorMax,
            int score,
            int featureCategories,
            @Nullable BlockPos doorPos
    ) {
        public int worldzero$floorY() {
            return this.interiorMin.getY() - 1;
        }

        public double worldzero$horizontalDistanceToBoundsSqr(double x, double z) {
            double minX = this.interiorMin.getX();
            double maxX = this.interiorMax.getX() + 1.0D;
            double minZ = this.interiorMin.getZ();
            double maxZ = this.interiorMax.getZ() + 1.0D;

            double dx = 0.0D;
            if (x < minX) {
                dx = minX - x;
            } else if (x > maxX) {
                dx = x - maxX;
            }

            double dz = 0.0D;
            if (z < minZ) {
                dz = minZ - z;
            } else if (z > maxZ) {
                dz = z - maxZ;
            }

            return dx * dx + dz * dz;
        }

        public boolean worldzero$isWithinTriggerDistanceRange(double x, double z) {
            double distanceSqr = worldzero$horizontalDistanceToBoundsSqr(x, z);
            double minDistance = WorldZeroConfig.worldzero$houseTriggerDistanceMinBlocks();
            double maxDistance = WorldZeroConfig.worldzero$houseTriggerDistanceMaxBlocks();
            return distanceSqr >= worldzero$square(minDistance)
                    && distanceSqr <= worldzero$square(maxDistance);
        }

        public boolean worldzero$isVisibleToPlayer(ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            Vec3 eyePos = player.getEyePosition();
            Vec3 viewDirection = player.getViewVector(1.0F);
            double centerX = (this.interiorMin.getX() + this.interiorMax.getX() + 1.0D) * 0.5D;
            double centerY = (this.interiorMin.getY() + this.interiorMax.getY() + 1.0D) * 0.5D;
            double centerZ = (this.interiorMin.getZ() + this.interiorMax.getZ() + 1.0D) * 0.5D;

            Vec3[] points = new Vec3[] {
                    new Vec3(centerX, centerY, centerZ),
                    new Vec3(this.interiorMin.getX() - 0.1D, centerY, centerZ),
                    new Vec3(this.interiorMax.getX() + 1.1D, centerY, centerZ),
                    new Vec3(centerX, centerY, this.interiorMin.getZ() - 0.1D),
                    new Vec3(centerX, centerY, this.interiorMax.getZ() + 1.1D),
                    new Vec3(centerX, this.interiorMax.getY() + 1.05D, centerZ)
            };

            for (Vec3 point : points) {
                if (!worldzero$isPointInViewCone(eyePos, viewDirection, point)) {
                    continue;
                }
                if (worldzero$isPointVisibleFrom(level, player, eyePos, point)) {
                    return true;
                }
            }

            if (this.doorPos != null) {
                Vec3 doorPoint = new Vec3(
                        this.doorPos.getX() + 0.5D,
                        this.doorPos.getY() + 0.9D,
                        this.doorPos.getZ() + 0.5D
                );
                return worldzero$isPointInViewCone(eyePos, viewDirection, doorPoint)
                        && worldzero$isPointVisibleFrom(level, player, eyePos, doorPoint);
            }

            return false;
        }

        private boolean worldzero$isPointVisibleFrom(
                ServerLevel level,
                ServerPlayer player,
                Vec3 eyePos,
                Vec3 target
        ) {
            BlockHitResult hitResult = level.clip(new ClipContext(
                    eyePos,
                    target,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            ));
            if (hitResult.getType() == HitResult.Type.MISS) {
                return true;
            }
            return worldzero$isHouseStructurePos(hitResult.getBlockPos());
        }

        private boolean worldzero$isHouseStructurePos(BlockPos pos) {
            return pos.getX() >= this.interiorMin.getX() - 1
                    && pos.getX() <= this.interiorMax.getX() + 1
                    && pos.getY() >= this.interiorMin.getY() - 1
                    && pos.getY() <= this.interiorMax.getY() + 1
                    && pos.getZ() >= this.interiorMin.getZ() - 1
                    && pos.getZ() <= this.interiorMax.getZ() + 1;
        }

        private static boolean worldzero$isPointInViewCone(Vec3 eyePos, Vec3 viewDirection, Vec3 target) {
            Vec3 toTarget = target.subtract(eyePos);
            double toTargetLengthSqr = toTarget.lengthSqr();
            if (toTargetLengthSqr < 1.0E-6D) {
                return true;
            }
            double inverseLength = 1.0D / Math.sqrt(toTargetLengthSqr);
            double dot = (toTarget.x * inverseLength) * viewDirection.x
                    + (toTarget.y * inverseLength) * viewDirection.y
                    + (toTarget.z * inverseLength) * viewDirection.z;
            return dot >= 0.2D;
        }
    }

    private record EvaluatedRoom(@Nullable DetectedHouse detectedHouse) {
    }
}
