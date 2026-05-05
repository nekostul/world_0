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
public final class WorldZeroEndReturnClientController {
    private static int worldzero$blackTicks;

    private WorldZeroEndReturnClientController() {
    }

    public static void worldzero$trigger(int durationTicks) {
        worldzero$blackTicks = Math.max(worldzero$blackTicks, Math.max(1, durationTicks));

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().stop();
        }
    }

    @SubscribeEvent
    public static void worldzero$onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || worldzero$blackTicks <= 0) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().stop();
        }

        worldzero$blackTicks--;
    }

    @SubscribeEvent
    public static void worldzero$onRenderGui(RenderGuiEvent.Pre event) {
        if (worldzero$blackTicks <= 0) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        guiGraphics.fill(0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), 0xFF000000);
    }

    @SubscribeEvent
    public static void worldzero$onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        worldzero$blackTicks = 0;
    }
}
