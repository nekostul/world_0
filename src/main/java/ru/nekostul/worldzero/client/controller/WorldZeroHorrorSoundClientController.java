package ru.nekostul.worldzero.client.controller;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;
import ru.nekostul.worldzero.network.WorldZeroNetwork;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroHorrorSoundClientController {
    private static final Map<SoundInstance, ActiveSound> WORLDZERO_ACTIVE_SOUNDS = new IdentityHashMap<>();
    private static boolean worldzero$masterBoostActive;
    private static float worldzero$originalMasterVolume = 1.0F;
    private static boolean worldzero$internalVolumeUpdate;

    private WorldZeroHorrorSoundClientController() {
    }

    public static void worldzero$play(ResourceLocation soundId, float pitch, boolean notifyWhenFinished) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null || minecraft.getSoundManager() == null) {
            if (notifyWhenFinished) {
                WorldZeroNetwork.sendHorrorSoundFinished();
            }
            return;
        }

        worldzero$boostMasterVolume(minecraft);
        SoundInstance soundInstance = new SimpleSoundInstance(
                soundId,
                SoundSource.MASTER,
                1.0F,
                pitch,
                RandomSource.create(),
                false,
                0,
                SoundInstance.Attenuation.NONE,
                0.0D,
                0.0D,
                0.0D,
                true
        );
        minecraft.getSoundManager().play(soundInstance);
        WORLDZERO_ACTIVE_SOUNDS.put(soundInstance, new ActiveSound(notifyWhenFinished));
    }

    public static void worldzero$stopAll() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getSoundManager() != null) {
            for (SoundInstance soundInstance : WORLDZERO_ACTIVE_SOUNDS.keySet()) {
                minecraft.getSoundManager().stop(soundInstance);
            }
        }
        WORLDZERO_ACTIVE_SOUNDS.clear();
        worldzero$restoreMasterVolume();
    }

    @SubscribeEvent
    public static void worldzero$onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null || minecraft.getSoundManager() == null) {
            WORLDZERO_ACTIVE_SOUNDS.clear();
            worldzero$restoreMasterVolume();
            return;
        }

        if (worldzero$masterBoostActive && minecraft.options.getSoundSourceVolume(SoundSource.MASTER) != 1.0F) {
            worldzero$setMasterVolume(minecraft, 1.0F);
            minecraft.getSoundManager().updateSourceVolume(SoundSource.MASTER, 1.0F);
        }

        Iterator<Map.Entry<SoundInstance, ActiveSound>> iterator = WORLDZERO_ACTIVE_SOUNDS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SoundInstance, ActiveSound> entry = iterator.next();
            if (minecraft.getSoundManager().isActive(entry.getKey())) {
                continue;
            }

            if (entry.getValue().worldzero$notifyWhenFinished && !entry.getValue().worldzero$finishSignalSent) {
                entry.getValue().worldzero$finishSignalSent = true;
                WorldZeroNetwork.sendHorrorSoundFinished();
            }
            iterator.remove();
        }

        if (WORLDZERO_ACTIVE_SOUNDS.isEmpty()) {
            worldzero$restoreMasterVolume();
        }
    }

    @SubscribeEvent
    public static void worldzero$onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        worldzero$stopAll();
    }

    private static void worldzero$boostMasterVolume(Minecraft minecraft) {
        if (!worldzero$masterBoostActive) {
            worldzero$originalMasterVolume = minecraft.options.getSoundSourceVolume(SoundSource.MASTER);
            worldzero$masterBoostActive = true;
        }
        worldzero$setMasterVolume(minecraft, 1.0F);
        minecraft.getSoundManager().updateSourceVolume(SoundSource.MASTER, 1.0F);
    }

    private static void worldzero$restoreMasterVolume() {
        if (!worldzero$masterBoostActive) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.options != null && minecraft.getSoundManager() != null) {
            worldzero$setMasterVolume(minecraft, worldzero$originalMasterVolume);
            minecraft.getSoundManager().updateSourceVolume(SoundSource.MASTER, worldzero$originalMasterVolume);
        }
        worldzero$masterBoostActive = false;
    }

    private static void worldzero$setMasterVolume(Minecraft minecraft, float volume) {
        worldzero$internalVolumeUpdate = true;
        try {
            minecraft.options.getSoundSourceOptionInstance(SoundSource.MASTER).set((double) volume);
        } finally {
            worldzero$internalVolumeUpdate = false;
        }
    }

    private static final class ActiveSound {
        private final boolean worldzero$notifyWhenFinished;
        private boolean worldzero$finishSignalSent;

        private ActiveSound(boolean notifyWhenFinished) {
            this.worldzero$notifyWhenFinished = notifyWhenFinished;
        }
    }
}
