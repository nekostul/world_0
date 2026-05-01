package ru.nekostul.worldzero;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroServerFreezeController {
    private static final Map<MinecraftServer, Map<UUID, FreezeState>> WORLDZERO_FREEZES = new WeakHashMap<>();

    private WorldZeroServerFreezeController() {
    }

    public static void worldzero$startFreeze(
            ServerPlayer player,
            int durationTicks,
            float forcedYaw,
            float forcedPitch
    ) {
        if (player == null || durationTicks <= 0) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        ServerLevel level = player.serverLevel();
        long endTick = worldzero$serverTick(server) + durationTicks;
        Map<UUID, FreezeState> freezes = WORLDZERO_FREEZES.computeIfAbsent(server, ignored -> new HashMap<>());
        FreezeState state = freezes.get(player.getUUID());
        if (state == null || !state.worldzero$dimension.equals(level.dimension())) {
            freezes.put(
                    player.getUUID(),
                    new FreezeState(
                            level.dimension(),
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            forcedYaw,
                            forcedPitch,
                            endTick
                    )
            );
            return;
        }

        state.worldzero$forcedYaw = forcedYaw;
        state.worldzero$forcedPitch = forcedPitch;
        state.worldzero$endTick = Math.max(state.worldzero$endTick, endTick);
    }

    public static void worldzero$stopFreeze(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }

        Map<UUID, FreezeState> freezes = WORLDZERO_FREEZES.get(player.getServer());
        if (freezes != null) {
            freezes.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void worldzero$onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.level instanceof ServerLevel level) || level.isClientSide() || level.dimension() != Level.OVERWORLD) {
            return;
        }

        MinecraftServer server = level.getServer();
        Map<UUID, FreezeState> freezes = WORLDZERO_FREEZES.get(server);
        if (freezes == null || freezes.isEmpty()) {
            return;
        }

        long gameTick = worldzero$serverTick(server);
        freezes.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            FreezeState state = entry.getValue();
            if (player == null || !player.isAlive() || !state.worldzero$dimension.equals(player.serverLevel().dimension())) {
                return true;
            }

            if (gameTick >= state.worldzero$endTick) {
                return true;
            }

            worldzero$applyFreeze(player, state);
            return false;
        });
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_FREEZES.remove(event.getServer());
    }

    private static void worldzero$applyFreeze(ServerPlayer player, FreezeState state) {
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.setSprinting(false);
        player.fallDistance = 0.0F;

        if (Float.isNaN(state.worldzero$forcedYaw) || Float.isNaN(state.worldzero$forcedPitch)) {
            player.teleportTo(state.worldzero$x, state.worldzero$y, state.worldzero$z);
            return;
        }

        player.teleportTo(
                player.serverLevel(),
                state.worldzero$x,
                state.worldzero$y,
                state.worldzero$z,
                state.worldzero$forcedYaw,
                state.worldzero$forcedPitch
        );
    }

    private static long worldzero$serverTick(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null) {
            return overworld.getGameTime();
        }

        return 0L;
    }

    private static final class FreezeState {
        private final ResourceKey<Level> worldzero$dimension;
        private final double worldzero$x;
        private final double worldzero$y;
        private final double worldzero$z;
        private float worldzero$forcedYaw;
        private float worldzero$forcedPitch;
        private long worldzero$endTick;

        private FreezeState(
                ResourceKey<Level> dimension,
                double x,
                double y,
                double z,
                float forcedYaw,
                float forcedPitch,
                long endTick
        ) {
            this.worldzero$dimension = dimension;
            this.worldzero$x = x;
            this.worldzero$y = y;
            this.worldzero$z = z;
            this.worldzero$forcedYaw = forcedYaw;
            this.worldzero$forcedPitch = forcedPitch;
            this.worldzero$endTick = endTick;
        }
    }
}
