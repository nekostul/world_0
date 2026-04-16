package ru.nekostul.worldzero;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class WorldZeroBlankDiscPlaybackPacket {
    public static void encode(WorldZeroBlankDiscPlaybackPacket packet, FriendlyByteBuf buffer) {
    }

    public static WorldZeroBlankDiscPlaybackPacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroBlankDiscPlaybackPacket();
    }

    public static void handle(WorldZeroBlankDiscPlaybackPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> WorldZeroBlankDiscClientController::worldzero$startPlayback
        ));
        context.setPacketHandled(true);
    }
}
