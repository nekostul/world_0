package ru.nekostul.worldzero;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroParalysisClientController {
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
    private static boolean worldzero$inputBlocked;
    private static boolean worldzero$cameraLockActive;
    private static BlockPos worldzero$bedTargetPos;
    private static int worldzero$echoEntityId = -1;
    private static boolean worldzero$cameraAlignedReported;
    private static WorldZeroParalysisBreathSound worldzero$activeBreathSound;
    private static SimpleSoundInstance worldzero$activeWarningSound;
    private static int worldzero$warningTicksRemaining;

    private WorldZeroParalysisClientController() {
    }

    public static void handlePacket(WorldZeroParalysisClientPacket packet) {
        switch (packet.worldzero$action()) {
            case WorldZeroParalysisClientPacket.WORLDZERO_ACTION_START_BED_VIEW -> worldzero$startBedView(
                    packet.worldzero$targetPos(),
                    packet.worldzero$entityId()
            );
            case WorldZeroParalysisClientPacket.WORLDZERO_ACTION_ECHO_GONE -> worldzero$onEchoGone(
                    packet.worldzero$durationTicks()
            );
            case WorldZeroParalysisClientPacket.WORLDZERO_ACTION_START_WARNING -> worldzero$startWarning(
                    packet.worldzero$durationTicks()
            );
            case WorldZeroParalysisClientPacket.WORLDZERO_ACTION_RETURN_TO_BED -> worldzero$returnToBed();
            case WorldZeroParalysisClientPacket.WORLDZERO_ACTION_START_FADE -> worldzero$startFinalFade(
                    packet.worldzero$durationTicks()
            );
            case WorldZeroParalysisClientPacket.WORLDZERO_ACTION_CLEAR -> worldzero$clearState();
            default -> {
            }
        }
    }

    public static boolean worldzero$isInputBlocked() {
        return worldzero$inputBlocked;
    }

    public static boolean worldzero$isPauseBlocked() {
        return worldzero$inputBlocked;
    }

    @SubscribeEvent
    public static void worldzero$onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!worldzero$inputBlocked && worldzero$warningTicksRemaining <= 0) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            worldzero$clearState();
            return;
        }

        if (worldzero$inputBlocked) {
            if (minecraft.screen != null) {
                minecraft.setScreen(null);
            }
            if (!minecraft.mouseHandler.isMouseGrabbed()) {
                minecraft.mouseHandler.grabMouse();
            }
            worldzero$releaseControlKeys(minecraft.options);
        }

        if (worldzero$cameraLockActive && worldzero$bedTargetPos != null && !WorldZeroFreezeClientController.isFreezeActive()) {
            worldzero$rotateCameraTowardBed(minecraft.player, worldzero$bedTargetPos);
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
    }

    @SubscribeEvent
    public static void worldzero$onScreenOpening(ScreenEvent.Opening event) {
        if (worldzero$isPauseBlocked() && event.getNewScreen() != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        worldzero$clearState();
    }

    private static void worldzero$startBedView(BlockPos bedPos, int echoEntityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundManager() == null) {
            return;
        }

        worldzero$inputBlocked = true;
        worldzero$cameraLockActive = true;
        worldzero$bedTargetPos = bedPos.immutable();
        worldzero$echoEntityId = echoEntityId;
        worldzero$cameraAlignedReported = false;
        worldzero$playOneShotSound(WORLDZERO_CREAK_SOUND_ID, SoundSource.PLAYERS, 0.85F, 0.96F);
        worldzero$startBreathLoop();
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
        worldzero$cameraLockActive = false;
        worldzero$stopWarningSound();
        if (worldzero$activeBreathSound != null) {
            worldzero$activeBreathSound.worldzero$stopNow();
            worldzero$activeBreathSound = null;
        }
        WorldZeroSleepFadeOverlay.worldzero$startForcedFade(durationTicks);
    }

    private static void worldzero$returnToBed() {
        worldzero$cameraLockActive = false;
        worldzero$bedTargetPos = null;
        worldzero$echoEntityId = -1;
        worldzero$cameraAlignedReported = false;
        worldzero$stopWarningSound();
        if (worldzero$activeBreathSound != null) {
            worldzero$activeBreathSound.worldzero$stopNow();
            worldzero$activeBreathSound = null;
        }
    }

    private static void worldzero$rotateCameraTowardBed(LocalPlayer player, BlockPos bedPos) {
        Vec3 target = worldzero$getCurrentCameraTarget(bedPos);
        double targetX = target.x;
        double targetY = target.y;
        double targetZ = target.z;
        double deltaX = targetX - player.getX();
        double deltaY = targetY - player.getEyeY();
        double deltaZ = targetZ - player.getZ();
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float targetYaw = (float) (Mth.atan2(deltaZ, deltaX) * (180.0D / Math.PI)) - 90.0F;
        float targetPitch = (float) (-(Mth.atan2(deltaY, horizontalDistance) * (180.0D / Math.PI)));
        float yawDelta = Mth.wrapDegrees(targetYaw - player.getYRot());
        float pitchDelta = targetPitch - player.getXRot();
        float yawStep = Mth.clamp(yawDelta, -1.4F, 1.4F);
        float pitchStep = Mth.clamp(pitchDelta, -0.8F, 0.8F);
        float nextYaw = player.getYRot() + yawStep;
        float nextPitch = player.getXRot() + pitchStep;
        player.setYRot(nextYaw);
        player.setYHeadRot(nextYaw);
        player.setYBodyRot(nextYaw);
        player.setXRot(nextPitch);
        player.yRotO = nextYaw;
        player.xRotO = nextPitch;
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

    private static void worldzero$releaseControlKeys(Options options) {
        options.keyUp.setDown(false);
        options.keyDown.setDown(false);
        options.keyLeft.setDown(false);
        options.keyRight.setDown(false);
        options.keyJump.setDown(false);
        options.keyShift.setDown(false);
        options.keySprint.setDown(false);
        options.keyAttack.setDown(false);
        options.keyUse.setDown(false);
        options.keyPickItem.setDown(false);
        options.keyDrop.setDown(false);
        options.keySwapOffhand.setDown(false);
        options.keyInventory.setDown(false);
        options.keySaveHotbarActivator.setDown(false);
        options.keyLoadHotbarActivator.setDown(false);
        for (int index = 0; index < options.keyHotbarSlots.length; index++) {
            options.keyHotbarSlots[index].setDown(false);
        }
    }

    private static void worldzero$clearState() {
        worldzero$inputBlocked = false;
        worldzero$cameraLockActive = false;
        worldzero$bedTargetPos = null;
        worldzero$echoEntityId = -1;
        worldzero$cameraAlignedReported = false;
        worldzero$warningTicksRemaining = 0;
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
            this.volume = 0.48F;
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
