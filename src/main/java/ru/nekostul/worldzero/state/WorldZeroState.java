package ru.nekostul.worldzero;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Lifecycle;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.stream.Stream;

public final class WorldZeroState {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PRIMARY_FILE_NAME = "primary_world.txt";
    private static final String WORLDZERO_DIR_NAME = ".worldzero";
    private static final String LEGACY_WORLDZERO_DIR_NAME = "worldzero";
    private static final String HIDDEN_WORLD_DIR_NAME = "hidden_worlds";
    private static final String BACKUP_DIR_NAME = "backups";
    private static final String REACTIVATION_FILE_NAME = "reactivation_pending.txt";
    private static final String CREATE_REDIRECT_FILE_NAME = "create_redirect_pending.txt";
    private static final String COMMAND_ANOMALY_FILE_NAME = "command_anomaly_seen.txt";
    private static final String FINALE_RECONNECT_STAGE_FILE_NAME = "finale_reconnect_stage.txt";
    private static final String WORLD_SUFFIX = "_0";
    private static final String DEFAULT_PRIMARY_WORLD_NAME = "World_0";
    private static final String NO_PRIMARY_TITLE_KEY = "worldzero.error.title";
    private static final String NO_PRIMARY_MESSAGE_KEY = "worldzero.error.inactive_world";
    private static final String MULTIPLAYER_ERROR_KEY = "worldzero.error.multiplayer_login_mismatch";
    private static final String REACTIVATED_OVERLAY_KEY = "worldzero.error.reactivated_overlay";
    private static final String CREATE_REDIRECT_OVERLAY_KEY = "worldzero.error.create_redirect_overlay";
    private static final String COMMAND_BLOCKED_VANILLA_KEY = "worldzero.error.command_blocked_vanilla";
    private static final String COMMAND_BLOCKED_TRACE_KEY = "worldzero.error.command_blocked_trace";
    private static final String LAN_CHEATS_TITLE_KEY = "worldzero.error.lan_cheats_title";
    private static final String LAN_CHEATS_MESSAGE_KEY = "worldzero.error.lan_cheats_message";

    private WorldZeroState() {
    }

    public static String ensureSuffix(String value) {
        if (value == null || value.isBlank()) {
            return WORLD_SUFFIX;
        }

        return value.endsWith(WORLD_SUFFIX) ? value : value + WORLD_SUFFIX;
    }

    public static String readPrimaryWorldId(Minecraft minecraft) {
        Path primaryFile = getPrimaryFilePath(minecraft);
        if (!Files.exists(primaryFile)) {
            return null;
        }

        try {
            String worldId = Files.readString(primaryFile, StandardCharsets.UTF_8).trim();
            if (worldId.isEmpty()) {
                return null;
            }
            return ensureSuffix(worldId);
        } catch (IOException exception) {
            LOGGER.warn("Failed to read WORLD_0 primary world marker", exception);
            return null;
        }
    }

    public static void writePrimaryWorldId(Minecraft minecraft, String worldId) {
        String normalizedWorldId = ensureSuffix(worldId);
        Path primaryFile = getPrimaryFilePath(minecraft);

        try {
            Files.createDirectories(primaryFile.getParent());
            Files.writeString(
                    primaryFile,
                    normalizedWorldId + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException exception) {
            LOGGER.warn("Failed to write WORLD_0 primary world marker", exception);
        }
    }

    public static boolean primaryWorldExists(LevelStorageSource levelSource, String worldId) {
        return worldId != null && levelSource.levelExists(worldId);
    }

    public static boolean isPrimaryWorld(Minecraft minecraft, String worldId) {
        String primaryWorldId = readPrimaryWorldId(minecraft);
        if (primaryWorldId == null || worldId == null) {
            return false;
        }

        return primaryWorldId.equals(ensureSuffix(worldId));
    }

    public static String resolveCreateTargetFolder(Minecraft minecraft, String requestedName) {
        String candidate = ensureSuffix(requestedName);
        if (minecraft == null) {
            return candidate;
        }

        LevelStorageSource levelSource = minecraft.getLevelSource();
        while (levelSource.levelExists(candidate)) {
            candidate = candidate + WORLD_SUFFIX;
        }

        return candidate;
    }

    public static boolean hiddenPrimaryWorldExists(Minecraft minecraft, String worldId) {
        if (worldId == null || worldId.isBlank()) {
            return false;
        }

        return Files.exists(getHiddenWorldPath(minecraft, worldId));
    }

    public static boolean restorePrimaryWorldFromBackup(
            Minecraft minecraft,
            LevelStorageSource levelSource,
            String worldId
    ) {
        String normalizedWorldId = ensureSuffix(worldId);
        Path backupPath = getBackupWorldPath(minecraft, normalizedWorldId);
        if (!Files.exists(backupPath)) {
            return false;
        }

        Path visiblePath = levelSource.getBaseDir().resolve(normalizedWorldId);
        if (Files.exists(visiblePath)) {
            return false;
        }

        try {
            copyDirectory(backupPath, visiblePath);
            return true;
        } catch (IOException exception) {
            LOGGER.warn("Failed to restore WORLD_0 primary world from backup {}", normalizedWorldId, exception);
            try {
                if (Files.exists(visiblePath)) {
                    deleteDirectory(visiblePath);
                }
            } catch (IOException ignored) {
            }
            return false;
        }
    }

    public static boolean refreshPrimaryWorldBackup(
            Minecraft minecraft,
            LevelStorageSource levelSource,
            String worldId
    ) {
        if (worldId == null || worldId.isBlank()) {
            return false;
        }

        String normalizedWorldId = ensureSuffix(worldId);
        Path visiblePath = levelSource.getBaseDir().resolve(normalizedWorldId);
        if (!Files.exists(visiblePath)) {
            return false;
        }

        Path backupPath = getBackupWorldPath(minecraft, normalizedWorldId);
        try {
            if (Files.exists(backupPath)) {
                deleteDirectory(backupPath);
            }

            copyDirectory(visiblePath, backupPath);
            return true;
        } catch (IOException exception) {
            LOGGER.warn("Failed to refresh WORLD_0 primary world backup {}", normalizedWorldId, exception);
            return false;
        }
    }

    public static boolean restoreHiddenPrimaryWorld(
            Minecraft minecraft,
            LevelStorageSource levelSource,
            String worldId
    ) {
        String normalizedWorldId = ensureSuffix(worldId);
        Path hiddenPath = getHiddenWorldPath(minecraft, normalizedWorldId);
        if (!Files.exists(hiddenPath)) {
            return false;
        }

        Path visiblePath = levelSource.getBaseDir().resolve(normalizedWorldId);
        if (Files.exists(visiblePath)) {
            return false;
        }

        try {
            Files.createDirectories(visiblePath.getParent());
            Files.move(hiddenPath, visiblePath);
            return true;
        } catch (IOException exception) {
            LOGGER.warn("Failed to restore hidden WORLD_0 primary world {}", normalizedWorldId, exception);
            return false;
        }
    }

    public static boolean hidePrimaryWorldDirectory(Minecraft minecraft, String worldId, Path worldDirectory) {
        String normalizedWorldId = ensureSuffix(worldId);
        Path hiddenPath = getHiddenWorldPath(minecraft, normalizedWorldId);
        if (worldDirectory == null || !Files.exists(worldDirectory)) {
            return false;
        }

        try {
            Files.createDirectories(hiddenPath.getParent());
            if (Files.exists(hiddenPath)) {
                deleteDirectory(hiddenPath);
            }

            Files.move(worldDirectory, hiddenPath);
            return true;
        } catch (IOException exception) {
            LOGGER.warn("Failed to hide WORLD_0 primary world {}", normalizedWorldId, exception);
            return false;
        }
    }

    public static void markReactivationOverlayPending(Minecraft minecraft) {
        Path pendingFile = getReactivationFilePath(minecraft);

        try {
            Files.createDirectories(pendingFile.getParent());
            Files.writeString(
                    pendingFile,
                    "pending" + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException exception) {
            LOGGER.warn("Failed to mark WORLD_0 reactivation overlay", exception);
        }
    }

    public static boolean consumeReactivationOverlayPending(Minecraft minecraft) {
        Path pendingFile = getReactivationFilePath(minecraft);
        if (!Files.exists(pendingFile)) {
            return false;
        }

        try {
            Files.deleteIfExists(pendingFile);
            return true;
        } catch (IOException exception) {
            LOGGER.warn("Failed to consume WORLD_0 reactivation overlay", exception);
            return false;
        }
    }

    public static void markCreateRedirectOverlayPending(Minecraft minecraft) {
        Path pendingFile = getCreateRedirectFilePath(minecraft);

        try {
            Files.createDirectories(pendingFile.getParent());
            Files.writeString(
                    pendingFile,
                    "pending" + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException exception) {
            LOGGER.warn("Failed to mark WORLD_0 create redirect overlay", exception);
        }
    }

    public static boolean consumeCreateRedirectOverlayPending(Minecraft minecraft) {
        Path pendingFile = getCreateRedirectFilePath(minecraft);
        if (!Files.exists(pendingFile)) {
            return false;
        }

        try {
            Files.deleteIfExists(pendingFile);
            return true;
        } catch (IOException exception) {
            LOGGER.warn("Failed to consume WORLD_0 create redirect overlay", exception);
            return false;
        }
    }

    public static LevelSettings ensureSuffixedLevelName(LevelSettings settings) {
        String suffixedName = ensureSuffix(settings.levelName());
        if (suffixedName.equals(settings.levelName())) {
            return settings;
        }

        return new LevelSettings(
                suffixedName,
                settings.gameType(),
                settings.hardcore(),
                settings.difficulty(),
                settings.allowCommands(),
                settings.gameRules().copy(),
                settings.getDataConfiguration(),
                settings.getLifecycle()
        );
    }

    public static LevelSettings createDefaultPrimarySettings(String worldId) {
        String levelName = worldId == null || worldId.isBlank()
                ? ensureSuffix(DEFAULT_PRIMARY_WORLD_NAME)
                : ensureSuffix(worldId);
        return new LevelSettings(
                levelName,
                GameType.SURVIVAL,
                false,
                Difficulty.HARD,
                false,
                new GameRules(),
                WorldDataConfiguration.DEFAULT,
                Lifecycle.stable()
        );
    }

    public static Component noPrimaryTitle() {
        return Component.translatable(NO_PRIMARY_TITLE_KEY);
    }

    public static Component noPrimaryMessage() {
        return Component.translatable(NO_PRIMARY_MESSAGE_KEY);
    }

    public static Component multiplayerDisconnectMessage() {
        return Component.translatable(MULTIPLAYER_ERROR_KEY);
    }

    public static Component reactivationOverlayMessage() {
        return Component.translatable(REACTIVATED_OVERLAY_KEY);
    }

    public static Component createRedirectOverlayMessage() {
        return Component.translatable(CREATE_REDIRECT_OVERLAY_KEY);
    }

    public static Component commandBlockedMessage() {
        return Component.translatable(COMMAND_BLOCKED_VANILLA_KEY)
                .append(Component.literal("\n"))
                .append(Component.translatable(COMMAND_BLOCKED_TRACE_KEY).withStyle(ChatFormatting.DARK_GRAY));
    }

    public static Component lanCheatsTitle() {
        return Component.translatable(LAN_CHEATS_TITLE_KEY);
    }

    public static Component lanCheatsMessage() {
        return Component.translatable(LAN_CHEATS_MESSAGE_KEY);
    }

    public static Component finaleReconnectTitle() {
        return Component.literal("Failed to connect to world");
    }

    public static Component finaleDuplicateSessionMessage() {
        return Component.literal("Player already in world\nDisconnecting duplicate session");
    }

    public static Component finaleActiveSessionMessage() {
        return Component.literal("Player already in world\nLocation: unknown\nState: active\n\nDisconnecting you");
    }

    public static int readFinaleReconnectStage(Minecraft minecraft) {
        if (minecraft == null) {
            return 0;
        }

        Path stageFile = getFinaleReconnectStageFilePath(minecraft);
        if (!Files.exists(stageFile)) {
            return 0;
        }

        try {
            return Math.max(0, Integer.parseInt(Files.readString(stageFile, StandardCharsets.UTF_8).trim()));
        } catch (IOException | NumberFormatException exception) {
            LOGGER.warn("Failed to read WORLD_0 finale reconnect stage", exception);
            return 0;
        }
    }

    public static void writeFinaleReconnectStage(Minecraft minecraft, int stage) {
        if (minecraft == null) {
            return;
        }

        Path stageFile = getFinaleReconnectStageFilePath(minecraft);
        try {
            Files.createDirectories(stageFile.getParent());
            Files.writeString(
                    stageFile,
                    Math.max(0, stage) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException exception) {
            LOGGER.warn("Failed to write WORLD_0 finale reconnect stage", exception);
        }
    }

    public static void clearFinaleReconnectStage(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }

        try {
            Files.deleteIfExists(getFinaleReconnectStageFilePath(minecraft));
        } catch (IOException exception) {
            LOGGER.warn("Failed to clear WORLD_0 finale reconnect stage", exception);
        }
    }

    public static boolean hasSeenCommandAnomaly(Minecraft minecraft) {
        if (minecraft == null) {
            return false;
        }

        return Files.exists(getCommandAnomalyFilePath(minecraft));
    }

    public static void markCommandAnomalySeen(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }

        Path markerFile = getCommandAnomalyFilePath(minecraft);
        try {
            Files.createDirectories(markerFile.getParent());
            Files.writeString(
                    markerFile,
                    "seen" + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException exception) {
            LOGGER.warn("Failed to persist WORLD_0 command anomaly marker", exception);
        }
    }

    private static void deleteDirectory(Path directory) throws IOException {
        try (Stream<Path> pathStream = Files.walk(directory)) {
            pathStream
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    });
        } catch (RuntimeException runtimeException) {
            if (runtimeException.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw runtimeException;
        }
    }

    private static void copyDirectory(Path sourceDirectory, Path targetDirectory) throws IOException {
        try (Stream<Path> pathStream = Files.walk(sourceDirectory)) {
            pathStream.forEach(sourcePath -> {
                Path relativePath = sourceDirectory.relativize(sourcePath);
                if (relativePath.getFileName() != null && "session.lock".equals(relativePath.getFileName().toString())) {
                    return;
                }

                Path targetPath = relativePath.getNameCount() == 0
                        ? targetDirectory
                        : targetDirectory.resolve(relativePath);
                try {
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        } catch (RuntimeException runtimeException) {
            if (runtimeException.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw runtimeException;
        }
    }

    private static Path getHiddenWorldPath(Minecraft minecraft, String worldId) {
        return getWorldZeroDirectory(minecraft)
                .resolve(HIDDEN_WORLD_DIR_NAME)
                .resolve(ensureSuffix(worldId));
    }

    private static Path getBackupWorldPath(Minecraft minecraft, String worldId) {
        return getWorldZeroDirectory(minecraft)
                .resolve(HIDDEN_WORLD_DIR_NAME)
                .resolve(BACKUP_DIR_NAME)
                .resolve(ensureSuffix(worldId));
    }

    private static Path getReactivationFilePath(Minecraft minecraft) {
        return getWorldZeroDirectory(minecraft).resolve(REACTIVATION_FILE_NAME);
    }

    private static Path getPrimaryFilePath(Minecraft minecraft) {
        return getWorldZeroDirectory(minecraft).resolve(PRIMARY_FILE_NAME);
    }

    private static Path getCreateRedirectFilePath(Minecraft minecraft) {
        return getWorldZeroDirectory(minecraft).resolve(CREATE_REDIRECT_FILE_NAME);
    }

    private static Path getCommandAnomalyFilePath(Minecraft minecraft) {
        return getWorldZeroDirectory(minecraft).resolve(COMMAND_ANOMALY_FILE_NAME);
    }

    private static Path getFinaleReconnectStageFilePath(Minecraft minecraft) {
        return getWorldZeroDirectory(minecraft).resolve(FINALE_RECONNECT_STAGE_FILE_NAME);
    }

    private static Path getWorldZeroDirectory(Minecraft minecraft) {
        Path gameDirectory = minecraft.gameDirectory.toPath();
        Path hiddenDirectory = gameDirectory.resolve(WORLDZERO_DIR_NAME);
        Path legacyDirectory = gameDirectory.resolve(LEGACY_WORLDZERO_DIR_NAME);

        if (Files.exists(legacyDirectory) && !Files.exists(hiddenDirectory)) {
            try {
                Files.move(legacyDirectory, hiddenDirectory);
            } catch (IOException exception) {
                LOGGER.warn("Failed to migrate legacy WORLD_0 storage directory", exception);
                return legacyDirectory;
            }
        }

        try {
            Files.createDirectories(hiddenDirectory);
            worldzero$markHiddenIfSupported(hiddenDirectory);
        } catch (IOException exception) {
            LOGGER.warn("Failed to initialize hidden WORLD_0 storage directory", exception);
        }

        return hiddenDirectory;
    }

    private static void worldzero$markHiddenIfSupported(Path path) {
        try {
            Files.setAttribute(path, "dos:hidden", true);
        } catch (UnsupportedOperationException | FileSystemException ignored) {
        } catch (IOException exception) {
            LOGGER.warn("Failed to set hidden attribute for WORLD_0 directory {}", path, exception);
        }
    }
}
