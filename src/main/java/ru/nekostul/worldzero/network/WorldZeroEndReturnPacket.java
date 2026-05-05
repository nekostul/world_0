package ru.nekostul.worldzero.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ru.nekostul.worldzero.client.controller.WorldZeroEndReturnClientController;

import java.util.function.Supplier;

public final class WorldZeroEndReturnPacket {
    private final int worldzero$durationTicks;

    public WorldZeroEndReturnPacket(int durationTicks) {
        this.worldzero$durationTicks = durationTicks;
    }

    public static void encode(WorldZeroEndReturnPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.worldzero$durationTicks);
    }

    public static WorldZeroEndReturnPacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroEndReturnPacket(buffer.readVarInt());
    }

    public static void handle(WorldZeroEndReturnPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> WorldZeroEndReturnClientController.worldzero$trigger(packet.worldzero$durationTicks)
        ));
        context.setPacketHandled(true);
    }
}
