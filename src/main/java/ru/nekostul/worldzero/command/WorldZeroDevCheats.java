package ru.nekostul.worldzero.command;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class WorldZeroDevCheats {
    private static final String WORLDZERO_DEV_NICK = "Dev";

    private WorldZeroDevCheats() {
    }

    public static boolean isAllowedForCurrentClient() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }

        if (minecraft.player != null) {
            return WORLDZERO_DEV_NICK.equals(minecraft.player.getGameProfile().getName());
        }

        return WORLDZERO_DEV_NICK.equals(minecraft.getUser().getName());
    }

    public static boolean isAllowedForSource(CommandSourceStack source) {
        if (source == null) {
            return false;
        }

        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return false;
        }

        return isAllowedForPlayer(player);
    }

    public static boolean isAllowedForPlayer(Player player) {
        if (player == null) {
            return false;
        }

        return WORLDZERO_DEV_NICK.equals(player.getGameProfile().getName());
    }
}
