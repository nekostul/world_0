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
        WORLDZERO_CHANNEL.registerMessage(
                worldzero$packetId++,
                WorldZeroBlankDiscPlaybackPacket.class,
                WorldZeroBlankDiscPlaybackPacket::encode,
                WorldZeroBlankDiscPlaybackPacket::decode,
                WorldZeroBlankDiscPlaybackPacket::handle
        );
        WORLDZERO_CHANNEL.registerMessage(
                worldzero$packetId++,
                WorldZeroBlankDiscFinishedPacket.class,
                WorldZeroBlankDiscFinishedPacket::encode,
                WorldZeroBlankDiscFinishedPacket::decode,
                WorldZeroBlankDiscFinishedPacket::handle
        );
        WORLDZERO_CHANNEL.registerMessage(
                worldzero$packetId++,
                WorldZeroBlankDiscErrorPacket.class,
                WorldZeroBlankDiscErrorPacket::encode,
                WorldZeroBlankDiscErrorPacket::decode,
                WorldZeroBlankDiscErrorPacket::handle
        );
        WORLDZERO_CHANNEL.registerMessage(
                worldzero$packetId++,
                WorldZeroKoridorDoorSoundPacket.class,
                WorldZeroKoridorDoorSoundPacket::encode,
                WorldZeroKoridorDoorSoundPacket::decode,
                WorldZeroKoridorDoorSoundPacket::handle
        );
        WORLDZERO_CHANNEL.registerMessage(
                worldzero$packetId++,
                WorldZeroHouseMusicFinishedPacket.class,
                WorldZeroHouseMusicFinishedPacket::encode,
                WorldZeroHouseMusicFinishedPacket::decode,
                WorldZeroHouseMusicFinishedPacket::handle
        );
    }

    public static void sendFreezeStart(ServerPlayer player, int durationTicks) {
        sendFreezeStart(player, durationTicks, -1, Float.NaN, Float.NaN);
    }

    public static void sendFreezeStart(ServerPlayer player, int durationTicks, int focusEntityId) {
        sendFreezeStart(player, durationTicks, focusEntityId, Float.NaN, Float.NaN);
    }

    public static void sendFreezeStart(
            ServerPlayer player,
            int durationTicks,
            int focusEntityId,
            float forcedYaw,
            float forcedPitch
    ) {
        WORLDZERO_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WorldZeroFreezeStartPacket(durationTicks, focusEntityId, forcedYaw, forcedPitch)
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

    public static void sendBlankDiscPlayback(ServerPlayer player) {
        WORLDZERO_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WorldZeroBlankDiscPlaybackPacket()
        );
    }

    public static void sendBlankDiscPlaybackFinished() {
        WORLDZERO_CHANNEL.sendToServer(new WorldZeroBlankDiscFinishedPacket());
    }

    public static void sendBlankDiscPlaybackError(ServerPlayer player) {
        WORLDZERO_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WorldZeroBlankDiscErrorPacket()
        );
    }

    public static void sendKoridorDoorSound(ServerPlayer player, ResourceLocation soundId, double x, double y, double z) {
        WORLDZERO_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WorldZeroKoridorDoorSoundPacket(soundId, x, y, z)
        );
    }

    public static void sendHouseMusicFinished() {
        WORLDZERO_CHANNEL.sendToServer(new WorldZeroHouseMusicFinishedPacket());
    }
}
