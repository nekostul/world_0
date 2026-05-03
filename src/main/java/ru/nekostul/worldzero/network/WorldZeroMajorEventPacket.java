package ru.nekostul.worldzero.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ru.nekostul.worldzero.client.controller.WorldZeroMajorEventClientController;

import java.util.function.Supplier;

public final class WorldZeroMajorEventPacket {
    public static final byte WORLDZERO_ACTION_WATCHING = 0;
    public static final byte WORLDZERO_ACTION_CORRUPTION = 1;
    public static final byte WORLDZERO_ACTION_SWARM = 2;
    public static final byte WORLDZERO_ACTION_TIME_LOOP = 3;
    public static final byte WORLDZERO_ACTION_GLITCH_RAIN = 4;
    public static final byte WORLDZERO_ACTION_CLEAR_ALL = 5;

    private final byte worldzero$action;
    private final int worldzero$durationTicks;
    private final int worldzero$variant;

    public WorldZeroMajorEventPacket(byte action, int durationTicks, int variant) {
        this.worldzero$action = action;
        this.worldzero$durationTicks = durationTicks;
        this.worldzero$variant = variant;
    }

    public static void encode(WorldZeroMajorEventPacket packet, FriendlyByteBuf buffer) {
        buffer.writeByte(packet.worldzero$action);
        buffer.writeVarInt(packet.worldzero$durationTicks);
        buffer.writeVarInt(packet.worldzero$variant);
    }

    public static WorldZeroMajorEventPacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroMajorEventPacket(buffer.readByte(), buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(WorldZeroMajorEventPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> WorldZeroMajorEventClientController.worldzero$trigger(
                        packet.worldzero$action,
                        packet.worldzero$durationTicks,
                        packet.worldzero$variant
                )
        ));
        context.setPacketHandled(true);
    }
}
