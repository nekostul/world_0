package ru.nekostul.worldzero.event.ambient;

import net.minecraft.server.level.ServerLevel;
import ru.nekostul.worldzero.event.horror.WorldZeroHorrorEventSystem;
import ru.nekostul.worldzero.event.major.WorldZeroMajorEventSystem;

public final class WorldZeroAmbientSoundEvent {
    private WorldZeroAmbientSoundEvent() {
    }

    public static boolean worldzero$isMajorEventStartBlocked(ServerLevel level) {
        return WorldZeroHorrorEventSystem.worldzero$isMinorAnomalyActive(level)
                || WorldZeroMajorEventSystem.worldzero$isMajorEventActive(level.getServer())
                || ru.nekostul.worldzero.event.horror.WorldZeroBlackEchoJumpscareEvent.worldzero$isActive(level.getServer())
                || ru.nekostul.worldzero.event.horror.WorldZeroTrapEvent.worldzero$isActive(level.getServer())
                || ru.nekostul.worldzero.event.horror.WorldZeroNightDarknessEvent.worldzero$isActive(level.getServer());
    }

    public static void worldzero$notifyMajorEventStarted(ServerLevel level) {
    }
}
