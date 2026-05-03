package ru.nekostul.worldzero.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ru.nekostul.worldzero.event.footsteps.WorldZeroFootstepsEvent;

import java.util.function.Supplier;

public final class WorldZeroBlankDiscFinishedPacket {
    public static void encode(WorldZeroBlankDiscFinishedPacket packet, FriendlyByteBuf buffer) {
    }

    public static WorldZeroBlankDiscFinishedPacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroBlankDiscFinishedPacket();
    }

    public static void handle(WorldZeroBlankDiscFinishedPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                WorldZeroFootstepsEvent.worldzero$acknowledgeBlankDiscPlaybackFinished(sender);
            }
        });
        context.setPacketHandled(true);
    }
}
