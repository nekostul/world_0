package ru.nekostul.worldzero;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class WorldZeroSkyWatchPacket {
    public static final byte WORLDZERO_ACTION_START = 0;
    public static final byte WORLDZERO_ACTION_CLEAR = 1;

    private final byte worldzero$action;
    private final int worldzero$durationTicks;
    private final int worldzero$variant;

    public WorldZeroSkyWatchPacket(byte action, int durationTicks, int variant) {
        this.worldzero$action = action;
        this.worldzero$durationTicks = durationTicks;
        this.worldzero$variant = variant;
    }

    public static void encode(WorldZeroSkyWatchPacket packet, FriendlyByteBuf buffer) {
        buffer.writeByte(packet.worldzero$action);
        buffer.writeVarInt(packet.worldzero$durationTicks);
        buffer.writeVarInt(packet.worldzero$variant);
    }

    public static WorldZeroSkyWatchPacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroSkyWatchPacket(
                buffer.readByte(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    public static void handle(WorldZeroSkyWatchPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> WorldZeroSkyWatchClientController.worldzero$trigger(
                        packet.worldzero$action,
                        packet.worldzero$durationTicks,
                        packet.worldzero$variant
                )
        ));
        context.setPacketHandled(true);
    }
}
