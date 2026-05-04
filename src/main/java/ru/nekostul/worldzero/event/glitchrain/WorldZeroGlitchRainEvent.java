package ru.nekostul.worldzero.event.glitchrain;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import ru.nekostul.worldzero.network.WorldZeroMajorEventPacket;
import ru.nekostul.worldzero.network.WorldZeroNetwork;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class WorldZeroGlitchRainEvent {
    private static final int WORLDZERO_DURATION_TICKS = 36 * 20;
    private static final Map<MinecraftServer, ActiveState> WORLDZERO_STATES = new WeakHashMap<>();

    private WorldZeroGlitchRainEvent() {
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
        if (!level.isRaining()) {
            level.setWeatherParameters(0, WORLDZERO_DURATION_TICKS + 200, true, level.isThundering());
        }

        WORLDZERO_STATES.put(server, new ActiveState(player.getUUID(), level.getGameTime() + WORLDZERO_DURATION_TICKS));
        WorldZeroNetwork.sendMajorEvent(
                player,
                WorldZeroMajorEventPacket.WORLDZERO_ACTION_GLITCH_RAIN,
                WORLDZERO_DURATION_TICKS,
                level.random.nextInt()
        );
        level.playSound(null, player.blockPosition(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.35F, 1.7F);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_STARE, SoundSource.AMBIENT, 0.2F, 0.48F);
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

        if (level.getGameTime() % 3L == 0L) {
            level.sendParticles(
                    ParticleTypes.PORTAL,
                    player.getX(),
                    player.getY() + 3.2D,
                    player.getZ(),
                    16,
                    6.0D,
                    1.8D,
                    6.0D,
                    0.02D
            );
            level.sendParticles(
                    ParticleTypes.END_ROD,
                    player.getX(),
                    player.getY() + 2.0D,
                    player.getZ(),
                    5,
                    4.5D,
                    1.4D,
                    4.5D,
                    0.01D
            );
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
        if (player != null) {
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

        private ActiveState(UUID playerId, long endTick) {
            this.worldzero$playerId = playerId;
            this.worldzero$endTick = endTick;
        }
    }
}
