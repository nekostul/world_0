package ru.nekostul.worldzero.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;

import java.util.List;
import java.util.stream.Stream;

public final class gWorldZeroLegacyBiomeSource extends BiomeSource {
    public static final Codec<WorldZeroLegacyBiomeSource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("seed").forGetter(WorldZeroLegacyBiomeSource::worldzero$getSeed),
            RegistryOps.retrieveGetter(Registries.BIOME)
    ).apply(instance, instance.stable(WorldZeroLegacyBiomeSource::new)));

    private static final double WORLDZERO_LAND_SCALE = 620.0D;
    private static final double WORLDZERO_LAND_DETAIL_SCALE = 260.0D;
    private static final double WORLDZERO_REGION_SCALE = 540.0D;
    private static final double WORLDZERO_REGION_DETAIL_SCALE = 220.0D;
    private static final double WORLDZERO_VARIANT_SCALE = 340.0D;
    private static final double WORLDZERO_VARIANT_DETAIL_SCALE = 150.0D;
    private static final double WORLDZERO_RIVER_SCALE = 170.0D;
    private static final double WORLDZERO_RIVER_DETAIL_SCALE = 86.0D;
    private static final double WORLDZERO_WARP_SCALE = 420.0D;
    private static final double WORLDZERO_WARP_STRENGTH = 46.0D;

    private final long worldzero$seed;
    private final Holder<Biome> worldzero$ocean;
    private final Holder<Biome> worldzero$plains;
    private final Holder<Biome> worldzero$forest;
    private final Holder<Biome> worldzero$birchForest;
    private final Holder<Biome> worldzero$darkForest;
    private final Holder<Biome> worldzero$jungle;
    private final Holder<Biome> worldzero$desert;
    private final Holder<Biome> worldzero$savanna;
    private final Holder<Biome> worldzero$taiga;
    private final Holder<Biome> worldzero$snowyPlains;
    private final Holder<Biome> worldzero$river;
    private final Holder<Biome> worldzero$beach;
    private final Holder<Biome> worldzero$badlands;
    private final List<Holder<Biome>> worldzero$possibleBiomes;
    private final PerlinNoise worldzero$landNoise;
    private final PerlinNoise worldzero$landDetailNoise;
    private final PerlinNoise worldzero$regionNoise;
    private final PerlinNoise worldzero$regionDetailNoise;
    private final PerlinNoise worldzero$variantNoise;
    private final PerlinNoise worldzero$variantDetailNoise;
    private final PerlinNoise worldzero$riverNoise;
    private final PerlinNoise worldzero$riverDetailNoise;
    private final PerlinNoise worldzero$warpXNoise;
    private final PerlinNoise worldzero$warpZNoise;

    public WorldZeroLegacyBiomeSource(long seed, HolderGetter<Biome> biomeGetter) {
        this.worldzero$seed = seed;
        this.worldzero$ocean = biomeGetter.getOrThrow(Biomes.OCEAN);
        this.worldzero$plains = biomeGetter.getOrThrow(Biomes.PLAINS);
        this.worldzero$forest = biomeGetter.getOrThrow(Biomes.FOREST);
        this.worldzero$birchForest = biomeGetter.getOrThrow(Biomes.BIRCH_FOREST);
        this.worldzero$darkForest = biomeGetter.getOrThrow(Biomes.DARK_FOREST);
        this.worldzero$jungle = biomeGetter.getOrThrow(Biomes.JUNGLE);
        this.worldzero$desert = biomeGetter.getOrThrow(Biomes.DESERT);
        this.worldzero$savanna = biomeGetter.getOrThrow(Biomes.SAVANNA);
        this.worldzero$taiga = biomeGetter.getOrThrow(Biomes.TAIGA);
        this.worldzero$snowyPlains = biomeGetter.getOrThrow(Biomes.SNOWY_PLAINS);
        this.worldzero$river = biomeGetter.getOrThrow(Biomes.RIVER);
        this.worldzero$beach = biomeGetter.getOrThrow(Biomes.BEACH);
        this.worldzero$badlands = biomeGetter.getOrThrow(Biomes.BADLANDS);
        this.worldzero$possibleBiomes = List.of(
                this.worldzero$ocean,
                this.worldzero$plains,
                this.worldzero$forest,
                this.worldzero$birchForest,
                this.worldzero$darkForest,
                this.worldzero$jungle,
                this.worldzero$desert,
                this.worldzero$savanna,
                this.worldzero$taiga,
                this.worldzero$snowyPlains,
                this.worldzero$river,
                this.worldzero$beach,
                this.worldzero$badlands
        );
        this.worldzero$landNoise = PerlinNoise.create(new LegacyRandomSource(seed + 11L), List.of(-4, -3, -2, -1));
        this.worldzero$landDetailNoise = PerlinNoise.create(new LegacyRandomSource(seed + 13L), List.of(-2, -1, 0));
        this.worldzero$regionNoise = PerlinNoise.create(new LegacyRandomSource(seed + 17L), List.of(-4, -3, -2, -1));
        this.worldzero$regionDetailNoise = PerlinNoise.create(new LegacyRandomSource(seed + 19L), List.of(-2, -1, 0));
        this.worldzero$variantNoise = PerlinNoise.create(new LegacyRandomSource(seed + 23L), List.of(-3, -2, -1, 0));
        this.worldzero$variantDetailNoise = PerlinNoise.create(new LegacyRandomSource(seed + 29L), List.of(-1, 0));
        this.worldzero$riverNoise = PerlinNoise.create(new LegacyRandomSource(seed + 31L), List.of(-3, -2, -1, 0));
        this.worldzero$riverDetailNoise = PerlinNoise.create(new LegacyRandomSource(seed + 37L), List.of(-1, 0));
        this.worldzero$warpXNoise = PerlinNoise.create(new LegacyRandomSource(seed + 41L), List.of(-2, -1, 0));
        this.worldzero$warpZNoise = PerlinNoise.create(new LegacyRandomSource(seed + 43L), List.of(-2, -1, 0));
    }

    public long worldzero$getSeed() {
        return this.worldzero$seed;
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return this.worldzero$possibleBiomes.stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return this.worldzero$pickBiome(QuartPos.toBlock(x), QuartPos.toBlock(z));
    }

    public Holder<Biome> worldzero$getSurfaceBiome(int worldX, int worldZ) {
        return this.worldzero$pickBiome(worldX, worldZ);
    }

    private Holder<Biome> worldzero$pickBiome(int worldX, int worldZ) {
        double warpedX = worldX + this.worldzero$sample2d(this.worldzero$warpXNoise, worldX / WORLDZERO_WARP_SCALE, worldZ / WORLDZERO_WARP_SCALE) * WORLDZERO_WARP_STRENGTH;
        double warpedZ = worldZ + this.worldzero$sample2d(this.worldzero$warpZNoise, worldX / WORLDZERO_WARP_SCALE, worldZ / WORLDZERO_WARP_SCALE) * WORLDZERO_WARP_STRENGTH;
        double land = this.worldzero$sample2d(this.worldzero$landNoise, warpedX / WORLDZERO_LAND_SCALE, warpedZ / WORLDZERO_LAND_SCALE)
                + this.worldzero$sample2d(this.worldzero$landDetailNoise, warpedX / WORLDZERO_LAND_DETAIL_SCALE, warpedZ / WORLDZERO_LAND_DETAIL_SCALE) * 0.18D;

        if (land < -0.22D) {
            return this.worldzero$ocean;
        }

        double river = Math.abs(
                this.worldzero$sample2d(this.worldzero$riverNoise, warpedX / WORLDZERO_RIVER_SCALE, warpedZ / WORLDZERO_RIVER_SCALE)
                        + this.worldzero$sample2d(this.worldzero$riverDetailNoise, warpedX / WORLDZERO_RIVER_DETAIL_SCALE, warpedZ / WORLDZERO_RIVER_DETAIL_SCALE) * 0.20D
        );
        if (land > -0.10D && river < 0.030D) {
            return this.worldzero$river;
        }

        if (land < -0.08D) {
            return this.worldzero$beach;
        }

        double region = this.worldzero$sample2d(this.worldzero$regionNoise, warpedX / WORLDZERO_REGION_SCALE, warpedZ / WORLDZERO_REGION_SCALE)
                + this.worldzero$sample2d(this.worldzero$regionDetailNoise, warpedX / WORLDZERO_REGION_DETAIL_SCALE, warpedZ / WORLDZERO_REGION_DETAIL_SCALE) * 0.15D;
        double variant = this.worldzero$sample2d(this.worldzero$variantNoise, warpedX / WORLDZERO_VARIANT_SCALE, warpedZ / WORLDZERO_VARIANT_SCALE)
                + this.worldzero$sample2d(this.worldzero$variantDetailNoise, warpedX / WORLDZERO_VARIANT_DETAIL_SCALE, warpedZ / WORLDZERO_VARIANT_DETAIL_SCALE) * 0.25D;
        int band = Mth.clamp((int) Math.floor((region + 1.0D) * 3.5D), 0, 6);

        return switch (band) {
            case 0 -> this.worldzero$snowyPlains;
            case 1 -> this.worldzero$taiga;
            case 2 -> variant < -0.25D ? this.worldzero$plains : this.worldzero$forest;
            case 3 -> variant > 0.35D ? this.worldzero$birchForest : this.worldzero$forest;
            case 4 -> variant > 0.10D ? this.worldzero$darkForest : this.worldzero$forest;
            case 5 -> variant > 0.22D ? this.worldzero$desert : this.worldzero$savanna;
            case 6 -> {
                if (variant > 0.36D) {
                    yield this.worldzero$badlands;
                }

                yield variant < -0.08D ? this.worldzero$jungle : this.worldzero$desert;
            }
            default -> this.worldzero$plains;
        };
    }

    private double worldzero$sample2d(PerlinNoise noise, double x, double z) {
        return noise.getValue(x, 0.0D, z);
    }
}
