package ru.nekostul.worldzero;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroSleepFadeOverlay {
    private static final double WORLDZERO_SLEEP_FADE_TICKS = 3.0D * 20.0D;
    private static double worldzero$sleepStartTick = -1.0D;

    private WorldZeroSleepFadeOverlay() {
    }

    @SubscribeEvent
    public static void worldzero$onGuiRender(RenderGuiEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            worldzero$sleepStartTick = -1.0D;
            return;
        }

        if (!minecraft.player.isSleeping()) {
            worldzero$sleepStartTick = -1.0D;
            return;
        }

        double currentTick = minecraft.level.getGameTime() + event.getPartialTick();
        if (worldzero$sleepStartTick < 0.0D) {
            worldzero$sleepStartTick = currentTick;
        }

        double progress = (currentTick - worldzero$sleepStartTick) / WORLDZERO_SLEEP_FADE_TICKS;
        if (progress <= 0.0D) {
            return;
        }

        if (progress > 1.0D) {
            progress = 1.0D;
        }

        int alpha = (int) Math.round(progress * 255.0D);
        if (alpha < 0) {
            alpha = 0;
        } else if (alpha > 255) {
            alpha = 255;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int color = (alpha << 24);
        guiGraphics.fill(0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), color);
    }
}
