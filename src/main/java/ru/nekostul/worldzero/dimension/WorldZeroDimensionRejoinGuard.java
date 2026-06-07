package ru.nekostul.worldzero.dimension;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bugfix guard: if a player logs out while inside one of the mod's custom dimensions
 * ({@code house}, {@code house_bad} or {@code koridor}), they are sent back out on their
 * next login using the mod's existing return logic. The teleport happens only after the
 * relog, on the player's first server tick, so the existing in-dimension behaviour, timers
 * and saved progress stay exactly as they were.
 */
@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroDimensionRejoinGuard {
    private static final Set<UUID> WORLDZERO_PENDING_RETURN = ConcurrentHashMap.newKeySet();

    private WorldZeroDimensionRejoinGuard() {
    }

    @SubscribeEvent
    public static void worldzero$onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (worldzero$isCustomDimension(player.serverLevel().dimension())) {
            WORLDZERO_PENDING_RETURN.add(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void worldzero$onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        if (!WORLDZERO_PENDING_RETURN.remove(player.getUUID())) {
            return;
        }

        worldzero$returnFromDimension(player);
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_PENDING_RETURN.clear();
    }

    private static void worldzero$returnFromDimension(ServerPlayer player) {
        ResourceKey<Level> dimension = player.serverLevel().dimension();
        if (dimension == WorldZeroHouseDimension.WORLDZERO_HOUSE_LEVEL) {
            WorldZeroHouseDimension.worldzero$returnPlayerFromHouse(player);
        } else if (dimension == WorldZeroHouseBadDimension.WORLDZERO_HOUSE_BAD_LEVEL) {
            WorldZeroHouseBadDimension.worldzero$returnPlayerFromHouseBad(player);
        } else if (dimension == WorldZeroKoridorDimension.WORLDZERO_KORIDOR_LEVEL) {
            WorldZeroKoridorDimension.worldzero$returnPlayerFromKoridor(player);
        }
    }

    private static boolean worldzero$isCustomDimension(ResourceKey<Level> dimension) {
        return dimension == WorldZeroHouseDimension.WORLDZERO_HOUSE_LEVEL
                || dimension == WorldZeroHouseBadDimension.WORLDZERO_HOUSE_BAD_LEVEL
                || dimension == WorldZeroKoridorDimension.WORLDZERO_KORIDOR_LEVEL;
    }
}
