package ru.nekostul.worldzero.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import ru.nekostul.worldzero.WorldZeroMod;

import java.util.function.Function;

public final class WorldZeroLegacyWorldFactory {
    public static final ResourceKey<DimensionType> WORLDZERO_LEGACY_OVERWORLD = ResourceKey.create(
            Registries.DIMENSION_TYPE,
            new ResourceLocation(WorldZeroMod.MOD_ID, "legacy_overworld")
    );

    private WorldZeroLegacyWorldFactory() {
    }

    public static WorldDimensions worldzero$createLegacyWorldDimensions(
            RegistryAccess registryAccess,
            long seed,
            Function<RegistryAccess, WorldDimensions> dimensionsFactory
    ) {
        WorldDimensions baseDimensions = dimensionsFactory.apply(registryAccess);
        Registry<Biome> biomes = registryAccess.registryOrThrow(Registries.BIOME);
        Registry<DimensionType> dimensionTypes = registryAccess.registryOrThrow(Registries.DIMENSION_TYPE);
        WorldZeroLegacyBiomeSource biomeSource = new WorldZeroLegacyBiomeSource(seed, biomes.asLookup());
        WorldZeroLegacyChunkGenerator chunkGenerator = new WorldZeroLegacyChunkGenerator(seed, biomeSource);
        Holder<DimensionType> overworldType = dimensionTypes.getHolder(WORLDZERO_LEGACY_OVERWORLD)
                .orElseGet(() -> dimensionTypes.getHolderOrThrow(BuiltinDimensionTypes.OVERWORLD));
        Registry<LevelStem> stems = WorldDimensions.withOverworld(baseDimensions.dimensions(), overworldType, chunkGenerator);
        return new WorldDimensions(stems);
    }
}
