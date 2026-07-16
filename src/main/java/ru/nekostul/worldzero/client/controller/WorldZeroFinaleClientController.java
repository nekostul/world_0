package ru.nekostul.worldzero.client.controller;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;
import ru.nekostul.worldzero.client.screen.WorldZeroFinalMenuScreen;
import ru.nekostul.worldzero.network.WorldZeroFinalePacket;
import ru.nekostul.worldzero.state.WorldZeroState;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroFinaleClientController {
    private static final int WORLDZERO_ABSOLUTE_SOUND_DELAY_TICKS = 2;
    private static int worldzero$silenceTicks;
    private static int worldzero$glitchTicks;
    private static int worldzero$glitchDurationTicks;
    private static int worldzero$glitchIntensity;
    private static int worldzero$glitchSeed;
    private static int worldzero$soundBreakTicks;
    private static int worldzero$soundBreakSeed;
    private static int worldzero$fullFreezeTicks;
    private static final ResourceLocation WORLDZERO_FALL_SOUND_ID = new ResourceLocation(
            WorldZeroMod.MOD_ID,
            "falling"
    );
    private static int worldzero$absoluteAttackTicks;
    private static int worldzero$absoluteAttackDurationTicks;
    private static int worldzero$absoluteAttackSeed;
    private static int worldzero$absoluteAttackSoundDelayTicks;
    private static SoundInstance worldzero$absoluteAttackSound;

    private WorldZeroFinaleClientController() {
    }

    public static void worldzero$trigger(byte action, int durationTicks, int variant) {
        if (action == WorldZeroFinalePacket.WORLDZERO_ACTION_CLEAR_ALL) {
            worldzero$clearState();
            WorldZeroFreezeClientController.finishFreeze();
            return;
        }

        int safeDurationTicks = Math.max(1, durationTicks);
        switch (action) {
            case WorldZeroFinalePacket.WORLDZERO_ACTION_START_SILENCE -> {
                worldzero$silenceTicks = safeDurationTicks;
                worldzero$stopAllSounds();
            }
            case WorldZeroFinalePacket.WORLDZERO_ACTION_GLITCH -> {
                worldzero$glitchTicks = safeDurationTicks;
                worldzero$glitchDurationTicks = safeDurationTicks;
                worldzero$glitchIntensity = Mth.clamp(variant, 1, 4);
                worldzero$glitchSeed = variant * 997 + safeDurationTicks * 31 + worldzero$glitchSeed + 17;
            }
            case WorldZeroFinalePacket.WORLDZERO_ACTION_SOUND_BREAK -> {
                worldzero$soundBreakTicks = safeDurationTicks;
                worldzero$soundBreakSeed = variant * 131 + safeDurationTicks * 7;
            }
            case WorldZeroFinalePacket.WORLDZERO_ACTION_FULL_FREEZE -> {
                worldzero$glitchTicks = 0;
                worldzero$soundBreakTicks = 0;
                worldzero$fullFreezeTicks = safeDurationTicks;
                worldzero$stopAllSounds();
            }
            case WorldZeroFinalePacket.WORLDZERO_ACTION_EXIT_TO_MENU -> worldzero$exitToMenu();
            case WorldZeroFinalePacket.WORLDZERO_ACTION_FINAL_BLACK_MENU -> worldzero$showFinalBlackMenu(variant != 0);
            case WorldZeroFinalePacket.WORLDZERO_ACTION_FORCE_MAX_VOLUME -> worldzero$forceMaxVolume();
            case WorldZeroFinalePacket.WORLDZERO_ACTION_ABSOLUTE_ATTACK -> worldzero$startAbsoluteAttack(safeDurationTicks, variant);
            default -> {
            }
        }
    }

    public static boolean worldzero$isFullSilenceActive() {
        return (worldzero$silenceTicks > 0 || worldzero$fullFreezeTicks > 0) && worldzero$absoluteAttackTicks <= 0;
    }

    @SubscribeEvent
    public static void worldzero$onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (worldzero$silenceTicks > 0) {
            worldzero$stopAllSounds();
            worldzero$silenceTicks--;
        }
        if (worldzero$glitchTicks > 0) {
            worldzero$glitchTicks--;
        }
        if (worldzero$soundBreakTicks > 0) {
            if (worldzero$shouldBreakSound(worldzero$soundBreakTicks)) {
                worldzero$stopAllSounds();
            }
            worldzero$soundBreakTicks--;
        }
        if (worldzero$fullFreezeTicks > 0) {
            if (worldzero$absoluteAttackTicks <= 0) {
                worldzero$stopAllSounds();
            }
            worldzero$fullFreezeTicks--;
        }
        if (worldzero$absoluteAttackTicks > 0) {
            worldzero$forceMaxVolume();
            if (worldzero$absoluteAttackSoundDelayTicks > 0) {
                worldzero$absoluteAttackSoundDelayTicks--;
            } else {
                worldzero$ensureAbsoluteAttackSound();
            }
            worldzero$absoluteAttackTicks--;
            if (worldzero$absoluteAttackTicks <= 0) {
                worldzero$stopAbsoluteAttackSound();
            }
        }
    }

    @SubscribeEvent
    public static void worldzero$onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }

        if (worldzero$absoluteAttackTicks > 0) {
            worldzero$renderAbsoluteAttackOverlay(event.getGuiGraphics());
        }
        if (worldzero$fullFreezeTicks > 0) {
            return;
        }
        if (worldzero$glitchTicks > 0) {
            worldzero$renderGlitch(event.getGuiGraphics());
        }
    }

    @SubscribeEvent
    public static void worldzero$onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        worldzero$clearState();
    }

    @SubscribeEvent
    public static void worldzero$onScreenOpening(ScreenEvent.Opening event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null
                && minecraft.screen instanceof SelectWorldScreen
                && event.getNewScreen() instanceof WorldZeroFinalMenuScreen) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onScreenInit(ScreenEvent.Init.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null
                || !(event.getScreen() instanceof SelectWorldScreen)
                || !WorldZeroState.hasFinalWorldCreationLock(minecraft)) {
            return;
        }

        String createWorldText = net.minecraft.network.chat.Component.translatable("selectWorld.create").getString();
        int expectedX = event.getScreen().width / 2 + 4;
        int expectedY = event.getScreen().height - 52;
        for (net.minecraft.client.gui.components.events.GuiEventListener listener : event.getListenersList()) {
            if (!(listener instanceof Button button)) {
                continue;
            }

            if (button.getX() == expectedX
                    && button.getY() == expectedY
                    && button.getWidth() == 150
                    && button.getHeight() == 20
                    && createWorldText.equals(button.getMessage().getString())) {
                button.active = false;
            }
        }
    }

    private static boolean worldzero$shouldBreakSound(int ticksRemaining) {
        int pulse = Math.abs(ticksRemaining * 13 + worldzero$soundBreakSeed);
        return pulse % 37 < 4 || pulse % 53 == 0;
    }

    private static void worldzero$renderGlitch(GuiGraphics guiGraphics) {
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        float alphaScale = worldzero$fade(worldzero$glitchTicks, worldzero$glitchDurationTicks);
        int baseAlpha = Mth.clamp((int) ((16 + worldzero$glitchIntensity * 13) * alphaScale), 0, 84);
        int pulse = (worldzero$glitchTicks / Math.max(2, 7 - worldzero$glitchIntensity)) & 1;
        int washColor = (baseAlpha << 24) | (pulse == 0 ? 0x080808 : 0xA0A0A0);
        guiGraphics.fill(0, 0, width, height, washColor);

        int stripCount = 12 + worldzero$glitchIntensity * 10;
        for (int index = 0; index < stripCount; index++) {
            int seed = Math.abs(worldzero$glitchSeed + index * 97 + worldzero$glitchTicks * (5 + worldzero$glitchIntensity));
            int y = seed % Math.max(1, height);
            int stripHeight = 1 + (seed / 7) % (2 + worldzero$glitchIntensity);
            int xOffset = ((seed / 11) % Math.max(1, width / 3)) - width / 6;
            int alpha = Mth.clamp((int) ((18 + index % 5 * 7 + worldzero$glitchIntensity * 8) * alphaScale), 0, 96);
            int color = (alpha << 24) | (index % 3 == 0 ? 0x111111 : index % 3 == 1 ? 0xB8B8B8 : 0xE8E8E8);
            guiGraphics.fill(Math.max(0, xOffset), y, Math.min(width, width + xOffset), Math.min(height, y + stripHeight), color);
        }

        int blockCount = 3 + worldzero$glitchIntensity * 5;
        for (int index = 0; index < blockCount; index++) {
            int seed = Math.abs(worldzero$glitchSeed * 3 + index * 173 + worldzero$glitchTicks * 19);
            int boxWidth = Math.max(8, width / (28 - worldzero$glitchIntensity * 3));
            int boxHeight = Math.max(4, height / (38 - worldzero$glitchIntensity * 4));
            int x = seed % Math.max(1, width);
            int y = (seed / 17) % Math.max(1, height);
            int alpha = Mth.clamp((int) ((22 + worldzero$glitchIntensity * 12) * alphaScale), 0, 90);
            guiGraphics.fill(x, y, Math.min(width, x + boxWidth), Math.min(height, y + boxHeight), alpha << 24);
        }
    }

    private static void worldzero$renderAbsoluteAttackOverlay(GuiGraphics guiGraphics) {
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        int elapsedTicks = Math.max(0, worldzero$absoluteAttackDurationTicks - worldzero$absoluteAttackTicks);
        int pulse = Math.abs(worldzero$absoluteAttackSeed + elapsedTicks * 97);
        int shakeX = pulse % 11 - 5;
        int shakeY = (pulse / 11) % 9 - 4;
        int alpha = 72 + (pulse % 40);
        guiGraphics.fill(0, 0, width, height, (alpha << 24) | 0xAA0000);

        for (int index = 0; index < 12; index++) {
            int seed = Math.abs(worldzero$absoluteAttackSeed + index * 181 + elapsedTicks * 53);
            int y = seed % Math.max(1, height);
            int stripHeight = 1 + seed % 4;
            int xOffset = shakeX + (seed / 17) % 13 - 6;
            int stripAlpha = 52 + seed % 54;
            int stripTop = Mth.clamp(y + shakeY, 0, height);
            int stripBottom = Mth.clamp(stripTop + stripHeight, 0, height);
            if (stripBottom <= stripTop) {
                continue;
            }
            guiGraphics.fill(
                    Math.min(0, xOffset) - 8,
                    stripTop,
                    Math.max(width, width + xOffset) + 8,
                    stripBottom,
                    (stripAlpha << 24) | 0xFF1010
            );
        }

        int edgeAlpha = 70 + (pulse % 48);
        guiGraphics.fill(0, 0, Math.min(width, 8 + Math.abs(shakeX)), height, (edgeAlpha << 24) | 0x7A0000);
        guiGraphics.fill(Math.max(0, width - 8 - Math.abs(shakeY)), 0, width, height, (edgeAlpha << 24) | 0x7A0000);
    }

    private static float worldzero$fade(int ticksRemaining, int durationTicks) {
        if (durationTicks <= 0) {
            return 0.0F;
        }

        float progress = Mth.clamp((float) ticksRemaining / (float) durationTicks, 0.0F, 1.0F);
        return Mth.clamp(Math.min(progress * 1.8F, (1.0F - progress) * 2.0F + 0.25F), 0.0F, 1.0F);
    }

    private static void worldzero$stopAllSounds() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().stop();
        }
    }

    private static void worldzero$exitToMenu() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        worldzero$clearState();
        WorldZeroFreezeClientController.finishFreeze();
        worldzero$stopAllSounds();
        WorldZeroState.writeFinaleReconnectStage(minecraft, 1);
        WorldZeroState.markFinalWorldCreationLock(minecraft);
        if (minecraft.level != null) {
            minecraft.level.disconnect();
        }
        minecraft.clearLevel(new TitleScreen());
    }

    private static void worldzero$showFinalBlackMenu(boolean quitOnly) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        worldzero$clearState();
        WorldZeroFreezeClientController.finishFreeze();
        WorldZeroState.clearFinaleReconnectStage(minecraft);
        worldzero$stopAllSounds();
        if (minecraft.level != null) {
            minecraft.level.disconnect();
        }
        minecraft.clearLevel(new WorldZeroFinalMenuScreen(quitOnly));
    }

    private static void worldzero$forceMaxVolume() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundManager() == null) {
            return;
        }

        for (SoundSource source : SoundSource.values()) {
            minecraft.options.getSoundSourceOptionInstance(source).set(1.0D);
            minecraft.getSoundManager().updateSourceVolume(source, 1.0F);
        }
    }

    private static void worldzero$startAbsoluteAttack(int durationTicks, int seed) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundManager() == null) {
            return;
        }

        worldzero$absoluteAttackTicks = Math.max(1, durationTicks);
        worldzero$absoluteAttackDurationTicks = worldzero$absoluteAttackTicks;
        worldzero$absoluteAttackSeed = seed;
        worldzero$absoluteAttackSoundDelayTicks = WORLDZERO_ABSOLUTE_SOUND_DELAY_TICKS;
        worldzero$forceMaxVolume();
        worldzero$stopAbsoluteAttackSound();
        worldzero$ensureAbsoluteAttackSound();
    }

    private static void worldzero$ensureAbsoluteAttackSound() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundManager() == null) {
            return;
        }

        if (worldzero$absoluteAttackSound != null && minecraft.getSoundManager().isActive(worldzero$absoluteAttackSound)) {
            return;
        }

        BlockPos soundPos = minecraft.player != null ? minecraft.player.blockPosition() : BlockPos.ZERO;
        SimpleSoundInstance soundInstance = new SimpleSoundInstance(
                SoundEvent.createVariableRangeEvent(WORLDZERO_FALL_SOUND_ID),
                SoundSource.PLAYERS,
                1.0F,
                1.0F,
                RandomSource.create(),
                soundPos
        );
        minecraft.getSoundManager().play(soundInstance);
        worldzero$absoluteAttackSound = soundInstance;
    }

    private static void worldzero$stopAbsoluteAttackSound() {
        if (worldzero$absoluteAttackSound == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().stop(worldzero$absoluteAttackSound);
        }
        worldzero$absoluteAttackSound = null;
    }

    private static void worldzero$clearState() {
        worldzero$silenceTicks = 0;
        worldzero$glitchTicks = 0;
        worldzero$glitchDurationTicks = 0;
        worldzero$glitchIntensity = 0;
        worldzero$soundBreakTicks = 0;
        worldzero$fullFreezeTicks = 0;
        worldzero$absoluteAttackTicks = 0;
        worldzero$absoluteAttackDurationTicks = 0;
        worldzero$absoluteAttackSoundDelayTicks = 0;
        worldzero$stopAbsoluteAttackSound();
    }
}
