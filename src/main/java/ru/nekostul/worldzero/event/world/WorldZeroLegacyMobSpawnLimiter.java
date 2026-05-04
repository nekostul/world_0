package ru.nekostul.worldzero.event.world;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;

import java.util.Set;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroLegacyMobSpawnLimiter {
    private static final String WORLDZERO_MINECRAFT_NAMESPACE = "minecraft";
    private static final Set<EntityType<?>> WORLDZERO_ALLOWED_MOBS = Set.of(
            EntityType.PIG,
            EntityType.COW,
            EntityType.SHEEP,
            EntityType.CHICKEN,
            EntityType.SQUID,
            EntityType.BAT,
            EntityType.MOOSHROOM,
            EntityType.ZOMBIE,
            EntityType.SKELETON,
            EntityType.SPIDER,
            EntityType.CREEPER,
            EntityType.ENDERMAN,
            EntityType.SLIME,
            EntityType.CAVE_SPIDER,
            EntityType.SILVERFISH,
            EntityType.GHAST,
            EntityType.BLAZE,
            EntityType.MAGMA_CUBE,
            EntityType.VILLAGER,
            EntityType.IRON_GOLEM,
            EntityType.SNOW_GOLEM,
            EntityType.WOLF,
            EntityType.OCELOT,
            EntityType.WITCH,
            EntityType.GIANT
    );

    private WorldZeroLegacyMobSpawnLimiter() {
    }

    @SubscribeEvent
    public static void worldzero$onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (worldzero$shouldBlock(event.getEntity().getType())) {
            event.setSpawnCancelled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()) {
            return;
        }

        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }

        if (worldzero$shouldBlock(mob.getType())) {
            event.setCanceled(true);
        }
    }

    private static boolean worldzero$shouldBlock(EntityType<?> type) {
        if (type == null || WORLDZERO_ALLOWED_MOBS.contains(type)) {
            return false;
        }

        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (typeId == null || !WORLDZERO_MINECRAFT_NAMESPACE.equals(typeId.getNamespace())) {
            return false;
        }

        return true;
    }
}
