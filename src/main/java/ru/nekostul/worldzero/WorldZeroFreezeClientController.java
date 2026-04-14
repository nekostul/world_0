package ru.nekostul.worldzero;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
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

    private WorldZeroFreezeClientController() {
    }

    public static void startFreeze(int durationTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        worldzero$freezeActive = true;
        worldzero$fallbackTicksRemaining = WORLDZERO_FREEZE_FALLBACK_TICKS;
        worldzero$capturedPlayerState = false;

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
            worldzero$lockedYaw = player.getYRot();
            worldzero$lockedPitch = player.getXRot();
            worldzero$capturedPlayerState = true;
        }

        if (minecraft.screen != null) {
            minecraft.setScreen(null);
        }

        worldzero$releaseControlKeys(minecraft.options);

        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.setSprinting(false);
        player.setPos(worldzero$lockedX, worldzero$lockedY, worldzero$lockedZ);
        player.setYRot(worldzero$lockedYaw);
        player.setYHeadRot(worldzero$lockedYaw);
        player.setYBodyRot(worldzero$lockedYaw);
        player.setXRot(worldzero$lockedPitch);
        player.yRotO = worldzero$lockedYaw;
        player.xRotO = worldzero$lockedPitch;

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
    }

    private static void worldzero$clearState() {
        worldzero$freezeActive = false;
        worldzero$fallbackTicksRemaining = 0;
        worldzero$capturedPlayerState = false;
    }
}
