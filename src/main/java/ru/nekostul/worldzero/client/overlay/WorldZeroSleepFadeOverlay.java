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
    private static double worldzero$forcedFadeStartTick = -1.0D;
    private static double worldzero$forcedFadeDurationTicks = 0.0D;
    private static boolean worldzero$forcedFadeActive;

    private WorldZeroSleepFadeOverlay() {
    }

    public static void worldzero$startForcedFade(int durationTicks) {
        worldzero$forcedFadeActive = true;
        worldzero$forcedFadeStartTick = -1.0D;
        worldzero$forcedFadeDurationTicks = Math.max(1.0D, durationTicks);
    }

    public static void worldzero$clearForcedFade() {
        worldzero$forcedFadeActive = false;
        worldzero$forcedFadeStartTick = -1.0D;
        worldzero$forcedFadeDurationTicks = 0.0D;
    }

    @SubscribeEvent
    public static void worldzero$onGuiRender(RenderGuiEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            worldzero$sleepStartTick = -1.0D;
            worldzero$clearForcedFade();
            return;
        }

        boolean playerSleeping = minecraft.player.isSleeping();
        if (!playerSleeping) {
            worldzero$sleepStartTick = -1.0D;
        }

        double currentTick = minecraft.level.getGameTime() + event.getPartialTick();
        if (playerSleeping && worldzero$sleepStartTick < 0.0D) {
            worldzero$sleepStartTick = currentTick;
        }

        double progress = 0.0D;
        if (playerSleeping && worldzero$sleepStartTick >= 0.0D) {
            progress = Math.max(progress, (currentTick - worldzero$sleepStartTick) / WORLDZERO_SLEEP_FADE_TICKS);
        }
        if (worldzero$forcedFadeActive) {
            if (worldzero$forcedFadeStartTick < 0.0D) {
                worldzero$forcedFadeStartTick = currentTick;
            }
            progress = Math.max(
                    progress,
                    (currentTick - worldzero$forcedFadeStartTick) / worldzero$forcedFadeDurationTicks
            );
        }
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
