package ru.nekostul.worldzero;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class WorldZeroFreezeStartPacket {
    private final int worldzero$durationTicks;
    private final int worldzero$focusEntityId;

    public WorldZeroFreezeStartPacket(int durationTicks) {
        this(durationTicks, -1);
    }

    public WorldZeroFreezeStartPacket(int durationTicks, int focusEntityId) {
        this.worldzero$durationTicks = durationTicks;
        this.worldzero$focusEntityId = focusEntityId;
    }

    public static void encode(WorldZeroFreezeStartPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.worldzero$durationTicks);
        buffer.writeVarInt(packet.worldzero$focusEntityId);
    }

    public static WorldZeroFreezeStartPacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroFreezeStartPacket(buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(WorldZeroFreezeStartPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> WorldZeroFreezeClientController.startFreeze(
                        packet.worldzero$durationTicks,
                        packet.worldzero$focusEntityId
                )
        ));
        context.setPacketHandled(true);
    }
}
