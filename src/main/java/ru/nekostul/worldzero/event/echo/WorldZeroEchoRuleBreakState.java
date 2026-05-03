package ru.nekostul.worldzero.event.echo;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

public final class WorldZeroEchoRuleBreakState {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int WORLDZERO_RULE_BREAK_TRIGGER_INDEX = 5;
    private static final String WORLDZERO_STATE_DIR = "worldzero";
    private static final String WORLDZERO_APPEARANCE_COUNT_FILE = "echo_appearance_count.txt";
    private static final String WORLDZERO_RULE_BREAK_DONE_FILE = "echo_rule_break_done.txt";
    private static final Map<Path, Progress> WORLDZERO_PROGRESS_CACHE = new HashMap<>();

    private WorldZeroEchoRuleBreakState() {
    }

    public static boolean shouldTriggerRuleBreak(MinecraftServer server) {
        Progress progress = worldzero$getProgress(server);
        return !progress.ruleBreakDone && progress.appearanceCount >= (WORLDZERO_RULE_BREAK_TRIGGER_INDEX - 1);
    }

    public static void recordNormalAppearance(MinecraftServer server) {
        Progress progress = worldzero$getProgress(server);
        Progress updated = new Progress(progress.appearanceCount + 1, progress.ruleBreakDone);
        worldzero$storeProgress(server, updated);
    }

    public static void recordRuleBreakAppearance(MinecraftServer server) {
        Progress progress = worldzero$getProgress(server);
        Progress updated = new Progress(progress.appearanceCount + 1, true);
        worldzero$storeProgress(server, updated);
    }

    private static Progress worldzero$getProgress(MinecraftServer server) {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        Progress cached = WORLDZERO_PROGRESS_CACHE.get(worldRoot);
        if (cached != null) {
            return cached;
        }

        Progress loaded = worldzero$loadProgress(worldRoot);
        WORLDZERO_PROGRESS_CACHE.put(worldRoot, loaded);
        return loaded;
    }

    private static void worldzero$storeProgress(MinecraftServer server, Progress progress) {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        WORLDZERO_PROGRESS_CACHE.put(worldRoot, progress);
        worldzero$saveProgress(worldRoot, progress);
    }

    private static Progress worldzero$loadProgress(Path worldRoot) {
        Path stateDirectory = worldzero$getStateDirectory(worldRoot);
        Path appearanceCountFile = stateDirectory.resolve(WORLDZERO_APPEARANCE_COUNT_FILE);
        Path ruleBreakDoneFile = stateDirectory.resolve(WORLDZERO_RULE_BREAK_DONE_FILE);

        int appearanceCount = 0;
        if (Files.exists(appearanceCountFile)) {
            try {
                String raw = Files.readString(appearanceCountFile, StandardCharsets.UTF_8).trim();
                if (!raw.isEmpty()) {
                    appearanceCount = Integer.parseInt(raw);
                }
            } catch (IOException | NumberFormatException exception) {
                LOGGER.warn("Failed to read WORLD_0 echo appearance count", exception);
            }
        }

        boolean ruleBreakDone = Files.exists(ruleBreakDoneFile);
        return new Progress(Math.max(0, appearanceCount), ruleBreakDone);
    }

    private static void worldzero$saveProgress(Path worldRoot, Progress progress) {
        Path stateDirectory = worldzero$getStateDirectory(worldRoot);
        Path appearanceCountFile = stateDirectory.resolve(WORLDZERO_APPEARANCE_COUNT_FILE);
        Path ruleBreakDoneFile = stateDirectory.resolve(WORLDZERO_RULE_BREAK_DONE_FILE);

        try {
            Files.createDirectories(stateDirectory);
            Files.writeString(
                    appearanceCountFile,
                    Integer.toString(progress.appearanceCount) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );

            if (progress.ruleBreakDone) {
                Files.writeString(
                        ruleBreakDoneFile,
                        "done" + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                );
            } else {
                Files.deleteIfExists(ruleBreakDoneFile);
            }
        } catch (IOException exception) {
            LOGGER.warn("Failed to persist WORLD_0 echo rule-break progress", exception);
        }
    }

    private static Path worldzero$getStateDirectory(Path worldRoot) {
        return worldRoot.resolve("data").resolve(WORLDZERO_STATE_DIR);
    }

    private record Progress(int appearanceCount, boolean ruleBreakDone) {
    }
}
