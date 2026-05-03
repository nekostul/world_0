package ru.nekostul.worldzero.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import ru.nekostul.worldzero.entity.WorldZeroHouseEchoEntity;

public class WorldZeroHouseEchoRenderer extends HumanoidMobRenderer<WorldZeroHouseEchoEntity, PlayerModel<WorldZeroHouseEchoEntity>> {
    private static final ResourceLocation WORLDZERO_STEVE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "minecraft",
            "textures/entity/steve.png"
    );
    private final PlayerModel<WorldZeroHouseEchoEntity> worldzero$defaultModel;
    private final PlayerModel<WorldZeroHouseEchoEntity> worldzero$slimModel;

    public WorldZeroHouseEchoRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.worldzero$defaultModel = this.getModel();
        this.worldzero$slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(WorldZeroHouseEchoEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return WORLDZERO_STEVE_TEXTURE;
        }

        return minecraft.player.getSkinTextureLocation();
    }

    @Override
    public void render(
            WorldZeroHouseEchoEntity entity,
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
