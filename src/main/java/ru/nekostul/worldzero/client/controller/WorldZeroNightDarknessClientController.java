package ru.nekostul.worldzero.client.controller;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroNightDarknessClientController {
    private static final int WORLDZERO_LOCKED_RENDER_DISTANCE_OPTION = 2;
    private static final int WORLDZERO_EFFECTIVE_RENDER_DISTANCE = 1;

    private static boolean worldzero$active;
    private static boolean worldzero$renderDistanceLocked;
    private static int worldzero$previousRenderDistance = -1;

    private WorldZeroNightDarknessClientController() {
    }

    public static void worldzero$setActive(boolean active) {
        if (active) {
            worldzero$activate();
        } else {
            worldzero$clearState();
        }
    }

    public static boolean worldzero$isRenderDistanceForced() {
        return worldzero$renderDistanceLocked;
    }

    public static int worldzero$getForcedEffectiveRenderDistance() {
        return WORLDZERO_EFFECTIVE_RENDER_DISTANCE;
    }

    @SubscribeEvent
    public static void worldzero$onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !worldzero$renderDistanceLocked) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            worldzero$enforceRenderDistanceLock(minecraft);
        }
    }

    @SubscribeEvent
    public static void worldzero$onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        worldzero$clearState();
    }

    private static void worldzero$activate() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null) {
            return;
        }

        worldzero$active = true;
        if (!worldzero$renderDistanceLocked) {
            worldzero$previousRenderDistance = minecraft.options.renderDistance().get();
        }
        worldzero$renderDistanceLocked = true;
        worldzero$enforceRenderDistanceLock(minecraft);
    }

    private static void worldzero$enforceRenderDistanceLock(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null) {
            return;
        }

        if (minecraft.options.renderDistance().get() == WORLDZERO_LOCKED_RENDER_DISTANCE_OPTION) {
            return;
        }

        minecraft.options.renderDistance().set(WORLDZERO_LOCKED_RENDER_DISTANCE_OPTION);
        if (minecraft.levelRenderer != null) {
            minecraft.levelRenderer.allChanged();
        }
    }

    private static void worldzero$restoreRenderDistance() {
        if (!worldzero$renderDistanceLocked) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int restoreDistance = worldzero$previousRenderDistance;
        worldzero$renderDistanceLocked = false;
        worldzero$previousRenderDistance = -1;
        if (minecraft == null || minecraft.options == null || restoreDistance < 0) {
            return;
        }

        if (minecraft.options.renderDistance().get() != restoreDistance) {
            minecraft.options.renderDistance().set(restoreDistance);
            if (minecraft.levelRenderer != null) {
                minecraft.levelRenderer.allChanged();
            }
        }
    }

    private static void worldzero$clearState() {
        worldzero$active = false;
        worldzero$restoreRenderDistance();
    }
}
