package ru.nekostul.worldzero.client.controller;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;
import ru.nekostul.worldzero.dimension.WorldZeroKoridorDimension;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroKoridorClientController {
    private static final ResourceLocation WORLDZERO_FAKE_C418_SOUND_ID = new ResourceLocation(
            WorldZeroMod.MOD_ID,
            "fake_c418"
    );
    private static final ResourceLocation WORLDZERO_OPEN_DOOR_SOUND_ID = new ResourceLocation(
            WorldZeroMod.MOD_ID,
            "open_door"
    );
    private static final ResourceLocation WORLDZERO_CLOSE_DOOR_SOUND_ID = new ResourceLocation(
            WorldZeroMod.MOD_ID,
            "close_door"
    );
    private static final ResourceLocation WORLDZERO_WOODEN_DOOR_OPEN_SOUND_ID = new ResourceLocation(
            "minecraft",
            "block.wooden_door.open"
    );
    private static final ResourceLocation WORLDZERO_WOODEN_DOOR_CLOSE_SOUND_ID = new ResourceLocation(
            "minecraft",
            "block.wooden_door.close"
    );
    private static final ResourceLocation WORLDZERO_IRON_DOOR_OPEN_SOUND_ID = new ResourceLocation(
            "minecraft",
            "block.iron_door.open"
    );
    private static final ResourceLocation WORLDZERO_IRON_DOOR_CLOSE_SOUND_ID = new ResourceLocation(
            "minecraft",
            "block.iron_door.close"
    );

    private static final Map<SoundSource, Float> WORLDZERO_ORIGINAL_VOLUMES = new EnumMap<>(SoundSource.class);

    private static boolean worldzero$koridorActive;
    private static boolean worldzero$internalVolumeUpdate;
    private static SoundInstance worldzero$activeKoridorSound;

    private WorldZeroKoridorClientController() {
    }

    public static boolean worldzero$isVolumeForced() {
        return worldzero$koridorActive || worldzero$isKoridorLevel(Minecraft.getInstance());
    }

    public static boolean worldzero$shouldBlockSoundOptionChange(@Nullable OptionInstance<?> optionInstance, @Nullable Object value) {
        Minecraft minecraft = Minecraft.getInstance();
        if (worldzero$internalVolumeUpdate
                || !worldzero$isVolumeForced()
                || !(value instanceof Double doubleValue)
                || doubleValue >= 0.999D
                || minecraft == null
                || minecraft.options == null
                || optionInstance == null) {
            return false;
        }

        for (SoundSource source : SoundSource.values()) {
            if (source == SoundSource.MUSIC) {
                continue;
            }

            if (minecraft.options.getSoundSourceOptionInstance(source) == optionInstance) {
                return true;
            }
        }

        return false;
    }

    public static boolean worldzero$isVanillaDoorSoundInstance(@Nullable SoundInstance soundInstance) {
        return soundInstance instanceof WorldZeroVanillaDoorSoundInstance;
    }

    @Nullable
    public static SoundInstance worldzero$createDoorReplacement(SoundInstance originalSound, int delay) {
        if (originalSound == null || !worldzero$isKoridorLevel(Minecraft.getInstance())) {
            return null;
        }

        ResourceLocation replacementSoundId = worldzero$getReplacementDoorSoundId(originalSound.getLocation());
        if (replacementSoundId == null) {
            return null;
        }

        return new SimpleSoundInstance(
                replacementSoundId,
                originalSound.getSource(),
                1.0F,
                1.0F,
                RandomSource.create(),
                false,
                0,
                originalSound.getAttenuation(),
                originalSound.getX(),
                originalSound.getY(),
                originalSound.getZ(),
                originalSound.isRelative()
        );
    }

    public static void worldzero$playVanillaDoorSound(ResourceLocation soundId, double x, double y, double z) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundManager() == null) {
            return;
        }

        minecraft.getSoundManager().play(new WorldZeroVanillaDoorSoundInstance(soundId, x, y, z));
    }

    @SubscribeEvent
    public static void worldzero$onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        boolean inKoridor = worldzero$isKoridorLevel(minecraft);
        if (!inKoridor) {
            if (worldzero$koridorActive) {
                worldzero$clearState();
            }
            return;
        }

        if (!worldzero$koridorActive) {
            worldzero$enterKoridor(minecraft);
        } else {
            worldzero$enforceVolumes(minecraft);
        }

        if (minecraft == null || minecraft.getSoundManager() == null) {
            worldzero$clearState();
            return;
        }

        if (worldzero$activeKoridorSound != null && !minecraft.getSoundManager().isActive(worldzero$activeKoridorSound)) {
            worldzero$activeKoridorSound = null;
        }
    }

    @SubscribeEvent
    public static void worldzero$onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        worldzero$clearState();
    }

    private static void worldzero$enterKoridor(Minecraft minecraft) {
        if (minecraft == null || minecraft.getSoundManager() == null || minecraft.options == null) {
            return;
        }

        worldzero$captureOriginalVolumes(minecraft);
        worldzero$koridorActive = true;
        worldzero$enforceVolumes(minecraft);
        worldzero$playEntryMusic(minecraft);
    }

    private static void worldzero$playEntryMusic(Minecraft minecraft) {
        if (minecraft == null || minecraft.getSoundManager() == null) {
            return;
        }

        SoundInstance koridorSound = new SimpleSoundInstance(
                WORLDZERO_FAKE_C418_SOUND_ID,
                SoundSource.RECORDS,
                1.0F,
                1.0F,
                RandomSource.create(),
                false,
                0,
                Attenuation.NONE,
                0.0D,
                0.0D,
                0.0D,
                true
        );
        minecraft.getSoundManager().play(koridorSound);
        worldzero$activeKoridorSound = koridorSound;
    }

    private static void worldzero$captureOriginalVolumes(Minecraft minecraft) {
        WORLDZERO_ORIGINAL_VOLUMES.clear();
        for (SoundSource source : SoundSource.values()) {
            if (source == SoundSource.MUSIC) {
                continue;
            }
            WORLDZERO_ORIGINAL_VOLUMES.put(source, minecraft.options.getSoundSourceVolume(source));
        }
    }

    private static void worldzero$enforceVolumes(Minecraft minecraft) {
        if (minecraft == null || minecraft.getSoundManager() == null || minecraft.options == null) {
            return;
        }

        for (SoundSource source : SoundSource.values()) {
            if (source == SoundSource.MUSIC) {
                continue;
            }
            if (minecraft.options.getSoundSourceVolume(source) != 1.0F) {
                worldzero$setSourceVolume(minecraft, source, 1.0F);
            }
            minecraft.getSoundManager().updateSourceVolume(source, 1.0F);
        }
    }

    private static void worldzero$clearState() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getSoundManager() != null) {
            if (worldzero$activeKoridorSound != null) {
                minecraft.getSoundManager().stop(worldzero$activeKoridorSound);
            }
            if (minecraft.options != null) {
                for (Map.Entry<SoundSource, Float> entry : WORLDZERO_ORIGINAL_VOLUMES.entrySet()) {
                    worldzero$setSourceVolume(minecraft, entry.getKey(), entry.getValue());
                    minecraft.getSoundManager().updateSourceVolume(entry.getKey(), entry.getValue());
                }
            }
        }

        WORLDZERO_ORIGINAL_VOLUMES.clear();
        worldzero$activeKoridorSound = null;
        worldzero$koridorActive = false;
    }

    private static boolean worldzero$isKoridorLevel(@Nullable Minecraft minecraft) {
        return minecraft != null
                && minecraft.player != null
                && minecraft.level != null
                && minecraft.level.dimension() == WorldZeroKoridorDimension.WORLDZERO_KORIDOR_LEVEL;
    }

    private static void worldzero$setSourceVolume(Minecraft minecraft, SoundSource source, float volume) {
        worldzero$internalVolumeUpdate = true;
        try {
            minecraft.options.getSoundSourceOptionInstance(source).set((double) volume);
        } finally {
            worldzero$internalVolumeUpdate = false;
        }
    }

    @Nullable
    private static ResourceLocation worldzero$getReplacementDoorSoundId(ResourceLocation originalSoundId) {
        if (WORLDZERO_WOODEN_DOOR_OPEN_SOUND_ID.equals(originalSoundId)
                || WORLDZERO_IRON_DOOR_OPEN_SOUND_ID.equals(originalSoundId)) {
            return WORLDZERO_OPEN_DOOR_SOUND_ID;
        }

        if (WORLDZERO_WOODEN_DOOR_CLOSE_SOUND_ID.equals(originalSoundId)
                || WORLDZERO_IRON_DOOR_CLOSE_SOUND_ID.equals(originalSoundId)) {
            return WORLDZERO_CLOSE_DOOR_SOUND_ID;
        }

        return null;
    }

    private static final class WorldZeroVanillaDoorSoundInstance extends SimpleSoundInstance {
        private WorldZeroVanillaDoorSoundInstance(ResourceLocation soundId, double x, double y, double z) {
            super(
                    soundId,
                    SoundSource.PLAYERS,
                    1.35F,
                    1.0F,
                    RandomSource.create(),
                    false,
                    0,
                    Attenuation.NONE,
                    x,
                    y,
                    z,
                    false
            );
        }
    }
}
