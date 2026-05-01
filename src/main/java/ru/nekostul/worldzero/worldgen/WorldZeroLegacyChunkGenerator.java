package ru.nekostul.worldzero.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import net.minecraft.server.level.WorldGenRegion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class WorldZeroLegacyChunkGenerator extends ChunkGenerator {
    public static final Codec<WorldZeroLegacyChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("seed").forGetter(WorldZeroLegacyChunkGenerator::worldzero$getSeed),
            WorldZeroLegacyBiomeSource.CODEC.fieldOf("biome_source").forGetter(WorldZeroLegacyChunkGenerator::worldzero$getLegacyBiomeSource)
    ).apply(instance, instance.stable(WorldZeroLegacyChunkGenerator::new)));

    private static final int WORLDZERO_MIN_Y = 0;
    private static final int WORLDZERO_GEN_DEPTH = 128;
    private static final int WORLDZERO_SEA_LEVEL = 63;
    private static final BlockState WORLDZERO_AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState WORLDZERO_STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState WORLDZERO_DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState WORLDZERO_GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState WORLDZERO_SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState WORLDZERO_SANDSTONE = Blocks.SANDSTONE.defaultBlockState();
    private static final BlockState WORLDZERO_RED_SAND = Blocks.RED_SAND.defaultBlockState();
    private static final BlockState WORLDZERO_TERRACOTTA = Blocks.TERRACOTTA.defaultBlockState();
    private static final BlockState WORLDZERO_WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState WORLDZERO_BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final Set<String> WORLDZERO_ALLOWED_PLACED_FEATURES = Set.of(
            "lake_lava_underground",
            "lake_lava_surface",
            "monster_room",
            "monster_room_deep",
            "fossil_upper",
            "fossil_lower",
            "desert_well",
            "ore_dirt",
            "ore_gravel",
            "ore_granite_upper",
            "ore_granite_lower",
            "ore_diorite_upper",
            "ore_diorite_lower",
            "ore_andesite_upper",
            "ore_andesite_lower",
            "ore_coal_upper",
            "ore_coal_lower",
            "ore_iron_upper",
            "ore_iron_middle",
            "ore_iron_small",
            "ore_gold",
            "ore_gold_lower",
            "ore_gold_extra",
            "ore_redstone",
            "ore_redstone_lower",
            "ore_diamond",
            "ore_diamond_large",
            "ore_diamond_buried",
            "ore_lapis",
            "ore_lapis_buried",
            "ore_copper",
            "disk_sand",
            "disk_clay",
            "disk_gravel",
            "spring_water",
            "spring_lava",
            "trees_plains",
            "trees_birch_and_oak",
            "trees_birch",
            "dark_forest_vegetation",
            "trees_jungle",
            "trees_savanna",
            "trees_taiga",
            "trees_snowy",
            "flower_default",
            "patch_grass_plain",
            "patch_grass_forest",
            "patch_grass_jungle",
            "patch_grass_savanna",
            "patch_grass_taiga_2",
            "patch_grass_badlands",
            "patch_dead_bush_2",
            "patch_dead_bush_badlands",
            "brown_mushroom_normal",
            "red_mushroom_normal",
            "brown_mushroom_taiga",
            "red_mushroom_taiga",
            "patch_sugar_cane",
            "patch_sugar_cane_desert",
            "patch_sugar_cane_badlands",
            "patch_pumpkin",
            "vines",
            "patch_melon",
            "patch_cactus_desert",
            "patch_cactus_decorated",
            "freeze_top_layer"
    );
    private static final Set<net.minecraft.resources.ResourceKey<Structure>> WORLDZERO_ALLOWED_STRUCTURES = Set.of(
            BuiltinStructures.MINESHAFT,
            BuiltinStructures.MINESHAFT_MESA,
            BuiltinStructures.JUNGLE_TEMPLE,
            BuiltinStructures.DESERT_PYRAMID,
            BuiltinStructures.STRONGHOLD,
            BuiltinStructures.VILLAGE_PLAINS,
            BuiltinStructures.VILLAGE_DESERT,
            BuiltinStructures.VILLAGE_SAVANNA,
            BuiltinStructures.VILLAGE_SNOWY,
            BuiltinStructures.VILLAGE_TAIGA
    );

    private final long worldzero$seed;
    private final WorldZeroLegacyBiomeSource worldzero$legacyBiomeSource;
    private final PerlinNoise worldzero$lowNoise;
    private final PerlinNoise worldzero$highNoise;
    private final PerlinNoise worldzero$selectorNoise;
    private final PerlinNoise worldzero$continentNoise;
    private final PerlinNoise worldzero$ridgeNoise;
    private final PerlinNoise worldzero$detailNoise;
    private final PerlinNoise worldzero$warpXNoise;
    private final PerlinNoise worldzero$warpZNoise;
    private final PerlinNoise worldzero$caveNoise;
    private final PerlinNoise worldzero$caveDetailNoise;
    private final Map<ResourceKey<Biome>, BiomeGenerationSettings> worldzero$generationSettingsCache = new HashMap<>();

    public WorldZeroLegacyChunkGenerator(long seed, WorldZeroLegacyBiomeSource biomeSource) {
        super(biomeSource);
        this.worldzero$seed = seed;
        this.worldzero$legacyBiomeSource = biomeSource;
        this.worldzero$lowNoise = PerlinNoise.create(new LegacyRandomSource(seed + 101L), List.of(-4, -3, -2, -1));
        this.worldzero$highNoise = PerlinNoise.create(new LegacyRandomSource(seed + 103L), List.of(-2, -1, 0));
        this.worldzero$selectorNoise = PerlinNoise.create(new LegacyRandomSource(seed + 107L), List.of(-3, -2, -1, 0));
        this.worldzero$continentNoise = PerlinNoise.create(new LegacyRandomSource(seed + 109L), List.of(-5, -4, -3, -2));
        this.worldzero$ridgeNoise = PerlinNoise.create(new LegacyRandomSource(seed + 113L), List.of(-2, -1, 0));
        this.worldzero$detailNoise = PerlinNoise.create(new LegacyRandomSource(seed + 127L), List.of(-1, 0));
        this.worldzero$warpXNoise = PerlinNoise.create(new LegacyRandomSource(seed + 131L), List.of(-3, -2, -1));
        this.worldzero$warpZNoise = PerlinNoise.create(new LegacyRandomSource(seed + 137L), List.of(-3, -2, -1));
        this.worldzero$caveNoise = PerlinNoise.create(new LegacyRandomSource(seed + 149L), List.of(-2, -1, 0));
        this.worldzero$caveDetailNoise = PerlinNoise.create(new LegacyRandomSource(seed + 151L), List.of(-1, 0));
    }

    public long worldzero$getSeed() {
        return this.worldzero$seed;
    }

    public WorldZeroLegacyBiomeSource worldzero$getLegacyBiomeSource() {
        return this.worldzero$legacyBiomeSource;
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long seed) {
        return ChunkGeneratorStructureState.createForNormal(
                randomState,
                seed,
                this.biomeSource,
                structureSets.filterElements(WorldZeroLegacyChunkGenerator::worldzero$isAllowedStructureSet)
        );
    }

    @Override
    public void applyCarvers(
            WorldGenRegion region,
            long seed,
            RandomState randomState,
            BiomeManager biomeManager,
            StructureManager structureManager,
            ChunkAccess chunk,
            GenerationStep.Carving carving
    ) {
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        ChunkPos chunkPos = region.getCenter();
        Holder<Biome> biome = region.getBiome(chunkPos.getWorldPosition().atY(region.getMaxBuildHeight() - 1));
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(region.getSeed(), chunkPos.getMinBlockX(), chunkPos.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(region, biome, chunkPos, random);
    }

    @Override
    public int getGenDepth() {
        return WORLDZERO_GEN_DEPTH;
    }

    @Override
    public int getSeaLevel() {
        return WORLDZERO_SEA_LEVEL;
    }

    @Override
    public int getMinY() {
        return WORLDZERO_MIN_Y;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            Executor executor,
            Blender blender,
            RandomState randomState,
            StructureManager structureManager,
            ChunkAccess chunk
    ) {
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        ChunkPos chunkPos = chunk.getPos();
        int minBlockX = chunkPos.getMinBlockX();
        int minBlockZ = chunkPos.getMinBlockZ();
        int chunkMinY = chunk.getMinBuildHeight();
        int chunkMaxY = chunk.getMaxBuildHeight();

        for (int localX = 0; localX < 16; localX++) {
            int worldX = minBlockX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldZ = minBlockZ + localZ;
                for (int y = chunkMinY; y < chunkMaxY; y++) {
                    BlockState state = this.worldzero$getBlockStateAt(worldX, y, worldZ);
                    if (state.isAir()) {
                        continue;
                    }

                    chunk.setBlockState(mutablePos.set(localX, y, localZ), state, false);
                    oceanFloor.update(localX, y, localZ, state);
                    worldSurface.update(localX, y, localZ, state);
                }
            }
        }

        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmapType, LevelHeightAccessor heightAccessor, RandomState randomState) {
        int minY = heightAccessor.getMinBuildHeight();
        int maxY = heightAccessor.getMaxBuildHeight();
        for (int y = maxY - 1; y >= minY; y--) {
            BlockState state = this.worldzero$getBlockStateAt(x, y, z);
            if (heightmapType.isOpaque().test(state)) {
                return y + 1;
            }
        }

        return minY;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
        int minY = heightAccessor.getMinBuildHeight();
        int maxY = heightAccessor.getMaxBuildHeight();
        BlockState[] states = new BlockState[Math.max(0, maxY - minY)];

        for (int y = minY; y < maxY; y++) {
            states[y - minY] = this.worldzero$getBlockStateAt(x, y, z);
        }

        return new NoiseColumn(minY, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState randomState, BlockPos pos) {
        lines.add("World_0 Legacy: low/high/selector");
        lines.add("World_0 Legacy Height: " + this.worldzero$sampleSurfaceHeight(pos.getX(), pos.getZ()));
    }

    @Override
    public BiomeGenerationSettings getBiomeGenerationSettings(Holder<Biome> biome) {
        return biome.unwrapKey()
                .map(key -> this.worldzero$generationSettingsCache.computeIfAbsent(key, unused -> this.worldzero$buildGenerationSettings(biome)))
                .orElseGet(() -> this.worldzero$buildGenerationSettings(biome));
    }

    private static boolean worldzero$isAllowedStructureSet(StructureSet structureSet) {
        return structureSet.structures().stream()
                .map(StructureSet.StructureSelectionEntry::structure)
                .allMatch(WorldZeroLegacyChunkGenerator::worldzero$isAllowedStructure);
    }

    private static boolean worldzero$isAllowedStructure(Holder<Structure> structureHolder) {
        return structureHolder.unwrapKey().filter(WORLDZERO_ALLOWED_STRUCTURES::contains).isPresent();
    }

    private BiomeGenerationSettings worldzero$buildGenerationSettings(Holder<Biome> biome) {
        BiomeGenerationSettings original = biome.value().getGenerationSettings();
        BiomeGenerationSettings.PlainBuilder builder = new BiomeGenerationSettings.PlainBuilder();

        for (GenerationStep.Carving step : GenerationStep.Carving.values()) {
            for (Holder<ConfiguredWorldCarver<?>> carver : original.getCarvers(step)) {
                builder.addCarver(step, carver);
            }
        }

        List<HolderSet<PlacedFeature>> features = original.features();
        for (int stepIndex = 0; stepIndex < features.size(); stepIndex++) {
            for (Holder<PlacedFeature> featureHolder : features.get(stepIndex)) {
                if (this.worldzero$shouldKeepPlacedFeature(biome, featureHolder, stepIndex)) {
                    builder.addFeature(stepIndex, featureHolder);
                }
            }
        }

        return builder.build();
    }

    private boolean worldzero$shouldKeepPlacedFeature(Holder<Biome> biome, Holder<PlacedFeature> featureHolder, int stepIndex) {
        String featurePath = featureHolder.unwrapKey()
                .map(featureKey -> featureKey.location().getPath())
                .orElse("");
        if (featurePath.isEmpty()) {
            return true;
        }

        if (worldzero$isBiome(biome, Biomes.OCEAN) && stepIndex == GenerationStep.Decoration.VEGETAL_DECORATION.ordinal()) {
            return false;
        }

        return WORLDZERO_ALLOWED_PLACED_FEATURES.contains(featurePath);
    }

    private BlockState worldzero$getBlockStateAt(int worldX, int y, int worldZ) {
        if (y < WORLDZERO_MIN_Y || y >= WORLDZERO_MIN_Y + WORLDZERO_GEN_DEPTH) {
            return WORLDZERO_AIR;
        }

        int surfaceHeight = this.worldzero$sampleSurfaceHeight(worldX, worldZ);
        if (y <= this.worldzero$getBedrockCeiling(worldX, worldZ)) {
            return WORLDZERO_BEDROCK;
        }

        if (y > surfaceHeight) {
            return y <= WORLDZERO_SEA_LEVEL ? WORLDZERO_WATER : WORLDZERO_AIR;
        }

        if (this.worldzero$isCave(worldX, y, worldZ, surfaceHeight)) {
            return WORLDZERO_AIR;
        }

        Holder<Biome> biome = this.worldzero$legacyBiomeSource.worldzero$getSurfaceBiome(worldX, worldZ);
        SurfacePalette palette = this.worldzero$getSurfacePalette(biome, worldX, worldZ, surfaceHeight);
        int depth = surfaceHeight - y;
        if (depth == 0) {
            return palette.worldzero$top();
        }

        if (depth < palette.worldzero$fillerDepth()) {
            return palette.worldzero$filler();
        }

        if (palette.worldzero$deep() != null && depth < palette.worldzero$deepDepth()) {
            return palette.worldzero$deep();
        }

        return WORLDZERO_STONE;
    }

    private int worldzero$sampleSurfaceHeight(int worldX, int worldZ) {
        double warpedX = worldX + this.worldzero$sample2d(this.worldzero$warpXNoise, worldX / 96.0D, worldZ / 96.0D) * 18.0D;
        double warpedZ = worldZ + this.worldzero$sample2d(this.worldzero$warpZNoise, worldX / 96.0D, worldZ / 96.0D) * 18.0D;
        double low = this.worldzero$sample2d(this.worldzero$lowNoise, warpedX / 180.0D, warpedZ / 180.0D) * 19.0D
                + this.worldzero$sample2d(this.worldzero$lowNoise, warpedX / 90.0D, warpedZ / 90.0D) * 7.0D;
        double high = this.worldzero$sample2d(this.worldzero$highNoise, warpedX / 82.0D, warpedZ / 82.0D) * 24.0D
                + this.worldzero$sample2d(this.worldzero$highNoise, warpedX / 38.0D, warpedZ / 38.0D) * 9.0D;
        double selector = Mth.clamp(
                (this.worldzero$sample2d(this.worldzero$selectorNoise, warpedX / 140.0D, warpedZ / 140.0D) + 1.0D) * 0.5D,
                0.0D,
                1.0D
        );
        double terrain = Mth.lerp(selector, low, high);
        double continental = this.worldzero$sample2d(this.worldzero$continentNoise, warpedX / 260.0D, warpedZ / 260.0D) * 18.0D;
        double cliffs = Math.max(
                0.0D,
                Math.abs(this.worldzero$sample2d(this.worldzero$ridgeNoise, warpedX / 48.0D, warpedZ / 48.0D)) - 0.42D
        ) * 44.0D;
        double jagged = this.worldzero$sample2d(this.worldzero$detailNoise, warpedX / 20.0D, warpedZ / 20.0D) * 4.5D
                + this.worldzero$sample2d(this.worldzero$detailNoise, warpedX / 11.0D, warpedZ / 11.0D) * 1.7D;
        double oddShift = this.worldzero$sample2d(this.worldzero$selectorNoise, warpedX / 64.0D, warpedZ / 64.0D) * 3.0D;
        double height = WORLDZERO_SEA_LEVEL + continental + terrain + cliffs + jagged + oddShift;
        if (selector < 0.25D) {
            height -= (0.25D - selector) * 14.0D;
        }

        return Mth.clamp(Mth.floor(height), 28, 110);
    }

    private SurfacePalette worldzero$getSurfacePalette(Holder<Biome> biome, int worldX, int worldZ, int surfaceHeight) {
        int fillerDepth = 4 + Math.floorMod(this.worldzero$hash(worldX, 0, worldZ), 2);
        boolean steepSlope = this.worldzero$isSteepSlope(worldX, worldZ, surfaceHeight);

        if (worldzero$isBiome(biome, Biomes.BADLANDS)) {
            return new SurfacePalette(WORLDZERO_RED_SAND, WORLDZERO_RED_SAND, WORLDZERO_TERRACOTTA, fillerDepth + 2, fillerDepth + 6);
        }

        if (worldzero$isBiome(biome, Biomes.DESERT)
                || worldzero$isBiome(biome, Biomes.BEACH)
                || worldzero$isBiome(biome, Biomes.SNOWY_BEACH)
                || worldzero$isBiome(biome, Biomes.OCEAN)
                || worldzero$isBiome(biome, Biomes.DEEP_OCEAN)
                || worldzero$isBiome(biome, Biomes.FROZEN_OCEAN)
                || surfaceHeight <= WORLDZERO_SEA_LEVEL - 2) {
            return new SurfacePalette(WORLDZERO_SAND, WORLDZERO_SAND, WORLDZERO_SANDSTONE, fillerDepth + 1, fillerDepth + 5);
        }

        if (worldzero$isBiome(biome, Biomes.STONY_SHORE)
                || worldzero$isBiome(biome, Biomes.WINDSWEPT_HILLS)
                || steepSlope) {
            return new SurfacePalette(WORLDZERO_STONE, WORLDZERO_STONE, WORLDZERO_STONE, fillerDepth, fillerDepth);
        }

        return new SurfacePalette(WORLDZERO_GRASS, WORLDZERO_DIRT, WORLDZERO_DIRT, fillerDepth, fillerDepth + 2);
    }

    private boolean worldzero$isSteepSlope(int worldX, int worldZ, int surfaceHeight) {
        int maxNeighborDelta = Math.max(
                Math.max(
                        Math.abs(surfaceHeight - this.worldzero$sampleSurfaceHeight(worldX + 1, worldZ)),
                        Math.abs(surfaceHeight - this.worldzero$sampleSurfaceHeight(worldX - 1, worldZ))
                ),
                Math.max(
                        Math.abs(surfaceHeight - this.worldzero$sampleSurfaceHeight(worldX, worldZ + 1)),
                        Math.abs(surfaceHeight - this.worldzero$sampleSurfaceHeight(worldX, worldZ - 1))
                )
        );
        return maxNeighborDelta >= 7;
    }

    private boolean worldzero$isCave(int worldX, int y, int worldZ, int surfaceHeight) {
        if (y <= 9 || y >= surfaceHeight - 3 || y > 76) {
            return false;
        }

        double primary = this.worldzero$caveNoise.getValue(worldX / 28.0D, y / 18.0D, worldZ / 28.0D);
        double detail = this.worldzero$caveDetailNoise.getValue(worldX / 16.0D, y / 12.0D, worldZ / 16.0D);
        return primary > 0.64D && detail > -0.15D;
    }

    private int worldzero$getBedrockCeiling(int worldX, int worldZ) {
        return 1 + Math.floorMod(this.worldzero$hash(worldX, 0, worldZ), 4);
    }

    private int worldzero$hash(int x, int y, int z) {
        long hash = this.worldzero$seed;
        hash ^= (long) x * 341873128712L;
        hash ^= (long) y * 132897987541L;
        hash ^= (long) z * 42317861L;
        hash = hash * hash * 42317861L + hash * 11L;
        return (int) (hash >> 16);
    }

    private double worldzero$sample2d(PerlinNoise noise, double x, double z) {
        return noise.getValue(x, 0.0D, z);
    }

    private static boolean worldzero$isBiome(Holder<Biome> biome, net.minecraft.resources.ResourceKey<Biome> key) {
        return biome.unwrapKey().filter(key::equals).isPresent();
    }

    private record SurfacePalette(
            BlockState worldzero$top,
            BlockState worldzero$filler,
            BlockState worldzero$deep,
            int worldzero$fillerDepth,
            int worldzero$deepDepth
    ) {
    }
}
