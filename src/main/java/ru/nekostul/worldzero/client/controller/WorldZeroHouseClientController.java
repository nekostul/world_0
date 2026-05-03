package ru.nekostul.worldzero.client.controller;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;
import ru.nekostul.worldzero.dimension.WorldZeroHouseBadDimension;
import ru.nekostul.worldzero.dimension.WorldZeroHouseDimension;
import ru.nekostul.worldzero.mixin.client.ChatComponentAccessor;
import ru.nekostul.worldzero.network.WorldZeroNetwork;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroHouseClientController {
    public static final byte WORLDZERO_MODE_DEFAULT = 0;
    public static final byte WORLDZERO_MODE_MUSIC = 1;
    public static final byte WORLDZERO_MODE_SILENT = 2;
    private static final ResourceLocation WORLDZERO_C418MINE_SOUND_ID = new ResourceLocation(
            WorldZeroMod.MOD_ID,
            "c418mine"
    );

    private static boolean worldzero$houseActive;
    private static boolean worldzero$finishSignalSent;
    private static byte worldzero$pendingHouseMode = WORLDZERO_MODE_DEFAULT;
    private static SoundInstance worldzero$activeHouseSound;
    private static final List<String> WORLDZERO_TRACKED_FAKE_CHAT_LINES = new ArrayList<>();

    private WorldZeroHouseClientController() {
    }

    public static void handleModePacket(byte mode) {
        worldzero$pendingHouseMode = mode;
        if (mode == WORLDZERO_MODE_SILENT) {
            worldzero$stopActiveHouseSound();
        }
    }

    public static void handleFakeChatLinePacket(String speaker, String defaultMessage, String englishMessage) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null || speaker.isBlank() || defaultMessage.isBlank()) {
            return;
        }

        String message = defaultMessage;
        if (minecraft.options != null
                && minecraft.options.languageCode != null
                && minecraft.options.languageCode.toLowerCase(java.util.Locale.ROOT).startsWith("en")
                && !englishMessage.isBlank()) {
            message = englishMessage;
        }

        Component chatLine = Component.translatable(
                "chat.type.text",
                Component.literal(speaker),
                Component.literal(message)
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
        WORLDZERO_TRACKED_FAKE_CHAT_LINES.add(chatLine.getString());
    }

    @SubscribeEvent
    public static void worldzero$onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        boolean inHouse = worldzero$isHouseLevel(minecraft);
        if (!inHouse) {
            if (worldzero$houseActive || !WORLDZERO_TRACKED_FAKE_CHAT_LINES.isEmpty()) {
                worldzero$clearState();
            }
            return;
        }

        if (!worldzero$houseActive) {
            worldzero$enterHouse(minecraft);
        }

        if (minecraft == null || minecraft.getSoundManager() == null) {
            worldzero$clearState();
            return;
        }

        if (worldzero$activeHouseSound == null) {
            return;
        }

        if (!minecraft.getSoundManager().isActive(worldzero$activeHouseSound)) {
            if (!worldzero$finishSignalSent) {
                worldzero$finishSignalSent = true;
                WorldZeroNetwork.sendHouseMusicFinished();
            }
            worldzero$activeHouseSound = null;
        }
    }

    @SubscribeEvent
    public static void worldzero$onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        worldzero$clearState();
    }

    @SubscribeEvent
    public static void worldzero$onRenderBlockHighlight(RenderHighlightEvent.Block event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!worldzero$isBarrierProtectedLevel(minecraft) || minecraft == null || minecraft.level == null) {
            return;
        }

        if (minecraft.level.getBlockState(event.getTarget().getBlockPos()).is(Blocks.BARRIER)) {
            event.setCanceled(true);
        }
    }

    private static void worldzero$enterHouse(@Nullable Minecraft minecraft) {
        if (minecraft == null || minecraft.getSoundManager() == null) {
            return;
        }

        worldzero$houseActive = true;
        worldzero$finishSignalSent = false;
        if (worldzero$pendingHouseMode == WORLDZERO_MODE_SILENT) {
            worldzero$activeHouseSound = null;
            return;
        }

        SoundInstance soundInstance = new SimpleSoundInstance(
                WORLDZERO_C418MINE_SOUND_ID,
                SoundSource.RECORDS,
                1.0F,
                1.0F,
                RandomSource.create(),
                false,
                0,
                Attenuation.NONE,
                0.0D,
                0.0D,
                0.0D,
                true
        );
        minecraft.getSoundManager().play(soundInstance);
        worldzero$activeHouseSound = soundInstance;
    }

    private static void worldzero$clearState() {
        worldzero$stopActiveHouseSound();
        worldzero$removeTrackedFakeChatLines();
        worldzero$houseActive = false;
        worldzero$finishSignalSent = false;
        worldzero$pendingHouseMode = WORLDZERO_MODE_DEFAULT;
    }

    private static void worldzero$stopActiveHouseSound() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getSoundManager() != null && worldzero$activeHouseSound != null) {
            minecraft.getSoundManager().stop(worldzero$activeHouseSound);
        }
        worldzero$activeHouseSound = null;
    }

    private static void worldzero$removeTrackedFakeChatLines() {
        if (WORLDZERO_TRACKED_FAKE_CHAT_LINES.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) {
            WORLDZERO_TRACKED_FAKE_CHAT_LINES.clear();
            return;
        }

        ChatComponent chat = minecraft.gui.getChat();
        ChatComponentAccessor accessor = (ChatComponentAccessor) (Object) chat;
        List<GuiMessage> allMessages = accessor.worldzero$getAllMessages();
        boolean removed = allMessages.removeIf(
                message -> WORLDZERO_TRACKED_FAKE_CHAT_LINES.contains(message.content().getString())
        );
        WORLDZERO_TRACKED_FAKE_CHAT_LINES.clear();
        if (removed) {
            accessor.worldzero$refreshTrimmedMessage();
        }
    }

    private static boolean worldzero$isHouseLevel(@Nullable Minecraft minecraft) {
        return minecraft != null
                && minecraft.player != null
                && minecraft.level != null
                && minecraft.level.dimension() == WorldZeroHouseDimension.WORLDZERO_HOUSE_LEVEL;
    }

    private static boolean worldzero$isBarrierProtectedLevel(@Nullable Minecraft minecraft) {
        return minecraft != null
                && minecraft.player != null
                && minecraft.level != null
                && (minecraft.level.dimension() == WorldZeroHouseDimension.WORLDZERO_HOUSE_LEVEL
                || minecraft.level.dimension() == WorldZeroHouseBadDimension.WORLDZERO_HOUSE_BAD_LEVEL);
    }
}
