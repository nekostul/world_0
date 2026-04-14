package ru.nekostul.worldzero;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class WorldZeroFreezeStartPacket {
    private final int worldzero$durationTicks;

    public WorldZeroFreezeStartPacket(int durationTicks) {
        this.worldzero$durationTicks = durationTicks;
    }

    public static void encode(WorldZeroFreezeStartPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.worldzero$durationTicks);
    }

    public static WorldZeroFreezeStartPacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroFreezeStartPacket(buffer.readVarInt());
    }

    public static void handle(WorldZeroFreezeStartPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> WorldZeroFreezeClientController.startFreeze(packet.worldzero$durationTicks)
        ));
        context.setPacketHandled(true);
    }
}
