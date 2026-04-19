package ru.nekostul.worldzero;

import net.minecraft.server.level.ServerLevel;

public final class WorldZeroAmbientSoundEvent {
    private WorldZeroAmbientSoundEvent() {
    }

    public static boolean worldzero$isMajorEventStartBlocked(ServerLevel level) {
        return false;
    }

    public static void worldzero$notifyMajorEventStarted(ServerLevel level) {
    }
}
