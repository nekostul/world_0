package ru.nekostul.worldzero.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nekostul.worldzero.WorldZeroState;

import java.io.IOException;
import java.util.function.Function;

@Mixin(WorldOpenFlows.class)
public abstract class WorldOpenFlowsMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private LevelStorageSource levelSource;

    @Unique
    private static final ThreadLocal<Boolean> WORLDZERO_INTERNAL_CALL = ThreadLocal.withInitial(() -> false);

    @Inject(method = "loadLevel", at = @At("HEAD"), cancellable = true)
    private void worldzero$forcePrimaryLoad(Screen screen, String worldId, CallbackInfo callbackInfo) {
        if (WORLDZERO_INTERNAL_CALL.get()) {
            return;
        }

        int finaleReconnectStage = WorldZeroState.readFinaleReconnectStage(this.minecraft);
        if (finaleReconnectStage == 1) {
            WorldZeroState.writeFinaleReconnectStage(this.minecraft, 2);
            this.worldzero$showFinaleReconnectError(screen, WorldZeroState.finaleDuplicateSessionMessage());
            callbackInfo.cancel();
            return;
        }
        if (finaleReconnectStage == 2) {
            WorldZeroState.writeFinaleReconnectStage(this.minecraft, 3);
            this.worldzero$showFinaleReconnectError(screen, WorldZeroState.finaleActiveSessionMessage());
            callbackInfo.cancel();
            return;
        }

        String primaryWorldId = WorldZeroState.readPrimaryWorldId(this.minecraft);
        if (primaryWorldId == null) {
            this.worldzero$showNoPrimaryScreen(screen);
            callbackInfo.cancel();
            return;
        }

        if (!WorldZeroState.primaryWorldExists(this.levelSource, primaryWorldId)) {
            if (WorldZeroState.restorePrimaryWorldFromBackup(this.minecraft, this.levelSource, primaryWorldId)) {
                WorldZeroState.markReactivationOverlayPending(this.minecraft);
                WORLDZERO_INTERNAL_CALL.set(true);
                try {
                    ((WorldOpenFlows) (Object) this).loadLevel(screen, primaryWorldId);
                } finally {
                    WORLDZERO_INTERNAL_CALL.set(false);
                }

                callbackInfo.cancel();
                return;
            }

            if (WorldZeroState.hiddenPrimaryWorldExists(this.minecraft, primaryWorldId)) {
                this.worldzero$showNoPrimaryScreen(screen);
                callbackInfo.cancel();
                return;
            }

            this.worldzero$recreatePrimaryWorld(primaryWorldId);
            callbackInfo.cancel();
            return;
        }

        if (primaryWorldId.equals(worldId)) {
            return;
        }

        WORLDZERO_INTERNAL_CALL.set(true);
        try {
            ((WorldOpenFlows) (Object) this).loadLevel(screen, primaryWorldId);
        } finally {
            WORLDZERO_INTERNAL_CALL.set(false);
        }

        callbackInfo.cancel();
    }

    @Inject(method = "createFreshLevel", at = @At("HEAD"), cancellable = true)
    private void worldzero$normalizeWorldCreation(
            String worldId,
            LevelSettings levelSettings,
            WorldOptions worldOptions,
            Function<RegistryAccess, WorldDimensions> dimensionsFactory,
            CallbackInfo callbackInfo
    ) {
        if (WORLDZERO_INTERNAL_CALL.get()) {
            return;
        }

        String normalizedWorldId = WorldZeroState.ensureSuffix(worldId);
        LevelSettings normalizedLevelSettings = WorldZeroState.ensureSuffixedLevelName(levelSettings);
        if (!normalizedWorldId.equals(worldId) || normalizedLevelSettings != levelSettings) {
            WORLDZERO_INTERNAL_CALL.set(true);
            try {
                ((WorldOpenFlows) (Object) this).createFreshLevel(
                        normalizedWorldId,
                        normalizedLevelSettings,
                        worldOptions,
                        dimensionsFactory
                );
            } finally {
                WORLDZERO_INTERNAL_CALL.set(false);
            }
            callbackInfo.cancel();
            return;
        }

        String primaryWorldId = WorldZeroState.readPrimaryWorldId(this.minecraft);
        if (primaryWorldId == null || !WorldZeroState.primaryWorldExists(this.levelSource, primaryWorldId)) {
            WorldZeroState.writePrimaryWorldId(this.minecraft, normalizedWorldId);
        }
    }

    @Redirect(
            method = "createFreshLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;doWorldLoad(Ljava/lang/String;Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;Lnet/minecraft/server/packs/repository/PackRepository;Lnet/minecraft/server/WorldStem;Z)V"
            )
    )
    private void worldzero$redirectFreshWorldLoad(
            Minecraft minecraft,
            String worldId,
            LevelStorageSource.LevelStorageAccess levelStorageAccess,
            PackRepository packRepository,
            WorldStem worldStem,
            boolean safeMode
    ) {
        String primaryWorldId = WorldZeroState.readPrimaryWorldId(this.minecraft);
        boolean shouldRedirect = primaryWorldId != null
                && WorldZeroState.primaryWorldExists(this.levelSource, primaryWorldId)
                && !primaryWorldId.equals(worldId);

        if (!shouldRedirect) {
            minecraft.doWorldLoad(worldId, levelStorageAccess, packRepository, worldStem, safeMode);
            return;
        }

        this.worldzero$closeCreatedWorldResources(levelStorageAccess, worldStem);

        WORLDZERO_INTERNAL_CALL.set(true);
        try {
            minecraft.createWorldOpenFlows().loadLevel(minecraft.screen, primaryWorldId);
        } finally {
            WORLDZERO_INTERNAL_CALL.set(false);
        }
    }

    @Unique
    private void worldzero$showNoPrimaryScreen(Screen previousScreen) {
        this.minecraft.setScreen(new AlertScreen(
                () -> this.minecraft.setScreen(previousScreen),
                WorldZeroState.noPrimaryTitle(),
                WorldZeroState.noPrimaryMessage(),
                CommonComponents.GUI_BACK,
                true
        ));
    }

    @Unique
    private void worldzero$showFinaleReconnectError(Screen previousScreen, net.minecraft.network.chat.Component message) {
        this.minecraft.setScreen(new AlertScreen(
                () -> this.minecraft.setScreen(previousScreen),
                WorldZeroState.finaleReconnectTitle(),
                message,
                CommonComponents.GUI_BACK,
                true
        ));
    }

    @Unique
    private void worldzero$recreatePrimaryWorld(String primaryWorldId) {
        WorldZeroState.writePrimaryWorldId(this.minecraft, primaryWorldId);

        WORLDZERO_INTERNAL_CALL.set(true);
        try {
            ((WorldOpenFlows) (Object) this).createFreshLevel(
                    primaryWorldId,
                    WorldZeroState.createDefaultPrimarySettings(primaryWorldId),
                    WorldOptions.defaultWithRandomSeed(),
                    WorldPresets::createNormalWorldDimensions
            );
        } finally {
            WORLDZERO_INTERNAL_CALL.set(false);
        }
    }

    @Unique
    private void worldzero$closeCreatedWorldResources(
            LevelStorageSource.LevelStorageAccess levelStorageAccess,
            WorldStem worldStem
    ) {
        worldStem.close();

        try {
            levelStorageAccess.close();
        } catch (IOException ignored) {
        }
    }
}
