package ru.nekostul.worldzero;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroClientCommandAnomaly {
    private WorldZeroClientCommandAnomaly() {
    }

    @SubscribeEvent
    public static void worldzero$registerClientCommands(RegisterClientCommandsEvent event) {
        if (WorldZeroDevCheats.isAllowedForCurrentClient()) {
            return;
        }

        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        worldzero$registerBlockedCommand(dispatcher, "gamemode");
        worldzero$registerBlockedCommand(dispatcher, "gamerule");
        worldzero$registerBlockedCommand(dispatcher, "tp");
    }

    private static void worldzero$registerBlockedCommand(
            CommandDispatcher<CommandSourceStack> dispatcher,
            String commandName
    ) {
        dispatcher.register(
                Commands.literal(commandName)
                        .executes(WorldZeroClientCommandAnomaly::worldzero$handleBlockedCheatCommand)
                        .then(Commands.argument("args", StringArgumentType.greedyString())
                                .executes(WorldZeroClientCommandAnomaly::worldzero$handleBlockedCheatCommand))
        );
    }

    private static int worldzero$handleBlockedCheatCommand(CommandContext<CommandSourceStack> context) {
        if (WorldZeroDevCheats.isAllowedForCurrentClient()) {
            return 1;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) {
            return 0;
        }

        if (!WorldZeroState.hasSeenCommandAnomaly(minecraft)) {
            WorldZeroState.markCommandAnomalySeen(minecraft);
            minecraft.gui.getChat().addMessage(
                    Component.translatable("worldzero.error.command_once_anomaly").withStyle(ChatFormatting.RED)
            );
            minecraft.gui.setOverlayMessage(Component.literal("0"), false);
            return 0;
        }

        minecraft.gui.getChat().addMessage(
                Component.translatable("worldzero.error.command_cheats_disabled").withStyle(ChatFormatting.RED)
        );
        return 0;
    }
}
