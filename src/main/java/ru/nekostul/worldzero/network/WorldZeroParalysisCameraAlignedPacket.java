package ru.nekostul.worldzero.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import ru.nekostul.worldzero.event.paralysis.WorldZeroParalysisEvent;

import java.util.function.Supplier;

public final class WorldZeroParalysisCameraAlignedPacket {
    public static void encode(WorldZeroParalysisCameraAlignedPacket packet, FriendlyByteBuf buffer) {
    }

    public static WorldZeroParalysisCameraAlignedPacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroParalysisCameraAlignedPacket();
    }

    public static void handle(WorldZeroParalysisCameraAlignedPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                WorldZeroParalysisEvent.worldzero$acknowledgeCameraAligned(context.getSender());
            }
        });
        context.setPacketHandled(true);
    }
}
