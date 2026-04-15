package ru.nekostul.worldzero;

import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.mixin.ChatComponentAccessor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroFallClientController {
    private static final String WORLDZERO_WARNING_CHAT_PREFIX = "<0> ";
    private static final int WORLDZERO_WARNING_CHAT_UPDATE_TICKS = 20;
    private static final String[] WORLDZERO_WARNING_CHAT_SEGMENTS = {
            "anchor_lost",
            "void_signal",
            "mem//corrupt",
            "bedrock_below",
            "dont_look_down",
            "falling=false",
            "frame::split"
    };
    private static final ResourceLocation WORLDZERO_FALL_SOUND_ID = new ResourceLocation(
            WorldZeroMod.MOD_ID,
            "falling"
    );
    private static final ResourceLocation WORLDZERO_WARNING_SOUND_ID = new ResourceLocation(
            WorldZeroMod.MOD_ID,
            "warning"
    );
    private static boolean worldzero$fallSoundPlayed;
    private static boolean worldzero$fallOverlayActive;
    private static boolean worldzero$warningRequested;
    private static int worldzero$warningStartDelayTicks;
    private static int worldzero$warningChatTicksUntilAppend;
    private static int worldzero$warningChatSegmentCount;
    private static boolean worldzero$volumeBoostActive;
    private static SoundInstance worldzero$activeFallSound;
    private static SoundInstance worldzero$activeWarningSound;
    private static final Map<SoundSource, Float> WORLDZERO_ORIGINAL_VOLUMES = new EnumMap<>(SoundSource.class);

    private WorldZeroFallClientController() {
    }

    public static void handleAction(byte action) {
        switch (action) {
            case WorldZeroFallClientPacket.WORLDZERO_ACTION_BEGIN -> {
                worldzero$reset();
                worldzero$warningRequested = true;
                worldzero$warningStartDelayTicks = 2;
            }
            case WorldZeroFallClientPacket.WORLDZERO_ACTION_START_FALL -> {
                worldzero$warningRequested = false;
                worldzero$warningStartDelayTicks = 0;
                worldzero$stopWarningSound();
                worldzero$removeWarningChatLine();
                worldzero$fallOverlayActive = true;
                worldzero$ensureGameCaptured();
                worldzero$playFallSound();
            }
            case WorldZeroFallClientPacket.WORLDZERO_ACTION_SHOW_DEATH -> {
                worldzero$warningRequested = false;
                worldzero$warningStartDelayTicks = 0;
                worldzero$removeWarningChatLine();
                worldzero$fallOverlayActive = false;
                worldzero$showFakeDeathScreen();
            }
            case WorldZeroFallClientPacket.WORLDZERO_ACTION_CLEAR -> worldzero$clearClientState();
            default -> {
            }
        }
    }

    public static boolean worldzero$isFallOverlayActive() {
        return worldzero$fallOverlayActive;
    }

    public static boolean worldzero$isFallPauseBlocked() {
        return worldzero$fallOverlayActive;
    }

    public static void worldzero$onFakeRespawnPressed() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.screen instanceof WorldZeroFakeDeathScreen) {
            minecraft.setScreen(null);
        }

        worldzero$clearClientState();
        WorldZeroNetwork.sendFallRespawn();
    }

    @SubscribeEvent
    public static void worldzero$onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        worldzero$clearClientState();
    }

    @SubscribeEvent
    public static void worldzero$onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (worldzero$fallOverlayActive && minecraft != null) {
            if (minecraft.screen != null) {
                minecraft.setScreen(null);
            }
            worldzero$ensureGameCaptured();
            worldzero$releaseFallControlKeys(minecraft.options);
        }

        if (worldzero$warningRequested && minecraft != null && minecraft.getSoundManager() != null) {
            if (worldzero$warningStartDelayTicks > 0) {
                worldzero$warningStartDelayTicks--;
            } else if (worldzero$activeWarningSound == null) {
                worldzero$playWarningSound();
            } else {
                worldzero$tickWarningChatLine();
            }
        }

        if (!worldzero$volumeBoostActive) {
            return;
        }

        if (minecraft == null || minecraft.getSoundManager() == null || worldzero$activeFallSound == null) {
            worldzero$restoreVolumes();
            return;
        }

        if (!minecraft.getSoundManager().isActive(worldzero$activeFallSound)) {
            worldzero$restoreVolumes();
        }
    }

    private static void worldzero$playFallSound() {
        if (worldzero$fallSoundPlayed) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundManager() == null) {
            return;
        }

        worldzero$boostVolumes(minecraft);
        BlockPos soundPos = minecraft.player != null ? minecraft.player.blockPosition() : BlockPos.ZERO;
        SimpleSoundInstance soundInstance = new SimpleSoundInstance(
                SoundEvent.createVariableRangeEvent(WORLDZERO_FALL_SOUND_ID),
                SoundSource.MASTER,
                1.0F,
                1.0F,
                RandomSource.create(),
                soundPos
        );
        minecraft.getSoundManager().play(soundInstance);
        worldzero$activeFallSound = soundInstance;
        worldzero$fallSoundPlayed = true;
    }

    private static void worldzero$playWarningSound() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundManager() == null) {
            return;
        }

        if (worldzero$activeWarningSound != null && minecraft.getSoundManager().isActive(worldzero$activeWarningSound)) {
            return;
        }

        worldzero$stopWarningSound();
        BlockPos soundPos = minecraft.player != null ? minecraft.player.blockPosition() : BlockPos.ZERO;
        SimpleSoundInstance soundInstance = new SimpleSoundInstance(
                SoundEvent.createVariableRangeEvent(WORLDZERO_WARNING_SOUND_ID),
                SoundSource.PLAYERS,
                1.0F,
                1.0F,
                RandomSource.create(),
                soundPos
        );
        minecraft.getSoundManager().play(soundInstance);
        worldzero$activeWarningSound = soundInstance;
        if (worldzero$warningChatSegmentCount <= 0) {
            worldzero$warningChatSegmentCount = 1;
        }
        worldzero$warningChatTicksUntilAppend = WORLDZERO_WARNING_CHAT_UPDATE_TICKS;
        worldzero$upsertWarningChatLine();
    }

    private static void worldzero$showFakeDeathScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        minecraft.setScreen(new WorldZeroFakeDeathScreen());
    }

    private static void worldzero$reset() {
        worldzero$fallSoundPlayed = false;
        worldzero$fallOverlayActive = false;
        worldzero$warningRequested = false;
        worldzero$warningStartDelayTicks = 0;
        worldzero$warningChatTicksUntilAppend = 0;
        worldzero$warningChatSegmentCount = 0;
        worldzero$stopWarningSound();
        worldzero$removeWarningChatLine();
        worldzero$restoreVolumes();
        worldzero$activeFallSound = null;
    }

    private static void worldzero$clearClientState() {
        worldzero$fallSoundPlayed = false;
        worldzero$fallOverlayActive = false;
        worldzero$warningRequested = false;
        worldzero$warningStartDelayTicks = 0;
        worldzero$warningChatTicksUntilAppend = 0;
        worldzero$warningChatSegmentCount = 0;
        worldzero$stopWarningSound();
        worldzero$removeWarningChatLine();
        worldzero$restoreVolumes();
        worldzero$activeFallSound = null;
    }

    @SubscribeEvent
    public static void worldzero$onScreenOpening(ScreenEvent.Opening event) {
        if (worldzero$isFallPauseBlocked() && event.getNewScreen() != null) {
            event.setCanceled(true);
        }
    }

    private static void worldzero$boostVolumes(Minecraft minecraft) {
        WORLDZERO_ORIGINAL_VOLUMES.clear();
        for (SoundSource source : SoundSource.values()) {
            float originalVolume = minecraft.options.getSoundSourceVolume(source);
            WORLDZERO_ORIGINAL_VOLUMES.put(source, originalVolume);
            minecraft.options.getSoundSourceOptionInstance(source).set(1.0D);
            minecraft.getSoundManager().updateSourceVolume(source, 1.0F);
        }
        worldzero$volumeBoostActive = true;
    }

    private static void worldzero$restoreVolumes() {
        if (!worldzero$volumeBoostActive) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getSoundManager() != null) {
            for (Map.Entry<SoundSource, Float> entry : WORLDZERO_ORIGINAL_VOLUMES.entrySet()) {
                minecraft.options.getSoundSourceOptionInstance(entry.getKey()).set((double) entry.getValue());
                minecraft.getSoundManager().updateSourceVolume(entry.getKey(), entry.getValue());
            }
        }

        WORLDZERO_ORIGINAL_VOLUMES.clear();
        worldzero$volumeBoostActive = false;
    }

    private static void worldzero$stopWarningSound() {
        if (worldzero$activeWarningSound == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().stop(worldzero$activeWarningSound);
        }
        worldzero$activeWarningSound = null;
    }

    private static void worldzero$releaseFallControlKeys(Options options) {
        options.keyUse.setDown(false);
        options.keyInventory.setDown(false);
        options.keyAttack.setDown(false);
        options.keyPickItem.setDown(false);
        options.keyDrop.setDown(false);
        options.keySwapOffhand.setDown(false);
        options.keySaveHotbarActivator.setDown(false);
        options.keyLoadHotbarActivator.setDown(false);
        for (int index = 0; index < options.keyHotbarSlots.length; index++) {
            options.keyHotbarSlots[index].setDown(false);
        }
    }

    private static void worldzero$ensureGameCaptured() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        if (minecraft.screen != null) {
            minecraft.setScreen(null);
        }

        if (!minecraft.mouseHandler.isMouseGrabbed()) {
            minecraft.mouseHandler.grabMouse();
        }
    }

    private static void worldzero$tickWarningChatLine() {
        if (worldzero$warningChatSegmentCount <= 0) {
            worldzero$warningChatSegmentCount = 1;
            worldzero$warningChatTicksUntilAppend = WORLDZERO_WARNING_CHAT_UPDATE_TICKS;
            worldzero$upsertWarningChatLine();
            return;
        }

        worldzero$warningChatTicksUntilAppend--;
        if (worldzero$warningChatTicksUntilAppend > 0) {
            return;
        }

        worldzero$warningChatSegmentCount++;
        worldzero$warningChatTicksUntilAppend = WORLDZERO_WARNING_CHAT_UPDATE_TICKS;
        worldzero$upsertWarningChatLine();
    }

    private static void worldzero$upsertWarningChatLine() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) {
            return;
        }

        ChatComponent chat = minecraft.gui.getChat();
        ChatComponentAccessor accessor = (ChatComponentAccessor) (Object) chat;
        List<GuiMessage> allMessages = accessor.worldzero$getAllMessages();
        allMessages.removeIf(message -> message.content().getString().startsWith(WORLDZERO_WARNING_CHAT_PREFIX));
        allMessages.add(0, new GuiMessage(
                minecraft.gui.getGuiTicks(),
                worldzero$buildWarningChatComponent(),
                null,
                null
        ));
        accessor.worldzero$refreshTrimmedMessage();
    }

    private static void worldzero$removeWarningChatLine() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) {
            return;
        }

        ChatComponent chat = minecraft.gui.getChat();
        ChatComponentAccessor accessor = (ChatComponentAccessor) (Object) chat;
        boolean removed = accessor.worldzero$getAllMessages().removeIf(
                message -> message.content().getString().startsWith(WORLDZERO_WARNING_CHAT_PREFIX)
        );
        if (removed) {
            accessor.worldzero$refreshTrimmedMessage();
        }
    }

    private static Component worldzero$buildWarningChatComponent() {
        MutableComponent message = Component.literal(WORLDZERO_WARNING_CHAT_PREFIX).withStyle(ChatFormatting.DARK_RED);
        int segmentCount = Math.max(1, worldzero$warningChatSegmentCount);
        for (int index = 0; index < segmentCount; index++) {
            String segment = WORLDZERO_WARNING_CHAT_SEGMENTS[index % WORLDZERO_WARNING_CHAT_SEGMENTS.length];
            if (index > 0) {
                message = message.append(Component.literal(" "));
            }
            message = message.append(Component.literal(segment).withStyle(ChatFormatting.DARK_RED, ChatFormatting.OBFUSCATED));
        }
        return message;
    }
}
