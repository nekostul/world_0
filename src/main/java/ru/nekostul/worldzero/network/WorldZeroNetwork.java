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
        WORLDZERO_CHANNEL.registerMessage(
                worldzero$packetId++,
                WorldZeroHouseClientModePacket.class,
                WorldZeroHouseClientModePacket::encode,
                WorldZeroHouseClientModePacket::decode,
                WorldZeroHouseClientModePacket::handle
        );
        WORLDZERO_CHANNEL.registerMessage(
                worldzero$packetId++,
                WorldZeroHouseChatLinePacket.class,
                WorldZeroHouseChatLinePacket::encode,
                WorldZeroHouseChatLinePacket::decode,
                WorldZeroHouseChatLinePacket::handle
        );
        WORLDZERO_CHANNEL.registerMessage(
                worldzero$packetId++,
                WorldZeroKeyboardBlockPacket.class,
                WorldZeroKeyboardBlockPacket::encode,
                WorldZeroKeyboardBlockPacket::decode,
                WorldZeroKeyboardBlockPacket::handle
        );
        WORLDZERO_CHANNEL.registerMessage(
                worldzero$packetId++,
                WorldZeroLocalizedChatLinePacket.class,
                WorldZeroLocalizedChatLinePacket::encode,
                WorldZeroLocalizedChatLinePacket::decode,
                WorldZeroLocalizedChatLinePacket::handle
        );
        WORLDZERO_CHANNEL.registerMessage(
                worldzero$packetId++,
                WorldZeroDoubleChatPacket.class,
                WorldZeroDoubleChatPacket::encode,
                WorldZeroDoubleChatPacket::decode,
                WorldZeroDoubleChatPacket::handle
        );
        WORLDZERO_CHANNEL.registerMessage(
                worldzero$packetId++,
                WorldZeroMinorAnomalyPacket.class,
                WorldZeroMinorAnomalyPacket::encode,
                WorldZeroMinorAnomalyPacket::decode,
                WorldZeroMinorAnomalyPacket::handle
        );
        WORLDZERO_CHANNEL.registerMessage(
                worldzero$packetId++,
                WorldZeroMajorEventPacket.class,
                WorldZeroMajorEventPacket::encode,
                WorldZeroMajorEventPacket::decode,
                WorldZeroMajorEventPacket::handle
        );
        WORLDZERO_CHANNEL.registerMessage(
                worldzero$packetId++,
                WorldZeroFinalePacket.class,
                WorldZeroFinalePacket::encode,
                WorldZeroFinalePacket::decode,
                WorldZeroFinalePacket::handle
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
        WorldZeroServerFreezeController.worldzero$startFreeze(player, durationTicks, forcedYaw, forcedPitch);
        WORLDZERO_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WorldZeroFreezeStartPacket(
                        durationTicks,
                        focusEntityId,
                        forcedYaw,
                        forcedPitch,
                        player.getX(),
                        player.getY(),
                        player.getZ()
                )
        );
    }

    public static void sendFreezeEnd(ServerPlayer player) {
        WorldZeroServerFreezeController.worldzero$stopFreeze(player);
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

    public static void sendHouseClientMode(ServerPlayer player, byte mode) {
        WORLDZERO_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WorldZeroHouseClientModePacket(mode)
        );
    }

    public static void sendHouseChatLine(ServerPlayer player, String speaker, String defaultMessage, String englishMessage) {
        WORLDZERO_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WorldZeroHouseChatLinePacket(speaker, defaultMessage, englishMessage)
        );
    }

    public static void sendKeyboardBlock(ServerPlayer player, int durationTicks) {
        WORLDZERO_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WorldZeroKeyboardBlockPacket(durationTicks)
        );
    }

    public static void sendLocalizedChatLine(ServerPlayer player, String speaker, String messageKey) {
        WORLDZERO_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WorldZeroLocalizedChatLinePacket(speaker, messageKey)
        );
    }

    public static void sendDoubleChatPlayerLine(ServerPlayer player, String speaker, String messageKey) {
        WORLDZERO_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WorldZeroDoubleChatPacket(
                        WorldZeroDoubleChatPacket.WORLDZERO_ACTION_PLAYER_LINE,
                        speaker,
                        messageKey
                )
        );
    }

    public static void sendDoubleChatSystemLine(ServerPlayer player, String speaker, String messageKey) {
        WORLDZERO_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WorldZeroDoubleChatPacket(
                        WorldZeroDoubleChatPacket.WORLDZERO_ACTION_SYSTEM_LINE,
                        speaker,
                        messageKey
                )
        );
    }

    public static void sendDoubleChatAutoLine(ServerPlayer player, String speaker, String messageKey) {
        WORLDZERO_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WorldZeroDoubleChatPacket(
                        WorldZeroDoubleChatPacket.WORLDZERO_ACTION_AUTO_LINE,
                        speaker,
                        messageKey
                )
        );
    }

    public static void sendDoubleChatLocalPort(ServerPlayer player, String port) {
        WORLDZERO_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WorldZeroDoubleChatPacket(
                        WorldZeroDoubleChatPacket.WORLDZERO_ACTION_LOCAL_PORT,
                        "",
                        port
                )
        );
    }

    public static void sendDoubleChatAutoSelfLine(ServerPlayer player, String messageKey) {
        WORLDZERO_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WorldZeroDoubleChatPacket(
                        WorldZeroDoubleChatPacket.WORLDZERO_ACTION_AUTO_SELF_LINE,
                        "",
                        messageKey
                )
        );
    }

    public static void sendMinorAnomaly(ServerPlayer player, byte action, int durationTicks, int variant) {
        WORLDZERO_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WorldZeroMinorAnomalyPacket(action, durationTicks, variant)
        );
    }

    public static void sendMajorEvent(ServerPlayer player, byte action, int durationTicks, int variant) {
        WORLDZERO_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WorldZeroMajorEventPacket(action, durationTicks, variant)
        );
    }

    public static void sendFinale(ServerPlayer player, byte action, int durationTicks, int variant) {
        WORLDZERO_CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WorldZeroFinalePacket(action, durationTicks, variant)
        );
    }
}
