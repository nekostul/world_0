package ru.nekostul.worldzero.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import ru.nekostul.worldzero.WorldZeroEchoEntity;

public class WorldZeroBlackEchoRenderer extends WorldZeroEchoRenderer {
    private static final ResourceLocation WORLDZERO_BLACK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "worldzero",
            "textures/entity/black_echo.png"
    );

    public WorldZeroBlackEchoRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(WorldZeroEchoEntity entity) {
        return WORLDZERO_BLACK_TEXTURE;
    }
}
