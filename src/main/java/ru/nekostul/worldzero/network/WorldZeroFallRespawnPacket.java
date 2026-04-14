package ru.nekostul.worldzero;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class WorldZeroFallRespawnPacket {
    public static void encode(WorldZeroFallRespawnPacket packet, FriendlyByteBuf buffer) {
    }

    public static WorldZeroFallRespawnPacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroFallRespawnPacket();
    }

    public static void handle(WorldZeroFallRespawnPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                WorldZeroFallEvent.worldzero$acknowledgeFakeRespawn(sender);
            }
        });
        context.setPacketHandled(true);
    }
}
