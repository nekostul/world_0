package ru.nekostul.worldzero.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ru.nekostul.worldzero.client.controller.WorldZeroFinaleClientController;

import java.util.function.Supplier;

public final class WorldZeroFinalePacket {
    public static final byte WORLDZERO_ACTION_CLEAR_ALL = 0;
    public static final byte WORLDZERO_ACTION_START_SILENCE = 1;
    public static final byte WORLDZERO_ACTION_GLITCH = 2;
    public static final byte WORLDZERO_ACTION_SOUND_BREAK = 3;
    public static final byte WORLDZERO_ACTION_FULL_FREEZE = 4;
    public static final byte WORLDZERO_ACTION_EXIT_TO_MENU = 5;
    public static final byte WORLDZERO_ACTION_FINAL_BLACK_MENU = 6;
    public static final byte WORLDZERO_ACTION_FORCE_MAX_VOLUME = 7;
    public static final byte WORLDZERO_ACTION_ABSOLUTE_ATTACK = 8;

    private final byte worldzero$action;
    private final int worldzero$durationTicks;
    private final int worldzero$variant;

    public WorldZeroFinalePacket(byte action, int durationTicks, int variant) {
        this.worldzero$action = action;
        this.worldzero$durationTicks = durationTicks;
        this.worldzero$variant = variant;
    }

    public static void encode(WorldZeroFinalePacket packet, FriendlyByteBuf buffer) {
        buffer.writeByte(packet.worldzero$action);
        buffer.writeVarInt(packet.worldzero$durationTicks);
        buffer.writeVarInt(packet.worldzero$variant);
    }

    public static WorldZeroFinalePacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroFinalePacket(buffer.readByte(), buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(WorldZeroFinalePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> WorldZeroFinaleClientController.worldzero$trigger(
                        packet.worldzero$action,
                        packet.worldzero$durationTicks,
                        packet.worldzero$variant
                )
        ));
        context.setPacketHandled(true);
    }
}
