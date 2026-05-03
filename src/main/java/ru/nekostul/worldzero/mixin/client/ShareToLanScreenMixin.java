package ru.nekostul.worldzero.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nekostul.worldzero.state.WorldZeroState;

@Mixin(ShareToLanScreen.class)
public abstract class ShareToLanScreenMixin {
    private static final String WORLDZERO_OPEN_TO_LAN_KEY = "lanServer.start";

    @Shadow
    private boolean commands;

    @Inject(
            method = "init",
            at = @At("TAIL")
    )
    private void worldzero$disableOpenToLanAfterPortReveal(CallbackInfo callbackInfo) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || !WorldZeroState.hasLocalPublishLock(minecraft)) {
            return;
        }

        ShareToLanScreen screen = (ShareToLanScreen) (Object) this;
        String startLabel = Component.translatable(WORLDZERO_OPEN_TO_LAN_KEY).getString();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && startLabel.equals(button.getMessage().getString())) {
                button.active = false;
            }
        }
    }

    @Inject(
            method = "lambda$init$2(Lnet/minecraft/client/server/IntegratedServer;Lnet/minecraft/client/gui/components/Button;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void worldzero$closeWorldOnLanCheats(
            IntegratedServer integratedServer,
            Button button,
            CallbackInfo callbackInfo
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && WorldZeroState.hasLocalPublishLock(minecraft)) {
            button.active = false;
            callbackInfo.cancel();
            return;
        }

        if (!this.commands) {
            return;
        }

        if (minecraft == null) {
            return;
        }

        if (minecraft.level != null) {
            minecraft.level.disconnect();
        }

        if (minecraft.isLocalServer()) {
            minecraft.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
        } else {
            minecraft.clearLevel();
        }

        minecraft.setScreen(new AlertScreen(
                () -> minecraft.setScreen(new TitleScreen()),
                WorldZeroState.lanCheatsTitle(),
                WorldZeroState.lanCheatsMessage(),
                CommonComponents.GUI_TO_TITLE,
                true
        ));
        callbackInfo.cancel();
    }
}
