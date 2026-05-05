package ru.nekostul.worldzero.client.controller;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroHotbarBlackoutClientController {
    private static final int WORLDZERO_BLACKOUT_COLOR = 0xFF000000;
    private static int worldzero$blackoutTicks;

    private WorldZeroHotbarBlackoutClientController() {
    }

    public static void worldzero$trigger(int durationTicks) {
        worldzero$blackoutTicks = Math.max(worldzero$blackoutTicks, Math.max(1, durationTicks));
    }

    @SubscribeEvent
    public static void worldzero$onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (worldzero$blackoutTicks > 0) {
            worldzero$blackoutTicks--;
        }
    }

    @SubscribeEvent
    public static void worldzero$onRenderGui(RenderGuiEvent.Post event) {
        if (worldzero$blackoutTicks <= 0) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        int left = width / 2 - 111;
        int right = width / 2 + 111;
        int top = height - 30;
        int bottom = height - 1;
        guiGraphics.fill(left, top, right, bottom, WORLDZERO_BLACKOUT_COLOR);
    }

    @SubscribeEvent
    public static void worldzero$onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        worldzero$blackoutTicks = 0;
    }
}
