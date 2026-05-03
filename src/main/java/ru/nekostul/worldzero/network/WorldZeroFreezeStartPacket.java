package ru.nekostul.worldzero.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ru.nekostul.worldzero.client.controller.WorldZeroFreezeClientController;

import java.util.function.Supplier;

public final class WorldZeroFreezeStartPacket {
    private final int worldzero$durationTicks;
    private final int worldzero$focusEntityId;
    private final float worldzero$forcedYaw;
    private final float worldzero$forcedPitch;
    private final double worldzero$lockedX;
    private final double worldzero$lockedY;
    private final double worldzero$lockedZ;

    public WorldZeroFreezeStartPacket(int durationTicks) {
        this(durationTicks, -1, Float.NaN, Float.NaN, Double.NaN, Double.NaN, Double.NaN);
    }

    public WorldZeroFreezeStartPacket(int durationTicks, int focusEntityId) {
        this(durationTicks, focusEntityId, Float.NaN, Float.NaN, Double.NaN, Double.NaN, Double.NaN);
    }

    public WorldZeroFreezeStartPacket(
            int durationTicks,
            int focusEntityId,
            float forcedYaw,
            float forcedPitch,
            double lockedX,
            double lockedY,
            double lockedZ
    ) {
        this.worldzero$durationTicks = durationTicks;
        this.worldzero$focusEntityId = focusEntityId;
        this.worldzero$forcedYaw = forcedYaw;
        this.worldzero$forcedPitch = forcedPitch;
        this.worldzero$lockedX = lockedX;
        this.worldzero$lockedY = lockedY;
        this.worldzero$lockedZ = lockedZ;
    }

    public static void encode(WorldZeroFreezeStartPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.worldzero$durationTicks);
        buffer.writeVarInt(packet.worldzero$focusEntityId);
        buffer.writeFloat(packet.worldzero$forcedYaw);
        buffer.writeFloat(packet.worldzero$forcedPitch);
        buffer.writeDouble(packet.worldzero$lockedX);
        buffer.writeDouble(packet.worldzero$lockedY);
        buffer.writeDouble(packet.worldzero$lockedZ);
    }

    public static WorldZeroFreezeStartPacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroFreezeStartPacket(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
        );
    }

    public static void handle(WorldZeroFreezeStartPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> WorldZeroFreezeClientController.startFreeze(
                        packet.worldzero$durationTicks,
                        packet.worldzero$focusEntityId,
                        packet.worldzero$forcedYaw,
                        packet.worldzero$forcedPitch,
                        packet.worldzero$lockedX,
                        packet.worldzero$lockedY,
                        packet.worldzero$lockedZ
                )
        ));
        context.setPacketHandled(true);
    }
}
