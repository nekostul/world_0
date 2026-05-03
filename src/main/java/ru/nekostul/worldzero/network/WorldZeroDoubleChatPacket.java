package ru.nekostul.worldzero;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class WorldZeroDoubleChatPacket {
    public static final byte WORLDZERO_ACTION_PLAYER_LINE = 0;
    public static final byte WORLDZERO_ACTION_SYSTEM_LINE = 1;
    public static final byte WORLDZERO_ACTION_AUTO_LINE = 2;
    public static final byte WORLDZERO_ACTION_LOCAL_PORT = 3;
    public static final byte WORLDZERO_ACTION_AUTO_SELF_LINE = 4;
    private final byte worldzero$action;
    private final String worldzero$speaker;
    private final String worldzero$message;

    public WorldZeroDoubleChatPacket(byte action, String speaker, String message) {
        this.worldzero$action = action;
        this.worldzero$speaker = speaker;
        this.worldzero$message = message;
    }

    public static void encode(WorldZeroDoubleChatPacket packet, FriendlyByteBuf buffer) {
        buffer.writeByte(packet.worldzero$action);
        buffer.writeUtf(packet.worldzero$speaker);
        buffer.writeUtf(packet.worldzero$message);
    }

    public static WorldZeroDoubleChatPacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroDoubleChatPacket(
                buffer.readByte(),
                buffer.readUtf(),
                buffer.readUtf()
        );
    }

    public static void handle(WorldZeroDoubleChatPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> WorldZeroDoubleChatClientController.worldzero$handlePacket(
                        packet.worldzero$action,
                        packet.worldzero$speaker,
                        packet.worldzero$message
                )
        ));
        context.setPacketHandled(true);
    }
}
