package ru.nekostul.worldzero;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class WorldZeroSwarmEvent {
    private static final int WORLDZERO_DURATION_TICKS = 45 * 20;
    private static final Map<MinecraftServer, ActiveState> WORLDZERO_STATES = new WeakHashMap<>();

    private WorldZeroSwarmEvent() {
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
        WORLDZERO_STATES.put(server, new ActiveState(player.getUUID(), level.getGameTime() + WORLDZERO_DURATION_TICKS));
        WorldZeroNetwork.sendMajorEvent(player, WorldZeroMajorEventPacket.WORLDZERO_ACTION_SWARM, WORLDZERO_DURATION_TICKS, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.BAT_AMBIENT, SoundSource.AMBIENT, 0.5F, 0.65F);
        level.playSound(null, player.blockPosition(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.8F, 0.5F);
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

        long gameTime = level.getGameTime();
        for (int index = 0; index < 6; index++) {
            double angle = (gameTime * 0.18D) + index * Math.PI * 2.0D / 6.0D;
            double radius = 1.9D + (index % 3) * 0.65D;
            double y = player.getY() + 0.5D + (index % 4) * 0.35D + Math.sin(gameTime * 0.11D + index) * 0.25D;
            level.sendParticles(
                    ParticleTypes.SMOKE,
                    player.getX() + Math.cos(angle) * radius,
                    y,
                    player.getZ() + Math.sin(angle) * radius,
                    2,
                    0.08D,
                    0.08D,
                    0.08D,
                    0.02D
            );
        }
        if (gameTime % 55L == 0L) {
            level.playSound(null, player.blockPosition(), SoundEvents.BAT_AMBIENT, SoundSource.AMBIENT, 0.35F, Mth.nextFloat(level.random, 0.45F, 0.75F));
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
