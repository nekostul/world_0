package ru.nekostul.worldzero.entity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import ru.nekostul.worldzero.dimension.WorldZeroVoidPortalDimension;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class WorldZeroEchoPresenceTracker {
    private static final Map<MinecraftServer, SessionState> WORLDZERO_SERVER_STATES = new WeakHashMap<>();

    private WorldZeroEchoPresenceTracker() {
    }

    public static void worldzero$trackEcho(WorldZeroEchoEntity entity) {
        SessionState state = worldzero$getOrCreateState(entity.level());
        if (state == null) {
            return;
        }

        UUID entityId = entity.getUUID();
        if (entity.getType() == WorldZeroEntities.WORLDZERO_ECHO.get()) {
            state.worldzero$normalEchoIds.add(entityId);
        } else if (entity.getType() == WorldZeroEntities.WORLDZERO_BLACK_ECHO.get()) {
            state.worldzero$blackEchoIds.add(entityId);
        }
    }

    public static void worldzero$untrackEcho(WorldZeroEchoEntity entity) {
        SessionState state = worldzero$getState(entity.level());
        if (state == null) {
            return;
        }

        UUID entityId = entity.getUUID();
        state.worldzero$normalEchoIds.remove(entityId);
        state.worldzero$blackEchoIds.remove(entityId);
    }

    public static void worldzero$trackHouseEcho(WorldZeroHouseEchoEntity entity) {
        SessionState state = worldzero$getOrCreateState(entity.level());
        if (state == null) {
            return;
        }

        state.worldzero$houseEchoIds.add(entity.getUUID());
    }

    public static void worldzero$untrackHouseEcho(WorldZeroHouseEchoEntity entity) {
        SessionState state = worldzero$getState(entity.level());
        if (state == null) {
            return;
        }

        state.worldzero$houseEchoIds.remove(entity.getUUID());
    }

    public static boolean worldzero$hasNormalEcho(MinecraftServer server) {
        SessionState state = WORLDZERO_SERVER_STATES.get(server);
        return state != null && !state.worldzero$normalEchoIds.isEmpty();
    }

    public static boolean worldzero$hasEchoOrBlackEcho(MinecraftServer server) {
        SessionState state = WORLDZERO_SERVER_STATES.get(server);
        return state != null
                && (!state.worldzero$normalEchoIds.isEmpty() || !state.worldzero$blackEchoIds.isEmpty());
    }

    public static boolean worldzero$hasAnyEcho(MinecraftServer server) {
        SessionState state = WORLDZERO_SERVER_STATES.get(server);
        return state != null
                && (!state.worldzero$normalEchoIds.isEmpty()
                || !state.worldzero$blackEchoIds.isEmpty()
                || !state.worldzero$houseEchoIds.isEmpty());
    }

    public static void worldzero$clear(MinecraftServer server) {
        if (server == null) {
            return;
        }

        WORLDZERO_SERVER_STATES.remove(server);
    }

    @Nullable
    private static SessionState worldzero$getOrCreateState(Level level) {
        MinecraftServer server = worldzero$getServer(level);
        return server != null ? WORLDZERO_SERVER_STATES.computeIfAbsent(server, ignored -> new SessionState()) : null;
    }

    @Nullable
    private static SessionState worldzero$getState(Level level) {
        MinecraftServer server = worldzero$getServer(level);
        return server != null ? WORLDZERO_SERVER_STATES.get(server) : null;
    }

    @Nullable
    private static MinecraftServer worldzero$getServer(Level level) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }

        if (serverLevel.dimension() == WorldZeroVoidPortalDimension.WORLDZERO_VOIDPORTAL_LEVEL) {
            return null;
        }

        return serverLevel.getServer();
    }

    private static final class SessionState {
        private final Set<UUID> worldzero$normalEchoIds = new HashSet<>();
        private final Set<UUID> worldzero$blackEchoIds = new HashSet<>();
        private final Set<UUID> worldzero$houseEchoIds = new HashSet<>();
    }
}
