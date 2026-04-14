package ru.nekostul.worldzero;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroFallClientController {
    private static final ResourceLocation WORLDZERO_FALL_SOUND_ID = new ResourceLocation(
            WorldZeroMod.MOD_ID,
            "falling_11"
    );
    private static boolean worldzero$fallSoundPlayed;
    private static boolean worldzero$fallOverlayActive;
    private static boolean worldzero$volumeBoostActive;
    private static SoundInstance worldzero$activeFallSound;
    private static final Map<SoundSource, Float> WORLDZERO_ORIGINAL_VOLUMES = new EnumMap<>(SoundSource.class);

    private WorldZeroFallClientController() {
    }

    public static void handleAction(byte action) {
        switch (action) {
            case WorldZeroFallClientPacket.WORLDZERO_ACTION_BEGIN -> worldzero$reset();
            case WorldZeroFallClientPacket.WORLDZERO_ACTION_START_FALL -> {
                worldzero$fallOverlayActive = true;
                worldzero$playFallSound();
            }
            case WorldZeroFallClientPacket.WORLDZERO_ACTION_SHOW_DEATH -> {
                worldzero$fallOverlayActive = false;
                worldzero$showFakeDeathScreen();
            }
            case WorldZeroFallClientPacket.WORLDZERO_ACTION_CLEAR -> worldzero$clearClientState();
            default -> {
            }
        }
    }

    public static boolean worldzero$isFallOverlayActive() {
        return worldzero$fallOverlayActive;
    }

    public static void worldzero$onFakeRespawnPressed() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.screen instanceof WorldZeroFakeDeathScreen) {
            minecraft.setScreen(null);
        }

        worldzero$clearClientState();
        WorldZeroNetwork.sendFallRespawn();
    }

    @SubscribeEvent
    public static void worldzero$onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        worldzero$clearClientState();
    }

    @SubscribeEvent
    public static void worldzero$onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !worldzero$volumeBoostActive) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundManager() == null || worldzero$activeFallSound == null) {
            worldzero$restoreVolumes();
            return;
        }

        if (!minecraft.getSoundManager().isActive(worldzero$activeFallSound)) {
            worldzero$restoreVolumes();
        }
    }

    private static void worldzero$playFallSound() {
        if (worldzero$fallSoundPlayed) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundManager() == null) {
            return;
        }

        worldzero$boostVolumes(minecraft);
        BlockPos soundPos = minecraft.player != null ? minecraft.player.blockPosition() : BlockPos.ZERO;
        SimpleSoundInstance soundInstance = new SimpleSoundInstance(
                SoundEvent.createVariableRangeEvent(WORLDZERO_FALL_SOUND_ID),
                SoundSource.MASTER,
                1.0F,
                1.0F,
                RandomSource.create(),
                soundPos
        );
        minecraft.getSoundManager().play(soundInstance);
        worldzero$activeFallSound = soundInstance;
        worldzero$fallSoundPlayed = true;
    }

    private static void worldzero$showFakeDeathScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        minecraft.setScreen(new WorldZeroFakeDeathScreen());
    }

    private static void worldzero$reset() {
        worldzero$fallSoundPlayed = false;
        worldzero$fallOverlayActive = false;
        worldzero$restoreVolumes();
        worldzero$activeFallSound = null;
    }

    private static void worldzero$clearClientState() {
        worldzero$fallSoundPlayed = false;
        worldzero$fallOverlayActive = false;
        worldzero$restoreVolumes();
        worldzero$activeFallSound = null;
    }

    private static void worldzero$boostVolumes(Minecraft minecraft) {
        WORLDZERO_ORIGINAL_VOLUMES.clear();
        for (SoundSource source : SoundSource.values()) {
            float originalVolume = minecraft.options.getSoundSourceVolume(source);
            WORLDZERO_ORIGINAL_VOLUMES.put(source, originalVolume);
            minecraft.options.getSoundSourceOptionInstance(source).set(1.0D);
            minecraft.getSoundManager().updateSourceVolume(source, 1.0F);
        }
        worldzero$volumeBoostActive = true;
    }

    private static void worldzero$restoreVolumes() {
        if (!worldzero$volumeBoostActive) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getSoundManager() != null) {
            for (Map.Entry<SoundSource, Float> entry : WORLDZERO_ORIGINAL_VOLUMES.entrySet()) {
                minecraft.options.getSoundSourceOptionInstance(entry.getKey()).set((double) entry.getValue());
                minecraft.getSoundManager().updateSourceVolume(entry.getKey(), entry.getValue());
            }
        }

        WORLDZERO_ORIGINAL_VOLUMES.clear();
        worldzero$volumeBoostActive = false;
    }
}
