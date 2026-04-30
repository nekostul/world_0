package ru.nekostul.worldzero;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroVoidDimension {
    public static final ResourceKey<Level> WORLDZERO_VOID_LEVEL = ResourceKey.create(
            Registries.DIMENSION,
            new ResourceLocation(WorldZeroMod.MOD_ID, "void")
    );

    private static final int WORLDZERO_FREEZE_TICKS = 180;
    private static final int WORLDZERO_ECHO_TAKE_OBSIDIAN_TICK = 320;
    private static final int WORLDZERO_FINAL_TICK = 4100;
    private static final int WORLDZERO_FINAL_MENU_TICK = 4140;
    private static final int WORLDZERO_KEYBOARD_BLOCK_TICKS = WORLDZERO_FINAL_TICK + 1;
    private static final int WORLDZERO_ABSOLUTE_EMPTY_TICKS = 30;
    private static final int WORLDZERO_ABSOLUTE_ATTACK_TICKS = 40;
    private static final int WORLDZERO_ABSOLUTE_TOTAL_TICKS = WORLDZERO_ABSOLUTE_EMPTY_TICKS + WORLDZERO_ABSOLUTE_ATTACK_TICKS;
    private static final int WORLDZERO_ABSOLUTE_RUN_TICKS = 8;
    private static final int WORLDZERO_ECHO_MOVE_TICKS = 20;
    private static final int WORLDZERO_ECHO_GLITCH_COUNT = 7;
    private static final float WORLDZERO_ECHO_PLACE_PITCH = 75.0F;
    private static final String WORLDZERO_SANEK_NAME = "sanek0001";
    private static final BlockPos WORLDZERO_FIRST_OBSIDIAN_POS = new BlockPos(-3, 64, 0);
    private static final BlockPos WORLDZERO_SECOND_OBSIDIAN_POS = new BlockPos(3, 64, 0);
    private static final int[] WORLDZERO_BUILD_TICKS = {360, 700, 1100, 1600, 2750};
    private static final BlockPos[] WORLDZERO_BUILD_POSITIONS = {
            new BlockPos(2, 64, 0),
            new BlockPos(1, 64, 0),
            new BlockPos(0, 64, 0),
            new BlockPos(-1, 64, 0),
            new BlockPos(-2, 64, 0)
    };
    private static final int WORLDZERO_CLEAR_RADIUS_X = 4;
    private static final int WORLDZERO_CLEAR_RADIUS_Z = 1;
    private static final int WORLDZERO_CLEAR_MIN_Y = 63;
    private static final int WORLDZERO_CLEAR_MAX_Y = 67;
    private static final float WORLDZERO_PLAYER_YAW = -90.0F;
    private static final float WORLDZERO_PLAYER_PITCH = 0.0F;
    private static final Map<MinecraftServer, Map<UUID, ReturnPoint>> WORLDZERO_RETURN_POINTS = new WeakHashMap<>();
    private static final Map<MinecraftServer, Map<UUID, VoidSceneState>> WORLDZERO_SCENES = new WeakHashMap<>();
    private static final Map<MinecraftServer, Map<UUID, AbsoluteFinalState>> WORLDZERO_ABSOLUTE_FINALS = new WeakHashMap<>();

    private WorldZeroVoidDimension() {
    }

    @SubscribeEvent
    public static void worldzero$onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.level instanceof ServerLevel level)
                || level.dimension() != WORLDZERO_VOID_LEVEL) {
            return;
        }

        MinecraftServer server = level.getServer();
        Map<UUID, VoidSceneState> scenes = WORLDZERO_SCENES.get(server);
        Map<UUID, AbsoluteFinalState> absoluteFinals = WORLDZERO_ABSOLUTE_FINALS.get(server);
        boolean hasScenes = scenes != null && !scenes.isEmpty();
        boolean hasAbsoluteFinals = absoluteFinals != null && !absoluteFinals.isEmpty();
        if (!hasScenes && !hasAbsoluteFinals) {
            return;
        }

        long gameTime = level.getGameTime();
        if (hasScenes) {
            Iterator<Map.Entry<UUID, VoidSceneState>> iterator = scenes.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, VoidSceneState> entry = iterator.next();
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                VoidSceneState scene = entry.getValue();
                if (player == null || !player.isAlive() || player.serverLevel().dimension() != WORLDZERO_VOID_LEVEL) {
                    worldzero$discardSceneEcho(server, scene);
                    iterator.remove();
                    continue;
                }

                if (gameTime < scene.worldzero$startTick + WORLDZERO_FREEZE_TICKS) {
                    worldzero$applyFullFreeze(player, scene);
                }

                int elapsedTicks = (int) (gameTime - scene.worldzero$startTick);
                worldzero$tickVoidDialogue(player, scene, elapsedTicks);
                worldzero$tickEchoBuild(level, scene, elapsedTicks);
                if (worldzero$isGlitchTick(scene, elapsedTicks)) {
                    worldzero$applyEchoGlitch(level, scene);
                }

                if (!scene.worldzero$finished && elapsedTicks >= WORLDZERO_FINAL_TICK) {
                    scene.worldzero$finished = true;
                    WorldZeroNetwork.sendKeyboardBlock(player, 0);
                }
                if (scene.worldzero$finaleScene && !scene.worldzero$finalMenuSent && elapsedTicks >= WORLDZERO_FINAL_MENU_TICK) {
                    scene.worldzero$finalMenuSent = true;
                    worldzero$discardSceneEcho(server, scene);
                    iterator.remove();
                    WorldZeroHorrorFinale.worldzero$finishFinalVoid(player);
                }
            }
        }
        if (hasAbsoluteFinals) {
            worldzero$tickAbsoluteFinals(level, server, absoluteFinals, gameTime);
        }
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_RETURN_POINTS.clear();
        WORLDZERO_SCENES.clear();
        WORLDZERO_ABSOLUTE_FINALS.clear();
    }

    public static boolean worldzero$teleportPlayerToVoid(ServerPlayer player) {
        return worldzero$teleportPlayerToVoid(player, false);
    }

    public static boolean worldzero$teleportPlayerToFinalVoid(ServerPlayer player) {
        return worldzero$teleportPlayerToVoid(player, true);
    }

    public static boolean worldzero$startAbsoluteFinal(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        ServerLevel voidLevel = server.getLevel(WORLDZERO_VOID_LEVEL);
        if (voidLevel == null) {
            return false;
        }

        worldzero$prepareVoidLevel(voidLevel);
        worldzero$ensureBuiltPath(voidLevel);
        worldzero$clearVoidScene(server, player.getUUID());
        worldzero$clearAbsoluteFinal(server, player.getUUID());

        if (player.isSleeping()) {
            player.stopSleepInBed(false, true);
        }
        voidLevel.getChunkAt(WORLDZERO_FIRST_OBSIDIAN_POS);
        voidLevel.getChunkAt(WORLDZERO_SECOND_OBSIDIAN_POS);
        double lockedX = WORLDZERO_FIRST_OBSIDIAN_POS.getX() + 0.5D;
        double lockedY = WORLDZERO_FIRST_OBSIDIAN_POS.getY() + 1.0D;
        double lockedZ = WORLDZERO_FIRST_OBSIDIAN_POS.getZ() + 0.5D;
        player.teleportTo(voidLevel, lockedX, lockedY, lockedZ, WORLDZERO_PLAYER_YAW, WORLDZERO_PLAYER_PITCH);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;

        WORLDZERO_ABSOLUTE_FINALS.computeIfAbsent(server, ignored -> new HashMap<>()).put(
                player.getUUID(),
                new AbsoluteFinalState(
                        voidLevel.getGameTime(),
                        lockedX,
                        lockedY,
                        lockedZ,
                        WORLDZERO_PLAYER_YAW,
                        WORLDZERO_PLAYER_PITCH
                )
        );
        WorldZeroNetwork.sendFreezeStart(
                player,
                WORLDZERO_ABSOLUTE_TOTAL_TICKS,
                -1,
                WORLDZERO_PLAYER_YAW,
                WORLDZERO_PLAYER_PITCH
        );
        WorldZeroNetwork.sendKeyboardBlock(player, WORLDZERO_ABSOLUTE_TOTAL_TICKS + 5);
        return true;
    }

    private static boolean worldzero$teleportPlayerToVoid(ServerPlayer player, boolean finalScene) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        ServerLevel voidLevel = server.getLevel(WORLDZERO_VOID_LEVEL);
        if (voidLevel == null) {
            return false;
        }

        Map<UUID, ReturnPoint> returnPoints = WORLDZERO_RETURN_POINTS.computeIfAbsent(server, ignored -> new HashMap<>());
        if (!finalScene && returnPoints.containsKey(player.getUUID())) {
            return false;
        }
        if (finalScene) {
            returnPoints.remove(player.getUUID());
        }

        worldzero$prepareVoidLevel(voidLevel);
        worldzero$clearVoidScene(server, player.getUUID());
        WorldZeroEchoEntity echo = worldzero$spawnVoidEcho(voidLevel, player);
        if (echo == null) {
            return false;
        }

        if (!finalScene) {
            returnPoints.put(
                    player.getUUID(),
                    new ReturnPoint(
                            player.serverLevel().dimension(),
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            player.getYRot(),
                            player.getXRot()
                    )
            );
        }

        if (player.isSleeping()) {
            player.stopSleepInBed(false, true);
        }
        voidLevel.getChunkAt(WORLDZERO_FIRST_OBSIDIAN_POS);
        player.teleportTo(
                voidLevel,
                WORLDZERO_FIRST_OBSIDIAN_POS.getX() + 0.5D,
                WORLDZERO_FIRST_OBSIDIAN_POS.getY() + 1.0D,
                WORLDZERO_FIRST_OBSIDIAN_POS.getZ() + 0.5D,
                WORLDZERO_PLAYER_YAW,
                WORLDZERO_PLAYER_PITCH
        );
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        WORLDZERO_SCENES.computeIfAbsent(server, ignored -> new HashMap<>()).put(
                player.getUUID(),
                new VoidSceneState(
                        voidLevel.getGameTime(),
                        WORLDZERO_FIRST_OBSIDIAN_POS.getX() + 0.5D,
                        WORLDZERO_FIRST_OBSIDIAN_POS.getY() + 1.0D,
                        WORLDZERO_FIRST_OBSIDIAN_POS.getZ() + 0.5D,
                        WORLDZERO_PLAYER_YAW,
                        WORLDZERO_PLAYER_PITCH,
                        echo.getUUID(),
                        worldzero$createGlitchTicks(voidLevel),
                        finalScene
                )
        );
        WorldZeroNetwork.sendFreezeStart(
                player,
                WORLDZERO_FREEZE_TICKS,
                -1,
                WORLDZERO_PLAYER_YAW,
                WORLDZERO_PLAYER_PITCH
        );
        WorldZeroNetwork.sendKeyboardBlock(player, finalScene ? WORLDZERO_FINAL_MENU_TICK + 1 : WORLDZERO_KEYBOARD_BLOCK_TICKS);
        return true;
    }

    public static boolean worldzero$returnPlayerFromVoid(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        Map<UUID, ReturnPoint> returnPoints = WORLDZERO_RETURN_POINTS.computeIfAbsent(server, ignored -> new HashMap<>());
        ReturnPoint returnPoint = returnPoints.remove(player.getUUID());
        worldzero$clearVoidScene(server, player.getUUID());
        if (returnPoint == null && player.serverLevel().dimension() != WORLDZERO_VOID_LEVEL) {
            return false;
        }

        ServerLevel targetLevel = null;
        double targetX;
        double targetY;
        double targetZ;
        float targetYaw;
        float targetPitch;
        if (returnPoint != null) {
            targetLevel = server.getLevel(returnPoint.worldzero$dimension);
        }

        if (targetLevel != null) {
            targetX = returnPoint.worldzero$x;
            targetY = returnPoint.worldzero$y;
            targetZ = returnPoint.worldzero$z;
            targetYaw = returnPoint.worldzero$yaw;
            targetPitch = returnPoint.worldzero$pitch;
        } else {
            targetLevel = server.overworld();
            if (targetLevel == null) {
                return false;
            }

            BlockPos spawnPos = targetLevel.getSharedSpawnPos();
            targetX = spawnPos.getX() + 0.5D;
            targetY = spawnPos.getY();
            targetZ = spawnPos.getZ() + 0.5D;
            targetYaw = targetLevel.getSharedSpawnAngle();
            targetPitch = 0.0F;
        }

        player.teleportTo(targetLevel, targetX, targetY, targetZ, targetYaw, targetPitch);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        WorldZeroNetwork.sendFreezeEnd(player);
        WorldZeroNetwork.sendKeyboardBlock(player, 0);
        return true;
    }

    private static void worldzero$tickVoidDialogue(ServerPlayer player, VoidSceneState scene, int elapsedTicks) {
        String echoName = player.getGameProfile().getName();
        switch (elapsedTicks) {
            case 40 -> worldzero$sendChatLine(player, echoName, "message.worldzero.void.line.0");
            case 100 -> worldzero$sendChatLine(player, WORLDZERO_SANEK_NAME, "message.worldzero.void.line.1");
            case 180 -> worldzero$sendChatLine(player, echoName, "message.worldzero.void.line.2");
            case 260 -> worldzero$sendChatLine(player, WORLDZERO_SANEK_NAME, "message.worldzero.void.line.3");
            case 480 -> worldzero$sendChatLine(player, echoName, "message.worldzero.void.line.4");
            case 560 -> worldzero$sendChatLine(player, WORLDZERO_SANEK_NAME, "message.worldzero.void.line.5");
            case 860 -> worldzero$sendChatLine(player, echoName, "message.worldzero.void.line.6");
            case 940 -> worldzero$sendChatLine(player, WORLDZERO_SANEK_NAME, "message.worldzero.void.line.7");
            case 1300 -> worldzero$sendChatLine(player, echoName, "message.worldzero.void.line.8");
            case 1380 -> worldzero$sendChatLine(player, WORLDZERO_SANEK_NAME, "message.worldzero.void.line.9");
            case 1850 -> worldzero$sendChatLine(player, echoName, "message.worldzero.void.line.10");
            case 1930 -> worldzero$sendChatLine(player, WORLDZERO_SANEK_NAME, "message.worldzero.void.line.11");
            case 2050 -> worldzero$sendChatLine(player, echoName, "message.worldzero.void.line.12");
            case 2200 -> worldzero$sendChatLine(player, WORLDZERO_SANEK_NAME, "message.worldzero.void.line.13");
            case 2450 -> worldzero$sendChatLine(player, WORLDZERO_SANEK_NAME, "message.worldzero.void.line.14");
            case 3000 -> worldzero$sendChatLine(player, echoName, "message.worldzero.void.line.15");
            case 3080 -> worldzero$sendChatLine(player, WORLDZERO_SANEK_NAME, "message.worldzero.void.line.16");
            case 3200 -> worldzero$sendChatLine(player, echoName, "message.worldzero.void.line.17");
            case 3280 -> worldzero$sendChatLine(player, WORLDZERO_SANEK_NAME, "message.worldzero.void.line.18");
            case 3500 -> worldzero$sendChatLine(player, echoName, "message.worldzero.void.line.19");
            case 3700 -> worldzero$sendChatLine(player, WORLDZERO_SANEK_NAME, "message.worldzero.void.line.20");
            case 4100 -> worldzero$sendChatLine(player, WORLDZERO_SANEK_NAME, "message.worldzero.void.line.21");
            default -> {
            }
        }
    }

    private static void worldzero$tickEchoBuild(ServerLevel level, VoidSceneState scene, int elapsedTicks) {
        Entity entity = level.getEntity(scene.worldzero$echoId);
        if (!(entity instanceof WorldZeroEchoEntity echo)) {
            return;
        }

        worldzero$tickEchoMovement(echo, scene);
        if (scene.worldzero$moveTicksRemaining > 0) {
            return;
        }

        if (!scene.worldzero$echoHoldingObsidian && elapsedTicks >= WORLDZERO_ECHO_TAKE_OBSIDIAN_TICK) {
            echo.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Blocks.OBSIDIAN));
            scene.worldzero$echoHoldingObsidian = true;
        }

        while (scene.worldzero$builtBlocks < WORLDZERO_BUILD_TICKS.length
                && elapsedTicks >= WORLDZERO_BUILD_TICKS[scene.worldzero$builtBlocks]) {
            BlockPos buildPos = WORLDZERO_BUILD_POSITIONS[scene.worldzero$builtBlocks];
            worldzero$lookDownForPlacement(echo);
            level.setBlock(buildPos, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            worldzero$startEchoMovement(scene, echo, buildPos);
            scene.worldzero$builtBlocks++;
            return;
        }
    }

    private static void worldzero$lookDownForPlacement(WorldZeroEchoEntity echo) {
        echo.setXRot(WORLDZERO_ECHO_PLACE_PITCH);
    }

    private static void worldzero$startEchoMovement(VoidSceneState scene, WorldZeroEchoEntity echo, BlockPos targetPos) {
        scene.worldzero$moveStartX = echo.getX();
        scene.worldzero$moveStartY = echo.getY();
        scene.worldzero$moveStartZ = echo.getZ();
        scene.worldzero$moveTargetX = targetPos.getX() + 0.5D;
        scene.worldzero$moveTargetY = targetPos.getY() + 1.0D;
        scene.worldzero$moveTargetZ = targetPos.getZ() + 0.5D;
        scene.worldzero$moveTicksRemaining = WORLDZERO_ECHO_MOVE_TICKS;
    }

    private static void worldzero$tickEchoMovement(WorldZeroEchoEntity echo, VoidSceneState scene) {
        if (scene.worldzero$moveTicksRemaining <= 0) {
            return;
        }

        int elapsedMoveTicks = WORLDZERO_ECHO_MOVE_TICKS - scene.worldzero$moveTicksRemaining + 1;
        double progress = elapsedMoveTicks / (double) WORLDZERO_ECHO_MOVE_TICKS;
        double nextX = scene.worldzero$moveStartX + (scene.worldzero$moveTargetX - scene.worldzero$moveStartX) * progress;
        double nextY = scene.worldzero$moveStartY + (scene.worldzero$moveTargetY - scene.worldzero$moveStartY) * progress;
        double nextZ = scene.worldzero$moveStartZ + (scene.worldzero$moveTargetZ - scene.worldzero$moveStartZ) * progress;
        echo.setPos(nextX, nextY, nextZ);
        echo.setDeltaMovement(
                (scene.worldzero$moveTargetX - scene.worldzero$moveStartX) / WORLDZERO_ECHO_MOVE_TICKS,
                (scene.worldzero$moveTargetY - scene.worldzero$moveStartY) / WORLDZERO_ECHO_MOVE_TICKS,
                (scene.worldzero$moveTargetZ - scene.worldzero$moveStartZ) / WORLDZERO_ECHO_MOVE_TICKS
        );
        echo.hasImpulse = true;
        scene.worldzero$moveTicksRemaining--;

        if (scene.worldzero$moveTicksRemaining <= 0) {
            echo.setPos(scene.worldzero$moveTargetX, scene.worldzero$moveTargetY, scene.worldzero$moveTargetZ);
            echo.setDeltaMovement(0.0D, 0.0D, 0.0D);
        }
    }

    private static int[] worldzero$createGlitchTicks(ServerLevel level) {
        int[] glitchTicks = new int[WORLDZERO_ECHO_GLITCH_COUNT];
        int count = 0;
        while (count < glitchTicks.length) {
            int candidate = level.random.nextInt(WORLDZERO_FINAL_TICK + 1);
            if (worldzero$isReservedVoidTick(candidate)) {
                continue;
            }

            boolean duplicate = false;
            for (int index = 0; index < count; index++) {
                if (glitchTicks[index] == candidate) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                glitchTicks[count] = candidate;
                count++;
            }
        }

        Arrays.sort(glitchTicks);
        return glitchTicks;
    }

    private static boolean worldzero$isReservedVoidTick(int tick) {
        return tick == WORLDZERO_ECHO_TAKE_OBSIDIAN_TICK
                || tick == WORLDZERO_FINAL_TICK
                || worldzero$isDialogueTick(tick)
                || worldzero$isBuildTick(tick);
    }

    private static boolean worldzero$isDialogueTick(int tick) {
        return switch (tick) {
            case 40, 100, 180, 260, 480, 560, 860, 940, 1300, 1380,
                    1850, 1930, 2050, 2200, 2450, 3000, 3080, 3200,
                    3280, 3500, 3700, 4100 -> true;
            default -> false;
        };
    }

    private static boolean worldzero$isBuildTick(int tick) {
        for (int buildTick : WORLDZERO_BUILD_TICKS) {
            if (tick == buildTick) {
                return true;
            }
        }
        return false;
    }

    private static boolean worldzero$isGlitchTick(VoidSceneState scene, int elapsedTicks) {
        for (int glitchTick : scene.worldzero$glitchTicks) {
            if (elapsedTicks == glitchTick) {
                return true;
            }
        }
        return false;
    }

    private static void worldzero$applyEchoGlitch(ServerLevel level, VoidSceneState scene) {
        Entity entity = level.getEntity(scene.worldzero$echoId);
        if (!(entity instanceof WorldZeroEchoEntity echo)) {
            return;
        }

        float yawOffset = level.random.nextBoolean() ? 92.0F : -92.0F;
        float pitch = level.random.nextBoolean() ? -35.0F : 35.0F;
        float yaw = echo.getYRot() + yawOffset;
        echo.setYRot(yaw);
        echo.setYHeadRot(yaw);
        echo.setYBodyRot(yaw);
        echo.setXRot(pitch);
    }

    private static void worldzero$tickAbsoluteFinals(
            ServerLevel level,
            MinecraftServer server,
            Map<UUID, AbsoluteFinalState> absoluteFinals,
            long gameTime
    ) {
        Iterator<Map.Entry<UUID, AbsoluteFinalState>> iterator = absoluteFinals.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, AbsoluteFinalState> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            AbsoluteFinalState scene = entry.getValue();
            if (player == null || !player.isAlive() || player.serverLevel().dimension() != WORLDZERO_VOID_LEVEL) {
                worldzero$discardAbsoluteBlackEcho(level, scene);
                iterator.remove();
                continue;
            }

            worldzero$applyAbsoluteFreeze(player, scene);
            int elapsedTicks = (int) (gameTime - scene.worldzero$startTick);
            if (!scene.worldzero$blackEchoSpawned && elapsedTicks >= WORLDZERO_ABSOLUTE_EMPTY_TICKS) {
                worldzero$spawnAbsoluteBlackEcho(level, player, scene);
            }
            if (scene.worldzero$blackEchoSpawned) {
                worldzero$tickAbsoluteBlackEcho(level, scene, elapsedTicks);
            }

            if (elapsedTicks >= WORLDZERO_ABSOLUTE_TOTAL_TICKS) {
                worldzero$discardAbsoluteBlackEcho(level, scene);
                iterator.remove();
                WorldZeroHorrorFinale.worldzero$finishAbsoluteFinal(player);
            }
        }
        if (absoluteFinals.isEmpty()) {
            WORLDZERO_ABSOLUTE_FINALS.remove(server);
        }
    }

    private static void worldzero$spawnAbsoluteBlackEcho(
            ServerLevel level,
            ServerPlayer player,
            AbsoluteFinalState scene
    ) {
        WorldZeroEchoEntity blakEcho = WorldZeroEntities.WORLDZERO_BLAK_ECHO.get().create(level);
        if (blakEcho == null) {
            return;
        }

        blakEcho.moveTo(
                WORLDZERO_SECOND_OBSIDIAN_POS.getX() + 0.5D,
                WORLDZERO_SECOND_OBSIDIAN_POS.getY() + 1.0D,
                WORLDZERO_SECOND_OBSIDIAN_POS.getZ() + 0.5D,
                90.0F,
                0.0F
        );
        blakEcho.worldzero$configureVoidScene();
        if (!level.addFreshEntity(blakEcho)) {
            return;
        }

        scene.worldzero$blackEchoSpawned = true;
        scene.worldzero$blackEchoId = blakEcho.getUUID();
        WorldZeroNetwork.sendFreezeStart(player, WORLDZERO_ABSOLUTE_ATTACK_TICKS, blakEcho.getId());
        WorldZeroNetwork.sendFinale(
                player,
                WorldZeroFinalePacket.WORLDZERO_ACTION_ABSOLUTE_ATTACK,
                WORLDZERO_ABSOLUTE_ATTACK_TICKS,
                level.random.nextInt()
        );
    }

    private static void worldzero$tickAbsoluteBlackEcho(ServerLevel level, AbsoluteFinalState scene, int elapsedTicks) {
        if (scene.worldzero$blackEchoId == null) {
            return;
        }

        Entity entity = level.getEntity(scene.worldzero$blackEchoId);
        if (!(entity instanceof WorldZeroEchoEntity blackEcho)) {
            return;
        }

        double progress = Mth.clamp(
                (elapsedTicks - WORLDZERO_ABSOLUTE_EMPTY_TICKS) / (double) WORLDZERO_ABSOLUTE_RUN_TICKS,
                0.0D,
                1.0D
        );
        double startX = WORLDZERO_SECOND_OBSIDIAN_POS.getX() + 0.5D;
        double startY = WORLDZERO_SECOND_OBSIDIAN_POS.getY() + 1.0D;
        double startZ = WORLDZERO_SECOND_OBSIDIAN_POS.getZ() + 0.5D;
        double targetX = scene.worldzero$lockedX + 0.75D;
        double nextX = Mth.lerp(progress, startX, targetX);
        blackEcho.setPos(nextX, startY, startZ);
        blackEcho.setDeltaMovement((targetX - startX) / WORLDZERO_ABSOLUTE_RUN_TICKS, 0.0D, 0.0D);
        blackEcho.setSprinting(true);
        blackEcho.setYRot(90.0F);
        blackEcho.setYHeadRot(90.0F);
        blackEcho.setYBodyRot(90.0F);
        blackEcho.setXRot(0.0F);
        blackEcho.hasImpulse = true;
    }

    private static void worldzero$applyAbsoluteFreeze(ServerPlayer player, AbsoluteFinalState scene) {
        player.teleportTo(
                player.serverLevel(),
                scene.worldzero$lockedX,
                scene.worldzero$lockedY,
                scene.worldzero$lockedZ,
                scene.worldzero$lockedYaw,
                scene.worldzero$lockedPitch
        );
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.setSprinting(false);
        player.fallDistance = 0.0F;
    }

    private static WorldZeroEchoEntity worldzero$spawnVoidEcho(ServerLevel level, ServerPlayer player) {
        WorldZeroEchoEntity echo = WorldZeroEntities.WORLDZERO_ECHO.get().create(level);
        if (echo == null) {
            return null;
        }

        echo.moveTo(
                WORLDZERO_SECOND_OBSIDIAN_POS.getX() + 0.5D,
                WORLDZERO_SECOND_OBSIDIAN_POS.getY() + 1.0D,
                WORLDZERO_SECOND_OBSIDIAN_POS.getZ() + 0.5D,
                90.0F,
                0.0F
        );
        echo.setCustomName(Component.literal(player.getGameProfile().getName()));
        echo.setCustomNameVisible(false);
        echo.worldzero$configureVoidScene();
        echo.worldzero$setEchoDespawnDistance(5.0D);
        if (!level.addFreshEntity(echo)) {
            return null;
        }

        return echo;
    }

    private static void worldzero$applyFullFreeze(ServerPlayer player, VoidSceneState scene) {
        player.teleportTo(
                player.serverLevel(),
                scene.worldzero$lockedX,
                scene.worldzero$lockedY,
                scene.worldzero$lockedZ,
                scene.worldzero$lockedYaw,
                scene.worldzero$lockedPitch
        );
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.setSprinting(false);
        player.fallDistance = 0.0F;
    }

    private static void worldzero$sendChatLine(ServerPlayer player, String speaker, String messageKey) {
        WorldZeroNetwork.sendLocalizedChatLine(player, speaker, messageKey);
    }

    private static void worldzero$clearVoidScene(MinecraftServer server, UUID playerId) {
        Map<UUID, VoidSceneState> scenes = WORLDZERO_SCENES.get(server);
        if (scenes == null) {
            return;
        }

        VoidSceneState scene = scenes.remove(playerId);
        if (scene != null) {
            worldzero$discardSceneEcho(server, scene);
        }
        if (scenes.isEmpty()) {
            WORLDZERO_SCENES.remove(server);
        }
    }

    private static void worldzero$clearAbsoluteFinal(MinecraftServer server, UUID playerId) {
        Map<UUID, AbsoluteFinalState> scenes = WORLDZERO_ABSOLUTE_FINALS.get(server);
        if (scenes == null) {
            return;
        }

        AbsoluteFinalState scene = scenes.remove(playerId);
        if (scene != null) {
            for (ServerLevel level : server.getAllLevels()) {
                worldzero$discardAbsoluteBlackEcho(level, scene);
            }
        }
        if (scenes.isEmpty()) {
            WORLDZERO_ABSOLUTE_FINALS.remove(server);
        }
    }

    private static void worldzero$discardSceneEcho(MinecraftServer server, VoidSceneState scene) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(scene.worldzero$echoId);
            if (entity != null) {
                entity.discard();
                return;
            }
        }
    }

    private static void worldzero$prepareVoidLevel(ServerLevel level) {
        level.getChunkAt(WORLDZERO_FIRST_OBSIDIAN_POS);
        level.getChunkAt(WORLDZERO_SECOND_OBSIDIAN_POS);
        for (int x = -WORLDZERO_CLEAR_RADIUS_X; x <= WORLDZERO_CLEAR_RADIUS_X; x++) {
            for (int y = WORLDZERO_CLEAR_MIN_Y; y <= WORLDZERO_CLEAR_MAX_Y; y++) {
                for (int z = -WORLDZERO_CLEAR_RADIUS_Z; z <= WORLDZERO_CLEAR_RADIUS_Z; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!pos.equals(WORLDZERO_FIRST_OBSIDIAN_POS) && !pos.equals(WORLDZERO_SECOND_OBSIDIAN_POS)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }

        level.setBlock(
                WORLDZERO_FIRST_OBSIDIAN_POS,
                Blocks.OBSIDIAN.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
        );
        level.setBlock(
                WORLDZERO_SECOND_OBSIDIAN_POS,
                Blocks.OBSIDIAN.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
        );
    }

    private static void worldzero$ensureBuiltPath(ServerLevel level) {
        for (BlockPos pos : WORLDZERO_BUILD_POSITIONS) {
            level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private static void worldzero$discardAbsoluteBlackEcho(ServerLevel level, AbsoluteFinalState scene) {
        if (scene.worldzero$blackEchoId == null) {
            return;
        }

        Entity entity = level.getEntity(scene.worldzero$blackEchoId);
        if (entity != null) {
            entity.discard();
        }
    }

    private static final class ReturnPoint {
        private final ResourceKey<Level> worldzero$dimension;
        private final double worldzero$x;
        private final double worldzero$y;
        private final double worldzero$z;
        private final float worldzero$yaw;
        private final float worldzero$pitch;

        private ReturnPoint(
                ResourceKey<Level> dimension,
                double x,
                double y,
                double z,
                float yaw,
                float pitch
        ) {
            this.worldzero$dimension = dimension;
            this.worldzero$x = x;
            this.worldzero$y = y;
            this.worldzero$z = z;
            this.worldzero$yaw = yaw;
            this.worldzero$pitch = pitch;
        }
    }

    private static final class VoidSceneState {
        private final long worldzero$startTick;
        private final double worldzero$lockedX;
        private final double worldzero$lockedY;
        private final double worldzero$lockedZ;
        private final float worldzero$lockedYaw;
        private final float worldzero$lockedPitch;
        private final UUID worldzero$echoId;
        private final int[] worldzero$glitchTicks;
        private final boolean worldzero$finaleScene;
        private boolean worldzero$echoHoldingObsidian;
        private int worldzero$builtBlocks;
        private int worldzero$moveTicksRemaining;
        private double worldzero$moveStartX;
        private double worldzero$moveStartY;
        private double worldzero$moveStartZ;
        private double worldzero$moveTargetX;
        private double worldzero$moveTargetY;
        private double worldzero$moveTargetZ;
        private boolean worldzero$finished;
        private boolean worldzero$finalMenuSent;

        private VoidSceneState(
                long startTick,
                double lockedX,
                double lockedY,
                double lockedZ,
                float lockedYaw,
                float lockedPitch,
                UUID echoId,
                int[] glitchTicks,
                boolean finaleScene
        ) {
            this.worldzero$startTick = startTick;
            this.worldzero$lockedX = lockedX;
            this.worldzero$lockedY = lockedY;
            this.worldzero$lockedZ = lockedZ;
            this.worldzero$lockedYaw = lockedYaw;
            this.worldzero$lockedPitch = lockedPitch;
            this.worldzero$echoId = echoId;
            this.worldzero$glitchTicks = glitchTicks;
            this.worldzero$finaleScene = finaleScene;
        }
    }

    private static final class AbsoluteFinalState {
        private final long worldzero$startTick;
        private final double worldzero$lockedX;
        private final double worldzero$lockedY;
        private final double worldzero$lockedZ;
        private final float worldzero$lockedYaw;
        private final float worldzero$lockedPitch;
        private boolean worldzero$blackEchoSpawned;
        private UUID worldzero$blackEchoId;

        private AbsoluteFinalState(
                long startTick,
                double lockedX,
                double lockedY,
                double lockedZ,
                float lockedYaw,
                float lockedPitch
        ) {
            this.worldzero$startTick = startTick;
            this.worldzero$lockedX = lockedX;
            this.worldzero$lockedY = lockedY;
            this.worldzero$lockedZ = lockedZ;
            this.worldzero$lockedYaw = lockedYaw;
            this.worldzero$lockedPitch = lockedPitch;
        }
    }
}
