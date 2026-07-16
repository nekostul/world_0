package ru.nekostul.worldzero.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.worldzero.WorldZeroMod;
import ru.nekostul.worldzero.entity.WorldZeroEchoEntity;
import ru.nekostul.worldzero.entity.WorldZeroEntities;
import ru.nekostul.worldzero.network.WorldZeroNetwork;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = WorldZeroMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldZeroVoidPortalDimension {
    public static final ResourceKey<Level> WORLDZERO_VOIDPORTAL_LEVEL = ResourceKey.create(
            Registries.DIMENSION,
            new ResourceLocation(WorldZeroMod.MOD_ID, "voidportal")
    );

    private static final int WORLDZERO_FLOOR_Y = 64;
    private static final int WORLDZERO_PLAYER_Y = WORLDZERO_FLOOR_Y + 1;
    private static final int WORLDZERO_CHUNK_RADIUS = 3;
    private static final int WORLDZERO_REDSTONE_TORCH_MIN_PER_CHUNK = 2;
    private static final int WORLDZERO_REDSTONE_TORCH_MAX_PER_CHUNK = 3;
    private static final int WORLDZERO_REDSTONE_TORCH_MAX_ATTEMPTS = 8;
    private static final int WORLDZERO_STATUE_CELL_SIZE = 14;
    private static final int WORLDZERO_STATUE_CELL_RADIUS = 4;
    private static final String[] WORLDZERO_SIGN_TEXTS = {
            "ты оставил это здесь",
            "do not count them",
            "wrong way again",
            "it remembers your name",
            "the floor repeats",
            "stop running",
            "это не первый цикл",
            "someone wore your skin",
            "look behind the statues",
            "the return is borrowed",
            "nothing spawned here",
            "wake up elsewhere",
            "your items are waiting",
            "the villagers know",
            "do not trust the portal",
            "one of them moved",
            "you are the noise",
            "keep going, it gets closer",
            "home is not loaded",
            "выход переместился"
    };
    private static final AABB WORLDZERO_ENTITY_SCAN_AABB = new AABB(
            -30_000_000.0D,
            -2_048.0D,
            -30_000_000.0D,
            30_000_000.0D,
            4_096.0D,
            30_000_000.0D
    );
    private static final Map<MinecraftServer, Map<UUID, ReturnPoint>> WORLDZERO_RETURN_POINTS = new WeakHashMap<>();
    private static final Map<MinecraftServer, Map<UUID, PlayerInventorySnapshot>> WORLDZERO_INVENTORY_SNAPSHOTS = new WeakHashMap<>();
    private static final Map<MinecraftServer, Set<Long>> WORLDZERO_PREPARED_CHUNKS = new WeakHashMap<>();
    private static final Map<MinecraftServer, Set<Long>> WORLDZERO_PREPARED_STATUE_CELLS = new WeakHashMap<>();
    private static final Map<MinecraftServer, Set<Long>> WORLDZERO_PREPARED_SIGN_CELLS = new WeakHashMap<>();

    private WorldZeroVoidPortalDimension() {
    }

    @SubscribeEvent
    public static void worldzero$onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.level instanceof ServerLevel level)
                || level.dimension() != WORLDZERO_VOIDPORTAL_LEVEL) {
            return;
        }

        if (level.getGameTime() % 10L != 0L) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator()) {
                worldzero$prepareAround(level, player.getX(), player.getZ());
                worldzero$spawnStatuesAround(level, player.getX(), player.getZ());
                worldzero$ensureSignsAround(level, player.getX(), player.getZ());
            }
        }
    }

    @SubscribeEvent
    public static void worldzero$onServerStopped(ServerStoppedEvent event) {
        WORLDZERO_RETURN_POINTS.remove(event.getServer());
        WORLDZERO_INVENTORY_SNAPSHOTS.remove(event.getServer());
        WORLDZERO_PREPARED_CHUNKS.remove(event.getServer());
        WORLDZERO_PREPARED_STATUE_CELLS.remove(event.getServer());
        WORLDZERO_PREPARED_SIGN_CELLS.remove(event.getServer());
    }

    @SubscribeEvent
    public static void worldzero$onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == WORLDZERO_VOIDPORTAL_LEVEL) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == WORLDZERO_VOIDPORTAL_LEVEL) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onBlockMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == WORLDZERO_VOIDPORTAL_LEVEL) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onAttackEntity(AttackEntityEvent event) {
        if (event.getTarget() instanceof Villager villager && worldzero$isVoidPortalEntity(villager)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldzero$onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Villager villager && worldzero$isVoidPortalEntity(villager)) {
            event.setCanceled(true);
        }
    }

    public static boolean worldzero$teleportPlayerToVoidPortal(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        ServerLevel voidPortalLevel = server.getLevel(WORLDZERO_VOIDPORTAL_LEVEL);
        if (voidPortalLevel == null) {
            return false;
        }

        Map<UUID, ReturnPoint> returnPoints = WORLDZERO_RETURN_POINTS.computeIfAbsent(server, ignored -> new HashMap<>());
        if (returnPoints.containsKey(player.getUUID())) {
            if (player.serverLevel().dimension() == WORLDZERO_VOIDPORTAL_LEVEL) {
                return false;
            }

            // Self-heal a stale void_portal session so the next portal entry is not blocked forever.
            returnPoints.remove(player.getUUID());
            if (returnPoints.isEmpty()) {
                WORLDZERO_RETURN_POINTS.remove(server);
            }
            worldzero$removeInventorySnapshot(server, player.getUUID());
        }
        WORLDZERO_INVENTORY_SNAPSHOTS.computeIfAbsent(server, ignored -> new HashMap<>()).put(
                player.getUUID(),
                PlayerInventorySnapshot.worldzero$fromPlayer(player)
        );

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

        WORLDZERO_PREPARED_STATUE_CELLS.remove(server);
        WORLDZERO_PREPARED_SIGN_CELLS.remove(server);
        worldzero$clearVoidPortalStatues(voidPortalLevel);
        worldzero$prepareAround(voidPortalLevel, 0.0D, 0.0D);
        worldzero$spawnStatuesAround(voidPortalLevel, 0.0D, 0.0D);
        worldzero$ensureSignsAround(voidPortalLevel, 0.0D, 0.0D);
        if (player.isSleeping()) {
            player.stopSleepInBed(false, true);
        }
        worldzero$clearPlayerInventory(player);
        player.teleportTo(voidPortalLevel, 0.5D, WORLDZERO_PLAYER_Y, 0.5D, player.getYRot(), player.getXRot());
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        return true;
    }

    public static boolean worldzero$returnPlayerFromVoidPortal(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        Map<UUID, ReturnPoint> returnPoints = WORLDZERO_RETURN_POINTS.computeIfAbsent(server, ignored -> new HashMap<>());
        ReturnPoint returnPoint = returnPoints.remove(player.getUUID());
        PlayerInventorySnapshot inventorySnapshot = worldzero$removeInventorySnapshot(server, player.getUUID());
        if (returnPoint == null && player.serverLevel().dimension() != WORLDZERO_VOIDPORTAL_LEVEL) {
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
        if (inventorySnapshot != null) {
            worldzero$restorePlayerInventory(player, inventorySnapshot);
        }
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        WorldZeroNetwork.sendKeyboardBlock(player, 0);
        return true;
    }

    private static void worldzero$prepareAround(ServerLevel level, double x, double z) {
        MinecraftServer server = level.getServer();
        Set<Long> preparedChunks = WORLDZERO_PREPARED_CHUNKS.computeIfAbsent(server, ignored -> new HashSet<>());
        int centerChunkX = (int) Math.floor(x) >> 4;
        int centerChunkZ = (int) Math.floor(z) >> 4;
        for (int chunkX = centerChunkX - WORLDZERO_CHUNK_RADIUS; chunkX <= centerChunkX + WORLDZERO_CHUNK_RADIUS; chunkX++) {
            for (int chunkZ = centerChunkZ - WORLDZERO_CHUNK_RADIUS; chunkZ <= centerChunkZ + WORLDZERO_CHUNK_RADIUS; chunkZ++) {
                long key = ChunkPos.asLong(chunkX, chunkZ);
                if (preparedChunks.add(key)) {
                    worldzero$prepareChunkFloor(level, chunkX, chunkZ);
                }
            }
        }
    }

    private static void worldzero$clearVoidPortalStatues(ServerLevel level) {
        for (WorldZeroEchoEntity echo : level.getEntitiesOfClass(
                WorldZeroEchoEntity.class,
                WORLDZERO_ENTITY_SCAN_AABB
        )) {
            echo.discard();
        }
        for (Villager villager : level.getEntitiesOfClass(
                Villager.class,
                WORLDZERO_ENTITY_SCAN_AABB
        )) {
            villager.discard();
        }
    }

    private static void worldzero$prepareChunkFloor(ServerLevel level, int chunkX, int chunkZ) {
        level.getChunk(chunkX, chunkZ);
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        for (int x = minX; x < minX + 16; x++) {
            for (int z = minZ; z < minZ + 16; z++) {
                level.setBlock(
                        new BlockPos(x, WORLDZERO_FLOOR_Y, z),
                        Blocks.BEDROCK.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
                );
            }
        }
        worldzero$placeChunkTorches(level, chunkX, chunkZ);
    }

    private static void worldzero$placeChunkTorches(ServerLevel level, int chunkX, int chunkZ) {
        Random random = new Random(level.getSeed() ^ (chunkX * 73428767L) ^ (chunkZ * 912931L) ^ 0x5A17B3L);
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int targetCount = WORLDZERO_REDSTONE_TORCH_MIN_PER_CHUNK
                + random.nextInt(WORLDZERO_REDSTONE_TORCH_MAX_PER_CHUNK - WORLDZERO_REDSTONE_TORCH_MIN_PER_CHUNK + 1);
        int placed = 0;

        for (int attempt = 0; attempt < WORLDZERO_REDSTONE_TORCH_MAX_ATTEMPTS && placed < targetCount; attempt++) {
            int x = minX + 1 + random.nextInt(14);
            int z = minZ + 1 + random.nextInt(14);
            if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
                continue;
            }

            BlockPos torchPos = new BlockPos(x, WORLDZERO_PLAYER_Y, z);
            if (!level.getBlockState(torchPos).isAir()) {
                continue;
            }

            level.setBlock(
                    torchPos,
                    Blocks.REDSTONE_TORCH.defaultBlockState(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            );
            placed++;
        }
    }

    private static void worldzero$spawnStatuesAround(ServerLevel level, double x, double z) {
        MinecraftServer server = level.getServer();
        Set<Long> preparedCells = WORLDZERO_PREPARED_STATUE_CELLS.computeIfAbsent(server, ignored -> new HashSet<>());
        int centerCellX = worldzero$floorDiv((int) Math.floor(x), WORLDZERO_STATUE_CELL_SIZE);
        int centerCellZ = worldzero$floorDiv((int) Math.floor(z), WORLDZERO_STATUE_CELL_SIZE);
        for (int cellX = centerCellX - WORLDZERO_STATUE_CELL_RADIUS; cellX <= centerCellX + WORLDZERO_STATUE_CELL_RADIUS; cellX++) {
            for (int cellZ = centerCellZ - WORLDZERO_STATUE_CELL_RADIUS; cellZ <= centerCellZ + WORLDZERO_STATUE_CELL_RADIUS; cellZ++) {
                if (Math.abs(cellX) <= 1 && Math.abs(cellZ) <= 1) {
                    continue;
                }

                long key = ChunkPos.asLong(cellX, cellZ);
                if (preparedCells.add(key)) {
                    worldzero$spawnStatuesForCell(level, cellX, cellZ);
                }
            }
        }
    }

    private static void worldzero$spawnStatuesForCell(ServerLevel level, int cellX, int cellZ) {
        Random random = new Random(level.getSeed() ^ (cellX * 341873128712L) ^ (cellZ * 132897987541L));
        int count = 1 + random.nextInt(3);
        for (int index = 0; index < count; index++) {
            double x = cellX * WORLDZERO_STATUE_CELL_SIZE + 1.5D + random.nextDouble() * (WORLDZERO_STATUE_CELL_SIZE - 3.0D);
            double z = cellZ * WORLDZERO_STATUE_CELL_SIZE + 1.5D + random.nextDouble() * (WORLDZERO_STATUE_CELL_SIZE - 3.0D);
            worldzero$spawnStatue(level, random, x, z);
        }
    }

    private static void worldzero$spawnStatue(ServerLevel level, Random random, double x, double z) {
        double y = WORLDZERO_PLAYER_Y;
        int selector = random.nextInt(3);
        Entity entity;
        if (selector == 0) {
            entity = WorldZeroEntities.WORLDZERO_ECHO.get().create(level);
        } else if (selector == 1) {
            entity = WorldZeroEntities.WORLDZERO_BLACK_ECHO.get().create(level);
        } else {
            entity = EntityType.VILLAGER.create(level);
        }

        if (entity == null) {
            return;
        }

        float yaw = random.nextFloat() * 360.0F;
        entity.moveTo(x, y, z, yaw, 0.0F);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setDeltaMovement(0.0D, 0.0D, 0.0D);
        if (entity instanceof WorldZeroEchoEntity echo) {
            echo.worldzero$configureStatue();
        }
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        }
        if (entity instanceof Villager villager) {
            villager.setNoAi(true);
        }
        level.addFreshEntity(entity);
    }

    private static void worldzero$ensureSignsAround(ServerLevel level, double x, double z) {
        MinecraftServer server = level.getServer();
        Set<Long> preparedCells = WORLDZERO_PREPARED_SIGN_CELLS.computeIfAbsent(server, ignored -> new HashSet<>());
        int centerCellX = worldzero$floorDiv((int) Math.floor(x), WORLDZERO_STATUE_CELL_SIZE);
        int centerCellZ = worldzero$floorDiv((int) Math.floor(z), WORLDZERO_STATUE_CELL_SIZE);
        for (int cellX = centerCellX - WORLDZERO_STATUE_CELL_RADIUS; cellX <= centerCellX + WORLDZERO_STATUE_CELL_RADIUS; cellX++) {
            for (int cellZ = centerCellZ - WORLDZERO_STATUE_CELL_RADIUS; cellZ <= centerCellZ + WORLDZERO_STATUE_CELL_RADIUS; cellZ++) {
                if (Math.abs(cellX) <= 1 && Math.abs(cellZ) <= 1) {
                    continue;
                }

                long key = ChunkPos.asLong(cellX, cellZ);
                boolean placeMissingSigns = preparedCells.add(key);
                worldzero$ensureSignsForCell(level, cellX, cellZ, placeMissingSigns);
            }
        }
    }

    private static void worldzero$ensureSignsForCell(ServerLevel level, int cellX, int cellZ, boolean placeMissingSigns) {
        Random random = new Random(level.getSeed() ^ (cellX * 42317861L) ^ (cellZ * 91827133L) ^ 0x51A7E5L);
        int count = 1 + random.nextInt(2);
        for (int index = 0; index < count; index++) {
            int x = cellX * WORLDZERO_STATUE_CELL_SIZE + 2 + random.nextInt(WORLDZERO_STATUE_CELL_SIZE - 4);
            int z = cellZ * WORLDZERO_STATUE_CELL_SIZE + 2 + random.nextInt(WORLDZERO_STATUE_CELL_SIZE - 4);
            int rotation = random.nextInt(16);
            String text = WORLDZERO_SIGN_TEXTS[random.nextInt(WORLDZERO_SIGN_TEXTS.length)];
            BlockPos signPos = new BlockPos(x, WORLDZERO_PLAYER_Y, z);
            if (placeMissingSigns && level.getBlockState(signPos).isAir()) {
                BlockState signState = Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation);
                level.setBlock(signPos, signState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            worldzero$restoreSignText(level, signPos, text);
        }
    }

    private static void worldzero$restoreSignText(ServerLevel level, BlockPos signPos, String text) {
        BlockEntity blockEntity = level.getBlockEntity(signPos);
        if (!(blockEntity instanceof SignBlockEntity signBlockEntity)) {
            return;
        }

        SignText signText = new SignText(
                worldzero$signComponents(text),
                worldzero$signComponents(text),
                DyeColor.BLACK,
                false
        );
        signBlockEntity.setText(signText, true);
        signBlockEntity.setText(signText, false);
        signBlockEntity.setChanged();
        BlockState state = level.getBlockState(signPos);
        level.sendBlockUpdated(signPos, state, state, Block.UPDATE_CLIENTS);
    }

    private static Component[] worldzero$signComponents(String text) {
        Component[] components = new Component[]{
                Component.empty(),
                Component.empty(),
                Component.empty(),
                Component.empty()
        };
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int lineIndex = 0;
        for (String word : words) {
            if (line.length() > 0 && line.length() + 1 + word.length() > 14 && lineIndex < components.length - 1) {
                components[lineIndex++] = Component.literal(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0 && lineIndex < components.length) {
            components[lineIndex] = Component.literal(line.toString());
        }
        return components;
    }

    private static boolean worldzero$isVoidPortalEntity(Entity entity) {
        return entity.level() instanceof ServerLevel level && level.dimension() == WORLDZERO_VOIDPORTAL_LEVEL;
    }

    private static PlayerInventorySnapshot worldzero$removeInventorySnapshot(MinecraftServer server, UUID playerId) {
        Map<UUID, PlayerInventorySnapshot> inventorySnapshots = WORLDZERO_INVENTORY_SNAPSHOTS.get(server);
        if (inventorySnapshots == null) {
            return null;
        }

        PlayerInventorySnapshot snapshot = inventorySnapshots.remove(playerId);
        if (inventorySnapshots.isEmpty()) {
            WORLDZERO_INVENTORY_SNAPSHOTS.remove(server);
        }
        return snapshot;
    }

    private static void worldzero$clearPlayerInventory(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        player.containerMenu.setCarried(ItemStack.EMPTY);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }

    private static void worldzero$restorePlayerInventory(ServerPlayer player, PlayerInventorySnapshot snapshot) {
        player.getInventory().clearContent();
        snapshot.worldzero$apply(player);
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        player.containerMenu.setCarried(ItemStack.EMPTY);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }

    private static int worldzero$floorDiv(int value, int divisor) {
        return Math.floorDiv(value, divisor);
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

    private static final class PlayerInventorySnapshot {
        private final CompoundTag worldzero$tag;

        private PlayerInventorySnapshot(CompoundTag tag) {
            this.worldzero$tag = tag;
        }

        private static PlayerInventorySnapshot worldzero$fromPlayer(ServerPlayer player) {
            CompoundTag tag = new CompoundTag();
            tag.put("Inventory", player.getInventory().save(new ListTag()));
            tag.putInt("SelectedSlot", player.getInventory().selected);
            return new PlayerInventorySnapshot(tag);
        }

        private void worldzero$apply(ServerPlayer player) {
            ListTag inventoryTag = this.worldzero$tag.getList("Inventory", Tag.TAG_COMPOUND);
            player.getInventory().load(inventoryTag);
            player.getInventory().selected = Math.max(0, Math.min(8, this.worldzero$tag.getInt("SelectedSlot")));
        }
    }
}
