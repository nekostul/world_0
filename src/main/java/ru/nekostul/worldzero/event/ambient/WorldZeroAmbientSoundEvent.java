package ru.nekostul.worldzero;

import net.minecraft.server.level.ServerLevel;

public final class WorldZeroAmbientSoundEvent {
    private WorldZeroAmbientSoundEvent() {
    }

    public static boolean worldzero$isMajorEventStartBlocked(ServerLevel level) {
        return WorldZeroHorrorEventSystem.worldzero$isMinorAnomalyActive(level)
                || WorldZeroMajorEventSystem.worldzero$isMajorEventActive(level.getServer());
    }

    public static void worldzero$notifyMajorEventStarted(ServerLevel level) {
    }
}
