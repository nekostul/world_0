package ru.nekostul.worldzero;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroHouseEvent {
    private static final AABB WORLDZERO_ENTITY_SCAN_AABB = new AABB(
            -30_000_000.0D,
            -2_048.0D,
            -30_000_000.0D,
            30_000_000.0D,
            4_096.0D,
            30_000_000.0D
    );
    private static final Map<MinecraftServer, SessionState> WORLDZERO_SERVER_STATES = new WeakHashMap<>();

    private WorldZeroHouseEvent() {
    }

    @SubscribeEvent
    public static void worldzero$onServerStarted(ServerStartedEvent event) {
        worldzero$cleanupOrphanDisplays(event.getServer());
    }

    @SubscribeEvent
    public static void worldzero$onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.player instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }

        if (!WorldZeroConfig.worldzero$isHouseEventEnabled()) {
            return;
        }

        if (!player.isAlive() || player.isSpectator() || player.level().dimension() != Level.OVERWORLD) {
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

        long gameTime = player.serverLevel().getGameTime();
        if (playerState.worldzero$activeEntityId != null) {
            Entity activeEntity = worldzero$findEntity(server, playerState.worldzero$activeEntityId);
            if (activeEntity instanceof WorldZeroHouseEchoEntity) {
                return;
            }
            playerState.worldzero$activeEntityId = null;
        }

        if (playerState.worldzero$queuedSpawnTick >= 0L) {
            if (gameTime < playerState.worldzero$queuedSpawnTick) {
                return;
            }

            WorldZeroHouseDetector.DetectedHouse detectedHouse = playerState.worldzero$pendingHouse;
            if (detectedHouse == null
                    || !worldzero$canTriggerForPlayer(player, detectedHouse)) {
                detectedHouse = WorldZeroHouseDetector.worldzero$findNearbyHouseForDebug(player);
                if (detectedHouse != null
                        && !worldzero$canTriggerForPlayer(player, detectedHouse)) {
                    detectedHouse = null;
                }
                if (detectedHouse == null
                        && playerState.worldzero$rememberedHouse != null
                        && gameTime <= playerState.worldzero$rememberedHouseUntilTick
                        && worldzero$canTriggerForPlayer(player, playerState.worldzero$rememberedHouse)) {
                    detectedHouse = playerState.worldzero$rememberedHouse;
                }
            }
            playerState.worldzero$queuedSpawnTick = -1L;
            playerState.worldzero$pendingHouse = null;
            if (detectedHouse == null) {
                playerState.worldzero$nextAllowedTick = gameTime + WorldZeroConfig.worldzero$houseScanIntervalTicks();
                return;
            }

            WorldZeroHouseEchoEntity houseEcho = worldzero$spawnHouseScene(player, detectedHouse, false);
            if (houseEcho == null) {
                playerState.worldzero$nextAllowedTick = gameTime + WorldZeroConfig.worldzero$houseScanIntervalTicks();
                return;
            }

            playerState.worldzero$activeEntityId = houseEcho.getUUID();
            playerState.worldzero$firstTriggerDone = true;
            playerState.worldzero$nextAllowedTick = gameTime + worldzero$randomRepeatDelay(player.serverLevel());
            playerState.worldzero$rememberedHouse = detectedHouse;
            playerState.worldzero$rememberedHouseUntilTick = gameTime + worldzero$randomRepeatDelay(player.serverLevel());
            return;
        }

        if (playerState.worldzero$firstTriggerDone && gameTime < playerState.worldzero$nextAllowedTick) {
            return;
        }

        if (gameTime - playerState.worldzero$lastScanTick < WorldZeroConfig.worldzero$houseScanIntervalTicks()) {
            return;
        }
        playerState.worldzero$lastScanTick = gameTime;

        WorldZeroHouseDetector.DetectedHouse nearbyHouse = WorldZeroHouseDetector.worldzero$findNearbyHouseForDebug(player);
        if (nearbyHouse != null) {
            playerState.worldzero$rememberedHouse = nearbyHouse;
            playerState.worldzero$rememberedHouseUntilTick = gameTime + worldzero$randomRepeatDelay(player.serverLevel());
        }

        WorldZeroHouseDetector.DetectedHouse detectedHouse = null;
        if (nearbyHouse != null
                && worldzero$canTriggerForPlayer(player, nearbyHouse)) {
            detectedHouse = nearbyHouse;
        } else if (playerState.worldzero$rememberedHouse != null
                && gameTime <= playerState.worldzero$rememberedHouseUntilTick) {
            WorldZeroHouseDetector.DetectedHouse rememberedHouse = playerState.worldzero$rememberedHouse;
            if (worldzero$canTriggerForPlayer(player, rememberedHouse)) {
                detectedHouse = rememberedHouse;
            }
        }
        if (detectedHouse == null) {
            return;
        }

        playerState.worldzero$queuedSpawnTick = gameTime + (!playerState.worldzero$firstTriggerDone
                ? worldzero$randomInitialDelay(player.serverLevel())
                : worldzero$randomRepeatAppearanceDelay(player.serverLevel()));
        playerState.worldzero$pendingHouse = detectedHouse;
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_SERVER_STATES.remove(event.getServer());
    }

    public static boolean worldzero$triggerHouseNow(ServerPlayer player) {
        return worldzero$triggerHouseNowInternal(player, true);
    }

    public static boolean worldzero$triggerHouseNowDebug(ServerPlayer player) {
        return worldzero$triggerHouseNowInternal(player, false);
    }

    private static boolean worldzero$triggerHouseNowInternal(ServerPlayer player, boolean enforceRealConditions) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return false;
        }

        if (player.level().dimension() != Level.OVERWORLD) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        SessionState sessionState = WORLDZERO_SERVER_STATES.computeIfAbsent(server, ignored -> new SessionState());
        PlayerState playerState = sessionState.worldzero$playerStates.computeIfAbsent(
                player.getUUID(),
                ignored -> new PlayerState()
        );

        if (playerState.worldzero$activeEntityId != null) {
            Entity activeEntity = worldzero$findEntity(server, playerState.worldzero$activeEntityId);
            if (activeEntity instanceof WorldZeroHouseEchoEntity) {
                return false;
            }
            playerState.worldzero$activeEntityId = null;
        }

        long gameTime = player.serverLevel().getGameTime();
        WorldZeroHouseDetector.DetectedHouse detectedHouse = WorldZeroHouseDetector.worldzero$findNearbyHouseForDebug(player);
        if (detectedHouse == null
                && playerState.worldzero$rememberedHouse != null
                && (!enforceRealConditions || gameTime <= playerState.worldzero$rememberedHouseUntilTick)) {
            detectedHouse = playerState.worldzero$rememberedHouse;
        }
        if (detectedHouse == null && !enforceRealConditions) {
            detectedHouse = worldzero$createDebugFallbackHouse(player);
        }
        if (detectedHouse == null) {
            return false;
        }
        if (enforceRealConditions && !worldzero$canTriggerForPlayer(player, detectedHouse)) {
            return false;
        }

        WorldZeroHouseEchoEntity houseEcho = worldzero$spawnHouseScene(player, detectedHouse, enforceRealConditions ? false : true);
        if (houseEcho == null) {
            return false;
        }

        playerState.worldzero$queuedSpawnTick = -1L;
        playerState.worldzero$pendingHouse = null;
        playerState.worldzero$activeEntityId = houseEcho.getUUID();
        playerState.worldzero$firstTriggerDone = true;
        playerState.worldzero$nextAllowedTick = gameTime + worldzero$randomRepeatDelay(player.serverLevel());
        playerState.worldzero$rememberedHouse = detectedHouse;
        playerState.worldzero$rememberedHouseUntilTick = gameTime + worldzero$randomRepeatDelay(player.serverLevel());
        return true;
    }

    @Nullable
    private static WorldZeroHouseEchoEntity worldzero$spawnHouseScene(
            ServerPlayer player,
            WorldZeroHouseDetector.DetectedHouse detectedHouse,
            boolean debugForce
    ) {
        ServerLevel level = player.serverLevel();
        BlockPos spawnPos = worldzero$findSpawnPosition(level, player, detectedHouse, debugForce);
        if (spawnPos == null) {
            return null;
        }

        WorldZeroHouseEchoEntity houseEcho = WorldZeroEntities.WORLDZERO_HOUSE_ECHO.get().create(level);
        if (houseEcho == null) {
            return null;
        }

        houseEcho.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, player.getYRot(), 0.0F);
        houseEcho.worldzero$configureScene(
                player.getUUID(),
                detectedHouse,
                Mth.nextInt(
                        level.random,
                        WorldZeroConfig.worldzero$houseLifetimeMinTicks(),
                        WorldZeroConfig.worldzero$houseLifetimeMaxTicks()
                ),
                worldzero$distortName(player.getGameProfile().getName()),
                debugForce
        );
        level.addFreshEntity(houseEcho);
        return houseEcho;
    }

    @Nullable
    private static BlockPos worldzero$findSpawnPosition(
            ServerLevel level,
            ServerPlayer player,
            WorldZeroHouseDetector.DetectedHouse detectedHouse,
            boolean debugForce
    ) {
        int minX = detectedHouse.interiorMin().getX();
        int minZ = detectedHouse.interiorMin().getZ();
        int maxX = detectedHouse.interiorMax().getX();
        int maxZ = detectedHouse.interiorMax().getZ();
        int floorY = detectedHouse.worldzero$floorY() + 1;
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;

        BlockPos[] bases = new BlockPos[] {
                new BlockPos(centerX, floorY, minZ - 2),
                new BlockPos(centerX, floorY, maxZ + 2),
                new BlockPos(minX - 2, floorY, centerZ),
                new BlockPos(maxX + 2, floorY, centerZ)
        };

        BlockPos bestPos = null;
        double bestDistance = debugForce ? Double.MAX_VALUE : -1.0D;
        for (BlockPos base : bases) {
            for (int offset = -2; offset <= 2; offset++) {
                BlockPos shiftedBase;
                if (base.getX() == minX - 2 || base.getX() == maxX + 2) {
                    shiftedBase = new BlockPos(base.getX(), base.getY(), base.getZ() + offset);
                } else {
                    shiftedBase = new BlockPos(base.getX() + offset, base.getY(), base.getZ());
                }

                BlockPos standable = worldzero$findStandablePosition(level, shiftedBase, floorY);
                if (standable == null) {
                    continue;
                }

                double playerDistance = player.distanceToSqr(
                        standable.getX() + 0.5D,
                        standable.getY(),
                        standable.getZ() + 0.5D
                );
                if (debugForce && playerDistance < worldzero$square(4.0D)) {
                    continue;
                }

                if (!debugForce && playerDistance < worldzero$square(WorldZeroConfig.worldzero$houseDisappearDistanceBlocks() + 2.0D)) {
                    continue;
                }

                if (!debugForce && playerDistance > bestDistance) {
                    bestDistance = playerDistance;
                    bestPos = standable;
                }

                if (debugForce) {
                    Vec3 eyePos = player.getEyePosition();
                    Vec3 target = new Vec3(standable.getX() + 0.5D, standable.getY() + 1.0D, standable.getZ() + 0.5D);
                    boolean visible = level.clip(new net.minecraft.world.level.ClipContext(
                            eyePos,
                            target,
                            net.minecraft.world.level.ClipContext.Block.COLLIDER,
                            net.minecraft.world.level.ClipContext.Fluid.NONE,
                            player
                    )).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
                    if (!visible) {
                        continue;
                    }
                    if (playerDistance < bestDistance) {
                        bestDistance = playerDistance;
                        bestPos = standable;
                    }
                }
            }
        }

        if (bestPos == null && debugForce) {
            BlockPos frontBase = new BlockPos(
                    Mth.floor(player.getX() + player.getLookAngle().x * 5.5D),
                    Mth.floor(player.getY()),
                    Mth.floor(player.getZ() + player.getLookAngle().z * 5.5D)
            );
            BlockPos frontStandable = worldzero$findStandablePosition(level, frontBase, frontBase.getY());
            if (frontStandable != null) {
                return frontStandable;
            }
        }

        return bestPos;
    }

    @Nullable
    private static BlockPos worldzero$findStandablePosition(ServerLevel level, BlockPos base, int referenceY) {
        for (int y = referenceY + 2; y >= referenceY - 3; y--) {
            BlockPos candidate = new BlockPos(base.getX(), y, base.getZ());
            if (!level.getBlockState(candidate).isAir()) {
                continue;
            }

            if (!level.getBlockState(candidate.above()).isAir()) {
                continue;
            }

            if (level.getBlockState(candidate.below()).isAir()) {
                continue;
            }

            if (!level.getBlockState(candidate.below()).getFluidState().isEmpty()) {
                continue;
            }

            return candidate;
        }

        return null;
    }

    @Nullable
    private static Entity worldzero$findEntity(MinecraftServer server, UUID entityId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }

        return null;
    }

    private static void worldzero$cleanupOrphanDisplays(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Display.BlockDisplay display : level.getEntitiesOfClass(
                    Display.BlockDisplay.class,
                    WORLDZERO_ENTITY_SCAN_AABB,
                    entity -> entity.getTags().contains(WorldZeroHouseEchoEntity.WORLDZERO_HOUSE_DISPLAY_TAG)
            )) {
                display.discard();
            }
        }
    }

    private static String worldzero$distortName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return "Player_";
        }

        char[] chars = playerName.toCharArray();
        for (int index = 0; index < chars.length; index++) {
            char character = chars[index];
            if (Character.isLetter(character)) {
                chars[index] = Character.isUpperCase(character)
                        ? Character.toLowerCase(character)
                        : Character.toUpperCase(character);
                return new String(chars);
            }
        }

        return playerName + "_";
    }

    private static long worldzero$randomInitialDelay(ServerLevel level) {
        return Mth.nextInt(
                level.random,
                WorldZeroConfig.worldzero$houseInitialDelayMinTicks(),
                WorldZeroConfig.worldzero$houseInitialDelayMaxTicks()
        );
    }

    private static long worldzero$randomRepeatAppearanceDelay(ServerLevel level) {
        return Mth.nextInt(
                level.random,
                WorldZeroConfig.worldzero$houseRepeatDelayMinTicks(),
                WorldZeroConfig.worldzero$houseRepeatDelayMaxTicks()
        );
    }

    private static long worldzero$randomRepeatDelay(ServerLevel level) {
        return Mth.nextInt(
                level.random,
                WorldZeroConfig.worldzero$houseRepeatMinTicks(),
                WorldZeroConfig.worldzero$houseRepeatMaxTicks()
        );
    }

    private static boolean worldzero$canTriggerForPlayer(
            ServerPlayer player,
            WorldZeroHouseDetector.DetectedHouse detectedHouse
    ) {
        return detectedHouse.worldzero$isWithinTriggerDistanceRange(player.getX(), player.getZ())
                && detectedHouse.worldzero$isVisibleToPlayer(player);
    }

    private static WorldZeroHouseDetector.DetectedHouse worldzero$createDebugFallbackHouse(ServerPlayer player) {
        BlockPos center = player.blockPosition();
        BlockPos interiorMin = center.offset(-3, 0, -3);
        BlockPos interiorMax = center.offset(3, 2, 3);
        return new WorldZeroHouseDetector.DetectedHouse(
                center,
                interiorMin,
                interiorMax,
                0,
                0,
                null
        );
    }

    private static double worldzero$square(double value) {
        return value * value;
    }

    private static final class SessionState {
        private final Map<UUID, PlayerState> worldzero$playerStates = new HashMap<>();
    }

    private static final class PlayerState {
        private boolean worldzero$firstTriggerDone;
        private long worldzero$nextAllowedTick;
        private long worldzero$lastScanTick;
        private long worldzero$queuedSpawnTick = -1L;
        private UUID worldzero$activeEntityId;
        private WorldZeroHouseDetector.DetectedHouse worldzero$pendingHouse;
        private WorldZeroHouseDetector.DetectedHouse worldzero$rememberedHouse;
        private long worldzero$rememberedHouseUntilTick;
    }
}
