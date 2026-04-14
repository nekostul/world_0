package ru.nekostul.worldzero.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nekostul.worldzero.WorldZeroState;

import java.io.IOException;
import java.nio.file.Path;

@Mixin(WorldSelectionList.WorldListEntry.class)
public abstract class WorldSelectionListEntryMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private LevelSummary summary;

    @Shadow
    @Final
    WorldSelectionList this$0;

    @Inject(method = "doDeleteWorld", at = @At("HEAD"), cancellable = true)
    private void worldzero$hidePrimaryWorldInsteadOfDelete(CallbackInfo callbackInfo) {
        String worldId = this.summary.getLevelId();
        if (!WorldZeroState.isPrimaryWorld(this.minecraft, worldId)) {
            return;
        }

        Path worldDirectory = this.minecraft.getLevelSource().getBaseDir().resolve(worldId);
        boolean hidden;
        try (LevelStorageSource.LevelStorageAccess ignored = this.minecraft.getLevelSource().createAccess(worldId)) {
            hidden = WorldZeroState.hidePrimaryWorldDirectory(this.minecraft, worldId, worldDirectory);
        } catch (IOException exception) {
            hidden = false;
        }

        if (!hidden) {
            SystemToast.onWorldDeleteFailure(this.minecraft, worldId);
        }

        ((WorldSelectionListAccessor) this.this$0).worldzero$reloadWorldList();
        callbackInfo.cancel();
    }
}
