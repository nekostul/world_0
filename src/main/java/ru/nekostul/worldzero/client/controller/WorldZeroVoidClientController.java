package ru.nekostul.worldzero;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.mixin.ChatComponentAccessor;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroVoidClientController {
    private static int worldzero$keyboardBlockTicksRemaining;

    private WorldZeroVoidClientController() {
    }

    public static void worldzero$startKeyboardBlock(int durationTicks) {
        if (durationTicks <= 0) {
            worldzero$keyboardBlockTicksRemaining = 0;
            return;
        }

        worldzero$keyboardBlockTicksRemaining = Math.max(worldzero$keyboardBlockTicksRemaining, durationTicks);
    }

    public static boolean worldzero$isKeyboardBlocked() {
        return worldzero$keyboardBlockTicksRemaining > 0;
    }

    public static boolean worldzero$isPauseBlocked() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null
                && minecraft.player != null
                && minecraft.level != null
                && minecraft.level.dimension() == WorldZeroVoidDimension.WORLDZERO_VOID_LEVEL
                && worldzero$keyboardBlockTicksRemaining > 0
                && !worldzero$isDevInVoid(minecraft);
    }

    public static void worldzero$handleChatLine(String speaker, String messageKey) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null || speaker.isBlank() || messageKey.isBlank()) {
            return;
        }

        Component chatLine = Component.translatable(
                "chat.type.text",
                Component.literal(speaker),
                Component.translatable(messageKey)
        );
        ChatComponent chat = minecraft.gui.getChat();
        ChatComponentAccessor accessor = (ChatComponentAccessor) (Object) chat;
        accessor.worldzero$getAllMessages().add(0, new GuiMessage(
                minecraft.gui.getGuiTicks(),
                chatLine,
                null,
                null
        ));
        accessor.worldzero$refreshTrimmedMessage();
    }

    @SubscribeEvent
    public static void worldzero$onScreenOpening(ScreenEvent.Opening event) {
        if (worldzero$isPauseBlocked()
                && event.getNewScreen() != null
                && event.getNewScreen().isPauseScreen()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || worldzero$keyboardBlockTicksRemaining <= 0) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            worldzero$keyboardBlockTicksRemaining = 0;
            return;
        }

        if (worldzero$isPauseBlocked()) {
            boolean closedPauseScreen = false;
            if (minecraft.screen != null && minecraft.screen.isPauseScreen()) {
                minecraft.setScreen(null);
                closedPauseScreen = true;
            }

            if ((closedPauseScreen || minecraft.screen == null) && !minecraft.mouseHandler.isMouseGrabbed()) {
                minecraft.mouseHandler.grabMouse();
            }
        }

        if (!worldzero$isDevInVoid(minecraft)) {
            worldzero$releaseControlKeys(minecraft.options);
        }
        worldzero$keyboardBlockTicksRemaining--;
    }

    @SubscribeEvent
    public static void worldzero$onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        worldzero$keyboardBlockTicksRemaining = 0;
    }

    private static void worldzero$releaseControlKeys(Options options) {
        options.keyUp.setDown(false);
        options.keyDown.setDown(false);
        options.keyLeft.setDown(false);
        options.keyRight.setDown(false);
        options.keyJump.setDown(false);
        options.keyShift.setDown(false);
        options.keySprint.setDown(false);
        options.keyAttack.setDown(false);
        options.keyUse.setDown(false);
        options.keyPickItem.setDown(false);
        options.keyDrop.setDown(false);
        options.keySwapOffhand.setDown(false);
        options.keyInventory.setDown(false);
        options.keySaveHotbarActivator.setDown(false);
        options.keyLoadHotbarActivator.setDown(false);
        for (int index = 0; index < options.keyHotbarSlots.length; index++) {
            options.keyHotbarSlots[index].setDown(false);
        }
    }

    private static boolean worldzero$isDevInVoid(Minecraft minecraft) {
        return minecraft.level != null
                && "Dev".equals(minecraft.player.getGameProfile().getName())
                && minecraft.level.dimension() == WorldZeroVoidDimension.WORLDZERO_VOID_LEVEL;
    }
}
