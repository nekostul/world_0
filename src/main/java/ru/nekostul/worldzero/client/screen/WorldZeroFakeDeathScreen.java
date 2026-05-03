package ru.nekostul.worldzero.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.nekostul.worldzero.client.controller.WorldZeroFallClientController;

public final class WorldZeroFakeDeathScreen extends Screen {
    public WorldZeroFakeDeathScreen() {
        super(Component.translatable("deathScreen.title"));
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.addRenderableWidget(Button.builder(
                Component.translatable("deathScreen.respawn"),
                button -> WorldZeroFallClientController.worldzero$onFakeRespawnPressed()
        ).bounds(this.width / 2 - 100, this.height / 4 + 72, 200, 20).build());
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xB0120000, 0xD0000000);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(2.0F, 2.0F, 2.0F);
        guiGraphics.drawCenteredString(
                this.font,
                this.title,
                this.width / 4,
                30,
                0xFFFFFF
        );
        guiGraphics.pose().popPose();
        guiGraphics.drawCenteredString(
                this.font,
                Component.literal(""),
                this.width / 2,
                this.height / 4 + 40,
                0xAAAAAA
        );
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
