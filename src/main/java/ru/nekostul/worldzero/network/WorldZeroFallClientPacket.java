package ru.nekostul.worldzero;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class WorldZeroFallClientPacket {
    public static final byte WORLDZERO_ACTION_BEGIN = 0;
    public static final byte WORLDZERO_ACTION_START_FALL = 1;
    public static final byte WORLDZERO_ACTION_SHOW_DEATH = 2;
    public static final byte WORLDZERO_ACTION_CLEAR = 3;

    private final byte worldzero$action;

    public WorldZeroFallClientPacket(byte action) {
        this.worldzero$action = action;
    }

    public static void encode(WorldZeroFallClientPacket packet, FriendlyByteBuf buffer) {
        buffer.writeByte(packet.worldzero$action);
    }

    public static WorldZeroFallClientPacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroFallClientPacket(buffer.readByte());
    }

    public static void handle(WorldZeroFallClientPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> WorldZeroFallClientController.handleAction(packet.worldzero$action)
        ));
        context.setPacketHandled(true);
    }
}
