package ru.nekostul.worldzero;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroParalysisClientController {
    private static final int WORLDZERO_LOCKED_RENDER_DISTANCE_OPTION = 2;
    private static final int WORLDZERO_EFFECTIVE_RENDER_DISTANCE = 1;
    private static final int WORLDZERO_BUSY_BED_MESSAGE_COOLDOWN_TICKS = 5 * 20;
    private static final int WORLDZERO_RETURN_TO_BED_KNOCK_TICKS = 65;
    private static final int WORLDZERO_RETURN_TO_BED_DOOR_DELAY_TICKS = 20;
    private static final int WORLDZERO_RETURN_TO_BED_FOOTSTEP_INTERVAL_TICKS = 8;
    private static final int WORLDZERO_RETURN_TO_BED_FOOTSTEP_COUNT = 5;
    private static final int WORLDZERO_RETURN_TO_BED_CHEST_CLOSE_DELAY_TICKS = 20;
    private static final int WORLDZERO_RETURN_TO_BED_GLASS_BREAK_DELAY_TICKS = 4;
    private static final int WORLDZERO_RETURN_TO_BED_GLASS_BREAK_DURATION_TICKS = 10;
    private static final int WORLDZERO_RETURN_TO_BED_FOOTSTEP_SPAN_TICKS =
            WORLDZERO_RETURN_TO_BED_FOOTSTEP_INTERVAL_TICKS * (WORLDZERO_RETURN_TO_BED_FOOTSTEP_COUNT - 1);
    private static final int WORLDZERO_RETURN_TO_BED_DOOR_APPROACH_START_TICKS = WORLDZERO_RETURN_TO_BED_KNOCK_TICKS
            + WORLDZERO_RETURN_TO_BED_DOOR_DELAY_TICKS;
    private static final int WORLDZERO_RETURN_TO_BED_OUTSIDE_STEP_DISTANCE = 4;
    private static final ResourceLocation WORLDZERO_CREAK_SOUND_ID = new ResourceLocation(
            WorldZeroMod.MOD_ID,
            "creak"
    );
    private static final ResourceLocation WORLDZERO_BREATH_SOUND_ID = new ResourceLocation(
            WorldZeroMod.MOD_ID,
            "breath"
    );
    private static final ResourceLocation WORLDZERO_WARNING_SOUND_ID = new ResourceLocation(
            WorldZeroMod.MOD_ID,
            "warning"
    );
    private static final ResourceLocation WORLDZERO_KNOCK_SOUND_ID = new ResourceLocation(
            WorldZeroMod.MOD_ID,
            "knock"
    );
    private static final ResourceLocation WORLDZERO_WOODEN_DOOR_OPEN_SOUND_ID = new ResourceLocation(
            "minecraft",
            "block.wooden_door.open"
    );
    private static final ResourceLocation WORLDZERO_IRON_DOOR_OPEN_SOUND_ID = new ResourceLocation(
            "minecraft",
            "block.iron_door.open"
    );
    private static final ResourceLocation WORLDZERO_WOODEN_DOOR_CLOSE_SOUND_ID = new ResourceLocation(
            "minecraft",
            "block.wooden_door.close"
    );
    private static final ResourceLocation WORLDZERO_IRON_DOOR_CLOSE_SOUND_ID = new ResourceLocation(
            "minecraft",
            "block.iron_door.close"
    );
    private static final ResourceLocation WORLDZERO_CHEST_OPEN_SOUND_ID = new ResourceLocation(
            "minecraft",
            "block.chest.open"
    );
    private static final ResourceLocation WORLDZERO_CHEST_CLOSE_SOUND_ID = new ResourceLocation(
            "minecraft",
            "block.chest.close"
    );
    private static final ResourceLocation WORLDZERO_ENDER_CHEST_OPEN_SOUND_ID = new ResourceLocation(
            "minecraft",
            "block.ender_chest.open"
    );
    private static final ResourceLocation WORLDZERO_ENDER_CHEST_CLOSE_SOUND_ID = new ResourceLocation(
            "minecraft",
            "block.ender_chest.close"
    );
    private static final ResourceLocation WORLDZERO_GLASS_BREAK_SOUND_ID = new ResourceLocation(
            "minecraft",
            "block.glass.break"
    );
    private static final ResourceLocation WORLDZERO_STONE_STEP_SOUND_ID = new ResourceLocation(
            "minecraft",
            "block.stone.step"
    );
    private static boolean worldzero$movementBlocked;
    private static boolean worldzero$bedViewActive;
    private static boolean worldzero$renderDistanceLocked;
    private static boolean worldzero$soundIsolationActive;
    private static int worldzero$previousRenderDistance = -1;
    private static BlockPos worldzero$bedTargetPos;
    private static int worldzero$echoEntityId = -1;
    private static boolean worldzero$cameraAlignedReported;
    private static WorldZeroParalysisBreathSound worldzero$activeBreathSound;
    private static SimpleSoundInstance worldzero$activeWarningSound;
    private static int worldzero$warningTicksRemaining;
    private static int worldzero$busyBedMessageCooldownTicks;
    private static boolean worldzero$returnBedSequenceActive;
    private static int worldzero$returnBedSequenceTicks;
    private static boolean worldzero$returnBedKnockPlayed;
    private static boolean worldzero$returnBedIntrusionPlayed;
    private static BlockPos worldzero$returnBedPos;
    private static final Set<ResourceLocation> WORLDZERO_ALLOWED_SEQUENCE_SOUND_IDS = new HashSet<>();

    private WorldZeroParalysisClientController() {
    }

    public static void handlePacket(WorldZeroParalysisClientPacket packet) {
        switch (packet.worldzero$action()) {
            case WorldZeroParalysisClientPacket.WORLDZERO_ACTION_START_BED_VIEW -> worldzero$startBedView(
                    packet.worldzero$targetPos(),
                    packet.worldzero$entityId()
            );
            case WorldZeroParalysisClientPacket.WORLDZERO_ACTION_PLAY_CREAK -> worldzero$playCreak();
            case WorldZeroParalysisClientPacket.WORLDZERO_ACTION_ECHO_GONE -> worldzero$onEchoGone(
                    packet.worldzero$durationTicks()
            );
            case WorldZeroParalysisClientPacket.WORLDZERO_ACTION_START_WARNING -> worldzero$startWarning(
                    packet.worldzero$durationTicks()
            );
            case WorldZeroParalysisClientPacket.WORLDZERO_ACTION_RETURN_TO_BED -> worldzero$returnToBed(
                    packet.worldzero$targetPos()
            );
            case WorldZeroParalysisClientPacket.WORLDZERO_ACTION_START_FADE -> worldzero$startFinalFade(
                    packet.worldzero$durationTicks()
            );
            case WorldZeroParalysisClientPacket.WORLDZERO_ACTION_CLEAR -> worldzero$clearState();
            default -> {
            }
        }
    }

    public static boolean worldzero$isKeyboardBlocked() {
        return worldzero$movementBlocked;
    }

    public static boolean worldzero$isMouseLookBlocked() {
        return false;
    }

    public static boolean worldzero$isMousePressBlocked() {
        return worldzero$movementBlocked && !worldzero$shouldKeepMouseFree(Minecraft.getInstance());
    }

    public static boolean worldzero$isMouseScrollBlocked() {
        return worldzero$movementBlocked && !worldzero$shouldKeepMouseFree(Minecraft.getInstance());
    }

    public static boolean worldzero$isPauseBlocked() {
        return worldzero$movementBlocked;
    }

    public static boolean worldzero$isRenderDistanceForced() {
        return worldzero$renderDistanceLocked;
    }

    public static int worldzero$getForcedEffectiveRenderDistance() {
        return WORLDZERO_EFFECTIVE_RENDER_DISTANCE;
    }

    public static boolean worldzero$isSoundIsolationActive() {
        return worldzero$soundIsolationActive;
    }

    public static boolean worldzero$isAllowedParalysisSound(SoundInstance soundInstance) {
        if (soundInstance == null) {
            return false;
        }

        ResourceLocation soundId = soundInstance.getLocation();
        return WORLDZERO_CREAK_SOUND_ID.equals(soundId)
                || WORLDZERO_BREATH_SOUND_ID.equals(soundId)
                || WORLDZERO_WARNING_SOUND_ID.equals(soundId)
                || WORLDZERO_ALLOWED_SEQUENCE_SOUND_IDS.contains(soundId);
    }

    public static boolean worldzero$isBedExitBlocked() {
        Minecraft minecraft = Minecraft.getInstance();
        return worldzero$returnBedSequenceActive
                && minecraft != null
                && minecraft.player != null
                && minecraft.player.isSleeping();
    }

    @SubscribeEvent
    public static void worldzero$onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!worldzero$movementBlocked
                && worldzero$warningTicksRemaining <= 0
                && !worldzero$renderDistanceLocked
                && !worldzero$returnBedSequenceActive) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            worldzero$clearState();
            return;
        }

        if (worldzero$renderDistanceLocked) {
            worldzero$enforceRenderDistanceLock(minecraft);
        }

        if (worldzero$returnBedSequenceActive) {
            worldzero$tickReturnBedSequence(minecraft);
        }

        if (worldzero$movementBlocked) {
            if (minecraft.screen != null && !worldzero$isAllowedParalysisScreen(minecraft, minecraft.screen)) {
                minecraft.setScreen(null);
            }
            if (!worldzero$shouldKeepMouseFree(minecraft) && !minecraft.mouseHandler.isMouseGrabbed()) {
                minecraft.mouseHandler.grabMouse();
            }
            worldzero$releaseMovementKeys(minecraft.options);
        }

        if (worldzero$bedViewActive && worldzero$bedTargetPos != null && !WorldZeroFreezeClientController.isFreezeActive()) {
            if (!worldzero$cameraAlignedReported && worldzero$isCameraAligned(minecraft.player, worldzero$bedTargetPos)) {
                worldzero$cameraAlignedReported = true;
                WorldZeroNetwork.sendParalysisCameraAligned();
            }
        }

        if (worldzero$warningTicksRemaining > 0) {
            worldzero$warningTicksRemaining--;
            if (worldzero$warningTicksRemaining <= 0) {
                worldzero$stopWarningSound();
            }
        }

        if (worldzero$busyBedMessageCooldownTicks > 0) {
            worldzero$busyBedMessageCooldownTicks--;
        }
    }

    @SubscribeEvent
    public static void worldzero$onScreenOpening(ScreenEvent.Opening event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (worldzero$isPauseBlocked()
                && event.getNewScreen() != null
                && !worldzero$isAllowedParalysisScreen(minecraft, event.getNewScreen())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        worldzero$clearState();
    }

    private static void worldzero$startBedView(BlockPos bedPos, int echoEntityId) {
        worldzero$activateParalysisPresentation();
        worldzero$movementBlocked = true;
        worldzero$bedViewActive = true;
        worldzero$bedTargetPos = bedPos.immutable();
        worldzero$echoEntityId = echoEntityId;
        worldzero$cameraAlignedReported = false;
        worldzero$startBreathLoop();
    }

    private static void worldzero$playCreak() {
        worldzero$activateParalysisPresentation();
        worldzero$playOneShotSound(WORLDZERO_CREAK_SOUND_ID, SoundSource.PLAYERS, 0.85F, 0.96F);
    }

    private static void worldzero$onEchoGone(int lingerTicks) {
        worldzero$echoEntityId = -1;
        if (worldzero$activeBreathSound != null) {
            worldzero$activeBreathSound.worldzero$beginLingering(Math.max(0, lingerTicks));
        }
    }

    private static void worldzero$startWarning(int durationTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundManager() == null) {
            return;
        }

        worldzero$activateParalysisPresentation();
        worldzero$stopWarningSound();
        BlockPos soundPos = minecraft.player != null ? minecraft.player.blockPosition() : BlockPos.ZERO;
        worldzero$activeWarningSound = new SimpleSoundInstance(
                SoundEvent.createVariableRangeEvent(WORLDZERO_WARNING_SOUND_ID),
                SoundSource.PLAYERS,
                1.0F,
                1.0F,
                RandomSource.create(),
                soundPos
        );
        minecraft.getSoundManager().play(worldzero$activeWarningSound);
        worldzero$warningTicksRemaining = Math.max(1, durationTicks);
    }

    private static void worldzero$startFinalFade(int durationTicks) {
        worldzero$bedViewActive = false;
        worldzero$restoreRenderDistance();
        worldzero$stopWarningSound();
        if (worldzero$activeBreathSound != null) {
            worldzero$activeBreathSound.worldzero$stopNow();
            worldzero$activeBreathSound = null;
        }
        WorldZeroSleepFadeOverlay.worldzero$startForcedFade(durationTicks);
    }

    private static void worldzero$returnToBed(BlockPos bedPos) {
        worldzero$activateParalysisPresentation();
        worldzero$bedViewActive = false;
        worldzero$bedTargetPos = null;
        worldzero$echoEntityId = -1;
        worldzero$cameraAlignedReported = false;
        worldzero$restoreRenderDistance();
        worldzero$stopWarningSound();
        if (worldzero$activeBreathSound != null) {
            worldzero$activeBreathSound.worldzero$stopNow();
            worldzero$activeBreathSound = null;
        }
        worldzero$returnBedSequenceActive = true;
        worldzero$returnBedSequenceTicks = 0;
        worldzero$returnBedKnockPlayed = false;
        worldzero$returnBedIntrusionPlayed = false;
        worldzero$returnBedPos = bedPos != null ? bedPos.immutable() : null;
        WORLDZERO_ALLOWED_SEQUENCE_SOUND_IDS.clear();
    }

    private static boolean worldzero$isCameraAligned(LocalPlayer player, BlockPos bedPos) {
        Vec3 target = worldzero$getCurrentCameraTarget(bedPos);
        double deltaX = target.x - player.getX();
        double deltaY = target.y - player.getEyeY();
        double deltaZ = target.z - player.getZ();
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float targetYaw = (float) (Mth.atan2(deltaZ, deltaX) * (180.0D / Math.PI)) - 90.0F;
        float targetPitch = (float) (-(Mth.atan2(deltaY, horizontalDistance) * (180.0D / Math.PI)));
        float yawDelta = Math.abs(Mth.wrapDegrees(targetYaw - player.getYRot()));
        float pitchDelta = Math.abs(targetPitch - player.getXRot());
        return yawDelta <= 2.5F && pitchDelta <= 2.0F;
    }

    private static Vec3 worldzero$getCurrentCameraTarget(BlockPos bedPos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.level != null && worldzero$echoEntityId >= 0) {
            Entity echo = minecraft.level.getEntity(worldzero$echoEntityId);
            if (echo != null) {
                return new Vec3(echo.getX(), echo.getEyeY() + 0.15D, echo.getZ());
            }
        }

        return new Vec3(bedPos.getX() + 0.5D, bedPos.getY() + 0.95D, bedPos.getZ() + 0.5D);
    }

    private static void worldzero$startBreathLoop() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundManager() == null) {
            return;
        }

        if (worldzero$activeBreathSound != null) {
            worldzero$activeBreathSound.worldzero$stopNow();
        }
        worldzero$activeBreathSound = new WorldZeroParalysisBreathSound();
        minecraft.getSoundManager().play(worldzero$activeBreathSound);
    }

    private static void worldzero$playOneShotSound(ResourceLocation soundId, SoundSource source, float volume, float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundManager() == null) {
            return;
        }

        BlockPos soundPos = minecraft.player != null ? minecraft.player.blockPosition() : BlockPos.ZERO;
        worldzero$allowSequenceSound(soundId);
        SimpleSoundInstance soundInstance = new SimpleSoundInstance(
                SoundEvent.createVariableRangeEvent(soundId),
                source,
                volume,
                pitch,
                RandomSource.create(),
                soundPos
        );
        minecraft.getSoundManager().play(soundInstance);
    }

    private static void worldzero$playOneShotSound(
            ResourceLocation soundId,
            SoundSource source,
            float volume,
            float pitch,
            BlockPos soundPos
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundManager() == null) {
            return;
        }

        worldzero$allowSequenceSound(soundId);
        SimpleSoundInstance soundInstance = new SimpleSoundInstance(
                SoundEvent.createVariableRangeEvent(soundId),
                source,
                volume,
                pitch,
                RandomSource.create(),
                soundPos
        );
        minecraft.getSoundManager().play(soundInstance);
    }

    private static void worldzero$playDelayedOneShotSound(
            ResourceLocation soundId,
            SoundSource source,
            float volume,
            float pitch,
            BlockPos soundPos,
            int delayTicks
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundManager() == null) {
            return;
        }

        worldzero$allowSequenceSound(soundId);
        SimpleSoundInstance soundInstance = new SimpleSoundInstance(
                SoundEvent.createVariableRangeEvent(soundId),
                source,
                volume,
                pitch,
                RandomSource.create(),
                soundPos
        );
        minecraft.getSoundManager().playDelayed(soundInstance, Math.max(0, delayTicks));
    }

    public static void worldzero$handleMousePress(int button, int action) {
        if (!worldzero$movementBlocked || button != 1 || action != 1 || worldzero$busyBedMessageCooldownTicks > 0) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null || minecraft.hitResult == null) {
            return;
        }

        if (!worldzero$isBedUseAttempt(minecraft.hitResult, minecraft)) {
            return;
        }

        worldzero$busyBedMessageCooldownTicks = WORLDZERO_BUSY_BED_MESSAGE_COOLDOWN_TICKS;
        minecraft.player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.worldzero.paralysis.bed_occupied"),
                true
        );
    }

    private static boolean worldzero$isBedUseAttempt(HitResult hitResult, Minecraft minecraft) {
        if (hitResult instanceof BlockHitResult blockHitResult) {
            return minecraft.level.getBlockState(blockHitResult.getBlockPos()).is(BlockTags.BEDS);
        }

        if (hitResult instanceof EntityHitResult entityHitResult) {
            Entity entity = entityHitResult.getEntity();
            return entity != null && entity.getId() == worldzero$echoEntityId;
        }

        return false;
    }

    private static void worldzero$stopWarningSound() {
        if (worldzero$activeWarningSound == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().stop(worldzero$activeWarningSound);
        }
        worldzero$activeWarningSound = null;
        worldzero$warningTicksRemaining = 0;
    }

    private static void worldzero$releaseMovementKeys(Options options) {
        options.keyUp.setDown(false);
        options.keyDown.setDown(false);
        options.keyLeft.setDown(false);
        options.keyRight.setDown(false);
        options.keyJump.setDown(false);
        options.keyShift.setDown(false);
        options.keySprint.setDown(false);
    }

    private static void worldzero$tickReturnBedSequence(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return;
        }

        worldzero$returnBedSequenceTicks++;
        if (!worldzero$returnBedKnockPlayed && worldzero$returnBedSequenceTicks >= WORLDZERO_RETURN_TO_BED_KNOCK_TICKS) {
            worldzero$returnBedKnockPlayed = true;
            BlockPos doorPos = worldzero$findNearestDoorPos(minecraft, worldzero$returnBedPos);
            worldzero$playOneShotSound(
                    WORLDZERO_KNOCK_SOUND_ID,
                    SoundSource.PLAYERS,
                    0.20F,
                    1.0F,
                    doorPos != null ? doorPos : worldzero$getSequenceCenter(minecraft)
            );
        }

        if (!worldzero$returnBedIntrusionPlayed
                && worldzero$returnBedSequenceTicks >= WORLDZERO_RETURN_TO_BED_DOOR_APPROACH_START_TICKS) {
            worldzero$returnBedIntrusionPlayed = true;
            worldzero$playReturnBedIntrusion(minecraft);
        }
    }

    private static void worldzero$playReturnBedIntrusion(Minecraft minecraft) {
        BlockPos doorPos = worldzero$findNearestDoorPos(minecraft, worldzero$returnBedPos);
        BlockPos bedPos = worldzero$returnBedPos != null ? worldzero$returnBedPos : worldzero$getSequenceCenter(minecraft);
        BlockPos chestPos = worldzero$findNearestChestPos(minecraft, bedPos);
        BlockPos glassPos = worldzero$findNearestGlassPos(minecraft, bedPos);
        BlockPos outsideDoorPos = worldzero$getReturnBedOutsideAnchor(minecraft, doorPos, bedPos);
        BlockPos targetPos = glassPos != null ? glassPos : bedPos;
        ResourceLocation doorOpenSoundId = worldzero$getDoorSoundId(minecraft, doorPos, true);
        ResourceLocation doorCloseSoundId = worldzero$getDoorSoundId(minecraft, doorPos, false);
        ResourceLocation chestOpenSoundId = worldzero$getChestSoundId(minecraft, chestPos, true);
        ResourceLocation chestCloseSoundId = worldzero$getChestSoundId(minecraft, chestPos, false);
        ResourceLocation glassSoundId = worldzero$getGlassBreakSoundId(minecraft, glassPos);
        int currentDelay = 0;
        BlockPos currentPos = doorPos != null ? doorPos : bedPos;

        worldzero$playDelayedFootsteps(minecraft, outsideDoorPos, currentPos, currentDelay);
        currentDelay += WORLDZERO_RETURN_TO_BED_FOOTSTEP_SPAN_TICKS;

        worldzero$playDelayedOneShotSound(
                doorOpenSoundId,
                SoundSource.PLAYERS,
                0.65F,
                1.0F,
                currentPos,
                currentDelay
        );

        if (chestPos != null) {
            worldzero$playDelayedFootsteps(minecraft, currentPos, chestPos, currentDelay);
            currentDelay += WORLDZERO_RETURN_TO_BED_FOOTSTEP_SPAN_TICKS;

            worldzero$playDelayedOneShotSound(
                    chestOpenSoundId,
                    SoundSource.PLAYERS,
                    0.70F,
                    1.0F,
                    chestPos,
                    currentDelay
            );
            currentDelay += WORLDZERO_RETURN_TO_BED_CHEST_CLOSE_DELAY_TICKS;
            worldzero$playDelayedOneShotSound(
                    chestCloseSoundId,
                    SoundSource.PLAYERS,
                    0.70F,
                    1.0F,
                    chestPos,
                    currentDelay
            );
            currentPos = chestPos;
        }

        worldzero$playDelayedFootsteps(minecraft, currentPos, targetPos, currentDelay);
        currentDelay += WORLDZERO_RETURN_TO_BED_FOOTSTEP_SPAN_TICKS
                + WORLDZERO_RETURN_TO_BED_GLASS_BREAK_DELAY_TICKS
                + WORLDZERO_RETURN_TO_BED_GLASS_BREAK_DURATION_TICKS;

        worldzero$playDelayedOneShotSound(
                glassSoundId,
                SoundSource.PLAYERS,
                0.75F,
                0.98F + minecraft.level.random.nextFloat() * 0.08F,
                targetPos,
                currentDelay
        );

        worldzero$playDelayedFootsteps(minecraft, targetPos, outsideDoorPos, currentDelay);
        currentDelay += WORLDZERO_RETURN_TO_BED_FOOTSTEP_SPAN_TICKS;

        worldzero$playDelayedOneShotSound(
                doorCloseSoundId,
                SoundSource.PLAYERS,
                0.65F,
                1.0F,
                doorPos != null ? doorPos : bedPos,
                currentDelay
        );
    }

    private static boolean worldzero$isAllowedParalysisScreen(@Nullable Minecraft minecraft, @Nullable Screen screen) {
        return screen instanceof InBedChatScreen && worldzero$shouldKeepMouseFree(minecraft);
    }

    private static boolean worldzero$shouldKeepMouseFree(@Nullable Minecraft minecraft) {
        return minecraft != null
                && worldzero$returnBedSequenceActive
                && minecraft.player != null
                && minecraft.player.isSleeping();
    }

    private static void worldzero$allowSequenceSound(@Nullable ResourceLocation soundId) {
        if (soundId != null) {
            WORLDZERO_ALLOWED_SEQUENCE_SOUND_IDS.add(soundId);
        }
    }

    private static BlockPos worldzero$getSequenceCenter(Minecraft minecraft) {
        if (worldzero$returnBedPos != null) {
            return worldzero$returnBedPos;
        }

        return minecraft.player != null ? minecraft.player.blockPosition() : BlockPos.ZERO;
    }

    @Nullable
    private static BlockPos worldzero$findNearestDoorPos(Minecraft minecraft, @Nullable BlockPos center) {
        if (minecraft.level == null || center == null) {
            return null;
        }

        BlockPos bestPos = null;
        double bestDistanceSqr = Double.MAX_VALUE;
        for (int x = center.getX() - 10; x <= center.getX() + 10; x++) {
            for (int y = center.getY() - 4; y <= center.getY() + 4; y++) {
                for (int z = center.getZ() - 10; z <= center.getZ() + 10; z++) {
                    BlockPos candidatePos = new BlockPos(x, y, z);
                    BlockState candidateState = minecraft.level.getBlockState(candidatePos);
                    if (!candidateState.is(BlockTags.DOORS) || !candidateState.hasProperty(DoorBlock.HALF)) {
                        continue;
                    }

                    BlockPos lowerPos = candidateState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                            ? candidatePos
                            : candidatePos.below();
                    double distanceSqr = center.distSqr(lowerPos);
                    if (distanceSqr < bestDistanceSqr) {
                        bestDistanceSqr = distanceSqr;
                        bestPos = lowerPos.immutable();
                    }
                }
            }
        }

        return bestPos;
    }

    @Nullable
    private static BlockPos worldzero$findNearestChestPos(Minecraft minecraft, @Nullable BlockPos center) {
        if (minecraft.level == null || center == null) {
            return null;
        }

        BlockPos bestPos = null;
        double bestDistanceSqr = Double.MAX_VALUE;
        for (int x = center.getX() - 10; x <= center.getX() + 10; x++) {
            for (int y = center.getY() - 4; y <= center.getY() + 4; y++) {
                for (int z = center.getZ() - 10; z <= center.getZ() + 10; z++) {
                    BlockPos candidatePos = new BlockPos(x, y, z);
                    BlockState candidateState = minecraft.level.getBlockState(candidatePos);
                    if (!worldzero$isChestLikeBlock(candidateState)) {
                        continue;
                    }

                    double distanceSqr = center.distSqr(candidatePos);
                    if (distanceSqr < bestDistanceSqr) {
                        bestDistanceSqr = distanceSqr;
                        bestPos = candidatePos.immutable();
                    }
                }
            }
        }

        return bestPos;
    }

    @Nullable
    private static BlockPos worldzero$findNearestGlassPos(Minecraft minecraft, @Nullable BlockPos center) {
        if (minecraft.level == null || center == null) {
            return null;
        }

        BlockPos bestPos = null;
        double bestDistanceSqr = Double.MAX_VALUE;
        for (int x = center.getX() - 10; x <= center.getX() + 10; x++) {
            for (int y = center.getY() - 4; y <= center.getY() + 4; y++) {
                for (int z = center.getZ() - 10; z <= center.getZ() + 10; z++) {
                    BlockPos candidatePos = new BlockPos(x, y, z);
                    BlockState candidateState = minecraft.level.getBlockState(candidatePos);
                    if (!worldzero$isGlassLikeBlock(candidateState)) {
                        continue;
                    }

                    double distanceSqr = center.distSqr(candidatePos);
                    if (distanceSqr < bestDistanceSqr) {
                        bestDistanceSqr = distanceSqr;
                        bestPos = candidatePos.immutable();
                    }
                }
            }
        }

        return bestPos;
    }

    private static ResourceLocation worldzero$getDoorSoundId(Minecraft minecraft, @Nullable BlockPos doorPos, boolean open) {
        if (minecraft.level == null || doorPos == null) {
            return open ? WORLDZERO_WOODEN_DOOR_OPEN_SOUND_ID : WORLDZERO_WOODEN_DOOR_CLOSE_SOUND_ID;
        }

        BlockState doorState = minecraft.level.getBlockState(doorPos);
        if (!doorState.is(BlockTags.DOORS)) {
            return open ? WORLDZERO_WOODEN_DOOR_OPEN_SOUND_ID : WORLDZERO_WOODEN_DOOR_CLOSE_SOUND_ID;
        }

        if (doorState.is(Blocks.IRON_DOOR)) {
            return open ? WORLDZERO_IRON_DOOR_OPEN_SOUND_ID : WORLDZERO_IRON_DOOR_CLOSE_SOUND_ID;
        }

        return open ? WORLDZERO_WOODEN_DOOR_OPEN_SOUND_ID : WORLDZERO_WOODEN_DOOR_CLOSE_SOUND_ID;
    }

    private static ResourceLocation worldzero$getChestSoundId(Minecraft minecraft, @Nullable BlockPos chestPos, boolean open) {
        if (minecraft.level == null || chestPos == null) {
            return open ? WORLDZERO_CHEST_OPEN_SOUND_ID : WORLDZERO_CHEST_CLOSE_SOUND_ID;
        }

        BlockState chestState = minecraft.level.getBlockState(chestPos);
        if (chestState.is(Blocks.ENDER_CHEST)) {
            return open ? WORLDZERO_ENDER_CHEST_OPEN_SOUND_ID : WORLDZERO_ENDER_CHEST_CLOSE_SOUND_ID;
        }

        return open ? WORLDZERO_CHEST_OPEN_SOUND_ID : WORLDZERO_CHEST_CLOSE_SOUND_ID;
    }

    private static ResourceLocation worldzero$getReturnBedStepSoundId(Minecraft minecraft, @Nullable BlockPos stepPos) {
        if (minecraft.level == null || stepPos == null) {
            return WORLDZERO_STONE_STEP_SOUND_ID;
        }

        BlockPos floorPos = stepPos.below();
        for (int depth = 0; depth < 3; depth++) {
            BlockPos candidatePos = stepPos.below(1 + depth);
            BlockState candidateState = minecraft.level.getBlockState(candidatePos);
            if (!candidateState.getCollisionShape(minecraft.level, candidatePos).isEmpty()) {
                floorPos = candidatePos;
                break;
            }
        }

        BlockState floorState = minecraft.level.getBlockState(floorPos);
        SoundType soundType = floorState.getSoundType(minecraft.level, floorPos, minecraft.player);
        return worldzero$getSoundId(soundType.getStepSound(), WORLDZERO_STONE_STEP_SOUND_ID);
    }

    private static ResourceLocation worldzero$getGlassBreakSoundId(Minecraft minecraft, @Nullable BlockPos glassPos) {
        if (minecraft.level == null || glassPos == null) {
            return WORLDZERO_GLASS_BREAK_SOUND_ID;
        }

        BlockState glassState = minecraft.level.getBlockState(glassPos);
        SoundType soundType = glassState.getSoundType(minecraft.level, glassPos, minecraft.player);
        return worldzero$getSoundId(soundType.getBreakSound(), WORLDZERO_GLASS_BREAK_SOUND_ID);
    }

    private static ResourceLocation worldzero$getSoundId(@Nullable SoundEvent soundEvent, ResourceLocation fallback) {
        if (soundEvent == null) {
            return fallback;
        }

        ResourceLocation soundId = BuiltInRegistries.SOUND_EVENT.getKey(soundEvent);
        return soundId != null ? soundId : fallback;
    }

    private static BlockPos worldzero$getIntrusionStepPos(
            @Nullable BlockPos startPos,
            BlockPos endPos,
            int stepIndex,
            int totalSteps
    ) {
        if (startPos == null) {
            return endPos;
        }

        double progress = (double) stepIndex / (double) (totalSteps + 1);
        double x = Mth.lerp(progress, startPos.getX() + 0.5D, endPos.getX() + 0.5D);
        double y = Mth.lerp(progress, startPos.getY(), endPos.getY());
        double z = Mth.lerp(progress, startPos.getZ() + 0.5D, endPos.getZ() + 0.5D);
        return BlockPos.containing(x, y, z);
    }

    private static void worldzero$playDelayedFootsteps(
            Minecraft minecraft,
            @Nullable BlockPos startPos,
            BlockPos endPos,
            int startDelay
    ) {
        for (int stepIndex = 0; stepIndex < WORLDZERO_RETURN_TO_BED_FOOTSTEP_COUNT; stepIndex++) {
            int delay = startDelay + WORLDZERO_RETURN_TO_BED_FOOTSTEP_INTERVAL_TICKS * stepIndex;
            BlockPos stepPos = worldzero$getIntrusionStepPos(startPos, endPos, stepIndex + 1, WORLDZERO_RETURN_TO_BED_FOOTSTEP_COUNT);
            ResourceLocation stepSoundId = worldzero$getReturnBedStepSoundId(minecraft, stepPos);
            worldzero$playDelayedOneShotSound(
                    stepSoundId,
                    SoundSource.PLAYERS,
                    0.38F,
                    0.94F + minecraft.level.random.nextFloat() * 0.12F,
                    stepPos,
                    delay
            );
        }
    }

    private static BlockPos worldzero$getReturnBedOutsideAnchor(
            Minecraft minecraft,
            @Nullable BlockPos doorPos,
            @Nullable BlockPos bedPos
    ) {
        if (doorPos == null) {
            return bedPos != null ? bedPos : worldzero$getSequenceCenter(minecraft);
        }

        Direction outsideDirection = worldzero$getReturnBedOutsideDirection(doorPos, bedPos);
        BlockPos bestPos = doorPos.immutable();
        for (int distance = 1; distance <= WORLDZERO_RETURN_TO_BED_OUTSIDE_STEP_DISTANCE; distance++) {
            BlockPos candidatePos = doorPos.relative(outsideDirection, distance);
            if (!worldzero$hasReturnBedFootstepSurface(minecraft, candidatePos)) {
                break;
            }
            bestPos = candidatePos.immutable();
        }
        return bestPos;
    }

    private static Direction worldzero$getReturnBedOutsideDirection(BlockPos doorPos, @Nullable BlockPos bedPos) {
        if (bedPos == null) {
            return Direction.SOUTH;
        }

        int deltaX = doorPos.getX() - bedPos.getX();
        int deltaZ = doorPos.getZ() - bedPos.getZ();
        if (Math.abs(deltaX) >= Math.abs(deltaZ)) {
            return deltaX >= 0 ? Direction.EAST : Direction.WEST;
        }

        return deltaZ >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static boolean worldzero$hasReturnBedFootstepSurface(Minecraft minecraft, BlockPos pos) {
        if (minecraft.level == null) {
            return false;
        }

        for (int depth = 1; depth <= 3; depth++) {
            BlockPos candidatePos = pos.below(depth);
            BlockState candidateState = minecraft.level.getBlockState(candidatePos);
            if (!candidateState.getCollisionShape(minecraft.level, candidatePos).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean worldzero$isGlassLikeBlock(BlockState state) {
        if (state.isAir()) {
            return false;
        }

        String blockPath = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return blockPath.contains("glass") || blockPath.contains("pane");
    }

    private static boolean worldzero$isChestLikeBlock(BlockState state) {
        if (state.isAir()) {
            return false;
        }

        String blockPath = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return blockPath.contains("chest") && !blockPath.contains("ender_chest");
    }

    private static void worldzero$activateParalysisPresentation() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null) {
            return;
        }

        if (!worldzero$renderDistanceLocked) {
            worldzero$previousRenderDistance = minecraft.options.renderDistance().get();
        }

        worldzero$renderDistanceLocked = true;
        worldzero$enforceRenderDistanceLock(minecraft);

        if (!worldzero$soundIsolationActive && minecraft.getSoundManager() != null) {
            worldzero$soundIsolationActive = true;
            minecraft.getSoundManager().stop();
        } else {
            worldzero$soundIsolationActive = true;
        }
    }

    private static void worldzero$enforceRenderDistanceLock(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null) {
            return;
        }

        int currentDistance = minecraft.options.renderDistance().get();
        if (currentDistance == WORLDZERO_LOCKED_RENDER_DISTANCE_OPTION) {
            return;
        }

        minecraft.options.renderDistance().set(WORLDZERO_LOCKED_RENDER_DISTANCE_OPTION);
        if (minecraft.levelRenderer != null) {
            minecraft.levelRenderer.allChanged();
        }
    }

    private static void worldzero$restoreRenderDistance() {
        if (!worldzero$renderDistanceLocked) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int restoreDistance = worldzero$previousRenderDistance;
        worldzero$renderDistanceLocked = false;
        worldzero$previousRenderDistance = -1;
        if (minecraft == null || minecraft.options == null || restoreDistance < 0) {
            return;
        }

        if (minecraft.options.renderDistance().get() != restoreDistance) {
            minecraft.options.renderDistance().set(restoreDistance);
            if (minecraft.levelRenderer != null) {
                minecraft.levelRenderer.allChanged();
            }
        }
    }

    private static void worldzero$clearState() {
        worldzero$movementBlocked = false;
        worldzero$bedViewActive = false;
        worldzero$soundIsolationActive = false;
        worldzero$bedTargetPos = null;
        worldzero$echoEntityId = -1;
        worldzero$cameraAlignedReported = false;
        worldzero$warningTicksRemaining = 0;
        worldzero$busyBedMessageCooldownTicks = 0;
        worldzero$returnBedSequenceActive = false;
        worldzero$returnBedSequenceTicks = 0;
        worldzero$returnBedKnockPlayed = false;
        worldzero$returnBedIntrusionPlayed = false;
        worldzero$returnBedPos = null;
        WORLDZERO_ALLOWED_SEQUENCE_SOUND_IDS.clear();
        worldzero$restoreRenderDistance();
        worldzero$stopWarningSound();
        if (worldzero$activeBreathSound != null) {
            worldzero$activeBreathSound.worldzero$stopNow();
            worldzero$activeBreathSound = null;
        }
        WorldZeroSleepFadeOverlay.worldzero$clearForcedFade();
    }

    private static final class WorldZeroParalysisBreathSound extends AbstractTickableSoundInstance {
        private int worldzero$lingerTicks;

        private WorldZeroParalysisBreathSound() {
            super(SoundEvent.createVariableRangeEvent(WORLDZERO_BREATH_SOUND_ID), SoundSource.PLAYERS, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.95F;
            this.pitch = 1.0F;
            this.attenuation = net.minecraft.client.resources.sounds.SoundInstance.Attenuation.LINEAR;
            this.relative = false;
        }

        @Override
        public void tick() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.level == null) {
                this.stop();
                return;
            }

            if (worldzero$echoEntityId >= 0) {
                Entity echo = minecraft.level.getEntity(worldzero$echoEntityId);
                if (echo != null) {
                    this.x = echo.getX();
                    this.y = echo.getEyeY();
                    this.z = echo.getZ();
                    return;
                }
            }

            if (this.worldzero$lingerTicks > 0) {
                this.worldzero$lingerTicks--;
                return;
            }

            this.stop();
        }

        private void worldzero$beginLingering(int ticks) {
            this.worldzero$lingerTicks = Math.max(this.worldzero$lingerTicks, ticks);
        }

        private void worldzero$stopNow() {
            this.stop();
        }
    }
}
