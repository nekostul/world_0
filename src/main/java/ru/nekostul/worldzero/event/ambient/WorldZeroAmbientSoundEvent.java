package ru.nekostul.worldzero;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroAmbientSoundEvent {
    private static final String WORLDZERO_SAVE_ID = "worldzero_ambient_sound_event";
    private static final long WORLDZERO_FAKE_C418_TRIGGER_MIN_TICKS = 50L * 60L * 20L;
    private static final long WORLDZERO_FAKE_C418_TRIGGER_MAX_TICKS = 120L * 60L * 20L;
    private static final long WORLDZERO_MAJOR_EVENT_TO_MUSIC_GAP_TICKS = 25L * 60L * 20L;
    private static final long WORLDZERO_MUSIC_TO_NEXT_EVENT_GAP_TICKS = 10L * 60L * 20L;
    private static final long WORLDZERO_DOOR_SOUND_MIN_TICKS = 30L * 60L * 20L;
    private static final long WORLDZERO_DOOR_SOUND_FIRST_MAX_TICKS = 70L * 60L * 20L;
    private static final long WORLDZERO_DOOR_SOUND_MAX_TICKS = 90L * 60L * 20L;
    private static final long WORLDZERO_DOOR_SOUND_GAP_MIN_TICKS = 5L * 60L * 20L;
    private static final ResourceLocation WORLDZERO_FAKE_C418_SOUND_ID = new ResourceLocation(WorldZeroMod.MOD_ID, "fake_c418");
    private static final ResourceLocation WORLDZERO_OPEN_DOOR_SOUND_ID = new ResourceLocation(WorldZeroMod.MOD_ID, "open_door");
    private static final ResourceLocation WORLDZERO_CLOSE_DOOR_SOUND_ID = new ResourceLocation(WorldZeroMod.MOD_ID, "close_door");

    private WorldZeroAmbientSoundEvent() {
    }

    @SubscribeEvent
    public static void worldzero$onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.level instanceof ServerLevel level) || level.isClientSide() || level.dimension() != Level.OVERWORLD) {
            return;
        }

        AmbientSoundSaveData saveData = worldzero$getSaveData(level);
        long gameTime = level.getGameTime();
        if (saveData.worldzero$fakeC418TriggerTick < 0L) {
            saveData.worldzero$fakeC418TriggerTick = worldzero$randomTick(
                    level,
                    WORLDZERO_FAKE_C418_TRIGGER_MIN_TICKS,
                    WORLDZERO_FAKE_C418_TRIGGER_MAX_TICKS
            );
            worldzero$initializeDoorSchedule(level, saveData);
            saveData.setDirty();
        }

        worldzero$tryPlayDoorSound(level, saveData, gameTime, true);
        worldzero$tryPlayDoorSound(level, saveData, gameTime, false);

        if (saveData.worldzero$fakeC418Played || gameTime < saveData.worldzero$fakeC418TriggerTick) {
            return;
        }

        if (worldzero$hasActiveConflictingEvent(level)) {
            return;
        }

        if (saveData.worldzero$lastMajorEventTick >= 0L
                && gameTime < saveData.worldzero$lastMajorEventTick + WORLDZERO_MAJOR_EVENT_TO_MUSIC_GAP_TICKS) {
            return;
        }

        ServerPlayer player = worldzero$pickTargetPlayer(level);
        if (player == null) {
            return;
        }

        worldzero$playSoundAtPlayer(player, WORLDZERO_FAKE_C418_SOUND_ID, SoundSource.MUSIC, 1.0F, 1.0F);
        saveData.worldzero$fakeC418Played = true;
        saveData.worldzero$nextMajorEventAllowedTick = gameTime + WORLDZERO_MUSIC_TO_NEXT_EVENT_GAP_TICKS;
        saveData.setDirty();
    }

    public static boolean worldzero$isMajorEventStartBlocked(ServerLevel level) {
        if (level == null || level.isClientSide() || level.dimension() != Level.OVERWORLD) {
            return false;
        }

        AmbientSoundSaveData saveData = worldzero$getSaveData(level);
        return saveData.worldzero$nextMajorEventAllowedTick >= 0L
                && level.getGameTime() < saveData.worldzero$nextMajorEventAllowedTick;
    }

    public static void worldzero$notifyMajorEventStarted(ServerLevel level) {
        if (level == null || level.isClientSide() || level.dimension() != Level.OVERWORLD) {
            return;
        }

        AmbientSoundSaveData saveData = worldzero$getSaveData(level);
        long gameTime = level.getGameTime();
        if (gameTime > saveData.worldzero$lastMajorEventTick) {
            saveData.worldzero$lastMajorEventTick = gameTime;
            saveData.setDirty();
        }
    }

    private static void worldzero$tryPlayDoorSound(
            ServerLevel level,
            AmbientSoundSaveData saveData,
            long gameTime,
            boolean openSound
    ) {
        long triggerTick = openSound ? saveData.worldzero$openDoorTriggerTick : saveData.worldzero$closeDoorTriggerTick;
        boolean alreadyPlayed = openSound ? saveData.worldzero$openDoorPlayed : saveData.worldzero$closeDoorPlayed;
        if (alreadyPlayed || triggerTick < 0L || gameTime < triggerTick) {
            return;
        }

        ServerPlayer player = worldzero$pickTargetPlayer(level);
        if (player == null) {
            return;
        }

        worldzero$playSoundAtPlayer(
                player,
                openSound ? WORLDZERO_OPEN_DOOR_SOUND_ID : WORLDZERO_CLOSE_DOOR_SOUND_ID,
                SoundSource.PLAYERS,
                1.0F,
                1.0F
        );
        if (openSound) {
            saveData.worldzero$openDoorPlayed = true;
        } else {
            saveData.worldzero$closeDoorPlayed = true;
        }
        saveData.setDirty();
    }

    private static void worldzero$initializeDoorSchedule(ServerLevel level, AmbientSoundSaveData saveData) {
        if (saveData.worldzero$openDoorTriggerTick >= 0L || saveData.worldzero$closeDoorTriggerTick >= 0L) {
            return;
        }

        long firstTick = worldzero$randomTick(level, WORLDZERO_DOOR_SOUND_MIN_TICKS, WORLDZERO_DOOR_SOUND_FIRST_MAX_TICKS);
        long secondMinTick = Math.min(WORLDZERO_DOOR_SOUND_MAX_TICKS, firstTick + WORLDZERO_DOOR_SOUND_GAP_MIN_TICKS);
        long secondTick = worldzero$randomTick(level, secondMinTick, WORLDZERO_DOOR_SOUND_MAX_TICKS);
        if (level.random.nextBoolean()) {
            saveData.worldzero$openDoorTriggerTick = firstTick;
            saveData.worldzero$closeDoorTriggerTick = secondTick;
        } else {
            saveData.worldzero$closeDoorTriggerTick = firstTick;
            saveData.worldzero$openDoorTriggerTick = secondTick;
        }
    }

    private static long worldzero$randomTick(ServerLevel level, long minTick, long maxTick) {
        if (maxTick <= minTick) {
            return minTick;
        }

        long span = maxTick - minTick + 1L;
        return minTick + (long) (level.random.nextDouble() * span);
    }

    private static void worldzero$playSoundAtPlayer(
            ServerPlayer player,
            ResourceLocation soundId,
            SoundSource source,
            float volume,
            float pitch
    ) {
        BlockPos soundPos = player.blockPosition();
        player.serverLevel().playSound(
                null,
                soundPos,
                SoundEvent.createVariableRangeEvent(soundId),
                source,
                volume,
                pitch
        );
    }

    private static boolean worldzero$hasActiveConflictingEvent(ServerLevel level) {
        return WorldZeroFreezeEvent.worldzero$isFreezeActive(level.getServer())
                || WorldZeroFallEvent.worldzero$isFallActive(level.getServer())
                || WorldZeroFootstepsEvent.worldzero$isFootstepsActive(level.getServer())
                || WorldZeroHouseEvent.worldzero$isHouseActive(level.getServer())
                || WorldZeroParalysisEvent.worldzero$isParalysisActive(level.getServer());
    }

    private static ServerPlayer worldzero$pickTargetPlayer(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator()) {
                return player;
            }
        }
        return null;
    }

    private static AmbientSoundSaveData worldzero$getSaveData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                AmbientSoundSaveData::worldzero$load,
                AmbientSoundSaveData::new,
                WORLDZERO_SAVE_ID
        );
    }

    private static final class AmbientSoundSaveData extends SavedData {
        private long worldzero$fakeC418TriggerTick = -1L;
        private boolean worldzero$fakeC418Played;
        private long worldzero$lastMajorEventTick = -1L;
        private long worldzero$nextMajorEventAllowedTick = -1L;
        private long worldzero$openDoorTriggerTick = -1L;
        private long worldzero$closeDoorTriggerTick = -1L;
        private boolean worldzero$openDoorPlayed;
        private boolean worldzero$closeDoorPlayed;

        private static AmbientSoundSaveData worldzero$load(CompoundTag tag) {
            AmbientSoundSaveData saveData = new AmbientSoundSaveData();
            saveData.worldzero$fakeC418TriggerTick = tag.getLong("fake_c418_trigger_tick");
            saveData.worldzero$fakeC418Played = tag.getBoolean("fake_c418_played");
            saveData.worldzero$lastMajorEventTick = tag.getLong("last_major_event_tick");
            saveData.worldzero$nextMajorEventAllowedTick = tag.getLong("next_major_event_allowed_tick");
            saveData.worldzero$openDoorTriggerTick = tag.getLong("open_door_trigger_tick");
            saveData.worldzero$closeDoorTriggerTick = tag.getLong("close_door_trigger_tick");
            saveData.worldzero$openDoorPlayed = tag.getBoolean("open_door_played");
            saveData.worldzero$closeDoorPlayed = tag.getBoolean("close_door_played");
            return saveData;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putLong("fake_c418_trigger_tick", this.worldzero$fakeC418TriggerTick);
            tag.putBoolean("fake_c418_played", this.worldzero$fakeC418Played);
            tag.putLong("last_major_event_tick", this.worldzero$lastMajorEventTick);
            tag.putLong("next_major_event_allowed_tick", this.worldzero$nextMajorEventAllowedTick);
            tag.putLong("open_door_trigger_tick", this.worldzero$openDoorTriggerTick);
            tag.putLong("close_door_trigger_tick", this.worldzero$closeDoorTriggerTick);
            tag.putBoolean("open_door_played", this.worldzero$openDoorPlayed);
            tag.putBoolean("close_door_played", this.worldzero$closeDoorPlayed);
            return tag;
        }
    }
}
