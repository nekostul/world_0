package ru.nekostul.worldzero.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ru.nekostul.worldzero.client.controller.WorldZeroHorrorSoundClientController;

import java.util.function.Supplier;

public final class WorldZeroHorrorSoundPacket {
    public static final byte WORLDZERO_ACTION_PLAY = 0;
    public static final byte WORLDZERO_ACTION_STOP_ALL = 1;

    private final byte worldzero$action;
    private final ResourceLocation worldzero$soundId;
    private final float worldzero$pitch;
    private final boolean worldzero$notifyWhenFinished;

    public WorldZeroHorrorSoundPacket(byte action, ResourceLocation soundId, float pitch, boolean notifyWhenFinished) {
        this.worldzero$action = action;
        this.worldzero$soundId = soundId;
        this.worldzero$pitch = pitch;
        this.worldzero$notifyWhenFinished = notifyWhenFinished;
    }

    public static void encode(WorldZeroHorrorSoundPacket packet, FriendlyByteBuf buffer) {
        buffer.writeByte(packet.worldzero$action);
        buffer.writeBoolean(packet.worldzero$soundId != null);
        if (packet.worldzero$soundId != null) {
            buffer.writeResourceLocation(packet.worldzero$soundId);
        }
        buffer.writeFloat(packet.worldzero$pitch);
        buffer.writeBoolean(packet.worldzero$notifyWhenFinished);
    }

    public static WorldZeroHorrorSoundPacket decode(FriendlyByteBuf buffer) {
        byte action = buffer.readByte();
        ResourceLocation soundId = null;
        if (buffer.readBoolean()) {
            soundId = buffer.readResourceLocation();
        }
        return new WorldZeroHorrorSoundPacket(
                action,
                soundId,
                buffer.readFloat(),
                buffer.readBoolean()
        );
    }

    public static void handle(WorldZeroHorrorSoundPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> {
                    if (packet.worldzero$action == WORLDZERO_ACTION_STOP_ALL) {
                        WorldZeroHorrorSoundClientController.worldzero$stopAll();
                    } else if (packet.worldzero$action == WORLDZERO_ACTION_PLAY && packet.worldzero$soundId != null) {
                        WorldZeroHorrorSoundClientController.worldzero$play(
                                packet.worldzero$soundId,
                                packet.worldzero$pitch,
                                packet.worldzero$notifyWhenFinished
                        );
                    }
                }
        ));
        context.setPacketHandled(true);
    }
}
