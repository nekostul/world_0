package ru.nekostul.worldzero;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(WorldZeroMod.MOD_ID)
public final class WorldZeroMod {
    public static final String MOD_ID = "worldzero";

    public WorldZeroMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, WorldZeroConfig.SPEC);
        WorldZeroEntities.ENTITY_TYPES.register(FMLJavaModLoadingContext.get().getModEventBus());
        WorldZeroNetwork.register();
    }
}
