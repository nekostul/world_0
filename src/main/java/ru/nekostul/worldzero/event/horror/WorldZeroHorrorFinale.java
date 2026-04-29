package ru.nekostul.worldzero;

import net.minecraft.server.level.ServerLevel;

public final class WorldZeroHorrorFinale {
    public static final long WORLDZERO_END_TICKS = 180L * 60L * 20L;

    private WorldZeroHorrorFinale() {
    }

    public static boolean worldzero$isEndReached(long worldTicks) {
        return worldTicks >= WORLDZERO_END_TICKS;
    }

    public static void worldzero$tickEnd(ServerLevel level) {
        if (level == null || level.getServer() == null) {
            return;
        }

        WorldZeroMinorAnomalies.worldzero$cancelAll(level.getServer());
    }
}
