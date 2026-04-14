package ru.nekostul.worldzero;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class WorldZeroFreezeEndPacket {
    public static void encode(WorldZeroFreezeEndPacket packet, FriendlyByteBuf buffer) {
    }

    public static WorldZeroFreezeEndPacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroFreezeEndPacket();
    }

    public static void handle(WorldZeroFreezeEndPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> WorldZeroFreezeClientController::finishFreeze
        ));
        context.setPacketHandled(true);
    }
}
