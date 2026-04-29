package ru.nekostul.worldzero;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class WorldZeroLocalizedChatLinePacket {
    private final String worldzero$speaker;
    private final String worldzero$messageKey;

    public WorldZeroLocalizedChatLinePacket(String speaker, String messageKey) {
        this.worldzero$speaker = speaker;
        this.worldzero$messageKey = messageKey;
    }

    public static void encode(WorldZeroLocalizedChatLinePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.worldzero$speaker);
        buffer.writeUtf(packet.worldzero$messageKey);
    }

    public static WorldZeroLocalizedChatLinePacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroLocalizedChatLinePacket(buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(WorldZeroLocalizedChatLinePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> WorldZeroVoidClientController.worldzero$handleChatLine(
                        packet.worldzero$speaker,
                        packet.worldzero$messageKey
                )
        ));
        context.setPacketHandled(true);
    }
}
