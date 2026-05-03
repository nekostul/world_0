package ru.nekostul.worldzero;

import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.mixin.ChatComponentAccessor;
import ru.nekostul.worldzero.mixin.ChatScreenAccessor;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroDoubleChatClientController {
    private static final int WORLDZERO_TYPE_INTERVAL_TICKS = 2;
    private static final int WORLDZERO_FINISH_HOLD_TICKS = 6;
    private static final String WORLDZERO_LOCAL_PORT_KEY = "message.worldzero.double_chat.local_port";
    private static final String WORLDZERO_LOCAL_PORT_COPY_HINT_KEY = "message.worldzero.double_chat.local_port.copy_hint";
    private static boolean worldzero$autoTypingActive;
    private static boolean worldzero$autoTypingSelfLine;
    private static String worldzero$autoTypingSpeaker = "";
    private static String worldzero$autoTypingMessageKey = "";
    private static int worldzero$autoTypingProgress;
    private static int worldzero$autoTypingTickDelay;
    private static int worldzero$autoTypingFinishHoldTicks;

    private WorldZeroDoubleChatClientController() {
    }

    public static void worldzero$handlePacket(byte action, String speaker, String messageKey) {
        switch (action) {
            case WorldZeroDoubleChatPacket.WORLDZERO_ACTION_PLAYER_LINE ->
                    worldzero$addPlayerLine(speaker, messageKey);
            case WorldZeroDoubleChatPacket.WORLDZERO_ACTION_SYSTEM_LINE ->
                    worldzero$addSystemLine(speaker, messageKey);
            case WorldZeroDoubleChatPacket.WORLDZERO_ACTION_AUTO_LINE ->
                    worldzero$startAutoTyping(speaker, messageKey);
            case WorldZeroDoubleChatPacket.WORLDZERO_ACTION_LOCAL_PORT ->
                    worldzero$addLocalPortLine(messageKey);
            case WorldZeroDoubleChatPacket.WORLDZERO_ACTION_AUTO_SELF_LINE ->
                    worldzero$startAutoTypingSelf(messageKey);
            default -> {
            }
        }
    }

    @SubscribeEvent
    public static void worldzero$onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !worldzero$autoTypingActive) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.gui == null) {
            worldzero$clearAutoTyping();
            return;
        }

        if (!(minecraft.screen instanceof ChatScreen chatScreen)) {
            minecraft.setScreen(new ChatScreen(""));
            return;
        }

        EditBox input = ((ChatScreenAccessor) chatScreen).worldzero$getInput();
        if (input == null) {
            return;
        }

        String localizedMessage = worldzero$translate(worldzero$autoTypingMessageKey);
        if (worldzero$autoTypingProgress < localizedMessage.length()) {
            worldzero$autoTypingTickDelay++;
            if (worldzero$autoTypingTickDelay >= WORLDZERO_TYPE_INTERVAL_TICKS) {
                worldzero$autoTypingTickDelay = 0;
                worldzero$autoTypingProgress++;
            }
            input.setValue(localizedMessage.substring(0, worldzero$autoTypingProgress));
            return;
        }

        input.setValue(localizedMessage);
        if (worldzero$autoTypingFinishHoldTicks < WORLDZERO_FINISH_HOLD_TICKS) {
            worldzero$autoTypingFinishHoldTicks++;
            return;
        }

        minecraft.setScreen(null);
        if (worldzero$autoTypingSelfLine) {
            worldzero$addSelfLine(worldzero$autoTypingMessageKey);
        } else {
            worldzero$addPlayerLine(worldzero$autoTypingSpeaker, worldzero$autoTypingMessageKey);
        }
        worldzero$clearAutoTyping();
    }

    @SubscribeEvent
    public static void worldzero$onClientChat(ClientChatEvent event) {
        if (worldzero$autoTypingActive) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        worldzero$clearAutoTyping();
    }

    private static void worldzero$addPlayerLine(String speaker, String messageKey) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null || speaker == null || speaker.isBlank()
                || messageKey == null || messageKey.isBlank()) {
            return;
        }

        worldzero$pushChatLine(Component.translatable(
                "chat.type.text",
                Component.literal(speaker),
                Component.translatable(messageKey)
        ));
    }

    private static void worldzero$addSystemLine(String speaker, String messageKey) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null || speaker == null || speaker.isBlank()
                || messageKey == null || messageKey.isBlank()) {
            return;
        }

        worldzero$pushChatLine(Component.translatable(messageKey, Component.literal(speaker)).withStyle(ChatFormatting.YELLOW));
    }

    private static void worldzero$addSelfLine(String messageKey) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null || minecraft.player == null
                || messageKey == null || messageKey.isBlank()) {
            return;
        }

        worldzero$pushChatLine(Component.translatable(
                "chat.type.text",
                minecraft.player.getName(),
                Component.translatable(messageKey)
        ));
    }

    private static void worldzero$addLocalPortLine(String port) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null || port == null || port.isBlank()) {
            return;
        }

        WorldZeroState.markLocalPublishLock(minecraft);

        Component portComponent = Component.literal("[" + port + "]").withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, port))
                .withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Component.translatable(WORLDZERO_LOCAL_PORT_COPY_HINT_KEY)
                )));
        worldzero$pushChatLine(Component.translatable(WORLDZERO_LOCAL_PORT_KEY, portComponent));
    }

    private static void worldzero$pushChatLine(Component component) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null || component == null) {
            return;
        }

        ChatComponent chat = minecraft.gui.getChat();
        ChatComponentAccessor accessor = (ChatComponentAccessor) (Object) chat;
        accessor.worldzero$getAllMessages().add(0, new GuiMessage(
                minecraft.gui.getGuiTicks(),
                component,
                null,
                null
        ));
        accessor.worldzero$refreshTrimmedMessage();
    }

    private static void worldzero$startAutoTyping(String speaker, String messageKey) {
        if (speaker == null || speaker.isBlank() || messageKey == null || messageKey.isBlank()) {
            return;
        }

        worldzero$autoTypingActive = true;
        worldzero$autoTypingSelfLine = false;
        worldzero$autoTypingSpeaker = speaker;
        worldzero$autoTypingMessageKey = messageKey;
        worldzero$autoTypingProgress = 0;
        worldzero$autoTypingTickDelay = 0;
        worldzero$autoTypingFinishHoldTicks = 0;
    }

    private static void worldzero$startAutoTypingSelf(String messageKey) {
        if (messageKey == null || messageKey.isBlank()) {
            return;
        }

        worldzero$autoTypingActive = true;
        worldzero$autoTypingSelfLine = true;
        worldzero$autoTypingSpeaker = "";
        worldzero$autoTypingMessageKey = messageKey;
        worldzero$autoTypingProgress = 0;
        worldzero$autoTypingTickDelay = 0;
        worldzero$autoTypingFinishHoldTicks = 0;
    }

    private static String worldzero$translate(String messageKey) {
        return Component.translatable(messageKey).getString();
    }

    private static void worldzero$clearAutoTyping() {
        worldzero$autoTypingActive = false;
        worldzero$autoTypingSelfLine = false;
        worldzero$autoTypingSpeaker = "";
        worldzero$autoTypingMessageKey = "";
        worldzero$autoTypingProgress = 0;
        worldzero$autoTypingTickDelay = 0;
        worldzero$autoTypingFinishHoldTicks = 0;
    }
}
