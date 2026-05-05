package ru.nekostul.worldzero.event.horror;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;
import ru.nekostul.worldzero.entity.WorldZeroEchoEntity;
import ru.nekostul.worldzero.entity.WorldZeroEchoPresenceTracker;
import ru.nekostul.worldzero.entity.WorldZeroEntities;
import ru.nekostul.worldzero.event.WorldZeroStoryTime;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroBlackEchoJumpscareEvent {
    private static final ResourceLocation WORLDZERO_BEJS1_SOUND_ID = new ResourceLocation(WorldZeroMod.MOD_ID, "bejs1");
    private static final ResourceLocation WORLDZERO_BEJS2_SOUND_ID = new ResourceLocation(WorldZeroMod.MOD_ID, "bejs2");
    private static final ResourceLocation WORLDZERO_BEJS4_SOUND_ID = new ResourceLocation(WorldZeroMod.MOD_ID, "bejs4");
    private static final long WORLDZERO_TICKS_PER_MINUTE = 60L * 20L;
    private static final long WORLDZERO_START_TICKS = 60L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_END_TICKS = 180L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_INITIAL_DELAY_MIN_TICKS = 4L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_INITIAL_DELAY_MAX_TICKS = 8L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_COOLDOWN_MIN_TICKS = 10L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_COOLDOWN_MAX_TICKS = 16L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_RETRY_COOLDOWN_TICKS = 30L * 20L;
    private static final double WORLDZERO_FRONT_DISTANCE_MIN_BLOCKS = 2.6D;
    private static final double WORLDZERO_FRONT_DISTANCE_MAX_BLOCKS = 3.4D;
    private static final double WORLDZERO_SIDE_DISTANCE_MIN_BLOCKS = 3.8D;
    private static final double WORLDZERO_SIDE_DISTANCE_MAX_BLOCKS = 5.0D;
    private static final double WORLDZERO_SIDE_FORWARD_MIN_BLOCKS = 0.8D;
    private static final double WORLDZERO_SIDE_FORWARD_MAX_BLOCKS = 2.0D;
    private static final double WORLDZERO_REAR_DISTANCE_MIN_BLOCKS = 4.4D;
    private static final double WORLDZERO_REAR_DISTANCE_MAX_BLOCKS = 5.8D;
    private static final double WORLDZERO_REAR_SIDE_OFFSET_MIN_BLOCKS = 0.7D;
    private static final double WORLDZERO_REAR_SIDE_OFFSET_MAX_BLOCKS = 1.4D;
    private static final int WORLDZERO_FRONT_STARE_DURATION_TICKS = 14;
    private static final int WORLDZERO_SIDE_GLANCE_DURATION_TICKS = 20;
    private static final int WORLDZERO_REAR_RUSH_DURATION_TICKS = 18;
    private static final int WORLDZERO_SIDE_GLANCE_SEEN_TICKS = 2;
    private static final double WORLDZERO_SIDE_GLANCE_LOOK_DOT = 0.86D;
    private static final double WORLDZERO_REAR_RUSH_SPEED_BLOCKS_PER_TICK = 0.48D;
    private static final double WORLDZERO_REAR_RUSH_END_DISTANCE_BLOCKS = 1.35D;
    private static final int WORLDZERO_ALL_VARIANTS_MASK = (1 << JumpscareVariant.values().length) - 1;
    private static final String WORLDZERO_SAVE_ID = "worldzero_black_echo_jumpscare";
    private static final Map<MinecraftServer, SessionState> WORLDZERO_SESSION_STATES = new WeakHashMap<>();

    private WorldZeroBlackEchoJumpscareEvent() {
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
        if (state.worldzero$active) {
            worldzero$tickActiveJumpscare(level, state);
            return;
        }

        long storyTicks = WorldZeroStoryTime.worldzero$getStoryTicks(level);
        if (storyTicks < WORLDZERO_START_TICKS
                || storyTicks >= WORLDZERO_END_TICKS
                || WorldZeroHorrorFinale.worldzero$isActive(server)
                || WorldZeroHorrorFinale.worldzero$isEndReached(storyTicks)) {
            return;
        }

        if (state.worldzero$retryCooldownTicks > 0L) {
            state.worldzero$retryCooldownTicks--;
            return;
        }

        JumpscareSaveData saveData = worldzero$getSaveData(level);
        if (saveData.worldzero$nextTriggerTick < 0L) {
            saveData.worldzero$nextTriggerTick = Math.max(
                    WORLDZERO_START_TICKS,
                    storyTicks + worldzero$randomRange(level, WORLDZERO_INITIAL_DELAY_MIN_TICKS, WORLDZERO_INITIAL_DELAY_MAX_TICKS)
            );
            saveData.setDirty();
            return;
        }

        if (storyTicks < saveData.worldzero$nextTriggerTick) {
            return;
        }

        if (worldzero$hasConflictingEvent(level) || WorldZeroEchoPresenceTracker.worldzero$hasAnyEcho(server)) {
            state.worldzero$retryCooldownTicks = WORLDZERO_RETRY_COOLDOWN_TICKS;
            return;
        }

        ServerPlayer player = worldzero$pickTargetPlayer(level);
        if (player == null) {
            state.worldzero$retryCooldownTicks = WORLDZERO_RETRY_COOLDOWN_TICKS;
            return;
        }

        JumpscareVariant variant = worldzero$pickVariant(level, saveData);
        if (variant == null || !worldzero$startJumpscare(level, state, player, variant)) {
            state.worldzero$retryCooldownTicks = WORLDZERO_RETRY_COOLDOWN_TICKS;
            return;
        }

        saveData.worldzero$nextTriggerTick = storyTicks + worldzero$randomRange(
                level,
                WORLDZERO_COOLDOWN_MIN_TICKS,
                WORLDZERO_COOLDOWN_MAX_TICKS
        );
        saveData.worldzero$lastVariantOrdinal = variant.ordinal();
        saveData.worldzero$usedVariantMask |= 1 << variant.ordinal();
        if ((saveData.worldzero$usedVariantMask & WORLDZERO_ALL_VARIANTS_MASK) == WORLDZERO_ALL_VARIANTS_MASK) {
            saveData.worldzero$usedVariantMask = 0;
        }
        saveData.setDirty();
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_SESSION_STATES.remove(event.getServer());
    }

    public static boolean worldzero$isActive(@Nullable MinecraftServer server) {
        if (server == null) {
            return false;
        }

        SessionState state = WORLDZERO_SESSION_STATES.get(server);
        return state != null && state.worldzero$active;
    }

    public static boolean worldzero$stopNow(@Nullable MinecraftServer server) {
        if (server == null) {
            return false;
        }

        SessionState state = WORLDZERO_SESSION_STATES.get(server);
        if (state == null || !state.worldzero$active) {
            return false;
        }

        worldzero$clearState(server, state);
        return true;
    }

    public static boolean worldzero$triggerNow(ServerPlayer player) {
        if (player == null
                || !player.isAlive()
                || player.isSpectator()
                || player.serverLevel().dimension() != Level.OVERWORLD) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        MinecraftServer server = player.getServer();
        if (server == null || worldzero$hasConflictingEvent(level) || WorldZeroEchoPresenceTracker.worldzero$hasAnyEcho(server)) {
            return false;
        }

        SessionState state = WORLDZERO_SESSION_STATES.computeIfAbsent(server, ignored -> new SessionState());
        if (state.worldzero$active) {
            return false;
        }

        JumpscareSaveData saveData = worldzero$getSaveData(level);
        JumpscareVariant preferredVariant = worldzero$pickVariant(level, saveData);
        if (preferredVariant != null && worldzero$startJumpscare(level, state, player, preferredVariant)) {
            saveData.worldzero$lastVariantOrdinal = preferredVariant.ordinal();
            saveData.worldzero$usedVariantMask |= 1 << preferredVariant.ordinal();
            if ((saveData.worldzero$usedVariantMask & WORLDZERO_ALL_VARIANTS_MASK) == WORLDZERO_ALL_VARIANTS_MASK) {
                saveData.worldzero$usedVariantMask = 0;
            }
            saveData.setDirty();
            return true;
        }

        for (JumpscareVariant variant : JumpscareVariant.values()) {
            if (variant == preferredVariant) {
                continue;
            }

            if (worldzero$startJumpscare(level, state, player, variant)) {
                saveData.worldzero$lastVariantOrdinal = variant.ordinal();
                saveData.worldzero$usedVariantMask |= 1 << variant.ordinal();
                if ((saveData.worldzero$usedVariantMask & WORLDZERO_ALL_VARIANTS_MASK) == WORLDZERO_ALL_VARIANTS_MASK) {
                    saveData.worldzero$usedVariantMask = 0;
                }
                saveData.setDirty();
                return true;
            }
        }

        return false;
    }

    private static void worldzero$tickActiveJumpscare(ServerLevel level, SessionState state) {
        MinecraftServer server = level.getServer();
        long storyTicks = WorldZeroStoryTime.worldzero$getStoryTicks(level);
        if (storyTicks >= WORLDZERO_END_TICKS
                || WorldZeroHorrorFinale.worldzero$isActive(server)
                || WorldZeroHorrorFinale.worldzero$isEndReached(storyTicks)) {
            worldzero$clearState(server, state);
            return;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(state.worldzero$targetPlayerId);
        Entity entity = worldzero$findEntity(server, state.worldzero$entityId);
        if (!(entity instanceof WorldZeroEchoEntity blackEcho)
                || player == null
                || !player.isAlive()
                || player.isSpectator()
                || player.serverLevel() != level) {
            worldzero$clearState(server, state);
            return;
        }

        if (state.worldzero$variant == JumpscareVariant.SIDE_GLANCE) {
            if (worldzero$isLookedAtBy(player, blackEcho, WORLDZERO_SIDE_GLANCE_LOOK_DOT)) {
                state.worldzero$seenTicks++;
                if (state.worldzero$seenTicks >= WORLDZERO_SIDE_GLANCE_SEEN_TICKS) {
                    worldzero$clearState(server, state);
                    return;
                }
            } else {
                state.worldzero$seenTicks = 0;
            }
        } else if (state.worldzero$variant == JumpscareVariant.REAR_RUSH) {
            if (worldzero$tickRearRush(level, player, blackEcho)) {
                worldzero$clearState(server, state);
                return;
            }
        }

        if (level.getGameTime() >= state.worldzero$endGameTick) {
            worldzero$clearState(server, state);
        }
    }

    private static boolean worldzero$startJumpscare(
            ServerLevel level,
            SessionState state,
            ServerPlayer player,
            JumpscareVariant variant
    ) {
        WorldZeroEchoEntity blackEcho = switch (variant) {
            case FRONT_STARE -> worldzero$spawnFrontStare(level, player);
            case SIDE_GLANCE -> worldzero$spawnSideGlance(level, player);
            case REAR_RUSH -> worldzero$spawnRearRush(level, player);
        };
        if (blackEcho == null) {
            return false;
        }

        int durationTicks = switch (variant) {
            case FRONT_STARE -> WORLDZERO_FRONT_STARE_DURATION_TICKS;
            case SIDE_GLANCE -> WORLDZERO_SIDE_GLANCE_DURATION_TICKS;
            case REAR_RUSH -> WORLDZERO_REAR_RUSH_DURATION_TICKS;
        };

        state.worldzero$active = true;
        state.worldzero$variant = variant;
        state.worldzero$entityId = blackEcho.getUUID();
        state.worldzero$targetPlayerId = player.getUUID();
        state.worldzero$endGameTick = level.getGameTime() + durationTicks;
        state.worldzero$retryCooldownTicks = 0L;
        state.worldzero$seenTicks = 0;

        worldzero$playVariantSound(player, variant);
        return true;
    }

    @Nullable
    private static JumpscareVariant worldzero$pickVariant(ServerLevel level, JumpscareSaveData saveData) {
        int usedVariantMask = saveData.worldzero$usedVariantMask;
        if ((usedVariantMask & WORLDZERO_ALL_VARIANTS_MASK) == WORLDZERO_ALL_VARIANTS_MASK) {
            usedVariantMask = 0;
        }

        List<JumpscareVariant> candidates = new ArrayList<>();
        for (JumpscareVariant variant : JumpscareVariant.values()) {
            int bit = 1 << variant.ordinal();
            if ((usedVariantMask & bit) != 0) {
                continue;
            }
            if (variant.ordinal() == saveData.worldzero$lastVariantOrdinal) {
                continue;
            }
            candidates.add(variant);
        }

        if (candidates.isEmpty()) {
            for (JumpscareVariant variant : JumpscareVariant.values()) {
                int bit = 1 << variant.ordinal();
                if ((usedVariantMask & bit) == 0) {
                    candidates.add(variant);
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.get(level.random.nextInt(candidates.size()));
    }

    @Nullable
    private static WorldZeroEchoEntity worldzero$spawnFrontStare(ServerLevel level, ServerPlayer player) {
        Vec3 look = worldzero$horizontalLook(player);
        double distance = Mth.nextDouble(level.random, WORLDZERO_FRONT_DISTANCE_MIN_BLOCKS, WORLDZERO_FRONT_DISTANCE_MAX_BLOCKS);
        Vec3 desiredPosition = player.position().add(look.scale(distance));
        BlockPos spawnPos = worldzero$findSpawnPos(level, desiredPosition, Mth.floor(player.getY()));
        return spawnPos != null ? worldzero$spawnBlackEcho(level, player, spawnPos) : null;
    }

    @Nullable
    private static WorldZeroEchoEntity worldzero$spawnSideGlance(ServerLevel level, ServerPlayer player) {
        Vec3 look = worldzero$horizontalLook(player);
        Vec3 side = worldzero$horizontalSide(look);
        double sideDistance = Mth.nextDouble(level.random, WORLDZERO_SIDE_DISTANCE_MIN_BLOCKS, WORLDZERO_SIDE_DISTANCE_MAX_BLOCKS);
        double forwardDistance = Mth.nextDouble(level.random, WORLDZERO_SIDE_FORWARD_MIN_BLOCKS, WORLDZERO_SIDE_FORWARD_MAX_BLOCKS);
        double[] signs = level.random.nextBoolean() ? new double[]{1.0D, -1.0D} : new double[]{-1.0D, 1.0D};

        for (double sign : signs) {
            Vec3 desiredPosition = player.position()
                    .add(look.scale(forwardDistance))
                    .add(side.scale(sideDistance * sign));
            BlockPos spawnPos = worldzero$findSpawnPos(level, desiredPosition, Mth.floor(player.getY()));
            if (spawnPos == null) {
                continue;
            }

            WorldZeroEchoEntity blackEcho = worldzero$spawnBlackEcho(level, player, spawnPos);
            if (blackEcho != null) {
                return blackEcho;
            }
        }

        return null;
    }

    @Nullable
    private static WorldZeroEchoEntity worldzero$spawnRearRush(ServerLevel level, ServerPlayer player) {
        Vec3 look = worldzero$horizontalLook(player);
        Vec3 side = worldzero$horizontalSide(look);
        double[] signs = level.random.nextBoolean() ? new double[]{1.0D, -1.0D} : new double[]{-1.0D, 1.0D};

        for (double sign : signs) {
            double rearDistance = Mth.nextDouble(level.random, WORLDZERO_REAR_DISTANCE_MIN_BLOCKS, WORLDZERO_REAR_DISTANCE_MAX_BLOCKS);
            double sideOffset = Mth.nextDouble(level.random, WORLDZERO_REAR_SIDE_OFFSET_MIN_BLOCKS, WORLDZERO_REAR_SIDE_OFFSET_MAX_BLOCKS);
            Vec3 desiredPosition = player.position()
                    .subtract(look.scale(rearDistance))
                    .add(side.scale(sideOffset * sign));
            BlockPos spawnPos = worldzero$findSpawnPos(level, desiredPosition, Mth.floor(player.getY()));
            if (spawnPos == null) {
                continue;
            }

            WorldZeroEchoEntity blackEcho = worldzero$spawnBlackEcho(level, player, spawnPos);
            if (blackEcho != null) {
                return blackEcho;
            }
        }

        return null;
    }

    private static boolean worldzero$tickRearRush(ServerLevel level, ServerPlayer player, WorldZeroEchoEntity blackEcho) {
        double deltaX = player.getX() - blackEcho.getX();
        double deltaZ = player.getZ() - blackEcho.getZ();
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        if (horizontalDistance < WORLDZERO_REAR_RUSH_END_DISTANCE_BLOCKS) {
            return true;
        }

        double step = Math.min(WORLDZERO_REAR_RUSH_SPEED_BLOCKS_PER_TICK, horizontalDistance);
        double nextX = blackEcho.getX() + (deltaX / horizontalDistance) * step;
        double nextZ = blackEcho.getZ() + (deltaZ / horizontalDistance) * step;
        double nextY = worldzero$resolveGroundY(level, nextX, nextZ, blackEcho.getY());
        float yaw = (float) (Mth.atan2(deltaZ, deltaX) * (180.0D / Math.PI)) - 90.0F;

        blackEcho.setPos(nextX, nextY, nextZ);
        blackEcho.setYRot(yaw);
        blackEcho.setYHeadRot(yaw);
        blackEcho.setYBodyRot(yaw);
        return false;
    }

    private static double worldzero$resolveGroundY(ServerLevel level, double x, double z, double currentY) {
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        double maxStepUp = currentY + 1.05D;
        double clampedGroundY = Math.min((double) groundY, maxStepUp);
        return clampedGroundY >= currentY - 1.5D ? clampedGroundY : currentY;
    }

    @Nullable
    private static BlockPos worldzero$findSpawnPos(ServerLevel level, Vec3 desiredPosition, int playerFeetY) {
        int baseX = Mth.floor(desiredPosition.x);
        int baseZ = Mth.floor(desiredPosition.z);
        int[] offsets = {0, 1, -1};

        for (int dx : offsets) {
            for (int dz : offsets) {
                for (int y = playerFeetY + 2; y >= playerFeetY - 4; y--) {
                    BlockPos spawnPos = new BlockPos(baseX + dx, y, baseZ + dz);
                    if (worldzero$isValidSpawn(level, spawnPos)) {
                        return spawnPos;
                    }
                }
            }
        }

        return null;
    }

    private static boolean worldzero$isValidSpawn(ServerLevel level, BlockPos spawnPos) {
        BlockState belowState = level.getBlockState(spawnPos.below());
        if (!belowState.isFaceSturdy(level, spawnPos.below(), Direction.UP) || !belowState.getFluidState().isEmpty()) {
            return false;
        }

        if (!level.getBlockState(spawnPos).isAir() || !level.getBlockState(spawnPos.above()).isAir()) {
            return false;
        }

        AABB spawnBox = WorldZeroEntities.WORLDZERO_BLACK_ECHO.get().getDimensions().makeBoundingBox(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D
        );
        return level.noCollision(spawnBox) && !level.containsAnyLiquid(spawnBox);
    }

    @Nullable
    private static WorldZeroEchoEntity worldzero$spawnBlackEcho(ServerLevel level, ServerPlayer player, BlockPos spawnPos) {
        WorldZeroEchoEntity blackEcho = WorldZeroEntities.WORLDZERO_BLACK_ECHO.get().create(level);
        if (blackEcho == null) {
            return null;
        }

        double spawnX = spawnPos.getX() + 0.5D;
        double spawnY = spawnPos.getY();
        double spawnZ = spawnPos.getZ() + 0.5D;
        double deltaX = player.getX() - spawnX;
        double deltaZ = player.getZ() - spawnZ;
        float yaw = (float) (Mth.atan2(deltaZ, deltaX) * (180.0D / Math.PI)) - 90.0F;

        blackEcho.moveTo(spawnX, spawnY, spawnZ, yaw, 0.0F);
        blackEcho.setSilent(true);
        blackEcho.setDeltaMovement(0.0D, 0.0D, 0.0D);
        level.addFreshEntity(blackEcho);
        return blackEcho;
    }

    private static void worldzero$playVariantSound(ServerPlayer player, JumpscareVariant variant) {
        ResourceLocation soundId = switch (variant) {
            case FRONT_STARE -> WORLDZERO_BEJS1_SOUND_ID;
            case SIDE_GLANCE -> WORLDZERO_BEJS2_SOUND_ID;
            case REAR_RUSH -> WORLDZERO_BEJS4_SOUND_ID;
        };
        float pitch = switch (variant) {
            case FRONT_STARE -> 1.0F;
            case SIDE_GLANCE -> 0.92F;
            case REAR_RUSH -> 0.84F;
        };
        WorldZeroNetwork.sendHorrorSound(player, soundId, pitch, false);
    }

    private static boolean worldzero$isLookedAtBy(ServerPlayer player, Entity entity, double dotThreshold) {
        if (!player.hasLineOfSight(entity)) {
            return false;
        }

        Vec3 directionToEcho = entity.getBoundingBox().getCenter().subtract(player.getEyePosition());
        if (directionToEcho.lengthSqr() < 0.0001D) {
            return false;
        }

        return player.getViewVector(1.0F).normalize().dot(directionToEcho.normalize()) >= dotThreshold;
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

    private static Vec3 worldzero$horizontalSide(Vec3 horizontalLook) {
        return new Vec3(-horizontalLook.z, 0.0D, horizontalLook.x).normalize();
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
                || WorldZeroTrapEvent.worldzero$isActive(server)
                || WorldZeroNightDarknessEvent.worldzero$isActive(server)
                || WorldZeroHorrorFinale.worldzero$isActive(server)
                || WorldZeroHorrorEventSystem.worldzero$isMinorAnomalyActive(level)
                || WorldZeroMajorEventSystem.worldzero$isMajorEventActive(server);
    }

    private static long worldzero$randomRange(ServerLevel level, long minValue, long maxValue) {
        if (minValue >= maxValue) {
            return minValue;
        }

        long bound = maxValue - minValue + 1L;
        return minValue + Math.floorMod(level.random.nextLong(), bound);
    }

    private static JumpscareSaveData worldzero$getSaveData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(JumpscareSaveData::load, JumpscareSaveData::new, WORLDZERO_SAVE_ID);
    }

    private static void worldzero$clearState(MinecraftServer server, SessionState state) {
        Entity entity = worldzero$findEntity(server, state.worldzero$entityId);
        if (entity != null) {
            entity.discard();
        }

        state.worldzero$active = false;
        state.worldzero$variant = null;
        state.worldzero$entityId = null;
        state.worldzero$targetPlayerId = null;
        state.worldzero$endGameTick = -1L;
        state.worldzero$seenTicks = 0;
    }

    private enum JumpscareVariant {
        FRONT_STARE,
        SIDE_GLANCE,
        REAR_RUSH
    }

    private static final class SessionState {
        private boolean worldzero$active;
        private long worldzero$retryCooldownTicks;
        private long worldzero$endGameTick = -1L;
        private int worldzero$seenTicks;
        private UUID worldzero$entityId;
        private UUID worldzero$targetPlayerId;
        private JumpscareVariant worldzero$variant;
    }

    private static final class JumpscareSaveData extends SavedData {
        private long worldzero$nextTriggerTick = -1L;
        private int worldzero$lastVariantOrdinal = -1;
        private int worldzero$usedVariantMask;

        private static JumpscareSaveData load(CompoundTag tag) {
            JumpscareSaveData saveData = new JumpscareSaveData();
            saveData.worldzero$nextTriggerTick = tag.contains("next_trigger_tick")
                    ? tag.getLong("next_trigger_tick")
                    : -1L;
            saveData.worldzero$lastVariantOrdinal = tag.getInt("last_variant_ordinal");
            saveData.worldzero$usedVariantMask = tag.getInt("used_variant_mask");
            return saveData;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putLong("next_trigger_tick", this.worldzero$nextTriggerTick);
            tag.putInt("last_variant_ordinal", this.worldzero$lastVariantOrdinal);
            tag.putInt("used_variant_mask", this.worldzero$usedVariantMask);
            return tag;
        }
    }
}
