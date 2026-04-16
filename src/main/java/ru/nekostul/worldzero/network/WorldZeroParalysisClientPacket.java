package ru.nekostul.worldzero;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class WorldZeroParalysisClientPacket {
    public static final byte WORLDZERO_ACTION_START_BED_VIEW = 0;
    public static final byte WORLDZERO_ACTION_ECHO_GONE = 1;
    public static final byte WORLDZERO_ACTION_START_WARNING = 2;
    public static final byte WORLDZERO_ACTION_RETURN_TO_BED = 3;
    public static final byte WORLDZERO_ACTION_START_FADE = 4;
    public static final byte WORLDZERO_ACTION_CLEAR = 5;

    private final byte worldzero$action;
    private final BlockPos worldzero$targetPos;
    private final int worldzero$entityId;
    private final int worldzero$durationTicks;

    public WorldZeroParalysisClientPacket(byte action, BlockPos targetPos, int entityId, int durationTicks) {
        this.worldzero$action = action;
        this.worldzero$targetPos = targetPos;
        this.worldzero$entityId = entityId;
        this.worldzero$durationTicks = durationTicks;
    }

    public byte worldzero$action() {
        return this.worldzero$action;
    }

    public BlockPos worldzero$targetPos() {
        return this.worldzero$targetPos;
    }

    public int worldzero$entityId() {
        return this.worldzero$entityId;
    }

    public int worldzero$durationTicks() {
        return this.worldzero$durationTicks;
    }

    public static void encode(WorldZeroParalysisClientPacket packet, FriendlyByteBuf buffer) {
        buffer.writeByte(packet.worldzero$action);
        buffer.writeBlockPos(packet.worldzero$targetPos);
        buffer.writeVarInt(packet.worldzero$entityId);
        buffer.writeVarInt(packet.worldzero$durationTicks);
    }

    public static WorldZeroParalysisClientPacket decode(FriendlyByteBuf buffer) {
        return new WorldZeroParalysisClientPacket(
                buffer.readByte(),
                buffer.readBlockPos(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    public static void handle(WorldZeroParalysisClientPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> WorldZeroParalysisClientController.handlePacket(packet)
        ));
        context.setPacketHandled(true);
    }
}
