package ru.nekostul.worldzero.client.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;
import ru.nekostul.worldzero.client.controller.WorldZeroFallClientController;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroFallOverlay {
    private static final String[] WORLDZERO_ERROR_LINES = {
            "java.lang.VoidThreadViolationError: player anchor detached from overworld frame",
            "    at net.minecraft.client.player.LocalPlayer.worldzero$collapse(LocalPlayer.java:-31)",
            "    at ru.nekostul.worldzero.VoidMirror.rebind(VoidMirror.java:666)",
            "Caused by: java.lang.IllegalSkyException: bedrock reached before ground"
    };

    private WorldZeroFallOverlay() {
    }

    @SubscribeEvent
    public static void worldzero$onRenderGui(RenderGuiEvent.Post event) {
        if (!WorldZeroFallClientController.worldzero$isFallOverlayActive()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        float animationTick = (float) (minecraft.level.getGameTime() + event.getPartialTick());
        int tintAlpha = 20 + Mth.floor((Mth.sin(animationTick * 0.45F) * 0.5F + 0.5F) * 18.0F);
        int tintColor = (tintAlpha << 24) | 0x990000;
        guiGraphics.fill(0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), tintColor);

        Font font = minecraft.font;
        int lineSpacing = font.lineHeight + 2;
        int totalHeight = WORLDZERO_ERROR_LINES.length * lineSpacing - 2;
        int centerX = guiGraphics.guiWidth() / 2;
        int startY = guiGraphics.guiHeight() / 2 - totalHeight / 2;
        int jitterX = Mth.floor(Mth.sin(animationTick * 2.4F) * 1.5F);
        int jitterY = Mth.floor(Mth.cos(animationTick * 1.8F) * 1.0F);

        for (int index = 0; index < WORLDZERO_ERROR_LINES.length; index++) {
            String line = WORLDZERO_ERROR_LINES[index];
            int color = index == 0 ? 0xFFF2D4D4 : 0xFFD9BABA;
            int lineX = centerX - font.width(line) / 2 + jitterX;
            int lineY = startY + index * lineSpacing + jitterY;
            guiGraphics.drawString(font, line, lineX, lineY, color, true);
        }
    }
}
