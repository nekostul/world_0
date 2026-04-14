package ru.nekostul.worldzero;

import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WorldZeroEntityAttributes {
    private WorldZeroEntityAttributes() {
    }

    @SubscribeEvent
    public static void worldzero$onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(WorldZeroEntities.WORLDZERO_ECHO.get(), WorldZeroEchoEntity.createAttributes().build());
        event.put(WorldZeroEntities.WORLDZERO_BLACK_ECHO.get(), WorldZeroEchoEntity.createAttributes().build());
        event.put(WorldZeroEntities.WORLDZERO_HOUSE_ECHO.get(), WorldZeroHouseEchoEntity.createAttributes().build());
    }
}
