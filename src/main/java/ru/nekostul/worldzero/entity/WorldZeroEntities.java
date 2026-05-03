package ru.nekostul.worldzero.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.nekostul.worldzero.WorldZeroMod;

public final class WorldZeroEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(
            ForgeRegistries.ENTITY_TYPES,
            WorldZeroMod.MOD_ID
    );

    public static final RegistryObject<EntityType<WorldZeroEchoEntity>> WORLDZERO_ECHO = ENTITY_TYPES.register(
            "worldzero_echo",
            () -> EntityType.Builder.of(WorldZeroEchoEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build(WorldZeroMod.MOD_ID + ":worldzero_echo")
    );

    public static final RegistryObject<EntityType<WorldZeroEchoEntity>> WORLDZERO_BLACK_ECHO = ENTITY_TYPES.register(
            "worldzero_black_echo",
            () -> EntityType.Builder.of(WorldZeroEchoEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build(WorldZeroMod.MOD_ID + ":worldzero_black_echo")
    );

    public static final RegistryObject<EntityType<WorldZeroEchoEntity>> WORLDZERO_BLAK_ECHO = ENTITY_TYPES.register(
            "blak_echo",
            () -> EntityType.Builder.of(WorldZeroEchoEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build(WorldZeroMod.MOD_ID + ":blak_echo")
    );

    public static final RegistryObject<EntityType<WorldZeroHouseEchoEntity>> WORLDZERO_HOUSE_ECHO = ENTITY_TYPES.register(
            "worldzero_house_echo",
            () -> EntityType.Builder.of(WorldZeroHouseEchoEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .noSave()
                    .build(WorldZeroMod.MOD_ID + ":worldzero_house_echo")
    );

    private WorldZeroEntities() {
    }
}
