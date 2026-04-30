package ru.nekostul.worldzero.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nekostul.worldzero.WorldZeroHorrorEventSystem;

@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
    @Unique
    private static final long WORLDZERO_SPAWN_FADE_START_TICKS = 30L * 60L * 20L;

    @Unique
    private static final long WORLDZERO_SPAWN_FADE_END_TICKS = 180L * 60L * 20L;

    @Unique
    private static final float WORLDZERO_MIN_SPAWN_MULTIPLIER = 0.0f;

    @Inject(method = "spawnCategoryForChunk", at = @At("HEAD"), cancellable = true)
    private static void worldzero$reduceNaturalMobSpawns(
            MobCategory category,
            ServerLevel serverLevel,
            LevelChunk chunk,
            NaturalSpawner.SpawnPredicate spawnPredicate,
            NaturalSpawner.AfterSpawnCallback afterSpawnCallback,
            CallbackInfo callbackInfo
    ) {
        if (category == MobCategory.MISC) {
            return;
        }

        float spawnMultiplier = worldzero$spawnMultiplier(worldzero$worldTicks(serverLevel));
        if (spawnMultiplier <= 0.0f) {
            callbackInfo.cancel();
            return;
        }

        if (spawnMultiplier >= 0.999f) {
            return;
        }

        if (serverLevel.random.nextFloat() > spawnMultiplier) {
            callbackInfo.cancel();
        }
    }

    @Unique
    private static long worldzero$worldTicks(ServerLevel serverLevel) {
        ServerLevel overworld = serverLevel.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return serverLevel.getGameTime();
        }

        return WorldZeroHorrorEventSystem.worldzero$getWorldTicks(overworld);
    }

    @Unique
    private static float worldzero$spawnMultiplier(long worldTicks) {
        if (worldTicks <= WORLDZERO_SPAWN_FADE_START_TICKS) {
            return 1.0f;
        }

        if (worldTicks >= WORLDZERO_SPAWN_FADE_END_TICKS) {
            return WORLDZERO_MIN_SPAWN_MULTIPLIER;
        }

        double progress = (double) (worldTicks - WORLDZERO_SPAWN_FADE_START_TICKS)
                / (double) (WORLDZERO_SPAWN_FADE_END_TICKS - WORLDZERO_SPAWN_FADE_START_TICKS);
        if (progress < 0.0D) {
            progress = 0.0D;
        } else if (progress > 1.0D) {
            progress = 1.0D;
        }

        // Keep early change very subtle, then tighten spawn rates closer to the end window.
        double easedProgress = progress * progress * progress;
        return (float) (1.0D - (1.0D - WORLDZERO_MIN_SPAWN_MULTIPLIER) * easedProgress);
    }
}
