package ru.nekostul.worldzero;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class WorldZeroHouseMusicFinishedPacket {
    public static void encode(WorldZeroHouseMusicFinishedPacket packet, FriendlyByteBuf buffer) {
    }

    public static WorldZeroHouseMusicFinishedPacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroHouseMusicFinishedPacket();
    }

    public static void handle(WorldZeroHouseMusicFinishedPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                WorldZeroHouseDimension.worldzero$acknowledgeMusicFinished(sender);
            }
        });
        context.setPacketHandled(true);
    }
}
