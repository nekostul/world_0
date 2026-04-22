package ru.nekostul.worldzero;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class WorldZeroHouseChatLinePacket {
    private final String worldzero$speaker;
    private final String worldzero$defaultMessage;
    private final String worldzero$englishMessage;

    public WorldZeroHouseChatLinePacket(String speaker, String defaultMessage, String englishMessage) {
        this.worldzero$speaker = speaker;
        this.worldzero$defaultMessage = defaultMessage;
        this.worldzero$englishMessage = englishMessage;
    }

    public static void encode(WorldZeroHouseChatLinePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.worldzero$speaker);
        buffer.writeUtf(packet.worldzero$defaultMessage);
        buffer.writeUtf(packet.worldzero$englishMessage);
    }

    public static WorldZeroHouseChatLinePacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroHouseChatLinePacket(buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(WorldZeroHouseChatLinePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> WorldZeroHouseClientController.handleFakeChatLinePacket(
                        packet.worldzero$speaker,
                        packet.worldzero$defaultMessage,
                        packet.worldzero$englishMessage
                )
        ));
        context.setPacketHandled(true);
    }
}
