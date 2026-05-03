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
import ru.nekostul.worldzero.network.WorldZeroMajorEventPacket;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroMajorEventClientController {
    private static int worldzero$watchingTicks;
    private static int worldzero$watchingDurationTicks;
    private static int worldzero$watchingVariant;
    private static int worldzero$corruptionTicks;
    private static int worldzero$corruptionDurationTicks;
    private static int worldzero$swarmTicks;
    private static int worldzero$swarmDurationTicks;
    private static int worldzero$timeLoopTicks;
    private static int worldzero$timeLoopDurationTicks;
    private static int worldzero$glitchRainTicks;
    private static int worldzero$glitchRainDurationTicks;
    private static int worldzero$glitchRainVariant;

    private WorldZeroMajorEventClientController() {
    }

    public static void worldzero$trigger(byte action, int durationTicks, int variant) {
        if (action == WorldZeroMajorEventPacket.WORLDZERO_ACTION_CLEAR_ALL) {
            worldzero$clearState();
            return;
        }

        int safeDurationTicks = Math.max(1, durationTicks);
        switch (action) {
            case WorldZeroMajorEventPacket.WORLDZERO_ACTION_WATCHING -> {
                worldzero$watchingTicks = safeDurationTicks;
                worldzero$watchingDurationTicks = safeDurationTicks;
                worldzero$watchingVariant = variant;
            }
            case WorldZeroMajorEventPacket.WORLDZERO_ACTION_CORRUPTION -> {
                worldzero$corruptionTicks = safeDurationTicks;
                worldzero$corruptionDurationTicks = safeDurationTicks;
            }
            case WorldZeroMajorEventPacket.WORLDZERO_ACTION_SWARM -> {
                worldzero$swarmTicks = safeDurationTicks;
                worldzero$swarmDurationTicks = safeDurationTicks;
            }
            case WorldZeroMajorEventPacket.WORLDZERO_ACTION_TIME_LOOP -> {
                worldzero$timeLoopTicks = safeDurationTicks;
                worldzero$timeLoopDurationTicks = safeDurationTicks;
            }
            case WorldZeroMajorEventPacket.WORLDZERO_ACTION_GLITCH_RAIN -> {
                worldzero$glitchRainTicks = safeDurationTicks;
                worldzero$glitchRainDurationTicks = safeDurationTicks;
                worldzero$glitchRainVariant = variant;
            }
            default -> {
            }
        }
    }

    @SubscribeEvent
    public static void worldzero$onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (worldzero$watchingTicks > 0) {
            worldzero$watchingTicks--;
        }
        if (worldzero$corruptionTicks > 0) {
            worldzero$corruptionTicks--;
        }
        if (worldzero$swarmTicks > 0) {
            worldzero$swarmTicks--;
        }
        if (worldzero$timeLoopTicks > 0) {
            worldzero$timeLoopTicks--;
        }
        if (worldzero$glitchRainTicks > 0) {
            worldzero$glitchRainTicks--;
        }
    }

    @SubscribeEvent
    public static void worldzero$onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        if (worldzero$watchingTicks > 0) {
            worldzero$renderWatching(guiGraphics);
        }
        if (worldzero$corruptionTicks > 0) {
            worldzero$renderCorruption(guiGraphics);
        }
        if (worldzero$swarmTicks > 0) {
            worldzero$renderSwarm(guiGraphics);
        }
        if (worldzero$timeLoopTicks > 0) {
            worldzero$renderTimeLoop(guiGraphics);
        }
        if (worldzero$glitchRainTicks > 0) {
            worldzero$renderGlitchRain(guiGraphics);
        }
    }

    @SubscribeEvent
    public static void worldzero$onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        worldzero$clearState();
    }

    private static void worldzero$renderWatching(GuiGraphics guiGraphics) {
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        int elapsed = worldzero$watchingDurationTicks - worldzero$watchingTicks;
        float alphaScale = worldzero$fade(worldzero$watchingTicks, worldzero$watchingDurationTicks);
        int edgeAlpha = Mth.clamp((int) (36.0F * alphaScale), 0, 42);
        guiGraphics.fill(0, 0, width, Math.max(20, height / 10), edgeAlpha << 24);
        guiGraphics.fill(0, height - Math.max(20, height / 10), width, height, edgeAlpha << 24);

        if (elapsed < 5 * 20) {
            return;
        }

        for (int index = 0; index < 5; index++) {
            int seed = worldzero$watchingVariant + index * 37;
            boolean leftSide = (index & 1) == 0;
            int x = leftSide ? 10 + Math.abs(seed * 17) % Math.max(18, width / 8) : width - 10 - Math.abs(seed * 19) % Math.max(18, width / 8);
            int y = height / 5 + Math.abs(seed * 13) % Math.max(28, height * 3 / 5);
            int eyeAlpha = Mth.clamp((int) ((90 - index * 8) * alphaScale), 0, 110);
            int color = (eyeAlpha << 24) | 0xE0E0E0;
            int eyeWidth = 8 + index % 2;
            guiGraphics.fill(x, y, x + eyeWidth, y + 2, color);
            guiGraphics.fill(x + 2, y - 2, x + eyeWidth - 1, y + 4, (Mth.clamp(eyeAlpha / 2, 0, 55) << 24) | 0xFFFFFF);
        }
    }

    private static void worldzero$renderCorruption(GuiGraphics guiGraphics) {
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        float alphaScale = worldzero$fade(worldzero$corruptionTicks, worldzero$corruptionDurationTicks);
        int alpha = Mth.clamp((int) (34.0F * alphaScale), 0, 42);
        guiGraphics.fill(0, 0, width, height, alpha << 24);
        for (int index = 0; index < 14; index++) {
            int x = Math.abs(index * 83 + worldzero$corruptionTicks * 3) % Math.max(1, width);
            int y = Math.abs(index * 41 + worldzero$corruptionTicks) % Math.max(1, height);
            int stripAlpha = Mth.clamp((int) ((24 - index % 5 * 3) * alphaScale), 0, 30);
            guiGraphics.fill(x, y, Math.min(width, x + 2 + index % 4), Math.min(height, y + height / 9), stripAlpha << 24);
        }
    }

    private static void worldzero$renderSwarm(GuiGraphics guiGraphics) {
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        float alphaScale = worldzero$fade(worldzero$swarmTicks, worldzero$swarmDurationTicks);
        for (int index = 0; index < 34; index++) {
            int x = Math.abs(index * 47 + worldzero$swarmTicks * (2 + index % 3)) % Math.max(1, width);
            int y = Math.abs(index * 29 + worldzero$swarmTicks * (1 + index % 2)) % Math.max(1, height);
            int size = 1 + index % 3;
            int alpha = Mth.clamp((int) ((70 - index % 6 * 6) * alphaScale), 0, 80);
            guiGraphics.fill(x, y, Math.min(width, x + size), Math.min(height, y + size), alpha << 24);
        }
    }

    private static void worldzero$renderTimeLoop(GuiGraphics guiGraphics) {
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        float alphaScale = worldzero$fade(worldzero$timeLoopTicks, worldzero$timeLoopDurationTicks);
        int pulse = (worldzero$timeLoopTicks / 5) & 1;
        int alpha = Mth.clamp((int) ((pulse == 0 ? 22 : 38) * alphaScale), 0, 42);
        int color = (alpha << 24) | (pulse == 0 ? 0x1E1E10 : 0xD8D0A0);
        guiGraphics.fill(0, 0, width, height, color);
    }

    private static void worldzero$renderGlitchRain(GuiGraphics guiGraphics) {
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        float alphaScale = worldzero$fade(worldzero$glitchRainTicks, worldzero$glitchRainDurationTicks);
        for (int index = 0; index < 26; index++) {
            int seed = worldzero$glitchRainVariant + index * 61;
            int x = Math.abs(seed * 11 + index * 17) % Math.max(1, width);
            int y = Math.abs(seed * 7 + worldzero$glitchRainTicks * (3 + index % 4)) % Math.max(1, height);
            int alpha = Mth.clamp((int) ((46 - index % 7 * 3) * alphaScale), 0, 54);
            int color = (alpha << 24) | (index % 2 == 0 ? 0x7FE8FF : 0xD9C6FF);
            guiGraphics.fill(x, y, Math.min(width, x + 2), Math.min(height, y + 8 + index % 12), color);
        }
    }

    private static float worldzero$fade(int ticksRemaining, int durationTicks) {
        if (durationTicks <= 0) {
            return 0.0F;
        }

        float progress = Mth.clamp((float) ticksRemaining / (float) durationTicks, 0.0F, 1.0F);
        return Mth.clamp(Math.min(progress * 2.0F, (1.0F - progress) * 2.0F + 0.2F), 0.0F, 1.0F);
    }

    private static void worldzero$clearState() {
        worldzero$watchingTicks = 0;
        worldzero$watchingDurationTicks = 0;
        worldzero$corruptionTicks = 0;
        worldzero$corruptionDurationTicks = 0;
        worldzero$swarmTicks = 0;
        worldzero$swarmDurationTicks = 0;
        worldzero$timeLoopTicks = 0;
        worldzero$timeLoopDurationTicks = 0;
        worldzero$glitchRainTicks = 0;
        worldzero$glitchRainDurationTicks = 0;
    }
}
