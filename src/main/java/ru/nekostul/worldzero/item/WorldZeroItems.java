package ru.nekostul.worldzero;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class WorldZeroItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(
            ForgeRegistries.ITEMS,
            WorldZeroMod.MOD_ID
    );

    public static final RegistryObject<Item> WORLDZERO_BLANK_DISC = ITEMS.register(
            "blank_disc",
            () -> new WorldZeroBlankDiscItem(new Item.Properties().stacksTo(1))
    );

    private WorldZeroItems() {
    }
}
