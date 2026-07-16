package ru.nekostul.worldzero.event.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;
import ru.nekostul.worldzero.network.WorldZeroNetwork;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroEndOverridesEvent {
    private static final AABB WORLDZERO_END_SCAN_AABB = new AABB(
            -30_000_000.0D,
            -2_048.0D,
            -30_000_000.0D,
            30_000_000.0D,
            4_096.0D,
            30_000_000.0D
    );
    private static final double WORLDZERO_DRAGON_X = 0.5D;
    private static final double WORLDZERO_DRAGON_Y = 90.0D;
    private static final double WORLDZERO_DRAGON_Z = 0.5D;
    private static final int WORLDZERO_RETURN_BLACK_TICKS = 40;
    private static final int WORLDZERO_RETURN_DELAY_TICKS = 5;
    private static final int WORLDZERO_DRAGON_RESCAN_INTERVAL_TICKS = 40;
    private static final Map<MinecraftServer, SessionState> WORLDZERO_SESSION_STATES = new WeakHashMap<>();

    private WorldZeroEndOverridesEvent() {
    }

    @SubscribeEvent
    public static void worldzero$onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.level instanceof ServerLevel level) || level.isClientSide() || level.dimension() != Level.END) {
            return;
        }

        MinecraftServer server = level.getServer();
        SessionState state = WORLDZERO_SESSION_STATES.computeIfAbsent(server, ignored -> new SessionState());

        Iterator<UUID> dragonIterator = state.worldzero$trackedDragonIds.iterator();
        while (dragonIterator.hasNext()) {
            UUID dragonId = dragonIterator.next();
            if (!(level.getEntity(dragonId) instanceof EnderDragon dragon) || !dragon.isAlive()) {
                dragonIterator.remove();
                continue;
            }

            worldzero$freezeDragon(dragon);
        }

        if (level.getGameTime() >= state.worldzero$nextDragonRescanTick) {
            for (EnderDragon dragon : level.getEntitiesOfClass(EnderDragon.class, WORLDZERO_END_SCAN_AABB, candidate -> candidate != null && candidate.isAlive())) {
                worldzero$freezeDragon(dragon);
                state.worldzero$trackedDragonIds.add(dragon.getUUID());
            }
            state.worldzero$nextDragonRescanTick = level.getGameTime() + WORLDZERO_DRAGON_RESCAN_INTERVAL_TICKS;
        }

        if (state.worldzero$pendingReturns.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, Long>> iterator = state.worldzero$pendingReturns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (level.getGameTime() < entry.getValue()) {
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null && player.isAlive() && !player.isSpectator() && player.serverLevel().dimension() == Level.END) {
                worldzero$returnPlayerToRespawn(server, player);
            }
            iterator.remove();
        }
    }

    @SubscribeEvent
    public static void worldzero$onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getEntity().getType() == EntityType.ENDERMAN
                && event.getEntity().level() instanceof ServerLevel level
                && level.dimension() == Level.END) {
            event.setSpawnCancelled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        if (event.getEntity() instanceof EnderMan && event.getLevel().dimension() == Level.END) {
            event.setCanceled(true);
            return;
        }

        if (event.getEntity() instanceof EnderDragon dragon && event.getLevel() instanceof ServerLevel level && level.dimension() == Level.END) {
            worldzero$freezeDragon(dragon);
            MinecraftServer server = level.getServer();
            if (server != null) {
                WORLDZERO_SESSION_STATES
                        .computeIfAbsent(server, ignored -> new SessionState())
                        .worldzero$trackedDragonIds
                        .add(dragon.getUUID());
            }
        }
    }

    @SubscribeEvent
    public static void worldzero$onLivingHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof EnderDragon dragon && dragon.level().dimension() == Level.END) {
            event.setAmount(0.0F);
        }
    }

    @SubscribeEvent
    public static void worldzero$onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)
                || !(dragon.level() instanceof ServerLevel level)
                || level.dimension() != Level.END) {
            return;
        }

        MinecraftServer server = level.getServer();
        SessionState state = WORLDZERO_SESSION_STATES.computeIfAbsent(server, ignored -> new SessionState());
        state.worldzero$trackedDragonIds.remove(dragon.getUUID());
        List<ServerPlayer> players = new ArrayList<>(level.players());
        for (ServerPlayer player : players) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }

            WorldZeroNetwork.sendEndReturn(player, WORLDZERO_RETURN_BLACK_TICKS);
            state.worldzero$pendingReturns.put(player.getUUID(), level.getGameTime() + WORLDZERO_RETURN_DELAY_TICKS);
        }
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_SESSION_STATES.remove(event.getServer());
    }

    private static void worldzero$freezeDragon(EnderDragon dragon) {
        dragon.setNoAi(true);
        dragon.setTarget(null);
        dragon.moveTo(WORLDZERO_DRAGON_X, WORLDZERO_DRAGON_Y, WORLDZERO_DRAGON_Z, 0.0F, 0.0F);
        dragon.setDeltaMovement(Vec3.ZERO);
        dragon.hurtMarked = true;
    }

    private static void worldzero$returnPlayerToRespawn(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) {
            return;
        }

        ServerLevel targetLevel = null;
        double targetX;
        double targetY;
        double targetZ;
        float targetYaw;
        float targetPitch;

        BlockPos respawnPos = player.getRespawnPosition();
        ServerLevel respawnLevel = server.getLevel(player.getRespawnDimension());
        if (respawnPos != null && respawnLevel != null) {
            targetLevel = respawnLevel;
            targetX = respawnPos.getX() + 0.5D;
            targetY = respawnPos.getY();
            targetZ = respawnPos.getZ() + 0.5D;
            targetYaw = player.getRespawnAngle();
            targetPitch = 0.0F;
        } else {
            targetLevel = server.overworld();
            if (targetLevel == null) {
                return;
            }

            BlockPos sharedSpawn = targetLevel.getSharedSpawnPos();
            targetX = sharedSpawn.getX() + 0.5D;
            targetY = sharedSpawn.getY();
            targetZ = sharedSpawn.getZ() + 0.5D;
            targetYaw = targetLevel.getSharedSpawnAngle();
            targetPitch = 0.0F;
        }

        player.teleportTo(targetLevel, targetX, targetY, targetZ, targetYaw, targetPitch);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
    }

    private static final class SessionState {
        private final Map<UUID, Long> worldzero$pendingReturns = new HashMap<>();
        private final Set<UUID> worldzero$trackedDragonIds = new HashSet<>();
        private long worldzero$nextDragonRescanTick;
    }
}
