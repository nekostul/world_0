package ru.nekostul.worldzero.client.renderer;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;
import ru.nekostul.worldzero.entity.WorldZeroEntities;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WorldZeroEntityRenderers {
    private WorldZeroEntityRenderers() {
    }

    @SubscribeEvent
    public static void worldzero$onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WorldZeroEntities.WORLDZERO_ECHO.get(), WorldZeroEchoRenderer::new);
        event.registerEntityRenderer(WorldZeroEntities.WORLDZERO_BLACK_ECHO.get(), WorldZeroBlackEchoRenderer::new);
        event.registerEntityRenderer(WorldZeroEntities.WORLDZERO_BLAK_ECHO.get(), WorldZeroBlakEchoRenderer::new);
        event.registerEntityRenderer(WorldZeroEntities.WORLDZERO_HOUSE_ECHO.get(), WorldZeroHouseEchoRenderer::new);
    }
}
