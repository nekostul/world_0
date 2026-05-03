package ru.nekostul.worldzero.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import ru.nekostul.worldzero.entity.WorldZeroEchoEntity;

public class WorldZeroBlakEchoRenderer extends WorldZeroEchoRenderer {
    private static final ResourceLocation WORLDZERO_BLAK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "worldzero",
            "textures/entity/blak_echo.png"
    );

    public WorldZeroBlakEchoRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(WorldZeroEchoEntity entity) {
        return WORLDZERO_BLAK_TEXTURE;
    }

    @Override
    protected boolean shouldShowName(WorldZeroEchoEntity entity) {
        return false;
    }
}
