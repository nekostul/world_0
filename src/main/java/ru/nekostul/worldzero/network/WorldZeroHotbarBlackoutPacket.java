package ru.nekostul.worldzero.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ru.nekostul.worldzero.client.controller.WorldZeroHotbarBlackoutClientController;

import java.util.function.Supplier;

public final class WorldZeroHotbarBlackoutPacket {
    private final int worldzero$durationTicks;

    public WorldZeroHotbarBlackoutPacket(int durationTicks) {
        this.worldzero$durationTicks = durationTicks;
    }

    public static void encode(WorldZeroHotbarBlackoutPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.worldzero$durationTicks);
    }

    public static WorldZeroHotbarBlackoutPacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroHotbarBlackoutPacket(buffer.readVarInt());
    }

    public static void handle(WorldZeroHotbarBlackoutPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> WorldZeroHotbarBlackoutClientController.worldzero$trigger(packet.worldzero$durationTicks)
        ));
        context.setPacketHandled(true);
    }
}
