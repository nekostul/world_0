package ru.nekostul.worldzero.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import ru.nekostul.worldzero.config.WorldZeroConfig;
import ru.nekostul.worldzero.event.house.WorldZeroHouseDetector;
import ru.nekostul.worldzero.network.WorldZeroNetwork;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WorldZeroHouseEchoEntity extends Monster {
    public static final String WORLDZERO_HOUSE_DISPLAY_TAG = "worldzero_house_display";
    private static final ResourceLocation WORLDZERO_WOODEN_DOOR_OPEN_SOUND_ID = new ResourceLocation(
            "minecraft",
            "block.wooden_door.open"
    );
    private static final ResourceLocation WORLDZERO_IRON_DOOR_OPEN_SOUND_ID = new ResourceLocation(
            "minecraft",
            "block.iron_door.open"
    );
    private static final ResourceLocation WORLDZERO_FENCE_GATE_OPEN_SOUND_ID = new ResourceLocation(
            "minecraft",
            "block.fence_gate.open"
    );

    private UUID worldzero$targetPlayerId;
    private int worldzero$interiorMinX;
    private int worldzero$interiorMinY;
    private int worldzero$interiorMinZ;
    private int worldzero$interiorMaxX;
    private int worldzero$interiorMaxY;
    private int worldzero$interiorMaxZ;
    private int worldzero$lifetimeTicksRemaining;
    private int worldzero$actionTicksRemaining;
    private int worldzero$stareTicksRemaining;
    private int worldzero$freezeBeforeVanishTicksRemaining;
    private boolean worldzero$rareFreezeTriggered;
    private boolean worldzero$ignoreDisappearDistance;
    @Nullable
    private UUID worldzero$ghostDisplayId;
    @Nullable
    private BlockPos worldzero$ghostBlockPos;
    private BlockState worldzero$ghostBlockState = Blocks.AIR.defaultBlockState();
    @Nullable
    private BlockPos worldzero$currentWorkPos;
    private boolean worldzero$farmRestoreActive;
    private final List<FarmTillTarget> worldzero$farmTillTargets = new ArrayList<>();
    private final List<FarmPlantTarget> worldzero$farmPlantTargets = new ArrayList<>();
    private int worldzero$farmPhase;
    private int worldzero$farmTillIndex;
    private int worldzero$farmPlantIndex;
    private int worldzero$farmActiveLineOrder;
    private int worldzero$farmActionCooldownTicks;
    private int worldzero$farmFinishTicks;
    private double worldzero$farmWalkDistance;
    private boolean worldzero$farmReadyToWake;
    private int worldzero$farmStartDelayTicks;
    private final List<BlockPos> worldzero$farmApproachTargets = new ArrayList<>();
    private final List<BlockPos> worldzero$farmDoorTargets = new ArrayList<>();
    private int worldzero$farmApproachIndex;
    private int worldzero$farmApproachPauseTicks;
    private int worldzero$farmDoorOpenStep;
    private boolean worldzero$farmDoorOpened;

    public WorldZeroHouseEchoEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.setNoAi(true);
        this.setInvulnerable(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    public void worldzero$configureFarmRestoration(
            UUID targetPlayerId,
            List<FarmTillTarget> tillTargets,
            List<FarmPlantTarget> plantTargets,
            List<BlockPos> approachTargets,
            List<BlockPos> doorTargets,
            int doorOpenStep,
            int startDelayTicks
    ) {
        this.worldzero$targetPlayerId = targetPlayerId;
        this.worldzero$farmRestoreActive = true;
        this.worldzero$ignoreDisappearDistance = true;
        this.worldzero$farmTillTargets.clear();
        this.worldzero$farmTillTargets.addAll(tillTargets);
        this.worldzero$farmPlantTargets.clear();
        this.worldzero$farmPlantTargets.addAll(plantTargets);
        this.worldzero$farmApproachTargets.clear();
        for (BlockPos approachTarget : approachTargets) {
            this.worldzero$farmApproachTargets.add(approachTarget.immutable());
        }
        this.worldzero$farmDoorTargets.clear();
        for (BlockPos doorTarget : doorTargets) {
            this.worldzero$farmDoorTargets.add(doorTarget.immutable());
        }
        this.worldzero$farmPhase = 2;
        this.worldzero$farmTillIndex = 0;
        this.worldzero$farmPlantIndex = 0;
        this.worldzero$farmActiveLineOrder = -1;
        this.worldzero$farmActionCooldownTicks = 0;
        this.worldzero$farmFinishTicks = 20;
        this.worldzero$farmWalkDistance = 0.0D;
        this.worldzero$farmReadyToWake = false;
        this.worldzero$farmStartDelayTicks = Math.max(0, startDelayTicks);
        this.worldzero$farmApproachIndex = 0;
        this.worldzero$farmApproachPauseTicks = 0;
        this.worldzero$farmDoorOpenStep = doorOpenStep;
        this.worldzero$farmDoorOpened = false;
        this.worldzero$currentWorkPos = null;
        this.worldzero$actionTicksRemaining = 0;
        this.worldzero$stareTicksRemaining = 0;
        this.worldzero$freezeBeforeVanishTicksRemaining = 0;
        this.worldzero$rareFreezeTriggered = false;
        if (this.level() instanceof ServerLevel serverLevel) {
            this.worldzero$removeGhostBlock(serverLevel, false);
        }
        this.setCustomName(null);
        this.setCustomNameVisible(false);
        this.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        this.worldzero$advanceToNextFarmLine();
        this.worldzero$refreshFarmHeldItem();
    }

    public void worldzero$configureScene(
            UUID targetPlayerId,
            WorldZeroHouseDetector.DetectedHouse detectedHouse,
            int lifetimeTicks,
            String displayName,
            boolean ignoreDisappearDistance
    ) {
        this.worldzero$targetPlayerId = targetPlayerId;
        this.worldzero$interiorMinX = detectedHouse.interiorMin().getX();
        this.worldzero$interiorMinY = detectedHouse.interiorMin().getY();
        this.worldzero$interiorMinZ = detectedHouse.interiorMin().getZ();
        this.worldzero$interiorMaxX = detectedHouse.interiorMax().getX();
        this.worldzero$interiorMaxY = detectedHouse.interiorMax().getY();
        this.worldzero$interiorMaxZ = detectedHouse.interiorMax().getZ();
        this.worldzero$lifetimeTicksRemaining = Math.max(20, lifetimeTicks);
        this.worldzero$ignoreDisappearDistance = ignoreDisappearDistance;
        this.setCustomName(Component.literal(displayName));
        this.setCustomNameVisible(true);
    }

    public boolean worldzero$isFarmRestorationReadyToWake() {
        return this.worldzero$farmRestoreActive && this.worldzero$farmReadyToWake;
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!this.level().isClientSide()) {
            WorldZeroEchoPresenceTracker.worldzero$trackHouseEcho(this);
        }
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
            this.worldzero$removeGhostBlock(serverLevel, false);
            WorldZeroEchoPresenceTracker.worldzero$untrackHouseEcho(this);
        }
        super.remove(reason);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }

        if (!(this.level() instanceof ServerLevel serverLevel) || this.worldzero$targetPlayerId == null) {
            this.discard();
            return;
        }

        net.minecraft.world.entity.player.Player rawTargetPlayer = serverLevel.getPlayerByUUID(this.worldzero$targetPlayerId);
        if (!(rawTargetPlayer instanceof ServerPlayer targetPlayer) || !targetPlayer.isAlive() || targetPlayer.isSpectator()) {
            this.discard();
            return;
        }

        if (!this.worldzero$ignoreDisappearDistance
                && this.distanceToSqr(targetPlayer) <= worldzero$square(WorldZeroConfig.worldzero$houseDisappearDistanceBlocks())) {
            this.discard();
            return;
        }

        if (this.worldzero$farmRestoreActive) {
            this.worldzero$tickFarmRestoration(serverLevel, targetPlayer);
            return;
        }

        this.worldzero$lifetimeTicksRemaining--;
        if (this.worldzero$lifetimeTicksRemaining <= 0) {
            this.discard();
            return;
        }

        if (this.worldzero$freezeBeforeVanishTicksRemaining > 0) {
            this.worldzero$freezeBeforeVanishTicksRemaining--;
            this.setSprinting(false);
            this.setDeltaMovement(Vec3.ZERO);
            this.worldzero$lookAtPlayer(targetPlayer);
            if (this.worldzero$freezeBeforeVanishTicksRemaining <= 0) {
                this.discard();
            }
            return;
        }

        if (this.worldzero$actionTicksRemaining > 0) {
            this.worldzero$actionTicksRemaining--;
        }

        this.worldzero$updateLookDirection(targetPlayer);
        this.worldzero$moveLikeSomethingIsWrong(serverLevel);

        if (this.worldzero$actionTicksRemaining <= 0) {
            this.worldzero$performBuildAction(serverLevel, targetPlayer);
            this.worldzero$actionTicksRemaining = Mth.nextInt(
                    serverLevel.random,
                    WorldZeroConfig.worldzero$houseActionMinTicks(),
                    WorldZeroConfig.worldzero$houseActionMaxTicks()
            );
        }
    }

    private void worldzero$updateLookDirection(ServerPlayer targetPlayer) {
        if (this.worldzero$stareTicksRemaining > 0) {
            this.worldzero$stareTicksRemaining--;
            this.worldzero$lookAtPlayer(targetPlayer);
            return;
        }

        if (this.worldzero$currentWorkPos != null && this.random.nextInt(4) != 0) {
            this.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                    Vec3.atCenterOf(this.worldzero$currentWorkPos));
            return;
        }

        Vec3 nowhere = new Vec3(
                this.getX() + (this.random.nextDouble() - 0.5D) * 12.0D,
                this.getEyeY() + (this.random.nextDouble() - 0.5D) * 4.0D,
                this.getZ() + (this.random.nextDouble() - 0.5D) * 12.0D
        );
        this.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, nowhere);
    }

    private void worldzero$moveLikeSomethingIsWrong(ServerLevel serverLevel) {
        if (this.worldzero$currentWorkPos == null || this.tickCount % 2 != 0) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }

        Vec3 target = Vec3.atBottomCenterOf(this.worldzero$currentWorkPos);
        Vec3 delta = target.subtract(this.position());
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontalDistance < 0.55D) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }

        double step = Math.min(0.32D, horizontalDistance);
        double jitterX = (this.random.nextDouble() - 0.5D) * 0.08D;
        double jitterZ = (this.random.nextDouble() - 0.5D) * 0.08D;
        double nextX = this.getX() + (delta.x / horizontalDistance) * step + jitterX;
        double nextZ = this.getZ() + (delta.z / horizontalDistance) * step + jitterZ;
        double nextY = this.worldzero$resolveStandingY(serverLevel, nextX, nextZ);

        this.setPos(nextX, nextY, nextZ);
        this.setSprinting(this.tickCount % 6 < 2);
        this.setDeltaMovement(Vec3.ZERO);
    }

    private void worldzero$performBuildAction(ServerLevel serverLevel, ServerPlayer targetPlayer) {
        if (!this.worldzero$rareFreezeTriggered
                && this.tickCount > 40
                && serverLevel.random.nextDouble() < WorldZeroConfig.worldzero$houseFreezeVanishChance()) {
            this.worldzero$rareFreezeTriggered = true;
            this.worldzero$freezeBeforeVanishTicksRemaining = Mth.nextInt(
                    serverLevel.random,
                    WorldZeroConfig.worldzero$houseFreezeVanishMinTicks(),
                    WorldZeroConfig.worldzero$houseFreezeVanishMaxTicks()
            );
            this.worldzero$removeGhostBlock(serverLevel, false);
            return;
        }

        if (this.worldzero$ghostDisplayId != null && this.worldzero$ghostBlockPos != null) {
            this.swing(InteractionHand.MAIN_HAND);
            this.worldzero$removeGhostBlock(serverLevel, true);
        } else {
            BlockPos targetPos = this.worldzero$pickWorkPosition(serverLevel);
            if (targetPos != null) {
                this.worldzero$currentWorkPos = targetPos;
                this.swing(InteractionHand.MAIN_HAND);
                this.worldzero$spawnGhostBlock(serverLevel, targetPos, this.worldzero$selectBuildState(serverLevel, targetPos));
            }
        }

        if (serverLevel.random.nextDouble() < WorldZeroConfig.worldzero$housePlayerStareChance()) {
            this.worldzero$stareTicksRemaining = Mth.nextInt(serverLevel.random, 6, 12);
            this.worldzero$lookAtPlayer(targetPlayer);
        }
    }

    @Nullable
    private BlockPos worldzero$pickWorkPosition(ServerLevel serverLevel) {
        int floorY = this.worldzero$interiorMinY - 1;

        for (int attempt = 0; attempt < 32; attempt++) {
            int mode = serverLevel.random.nextInt(5);
            int x;
            int y;
            int z;
            Direction supportDirection;

            if (mode == 0) {
                x = this.worldzero$interiorMinX - 1;
                y = Mth.nextInt(serverLevel.random, floorY + 1, this.worldzero$interiorMaxY);
                z = Mth.nextInt(serverLevel.random, this.worldzero$interiorMinZ, this.worldzero$interiorMaxZ);
                supportDirection = Direction.EAST;
            } else if (mode == 1) {
                x = this.worldzero$interiorMaxX + 1;
                y = Mth.nextInt(serverLevel.random, floorY + 1, this.worldzero$interiorMaxY);
                z = Mth.nextInt(serverLevel.random, this.worldzero$interiorMinZ, this.worldzero$interiorMaxZ);
                supportDirection = Direction.WEST;
            } else if (mode == 2) {
                x = Mth.nextInt(serverLevel.random, this.worldzero$interiorMinX, this.worldzero$interiorMaxX);
                y = Mth.nextInt(serverLevel.random, floorY + 1, this.worldzero$interiorMaxY);
                z = this.worldzero$interiorMinZ - 1;
                supportDirection = Direction.SOUTH;
            } else if (mode == 3) {
                x = Mth.nextInt(serverLevel.random, this.worldzero$interiorMinX, this.worldzero$interiorMaxX);
                y = Mth.nextInt(serverLevel.random, floorY + 1, this.worldzero$interiorMaxY);
                z = this.worldzero$interiorMaxZ + 1;
                supportDirection = Direction.NORTH;
            } else {
                x = Mth.nextInt(serverLevel.random, this.worldzero$interiorMinX, this.worldzero$interiorMaxX);
                y = this.worldzero$interiorMaxY + 1;
                z = Mth.nextInt(serverLevel.random, this.worldzero$interiorMinZ, this.worldzero$interiorMaxZ);
                supportDirection = Direction.DOWN;
            }

            BlockPos candidate = new BlockPos(x, y, z);
            if (!serverLevel.getBlockState(candidate).isAir()) {
                continue;
            }

            if (!serverLevel.getBlockState(candidate.relative(supportDirection)).getFluidState().isEmpty()) {
                continue;
            }

            if (serverLevel.getBlockState(candidate.relative(supportDirection)).isAir()) {
                continue;
            }

            return candidate;
        }

        int baseX = Mth.floor(this.getX());
        int baseY = Mth.floor(this.getY());
        int baseZ = Mth.floor(this.getZ());
        for (int attempt = 0; attempt < 12; attempt++) {
            int x = baseX + Mth.nextInt(serverLevel.random, -2, 2);
            int y = baseY + Mth.nextInt(serverLevel.random, -1, 2);
            int z = baseZ + Mth.nextInt(serverLevel.random, -2, 2);
            BlockPos fallback = new BlockPos(x, y, z);
            if (!serverLevel.getBlockState(fallback).isAir()) {
                continue;
            }
            if (!serverLevel.getBlockState(fallback.below()).getFluidState().isEmpty()) {
                continue;
            }
            if (serverLevel.getBlockState(fallback.below()).isAir()) {
                continue;
            }
            return fallback;
        }

        return null;
    }

    private double worldzero$resolveStandingY(ServerLevel serverLevel, double x, double z) {
        int groundY = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        double desiredY = (double) groundY;
        double maxStepUp = this.getY() + 0.8D;
        double minStepDown = this.getY() - 1.25D;
        if (desiredY > maxStepUp) {
            desiredY = maxStepUp;
        }
        if (desiredY < minStepDown) {
            desiredY = minStepDown;
        }
        return desiredY;
    }

    private BlockState worldzero$selectBuildState(ServerLevel serverLevel, BlockPos targetPos) {
        for (Direction direction : Direction.values()) {
            BlockState neighborState = serverLevel.getBlockState(targetPos.relative(direction));
            if (neighborState.isAir()) {
                continue;
            }

            if (!neighborState.getFluidState().isEmpty()) {
                continue;
            }

            if (neighborState.is(net.minecraft.tags.BlockTags.DOORS)
                    || neighborState.is(net.minecraft.tags.BlockTags.BEDS)
                    || neighborState.getLightEmission(serverLevel, targetPos.relative(direction)) > 0) {
                continue;
            }

            return neighborState;
        }

        return switch (serverLevel.random.nextInt(4)) {
            case 0 -> Blocks.OAK_PLANKS.defaultBlockState();
            case 1 -> Blocks.COBBLESTONE.defaultBlockState();
            case 2 -> Blocks.STONE_BRICKS.defaultBlockState();
            default -> Blocks.SPRUCE_PLANKS.defaultBlockState();
        };
    }

    private void worldzero$spawnGhostBlock(ServerLevel serverLevel, BlockPos blockPos, BlockState blockState) {
        Display.BlockDisplay blockDisplay = EntityType.BLOCK_DISPLAY.create(serverLevel);
        if (blockDisplay == null) {
            return;
        }

        CompoundTag displayTag = blockDisplay.saveWithoutId(new CompoundTag());
        displayTag.put("block_state", NbtUtils.writeBlockState(blockState));
        displayTag.putFloat("width", 1.0F);
        displayTag.putFloat("height", 1.0F);
        displayTag.putInt("interpolation_duration", 1);
        blockDisplay.load(displayTag);
        blockDisplay.setInvulnerable(true);
        blockDisplay.setNoGravity(true);
        blockDisplay.addTag(WORLDZERO_HOUSE_DISPLAY_TAG);
        blockDisplay.setPos(blockPos.getX() + 0.5D, blockPos.getY(), blockPos.getZ() + 0.5D);
        serverLevel.addFreshEntity(blockDisplay);

        this.worldzero$ghostDisplayId = blockDisplay.getUUID();
        this.worldzero$ghostBlockPos = blockPos;
        this.worldzero$ghostBlockState = blockState;

        SoundType soundType = blockState.getSoundType(serverLevel, blockPos, this);
        serverLevel.playSound(
                null,
                blockPos,
                soundType.getPlaceSound(),
                SoundSource.PLAYERS,
                0.65F,
                0.92F + serverLevel.random.nextFloat() * 0.14F
        );
    }

    private void worldzero$removeGhostBlock(ServerLevel serverLevel, boolean showBreakEffect) {
        if (this.worldzero$ghostDisplayId != null) {
            Entity displayEntity = serverLevel.getEntity(this.worldzero$ghostDisplayId);
            if (displayEntity != null) {
                displayEntity.discard();
            }
        }

        if (showBreakEffect && this.worldzero$ghostBlockPos != null) {
            serverLevel.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, this.worldzero$ghostBlockState),
                    this.worldzero$ghostBlockPos.getX() + 0.5D,
                    this.worldzero$ghostBlockPos.getY() + 0.5D,
                    this.worldzero$ghostBlockPos.getZ() + 0.5D,
                    18,
                    0.25D,
                    0.25D,
                    0.25D,
                    0.02D
            );
            serverLevel.levelEvent(2001, this.worldzero$ghostBlockPos, Block.getId(this.worldzero$ghostBlockState));
        }

        this.worldzero$ghostDisplayId = null;
        this.worldzero$ghostBlockPos = null;
        this.worldzero$ghostBlockState = Blocks.AIR.defaultBlockState();
    }

    private void worldzero$lookAtPlayer(ServerPlayer targetPlayer) {
        this.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, targetPlayer.getEyePosition());
        this.setYHeadRot(this.getYRot());
        this.setYBodyRot(this.getYRot());
    }

    private void worldzero$tickFarmRestoration(ServerLevel serverLevel, ServerPlayer targetPlayer) {
        if (this.worldzero$farmStartDelayTicks > 0) {
            this.worldzero$farmStartDelayTicks--;
            this.worldzero$currentWorkPos = null;
            this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            this.setDeltaMovement(Vec3.ZERO);
            this.setSprinting(false);
            this.worldzero$lookAtPlayer(targetPlayer);
            return;
        }

        if (this.worldzero$farmApproachPauseTicks > 0) {
            this.worldzero$farmApproachPauseTicks--;
            this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            this.setDeltaMovement(Vec3.ZERO);
            this.setSprinting(false);
            if (this.worldzero$currentWorkPos != null) {
                this.worldzero$lookAtBlock(this.worldzero$currentWorkPos);
            }
            return;
        }

        if (this.worldzero$farmApproachIndex < this.worldzero$farmApproachTargets.size()) {
            BlockPos target = this.worldzero$farmApproachTargets.get(this.worldzero$farmApproachIndex);
            this.worldzero$currentWorkPos = target;
            this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (!this.worldzero$farmDoorOpened
                    && this.worldzero$farmDoorOpenStep == this.worldzero$farmApproachIndex
                    && this.worldzero$tryOpenFarmScriptDoors(serverLevel, targetPlayer)) {
                this.worldzero$farmApproachPauseTicks = 8;
                this.setDeltaMovement(Vec3.ZERO);
                this.setSprinting(false);
                return;
            }

            if (this.worldzero$isFarmPassageTarget(serverLevel, target)
                    && this.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D) <= 2.25D
                    && this.worldzero$tryOpenFarmPassage(serverLevel, target, targetPlayer)) {
                this.worldzero$farmApproachPauseTicks = 6;
                this.setDeltaMovement(Vec3.ZERO);
                this.setSprinting(false);
                return;
            }

            if (this.worldzero$moveTowardTarget(serverLevel, target, 0.12D, true)) {
                return;
            }

            this.worldzero$farmApproachIndex++;
            this.setDeltaMovement(Vec3.ZERO);
            this.setSprinting(false);
            return;
        }

        if (this.worldzero$farmReadyToWake) {
            this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            this.setDeltaMovement(Vec3.ZERO);
            this.setSprinting(false);
            this.worldzero$lookAtPlayer(targetPlayer);
            return;
        }

        if (this.worldzero$farmFinishTicks > 0
                && this.worldzero$farmPhase >= 2
                && this.worldzero$farmTillIndex >= this.worldzero$farmTillTargets.size()
                && this.worldzero$farmPlantIndex >= this.worldzero$farmPlantTargets.size()) {
            this.worldzero$farmFinishTicks--;
            this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            this.setDeltaMovement(Vec3.ZERO);
            this.setSprinting(false);
            this.worldzero$lookAtPlayer(targetPlayer);
            if (this.worldzero$farmFinishTicks <= 0) {
                this.worldzero$farmReadyToWake = true;
            }
            return;
        }

        if (this.worldzero$farmPhase == 0) {
            if (!this.worldzero$hasPendingTillForActiveLine()) {
                if (this.worldzero$hasPendingPlantForActiveLine()) {
                    this.worldzero$farmPhase = 1;
                    this.worldzero$refreshFarmHeldItem();
                } else {
                    this.worldzero$advanceToNextFarmLine();
                }
                this.worldzero$farmActionCooldownTicks = 0;
                return;
            }

            FarmTillTarget target = this.worldzero$farmTillTargets.get(this.worldzero$farmTillIndex);
            this.worldzero$currentWorkPos = target.worldzero$pos;
            this.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
            if (this.worldzero$moveTowardTarget(serverLevel, target.worldzero$pos, 0.14D, false)) {
                return;
            }

            if (this.worldzero$farmActionCooldownTicks > 0) {
                this.worldzero$farmActionCooldownTicks--;
                this.worldzero$lookAtBlock(target.worldzero$pos);
                return;
            }

            this.swing(InteractionHand.MAIN_HAND);
            serverLevel.setBlock(
                    target.worldzero$pos,
                    target.worldzero$restoreState,
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            );
            serverLevel.playSound(
                    null,
                    target.worldzero$pos,
                    SoundEvents.HOE_TILL,
                    SoundSource.PLAYERS,
                    0.85F,
                    0.94F + serverLevel.random.nextFloat() * 0.12F
            );
            this.worldzero$farmActionCooldownTicks = 4;
            this.worldzero$farmTillIndex++;
            if (!this.worldzero$hasPendingTillForActiveLine()) {
                if (this.worldzero$hasPendingPlantForActiveLine()) {
                    this.worldzero$farmPhase = 1;
                    this.worldzero$refreshFarmHeldItem();
                } else {
                    this.worldzero$advanceToNextFarmLine();
                }
            }
            return;
        }

        if (this.worldzero$farmPhase == 1) {
            if (!this.worldzero$hasPendingPlantForActiveLine()) {
                this.worldzero$advanceToNextFarmLine();
                return;
            }

            FarmPlantTarget target = this.worldzero$farmPlantTargets.get(this.worldzero$farmPlantIndex);
            this.worldzero$currentWorkPos = target.worldzero$soilPos;
            this.setItemInHand(InteractionHand.MAIN_HAND, target.worldzero$heldItem.copy());
            if (this.worldzero$moveTowardTarget(serverLevel, target.worldzero$soilPos, 0.14D, false)) {
                return;
            }

            if (this.worldzero$farmActionCooldownTicks > 0) {
                this.worldzero$farmActionCooldownTicks--;
                this.worldzero$lookAtBlock(target.worldzero$plantPos);
                return;
            }

            this.swing(InteractionHand.MAIN_HAND);
            serverLevel.setBlock(
                    target.worldzero$soilPos,
                    target.worldzero$soilState,
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            );
            serverLevel.setBlock(
                    target.worldzero$plantPos,
                    target.worldzero$plantState,
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            );
            SoundType plantSound = target.worldzero$plantState.getSoundType(serverLevel, target.worldzero$plantPos, this);
            serverLevel.playSound(
                    null,
                    target.worldzero$plantPos,
                    plantSound.getPlaceSound(),
                    SoundSource.PLAYERS,
                    0.8F,
                    0.96F + serverLevel.random.nextFloat() * 0.1F
            );
            this.worldzero$farmActionCooldownTicks = 4;
            this.worldzero$farmPlantIndex++;
            if (!this.worldzero$hasPendingPlantForActiveLine()) {
                this.worldzero$advanceToNextFarmLine();
            }
            return;
        }

        this.worldzero$lookAtPlayer(targetPlayer);
        this.setDeltaMovement(Vec3.ZERO);
        this.setSprinting(false);
    }

    private boolean worldzero$moveTowardTarget(
            ServerLevel serverLevel,
            BlockPos targetPos,
            double speedPerTick,
            boolean targetIsStandPosition
    ) {
        Vec3 target = Vec3.atBottomCenterOf(targetPos);
        Vec3 delta = target.subtract(this.position());
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        this.worldzero$lookAtBlock(targetPos);
        if (horizontalDistance <= 0.42D) {
            this.setDeltaMovement(Vec3.ZERO);
            this.setSprinting(false);
            return false;
        }

        double step = Math.min(speedPerTick, horizontalDistance);
        double moveX = (delta.x / horizontalDistance) * step;
        double moveZ = (delta.z / horizontalDistance) * step;
        double desiredY = targetIsStandPosition ? targetPos.getY() : targetPos.getY() + 1.0D;
        double maxStepUp = this.getY() + 0.5D;
        double minStepDown = this.getY() - 0.5D;
        double nextY = Mth.clamp(desiredY, minStepDown, maxStepUp);
        Vec3 movement = new Vec3(moveX, nextY - this.getY(), moveZ);
        this.setDeltaMovement(movement);
        this.setPos(this.getX() + moveX, nextY, this.getZ() + moveZ);
        this.hasImpulse = true;
        this.setSprinting(false);
        this.worldzero$playFarmFootstep(serverLevel, Math.sqrt(moveX * moveX + moveZ * moveZ));
        return true;
    }

    private boolean worldzero$tryOpenFarmScriptDoors(ServerLevel serverLevel, ServerPlayer targetPlayer) {
        if (this.worldzero$farmDoorOpened || this.worldzero$farmDoorTargets.isEmpty()) {
            return false;
        }

        boolean openedAny = false;
        boolean playedSound = false;
        for (BlockPos doorTarget : this.worldzero$farmDoorTargets) {
            boolean opened = this.worldzero$tryOpenFarmPassage(
                    serverLevel,
                    doorTarget,
                    playedSound ? null : targetPlayer,
                    !playedSound
            );
            openedAny |= opened;
            if (opened && !playedSound) {
                playedSound = true;
            }
        }
        if (openedAny) {
            this.worldzero$farmDoorOpened = true;
        }
        return openedAny;
    }

    private boolean worldzero$isFarmPassageTarget(ServerLevel serverLevel, BlockPos targetPos) {
        BlockState state = serverLevel.getBlockState(targetPos);
        return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock;
    }

    private boolean worldzero$tryOpenFarmPassage(ServerLevel serverLevel, BlockPos targetPos, @Nullable ServerPlayer targetPlayer) {
        return this.worldzero$tryOpenFarmPassage(serverLevel, targetPos, targetPlayer, true);
    }

    private boolean worldzero$tryOpenFarmPassage(
            ServerLevel serverLevel,
            BlockPos targetPos,
            @Nullable ServerPlayer targetPlayer,
            boolean playSound
    ) {
        BlockState state = serverLevel.getBlockState(targetPos);
        if (state.getBlock() instanceof FenceGateBlock gateBlock
                && state.hasProperty(FenceGateBlock.OPEN)) {
            if (state.getValue(FenceGateBlock.OPEN)) {
                return false;
            }

            serverLevel.setBlock(
                    targetPos,
                    state.setValue(FenceGateBlock.OPEN, true),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            );
            if (playSound) {
                if (targetPlayer != null) {
                    WorldZeroNetwork.sendKoridorDoorSound(
                            targetPlayer,
                            WORLDZERO_FENCE_GATE_OPEN_SOUND_ID,
                            targetPos.getX() + 0.5D,
                            targetPos.getY() + 0.5D,
                            targetPos.getZ() + 0.5D
                    );
                } else {
                    serverLevel.playSound(
                            null,
                            targetPos,
                            SoundEvents.FENCE_GATE_OPEN,
                            SoundSource.PLAYERS,
                            1.2F,
                            0.96F + serverLevel.random.nextFloat() * 0.08F
                    );
                }
            }
            return true;
        }

        BlockPos lowerPos = targetPos;
        BlockState lowerState = state;
        if (lowerState.hasProperty(DoorBlock.HALF) && lowerState.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            lowerPos = lowerPos.below();
            lowerState = serverLevel.getBlockState(lowerPos);
        }

        if (!(lowerState.getBlock() instanceof DoorBlock)
                || !lowerState.hasProperty(DoorBlock.OPEN)
                || !lowerState.hasProperty(DoorBlock.HALF)
                || lowerState.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
            return false;
        }

        if (lowerState.getValue(DoorBlock.OPEN)) {
            return false;
        }

        BlockPos upperPos = lowerPos.above();
        BlockState upperState = serverLevel.getBlockState(upperPos);
        if (!(upperState.getBlock() instanceof DoorBlock)
                || !upperState.hasProperty(DoorBlock.HALF)
                || upperState.getValue(DoorBlock.HALF) != DoubleBlockHalf.UPPER
                || !upperState.hasProperty(DoorBlock.OPEN)) {
            return false;
        }

        serverLevel.setBlock(
                lowerPos,
                lowerState.setValue(DoorBlock.OPEN, true),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
        );
        serverLevel.setBlock(
                upperPos,
                upperState.setValue(DoorBlock.OPEN, true),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
        );
        if (playSound) {
            if (targetPlayer != null) {
                ResourceLocation soundId = lowerState.is(Blocks.IRON_DOOR)
                        ? WORLDZERO_IRON_DOOR_OPEN_SOUND_ID
                        : WORLDZERO_WOODEN_DOOR_OPEN_SOUND_ID;
                WorldZeroNetwork.sendKoridorDoorSound(
                        targetPlayer,
                        soundId,
                        lowerPos.getX() + 0.5D,
                        lowerPos.getY() + 0.5D,
                        lowerPos.getZ() + 0.5D
                );
            } else {
                serverLevel.playSound(
                        null,
                        lowerPos,
                        lowerState.is(Blocks.IRON_DOOR) ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.WOODEN_DOOR_OPEN,
                        SoundSource.PLAYERS,
                        1.2F,
                        0.96F + serverLevel.random.nextFloat() * 0.08F
                );
            }
        }
        return true;
    }

    private void worldzero$playFarmFootstep(ServerLevel serverLevel, double horizontalDistanceMoved) {
        this.worldzero$farmWalkDistance += horizontalDistanceMoved;
        if (this.worldzero$farmWalkDistance < 0.9D) {
            return;
        }
        this.worldzero$farmWalkDistance = 0.0D;

        BlockPos belowPos = this.blockPosition().below();
        BlockState belowState = serverLevel.getBlockState(belowPos);
        if (belowState.isAir()) {
            return;
        }

        SoundType soundType = belowState.getSoundType(serverLevel, belowPos, this);
        serverLevel.playSound(
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                soundType.getStepSound(),
                SoundSource.PLAYERS,
                Math.max(0.24F, soundType.getVolume() * 0.3F),
                soundType.getPitch()
        );
    }

    private void worldzero$lookAtBlock(BlockPos targetPos) {
        this.lookAt(
                net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                Vec3.atCenterOf(targetPos)
        );
        this.setYHeadRot(this.getYRot());
        this.setYBodyRot(this.getYRot());
    }

    private ItemStack worldzero$getCurrentPlantItem() {
        if (this.worldzero$farmPlantIndex < 0 || this.worldzero$farmPlantIndex >= this.worldzero$farmPlantTargets.size()) {
            return ItemStack.EMPTY;
        }

        return this.worldzero$farmPlantTargets.get(this.worldzero$farmPlantIndex).worldzero$heldItem.copy();
    }

    private void worldzero$advanceToNextFarmLine() {
        int nextTillLine = this.worldzero$farmTillIndex < this.worldzero$farmTillTargets.size()
                ? this.worldzero$farmTillTargets.get(this.worldzero$farmTillIndex).worldzero$lineOrder
                : Integer.MAX_VALUE;
        int nextPlantLine = this.worldzero$farmPlantIndex < this.worldzero$farmPlantTargets.size()
                ? this.worldzero$farmPlantTargets.get(this.worldzero$farmPlantIndex).worldzero$lineOrder
                : Integer.MAX_VALUE;

        if (nextTillLine == Integer.MAX_VALUE && nextPlantLine == Integer.MAX_VALUE) {
            this.worldzero$farmActiveLineOrder = Integer.MAX_VALUE;
            this.worldzero$farmPhase = 2;
            this.worldzero$refreshFarmHeldItem();
            return;
        }

        this.worldzero$farmActiveLineOrder = Math.min(nextTillLine, nextPlantLine);
        this.worldzero$farmPhase = nextTillLine == this.worldzero$farmActiveLineOrder ? 0 : 1;
        this.worldzero$farmActionCooldownTicks = 0;
        this.worldzero$refreshFarmHeldItem();
    }

    private boolean worldzero$hasPendingTillForActiveLine() {
        return this.worldzero$farmTillIndex < this.worldzero$farmTillTargets.size()
                && this.worldzero$farmTillTargets.get(this.worldzero$farmTillIndex).worldzero$lineOrder == this.worldzero$farmActiveLineOrder;
    }

    private boolean worldzero$hasPendingPlantForActiveLine() {
        return this.worldzero$farmPlantIndex < this.worldzero$farmPlantTargets.size()
                && this.worldzero$farmPlantTargets.get(this.worldzero$farmPlantIndex).worldzero$lineOrder == this.worldzero$farmActiveLineOrder;
    }

    private void worldzero$refreshFarmHeldItem() {
        if (this.worldzero$farmPhase == 0) {
            this.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
            return;
        }

        if (this.worldzero$farmPhase == 1) {
            this.setItemInHand(InteractionHand.MAIN_HAND, this.worldzero$getCurrentPlantItem());
            return;
        }

        this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    public static final class FarmTillTarget {
        final BlockPos worldzero$pos;
        final BlockState worldzero$restoreState;
        final int worldzero$lineOrder;

        public FarmTillTarget(BlockPos pos, BlockState restoreState, int lineOrder) {
            this.worldzero$pos = pos.immutable();
            this.worldzero$restoreState = restoreState;
            this.worldzero$lineOrder = lineOrder;
        }

        public BlockPos worldzero$pos() {
            return this.worldzero$pos;
        }
    }

    public static final class FarmPlantTarget {
        final BlockPos worldzero$soilPos;
        final BlockPos worldzero$plantPos;
        final BlockState worldzero$soilState;
        final BlockState worldzero$plantState;
        final ItemStack worldzero$heldItem;
        final int worldzero$lineOrder;

        public FarmPlantTarget(
                BlockPos soilPos,
                BlockPos plantPos,
                BlockState soilState,
                BlockState plantState,
                ItemStack heldItem,
                int lineOrder
        ) {
            this.worldzero$soilPos = soilPos.immutable();
            this.worldzero$plantPos = plantPos.immutable();
            this.worldzero$soilState = soilState;
            this.worldzero$plantState = plantState;
            this.worldzero$heldItem = heldItem.copy();
            this.worldzero$lineOrder = lineOrder;
        }

        public BlockPos worldzero$soilPos() {
            return this.worldzero$soilPos;
        }
    }

    private static double worldzero$square(double value) {
        return value * value;
    }
}
