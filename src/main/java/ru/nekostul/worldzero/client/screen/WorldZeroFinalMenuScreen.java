package ru.nekostul.worldzero.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

public final class WorldZeroFinalMenuScreen extends Screen {
    private static final int WORLDZERO_SINGLEPLAYER_ACTIVE_TICK = 420;
    private static final String WORLDZERO_SANEK_FAKE = "sanek0002";
    private static final String WORLDZERO_SANEK_REAL = "sanek0001";
    private static final String WORLDZERO_FINAL_MENU_LINE_0 = "message.worldzero.final_menu.line.0";
    private static final String WORLDZERO_FINAL_MENU_LINE_1 = "message.worldzero.final_menu.line.1";
    private static final String WORLDZERO_FINAL_MENU_LINE_2 = "message.worldzero.final_menu.line.2";
    private static final String WORLDZERO_FINAL_MENU_LINE_3 = "message.worldzero.final_menu.line.3";
    private static final String WORLDZERO_FINAL_MENU_LINE_4 = "message.worldzero.final_menu.line.4";
    private final boolean worldzero$quitOnly;
    private Button worldzero$singleplayerButton;
    private int worldzero$ticks;

    public WorldZeroFinalMenuScreen() {
        this(false);
    }

    public WorldZeroFinalMenuScreen(boolean quitOnly) {
        super(Component.empty());
        this.worldzero$quitOnly = quitOnly;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 100;
        int top = this.height / 4 + 48;
        this.worldzero$singleplayerButton = this.addRenderableWidget(Button.builder(Component.translatable("menu.singleplayer"), button -> {
            this.worldzero$openSingleplayer();
        }).bounds(left, top, 200, 20).build());
        this.worldzero$singleplayerButton.active = !this.worldzero$quitOnly
                && this.worldzero$ticks >= WORLDZERO_SINGLEPLAYER_ACTIVE_TICK;
        this.addRenderableWidget(Button.builder(Component.translatable("menu.multiplayer"), button -> {
        }).bounds(left, top + 24, 200, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("fml.menu.mods"), button -> {
        }).bounds(left, top + 48, 98, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("menu.online"), button -> {
        }).bounds(this.width / 2 + 2, top + 48, 98, 20).build());
        this.addRenderableWidget(new ImageButton(
                this.width / 2 - 124,
                top + 84,
                20,
                20,
                0,
                106,
                20,
                Button.WIDGETS_LOCATION,
                256,
                256,
                button -> {
                },
                Component.translatable("narrator.button.language")
        ));
        this.addRenderableWidget(Button.builder(Component.translatable("menu.options"), button -> {
        }).bounds(left, top + 84, 98, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("menu.quit").append("*"), button -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) {
                minecraft.stop();
            }
        }).bounds(this.width / 2 + 2, top + 84, 98, 20).build());
        this.addRenderableWidget(new ImageButton(
                this.width / 2 + 104,
                top + 84,
                20,
                20,
                0,
                0,
                20,
                Button.ACCESSIBILITY_TEXTURE,
                32,
                64,
                button -> {
                },
                Component.translatable("narrator.button.accessibility")
        ));
    }

    @Override
    public void tick() {
        if (!this.worldzero$quitOnly) {
            this.worldzero$ticks++;
        }
        if (this.worldzero$singleplayerButton != null) {
            this.worldzero$singleplayerButton.active = !this.worldzero$quitOnly
                    && this.worldzero$ticks >= WORLDZERO_SINGLEPLAYER_ACTIVE_TICK;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF000000);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (!this.worldzero$quitOnly) {
            this.worldzero$renderChat(guiGraphics);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private void worldzero$openSingleplayer() {
        Minecraft minecraft = this.minecraft == null ? Minecraft.getInstance() : this.minecraft;
        if (minecraft == null || this.worldzero$quitOnly || this.worldzero$ticks < WORLDZERO_SINGLEPLAYER_ACTIVE_TICK) {
            return;
        }

        minecraft.setScreen(new SelectWorldScreen(this));
    }

    private void worldzero$renderChat(GuiGraphics guiGraphics) {
        Component[] messages = this.worldzero$visibleChatMessages();
        if (messages.length == 0) {
            return;
        }

        int lineHeight = 9;
        int bottom = this.height - 36;
        for (int index = 0; index < messages.length; index++) {
            Component message = messages[index];
            int y = bottom - (messages.length - 1 - index) * lineHeight;
            int width = this.font.width(message);
            guiGraphics.fill(2, y - 1, 6 + width, y + lineHeight, 0x7F000000);
            guiGraphics.drawString(this.font, message, 4, y, 0xFFFFFFFF, true);
        }
    }

    private Component[] worldzero$visibleChatMessages() {
        int count = 0;
        if (this.worldzero$ticks >= 60) {
            count++;
        }
        if (this.worldzero$ticks >= 110) {
            count++;
        }
        if (this.worldzero$ticks >= 170) {
            count++;
        }
        if (this.worldzero$ticks >= 240) {
            count++;
        }
        boolean showRealWarning = this.worldzero$ticks >= 330 && this.worldzero$ticks < 360;
        if (showRealWarning) {
            count++;
        }

        Component[] messages = new Component[count];
        int index = 0;
        if (this.worldzero$ticks >= 60) {
            messages[index++] = worldzero$chatLine(WORLDZERO_SANEK_FAKE, WORLDZERO_FINAL_MENU_LINE_0);
        }
        if (this.worldzero$ticks >= 110) {
            messages[index++] = worldzero$chatLine(WORLDZERO_SANEK_FAKE, WORLDZERO_FINAL_MENU_LINE_1);
        }
        if (this.worldzero$ticks >= 170) {
            messages[index++] = worldzero$chatLine(WORLDZERO_SANEK_FAKE, WORLDZERO_FINAL_MENU_LINE_2);
        }
        if (this.worldzero$ticks >= 240) {
            messages[index++] = worldzero$chatLine(WORLDZERO_SANEK_FAKE, WORLDZERO_FINAL_MENU_LINE_3);
        }
        if (showRealWarning) {
            messages[index] = worldzero$chatLine(WORLDZERO_SANEK_REAL, WORLDZERO_FINAL_MENU_LINE_4);
        }
        return messages;
    }

    private static Component worldzero$chatLine(String speaker, String messageKey) {
        return Component.translatable(
                "chat.type.text",
                Component.literal(speaker),
                Component.translatable(messageKey)
        );
    }
}
