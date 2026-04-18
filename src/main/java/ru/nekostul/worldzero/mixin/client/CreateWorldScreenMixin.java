package ru.nekostul.worldzero.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.nekostul.worldzero.WorldZeroDevCheats;
import ru.nekostul.worldzero.WorldZeroState;

import java.util.Optional;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {
    @ModifyArg(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/tabs/TabNavigationBar$Builder;addTabs([Lnet/minecraft/client/gui/components/tabs/Tab;)Lnet/minecraft/client/gui/components/tabs/TabNavigationBar$Builder;"
            ),
            index = 0
    )
    private Tab[] worldzero$keepOnlyGameTab(Tab[] tabs) {
        if (WorldZeroDevCheats.isAllowedForCurrentClient() || tabs.length == 0) {
            return tabs;
        }

        return new Tab[]{tabs[0]};
    }

    @Redirect(
            method = "createNewWorldDirectory",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/worldselection/WorldCreationUiState;getTargetFolder()Ljava/lang/String;"
            )
    )
    private String worldzero$forceSuffixedTargetFolder(WorldCreationUiState uiState) {
        return WorldZeroState.resolveCreateTargetFolder(Minecraft.getInstance(), uiState.getName());
    }

    @Redirect(
            method = "createLevelSettings",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/worldselection/WorldCreationUiState;getName()Ljava/lang/String;"
            )
    )
    private String worldzero$forceSuffixedLevelName(WorldCreationUiState uiState) {
        return WorldZeroState.ensureSuffix(uiState.getName());
    }

    @Inject(method = "createNewWorldDirectory", at = @At("HEAD"), cancellable = true)
    private void worldzero$restoreHiddenPrimaryBeforeCreation(
            CallbackInfoReturnable<Optional<LevelStorageSource.LevelStorageAccess>> callbackInfo
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        String primaryWorldId = WorldZeroState.readPrimaryWorldId(minecraft);
        if (primaryWorldId == null) {
            return;
        }

        if (WorldZeroState.primaryWorldExists(minecraft.getLevelSource(), primaryWorldId)) {
            WorldZeroState.markCreateRedirectOverlayPending(minecraft);
            minecraft.createWorldOpenFlows().loadLevel(minecraft.screen, primaryWorldId);
            callbackInfo.setReturnValue(Optional.empty());
            return;
        }

        if (WorldZeroState.restorePrimaryWorldFromBackup(
                minecraft,
                minecraft.getLevelSource(),
                primaryWorldId
        )) {
            WorldZeroState.markReactivationOverlayPending(minecraft);
            minecraft.createWorldOpenFlows().loadLevel(minecraft.screen, primaryWorldId);
            callbackInfo.setReturnValue(Optional.empty());
            return;
        }

        if (!WorldZeroState.hiddenPrimaryWorldExists(minecraft, primaryWorldId)) {
            return;
        }

        boolean restored = WorldZeroState.restoreHiddenPrimaryWorld(
                minecraft,
                minecraft.getLevelSource(),
                primaryWorldId
        );
        if (!restored) {
            return;
        }

        WorldZeroState.markReactivationOverlayPending(minecraft);
        minecraft.createWorldOpenFlows().loadLevel(minecraft.screen, primaryWorldId);
        callbackInfo.setReturnValue(Optional.empty());
    }

    @Inject(method = "createNewWorldDirectory", at = @At("RETURN"))
    private void worldzero$markPrimaryOnStandardCreation(
            CallbackInfoReturnable<Optional<LevelStorageSource.LevelStorageAccess>> callbackInfo
    ) {
        Optional<LevelStorageSource.LevelStorageAccess> levelAccess = callbackInfo.getReturnValue();
        if (levelAccess.isEmpty()) {
            return;
        }

        String createdWorldId = levelAccess.get().getLevelId();
        Minecraft minecraft = Minecraft.getInstance();
        String primaryWorldId = WorldZeroState.readPrimaryWorldId(minecraft);
        if (primaryWorldId == null || !WorldZeroState.primaryWorldExists(minecraft.getLevelSource(), primaryWorldId)) {
            WorldZeroState.writePrimaryWorldId(minecraft, createdWorldId);
        }
    }

    @Inject(method = "createLevelSettings", at = @At("RETURN"), cancellable = true)
    private void worldzero$forceAllowedCreationSettings(CallbackInfoReturnable<LevelSettings> callbackInfo) {
        if (WorldZeroDevCheats.isAllowedForCurrentClient()) {
            return;
        }

        LevelSettings levelSettings = callbackInfo.getReturnValue();
        callbackInfo.setReturnValue(new LevelSettings(
                levelSettings.levelName(),
                GameType.SURVIVAL,
                false,
                Difficulty.HARD,
                false,
                new GameRules(),
                levelSettings.getDataConfiguration(),
                levelSettings.getLifecycle()
        ));
    }
}
