package ru.nekostul.worldzero;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

public class WorldZeroHouseEchoEntity extends Monster {
    public static final String WORLDZERO_HOUSE_DISPLAY_TAG = "worldzero_house_display";

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

    @Override
    protected void registerGoals() {
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

    private static double worldzero$square(double value) {
        return value * value;
    }
}
