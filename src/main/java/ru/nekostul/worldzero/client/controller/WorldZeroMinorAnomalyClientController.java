package ru.nekostul.worldzero.client.controller;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;
import ru.nekostul.worldzero.network.WorldZeroMinorAnomalyPacket;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroMinorAnomalyClientController {
    private static int worldzero$peripheralEchoTicks;
    private static int worldzero$peripheralEchoDurationTicks;
    private static int worldzero$peripheralEchoSide;
    private static int worldzero$shadowDelayTicks;
    private static int worldzero$shadowDelayDurationTicks;
    private static int worldzero$shadowDelayOffset;

    private WorldZeroMinorAnomalyClientController() {
    }

    public static void worldzero$trigger(byte action, int durationTicks, int variant) {
        if (action == WorldZeroMinorAnomalyPacket.WORLDZERO_ACTION_CLEAR_ALL) {
            worldzero$clearState();
            return;
        }

        int safeDurationTicks = Math.max(1, durationTicks);
        if (action == WorldZeroMinorAnomalyPacket.WORLDZERO_ACTION_PERIPHERAL_ECHO) {
            worldzero$peripheralEchoTicks = safeDurationTicks;
            worldzero$peripheralEchoDurationTicks = safeDurationTicks;
            worldzero$peripheralEchoSide = variant & 1;
        } else if (action == WorldZeroMinorAnomalyPacket.WORLDZERO_ACTION_SHADOW_DELAY) {
            worldzero$shadowDelayTicks = safeDurationTicks;
            worldzero$shadowDelayDurationTicks = safeDurationTicks;
            worldzero$shadowDelayOffset = Mth.clamp(variant, -1, 1);
        }
    }

    @SubscribeEvent
    public static void worldzero$onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (worldzero$peripheralEchoTicks > 0) {
            worldzero$peripheralEchoTicks--;
        }
        if (worldzero$shadowDelayTicks > 0) {
            worldzero$shadowDelayTicks--;
        }
    }

    @SubscribeEvent
    public static void worldzero$onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        if (worldzero$peripheralEchoTicks > 0) {
            worldzero$renderPeripheralEcho(guiGraphics);
        }
        if (worldzero$shadowDelayTicks > 0) {
            worldzero$renderShadowDelay(guiGraphics);
        }
    }

    @SubscribeEvent
    public static void worldzero$onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        worldzero$clearState();
    }

    private static void worldzero$renderPeripheralEcho(GuiGraphics guiGraphics) {
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        float alphaScale = worldzero$fade(worldzero$peripheralEchoTicks, worldzero$peripheralEchoDurationTicks);
        int smudgeWidth = Math.max(32, width / 7);
        int smudgeHeight = Math.max(70, height / 2);
        int top = height / 2 - smudgeHeight / 2;
        int left = worldzero$peripheralEchoSide == 0 ? 0 : width - smudgeWidth;

        for (int index = 0; index < 6; index++) {
            int stripAlpha = Mth.clamp((int) ((64 - index * 8) * alphaScale), 0, 90);
            int color = stripAlpha << 24;
            int inset = index * smudgeWidth / 12;
            int x1 = worldzero$peripheralEchoSide == 0 ? left + inset : left + smudgeWidth / 2 - inset;
            int x2 = worldzero$peripheralEchoSide == 0 ? left + smudgeWidth - inset : left + smudgeWidth - inset / 2;
            int y1 = top + index * smudgeHeight / 18;
            int y2 = top + smudgeHeight - index * smudgeHeight / 18;
            guiGraphics.fill(x1, y1, x2, y2, color);
        }
    }

    private static void worldzero$renderShadowDelay(GuiGraphics guiGraphics) {
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        float alphaScale = worldzero$fade(worldzero$shadowDelayTicks, worldzero$shadowDelayDurationTicks);
        int centerX = width / 2 + worldzero$shadowDelayOffset * Math.max(18, width / 18);
        int span = Math.max(120, width / 3);
        int baseY = height - Math.max(42, height / 6);

        for (int index = 0; index < 9; index++) {
            int distanceFromCenter = Math.abs(index - 4);
            int segmentWidth = Math.max(12, span / (7 + distanceFromCenter));
            int x = centerX - span / 2 + index * span / 8 - segmentWidth / 2;
            int y = baseY + (int) (Math.sin((index + worldzero$shadowDelayOffset * 2.0D) * 1.4D) * 4.0D);
            int stripAlpha = Mth.clamp((int) ((48 - distanceFromCenter * 7) * alphaScale), 0, 70);
            int color = stripAlpha << 24;
            guiGraphics.fill(x, y, x + segmentWidth, y + 2, color);

            if ((index & 1) == 0) {
                int tailAlpha = Mth.clamp((int) ((22 - distanceFromCenter * 3) * alphaScale), 0, 42);
                int tailColor = tailAlpha << 24;
                guiGraphics.fill(x + segmentWidth / 2, y - 6 - distanceFromCenter, x + segmentWidth / 2 + 2, y + 1, tailColor);
            }
        }

        int drift = worldzero$shadowDelayOffset == 0 ? 1 : worldzero$shadowDelayOffset;
        int sideX = centerX - drift * Math.max(24, width / 24);
        for (int index = 0; index < 4; index++) {
            int stripAlpha = Mth.clamp((int) ((24 - index * 4) * alphaScale), 0, 38);
            int color = stripAlpha << 24;
            int x1 = sideX - drift * index * 5;
            int x2 = x1 + drift * (4 + index);
            int y1 = baseY - 28 - index * 9;
            if (x2 < x1) {
                int oldX1 = x1;
                x1 = x2;
                x2 = oldX1;
            }
            guiGraphics.fill(x1, y1, x2, y1 + Math.max(10, height / 30), color);
        }
    }

    private static float worldzero$fade(int ticksRemaining, int durationTicks) {
        if (durationTicks <= 0) {
            return 0.0F;
        }

        float progress = Mth.clamp((float) ticksRemaining / (float) durationTicks, 0.0F, 1.0F);
        return Mth.clamp(progress * 1.7F, 0.0F, 1.0F);
    }

    private static void worldzero$clearState() {
        worldzero$peripheralEchoTicks = 0;
        worldzero$peripheralEchoDurationTicks = 0;
        worldzero$shadowDelayTicks = 0;
        worldzero$shadowDelayDurationTicks = 0;
        worldzero$shadowDelayOffset = 0;
    }
}
