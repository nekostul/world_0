package ru.nekostul.worldzero;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class WorldZeroKeyboardBlockPacket {
    private final int worldzero$durationTicks;

    public WorldZeroKeyboardBlockPacket(int durationTicks) {
        this.worldzero$durationTicks = durationTicks;
    }

    public static void encode(WorldZeroKeyboardBlockPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.worldzero$durationTicks);
    }

    public static WorldZeroKeyboardBlockPacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroKeyboardBlockPacket(buffer.readVarInt());
    }

    public static void handle(WorldZeroKeyboardBlockPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> WorldZeroVoidClientController.worldzero$startKeyboardBlock(packet.worldzero$durationTicks)
        ));
        context.setPacketHandled(true);
    }
}
