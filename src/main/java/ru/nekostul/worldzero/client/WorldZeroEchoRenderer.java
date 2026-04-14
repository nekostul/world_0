package ru.nekostul.worldzero.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import ru.nekostul.worldzero.WorldZeroEchoEntity;

public class WorldZeroEchoRenderer extends HumanoidMobRenderer<WorldZeroEchoEntity, PlayerModel<WorldZeroEchoEntity>> {
    private static final ResourceLocation WORLDZERO_STEVE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "minecraft",
            "textures/entity/steve.png"
    );
    private final PlayerModel<WorldZeroEchoEntity> worldzero$defaultModel;
    private final PlayerModel<WorldZeroEchoEntity> worldzero$slimModel;

    public WorldZeroEchoRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.worldzero$defaultModel = this.getModel();
        this.worldzero$slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public ResourceLocation getTextureLocation(WorldZeroEchoEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return WORLDZERO_STEVE_TEXTURE;
        }

        return minecraft.player.getSkinTextureLocation();
    }

    @Override
    public void render(
            WorldZeroEchoEntity entity,
            float entityYaw,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && "slim".equals(minecraft.player.getModelName())) {
            this.model = this.worldzero$slimModel;
        } else {
            this.model = this.worldzero$defaultModel;
        }

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }
}
