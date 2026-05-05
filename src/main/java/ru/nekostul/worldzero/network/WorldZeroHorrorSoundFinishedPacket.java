package ru.nekostul.worldzero.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ru.nekostul.worldzero.event.horror.WorldZeroNightDarknessEvent;
import ru.nekostul.worldzero.event.horror.WorldZeroTrapEvent;

import java.util.function.Supplier;

public final class WorldZeroHorrorSoundFinishedPacket {
    public static void encode(WorldZeroHorrorSoundFinishedPacket packet, FriendlyByteBuf buffer) {
    }

    public static WorldZeroHorrorSoundFinishedPacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroHorrorSoundFinishedPacket();
    }

    public static void handle(WorldZeroHorrorSoundFinishedPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                WorldZeroTrapEvent.worldzero$acknowledgeAmbientSoundFinished(sender);
                WorldZeroNightDarknessEvent.worldzero$acknowledgeSoundFinished(sender);
            }
        });
        context.setPacketHandled(true);
    }
}
