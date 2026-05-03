package ru.nekostul.worldzero;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroSkyWatchClientController {
    private static final ResourceLocation WORLDZERO_DANGER_SOUND_ID = new ResourceLocation(WorldZeroMod.MOD_ID, "danger");
    private static final String WORLDZERO_DISTANCE_KEY = "message.worldzero.sky_watch.distance";
    private static final String WORLDZERO_HIDE_KEY = "message.worldzero.sky_watch.hide";
    private static final String WORLDZERO_RUN_KEY = "message.worldzero.sky_watch.run";
    private static final int WORLDZERO_FAST_PHASE_TICKS = 10 * 20;
    private static final int WORLDZERO_SLOW_PHASE_TICKS = 18 * 20;
    private static final int WORLDZERO_WARNING_START_TICKS = 12 * 20;
    private static final int WORLDZERO_MIN_PROMPT_DELAY_TICKS = 5;
    private static final int WORLDZERO_MAX_PROMPT_DELAY_TICKS = 15;
    private static final int WORLDZERO_MAX_ACTIVE_PROMPTS = 10;
    private static final int WORLDZERO_PROMPT_MIN_LIFETIME_TICKS = 55;
    private static final int WORLDZERO_PROMPT_MAX_LIFETIME_TICKS = 110;
    private static final int WORLDZERO_COUNTER_START = 1300;
    private static final int WORLDZERO_COUNTER_FAST_END = 50;
    private static final int WORLDZERO_COUNTER_FINAL_END = 5;

    private static int worldzero$activeTicks;
    private static int worldzero$durationTicks;
    private static int worldzero$seed;
    private static int worldzero$nextPromptTicks;
    private static RandomSource worldzero$promptRandom;
    private static SoundInstance worldzero$dangerSound;
    private static boolean worldzero$internalVolumeUpdate;
    private static final Map<SoundSource, Float> WORLDZERO_ORIGINAL_VOLUMES = new EnumMap<>(SoundSource.class);
    private static final List<Prompt> WORLDZERO_PROMPTS = new ArrayList<>();

    private WorldZeroSkyWatchClientController() {
    }

    public static void worldzero$trigger(byte action, int durationTicks, int variant) {
        if (action == WorldZeroSkyWatchPacket.WORLDZERO_ACTION_CLEAR) {
            worldzero$clearState();
            return;
        }

        if (action != WorldZeroSkyWatchPacket.WORLDZERO_ACTION_START) {
            return;
        }

        worldzero$clearState();
        worldzero$activeTicks = Math.max(1, durationTicks);
        worldzero$durationTicks = worldzero$activeTicks;
        worldzero$seed = variant;
        worldzero$promptRandom = RandomSource.create((long) variant * 341873128712L + 132897987541L);
        worldzero$nextPromptTicks = worldzero$randomPromptDelay();
        worldzero$startVolumeBoost();
        worldzero$playDangerSound();
    }

    public static boolean worldzero$isActive() {
        return worldzero$activeTicks > 0;
    }

    public static boolean worldzero$isPauseBlocked() {
        return worldzero$isActive();
    }

    public static boolean worldzero$isVolumeForced() {
        return worldzero$isActive() || !WORLDZERO_ORIGINAL_VOLUMES.isEmpty();
    }

    public static boolean worldzero$shouldBlockSoundOptionChange(@Nullable OptionInstance<?> optionInstance, @Nullable Object value) {
        Minecraft minecraft = Minecraft.getInstance();
        if (worldzero$internalVolumeUpdate
                || !worldzero$isVolumeForced()
                || !(value instanceof Double doubleValue)
                || doubleValue >= 0.999D
                || minecraft == null
                || minecraft.options == null
                || optionInstance == null) {
            return false;
        }

        for (SoundSource source : SoundSource.values()) {
            if (source == SoundSource.MUSIC) {
                continue;
            }

            if (minecraft.options.getSoundSourceOptionInstance(source) == optionInstance) {
                return true;
            }
        }

        return false;
    }

    @SubscribeEvent
    public static void worldzero$onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (worldzero$activeTicks <= 0) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.screen != null && minecraft.screen.isPauseScreen()) {
            minecraft.setScreen(null);
            if (!minecraft.mouseHandler.isMouseGrabbed()) {
                minecraft.mouseHandler.grabMouse();
            }
        }

        worldzero$enforceVolumes(minecraft);

        worldzero$activeTicks--;
        int elapsedTicks = Math.max(0, worldzero$durationTicks - worldzero$activeTicks);
        if (elapsedTicks >= WORLDZERO_WARNING_START_TICKS) {
            if (worldzero$nextPromptTicks > 0) {
                worldzero$nextPromptTicks--;
            }
            if (worldzero$nextPromptTicks <= 0) {
                worldzero$spawnPrompt();
                worldzero$nextPromptTicks = worldzero$randomPromptDelay();
            }
        }

        Iterator<Prompt> iterator = WORLDZERO_PROMPTS.iterator();
        while (iterator.hasNext()) {
            Prompt prompt = iterator.next();
            prompt.worldzero$ticksRemaining--;
            if (prompt.worldzero$ticksRemaining <= 0) {
                iterator.remove();
            }
        }

        if (worldzero$activeTicks <= 0) {
            worldzero$clearState();
        }
    }

    @SubscribeEvent
    public static void worldzero$onRenderGui(RenderGuiEvent.Post event) {
        if (!worldzero$isActive()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        worldzero$renderCounter(guiGraphics, minecraft.font);
        worldzero$renderPrompts(guiGraphics, minecraft.font);
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
    public static void worldzero$onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        worldzero$clearState();
    }

    private static void worldzero$renderCounter(GuiGraphics guiGraphics, Font font) {
        int elapsedTicks = Math.max(0, worldzero$durationTicks - worldzero$activeTicks);
        int counterValue = worldzero$resolveCounterValue(elapsedTicks);
        Component counter = Component.translatable(WORLDZERO_DISTANCE_KEY, counterValue);
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        float pulse = 0.88F + (Mth.sin((elapsedTicks + worldzero$seed) * 0.12F) * 0.5F + 0.5F) * 0.16F;
        float scale = elapsedTicks < WORLDZERO_FAST_PHASE_TICKS ? 1.18F + pulse * 0.1F : 1.03F + pulse * 0.05F;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0F);
        int drawX = Mth.floor((width / 2.0F - font.width(counter) * scale / 2.0F) / scale);
        int drawY = Mth.floor((height - 64.0F - font.lineHeight * scale) / scale);
        guiGraphics.drawString(font, counter, drawX, drawY, 0xFFF4F4F4, true);
        guiGraphics.pose().popPose();
    }

    private static void worldzero$renderPrompts(GuiGraphics guiGraphics, Font font) {
        for (Prompt prompt : WORLDZERO_PROMPTS) {
            String text = Component.translatable(prompt.worldzero$messageKey).getString();
            float alphaProgress = Mth.clamp((float) prompt.worldzero$ticksRemaining / (float) prompt.worldzero$maxTicks, 0.0F, 1.0F);
            int alpha = Mth.clamp((int) (70 + alphaProgress * 165.0F), 70, 235);
            int color = (alpha << 24) | prompt.worldzero$rgbColor;

            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(prompt.worldzero$scale, prompt.worldzero$scale, 1.0F);
            int drawX = Mth.floor(prompt.worldzero$x / prompt.worldzero$scale);
            int drawY = Mth.floor(prompt.worldzero$y / prompt.worldzero$scale);
            guiGraphics.drawString(font, text, drawX, drawY, color, true);
            guiGraphics.pose().popPose();
        }
    }

    private static int worldzero$resolveCounterValue(int elapsedTicks) {
        if (elapsedTicks <= 0) {
            return WORLDZERO_COUNTER_START;
        }

        if (elapsedTicks < WORLDZERO_FAST_PHASE_TICKS) {
            float progress = (float) elapsedTicks / (float) WORLDZERO_FAST_PHASE_TICKS;
            float eased = 1.0F - (float) Math.pow(1.0F - progress, 2.65D);
            return Mth.clamp(
                    Math.round(Mth.lerp(eased, WORLDZERO_COUNTER_START, WORLDZERO_COUNTER_FAST_END)),
                    WORLDZERO_COUNTER_FAST_END,
                    WORLDZERO_COUNTER_START
            );
        }

        if (elapsedTicks < WORLDZERO_FAST_PHASE_TICKS + WORLDZERO_SLOW_PHASE_TICKS) {
            float progress = (float) (elapsedTicks - WORLDZERO_FAST_PHASE_TICKS) / (float) WORLDZERO_SLOW_PHASE_TICKS;
            float eased = 1.0F - (float) Math.pow(1.0F - progress, 1.35D);
            return Mth.clamp(
                    Math.round(Mth.lerp(eased, WORLDZERO_COUNTER_FAST_END, WORLDZERO_COUNTER_FINAL_END)),
                    WORLDZERO_COUNTER_FINAL_END,
                    WORLDZERO_COUNTER_FAST_END
            );
        }

        return WORLDZERO_COUNTER_FINAL_END;
    }

    private static void worldzero$spawnPrompt() {
        if (worldzero$promptRandom == null) {
            worldzero$promptRandom = RandomSource.create();
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return;
        }

        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        boolean leftSide = worldzero$promptRandom.nextBoolean();
        float scale = Mth.nextFloat(worldzero$promptRandom, 0.82F, 1.38F);
        int x = leftSide
                ? Mth.floor(width * Mth.nextFloat(worldzero$promptRandom, 0.07F, 0.30F))
                : Mth.floor(width * Mth.nextFloat(worldzero$promptRandom, 0.68F, 0.90F));
        int y = Mth.floor(height * Mth.nextFloat(worldzero$promptRandom, 0.16F, 0.74F));
        int lifetime = worldzero$promptRandom.nextInt(WORLDZERO_PROMPT_MAX_LIFETIME_TICKS - WORLDZERO_PROMPT_MIN_LIFETIME_TICKS + 1)
                + WORLDZERO_PROMPT_MIN_LIFETIME_TICKS;
        String key = worldzero$promptRandom.nextBoolean() ? WORLDZERO_HIDE_KEY : WORLDZERO_RUN_KEY;
        int rgb = worldzero$promptRandom.nextBoolean() ? 0xFFFFFF : 0xFF4040;

        WORLDZERO_PROMPTS.add(new Prompt(key, x, y, scale, rgb, lifetime));
        while (WORLDZERO_PROMPTS.size() > WORLDZERO_MAX_ACTIVE_PROMPTS) {
            WORLDZERO_PROMPTS.remove(0);
        }
    }

    private static int worldzero$randomPromptDelay() {
        if (worldzero$promptRandom == null) {
            worldzero$promptRandom = RandomSource.create();
        }

        return worldzero$promptRandom.nextInt(
                WORLDZERO_MAX_PROMPT_DELAY_TICKS - WORLDZERO_MIN_PROMPT_DELAY_TICKS + 1
        ) + WORLDZERO_MIN_PROMPT_DELAY_TICKS;
    }

    private static void worldzero$playDangerSound() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundManager() == null) {
            return;
        }

        worldzero$stopDangerSound();
        SoundInstance soundInstance = new SimpleSoundInstance(
                WORLDZERO_DANGER_SOUND_ID,
                SoundSource.MASTER,
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
        worldzero$dangerSound = soundInstance;
    }

    private static void worldzero$startVolumeBoost() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null || minecraft.getSoundManager() == null) {
            return;
        }

        WORLDZERO_ORIGINAL_VOLUMES.clear();
        for (SoundSource source : SoundSource.values()) {
            if (source == SoundSource.MUSIC) {
                continue;
            }
            WORLDZERO_ORIGINAL_VOLUMES.put(source, minecraft.options.getSoundSourceVolume(source));
        }
        worldzero$enforceVolumes(minecraft);
    }

    private static void worldzero$enforceVolumes(@Nullable Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null || minecraft.getSoundManager() == null) {
            return;
        }

        for (SoundSource source : SoundSource.values()) {
            if (source == SoundSource.MUSIC) {
                continue;
            }
            if (minecraft.options.getSoundSourceVolume(source) != 1.0F) {
                worldzero$setSourceVolume(minecraft, source, 1.0F);
            }
            minecraft.getSoundManager().updateSourceVolume(source, 1.0F);
        }
    }

    private static void worldzero$restoreVolumes() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null || minecraft.getSoundManager() == null) {
            WORLDZERO_ORIGINAL_VOLUMES.clear();
            return;
        }

        for (Map.Entry<SoundSource, Float> entry : WORLDZERO_ORIGINAL_VOLUMES.entrySet()) {
            worldzero$setSourceVolume(minecraft, entry.getKey(), entry.getValue());
            minecraft.getSoundManager().updateSourceVolume(entry.getKey(), entry.getValue());
        }
        WORLDZERO_ORIGINAL_VOLUMES.clear();
    }

    private static void worldzero$setSourceVolume(Minecraft minecraft, SoundSource source, float volume) {
        worldzero$internalVolumeUpdate = true;
        try {
            minecraft.options.getSoundSourceOptionInstance(source).set((double) volume);
        } finally {
            worldzero$internalVolumeUpdate = false;
        }
    }

    private static void worldzero$stopDangerSound() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getSoundManager() != null && worldzero$dangerSound != null) {
            minecraft.getSoundManager().stop(worldzero$dangerSound);
        }
        worldzero$dangerSound = null;
    }

    private static void worldzero$clearState() {
        worldzero$activeTicks = 0;
        worldzero$durationTicks = 0;
        worldzero$seed = 0;
        worldzero$nextPromptTicks = 0;
        worldzero$promptRandom = null;
        WORLDZERO_PROMPTS.clear();
        worldzero$stopDangerSound();
        worldzero$restoreVolumes();
    }

    private static final class Prompt {
        private final String worldzero$messageKey;
        private final int worldzero$x;
        private final int worldzero$y;
        private final float worldzero$scale;
        private final int worldzero$rgbColor;
        private final int worldzero$maxTicks;
        private int worldzero$ticksRemaining;

        private Prompt(String messageKey, int x, int y, float scale, int rgbColor, int maxTicks) {
            this.worldzero$messageKey = messageKey;
            this.worldzero$x = x;
            this.worldzero$y = y;
            this.worldzero$scale = scale;
            this.worldzero$rgbColor = rgbColor;
            this.worldzero$maxTicks = maxTicks;
            this.worldzero$ticksRemaining = maxTicks;
        }
    }
}
