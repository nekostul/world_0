package ru.nekostul.worldzero.mixin.client;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import java.util.List;

@Mixin(WorldCreationUiState.class)
public abstract class WorldCreationUiStateMixin {
    @Shadow
    public abstract List<WorldCreationUiState.WorldTypeEntry> getNormalPresetList();

    @ModifyVariable(method = "setGameMode", at = @At("HEAD"), argsOnly = true)
    private WorldCreationUiState.SelectedGameMode worldzero$forceSurvivalMode(
            WorldCreationUiState.SelectedGameMode gameMode
    ) {
        return WorldCreationUiState.SelectedGameMode.SURVIVAL;
    }

    @ModifyVariable(method = "setAllowCheats", at = @At("HEAD"), argsOnly = true)
    private boolean worldzero$forceCheatsOff(boolean allowCheats) {
        return false;
    }

    @ModifyVariable(method = "setWorldType", at = @At("HEAD"), argsOnly = true)
    private WorldCreationUiState.WorldTypeEntry worldzero$forceDefaultWorldType(
            WorldCreationUiState.WorldTypeEntry worldType
    ) {
        List<WorldCreationUiState.WorldTypeEntry> normalPresetList = this.getNormalPresetList();
        if (normalPresetList.isEmpty()) {
            return worldType;
        }

        return normalPresetList.get(0);
    }

    @ModifyVariable(method = "setGameRules", at = @At("HEAD"), argsOnly = true)
    private GameRules worldzero$forceDefaultGameRules(GameRules gameRules) {
        return new GameRules();
    }
}
