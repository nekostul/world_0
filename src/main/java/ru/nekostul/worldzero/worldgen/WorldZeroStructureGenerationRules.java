package ru.nekostul.worldzero.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.Set;

public final class WorldZeroStructureGenerationRules {
    private static final String WORLDZERO_MINECRAFT_NAMESPACE = "minecraft";
    private static final Set<ResourceKey<Structure>> WORLDZERO_ALLOWED_STRUCTURES = Set.of(
            BuiltinStructures.VILLAGE_PLAINS,
            BuiltinStructures.VILLAGE_DESERT,
            BuiltinStructures.VILLAGE_SAVANNA,
            BuiltinStructures.VILLAGE_SNOWY,
            BuiltinStructures.VILLAGE_TAIGA,
            BuiltinStructures.STRONGHOLD,
            BuiltinStructures.MINESHAFT,
            BuiltinStructures.MINESHAFT_MESA,
            BuiltinStructures.DESERT_PYRAMID,
            BuiltinStructures.JUNGLE_TEMPLE,
            BuiltinStructures.SWAMP_HUT,
            BuiltinStructures.FORTRESS
    );

    private WorldZeroStructureGenerationRules() {
    }

    public static boolean worldzero$isAllowedStructure(Holder<Structure> structureHolder) {
        return structureHolder != null
                && structureHolder.unwrapKey()
                .map(WorldZeroStructureGenerationRules::worldzero$isAllowedStructureKey)
                .orElse(true);
    }

    public static boolean worldzero$isAllowedStructureEntry(StructureSet.StructureSelectionEntry selectionEntry) {
        return selectionEntry != null && worldzero$isAllowedStructure(selectionEntry.structure());
    }

    private static boolean worldzero$isAllowedStructureKey(ResourceKey<Structure> structureKey) {
        if (structureKey == null) {
            return false;
        }

        if (!WORLDZERO_MINECRAFT_NAMESPACE.equals(structureKey.location().getNamespace())) {
            return true;
        }

        return WORLDZERO_ALLOWED_STRUCTURES.contains(structureKey);
    }
}
