package ru.nekostul.worldzero;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class WorldZeroKoridorDoorSoundPacket {
    private final ResourceLocation worldzero$soundId;
    private final double worldzero$x;
    private final double worldzero$y;
    private final double worldzero$z;

    public WorldZeroKoridorDoorSoundPacket(ResourceLocation soundId, double x, double y, double z) {
        this.worldzero$soundId = soundId;
        this.worldzero$x = x;
        this.worldzero$y = y;
        this.worldzero$z = z;
    }

    public ResourceLocation worldzero$soundId() {
        return this.worldzero$soundId;
    }

    public double worldzero$x() {
        return this.worldzero$x;
    }

    public double worldzero$y() {
        return this.worldzero$y;
    }

    public double worldzero$z() {
        return this.worldzero$z;
    }

    public static void encode(WorldZeroKoridorDoorSoundPacket packet, FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(packet.worldzero$soundId);
        buffer.writeDouble(packet.worldzero$x);
        buffer.writeDouble(packet.worldzero$y);
        buffer.writeDouble(packet.worldzero$z);
    }

    public static WorldZeroKoridorDoorSoundPacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroKoridorDoorSoundPacket(
                buffer.readResourceLocation(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
        );
    }

    public static void handle(WorldZeroKoridorDoorSoundPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> WorldZeroKoridorClientController.worldzero$playVanillaDoorSound(
                        packet.worldzero$soundId(),
                        packet.worldzero$x(),
                        packet.worldzero$y(),
                        packet.worldzero$z()
                )
        ));
        context.setPacketHandled(true);
    }
}
