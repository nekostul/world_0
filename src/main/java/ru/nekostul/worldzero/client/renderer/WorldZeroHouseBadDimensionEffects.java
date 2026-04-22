package ru.nekostul.worldzero;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WorldZeroHouseBadDimensionEffects {
    private static final ResourceLocation WORLDZERO_HOUSE_BAD_EFFECTS_ID = new ResourceLocation(
            WorldZeroMod.MOD_ID,
            "house_bad"
    );

    private WorldZeroHouseBadDimensionEffects() {
    }

    @SubscribeEvent
    public static void worldzero$onRegisterDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(WORLDZERO_HOUSE_BAD_EFFECTS_ID, new HouseBadEffects());
    }

    private static final class HouseBadEffects extends DimensionSpecialEffects {
        private HouseBadEffects() {
            super(0.0F, false, SkyType.NONE, false, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
            return Vec3.ZERO;
        }

        @Override
        public boolean isFoggyAt(int x, int z) {
            return false;
        }

        @Override
        public boolean renderClouds(
                ClientLevel level,
                int ticks,
                float partialTick,
                PoseStack poseStack,
                double camX,
                double camY,
                double camZ,
                Matrix4f projectionMatrix
        ) {
            return true;
        }

        @Override
        public boolean renderSky(
                ClientLevel level,
                int ticks,
                float partialTick,
                PoseStack poseStack,
                Camera camera,
                Matrix4f projectionMatrix,
                boolean isFoggy,
                Runnable setupFog
        ) {
            return true;
        }

        @Override
        public boolean renderSnowAndRain(
                ClientLevel level,
                int ticks,
                float partialTick,
                LightTexture lightTexture,
                double camX,
                double camY,
                double camZ
        ) {
            return true;
        }

        @Override
        public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
            return true;
        }
    }
}
