package ru.nekostul.worldzero.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ServerboundChangeDifficultyPacket;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nekostul.worldzero.state.WorldZeroState;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Shadow
    @Final
    private Connection connection;

    @Inject(method = "handleLogin", at = @At("HEAD"), cancellable = true)
    private void worldzero$blockMultiplayerLogin(ClientboundLoginPacket packet, CallbackInfo callbackInfo) {
        if (this.connection.isMemoryConnection()) {
            return;
        }

        this.connection.disconnect(WorldZeroState.multiplayerDisconnectMessage());
        callbackInfo.cancel();
    }

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void worldzero$showReactivationOverlay(ClientboundLoginPacket packet, CallbackInfo callbackInfo) {
        if (!this.connection.isMemoryConnection()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) {
            return;
        }

        if (WorldZeroState.consumeCreateRedirectOverlayPending(minecraft)) {
            minecraft.gui.setOverlayMessage(WorldZeroState.createRedirectOverlayMessage(), false);
            return;
        }

        if (WorldZeroState.consumeReactivationOverlayPending(minecraft)) {
            minecraft.gui.setOverlayMessage(WorldZeroState.reactivationOverlayMessage(), false);
        }

        String primaryWorldId = WorldZeroState.readPrimaryWorldId(minecraft);
        if (primaryWorldId != null) {
            WorldZeroState.refreshPrimaryWorldBackup(minecraft, minecraft.getLevelSource(), primaryWorldId);
        }
    }

    @ModifyVariable(method = "send", at = @At("HEAD"), argsOnly = true)
    private Packet<?> worldzero$forceHardDifficultyPacket(Packet<?> packet) {
        if (!(packet instanceof ServerboundChangeDifficultyPacket)) {
            return packet;
        }

        return new ServerboundChangeDifficultyPacket(Difficulty.HARD);
    }
}
