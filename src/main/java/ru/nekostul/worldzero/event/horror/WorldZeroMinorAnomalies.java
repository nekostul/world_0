package ru.nekostul.worldzero.event.horror;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.nekostul.worldzero.WorldZeroMod;
import ru.nekostul.worldzero.entity.WorldZeroEchoEntity;
import ru.nekostul.worldzero.entity.WorldZeroEntities;
import ru.nekostul.worldzero.entity.WorldZeroHouseEchoEntity;
import ru.nekostul.worldzero.event.WorldZeroStoryTime;
import ru.nekostul.worldzero.network.WorldZeroMinorAnomalyPacket;
import ru.nekostul.worldzero.network.WorldZeroNetwork;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class WorldZeroMinorAnomalies {
    private static final ResourceLocation WORLDZERO_BREATH_SOUND_ID = new ResourceLocation(WorldZeroMod.MOD_ID, "breath");
    private static final ResourceLocation WORLDZERO_WARNING_SOUND_ID = new ResourceLocation(WorldZeroMod.MOD_ID, "warning");
    private static final double WORLDZERO_MIN_OBJECT_DISTANCE_SQR = 5.0D * 5.0D;
    private static final double WORLDZERO_MAX_OBJECT_DISTANCE_SQR = 10.0D * 10.0D;
    private static final int WORLDZERO_OBJECT_RADIUS_BLOCKS = 10;
    private static final int WORLDZERO_LIGHT_RADIUS_BLOCKS = 12;
    private static final int WORLDZERO_BLOCK_UPDATE_FLAGS = 18;
    private static final int WORLDZERO_PHANTOM_STEPS_MIN_DURATION_TICKS = 60;
    private static final int WORLDZERO_PHANTOM_STEPS_MAX_DURATION_TICKS = 90;
    private static final int WORLDZERO_PHANTOM_STEPS_MIN_INTERVAL_TICKS = 8;
    private static final int WORLDZERO_PHANTOM_STEPS_MAX_INTERVAL_TICKS = 13;
    private static final double WORLDZERO_PHANTOM_STEPS_DISTANCE_MIN = 4.8D;
    private static final double WORLDZERO_PHANTOM_STEPS_DISTANCE_MAX = 6.6D;
    private static final float WORLDZERO_PHANTOM_STEPS_VOLUME = 0.34F;
    private static final double WORLDZERO_PHANTOM_STEPS_LOOK_DOT_THRESHOLD = 0.58D;
    private static final int WORLDZERO_WRONG_WIND_SOUND_TICKS = 15 * 20 + 2;
    private static final int WORLDZERO_WRONG_WIND_ECHO_SEEN_TICKS = 2;
    private static final int WORLDZERO_WRONG_WIND_ECHO_MIN_INTERVAL_TICKS = 10;
    private static final int WORLDZERO_WRONG_WIND_ECHO_MAX_INTERVAL_TICKS = 18;
    private static final double WORLDZERO_WRONG_WIND_LOOK_DOT_THRESHOLD = 0.65D;
    private static final double WORLDZERO_WRONG_WIND_ECHO_SPEED_MIN = 0.42D;
    private static final double WORLDZERO_WRONG_WIND_ECHO_SPEED_MAX = 0.62D;
    private static final double[] WORLDZERO_WRONG_WIND_SPAWN_ANGLES = {
            Math.PI * 0.62D,
            -Math.PI * 0.62D,
            Math.PI * 0.38D,
            -Math.PI * 0.38D
    };
    private static final Map<MinecraftServer, SessionState> WORLDZERO_SESSION_STATES = new WeakHashMap<>();

    private WorldZeroMinorAnomalies() {
    }

    public static void worldzero$tick(ServerLevel level, long worldTicks) {
        SessionState state = WORLDZERO_SESSION_STATES.get(level.getServer());
        if (state == null) {
            return;
        }

        worldzero$tickPendingSteps(level, state, worldTicks);
        worldzero$tickActivePhantomSteps(level, state, worldTicks);
        worldzero$tickPendingSounds(level, state, worldTicks);
        worldzero$tickPendingDoorCloses(level, state, worldTicks);
        worldzero$tickPendingBlockRestores(level, state, worldTicks);
        worldzero$tickActiveWrongWinds(level, state, worldTicks);
        worldzero$tickActiveWrongWindEchoes(level, state, worldTicks);
        worldzero$tickActiveBlackouts(level, state, worldTicks);
    }

    public static void worldzero$cancelAll(MinecraftServer server) {
        if (server == null) {
            return;
        }

        SessionState state = WORLDZERO_SESSION_STATES.remove(server);
        if (state == null) {
            return;
        }

        for (PendingDoorClose doorClose : state.worldzero$pendingDoorCloses) {
            ServerLevel level = server.getLevel(doorClose.dimension());
            if (level != null) {
                worldzero$setDoorOpen(level, doorClose.lowerPos(), false);
            }
        }

        for (PendingBlockRestore blockRestore : state.worldzero$pendingBlockRestores) {
            ServerLevel level = server.getLevel(blockRestore.dimension());
            if (level != null) {
                worldzero$restoreBlock(level, blockRestore);
            }
        }

        for (ActiveBlackout blackout : state.worldzero$activeBlackouts) {
            ServerLevel level = server.getLevel(blackout.dimension());
            if (level != null) {
                worldzero$finishBlackout(level, blackout);
            }
        }

        for (ActiveWrongWindEcho wrongWindEcho : state.worldzero$activeWrongWindEchoes) {
            ServerLevel level = server.getLevel(wrongWindEcho.worldzero$dimension);
            if (level != null) {
                Entity entity = level.getEntity(wrongWindEcho.worldzero$echoId);
                if (entity != null) {
                    entity.discard();
                }
            }
        }
    }

    public static TriggerResult worldzero$tryTriggerRandom(
            ServerLevel level,
            WorldZeroHorrorPhase phase,
            long worldTicks
    ) {
        return worldzero$tryTriggerRandom(level, phase, worldTicks, true);
    }

    public static TriggerResult worldzero$tryTriggerRandom(
            ServerLevel level,
            WorldZeroHorrorPhase phase,
            long worldTicks,
            boolean allowWrongWind
    ) {
        ServerPlayer player = worldzero$pickTargetPlayer(level);
        if (player == null) {
            return TriggerResult.worldzero$failed();
        }

        MinorAnomalyType[] candidates = worldzero$candidatesForPhase(phase);
        if (candidates.length == 0) {
            return TriggerResult.worldzero$failed();
        }

        int offset = level.random.nextInt(candidates.length);
        for (int index = 0; index < candidates.length; index++) {
            MinorAnomalyType anomalyType = candidates[(offset + index) % candidates.length];
            if (!allowWrongWind && anomalyType == MinorAnomalyType.WRONG_WIND) {
                continue;
            }

            TriggerResult result = worldzero$trigger(level, player, anomalyType, worldTicks);
            if (result.worldzero$triggered()) {
                return result;
            }
        }

        return TriggerResult.worldzero$failed();
    }

    public static TriggerResult worldzero$trigger(
            ServerLevel level,
            ServerPlayer player,
            MinorAnomalyType anomalyType,
            long worldTicks
    ) {
        if (!worldzero$isValidPlayer(player) || level.dimension() != Level.OVERWORLD) {
            return TriggerResult.worldzero$failed();
        }

        return switch (anomalyType) {
            case PERIPHERAL_ECHO -> worldzero$triggerPeripheralEcho(level, player);
            case PHANTOM_STEPS -> worldzero$triggerPhantomSteps(level, player, worldTicks);
            case WHISPER -> worldzero$triggerWhisper(level, player);
            case OBJECT_PRESENCE -> worldzero$triggerObjectPresence(level, player, worldTicks);
            case LIGHT_ANOMALY -> worldzero$triggerLightAnomaly(level, player, worldTicks);
            case SHADOW_DELAY -> worldzero$triggerShadowDelay(level, player);
            case WRONG_WIND -> worldzero$triggerWrongWind(level, player, worldTicks);
            case ENTITY_BLACKOUT -> worldzero$triggerEntityBlackout(level, player, worldTicks);
            case BLOCK_BLINK -> worldzero$triggerBlockBlink(level, player, worldTicks);
        };
    }

    private static TriggerResult worldzero$triggerPeripheralEcho(ServerLevel level, ServerPlayer player) {
        int durationTicks = Mth.nextInt(level.random, 6, 10);
        WorldZeroNetwork.sendMinorAnomaly(
                player,
                WorldZeroMinorAnomalyPacket.WORLDZERO_ACTION_PERIPHERAL_ECHO,
                durationTicks,
                level.random.nextInt(2)
        );
        return TriggerResult.worldzero$success(MinorAnomalyType.PERIPHERAL_ECHO, durationTicks + 5);
    }

    private static TriggerResult worldzero$triggerPhantomSteps(ServerLevel level, ServerPlayer player, long worldTicks) {
        double sideOffset = Mth.nextDouble(level.random, -0.35D, 0.35D);
        Vec3 source = worldzero$resolveFootstepSource(
                level,
                player,
                sideOffset,
                WORLDZERO_PHANTOM_STEPS_DISTANCE_MIN,
                WORLDZERO_PHANTOM_STEPS_DISTANCE_MAX
        );
        worldzero$playFootstepAt(level, player, source, WORLDZERO_PHANTOM_STEPS_VOLUME);

        int durationTicks = Mth.nextInt(
                level.random,
                WORLDZERO_PHANTOM_STEPS_MIN_DURATION_TICKS,
                WORLDZERO_PHANTOM_STEPS_MAX_DURATION_TICKS
        );
        worldzero$getState(level).worldzero$activePhantomSteps.add(new ActivePhantomSteps(
                level.dimension(),
                player.getUUID(),
                source,
                sideOffset,
                worldTicks + Mth.nextInt(level.random, WORLDZERO_PHANTOM_STEPS_MIN_INTERVAL_TICKS, WORLDZERO_PHANTOM_STEPS_MAX_INTERVAL_TICKS),
                worldTicks + durationTicks
        ));
        return TriggerResult.worldzero$success(MinorAnomalyType.PHANTOM_STEPS, durationTicks);
    }

    private static TriggerResult worldzero$triggerWhisper(ServerLevel level, ServerPlayer player) {
        Vec3 source = worldzero$randomNearSideSource(level, player, 3.0D, 5.5D);
        level.playSound(
                null,
                source.x,
                source.y,
                source.z,
                SoundEvent.createVariableRangeEvent(WORLDZERO_BREATH_SOUND_ID),
                SoundSource.PLAYERS,
                Mth.nextFloat(level.random, 0.75F, 0.95F),
                Mth.nextFloat(level.random, 0.72F, 0.88F)
        );
        return TriggerResult.worldzero$success(MinorAnomalyType.WHISPER, 35);
    }

    private static TriggerResult worldzero$triggerObjectPresence(ServerLevel level, ServerPlayer player, long worldTicks) {
        ObjectPresenceTarget target = worldzero$findObjectPresenceTarget(level, player);
        if (target == null) {
            return TriggerResult.worldzero$failed();
        }

        int closeDelayTicks = Mth.nextInt(level.random, 12, 28);
        if (target.door()) {
            if (!worldzero$setDoorOpen(level, target.pos(), true)) {
                return TriggerResult.worldzero$failed();
            }

            worldzero$playDoorSound(level, target.pos(), true);
            worldzero$getState(level).worldzero$pendingDoorCloses.add(new PendingDoorClose(
                    level.dimension(),
                    target.pos(),
                    worldTicks + closeDelayTicks
            ));
        } else {
            worldzero$playChestSound(level, target.pos(), true);
            worldzero$getState(level).worldzero$pendingSounds.add(new PendingSound(
                    level.dimension(),
                    target.pos().getX() + 0.5D,
                    target.pos().getY() + 0.5D,
                    target.pos().getZ() + 0.5D,
                    worldzero$chestSound(level, target.pos(), false),
                    SoundSource.BLOCKS,
                    0.8F,
                    0.92F + level.random.nextFloat() * 0.1F,
                    worldTicks + closeDelayTicks
            ));
        }

        return TriggerResult.worldzero$success(MinorAnomalyType.OBJECT_PRESENCE, closeDelayTicks + 6);
    }

    private static TriggerResult worldzero$triggerLightAnomaly(ServerLevel level, ServerPlayer player, long worldTicks) {
        LightTarget target = worldzero$findLightTarget(level, player);
        if (target == null) {
            return TriggerResult.worldzero$failed();
        }

        int durationTicks = Mth.nextInt(level.random, 40, 80);
        level.setBlock(target.pos(), target.temporaryState(), Block.UPDATE_CLIENTS);
        worldzero$getState(level).worldzero$pendingBlockRestores.add(new PendingBlockRestore(
                level.dimension(),
                target.pos(),
                target.originalState(),
                target.temporaryState(),
                BlockRestoreMode.TEMPORARY_STATE,
                worldTicks + durationTicks
        ));
        level.playSound(
                null,
                target.pos(),
                SoundEvents.AMBIENT_CAVE.value(),
                SoundSource.AMBIENT,
                0.33F,
                Mth.nextFloat(level.random, 0.48F, 0.68F)
        );
        return TriggerResult.worldzero$success(MinorAnomalyType.LIGHT_ANOMALY, durationTicks + 2);
    }

    private static TriggerResult worldzero$triggerShadowDelay(ServerLevel level, ServerPlayer player) {
        int durationTicks = Mth.nextInt(level.random, 10, 20);
        WorldZeroNetwork.sendMinorAnomaly(
                player,
                WorldZeroMinorAnomalyPacket.WORLDZERO_ACTION_SHADOW_DELAY,
                durationTicks,
                Mth.nextInt(level.random, -1, 1)
        );
        return TriggerResult.worldzero$success(MinorAnomalyType.SHADOW_DELAY, durationTicks + 4);
    }

    private static TriggerResult worldzero$triggerWrongWind(ServerLevel level, ServerPlayer player, long worldTicks) {
        if (!worldzero$isPlayerOutside(level, player)) {
            return TriggerResult.worldzero$failed();
        }

        int durationTicks = WORLDZERO_WRONG_WIND_SOUND_TICKS;
        level.playSound(
                null,
                player.getX(),
                player.getY() + 1.0D,
                player.getZ(),
                SoundEvent.createVariableRangeEvent(WORLDZERO_WARNING_SOUND_ID),
                SoundSource.WEATHER,
                0.3F,
                0.55F + level.random.nextFloat() * 0.08F
        );

        ActiveWrongWind wrongWind = new ActiveWrongWind(
                level.dimension(),
                player.getUUID(),
                worldTicks + durationTicks,
                worldTicks,
                level.random.nextInt(WORLDZERO_WRONG_WIND_SPAWN_ANGLES.length)
        );
        worldzero$getState(level).worldzero$activeWrongWinds.add(wrongWind);
        boolean spawned = worldzero$spawnWrongWindEcho(level, player, wrongWind, worldTicks);
        wrongWind.worldzero$nextEchoWorldTick = worldTicks + (spawned
                ? Mth.nextInt(level.random, WORLDZERO_WRONG_WIND_ECHO_MIN_INTERVAL_TICKS, WORLDZERO_WRONG_WIND_ECHO_MAX_INTERVAL_TICKS)
                : 6);
        return TriggerResult.worldzero$success(MinorAnomalyType.WRONG_WIND, durationTicks);
    }

    private static TriggerResult worldzero$triggerEntityBlackout(ServerLevel level, ServerPlayer player, long worldTicks) {
        Mob target = worldzero$findBlackoutTarget(level, player);
        if (target == null) {
            return TriggerResult.worldzero$failed();
        }

        int durationTicks = Mth.nextInt(level.random, 10, 20);
        boolean wasInvisible = target.isInvisible();
        target.setInvisible(true);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, durationTicks, 6, false, false));
        worldzero$spawnBlackoutParticles(level, target);
        worldzero$getState(level).worldzero$activeBlackouts.add(new ActiveBlackout(
                level.dimension(),
                target.getUUID(),
                wasInvisible,
                worldTicks + durationTicks
        ));
        return TriggerResult.worldzero$success(MinorAnomalyType.ENTITY_BLACKOUT, durationTicks + 2);
    }

    private static TriggerResult worldzero$triggerBlockBlink(ServerLevel level, ServerPlayer player, long worldTicks) {
        BlockHitResult hitResult = worldzero$getLookedAtBlock(level, player, 18.0D);
        if (hitResult == null) {
            return TriggerResult.worldzero$failed();
        }

        BlockPos pos = hitResult.getBlockPos();
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) < 4.0D * 4.0D) {
            return TriggerResult.worldzero$failed();
        }

        BlockState originalState = level.getBlockState(pos);
        if (!worldzero$isBlinkableBlock(level, pos, originalState)) {
            return TriggerResult.worldzero$failed();
        }

        int durationTicks = Mth.nextInt(level.random, 6, 14);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        worldzero$getState(level).worldzero$pendingBlockRestores.add(new PendingBlockRestore(
                level.dimension(),
                pos.immutable(),
                originalState,
                Blocks.AIR.defaultBlockState(),
                BlockRestoreMode.AIR_ONLY,
                worldTicks + durationTicks
        ));
        return TriggerResult.worldzero$success(MinorAnomalyType.BLOCK_BLINK, durationTicks + 2);
    }

    private static void worldzero$tickPendingSteps(ServerLevel level, SessionState state, long worldTicks) {
        Iterator<PendingStep> iterator = state.worldzero$pendingSteps.iterator();
        while (iterator.hasNext()) {
            PendingStep pendingStep = iterator.next();
            if (pendingStep.dimension() != level.dimension() || worldTicks < pendingStep.triggerWorldTick()) {
                continue;
            }

            ServerPlayer player = level.getServer().getPlayerList().getPlayer(pendingStep.playerId());
            if (player != null) {
                Vec3 source = pendingStep.source().add(
                        Mth.nextDouble(level.random, -0.35D, 0.35D),
                        0.0D,
                        Mth.nextDouble(level.random, -0.35D, 0.35D)
                );
                worldzero$playFootstepAt(level, player, source);
            }
            iterator.remove();
        }
    }

    private static void worldzero$tickActivePhantomSteps(ServerLevel level, SessionState state, long worldTicks) {
        Iterator<ActivePhantomSteps> iterator = state.worldzero$activePhantomSteps.iterator();
        while (iterator.hasNext()) {
            ActivePhantomSteps phantomSteps = iterator.next();
            if (phantomSteps.worldzero$dimension != level.dimension()) {
                continue;
            }

            ServerPlayer player = level.getServer().getPlayerList().getPlayer(phantomSteps.worldzero$playerId);
            if (!worldzero$isValidPlayer(player)
                    || worldTicks >= phantomSteps.worldzero$endWorldTick
                    || worldzero$isLookingTowardSource(player, phantomSteps.worldzero$lastSource, WORLDZERO_PHANTOM_STEPS_LOOK_DOT_THRESHOLD)) {
                iterator.remove();
                continue;
            }

            if (worldTicks < phantomSteps.worldzero$nextStepWorldTick) {
                continue;
            }

            Vec3 source = worldzero$resolveFootstepSource(
                    level,
                    player,
                    phantomSteps.worldzero$sideOffset,
                    WORLDZERO_PHANTOM_STEPS_DISTANCE_MIN,
                    WORLDZERO_PHANTOM_STEPS_DISTANCE_MAX
            );
            worldzero$playFootstepAt(level, player, source, WORLDZERO_PHANTOM_STEPS_VOLUME);
            phantomSteps.worldzero$lastSource = source;
            phantomSteps.worldzero$nextStepWorldTick = worldTicks + Mth.nextInt(
                    level.random,
                    WORLDZERO_PHANTOM_STEPS_MIN_INTERVAL_TICKS,
                    WORLDZERO_PHANTOM_STEPS_MAX_INTERVAL_TICKS
            );
        }
    }

    private static void worldzero$tickPendingSounds(ServerLevel level, SessionState state, long worldTicks) {
        Iterator<PendingSound> iterator = state.worldzero$pendingSounds.iterator();
        while (iterator.hasNext()) {
            PendingSound pendingSound = iterator.next();
            if (pendingSound.dimension() != level.dimension() || worldTicks < pendingSound.triggerWorldTick()) {
                continue;
            }

            level.playSound(
                    null,
                    pendingSound.x(),
                    pendingSound.y(),
                    pendingSound.z(),
                    pendingSound.soundEvent(),
                    pendingSound.source(),
                    pendingSound.volume(),
                    pendingSound.pitch()
            );
            iterator.remove();
        }
    }

    private static void worldzero$tickPendingDoorCloses(ServerLevel level, SessionState state, long worldTicks) {
        Iterator<PendingDoorClose> iterator = state.worldzero$pendingDoorCloses.iterator();
        while (iterator.hasNext()) {
            PendingDoorClose doorClose = iterator.next();
            if (doorClose.dimension() != level.dimension() || worldTicks < doorClose.triggerWorldTick()) {
                continue;
            }

            if (worldzero$setDoorOpen(level, doorClose.lowerPos(), false)) {
                worldzero$playDoorSound(level, doorClose.lowerPos(), false);
            }
            iterator.remove();
        }
    }

    private static void worldzero$tickPendingBlockRestores(ServerLevel level, SessionState state, long worldTicks) {
        Iterator<PendingBlockRestore> iterator = state.worldzero$pendingBlockRestores.iterator();
        while (iterator.hasNext()) {
            PendingBlockRestore blockRestore = iterator.next();
            if (blockRestore.dimension() != level.dimension() || worldTicks < blockRestore.triggerWorldTick()) {
                continue;
            }

            worldzero$restoreBlock(level, blockRestore);
            iterator.remove();
        }
    }

    private static void worldzero$tickActiveWrongWinds(ServerLevel level, SessionState state, long worldTicks) {
        Iterator<ActiveWrongWind> iterator = state.worldzero$activeWrongWinds.iterator();
        while (iterator.hasNext()) {
            ActiveWrongWind wrongWind = iterator.next();
            if (wrongWind.worldzero$dimension != level.dimension()) {
                continue;
            }

            ServerPlayer player = level.getServer().getPlayerList().getPlayer(wrongWind.worldzero$playerId);
            if (!worldzero$isValidPlayer(player) || worldTicks >= wrongWind.worldzero$endWorldTick) {
                iterator.remove();
                continue;
            }

            if (worldTicks < wrongWind.worldzero$nextEchoWorldTick) {
                continue;
            }

            boolean spawned = worldzero$spawnWrongWindEcho(level, player, wrongWind, worldTicks);
            int delayTicks = spawned
                    ? Mth.nextInt(level.random, WORLDZERO_WRONG_WIND_ECHO_MIN_INTERVAL_TICKS, WORLDZERO_WRONG_WIND_ECHO_MAX_INTERVAL_TICKS)
                    : 6;
            wrongWind.worldzero$nextEchoWorldTick = worldTicks + delayTicks;
        }
    }

    private static void worldzero$tickActiveWrongWindEchoes(ServerLevel level, SessionState state, long worldTicks) {
        Iterator<ActiveWrongWindEcho> iterator = state.worldzero$activeWrongWindEchoes.iterator();
        while (iterator.hasNext()) {
            ActiveWrongWindEcho wrongWindEcho = iterator.next();
            if (wrongWindEcho.worldzero$dimension != level.dimension()) {
                continue;
            }

            Entity entity = level.getEntity(wrongWindEcho.worldzero$echoId);
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(wrongWindEcho.worldzero$playerId);
            if (!(entity instanceof WorldZeroEchoEntity) || !worldzero$isValidPlayer(player) || worldTicks >= wrongWindEcho.worldzero$endWorldTick) {
                if (entity != null) {
                    entity.discard();
                }
                iterator.remove();
                continue;
            }

            if (worldzero$isWrongWindEchoSeen(player, entity)) {
                wrongWindEcho.worldzero$seenTicks++;
                if (wrongWindEcho.worldzero$seenTicks >= WORLDZERO_WRONG_WIND_ECHO_SEEN_TICKS) {
                    entity.discard();
                    iterator.remove();
                }
            } else {
                wrongWindEcho.worldzero$seenTicks = 0;
            }
        }
    }

    private static void worldzero$tickActiveBlackouts(ServerLevel level, SessionState state, long worldTicks) {
        Iterator<ActiveBlackout> iterator = state.worldzero$activeBlackouts.iterator();
        while (iterator.hasNext()) {
            ActiveBlackout blackout = iterator.next();
            if (blackout.dimension() != level.dimension()) {
                continue;
            }

            Entity entity = level.getEntity(blackout.entityId());
            if (entity instanceof Mob mob && worldTicks < blackout.endWorldTick()) {
                worldzero$spawnBlackoutParticles(level, mob);
                continue;
            }

            worldzero$finishBlackout(level, blackout);
            iterator.remove();
        }
    }

    private static void worldzero$restoreBlock(ServerLevel level, PendingBlockRestore blockRestore) {
        BlockState currentState = level.getBlockState(blockRestore.pos());
        if (blockRestore.mode() == BlockRestoreMode.AIR_ONLY) {
            if (currentState.isAir()) {
                level.setBlock(blockRestore.pos(), blockRestore.originalState(), Block.UPDATE_CLIENTS);
            }
            return;
        }

        if (currentState == blockRestore.temporaryState() || currentState.equals(blockRestore.temporaryState())) {
            level.setBlock(blockRestore.pos(), blockRestore.originalState(), Block.UPDATE_CLIENTS);
        }
    }

    private static void worldzero$finishBlackout(ServerLevel level, ActiveBlackout blackout) {
        Entity entity = level.getEntity(blackout.entityId());
        if (entity != null && !blackout.wasInvisible()) {
            entity.setInvisible(false);
        }
    }

    private static boolean worldzero$spawnWrongWindEcho(
            ServerLevel level,
            ServerPlayer player,
            ActiveWrongWind wrongWind,
            long worldTicks
    ) {
        Vec3 spawnPos = worldzero$findWrongWindEchoSpawn(level, player, wrongWind.worldzero$spawnIndex);
        wrongWind.worldzero$spawnIndex++;
        if (spawnPos == null) {
            return false;
        }

        WorldZeroEchoEntity echo = WorldZeroEntities.WORLDZERO_ECHO.get().create(level);
        if (echo == null) {
            return false;
        }

        double directionX = player.getX() - spawnPos.x;
        double directionZ = player.getZ() - spawnPos.z;
        double horizontalDistance = Math.sqrt(directionX * directionX + directionZ * directionZ);
        if (horizontalDistance < 0.0001D) {
            return false;
        }

        double speed = Mth.nextDouble(level.random, WORLDZERO_WRONG_WIND_ECHO_SPEED_MIN, WORLDZERO_WRONG_WIND_ECHO_SPEED_MAX);
        int travelTicks = Mth.clamp(Mth.ceil(horizontalDistance / speed), 10, 24);
        float yaw = (float) (Mth.atan2(directionZ, directionX) * (180.0D / Math.PI)) - 90.0F;

        echo.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, yaw, 0.0F);
        echo.setDeltaMovement(0.0D, 0.0D, 0.0D);
        echo.setCustomName(Component.literal(player.getGameProfile().getName()));
        echo.setCustomNameVisible(false);
        echo.worldzero$configureKoridorPass(directionX, directionZ, speed, travelTicks);
        level.addFreshEntity(echo);

        worldzero$getState(level).worldzero$activeWrongWindEchoes.add(new ActiveWrongWindEcho(
                level.dimension(),
                echo.getUUID(),
                player.getUUID(),
                Math.min(wrongWind.worldzero$endWorldTick, worldTicks + travelTicks + 2)
        ));
        return true;
    }

    @Nullable
    private static Vec3 worldzero$findWrongWindEchoSpawn(ServerLevel level, ServerPlayer player, int spawnIndex) {
        Vec3 look = worldzero$horizontalLook(player);
        double baseAngle = Math.atan2(look.z, look.x);
        double angleOffset = WORLDZERO_WRONG_WIND_SPAWN_ANGLES[spawnIndex % WORLDZERO_WRONG_WIND_SPAWN_ANGLES.length];
        double angle = baseAngle + angleOffset + Mth.nextDouble(level.random, -0.18D, 0.18D);

        double[] distances = {10.5D, 8.5D, 12.5D, 6.5D};
        for (double distance : distances) {
            Vec3 candidate = worldzero$findWrongWindEchoSpawnAt(level, player, angle, distance);
            if (candidate != null) {
                return candidate;
            }
        }

        for (int attempt = 0; attempt < WORLDZERO_WRONG_WIND_SPAWN_ANGLES.length; attempt++) {
            double fallbackAngle = baseAngle
                    + WORLDZERO_WRONG_WIND_SPAWN_ANGLES[(spawnIndex + attempt + 1) % WORLDZERO_WRONG_WIND_SPAWN_ANGLES.length]
                    + Mth.nextDouble(level.random, -0.25D, 0.25D);
            Vec3 candidate = worldzero$findWrongWindEchoSpawnAt(level, player, fallbackAngle, 8.5D);
            if (candidate != null) {
                return candidate;
            }
        }

        return null;
    }

    @Nullable
    private static Vec3 worldzero$findWrongWindEchoSpawnAt(
            ServerLevel level,
            ServerPlayer player,
            double angle,
            double distance
    ) {
        double x = player.getX() + Math.cos(angle) * distance;
        double z = player.getZ() + Math.sin(angle) * distance;
        int baseY = Mth.floor(player.getY());

        for (int yOffset = 1; yOffset >= -4; yOffset--) {
            Vec3 candidate = new Vec3(x, baseY + yOffset, z);
            if (worldzero$isWrongWindEchoSpawnValid(level, candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private static boolean worldzero$isWrongWindEchoSpawnValid(ServerLevel level, Vec3 candidate) {
        AABB spawnBox = WorldZeroEntities.WORLDZERO_ECHO.get().getDimensions().makeBoundingBox(
                candidate.x,
                candidate.y,
                candidate.z
        );
        if (!level.noCollision(spawnBox) || level.containsAnyLiquid(spawnBox)) {
            return false;
        }

        BlockPos feetPos = BlockPos.containing(candidate.x, candidate.y, candidate.z);
        BlockPos belowPos = feetPos.below();
        BlockState belowState = level.getBlockState(belowPos);
        return belowState.isFaceSturdy(level, belowPos, Direction.UP)
                && belowState.getFluidState().isEmpty();
    }

    private static boolean worldzero$isWrongWindEchoSeen(ServerPlayer player, Entity entity) {
        if (!player.hasLineOfSight(entity)) {
            return false;
        }

        Vec3 directionToEcho = entity.getBoundingBox().getCenter().subtract(player.getEyePosition());
        if (directionToEcho.lengthSqr() < 0.0001D) {
            return false;
        }

        return player.getViewVector(1.0F).normalize().dot(directionToEcho.normalize()) >= WORLDZERO_WRONG_WIND_LOOK_DOT_THRESHOLD;
    }

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

    private static boolean worldzero$isValidPlayer(ServerPlayer player) {
        return player != null && player.isAlive() && !player.isSpectator();
    }

    private static boolean worldzero$isPlayerOutside(ServerLevel level, ServerPlayer player) {
        BlockPos feetPos = player.blockPosition();
        BlockPos eyePos = BlockPos.containing(player.getEyePosition());
        FluidState feetFluid = level.getFluidState(feetPos);
        FluidState eyeFluid = level.getFluidState(eyePos);
        return level.canSeeSky(feetPos)
                && level.canSeeSky(eyePos)
                && feetFluid.isEmpty()
                && eyeFluid.isEmpty();
    }

    private static boolean worldzero$isLookingTowardSource(ServerPlayer player, Vec3 source, double dotThreshold) {
        Vec3 toSource = source.subtract(player.getEyePosition());
        if (toSource.lengthSqr() < 0.0001D) {
            return false;
        }

        return player.getViewVector(1.0F).normalize().dot(toSource.normalize()) >= dotThreshold;
    }

    private static MinorAnomalyType[] worldzero$candidatesForPhase(WorldZeroHorrorPhase phase) {
        return switch (phase) {
            case EARLY -> new MinorAnomalyType[]{
                    MinorAnomalyType.PERIPHERAL_ECHO,
                    MinorAnomalyType.WHISPER
            };
            case ACTIVE -> new MinorAnomalyType[]{
                    MinorAnomalyType.PERIPHERAL_ECHO,
                    MinorAnomalyType.WHISPER
            };
            case RISING -> new MinorAnomalyType[]{
                    MinorAnomalyType.PERIPHERAL_ECHO,
                    MinorAnomalyType.WHISPER,
                    MinorAnomalyType.LIGHT_ANOMALY,
                    MinorAnomalyType.WRONG_WIND
            };
            case PEAK -> new MinorAnomalyType[]{
                    MinorAnomalyType.PERIPHERAL_ECHO,
                    MinorAnomalyType.WHISPER,
                    MinorAnomalyType.LIGHT_ANOMALY,
                    MinorAnomalyType.WRONG_WIND,
                    MinorAnomalyType.SHADOW_DELAY,
                    MinorAnomalyType.ENTITY_BLACKOUT,
                    MinorAnomalyType.BLOCK_BLINK
            };
            case DECLINE -> new MinorAnomalyType[]{
                    MinorAnomalyType.WHISPER,
                    MinorAnomalyType.PERIPHERAL_ECHO,
                    MinorAnomalyType.LIGHT_ANOMALY,
                    MinorAnomalyType.SHADOW_DELAY,
                    MinorAnomalyType.ENTITY_BLACKOUT,
                    MinorAnomalyType.BLOCK_BLINK
            };
            default -> new MinorAnomalyType[0];
        };
    }

    private static Vec3 worldzero$resolveFootstepSource(ServerLevel level, ServerPlayer player) {
        return worldzero$resolveFootstepSource(
                level,
                player,
                Mth.nextDouble(level.random, -1.2D, 1.2D),
                2.2D,
                3.2D
        );
    }

    private static Vec3 worldzero$resolveFootstepSource(
            ServerLevel level,
            ServerPlayer player,
            double sideOffset,
            double minDistance,
            double maxDistance
    ) {
        Vec3 look = player.getViewVector(1.0F);
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() < 1.0E-6D) {
            float radians = (float) Math.toRadians(player.getYRot());
            horizontalLook = new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
        }
        horizontalLook = horizontalLook.normalize();

        Vec3 side = new Vec3(-horizontalLook.z, 0.0D, horizontalLook.x).scale(sideOffset);
        Vec3 base = player.position().subtract(horizontalLook.scale(Mth.nextDouble(level.random, minDistance, maxDistance))).add(side);
        int baseX = Mth.floor(base.x);
        int baseZ = Mth.floor(base.z);
        int playerY = Mth.floor(player.getY());

        for (int dy = 1; dy >= -3; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos candidate = new BlockPos(baseX + dx, playerY + dy, baseZ + dz);
                    if (worldzero$isWalkableStepSpot(level, candidate)) {
                        return new Vec3(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
                    }
                }
            }
        }

        return new Vec3(base.x, player.getY(), base.z);
    }

    private static boolean worldzero$isWalkableStepSpot(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockState aboveState = level.getBlockState(pos.above());
        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);
        FluidState fluidState = state.getFluidState();
        return state.isAir()
                && aboveState.isAir()
                && fluidState.isEmpty()
                && belowState.isFaceSturdy(level, belowPos, Direction.UP)
                && belowState.getFluidState().isEmpty();
    }

    private static void worldzero$playFootstepAt(ServerLevel level, @Nullable Entity entity, Vec3 source) {
        worldzero$playFootstepAt(level, entity, source, 0.48F);
    }

    private static void worldzero$playFootstepAt(ServerLevel level, @Nullable Entity entity, Vec3 source, float volume) {
        BlockPos belowPos = worldzero$resolveFootstepSoundBlock(entity, source);
        BlockState belowState = level.getBlockState(belowPos);
        SoundType soundType = belowState.getSoundType(level, belowPos, entity);
        level.playSound(
                null,
                source.x,
                source.y,
                source.z,
                soundType.getStepSound(),
                SoundSource.BLOCKS,
                volume,
                0.86F + level.random.nextFloat() * 0.16F
        );
    }

    private static BlockPos worldzero$resolveFootstepSoundBlock(@Nullable Entity entity, Vec3 source) {
        if (entity != null) {
            return BlockPos.containing(entity.getX(), entity.getY() - 0.2D, entity.getZ());
        }

        return BlockPos.containing(source.x, source.y - 0.2D, source.z);
    }

    private static Vec3 worldzero$randomDistantSource(
            ServerLevel level,
            ServerPlayer player,
            double minDistance,
            double maxDistance
    ) {
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        double distance = Mth.nextDouble(level.random, minDistance, maxDistance);
        return new Vec3(
                player.getX() + Math.cos(angle) * distance,
                player.getY() + Mth.nextDouble(level.random, -1.0D, 2.0D),
                player.getZ() + Math.sin(angle) * distance
        );
    }

    private static Vec3 worldzero$randomNearSideSource(
            ServerLevel level,
            ServerPlayer player,
            double minDistance,
            double maxDistance
    ) {
        Vec3 look = worldzero$horizontalLook(player);
        Vec3 side = new Vec3(-look.z, 0.0D, look.x);
        double sideDistance = Mth.nextDouble(level.random, minDistance, maxDistance);
        if (level.random.nextBoolean()) {
            sideDistance = -sideDistance;
        }

        double backDistance = Mth.nextDouble(level.random, 1.0D, 2.2D);
        return player.position()
                .subtract(look.scale(backDistance))
                .add(side.scale(sideDistance))
                .add(0.0D, Mth.nextDouble(level.random, 0.4D, 1.4D), 0.0D);
    }

    private static Vec3 worldzero$horizontalLook(ServerPlayer player) {
        Vec3 look = player.getViewVector(1.0F);
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() < 1.0E-6D) {
            float radians = (float) Math.toRadians(player.getYRot());
            horizontalLook = new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
        }
        return horizontalLook.normalize();
    }

    @Nullable
    private static ObjectPresenceTarget worldzero$findObjectPresenceTarget(ServerLevel level, ServerPlayer player) {
        List<ObjectPresenceTarget> targets = new ArrayList<>();
        BlockPos center = player.blockPosition();
        BlockPos min = center.offset(-WORLDZERO_OBJECT_RADIUS_BLOCKS, -4, -WORLDZERO_OBJECT_RADIUS_BLOCKS);
        BlockPos max = center.offset(WORLDZERO_OBJECT_RADIUS_BLOCKS, 4, WORLDZERO_OBJECT_RADIUS_BLOCKS);

        for (BlockPos mutablePos : BlockPos.betweenClosed(min, max)) {
            BlockPos pos = mutablePos.immutable();
            double distanceSqr = pos.distSqr(center);
            if (distanceSqr < WORLDZERO_MIN_OBJECT_DISTANCE_SQR
                    || distanceSqr > WORLDZERO_MAX_OBJECT_DISTANCE_SQR
                    || worldzero$isDirectlyInFront(player, pos, 0.82D)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.DOORS)
                    && state.hasProperty(DoorBlock.HALF)
                    && state.hasProperty(DoorBlock.OPEN)
                    && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                    && !state.getValue(DoorBlock.OPEN)) {
                targets.add(new ObjectPresenceTarget(true, pos));
            } else if (state.getBlock() instanceof ChestBlock) {
                targets.add(new ObjectPresenceTarget(false, pos));
            }
        }

        if (targets.isEmpty()) {
            return null;
        }

        return targets.get(level.random.nextInt(targets.size()));
    }

    @Nullable
    private static LightTarget worldzero$findLightTarget(ServerLevel level, ServerPlayer player) {
        List<LightTarget> targets = new ArrayList<>();
        BlockPos center = player.blockPosition();
        BlockPos min = center.offset(-WORLDZERO_LIGHT_RADIUS_BLOCKS, -5, -WORLDZERO_LIGHT_RADIUS_BLOCKS);
        BlockPos max = center.offset(WORLDZERO_LIGHT_RADIUS_BLOCKS, 5, WORLDZERO_LIGHT_RADIUS_BLOCKS);

        for (BlockPos mutablePos : BlockPos.betweenClosed(min, max)) {
            BlockPos pos = mutablePos.immutable();
            double distanceSqr = pos.distSqr(center);
            if (distanceSqr < 3.0D * 3.0D || distanceSqr > WORLDZERO_LIGHT_RADIUS_BLOCKS * WORLDZERO_LIGHT_RADIUS_BLOCKS) {
                continue;
            }

            BlockState originalState = level.getBlockState(pos);
            BlockState temporaryState = worldzero$createTemporaryLightState(originalState);
            if (temporaryState != null) {
                targets.add(new LightTarget(pos, originalState, temporaryState));
            }
        }

        if (targets.isEmpty()) {
            return null;
        }

        return targets.get(level.random.nextInt(targets.size()));
    }

    @Nullable
    private static BlockState worldzero$createTemporaryLightState(BlockState originalState) {
        if (originalState.is(Blocks.TORCH)) {
            return Blocks.SOUL_TORCH.defaultBlockState();
        }

        if (originalState.is(Blocks.WALL_TORCH) && originalState.hasProperty(WallTorchBlock.FACING)) {
            return Blocks.SOUL_WALL_TORCH.defaultBlockState()
                    .setValue(WallTorchBlock.FACING, originalState.getValue(WallTorchBlock.FACING));
        }

        return null;
    }

    @Nullable
    private static Mob worldzero$findBlackoutTarget(ServerLevel level, ServerPlayer player) {
        List<Mob> targets = level.getEntitiesOfClass(
                Mob.class,
                player.getBoundingBox().inflate(18.0D),
                mob -> mob.isAlive()
                        && !mob.isRemoved()
                        && !(mob instanceof WorldZeroEchoEntity)
                        && !(mob instanceof WorldZeroHouseEchoEntity)
                        && mob.distanceToSqr(player) >= 5.0D * 5.0D
                        && mob.distanceToSqr(player) <= 18.0D * 18.0D
        );

        if (targets.isEmpty()) {
            return null;
        }

        return targets.get(level.random.nextInt(targets.size()));
    }

    private static void worldzero$spawnBlackoutParticles(ServerLevel level, Mob target) {
        level.sendParticles(
                ParticleTypes.SMOKE,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.55D,
                target.getZ(),
                4,
                target.getBbWidth() * 0.35D,
                target.getBbHeight() * 0.25D,
                target.getBbWidth() * 0.35D,
                0.01D
        );
    }

    @Nullable
    private static BlockHitResult worldzero$getLookedAtBlock(ServerLevel level, ServerPlayer player, double distance) {
        Vec3 eyePosition = player.getEyePosition();
        Vec3 end = eyePosition.add(player.getViewVector(1.0F).scale(distance));
        HitResult hitResult = level.clip(new ClipContext(
                eyePosition,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        if (hitResult.getType() != HitResult.Type.BLOCK || !(hitResult instanceof BlockHitResult blockHitResult)) {
            return null;
        }

        return blockHitResult;
    }

    private static boolean worldzero$isBlinkableBlock(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir()
                && !state.is(BlockTags.DOORS)
                && state.getFluidState().isEmpty()
                && level.getBlockEntity(pos) == null
                && state.getDestroySpeed(level, pos) >= 0.0F
                && state.isFaceSturdy(level, pos, Direction.UP);
    }

    private static boolean worldzero$isDirectlyInFront(ServerPlayer player, BlockPos pos, double dotThreshold) {
        Vec3 target = Vec3.atCenterOf(pos).subtract(player.getEyePosition());
        if (target.lengthSqr() < 0.0001D) {
            return true;
        }

        return target.normalize().dot(player.getViewVector(1.0F).normalize()) > dotThreshold;
    }

    private static boolean worldzero$setDoorOpen(ServerLevel level, BlockPos pos, boolean open) {
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

        level.setBlock(lowerPos, lowerState.setValue(DoorBlock.OPEN, open), WORLDZERO_BLOCK_UPDATE_FLAGS);
        level.setBlock(upperPos, upperState.setValue(DoorBlock.OPEN, open), WORLDZERO_BLOCK_UPDATE_FLAGS);
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
                0.8F,
                0.94F + level.random.nextFloat() * 0.08F
        );
    }

    private static void worldzero$playChestSound(ServerLevel level, BlockPos chestPos, boolean open) {
        level.playSound(
                null,
                chestPos.getX() + 0.5D,
                chestPos.getY() + 0.5D,
                chestPos.getZ() + 0.5D,
                worldzero$chestSound(level, chestPos, open),
                SoundSource.BLOCKS,
                0.8F,
                0.92F + level.random.nextFloat() * 0.1F
        );
    }

    private static SoundEvent worldzero$chestSound(ServerLevel level, BlockPos chestPos, boolean open) {
        BlockState state = level.getBlockState(chestPos);
        if (state.is(Blocks.ENDER_CHEST)) {
            return open ? SoundEvents.ENDER_CHEST_OPEN : SoundEvents.ENDER_CHEST_CLOSE;
        }

        return open ? SoundEvents.CHEST_OPEN : SoundEvents.CHEST_CLOSE;
    }

    private static SessionState worldzero$getState(ServerLevel level) {
        return WORLDZERO_SESSION_STATES.computeIfAbsent(level.getServer(), ignored -> new SessionState());
    }

    public enum MinorAnomalyType {
        PERIPHERAL_ECHO("peripheral_echo"),
        PHANTOM_STEPS("phantom_steps"),
        WHISPER("whisper"),
        OBJECT_PRESENCE("object_presence"),
        LIGHT_ANOMALY("light_anomaly"),
        SHADOW_DELAY("shadow_delay"),
        WRONG_WIND("wrong_wind"),
        ENTITY_BLACKOUT("entity_blackout"),
        BLOCK_BLINK("block_blink");

        private final String worldzero$debugName;

        MinorAnomalyType(String debugName) {
            this.worldzero$debugName = debugName;
        }

        public String worldzero$debugName() {
            return this.worldzero$debugName;
        }
    }

    public static final class TriggerResult {
        private static final TriggerResult WORLDZERO_FAILED = new TriggerResult(false, null, 0);
        private final boolean worldzero$triggered;
        @Nullable
        private final MinorAnomalyType worldzero$type;
        private final int worldzero$durationTicks;

        private TriggerResult(boolean triggered, @Nullable MinorAnomalyType type, int durationTicks) {
            this.worldzero$triggered = triggered;
            this.worldzero$type = type;
            this.worldzero$durationTicks = durationTicks;
        }

        private static TriggerResult worldzero$success(MinorAnomalyType type, int durationTicks) {
            return new TriggerResult(true, type, Math.max(1, durationTicks));
        }

        private static TriggerResult worldzero$failed() {
            return WORLDZERO_FAILED;
        }

        public boolean worldzero$triggered() {
            return this.worldzero$triggered;
        }

        @Nullable
        public MinorAnomalyType worldzero$type() {
            return this.worldzero$type;
        }

        public int worldzero$durationTicks() {
            return this.worldzero$durationTicks;
        }
    }

    private enum BlockRestoreMode {
        TEMPORARY_STATE,
        AIR_ONLY
    }

    private static final class SessionState {
        private final List<PendingStep> worldzero$pendingSteps = new ArrayList<>();
        private final List<ActivePhantomSteps> worldzero$activePhantomSteps = new ArrayList<>();
        private final List<PendingSound> worldzero$pendingSounds = new ArrayList<>();
        private final List<PendingDoorClose> worldzero$pendingDoorCloses = new ArrayList<>();
        private final List<PendingBlockRestore> worldzero$pendingBlockRestores = new ArrayList<>();
        private final List<ActiveWrongWind> worldzero$activeWrongWinds = new ArrayList<>();
        private final List<ActiveWrongWindEcho> worldzero$activeWrongWindEchoes = new ArrayList<>();
        private final List<ActiveBlackout> worldzero$activeBlackouts = new ArrayList<>();
    }

    private record PendingStep(ResourceKey<Level> dimension, UUID playerId, Vec3 source, long triggerWorldTick) {
    }

    private static final class ActivePhantomSteps {
        private final ResourceKey<Level> worldzero$dimension;
        private final UUID worldzero$playerId;
        private final double worldzero$sideOffset;
        private long worldzero$nextStepWorldTick;
        private final long worldzero$endWorldTick;
        private Vec3 worldzero$lastSource;

        private ActivePhantomSteps(
                ResourceKey<Level> dimension,
                UUID playerId,
                Vec3 lastSource,
                double sideOffset,
                long nextStepWorldTick,
                long endWorldTick
        ) {
            this.worldzero$dimension = dimension;
            this.worldzero$playerId = playerId;
            this.worldzero$lastSource = lastSource;
            this.worldzero$sideOffset = sideOffset;
            this.worldzero$nextStepWorldTick = nextStepWorldTick;
            this.worldzero$endWorldTick = endWorldTick;
        }
    }

    private record PendingSound(
            ResourceKey<Level> dimension,
            double x,
            double y,
            double z,
            SoundEvent soundEvent,
            SoundSource source,
            float volume,
            float pitch,
            long triggerWorldTick
    ) {
    }

    private record PendingDoorClose(ResourceKey<Level> dimension, BlockPos lowerPos, long triggerWorldTick) {
    }

    private record PendingBlockRestore(
            ResourceKey<Level> dimension,
            BlockPos pos,
            BlockState originalState,
            BlockState temporaryState,
            BlockRestoreMode mode,
            long triggerWorldTick
    ) {
    }

    private record ActiveBlackout(
            ResourceKey<Level> dimension,
            UUID entityId,
            boolean wasInvisible,
            long endWorldTick
    ) {
    }

    private static final class ActiveWrongWind {
        private final ResourceKey<Level> worldzero$dimension;
        private final UUID worldzero$playerId;
        private final long worldzero$endWorldTick;
        private long worldzero$nextEchoWorldTick;
        private int worldzero$spawnIndex;

        private ActiveWrongWind(
                ResourceKey<Level> dimension,
                UUID playerId,
                long endWorldTick,
                long nextEchoWorldTick,
                int spawnIndex
        ) {
            this.worldzero$dimension = dimension;
            this.worldzero$playerId = playerId;
            this.worldzero$endWorldTick = endWorldTick;
            this.worldzero$nextEchoWorldTick = nextEchoWorldTick;
            this.worldzero$spawnIndex = spawnIndex;
        }
    }

    private static final class ActiveWrongWindEcho {
        private final ResourceKey<Level> worldzero$dimension;
        private final UUID worldzero$echoId;
        private final UUID worldzero$playerId;
        private final long worldzero$endWorldTick;
        private int worldzero$seenTicks;

        private ActiveWrongWindEcho(
                ResourceKey<Level> dimension,
                UUID echoId,
                UUID playerId,
                long endWorldTick
        ) {
            this.worldzero$dimension = dimension;
            this.worldzero$echoId = echoId;
            this.worldzero$playerId = playerId;
            this.worldzero$endWorldTick = endWorldTick;
        }
    }

    private record ObjectPresenceTarget(boolean door, BlockPos pos) {
    }

    private record LightTarget(BlockPos pos, BlockState originalState, BlockState temporaryState) {
    }
}
