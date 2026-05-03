package ru.nekostul.worldzero.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;
import ru.nekostul.worldzero.config.WorldZeroConfig;
import ru.nekostul.worldzero.dimension.WorldZeroHouseBadDimension;
import ru.nekostul.worldzero.dimension.WorldZeroHouseDimension;
import ru.nekostul.worldzero.dimension.WorldZeroKoridorDimension;
import ru.nekostul.worldzero.dimension.WorldZeroVoidDimension;
import ru.nekostul.worldzero.dimension.WorldZeroVoidPortalDimension;
import ru.nekostul.worldzero.entity.WorldZeroEchoEntity;
import ru.nekostul.worldzero.entity.WorldZeroEntities;
import ru.nekostul.worldzero.entity.WorldZeroHouseEchoEntity;
import ru.nekostul.worldzero.event.chat.WorldZeroDoubleChatEvent;
import ru.nekostul.worldzero.event.echo.WorldZeroEchoPhaseOneSpawner;
import ru.nekostul.worldzero.event.fall.WorldZeroFallEvent;
import ru.nekostul.worldzero.event.footsteps.WorldZeroFootstepsEvent;
import ru.nekostul.worldzero.event.freeze.WorldZeroFreezeEvent;
import ru.nekostul.worldzero.event.horror.WorldZeroHorrorEventSystem;
import ru.nekostul.worldzero.event.horror.WorldZeroHorrorFinale;
import ru.nekostul.worldzero.event.horror.WorldZeroMinorAnomalies;
import ru.nekostul.worldzero.event.house.WorldZeroHouseDetector;
import ru.nekostul.worldzero.event.house.WorldZeroHouseEvent;
import ru.nekostul.worldzero.event.major.WorldZeroMajorEventSystem;
import ru.nekostul.worldzero.event.major.WorldZeroMajorEventType;
import ru.nekostul.worldzero.event.memory.WorldZeroWorldMemoryEvent;
import ru.nekostul.worldzero.event.mining.WorldZeroLastBlockEvent;
import ru.nekostul.worldzero.event.mining.WorldZeroMinePresenceEvent;
import ru.nekostul.worldzero.event.paralysis.WorldZeroParalysisEvent;
import ru.nekostul.worldzero.event.skywatch.WorldZeroSkyWatchEvent;
import ru.nekostul.worldzero.event.structure.WorldZeroOverworldStructureEvent;
import ru.nekostul.worldzero.event.voidcall.WorldZeroVoidCallEvent;
import ru.nekostul.worldzero.network.WorldZeroFinalePacket;
import ru.nekostul.worldzero.network.WorldZeroNetwork;

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
                        .then(Commands.literal("stop_all_events")
                                .executes(context -> worldzero$stopAllEvents(context.getSource())))
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
                        .then(Commands.literal("trigger_peripheral_echo")
                                .executes(context -> worldzero$triggerMinorAnomaly(
                                        context.getSource(),
                                        WorldZeroMinorAnomalies.MinorAnomalyType.PERIPHERAL_ECHO
                                )))
                        .then(Commands.literal("trigger_phantom_steps")
                                .executes(context -> worldzero$triggerMinorAnomaly(
                                        context.getSource(),
                                        WorldZeroMinorAnomalies.MinorAnomalyType.PHANTOM_STEPS
                                )))
                        .then(Commands.literal("trigger_whisper")
                                .executes(context -> worldzero$triggerMinorAnomaly(
                                        context.getSource(),
                                        WorldZeroMinorAnomalies.MinorAnomalyType.WHISPER
                                )))
                        .then(Commands.literal("trigger_object_presence")
                                .executes(context -> worldzero$triggerMinorAnomaly(
                                        context.getSource(),
                                        WorldZeroMinorAnomalies.MinorAnomalyType.OBJECT_PRESENCE
                                )))
                        .then(Commands.literal("trigger_light_anomaly")
                                .executes(context -> worldzero$triggerMinorAnomaly(
                                        context.getSource(),
                                        WorldZeroMinorAnomalies.MinorAnomalyType.LIGHT_ANOMALY
                                )))
                        .then(Commands.literal("trigger_shadow_delay")
                                .executes(context -> worldzero$triggerMinorAnomaly(
                                        context.getSource(),
                                        WorldZeroMinorAnomalies.MinorAnomalyType.SHADOW_DELAY
                                )))
                        .then(Commands.literal("trigger_wrong_wind")
                                .executes(context -> worldzero$triggerMinorAnomaly(
                                        context.getSource(),
                                        WorldZeroMinorAnomalies.MinorAnomalyType.WRONG_WIND
                                )))
                        .then(Commands.literal("trigger_entity_blackout")
                                .executes(context -> worldzero$triggerMinorAnomaly(
                                        context.getSource(),
                                        WorldZeroMinorAnomalies.MinorAnomalyType.ENTITY_BLACKOUT
                                )))
                        .then(Commands.literal("trigger_block_blink")
                                .executes(context -> worldzero$triggerMinorAnomaly(
                                        context.getSource(),
                                        WorldZeroMinorAnomalies.MinorAnomalyType.BLOCK_BLINK
                                )))
                        .then(Commands.literal("trigger_watching")
                                .executes(context -> worldzero$triggerMajorEvent(
                                        context.getSource(),
                                        WorldZeroMajorEventType.WATCHING
                                )))
                        .then(Commands.literal("trigger_stalker")
                                .executes(context -> worldzero$triggerMajorEvent(
                                        context.getSource(),
                                        WorldZeroMajorEventType.STALKER
                                )))
                        .then(Commands.literal("trigger_void_call")
                                .executes(context -> worldzero$triggerMajorEvent(
                                        context.getSource(),
                                        WorldZeroMajorEventType.VOID_CALL
                                )))
                        .then(Commands.literal("trigger_growth")
                                .executes(context -> worldzero$triggerMajorEvent(
                                        context.getSource(),
                                        WorldZeroMajorEventType.GROWTH
                                )))
                        .then(Commands.literal("trigger_swarm")
                                .executes(context -> worldzero$triggerMajorEvent(
                                        context.getSource(),
                                        WorldZeroMajorEventType.SWARM
                                )))
                        .then(Commands.literal("trigger_time_loop")
                                .executes(context -> worldzero$triggerMajorEvent(
                                        context.getSource(),
                                        WorldZeroMajorEventType.TIME_LOOP
                                )))
                        .then(Commands.literal("trigger_glitch_rain")
                                .executes(context -> worldzero$triggerMajorEvent(
                                        context.getSource(),
                                        WorldZeroMajorEventType.GLITCH_RAIN
                                )))
                        .then(Commands.literal("trigger_finale")
                                .executes(context -> worldzero$triggerFinale(context.getSource())))
                        .then(Commands.literal("trigger_final_menu")
                                .executes(context -> worldzero$triggerFinalMenu(context.getSource())))
                        .then(Commands.literal("trigger_final_screamer")
                                .executes(context -> worldzero$triggerFinalScreamer(context.getSource())))
                        .then(Commands.literal("trigger_memory")
                                .executes(context -> worldzero$triggerMemory(context.getSource())))
                        .then(Commands.literal("trigger_last_block")
                                .executes(context -> worldzero$triggerLastBlock(context.getSource())))
                        .then(Commands.literal("trigger_mine_presence")
                                .executes(context -> worldzero$triggerMinePresence(context.getSource())))
                        .then(Commands.literal("trigger_koridor_run")
                                .executes(context -> worldzero$triggerKoridorRun(context.getSource())))
                        .then(Commands.literal("trigger_koridor_chase")
                                .executes(context -> worldzero$triggerKoridorChase(context.getSource())))
                        .then(Commands.literal("tp_koridor")
                                .executes(context -> worldzero$teleportToKoridor(context.getSource())))
                        .then(Commands.literal("tp_house")
                                .executes(context -> worldzero$teleportToHouse(context.getSource())))
                        .then(Commands.literal("trigger_house_farm_dream")
                                .executes(context -> worldzero$triggerHouseFarmDream(context.getSource())))
                        .then(Commands.literal("trigger_house_farm_demo")
                                .executes(context -> worldzero$triggerHouseFarmDemo(context.getSource())))
                        .then(Commands.literal("tp_house_bad")
                                .executes(context -> worldzero$teleportToHouseBad(context.getSource())))
                        .then(Commands.literal("tp_void")
                                .executes(context -> worldzero$teleportToVoid(context.getSource())))
                        .then(Commands.literal("tp_voidportal")
                                .executes(context -> worldzero$teleportToVoidPortal(context.getSource())))
                        .then(Commands.literal("return_from_voidportal")
                                .executes(context -> worldzero$returnFromVoidPortal(context.getSource())))
                        .then(Commands.literal("return_to_overworld")
                                .executes(context -> worldzero$returnToOverworld(context.getSource())))
                        .then(Commands.literal("clean_disc")
                                .executes(context -> worldzero$cleanDisc(context.getSource())))
                        .then(Commands.literal("trigger_house")
                                .executes(context -> worldzero$triggerHouse(context.getSource())))
                        .then(Commands.literal("trigger_house_real")
                                .executes(context -> worldzero$triggerHouseReal(context.getSource())))
                        .then(Commands.literal("trigger_double_chat")
                                .executes(context -> worldzero$triggerDoubleChat(context.getSource())))
                        .then(Commands.literal("trigger_sky_watch")
                                .executes(context -> worldzero$triggerSkyWatch(context.getSource())))
                        .then(Commands.literal("reset_double_chat")
                                .executes(context -> worldzero$resetDoubleChat(context.getSource())))
                        .then(Commands.literal("spawn")
                                .then(Commands.literal("portal")
                                        .executes(context -> worldzero$spawnStructure(context.getSource(), "portal")))
                                .then(Commands.literal("portal2")
                                        .executes(context -> worldzero$spawnStructure(context.getSource(), "portal2")))
                                .then(Commands.literal("dom")
                                        .executes(context -> worldzero$spawnStructure(context.getSource(), "dom"))))
                        .then(Commands.literal("update")
                                .then(Commands.literal("dom")
                                        .executes(context -> worldzero$updateDomStructure(context.getSource()))))
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

    private static int worldzero$stopAllEvents(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        int stopped = 0;
        if (WorldZeroHorrorEventSystem.worldzero$stopAllEvents(server)) {
            stopped++;
        }
        if (WorldZeroMajorEventSystem.worldzero$stopAllEvents(server)) {
            stopped++;
        }
        if (WorldZeroHorrorFinale.worldzero$stopNow(server)) {
            stopped++;
        }
        if (WorldZeroFreezeEvent.worldzero$stopFreezeNow(server)) {
            stopped++;
        }
        if (WorldZeroFallEvent.worldzero$stopFallNow(server)) {
            stopped++;
        }
        if (WorldZeroParalysisEvent.worldzero$stopParalysisNow(server)) {
            stopped++;
        }
        if (WorldZeroFootstepsEvent.worldzero$stopFootstepsNow(server)) {
            stopped++;
        }
        if (WorldZeroHouseEvent.worldzero$stopHouseNow(server)) {
            stopped++;
        }
        if (WorldZeroWorldMemoryEvent.worldzero$stopMemoryNow(server)) {
            stopped++;
        }
        if (WorldZeroLastBlockEvent.worldzero$stopLastBlockNow(server)) {
            stopped++;
        }
        if (WorldZeroMinePresenceEvent.worldzero$stopNow(server)) {
            stopped++;
        }
        if (WorldZeroSkyWatchEvent.worldzero$stopNow(server)) {
            stopped++;
        }
        if (WorldZeroFootstepsEvent.worldzero$resetBlankDiscPlayback(server)) {
            stopped++;
        }

        int removedEntities = worldzero$removeAllEventEntities(server);
        int finalStopped = stopped;
        source.sendSuccess(() -> Component.literal(
                "[WORLD_0][DEV] stop_all_events completed: stopped_groups="
                        + finalStopped
                        + ", removed_event_entities="
                        + removedEntities
        ), false);
        return stopped + removedEntities > 0 ? 1 : 0;
    }

    private static int worldzero$removeAll(CommandSourceStack source) {
        int removed = worldzero$removeAllEventEntities(source.getServer());

        int finalRemoved = removed;
        source.sendSuccess(() -> Component.literal(
                "[WORLD_0][DEV] removed total echo entities: " + finalRemoved
        ), false);
        return removed;
    }

    private static int worldzero$removeAllEventEntities(MinecraftServer server) {
        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (WorldZeroEchoEntity entity : level.getEntitiesOfClass(
                    WorldZeroEchoEntity.class,
                    WORLDZERO_ENTITY_SCAN_AABB
            )) {
                entity.discard();
                removed++;
            }
            for (WorldZeroHouseEchoEntity entity : level.getEntitiesOfClass(
                    WorldZeroHouseEchoEntity.class,
                    WORLDZERO_ENTITY_SCAN_AABB
            )) {
                entity.discard();
                removed++;
            }
        }
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

    private static int worldzero$triggerMinorAnomaly(
            CommandSourceStack source,
            WorldZeroMinorAnomalies.MinorAnomalyType anomalyType
    ) {
        if (source.getPlayer() == null) {
            return 0;
        }

        boolean triggered = WorldZeroHorrorEventSystem.worldzero$triggerMinorAnomalyNow(
                source.getPlayer(),
                anomalyType
        );
        source.sendSuccess(() -> Component.literal(
                triggered
                        ? "[WORLD_0][DEV] minor anomaly triggered: " + anomalyType.worldzero$debugName()
                        : "[WORLD_0][DEV] minor anomaly failed: " + anomalyType.worldzero$debugName()
                        + " (active event, invalid conditions, or no valid target)"
        ), false);
        return triggered ? 1 : 0;
    }

    private static int worldzero$triggerMajorEvent(
            CommandSourceStack source,
            WorldZeroMajorEventType eventType
    ) {
        if (source.getPlayer() == null) {
            return 0;
        }

        boolean triggered = WorldZeroMajorEventSystem.worldzero$triggerMajorEventNow(
                source.getPlayer(),
                eventType
        );
        source.sendSuccess(() -> Component.literal(
                triggered
                        ? "[WORLD_0][DEV] major event triggered: " + eventType.worldzero$debugName()
                        : "[WORLD_0][DEV] major event failed: " + eventType.worldzero$debugName()
                        + " (active event, invalid conditions, or no valid target)"
        ), false);
        return triggered ? 1 : 0;
    }

    private static int worldzero$triggerFinale(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }

        boolean triggered = WorldZeroHorrorFinale.worldzero$triggerNow(source.getPlayer());
        source.sendSuccess(() -> Component.literal(
                triggered
                        ? "[WORLD_0][DEV] finale event triggered"
                        : "[WORLD_0][DEV] finale event trigger failed (invalid player or missing overworld)"
        ), false);
        return triggered ? 1 : 0;
    }

    private static int worldzero$triggerFinalMenu(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }

        WorldZeroHorrorFinale.worldzero$prepareFinalMenuEntry(player);
        WorldZeroNetwork.sendFinale(player, WorldZeroFinalePacket.WORLDZERO_ACTION_FINAL_BLACK_MENU, 1, 0);
        source.sendSuccess(() -> Component.literal("[WORLD_0][DEV] final menu opened"), false);
        return 1;
    }

    private static int worldzero$triggerFinalScreamer(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }

        boolean triggered = WorldZeroVoidDimension.worldzero$startAbsoluteFinal(player);
        source.sendSuccess(() -> Component.literal(
                triggered
                        ? "[WORLD_0][DEV] final screamer triggered"
                        : "[WORLD_0][DEV] final screamer failed (invalid player or missing void dimension)"
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

    private static int worldzero$teleportToHouseBad(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }

        boolean teleported = WorldZeroHouseBadDimension.worldzero$teleportPlayerToHouseBad(source.getPlayer());
        source.sendSuccess(() -> Component.literal(
                teleported
                        ? "[WORLD_0][DEV] teleported to house_bad dimension"
                        : "[WORLD_0][DEV] house_bad teleport failed (dimension, structure, or active return state is unavailable)"
        ), false);
        return teleported ? 1 : 0;
    }

    private static int worldzero$teleportToVoid(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }

        boolean teleported = WorldZeroVoidDimension.worldzero$teleportPlayerToVoid(source.getPlayer());
        source.sendSuccess(() -> Component.literal(
                teleported
                        ? "[WORLD_0][DEV] teleported to void dimension"
                        : "[WORLD_0][DEV] void teleport failed (dimension or active return state is unavailable)"
        ), false);
        return teleported ? 1 : 0;
    }

    private static int worldzero$teleportToVoidPortal(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }

        boolean teleported = WorldZeroVoidPortalDimension.worldzero$teleportPlayerToVoidPortal(source.getPlayer());
        source.sendSuccess(() -> Component.literal(
                teleported
                        ? "[WORLD_0][DEV] teleported to voidportal dimension"
                        : "[WORLD_0][DEV] voidportal teleport failed (dimension or active return state is unavailable)"
        ), false);
        return teleported ? 1 : 0;
    }

    private static int worldzero$returnFromVoidPortal(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }

        boolean teleported = WorldZeroVoidCallEvent.worldzero$returnFromVoidPortalNow(player);
        source.sendSuccess(() -> Component.literal(
                teleported
                        ? "[WORLD_0][DEV] returned from voidportal dimension"
                        : "[WORLD_0][DEV] return_from_voidportal failed (no saved return point or target dimension is unavailable)"
        ), false);
        return teleported ? 1 : 0;
    }

    private static int worldzero$returnToOverworld(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }

        boolean teleported;
        String dimensionName;
        if (player.serverLevel().dimension() == WorldZeroKoridorDimension.WORLDZERO_KORIDOR_LEVEL) {
            teleported = WorldZeroKoridorDimension.worldzero$returnPlayerFromKoridor(player);
            dimensionName = "koridor";
        } else if (player.serverLevel().dimension() == WorldZeroHouseDimension.WORLDZERO_HOUSE_LEVEL) {
            teleported = WorldZeroHouseDimension.worldzero$returnPlayerFromHouse(player);
            dimensionName = "house";
        } else if (player.serverLevel().dimension() == WorldZeroHouseBadDimension.WORLDZERO_HOUSE_BAD_LEVEL) {
            teleported = WorldZeroHouseBadDimension.worldzero$returnPlayerFromHouseBad(player);
            dimensionName = "house_bad";
        } else if (player.serverLevel().dimension() == WorldZeroVoidDimension.WORLDZERO_VOID_LEVEL) {
            teleported = WorldZeroVoidDimension.worldzero$returnPlayerFromVoid(player);
            dimensionName = "void";
        } else if (player.serverLevel().dimension() == WorldZeroVoidPortalDimension.WORLDZERO_VOIDPORTAL_LEVEL) {
            teleported = WorldZeroVoidCallEvent.worldzero$returnFromVoidPortalNow(player);
            dimensionName = "voidportal";
        } else {
            source.sendSuccess(() -> Component.literal(
                    "[WORLD_0][DEV] return_to_overworld failed (player is not in a worldzero custom dimension)"
            ), false);
            return 0;
        }

        String finalDimensionName = dimensionName;
        source.sendSuccess(() -> Component.literal(
                teleported
                        ? "[WORLD_0][DEV] returned from " + finalDimensionName + " dimension"
                        : "[WORLD_0][DEV] return_to_overworld failed from " + finalDimensionName + " (no saved return point or target dimension is unavailable)"
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

    private static int worldzero$spawnStructure(CommandSourceStack source, String structureName) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }

        boolean spawned = WorldZeroOverworldStructureEvent.worldzero$spawnDebugStructure(player, structureName);
        source.sendSuccess(() -> Component.literal(
                spawned
                        ? "[WORLD_0][DEV] spawned structure: " + structureName
                        : "[WORLD_0][DEV] spawn failed for structure: " + structureName
                        + " (be in overworld and keep a valid surface in front of you)"
        ), false);
        return spawned ? 1 : 0;
    }

    private static int worldzero$updateDomStructure(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }

        boolean updated = WorldZeroOverworldStructureEvent.worldzero$updateNearestDomDebug(player);
        source.sendSuccess(() -> Component.literal(
                updated
                        ? "[WORLD_0][DEV] updated nearest dom to dom2"
                        : "[WORLD_0][DEV] update failed for dom (no nearby dom found or no valid template)"
        ), false);
        return updated ? 1 : 0;
    }

    private static int worldzero$triggerDoubleChat(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }

        boolean triggered = WorldZeroDoubleChatEvent.worldzero$triggerNow(player);
        source.sendSuccess(() -> Component.literal(
                triggered
                        ? "[WORLD_0][DEV] double-chat scenario triggered"
                        : "[WORLD_0][DEV] double-chat scenario trigger failed (be alive in overworld)"
        ), false);
        return triggered ? 1 : 0;
    }

    private static int worldzero$triggerSkyWatch(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }

        String blocker = WorldZeroSkyWatchEvent.worldzero$getDebugTriggerBlocker(player);
        boolean triggered = WorldZeroSkyWatchEvent.worldzero$triggerNow(player);
        source.sendSuccess(() -> Component.literal(
                triggered
                        ? "[WORLD_0][DEV] sky-watch event triggered"
                        : "[WORLD_0][DEV] sky-watch event trigger failed"
                        + (blocker == null ? " (notice message could not be sent)" : " (" + blocker + ")")
        ), false);
        return triggered ? 1 : 0;
    }

    private static int worldzero$triggerMinePresence(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }

        String blocker = WorldZeroMinePresenceEvent.worldzero$getDebugTriggerBlocker(player);
        boolean triggered = WorldZeroMinePresenceEvent.worldzero$triggerNow(player);
        source.sendSuccess(() -> Component.literal(
                triggered
                        ? "[WORLD_0][DEV] mine-presence event triggered"
                        : "[WORLD_0][DEV] mine-presence event trigger failed"
                        + (blocker == null ? "" : " (" + blocker + ")")
        ), false);
        return triggered ? 1 : 0;
    }

    private static int worldzero$resetDoubleChat(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }

        boolean reset = WorldZeroDoubleChatEvent.worldzero$resetNow(player);
        source.sendSuccess(() -> Component.literal(
                reset
                        ? "[WORLD_0][DEV] double-chat scenario reset"
                        : "[WORLD_0][DEV] double-chat scenario had nothing to reset"
        ), false);
        return reset ? 1 : 0;
    }
}
