package ru.nekostul.worldzero;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroMobFadeOut {
    private static final long WORLDZERO_MOB_FADE_END_TICKS = 180L * 60L * 20L;
    private static final long WORLDZERO_FADE_CHECK_INTERVAL_TICKS = 20L;
    private static final AABB WORLDZERO_ENTITY_SCAN_AABB = new AABB(
            -30_000_000.0D,
            -2_048.0D,
            -30_000_000.0D,
            30_000_000.0D,
            4_096.0D,
            30_000_000.0D
    );

    private WorldZeroMobFadeOut() {
    }

    @SubscribeEvent
    public static void worldzero$onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.level instanceof ServerLevel serverLevel) || serverLevel.isClientSide()) {
            return;
        }

        if (serverLevel.dimension() == WorldZeroVoidPortalDimension.WORLDZERO_VOIDPORTAL_LEVEL) {
            return;
        }

        long worldTicks = worldzero$worldTicks(serverLevel);
        if (worldTicks >= WORLDZERO_MOB_FADE_END_TICKS && worldTicks % WORLDZERO_FADE_CHECK_INTERVAL_TICKS == 0L) {
            worldzero$removeAllMobs(serverLevel);
        }
    }

    private static long worldzero$worldTicks(ServerLevel serverLevel) {
        ServerLevel overworld = serverLevel.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null) {
            return serverLevel.getGameTime();
        }

        return WorldZeroHorrorEventSystem.worldzero$getWorldTicks(overworld);
    }

    private static void worldzero$removeAllMobs(ServerLevel serverLevel) {
        for (Mob mob : serverLevel.getEntitiesOfClass(
                Mob.class,
                WORLDZERO_ENTITY_SCAN_AABB,
                WorldZeroMobFadeOut::worldzero$shouldFadeMob
        )) {
            mob.discard();
        }
    }

    private static boolean worldzero$shouldFadeMob(Mob mob) {
        EntityType<?> type = mob.getType();
        return type != WorldZeroEntities.WORLDZERO_ECHO.get()
                && type != WorldZeroEntities.WORLDZERO_HOUSE_ECHO.get()
                && type != WorldZeroEntities.WORLDZERO_BLACK_ECHO.get()
                && type != WorldZeroEntities.WORLDZERO_BLAK_ECHO.get();
    }
}
