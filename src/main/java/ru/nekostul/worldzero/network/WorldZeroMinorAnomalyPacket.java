package ru.nekostul.worldzero.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ru.nekostul.worldzero.client.controller.WorldZeroMinorAnomalyClientController;

import java.util.function.Supplier;

public final class WorldZeroMinorAnomalyPacket {
    public static final byte WORLDZERO_ACTION_PERIPHERAL_ECHO = 0;
    public static final byte WORLDZERO_ACTION_SHADOW_DELAY = 1;
    public static final byte WORLDZERO_ACTION_CLEAR_ALL = 2;

    private final byte worldzero$action;
    private final int worldzero$durationTicks;
    private final int worldzero$variant;

    public WorldZeroMinorAnomalyPacket(byte action, int durationTicks, int variant) {
        this.worldzero$action = action;
        this.worldzero$durationTicks = durationTicks;
        this.worldzero$variant = variant;
    }

    public static void encode(WorldZeroMinorAnomalyPacket packet, FriendlyByteBuf buffer) {
        buffer.writeByte(packet.worldzero$action);
        buffer.writeVarInt(packet.worldzero$durationTicks);
        buffer.writeVarInt(packet.worldzero$variant);
    }

    public static WorldZeroMinorAnomalyPacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroMinorAnomalyPacket(buffer.readByte(), buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(WorldZeroMinorAnomalyPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> WorldZeroMinorAnomalyClientController.worldzero$trigger(
                        packet.worldzero$action,
                        packet.worldzero$durationTicks,
                        packet.worldzero$variant
                )
        ));
        context.setPacketHandled(true);
    }
}
