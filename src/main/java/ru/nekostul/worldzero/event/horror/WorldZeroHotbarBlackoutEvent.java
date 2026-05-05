package ru.nekostul.worldzero.event.horror;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;
import ru.nekostul.worldzero.event.WorldZeroStoryTime;
import ru.nekostul.worldzero.network.WorldZeroNetwork;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroHotbarBlackoutEvent {
    private static final long WORLDZERO_TICKS_PER_MINUTE = 20L * 60L;
    private static final long WORLDZERO_WINDOW_START_TICKS = 30L * WORLDZERO_TICKS_PER_MINUTE;
    private static final long WORLDZERO_WINDOW_END_TICKS = 60L * WORLDZERO_TICKS_PER_MINUTE;
    private static final int WORLDZERO_BLACKOUT_DURATION_TICKS = 20;
    private static final String WORLDZERO_SAVE_ID = "worldzero_hotbar_blackout";

    private WorldZeroHotbarBlackoutEvent() {
    }

    @SubscribeEvent
    public static void worldzero$onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.level instanceof ServerLevel level) || level.isClientSide() || level.dimension() != Level.OVERWORLD) {
            return;
        }

        BlackoutSaveData saveData = worldzero$getSaveData(level);
        if (saveData.worldzero$triggered) {
            return;
        }

        long storyTicks = WorldZeroStoryTime.worldzero$getStoryTicks(level);
        if (saveData.worldzero$triggerTick < 0L) {
            if (storyTicks > WORLDZERO_WINDOW_END_TICKS) {
                saveData.worldzero$triggered = true;
            } else {
                saveData.worldzero$triggerTick = worldzero$randomTriggerTick(
                        level,
                        Math.max(storyTicks, WORLDZERO_WINDOW_START_TICKS),
                        WORLDZERO_WINDOW_END_TICKS
                );
            }
            saveData.setDirty();
        }

        if (saveData.worldzero$triggered || storyTicks < saveData.worldzero$triggerTick) {
            return;
        }

        if (storyTicks > WORLDZERO_WINDOW_END_TICKS) {
            saveData.worldzero$triggered = true;
            saveData.setDirty();
            return;
        }

        ServerPlayer targetPlayer = worldzero$findTargetPlayer(level);
        if (targetPlayer == null) {
            return;
        }

        WorldZeroNetwork.sendHotbarBlackout(targetPlayer, WORLDZERO_BLACKOUT_DURATION_TICKS);
        saveData.worldzero$triggered = true;
        saveData.setDirty();
    }

    private static ServerPlayer worldzero$findTargetPlayer(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (WorldZeroStoryTime.worldzero$canReceiveStoryEvent(player)) {
                return player;
            }
        }

        return null;
    }

    private static long worldzero$randomTriggerTick(ServerLevel level, long minTick, long maxTick) {
        if (maxTick <= minTick) {
            return minTick;
        }

        long span = maxTick - minTick + 1L;
        return minTick + (long) (level.random.nextDouble() * span);
    }

    private static BlackoutSaveData worldzero$getSaveData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                BlackoutSaveData::worldzero$load,
                BlackoutSaveData::new,
                WORLDZERO_SAVE_ID
        );
    }

    private static final class BlackoutSaveData extends SavedData {
        private long worldzero$triggerTick = -1L;
        private boolean worldzero$triggered;

        private static BlackoutSaveData worldzero$load(CompoundTag tag) {
            BlackoutSaveData saveData = new BlackoutSaveData();
            saveData.worldzero$triggerTick = tag.contains("trigger_tick") ? tag.getLong("trigger_tick") : -1L;
            saveData.worldzero$triggered = tag.contains("triggered")
                    ? tag.getBoolean("triggered")
                    : tag.getBoolean("completed");
            return saveData;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putLong("trigger_tick", this.worldzero$triggerTick);
            tag.putBoolean("triggered", this.worldzero$triggered);
            tag.putBoolean("completed", this.worldzero$triggered);
            return tag;
        }
    }
}
