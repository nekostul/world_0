package ru.nekostul.worldzero;

import net.minecraft.core.BlockPos;
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
        WORLDZERO_CHANNEL.registerMessage(
                worldzero$packetId++,
                WorldZeroFallClientPacket.class,
                WorldZeroFallClientPacket::encode,
                WorldZeroFallClientPacket::decode,
                WorldZeroFallClientPacket::handle
        );
        WORLDZERO_CHANNEL.registerMessage(
                worldzero$packetId++,
                WorldZeroFallRespawnPacket.class,
                WorldZeroFallRespawnPacket::encode,
                WorldZeroFallRespawnPacket::decode,
                WorldZeroFallRespawnPacket::handle
        );
        WORLDZERO_CHANNEL.registerMessage(
                worldzero$packetId++,
                WorldZeroParalysisClientPacket.class,
                WorldZeroParalysisClientPacket::encode,
                WorldZeroParalysisClientPacket::decode,
                WorldZeroParalysisClientPacket::handle
        );
        WORLDZERO_CHANNEL.registerMessage(
                worldzero$packetId++,
                WorldZeroParalysisCameraAlignedPacket.class,
                WorldZeroParalysisCameraAlignedPacket::encode,
                WorldZeroParalysisCameraAlignedPacket::decode,
                WorldZeroParalysisCameraAlignedPacket::handle
        );
    }

    public static void sendFreezeStart(ServerPlayer player, int durationTicks) {
        sendFreezeStart(player, durationTicks, -1);
    }

    public static void sendFreezeStart(ServerPlayer player, int durationTicks, int focusEntityId) {
        WORLDZERO_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WorldZeroFreezeStartPacket(durationTicks, focusEntityId)
        );
    }

    public static void sendFreezeEnd(ServerPlayer player) {
        WORLDZERO_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WorldZeroFreezeEndPacket()
        );
    }

    public static void sendFallClientAction(ServerPlayer player, byte action) {
        WORLDZERO_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WorldZeroFallClientPacket(action)
        );
    }

    public static void sendFallRespawn() {
        WORLDZERO_CHANNEL.sendToServer(new WorldZeroFallRespawnPacket());
    }

    public static void sendParalysisClientAction(ServerPlayer player, byte action, BlockPos targetPos, int entityId, int durationTicks) {
        WORLDZERO_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WorldZeroParalysisClientPacket(action, targetPos, entityId, durationTicks)
        );
    }

    public static void sendParalysisCameraAligned() {
        WORLDZERO_CHANNEL.sendToServer(new WorldZeroParalysisCameraAlignedPacket());
    }
}
