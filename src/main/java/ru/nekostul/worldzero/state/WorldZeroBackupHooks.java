package ru.nekostul.worldzero;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroBackupHooks {
    private static boolean worldzero$wasInWorld;
    private static String worldzero$lastPrimaryWorldId;

    private WorldZeroBackupHooks() {
    }

    @SubscribeEvent
    public static void worldzero$onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        boolean isInWorld = minecraft.level != null;
        if (isInWorld) {
            String primaryWorldId = WorldZeroState.readPrimaryWorldId(minecraft);
            if (primaryWorldId != null) {
                worldzero$lastPrimaryWorldId = primaryWorldId;
            }

            if (!worldzero$wasInWorld) {
                worldzero$wasInWorld = true;
                worldzero$backupPrimary(minecraft, worldzero$lastPrimaryWorldId);
            }
            return;
        }

        if (!worldzero$wasInWorld) {
            return;
        }

        worldzero$wasInWorld = false;
        worldzero$backupPrimary(minecraft, worldzero$lastPrimaryWorldId);
        worldzero$lastPrimaryWorldId = null;
    }

    private static void worldzero$backupPrimary(Minecraft minecraft, String primaryWorldId) {
        if (primaryWorldId == null) {
            return;
        }

        WorldZeroState.refreshPrimaryWorldBackup(minecraft, minecraft.getLevelSource(), primaryWorldId);
    }
}
