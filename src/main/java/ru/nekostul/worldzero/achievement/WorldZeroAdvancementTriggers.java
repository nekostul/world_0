package ru.nekostul.worldzero.achievement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import ru.nekostul.worldzero.WorldZeroMod;

public final class WorldZeroAdvancementTriggers {
    public static final ResourceLocation HE_IS_CLOSE = ResourceLocation.fromNamespaceAndPath(WorldZeroMod.MOD_ID, "he_is_close");
    public static final ResourceLocation PARALYSIS = ResourceLocation.fromNamespaceAndPath(WorldZeroMod.MOD_ID, "paralysis");
    public static final ResourceLocation FORGOTTEN_DISC = ResourceLocation.fromNamespaceAndPath(WorldZeroMod.MOD_ID, "forgotten_disc");

    private WorldZeroAdvancementTriggers() {
    }

    public static void grantAdvancement(ServerPlayer player, ResourceLocation advancement) {
        if (player == null || player.getServer() == null) {
            return;
        }

        var advancementHolder = player.getServer().getAdvancements().getAdvancement(advancement);
        if (advancementHolder != null) {
            player.getAdvancements().award(advancementHolder, "trigger");
        }
    }

    public static void grantHeIsClose(ServerPlayer player) {
        grantAdvancement(player, HE_IS_CLOSE);
    }

    public static void grantParalysis(ServerPlayer player) {
        grantAdvancement(player, PARALYSIS);
    }

    public static void grantForgottenDisc(ServerPlayer player) {
        grantAdvancement(player, FORGOTTEN_DISC);
    }
}
