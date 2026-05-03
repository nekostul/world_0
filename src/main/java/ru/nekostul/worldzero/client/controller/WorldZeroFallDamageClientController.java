package ru.nekostul.worldzero.client.controller;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import ru.nekostul.worldzero.WorldZeroMod;

import javax.annotation.Nullable;

public final class WorldZeroFallDamageClientController {
    private static final ResourceLocation WORLDZERO_HURT_SOUND_ID = new ResourceLocation(
            WorldZeroMod.MOD_ID,
            "hurt"
    );
    private static final ResourceLocation WORLDZERO_PLAYER_BIG_FALL_SOUND_ID = SoundEvents.PLAYER_BIG_FALL.getLocation();
    private static final ResourceLocation WORLDZERO_PLAYER_HURT_SOUND_ID = SoundEvents.PLAYER_HURT.getLocation();
    private static final ResourceLocation WORLDZERO_PLAYER_SMALL_FALL_SOUND_ID = SoundEvents.PLAYER_SMALL_FALL.getLocation();
    private static final long WORLDZERO_PLAYER_HURT_MUTE_WINDOW_MS = 250L;

    private static long worldzero$mutePlayerHurtUntilMillis;
    private static double worldzero$lastFallSoundX;
    private static double worldzero$lastFallSoundY;
    private static double worldzero$lastFallSoundZ;

    private WorldZeroFallDamageClientController() {
    }

    @Nullable
    public static SoundInstance worldzero$createFallDamageReplacement(@Nullable SoundInstance originalSound) {
        if (originalSound == null || !worldzero$shouldReplaceFallDamageSound(originalSound)) {
            return null;
        }

        worldzero$mutePlayerHurtUntilMillis = System.currentTimeMillis() + WORLDZERO_PLAYER_HURT_MUTE_WINDOW_MS;
        worldzero$lastFallSoundX = originalSound.getX();
        worldzero$lastFallSoundY = originalSound.getY();
        worldzero$lastFallSoundZ = originalSound.getZ();
        return new SimpleSoundInstance(
                WORLDZERO_HURT_SOUND_ID,
                originalSound.getSource(),
                1.0F,
                1.0F,
                RandomSource.create(),
                originalSound.isLooping(),
                originalSound.getDelay(),
                originalSound.getAttenuation(),
                originalSound.getX(),
                originalSound.getY(),
                originalSound.getZ(),
                originalSound.isRelative()
        );
    }

    @Nullable
    public static SoundInstance worldzero$createFallDamageMute(@Nullable SoundInstance originalSound) {
        if (originalSound == null || !worldzero$shouldMutePlayerHurtSound(originalSound)) {
            return null;
        }

        return new SimpleSoundInstance(
                SoundManager.INTENTIONALLY_EMPTY_SOUND_LOCATION,
                originalSound.getSource(),
                1.0F,
                1.0F,
                RandomSource.create(),
                originalSound.isLooping(),
                originalSound.getDelay(),
                originalSound.getAttenuation(),
                originalSound.getX(),
                originalSound.getY(),
                originalSound.getZ(),
                originalSound.isRelative()
        );
    }

    private static boolean worldzero$shouldReplaceFallDamageSound(SoundInstance soundInstance) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null
                || minecraft.player == null
                || minecraft.level == null
                || !Level.OVERWORLD.equals(minecraft.level.dimension())) {
            return false;
        }

        ResourceLocation soundId = soundInstance.getLocation();
        return WORLDZERO_PLAYER_SMALL_FALL_SOUND_ID.equals(soundId)
                || WORLDZERO_PLAYER_BIG_FALL_SOUND_ID.equals(soundId);
    }

    private static boolean worldzero$shouldMutePlayerHurtSound(SoundInstance soundInstance) {
        if (System.currentTimeMillis() > worldzero$mutePlayerHurtUntilMillis) {
            return false;
        }

        if (!worldzero$shouldHandleOverworldPlayerFallSound(soundInstance)) {
            return false;
        }

        if (!WORLDZERO_PLAYER_HURT_SOUND_ID.equals(soundInstance.getLocation())) {
            return false;
        }

        double deltaX = soundInstance.getX() - worldzero$lastFallSoundX;
        double deltaY = soundInstance.getY() - worldzero$lastFallSoundY;
        double deltaZ = soundInstance.getZ() - worldzero$lastFallSoundZ;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ <= 4.0D;
    }

    private static boolean worldzero$shouldHandleOverworldPlayerFallSound(SoundInstance soundInstance) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null
                && minecraft.player != null
                && minecraft.level != null
                && Level.OVERWORLD.equals(minecraft.level.dimension())
                && soundInstance != null;
    }
}
