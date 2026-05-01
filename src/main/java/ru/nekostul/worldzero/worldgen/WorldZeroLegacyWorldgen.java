package ru.nekostul.worldzero.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import ru.nekostul.worldzero.WorldZeroMod;

public final class WorldZeroLegacyWorldgen {
    public static final DeferredRegister<Codec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(
            Registries.BIOME_SOURCE,
            WorldZeroMod.MOD_ID
    );
    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(
            Registries.CHUNK_GENERATOR,
            WorldZeroMod.MOD_ID
    );

    public static final RegistryObject<Codec<WorldZeroLegacyBiomeSource>> LEGACY_BIOME_SOURCE = BIOME_SOURCES.register(
            "legacy_biome_source",
            () -> WorldZeroLegacyBiomeSource.CODEC
    );
    public static final RegistryObject<Codec<WorldZeroLegacyChunkGenerator>> LEGACY_CHUNK_GENERATOR = CHUNK_GENERATORS.register(
            "legacy_chunk_generator",
            () -> WorldZeroLegacyChunkGenerator.CODEC
    );

    private WorldZeroLegacyWorldgen() {
    }

    public static void worldzero$register(IEventBus eventBus) {
        BIOME_SOURCES.register(eventBus);
        CHUNK_GENERATORS.register(eventBus);
    }
}
