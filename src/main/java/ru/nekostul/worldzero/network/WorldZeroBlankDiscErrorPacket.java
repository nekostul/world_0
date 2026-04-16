package ru.nekostul.worldzero;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class WorldZeroBlankDiscErrorPacket {
    public static void encode(WorldZeroBlankDiscErrorPacket packet, FriendlyByteBuf buffer) {
    }

    public static WorldZeroBlankDiscErrorPacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroBlankDiscErrorPacket();
    }

    public static void handle(WorldZeroBlankDiscErrorPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> WorldZeroBlankDiscClientController::worldzero$showPlaybackErrorMessage
        ));
        context.setPacketHandled(true);
    }
}
