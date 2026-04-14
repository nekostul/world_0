package ru.nekostul.worldzero;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class WorldZeroNetwork {
    private static final String WORLDZERO_PROTOCOL_VERSION = "1";
    private static final SimpleChannel WORLDZERO_CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(WorldZeroMod.MOD_ID, "main"))
            .networkProtocolVersion(() -> WORLDZERO_PROTOCOL_VERSION)
            .clientAcceptedVersions(WORLDZERO_PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(WORLDZERO_PROTOCOL_VERSION::equals)
            .simpleChannel();
    private static int worldzero$packetId;

    private WorldZeroNetwork() {
    }

    public static void register() {
        WORLDZERO_CHANNEL.registerMessage(
                worldzero$packetId++,
                WorldZeroFreezeStartPacket.class,
                WorldZeroFreezeStartPacket::encode,
                WorldZeroFreezeStartPacket::decode,
                WorldZeroFreezeStartPacket::handle
        );
        WORLDZERO_CHANNEL.registerMessage(
                worldzero$packetId++,
                WorldZeroFreezeEndPacket.class,
                WorldZeroFreezeEndPacket::encode,
                WorldZeroFreezeEndPacket::decode,
                WorldZeroFreezeEndPacket::handle
        );
    }

    public static void sendFreezeStart(ServerPlayer player, int durationTicks) {
        WORLDZERO_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WorldZeroFreezeStartPacket(durationTicks)
        );
    }

    public static void sendFreezeEnd(ServerPlayer player) {
        WORLDZERO_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WorldZeroFreezeEndPacket()
        );
    }
}
