package ru.nekostul.worldzero;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroFreezeClientController {
    private static final int WORLDZERO_FREEZE_FALLBACK_TICKS = 30 * 20;
    private static boolean worldzero$freezeActive;
    private static int worldzero$fallbackTicksRemaining;
    private static boolean worldzero$capturedPlayerState;
    private static double worldzero$lockedX;
    private static double worldzero$lockedY;
    private static double worldzero$lockedZ;
    private static float worldzero$lockedYaw;
    private static float worldzero$lockedPitch;
    private static int worldzero$focusEntityId = -1;
    private static float worldzero$forcedYaw = Float.NaN;
    private static float worldzero$forcedPitch = Float.NaN;

    private WorldZeroFreezeClientController() {
    }

    public static void startFreeze(int durationTicks) {
        startFreeze(durationTicks, -1, Float.NaN, Float.NaN);
    }

    public static void startFreeze(int durationTicks, int focusEntityId) {
        startFreeze(durationTicks, focusEntityId, Float.NaN, Float.NaN);
    }

    public static void startFreeze(int durationTicks, int focusEntityId, float forcedYaw, float forcedPitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        worldzero$freezeActive = true;
        worldzero$fallbackTicksRemaining = WORLDZERO_FREEZE_FALLBACK_TICKS;
        worldzero$capturedPlayerState = false;
        worldzero$focusEntityId = focusEntityId;
        worldzero$forcedYaw = forcedYaw;
        worldzero$forcedPitch = forcedPitch;

        if (minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().stop();
        }
    }

    public static boolean isFreezeActive() {
        return worldzero$freezeActive;
    }

    public static void finishFreeze() {
        worldzero$clearState();
    }

    @SubscribeEvent
    public static void worldzero$onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !worldzero$freezeActive) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            worldzero$clearState();
            return;
        }

        LocalPlayer player = minecraft.player;
        if (!worldzero$capturedPlayerState) {
            worldzero$lockedX = player.getX();
            worldzero$lockedY = player.getY();
            worldzero$lockedZ = player.getZ();
            worldzero$lockedYaw = Float.isNaN(worldzero$forcedYaw) ? player.getYRot() : worldzero$forcedYaw;
            worldzero$lockedPitch = Float.isNaN(worldzero$forcedPitch) ? player.getXRot() : worldzero$forcedPitch;
            worldzero$capturedPlayerState = true;
        }

        if (minecraft.screen != null) {
            minecraft.setScreen(null);
        }
        if (!minecraft.mouseHandler.isMouseGrabbed()) {
            minecraft.mouseHandler.grabMouse();
        }

        worldzero$releaseControlKeys(minecraft.options);

        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.setSprinting(false);
        player.setPos(worldzero$lockedX, worldzero$lockedY, worldzero$lockedZ);
        float targetYaw = worldzero$lockedYaw;
        float targetPitch = worldzero$lockedPitch;
        if (worldzero$focusEntityId >= 0) {
            Entity focusEntity = minecraft.level.getEntity(worldzero$focusEntityId);
            if (focusEntity != null) {
                double deltaX = focusEntity.getX() - player.getX();
                double deltaY = focusEntity.getEyeY() - player.getEyeY();
                double deltaZ = focusEntity.getZ() - player.getZ();
                double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                targetYaw = (float) (Mth.atan2(deltaZ, deltaX) * (180.0D / Math.PI)) - 90.0F;
                targetPitch = (float) (-(Mth.atan2(deltaY, horizontalDistance) * (180.0D / Math.PI)));
            }
        }
        player.setYRot(targetYaw);
        player.setYHeadRot(targetYaw);
        player.setYBodyRot(targetYaw);
        player.setXRot(targetPitch);
        player.yRotO = targetYaw;
        player.xRotO = targetPitch;

        worldzero$fallbackTicksRemaining--;
        if (worldzero$fallbackTicksRemaining <= 0) {
            worldzero$clearState();
        }
    }

    @SubscribeEvent
    public static void worldzero$onScreenOpening(ScreenEvent.Opening event) {
        if (isFreezeActive() && event.getNewScreen() != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        worldzero$clearState();
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
        worldzero$freezeActive = false;
        worldzero$fallbackTicksRemaining = 0;
        worldzero$capturedPlayerState = false;
        worldzero$focusEntityId = -1;
        worldzero$forcedYaw = Float.NaN;
        worldzero$forcedPitch = Float.NaN;
    }
}
