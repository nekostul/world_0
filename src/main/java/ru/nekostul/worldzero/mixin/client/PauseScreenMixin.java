package ru.nekostul.worldzero.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nekostul.worldzero.state.WorldZeroState;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {
    private static final String WORLDZERO_OPEN_TO_LAN_KEY = "menu.shareToLan";

    @Inject(method = "createPauseMenu", at = @At("TAIL"))
    private void worldzero$disableOpenToLanInPauseMenu(CallbackInfo callbackInfo) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || !WorldZeroState.hasLocalPublishLock(minecraft)) {
            return;
        }

        PauseScreen screen = (PauseScreen) (Object) this;
        String label = Component.translatable(WORLDZERO_OPEN_TO_LAN_KEY).getString();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && label.equals(button.getMessage().getString())) {
                button.active = false;
            }
        }
    }
}
