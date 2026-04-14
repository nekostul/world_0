package ru.nekostul.worldzero;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroEchoPhaseOneSpawner {
    private static final long WORLDZERO_COOLDOWN_MIN_TICKS = 10L * 60L * 20L;
    private static final long WORLDZERO_COOLDOWN_MAX_TICKS = 20L * 60L * 20L;
    private static final double WORLDZERO_SPAWN_MIN_DISTANCE = 15.0D;
    private static final double WORLDZERO_SPAWN_MAX_DISTANCE = 30.0D;
    private static final int WORLDZERO_RULE_BREAK_IDLE_MIN_TICKS = 20;
    private static final int WORLDZERO_RULE_BREAK_IDLE_MAX_TICKS = 40;
    private static final double WORLDZERO_ECHO_DESPAWN_MIN_DISTANCE = 5.0D;
    private static final double WORLDZERO_ECHO_DESPAWN_MAX_DISTANCE = 10.0D;
    private static final int WORLDZERO_SPAWN_ATTEMPTS = 40;
    private static final double WORLDZERO_VIEW_DOT_THRESHOLD = 0.15D;
    private static final AABB WORLDZERO_ENTITY_SCAN_AABB = new AABB(
            -30_000_000.0D,
            -2_048.0D,
            -30_000_000.0D,
            30_000_000.0D,
            4_096.0D,
            30_000_000.0D
    );
    private static final Map<UUID, Long> WORLDZERO_NEXT_ALLOWED_SPAWN_TICK = new HashMap<>();

    private WorldZeroEchoPhaseOneSpawner() {
    }

    public static boolean worldzero$triggerPhaseOneSpawn(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        if (worldzero$hasActiveEcho(level.getServer())) {
            return false;
        }

        boolean shouldTriggerRuleBreak = WorldZeroEchoRuleBreakState.shouldTriggerRuleBreak(level.getServer());
        boolean spawned = worldzero$trySpawnEcho(level, player, shouldTriggerRuleBreak);
        if (spawned) {
            if (shouldTriggerRuleBreak) {
                WorldZeroEchoRuleBreakState.recordRuleBreakAppearance(level.getServer());
            } else {
                WorldZeroEchoRuleBreakState.recordNormalAppearance(level.getServer());
            }

            WORLDZERO_NEXT_ALLOWED_SPAWN_TICK.put(
                    player.getUUID(),
                    level.getGameTime() + worldzero$randomCooldown(level)
            );
        }
        return spawned;
    }

    public static boolean worldzero$triggerRuleBreakSpawn(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        if (worldzero$hasActiveEcho(level.getServer())) {
            return false;
        }

        boolean spawned = worldzero$trySpawnEcho(level, player, true);
        if (spawned) {
            WORLDZERO_NEXT_ALLOWED_SPAWN_TICK.put(
                    player.getUUID(),
                    level.getGameTime() + worldzero$randomCooldown(level)
            );
        }

        return spawned;
    }

    @SubscribeEvent
    public static void worldzero$onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.player instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }

        if (!player.isAlive() || player.isSpectator()) {
            return;
        }

        ServerLevel level = player.serverLevel();
        long gameTime = level.getGameTime();

        long nextAllowedSpawn = WORLDZERO_NEXT_ALLOWED_SPAWN_TICK.computeIfAbsent(
                player.getUUID(),
                uuid -> gameTime + worldzero$randomCooldown(level)
        );

        if (gameTime < nextAllowedSpawn) {
            return;
        }

        WORLDZERO_NEXT_ALLOWED_SPAWN_TICK.put(player.getUUID(), gameTime + worldzero$randomCooldown(level));

        if (worldzero$hasActiveEcho(level.getServer())) {
            return;
        }

        boolean shouldTriggerRuleBreak = WorldZeroEchoRuleBreakState.shouldTriggerRuleBreak(level.getServer());
        boolean spawned = worldzero$trySpawnEcho(level, player, shouldTriggerRuleBreak);
        if (!spawned) {
            return;
        }

        if (shouldTriggerRuleBreak) {
            WorldZeroEchoRuleBreakState.recordRuleBreakAppearance(level.getServer());
        } else {
            WorldZeroEchoRuleBreakState.recordNormalAppearance(level.getServer());
        }
    }

    private static boolean worldzero$hasActiveEcho(MinecraftServer server) {
        for (ServerLevel serverLevel : server.getAllLevels()) {
            if (!serverLevel.getEntitiesOfClass(
                    WorldZeroEchoEntity.class,
                    WORLDZERO_ENTITY_SCAN_AABB,
                    echo -> echo.getType() == WorldZeroEntities.WORLDZERO_ECHO.get()
            ).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private static boolean worldzero$trySpawnEcho(
            ServerLevel level,
            ServerPlayer player,
            boolean ruleBreakEvent
    ) {
        for (int attempt = 0; attempt < WORLDZERO_SPAWN_ATTEMPTS; attempt++) {
            double distance = Mth.nextDouble(level.random, WORLDZERO_SPAWN_MIN_DISTANCE, WORLDZERO_SPAWN_MAX_DISTANCE);
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double candidateX = player.getX() + Math.cos(angle) * distance;
            double candidateZ = player.getZ() + Math.sin(angle) * distance;

            int blockX = Mth.floor(candidateX);
            int blockZ = Mth.floor(candidateZ);
            int spawnY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
            BlockPos spawnPos = new BlockPos(blockX, spawnY, blockZ);
            BlockPos belowPos = spawnPos.below();

            BlockState belowState = level.getBlockState(belowPos);
            if (!belowState.isFaceSturdy(level, belowPos, net.minecraft.core.Direction.UP)) {
                continue;
            }

            if (!level.getBlockState(spawnPos).isAir() || !level.getBlockState(spawnPos.above()).isAir()) {
                continue;
            }

            double spawnX = blockX + 0.5D;
            double spawnZ = blockZ + 0.5D;
            Vec3 spawnViewPoint = new Vec3(spawnX, spawnY + 1.0D, spawnZ);
            if (!worldzero$isInPlayerView(level, player, spawnViewPoint)) {
                continue;
            }

            AABB spawnBox = WorldZeroEntities.WORLDZERO_ECHO.get().getDimensions().makeBoundingBox(spawnX, spawnY, spawnZ);
            if (!level.noCollision(spawnBox) || level.containsAnyLiquid(spawnBox)) {
                continue;
            }

            WorldZeroEchoEntity echo = WorldZeroEntities.WORLDZERO_ECHO.get().create(level);
            if (echo == null) {
                return false;
            }

            echo.moveTo(spawnX, spawnY, spawnZ, player.getYRot() + 180.0F, 0.0F);
            echo.setCustomName(net.minecraft.network.chat.Component.literal(player.getGameProfile().getName()));
            echo.setCustomNameVisible(false);
            if (ruleBreakEvent) {
                echo.worldzero$configureRuleBreakEvent(
                        player.getUUID(),
                        Mth.nextInt(level.random, WORLDZERO_RULE_BREAK_IDLE_MIN_TICKS, WORLDZERO_RULE_BREAK_IDLE_MAX_TICKS)
                );
            } else {
                echo.worldzero$setEchoDespawnDistance(
                        Mth.nextDouble(level.random, WORLDZERO_ECHO_DESPAWN_MIN_DISTANCE, WORLDZERO_ECHO_DESPAWN_MAX_DISTANCE)
                );
            }
            level.addFreshEntity(echo);
            return true;
        }

        return false;
    }

    private static boolean worldzero$isInPlayerView(ServerLevel level, ServerPlayer player, Vec3 target) {
        Vec3 eyePosition = player.getEyePosition();
        Vec3 directionToTarget = target.subtract(eyePosition);
        if (directionToTarget.lengthSqr() < 0.0001D) {
            return false;
        }

        Vec3 lookVector = player.getViewVector(1.0F).normalize();
        Vec3 normalizedDirection = directionToTarget.normalize();
        if (normalizedDirection.dot(lookVector) < WORLDZERO_VIEW_DOT_THRESHOLD) {
            return false;
        }

        HitResult hitResult = level.clip(new ClipContext(
                eyePosition,
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
        return !level.getBlockState(hitPos).isSolidRender(level, hitPos);
    }

    private static long worldzero$randomCooldown(ServerLevel level) {
        long span = WORLDZERO_COOLDOWN_MAX_TICKS - WORLDZERO_COOLDOWN_MIN_TICKS + 1L;
        return WORLDZERO_COOLDOWN_MIN_TICKS + (long) (level.random.nextDouble() * span);
    }
}
