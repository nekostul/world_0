package ru.nekostul.worldzero.event.timeloop;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import ru.nekostul.worldzero.event.corruption.WorldZeroCorruptionEvent;
import ru.nekostul.worldzero.event.stalker.WorldZeroStalkerEvent;
import ru.nekostul.worldzero.network.WorldZeroMajorEventPacket;
import ru.nekostul.worldzero.network.WorldZeroNetwork;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class WorldZeroTimeLoopEvent {
    private static final int WORLDZERO_DURATION_TICKS = 28 * 20;
    private static final int WORLDZERO_STALKER_DELAY_TICKS = 12 * 20;
    private static final Map<MinecraftServer, ActiveState> WORLDZERO_STATES = new WeakHashMap<>();

    private WorldZeroTimeLoopEvent() {
    }

    public static boolean worldzero$triggerNow(ServerPlayer player) {
        if (!worldzero$isValidPlayer(player) || player.serverLevel().dimension() != Level.OVERWORLD) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null || worldzero$isActive(server)) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        WORLDZERO_STATES.put(server, new ActiveState(
                player.getUUID(),
                level.getGameTime() + WORLDZERO_DURATION_TICKS,
                level.getGameTime() + WORLDZERO_STALKER_DELAY_TICKS
        ));
        WorldZeroCorruptionEvent.worldzero$triggerNow(player);
        WorldZeroNetwork.sendMajorEvent(player, WorldZeroMajorEventPacket.WORLDZERO_ACTION_TIME_LOOP, WORLDZERO_DURATION_TICKS, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.45F, 1.6F);
        return true;
    }

    public static void worldzero$tick(ServerLevel level) {
        ActiveState state = WORLDZERO_STATES.get(level.getServer());
        if (state == null) {
            return;
        }

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(state.worldzero$playerId);
        if (!worldzero$isValidPlayer(player) || level.getGameTime() >= state.worldzero$endTick) {
            worldzero$stopNow(level.getServer());
            return;
        }

        if (!state.worldzero$stalkerTriggered && level.getGameTime() >= state.worldzero$stalkerTriggerTick) {
            state.worldzero$stalkerTriggered = true;
            WorldZeroStalkerEvent.worldzero$triggerNow(player);
        }
    }

    public static boolean worldzero$isActive(MinecraftServer server) {
        return server != null && WORLDZERO_STATES.containsKey(server);
    }

    public static boolean worldzero$stopNow(MinecraftServer server) {
        ActiveState state = WORLDZERO_STATES.remove(server);
        if (server == null || state == null) {
            return false;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(state.worldzero$playerId);
        if (player != null && !WorldZeroCorruptionEvent.worldzero$isActive(server)) {
            WorldZeroNetwork.sendMajorEvent(player, WorldZeroMajorEventPacket.WORLDZERO_ACTION_CLEAR_ALL, 1, 0);
        }
        return true;
    }

    private static boolean worldzero$isValidPlayer(ServerPlayer player) {
        return player != null && player.isAlive() && !player.isSpectator();
    }

    private static final class ActiveState {
        private final UUID worldzero$playerId;
        private final long worldzero$endTick;
        private final long worldzero$stalkerTriggerTick;
        private boolean worldzero$stalkerTriggered;

        private ActiveState(UUID playerId, long endTick, long stalkerTriggerTick) {
            this.worldzero$playerId = playerId;
            this.worldzero$endTick = endTick;
            this.worldzero$stalkerTriggerTick = stalkerTriggerTick;
        }
    }
}
