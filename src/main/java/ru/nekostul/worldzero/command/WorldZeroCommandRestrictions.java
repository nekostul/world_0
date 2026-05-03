package ru.nekostul.worldzero.command;

import net.minecraft.commands.CommandRuntimeException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;
import ru.nekostul.worldzero.state.WorldZeroState;

import java.util.Locale;
import java.util.Set;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroCommandRestrictions {
    private static final long WORLDZERO_COMMAND_LOCK_START_TICKS = 30L * 60L * 20L;
    private static final Set<String> WORLDZERO_BLOCKED_COMMANDS = Set.of(
            "time"
    );

    private WorldZeroCommandRestrictions() {
    }

    @SubscribeEvent
    public static void worldzero$onCommand(CommandEvent event) {
        CommandSourceStack source = event.getParseResults().getContext().getSource();
        if (source == null || source.getPlayer() == null) {
            return;
        }

        if (WorldZeroDevCheats.isAllowedForSource(source)) {
            return;
        }

        long gameTime = source.getServer().overworld().getGameTime();
        if (gameTime < WORLDZERO_COMMAND_LOCK_START_TICKS) {
            return;
        }

        String commandName = worldzero$extractCommandName(event.getParseResults().getReader().getString());
        if (commandName == null || !WORLDZERO_BLOCKED_COMMANDS.contains(commandName)) {
            return;
        }

        event.setCanceled(true);
        event.setException(new CommandRuntimeException(WorldZeroState.commandBlockedMessage()));
    }

    private static String worldzero$extractCommandName(String commandInput) {
        if (commandInput == null) {
            return null;
        }

        String normalized = commandInput.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        int whitespaceIndex = normalized.indexOf(' ');
        String commandName = whitespaceIndex >= 0 ? normalized.substring(0, whitespaceIndex) : normalized;
        if (commandName.isEmpty()) {
            return null;
        }

        int namespaceIndex = commandName.indexOf(':');
        if (namespaceIndex >= 0 && namespaceIndex < commandName.length() - 1) {
            commandName = commandName.substring(namespaceIndex + 1);
        }

        return commandName.toLowerCase(Locale.ROOT);
    }
}
