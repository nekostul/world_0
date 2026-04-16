package ru.nekostul.worldzero;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import ru.nekostul.worldzero.mixin.ChatComponentAccessor;

import java.util.List;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroBlankDiscClientController {
    private static final String WORLDZERO_ERROR_CHAT_MESSAGE =
            "Internal Exception: java.lang.IllegalStateException: playback ended but signal still active\n"
                    + "source not cleared from memory\n"
                    + "recommended action: destro—";
    private static final int WORLDZERO_ERROR_CHAT_LIFETIME_TICKS = 10 * 20;
    private static final ResourceLocation WORLDZERO_FAKE_DISC_SOUND_ID = new ResourceLocation(
            WorldZeroMod.MOD_ID,
            "fake_disc11"
    );
    private static SoundInstance worldzero$activePlayback;
    private static boolean worldzero$playbackActive;
    private static int worldzero$errorChatTicksRemaining;

    private WorldZeroBlankDiscClientController() {
    }

    public static void worldzero$startPlayback() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundManager() == null) {
            return;
        }

        worldzero$clearState();
        SoundInstance soundInstance = new SimpleSoundInstance(
                WORLDZERO_FAKE_DISC_SOUND_ID,
                SoundSource.RECORDS,
                1.0F,
                1.0F,
                RandomSource.create(),
                false,
                0,
                SoundInstance.Attenuation.NONE,
                0.0D,
                0.0D,
                0.0D,
                true
        );
        minecraft.getSoundManager().play(soundInstance);
        worldzero$activePlayback = soundInstance;
        worldzero$playbackActive = true;
    }

    public static boolean worldzero$shouldBlockEscape(int key, int action) {
        return worldzero$playbackActive
                && key == GLFW.GLFW_KEY_ESCAPE
                && action != GLFW.GLFW_RELEASE;
    }

    public static boolean worldzero$isPauseBlocked() {
        return worldzero$playbackActive;
    }

    public static void worldzero$showPlaybackErrorMessage() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) {
            return;
        }

        worldzero$removePlaybackErrorMessage();
        minecraft.gui.getChat().addMessage(Component.literal(WORLDZERO_ERROR_CHAT_MESSAGE));
        worldzero$errorChatTicksRemaining = WORLDZERO_ERROR_CHAT_LIFETIME_TICKS;
    }

    @SubscribeEvent
    public static void worldzero$onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (worldzero$errorChatTicksRemaining > 0) {
            worldzero$errorChatTicksRemaining--;
            if (worldzero$errorChatTicksRemaining <= 0) {
                worldzero$removePlaybackErrorMessage();
            }
        }

        if (!worldzero$playbackActive) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundManager() == null || worldzero$activePlayback == null) {
            worldzero$clearPlaybackState();
            return;
        }

        if (!minecraft.getSoundManager().isActive(worldzero$activePlayback)) {
            WorldZeroNetwork.sendBlankDiscPlaybackFinished();
            worldzero$clearPlaybackState();
        }
    }

    @SubscribeEvent
    public static void worldzero$onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        worldzero$clearState();
    }

    private static void worldzero$removePlaybackErrorMessage() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) {
            return;
        }

        ChatComponent chat = minecraft.gui.getChat();
        ChatComponentAccessor accessor = (ChatComponentAccessor) (Object) chat;
        List<GuiMessage> allMessages = accessor.worldzero$getAllMessages();
        boolean removed = allMessages.removeIf(
                message -> WORLDZERO_ERROR_CHAT_MESSAGE.equals(message.content().getString())
        );
        if (removed) {
            accessor.worldzero$refreshTrimmedMessage();
        }
    }

    private static void worldzero$clearPlaybackState() {
        worldzero$activePlayback = null;
        worldzero$playbackActive = false;
    }

    private static void worldzero$clearState() {
        worldzero$clearPlaybackState();
        worldzero$errorChatTicksRemaining = 0;
        worldzero$removePlaybackErrorMessage();
    }
}
