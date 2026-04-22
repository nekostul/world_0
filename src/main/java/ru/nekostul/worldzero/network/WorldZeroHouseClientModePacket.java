package ru.nekostul.worldzero;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class WorldZeroHouseClientModePacket {
    private final byte worldzero$mode;

    public WorldZeroHouseClientModePacket(byte mode) {
        this.worldzero$mode = mode;
    }

    public static void encode(WorldZeroHouseClientModePacket packet, FriendlyByteBuf buffer) {
        buffer.writeByte(packet.worldzero$mode);
    }

    public static WorldZeroHouseClientModePacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroHouseClientModePacket(buffer.readByte());
    }

    public static void handle(WorldZeroHouseClientModePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> WorldZeroHouseClientController.handleModePacket(packet.worldzero$mode)
        ));
        context.setPacketHandled(true);
    }
}
