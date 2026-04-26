package ru.nekostul.worldzero;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroHouseBadClientController {
    private static final String WORLDZERO_ALPHA_LABEL = "Minecraft Alpha 1.0.16_02";
    private static final String WORLDZERO_DEV_NO_BARS_PLAYER_NAME = "Dev";
    private static final int WORLDZERO_FORCED_FOV = 70;
    private static int worldzero$previousFov = -1;

    private WorldZeroHouseBadClientController() {
    }

    public static boolean worldzero$shouldBlockEscape(int key, int action) {
        return worldzero$isHouseBadActive()
                && key == GLFW.GLFW_KEY_ESCAPE
                && action != GLFW.GLFW_RELEASE;
    }

    public static boolean worldzero$isPauseBlocked() {
        return worldzero$isHouseBadActive();
    }

    @SubscribeEvent
    public static void worldzero$onScreenOpening(ScreenEvent.Opening event) {
        if (worldzero$isPauseBlocked()
                && event.getNewScreen() != null
                && event.getNewScreen().isPauseScreen()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        if (!worldzero$isHouseBadActive()) {
            worldzero$restoreFov(minecraft);
            return;
        }

        worldzero$enforceFov(minecraft);
        if (minecraft.options != null) {
            minecraft.options.keySprint.setDown(false);
        }
        if (minecraft.player != null) {
            minecraft.player.setSprinting(false);
        }

        boolean closedPauseScreen = false;
        if (minecraft.screen != null && minecraft.screen.isPauseScreen()) {
            minecraft.setScreen(null);
            closedPauseScreen = true;
        }

        if ((closedPauseScreen || minecraft.screen == null) && !minecraft.mouseHandler.isMouseGrabbed()) {
            minecraft.mouseHandler.grabMouse();
        }
    }

    @SubscribeEvent
    public static void worldzero$onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        worldzero$restoreFov(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void worldzero$onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (!worldzero$isHouseBadActive()) {
            return;
        }

        if (event.getOverlay().id().equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                || event.getOverlay().id().equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onRenderGui(RenderGuiEvent.Post event) {
        if (!worldzero$isHouseBadActive()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int guiWidth = guiGraphics.guiWidth();
        int guiHeight = guiGraphics.guiHeight();
        int barWidth = 0;
        if (!worldzero$shouldDisableAspectBars(minecraft)) {
            int visibleWidth = (guiHeight * 4) / 3;
            barWidth = Math.max(0, (guiWidth - visibleWidth) / 2);
        }
        if (barWidth > 0) {
            guiGraphics.fill(0, 0, barWidth, guiHeight, 0xFF000000);
            guiGraphics.fill(guiWidth - barWidth, 0, guiWidth, guiHeight, 0xFF000000);
        }

        Font font = minecraft.font;
        guiGraphics.drawString(font, WORLDZERO_ALPHA_LABEL, barWidth + 2, 2, 0xFFFFFFFF, true);
    }

    private static boolean worldzero$isHouseBadActive() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null
                && minecraft.player != null
                && minecraft.level != null
                && minecraft.level.dimension() == WorldZeroHouseBadDimension.WORLDZERO_HOUSE_BAD_LEVEL;
    }

    private static boolean worldzero$shouldDisableAspectBars(Minecraft minecraft) {
        return minecraft != null
                && minecraft.player != null
                && WORLDZERO_DEV_NO_BARS_PLAYER_NAME.equals(minecraft.player.getGameProfile().getName());
    }

    private static void worldzero$enforceFov(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null) {
            return;
        }

        int currentFov = minecraft.options.fov().get();
        if (worldzero$previousFov < 0) {
            worldzero$previousFov = currentFov;
        }

        if (currentFov != WORLDZERO_FORCED_FOV) {
            minecraft.options.fov().set(WORLDZERO_FORCED_FOV);
        }
    }

    private static void worldzero$restoreFov(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null || worldzero$previousFov < 0) {
            return;
        }

        if (minecraft.options.fov().get() != worldzero$previousFov) {
            minecraft.options.fov().set(worldzero$previousFov);
        }
        worldzero$previousFov = -1;
    }
}
