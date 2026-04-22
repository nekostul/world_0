package ru.nekostul.worldzero;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroDevEntityCommands {
    private static final AABB WORLDZERO_ENTITY_SCAN_AABB = new AABB(
            -30_000_000.0D,
            -2_048.0D,
            -30_000_000.0D,
            30_000_000.0D,
            4_096.0D,
            30_000_000.0D
    );

    private WorldZeroDevEntityCommands() {
    }

    @SubscribeEvent
    public static void worldzero$onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("wzdev")
                        .requires(WorldZeroDevCheats::isAllowedForSource)
                        .then(Commands.literal("remove_echo")
                                .executes(context -> worldzero$removeByType(
                                        context.getSource(),
                                        WorldZeroEntities.WORLDZERO_ECHO.get(),
                                        "worldzero_echo"
                                )))
                        .then(Commands.literal("remove_black_echo")
                                .executes(context -> worldzero$removeByType(
                                        context.getSource(),
                                        WorldZeroEntities.WORLDZERO_BLACK_ECHO.get(),
                                        "worldzero_black_echo"
                                )))
                        .then(Commands.literal("remove_all_echoes")
                                .executes(context -> worldzero$removeAll(context.getSource())))
                        .then(Commands.literal("trigger_echo")
                                .executes(context -> worldzero$triggerEcho(context.getSource())))
                        .then(Commands.literal("trigger_echo_rule_break")
                                .executes(context -> worldzero$triggerEchoRuleBreak(context.getSource())))
                        .then(Commands.literal("trigger_freeze")
                                .executes(context -> worldzero$triggerFreeze(context.getSource())))
                        .then(Commands.literal("trigger_fall")
                                .executes(context -> worldzero$triggerFall(context.getSource())))
                        .then(Commands.literal("trigger_paralysis")
                                .executes(context -> worldzero$triggerParalysis(context.getSource())))
                        .then(Commands.literal("trigger_footsteps")
                                .executes(context -> worldzero$triggerFootsteps(context.getSource())))
                        .then(Commands.literal("trigger_memory")
                                .executes(context -> worldzero$triggerMemory(context.getSource())))
                        .then(Commands.literal("trigger_last_block")
                                .executes(context -> worldzero$triggerLastBlock(context.getSource())))
                        .then(Commands.literal("trigger_koridor_run")
                                .executes(context -> worldzero$triggerKoridorRun(context.getSource())))
                        .then(Commands.literal("trigger_koridor_chase")
                                .executes(context -> worldzero$triggerKoridorChase(context.getSource())))
                        .then(Commands.literal("tp_koridor")
                                .executes(context -> worldzero$teleportToKoridor(context.getSource())))
                        .then(Commands.literal("return_from_koridor")
                                .executes(context -> worldzero$returnFromKoridor(context.getSource())))
                        .then(Commands.literal("tp_house")
                                .executes(context -> worldzero$teleportToHouse(context.getSource())))
                        .then(Commands.literal("trigger_house_farm_dream")
                                .executes(context -> worldzero$triggerHouseFarmDream(context.getSource())))
                        .then(Commands.literal("trigger_house_farm_demo")
                                .executes(context -> worldzero$triggerHouseFarmDemo(context.getSource())))
                        .then(Commands.literal("return_from_house")
                                .executes(context -> worldzero$returnFromHouse(context.getSource())))
                        .then(Commands.literal("clean_disc")
                                .executes(context -> worldzero$cleanDisc(context.getSource())))
                        .then(Commands.literal("trigger_house")
                                .executes(context -> worldzero$triggerHouse(context.getSource())))
                        .then(Commands.literal("trigger_house_real")
                                .executes(context -> worldzero$triggerHouseReal(context.getSource())))
                        .then(Commands.literal("detect_house")
                                .executes(context -> worldzero$detectHouse(context.getSource())))
        );
    }

    private static int worldzero$removeByType(
            CommandSourceStack source,
            EntityType<? extends WorldZeroEchoEntity> type,
            String debugName
    ) {
        int removed = 0;
        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (WorldZeroEchoEntity entity : level.getEntitiesOfClass(
                    WorldZeroEchoEntity.class,
                    WORLDZERO_ENTITY_SCAN_AABB,
                    candidate -> candidate.getType() == type
            )) {
                entity.discard();
                removed++;
            }
        }

        int finalRemoved = removed;
        source.sendSuccess(() -> Component.literal(
                "[WORLD_0][DEV] removed " + finalRemoved + " entities of type " + debugName
        ), false);
        return removed;
    }

    private static int worldzero$removeAll(CommandSourceStack source) {
        int removed = 0;
        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (WorldZeroEchoEntity entity : level.getEntitiesOfClass(
                    WorldZeroEchoEntity.class,
                    WORLDZERO_ENTITY_SCAN_AABB
            )) {
                entity.discard();
                removed++;
            }
        }

        int finalRemoved = removed;
        source.sendSuccess(() -> Component.literal(
                "[WORLD_0][DEV] removed total echo entities: " + finalRemoved
        ), false);
        return removed;
    }

    private static int worldzero$triggerEcho(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }

        boolean spawned = WorldZeroEchoPhaseOneSpawner.worldzero$triggerPhaseOneSpawn(source.getPlayer());
        source.sendSuccess(() -> Component.literal(
                spawned
                        ? "[WORLD_0][DEV] phase-1 echo spawn triggered"
                        : "[WORLD_0][DEV] phase-1 echo spawn failed (active echo or no valid spawn point)"
        ), false);
        return spawned ? 1 : 0;
    }

    private static int worldzero$triggerEchoRuleBreak(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }

        boolean spawned = WorldZeroEchoPhaseOneSpawner.worldzero$triggerRuleBreakSpawn(source.getPlayer());
        source.sendSuccess(() -> Component.literal(
                spawned
                        ? "[WORLD_0][DEV] rule-break echo spawn triggered"
                        : "[WORLD_0][DEV] rule-break echo spawn failed (active echo or no valid spawn point)"
        ), false);
        return spawned ? 1 : 0;
    }

    private static int worldzero$triggerFreeze(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }

        boolean triggered = WorldZeroFreezeEvent.worldzero$triggerFreezeNow(source.getPlayer());
        source.sendSuccess(() -> Component.literal(
                triggered
                        ? "[WORLD_0][DEV] freeze event triggered"
                        : "[WORLD_0][DEV] freeze event trigger failed (already active or invalid player)"
        ), false);
        return triggered ? 1 : 0;
    }

    private static int worldzero$triggerFall(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }

        boolean triggered = WorldZeroFallEvent.worldzero$triggerFallNow(source.getPlayer());
        source.sendSuccess(() -> Component.literal(
                triggered
                        ? "[WORLD_0][DEV] fall event force-triggered"
                        : "[WORLD_0][DEV] fall event trigger failed (already active, invalid player, or no valid black_echo spawn)"
        ), false);
        return triggered ? 1 : 0;
    }

    private static int worldzero$triggerFootsteps(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }

        boolean triggered = WorldZeroFootstepsEvent.worldzero$triggerFootstepsNow(source.getPlayer());
        source.sendSuccess(() -> Component.literal(
                triggered
                        ? "[WORLD_0][DEV] footsteps event force-triggered"
                        : "[WORLD_0][DEV] footsteps event trigger failed (already active or invalid player)"
        ), false);
        return triggered ? 1 : 0;
    }

    private static int worldzero$triggerParalysis(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }

        boolean triggered = WorldZeroParalysisEvent.worldzero$triggerParalysisNow(source.getPlayer());
        source.sendSuccess(() -> Component.literal(
                triggered
                        ? "[WORLD_0][DEV] paralysis event force-triggered"
                        : "[WORLD_0][DEV] paralysis event trigger failed (already active, invalid player, or no bed found)"
        ), false);
        return triggered ? 1 : 0;
    }

    private static int worldzero$triggerMemory(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }

        boolean triggered = WorldZeroWorldMemoryEvent.worldzero$triggerMemoryNow(source.getPlayer());
        source.sendSuccess(() -> Component.literal(
                triggered
                        ? "[WORLD_0][DEV] world-memory anomaly force-triggered"
                        : "[WORLD_0][DEV] world-memory trigger failed (active event, no nearby house, or no valid anomaly target)"
        ), false);
        return triggered ? 1 : 0;
    }

    private static int worldzero$triggerLastBlock(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }

        boolean triggered = WorldZeroLastBlockEvent.worldzero$triggerLastBlockNow(source.getPlayer());
        source.sendSuccess(() -> Component.literal(
                triggered
                        ? "[WORLD_0][DEV] last-block appearance force-triggered"
                        : "[WORLD_0][DEV] last-block trigger failed (look at a solid block with open space behind it, or another echo/event is active)"
        ), false);
        return triggered ? 1 : 0;
    }

    private static int worldzero$cleanDisc(CommandSourceStack source) {
        boolean changed = WorldZeroFootstepsEvent.worldzero$resetBlankDiscPlayback(source.getServer());
        source.sendSuccess(() -> Component.literal(
                changed
                        ? "[WORLD_0][DEV] blank disc playback status cleared"
                        : "[WORLD_0][DEV] blank disc playback status was already clean"
        ), false);
        return changed ? 1 : 0;
    }

    private static int worldzero$triggerKoridorRun(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }

        boolean triggered = WorldZeroKoridorDimension.worldzero$triggerEchoRunNow(source.getPlayer());
        source.sendSuccess(() -> Component.literal(
                triggered
                        ? "[WORLD_0][DEV] koridor echo run force-triggered"
                        : "[WORLD_0][DEV] koridor echo run failed (be in koridor, look forward, and keep distant closed doors in view)"
        ), false);
        return triggered ? 1 : 0;
    }

    private static int worldzero$triggerKoridorChase(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }

        boolean triggered = WorldZeroKoridorDimension.worldzero$triggerDreamChaseNow(source.getPlayer());
        source.sendSuccess(() -> Component.literal(
                triggered
                        ? "[WORLD_0][DEV] koridor black_echo chase force-triggered"
                        : "[WORLD_0][DEV] koridor black_echo chase failed (be in koridor and keep a distant corridor segment loaded)"
        ), false);
        return triggered ? 1 : 0;
    }

    private static int worldzero$teleportToKoridor(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }

        boolean teleported = WorldZeroKoridorDimension.worldzero$teleportPlayerToKoridor(source.getPlayer());
        source.sendSuccess(() -> Component.literal(
                teleported
                        ? "[WORLD_0][DEV] teleported to koridor dimension"
                        : "[WORLD_0][DEV] koridor teleport failed (dimension or structure is unavailable)"
        ), false);
        return teleported ? 1 : 0;
    }

    private static int worldzero$returnFromKoridor(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }

        boolean teleported = WorldZeroKoridorDimension.worldzero$returnPlayerFromKoridor(source.getPlayer());
        source.sendSuccess(() -> Component.literal(
                teleported
                        ? "[WORLD_0][DEV] returned from koridor dimension"
                        : "[WORLD_0][DEV] koridor return failed (no saved return point or target dimension is unavailable)"
        ), false);
        return teleported ? 1 : 0;
    }

    private static int worldzero$teleportToHouse(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }

        boolean teleported = WorldZeroHouseDimension.worldzero$teleportPlayerToHouse(source.getPlayer());
        source.sendSuccess(() -> Component.literal(
                teleported
                        ? "[WORLD_0][DEV] teleported to house dimension"
                        : "[WORLD_0][DEV] house teleport failed (dimension, structure, or active return state is unavailable)"
        ), false);
        return teleported ? 1 : 0;
    }

    private static int worldzero$returnFromHouse(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }

        boolean teleported = WorldZeroHouseDimension.worldzero$returnPlayerFromHouse(source.getPlayer());
        source.sendSuccess(() -> Component.literal(
                teleported
                        ? "[WORLD_0][DEV] returned from house dimension"
                        : "[WORLD_0][DEV] house return failed (no saved return point or target dimension is unavailable)"
        ), false);
        return teleported ? 1 : 0;
    }

    private static int worldzero$triggerHouseFarmDream(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }

        boolean triggered = WorldZeroHouseDimension.worldzero$triggerRestorationDreamNow(source.getPlayer());
        source.sendSuccess(() -> Component.literal(
                triggered
                        ? "[WORLD_0][DEV] house farm restoration dream triggered"
                        : "[WORLD_0][DEV] house farm restoration dream failed (needs recorded broken farmland from a normal house visit)"
        ), false);
        return triggered ? 1 : 0;
    }

    private static int worldzero$triggerHouseFarmDemo(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }

        boolean triggered = WorldZeroHouseDimension.worldzero$triggerRestorationDreamDemoNow(source.getPlayer());
        source.sendSuccess(() -> Component.literal(
                triggered
                        ? "[WORLD_0][DEV] house farm restoration demo triggered"
                        : "[WORLD_0][DEV] house farm restoration demo failed (house dimension/template or return state is unavailable)"
        ), false);
        return triggered ? 1 : 0;
    }

    private static int worldzero$triggerHouse(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }

        boolean triggered = WorldZeroHouseEvent.worldzero$triggerHouseNowDebug(source.getPlayer());
        source.sendSuccess(() -> Component.literal(
                triggered
                        ? "[WORLD_0][DEV] house event force-triggered (debug)"
                        : "[WORLD_0][DEV] house force-trigger failed (already active)"
        ), false);
        return triggered ? 1 : 0;
    }

    private static int worldzero$triggerHouseReal(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }

        WorldZeroHouseDetector.DetectedHouse detectedHouse = WorldZeroHouseDetector.worldzero$findNearbyHouseForDebug(source.getPlayer());
        if (detectedHouse == null) {
            source.sendSuccess(() -> Component.literal(
                    "[WORLD_0][DEV] house event real-trigger failed (house geometry/features not detected nearby)"
            ), false);
            return 0;
        }

        boolean inTriggerRange = detectedHouse.worldzero$isWithinTriggerDistanceRange(
                source.getPlayer().getX(),
                source.getPlayer().getZ()
        );
        if (!inTriggerRange) {
            double distanceToBounds = Math.sqrt(
                    detectedHouse.worldzero$horizontalDistanceToBoundsSqr(
                            source.getPlayer().getX(),
                            source.getPlayer().getZ()
                    )
            );
            source.sendSuccess(() -> Component.literal(
                    "[WORLD_0][DEV] house event real-trigger failed (player is out of trigger range: "
                            + String.format("%.2f", distanceToBounds)
                            + " blocks to house, need "
                            + String.format("%.1f", WorldZeroConfig.worldzero$houseTriggerDistanceMinBlocks())
                            + "-"
                            + String.format("%.1f", WorldZeroConfig.worldzero$houseTriggerDistanceMaxBlocks())
                            + ")"
            ), false);
            return 0;
        }

        boolean triggered = WorldZeroHouseEvent.worldzero$triggerHouseNow(source.getPlayer());
        source.sendSuccess(() -> Component.literal(
                triggered
                        ? "[WORLD_0][DEV] house event real-triggered"
                        : "[WORLD_0][DEV] house event real-trigger failed (already active)"
        ), false);
        return triggered ? 1 : 0;
    }

    private static int worldzero$detectHouse(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }

        WorldZeroHouseDetector.DetectedHouse detectedHouse = WorldZeroHouseDetector.worldzero$findNearbyHouseForDebug(source.getPlayer());
        if (detectedHouse == null) {
            source.sendSuccess(() -> Component.literal(
                    "[WORLD_0][DEV] house detector: no valid house geometry/features found nearby"
            ), false);
            return 0;
        }

        int width = detectedHouse.interiorMax().getX() - detectedHouse.interiorMin().getX() + 1;
        int length = detectedHouse.interiorMax().getZ() - detectedHouse.interiorMin().getZ() + 1;
        int height = detectedHouse.interiorMax().getY() - detectedHouse.interiorMin().getY() + 1;
        int structureWidth = width + 2;
        int structureLength = length + 2;
        int structureHeight = height + 2;
        double distanceToBounds = Math.sqrt(
                detectedHouse.worldzero$horizontalDistanceToBoundsSqr(
                        source.getPlayer().getX(),
                        source.getPlayer().getZ()
                )
        );
        boolean inTriggerRange = detectedHouse.worldzero$isWithinTriggerDistanceRange(
                source.getPlayer().getX(),
                source.getPlayer().getZ()
        );
        boolean houseVisible = detectedHouse.worldzero$isVisibleToPlayer(source.getPlayer());

        String rangeInfo = "distance_to_house=" + String.format("%.2f", distanceToBounds)
                + ", trigger_range_ok=" + inTriggerRange
                + ", house_visible=" + houseVisible
                + " (need "
                + String.format("%.1f", WorldZeroConfig.worldzero$houseTriggerDistanceMinBlocks())
                + "-"
                + String.format("%.1f", WorldZeroConfig.worldzero$houseTriggerDistanceMaxBlocks())
                + ")";
        source.sendSuccess(() -> Component.literal(
                "[WORLD_0][DEV] house detector: score=" + detectedHouse.score()
                        + ", features=" + detectedHouse.featureCategories()
                        + ", interior=" + width + "x" + length + "x" + height
                        + ", structure=" + structureWidth + "x" + structureLength + "x" + structureHeight
                        + ", center=" + detectedHouse.center().getX() + " "
                        + detectedHouse.center().getY() + " "
                        + detectedHouse.center().getZ()
                        + ", " + rangeInfo
        ), false);
        return 1;
    }
}
