package ru.nekostul.worldzero.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ru.nekostul.worldzero.client.controller.WorldZeroNightDarknessClientController;

import java.util.function.Supplier;

public final class WorldZeroNightDarknessPacket {
    private final boolean worldzero$active;

    public WorldZeroNightDarknessPacket(boolean active) {
        this.worldzero$active = active;
    }

    public static void encode(WorldZeroNightDarknessPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.worldzero$active);
    }

    public static WorldZeroNightDarknessPacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroNightDarknessPacket(buffer.readBoolean());
    }

    public static void handle(WorldZeroNightDarknessPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> WorldZeroNightDarknessClientController.worldzero$setActive(packet.worldzero$active)
        ));
        context.setPacketHandled(true);
    }
}
