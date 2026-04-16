package ru.nekostul.worldzero;

import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import ru.nekostul.worldzero.achievement.WorldZeroAdvancementTriggers;

import java.util.UUID;

public class WorldZeroEchoEntity extends Monster {
    private static final double WORLDZERO_ECHO_DESPAWN_MIN_DISTANCE = 5.0D;
    private static final double WORLDZERO_ECHO_DESPAWN_MAX_DISTANCE = 10.0D;
    private static final double WORLDZERO_RULE_BREAK_DESPAWN_DISTANCE_SQR = 4.0D * 4.0D;
    private static final int WORLDZERO_RULE_BREAK_DESPAWN_TIMER_START_TICKS = 5 * 20;
    private static final int WORLDZERO_RULE_BREAK_DESPAWN_TIMER_END_TICKS = 10 * 20;
    private static final double WORLDZERO_RULE_BREAK_SPRINT_SPEED_BLOCKS_PER_TICK = 0.32D;
    private static final int WORLDZERO_RULE_BREAK_IDLE_MIN_TICKS = 20;
    private static final int WORLDZERO_RULE_BREAK_IDLE_MAX_TICKS = 40;
    private static final double WORLDZERO_FREEZE_PASS_SPEED_MIN = 0.20D;
    private static final double WORLDZERO_FREEZE_PASS_SPEED_MAX = 0.80D;
    private static final int WORLDZERO_FREEZE_PASS_MIN_TICKS = 20;
    private static final int WORLDZERO_FREEZE_PASS_MAX_TICKS = 40;
    private static final int WORLDZERO_FREEZE_PASS_FOOTSTEP_INTERVAL_TICKS = 4;
    private static final int WORLDZERO_HE_IS_CLOSE_REQUIRED_TICKS = 15 * 20;
    private static final double WORLDZERO_HE_IS_CLOSE_LOOK_DOT_THRESHOLD = 0.975D;
    private double worldzero$echoDespawnDistanceSqr = 8.0D * 8.0D;
    private boolean worldzero$ruleBreakEventActive;
    private UUID worldzero$ruleBreakTargetPlayerId;
    private int worldzero$ruleBreakIdleTicks;
    private int worldzero$ruleBreakChaseTicks;
    private boolean worldzero$ruleBreakCountdownStarted;
    private boolean worldzero$freezePassActive;
    private double worldzero$freezePassDirectionX;
    private double worldzero$freezePassDirectionZ;
    private double worldzero$freezePassSpeed;
    private int worldzero$freezePassTicksRemaining;
    private boolean worldzero$windowWatchActive;
    private UUID worldzero$windowWatchTargetPlayerId;
    private int worldzero$windowWatchTicksRemaining;
    private boolean worldzero$paralysisBedActive;
    private boolean worldzero$heIsCloseTrackingActive;
    private UUID worldzero$heIsCloseTargetPlayerId;
    private int worldzero$heIsCloseLookTicks;
    private boolean worldzero$heIsCloseGranted;

    public WorldZeroEchoEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.setNoAi(true);
        this.setInvulnerable(true);
        this.setSilent(true);
        this.setCustomNameVisible(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D);
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
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }

        Player nearestPlayer = this.level().getNearestPlayer(this, 128.0D);
        Player focusPlayer = this.worldzero$resolveFocusPlayer(nearestPlayer);
        if (this.getType() == WorldZeroEntities.WORLDZERO_BLACK_ECHO.get() && this.worldzero$freezePassActive) {
            this.worldzero$tickFreezePass();
            if (this.isRemoved()) {
                return;
            }
        }

        if (focusPlayer != null && !this.worldzero$paralysisBedActive) {
            this.worldzero$lookAtPlayer(focusPlayer);
        }

        if (this.getType() == WorldZeroEntities.WORLDZERO_ECHO.get()) {
            this.worldzero$tickHeIsCloseTracking();
            if (this.worldzero$paralysisBedActive) {
                this.setDeltaMovement(0.0D, 0.0D, 0.0D);
                this.setSprinting(false);
            } else if (this.worldzero$windowWatchActive) {
                this.worldzero$tickWindowWatchEvent(focusPlayer);
                if (this.isRemoved()) {
                    return;
                }
            } else if (this.worldzero$ruleBreakEventActive) {
                this.worldzero$tickRuleBreakEvent(focusPlayer);
                if (this.isRemoved()) {
                    return;
                }
            } else if (nearestPlayer != null && this.distanceToSqr(nearestPlayer) < this.worldzero$echoDespawnDistanceSqr) {
                this.discard();
                return;
            }
        }

        if (this.tickCount == 1 || this.tickCount % 20 == 0) {
            this.worldzero$updateNameTag(this.worldzero$resolveFocusPlayer(nearestPlayer));
        }
    }

    private void worldzero$updateNameTag(Player nearestPlayer) {
        if (this.getType() == WorldZeroEntities.WORLDZERO_BLACK_ECHO.get()) {
            if (this.getCustomName() != null) {
                this.setCustomName(null);
            }
            if (this.isCustomNameVisible()) {
                this.setCustomNameVisible(false);
            }
            return;
        }

        if (this.getType() != WorldZeroEntities.WORLDZERO_ECHO.get()) {
            return;
        }

        if (nearestPlayer != null) {
            String playerName = nearestPlayer.getGameProfile().getName();
            if (!playerName.equals(this.getName().getString())) {
                this.setCustomName(Component.literal(playerName));
            }
        }

        if (this.isCustomNameVisible()) {
            this.setCustomNameVisible(false);
        }
    }

    private void worldzero$lookAtPlayer(Player player) {
        double deltaX = player.getX() - this.getX();
        double deltaY = player.getEyeY() - this.getEyeY();
        double deltaZ = player.getZ() - this.getZ();
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        float yaw = (float) (Mth.atan2(deltaZ, deltaX) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(deltaY, horizontalDistance) * (180.0D / Math.PI)));

        this.setYRot(yaw);
        this.setXRot(pitch);
        this.yHeadRot = yaw;
        this.yBodyRot = yaw;
    }

    public void worldzero$setEchoDespawnDistance(double distanceBlocks) {
        double clampedDistance = Mth.clamp(
                distanceBlocks,
                WORLDZERO_ECHO_DESPAWN_MIN_DISTANCE,
                WORLDZERO_ECHO_DESPAWN_MAX_DISTANCE
        );
        this.worldzero$echoDespawnDistanceSqr = clampedDistance * clampedDistance;
    }

    public void worldzero$configureRuleBreakEvent(UUID targetPlayerId, int idleTicks) {
        this.worldzero$ruleBreakEventActive = true;
        this.worldzero$ruleBreakTargetPlayerId = targetPlayerId;
        this.worldzero$ruleBreakIdleTicks = Mth.clamp(
                idleTicks,
                WORLDZERO_RULE_BREAK_IDLE_MIN_TICKS,
                WORLDZERO_RULE_BREAK_IDLE_MAX_TICKS
        );
        this.worldzero$ruleBreakChaseTicks = 0;
        this.worldzero$ruleBreakCountdownStarted = false;
        this.worldzero$echoDespawnDistanceSqr = WORLDZERO_RULE_BREAK_DESPAWN_DISTANCE_SQR;
    }

    public void worldzero$configureFreezePass(double directionX, double directionZ, double speed, int durationTicks) {
        double directionLength = Math.sqrt(directionX * directionX + directionZ * directionZ);
        if (directionLength < 0.0001D) {
            this.worldzero$freezePassDirectionX = 1.0D;
            this.worldzero$freezePassDirectionZ = 0.0D;
        } else {
            this.worldzero$freezePassDirectionX = directionX / directionLength;
            this.worldzero$freezePassDirectionZ = directionZ / directionLength;
        }

        this.worldzero$freezePassSpeed = Mth.clamp(speed, WORLDZERO_FREEZE_PASS_SPEED_MIN, WORLDZERO_FREEZE_PASS_SPEED_MAX);
        this.worldzero$freezePassTicksRemaining = Mth.clamp(
                durationTicks,
                WORLDZERO_FREEZE_PASS_MIN_TICKS,
                WORLDZERO_FREEZE_PASS_MAX_TICKS
        );
        this.worldzero$freezePassActive = true;
    }

    public void worldzero$configureWindowWatch(UUID targetPlayerId, int durationTicks) {
        this.worldzero$windowWatchActive = true;
        this.worldzero$windowWatchTargetPlayerId = targetPlayerId;
        this.worldzero$windowWatchTicksRemaining = Math.max(20, durationTicks);
        this.worldzero$ruleBreakEventActive = false;
    }

    public void worldzero$configureHeIsCloseTracking(UUID targetPlayerId) {
        this.worldzero$heIsCloseTrackingActive = true;
        this.worldzero$heIsCloseTargetPlayerId = targetPlayerId;
        this.worldzero$heIsCloseLookTicks = 0;
        this.worldzero$heIsCloseGranted = false;
    }

    public void worldzero$setParalysisBedActive(boolean active) {
        this.worldzero$paralysisBedActive = active;
        if (active) {
            this.worldzero$windowWatchActive = false;
            this.worldzero$ruleBreakEventActive = false;
            this.setDeltaMovement(0.0D, 0.0D, 0.0D);
            this.setSprinting(false);
        }
    }

    private void worldzero$tickHeIsCloseTracking() {
        if (!this.worldzero$heIsCloseTrackingActive || this.worldzero$heIsCloseGranted) {
            return;
        }

        if (!(this.level() instanceof ServerLevel serverLevel) || this.worldzero$heIsCloseTargetPlayerId == null) {
            this.worldzero$heIsCloseLookTicks = 0;
            return;
        }

        Player targetPlayer = serverLevel.getPlayerByUUID(this.worldzero$heIsCloseTargetPlayerId);
        if (!(targetPlayer instanceof ServerPlayer serverPlayer) || !targetPlayer.isAlive() || targetPlayer.isSpectator()) {
            this.worldzero$heIsCloseLookTicks = 0;
            return;
        }

        if (!this.worldzero$isLookedAtBy(serverPlayer)) {
            this.worldzero$heIsCloseLookTicks = 0;
            return;
        }

        this.worldzero$heIsCloseLookTicks++;
        if (this.worldzero$heIsCloseLookTicks >= WORLDZERO_HE_IS_CLOSE_REQUIRED_TICKS) {
            WorldZeroAdvancementTriggers.grantHeIsClose(serverPlayer);
            this.worldzero$heIsCloseGranted = true;
        }
    }

    private boolean worldzero$isLookedAtBy(ServerPlayer player) {
        if (!player.hasLineOfSight(this)) {
            return false;
        }

        Vec3 eyePosition = player.getEyePosition();
        Vec3 directionToEcho = this.getBoundingBox().getCenter().subtract(eyePosition);
        if (directionToEcho.lengthSqr() < 0.0001D) {
            return false;
        }

        Vec3 lookVector = player.getViewVector(1.0F).normalize();
        return lookVector.dot(directionToEcho.normalize()) >= WORLDZERO_HE_IS_CLOSE_LOOK_DOT_THRESHOLD;
    }

    private void worldzero$tickRuleBreakEvent(Player fallbackNearestPlayer) {
        Player targetPlayer = this.worldzero$resolveRuleBreakTargetPlayer(fallbackNearestPlayer);
        if (targetPlayer != null) {
            this.worldzero$lookAtPlayer(targetPlayer);
        }

        if (this.tickCount <= this.worldzero$ruleBreakIdleTicks) {
            this.setSprinting(false);
            return;
        }

        this.worldzero$ruleBreakChaseTicks++;
        if (!this.worldzero$ruleBreakCountdownStarted
                && this.worldzero$ruleBreakChaseTicks >= WORLDZERO_RULE_BREAK_DESPAWN_TIMER_START_TICKS) {
            this.worldzero$ruleBreakCountdownStarted = true;
        }

        if (targetPlayer != null) {
            if (this.distanceToSqr(targetPlayer) <= WORLDZERO_RULE_BREAK_DESPAWN_DISTANCE_SQR) {
                this.discard();
                return;
            }

            this.worldzero$chaseTowardPlayer(targetPlayer);
        }

        if (this.worldzero$ruleBreakCountdownStarted
                && this.worldzero$ruleBreakChaseTicks >= WORLDZERO_RULE_BREAK_DESPAWN_TIMER_END_TICKS) {
            this.discard();
        }
    }

    private void worldzero$tickWindowWatchEvent(Player focusPlayer) {
        if (focusPlayer != null) {
            this.worldzero$lookAtPlayer(focusPlayer);
        }

        this.setDeltaMovement(0.0D, 0.0D, 0.0D);
        this.setSprinting(false);
        this.worldzero$windowWatchTicksRemaining--;
        if (this.worldzero$windowWatchTicksRemaining <= 0) {
            this.discard();
        }
    }

    private Player worldzero$resolveRuleBreakTargetPlayer(Player fallbackNearestPlayer) {
        if (!this.worldzero$ruleBreakEventActive || this.worldzero$ruleBreakTargetPlayerId == null) {
            return fallbackNearestPlayer;
        }

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return fallbackNearestPlayer;
        }

        Player targetPlayer = serverLevel.getPlayerByUUID(this.worldzero$ruleBreakTargetPlayerId);
        return targetPlayer != null ? targetPlayer : fallbackNearestPlayer;
    }

    private Player worldzero$resolveFocusPlayer(Player fallbackNearestPlayer) {
        if (this.worldzero$windowWatchActive && this.worldzero$windowWatchTargetPlayerId != null) {
            if (this.level() instanceof ServerLevel serverLevel) {
                Player targetPlayer = serverLevel.getPlayerByUUID(this.worldzero$windowWatchTargetPlayerId);
                if (targetPlayer != null) {
                    return targetPlayer;
                }
            }
        }

        return this.worldzero$resolveRuleBreakTargetPlayer(fallbackNearestPlayer);
    }

    private void worldzero$chaseTowardPlayer(Player player) {
        double deltaX = player.getX() - this.getX();
        double deltaZ = player.getZ() - this.getZ();
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        if (horizontalDistance < 0.0001D) {
            return;
        }

        double step = Math.min(WORLDZERO_RULE_BREAK_SPRINT_SPEED_BLOCKS_PER_TICK, horizontalDistance);
        double nextX = this.getX() + (deltaX / horizontalDistance) * step;
        double nextZ = this.getZ() + (deltaZ / horizontalDistance) * step;
        double nextY = this.getY();

        if (this.level() instanceof ServerLevel serverLevel) {
            int groundY = serverLevel.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Mth.floor(nextX),
                    Mth.floor(nextZ)
            );
            double groundHeight = (double) groundY;
            double maxStepUp = this.getY() + 1.05D;
            if (groundHeight > maxStepUp) {
                groundHeight = maxStepUp;
            }
            nextY = groundHeight;
        }

        this.setPos(nextX, nextY, nextZ);
        this.setSprinting(true);
    }

    private void worldzero$tickFreezePass() {
        if (this.worldzero$freezePassTicksRemaining <= 0) {
            this.discard();
            return;
        }

        double nextX = this.getX() + this.worldzero$freezePassDirectionX * this.worldzero$freezePassSpeed;
        double nextZ = this.getZ() + this.worldzero$freezePassDirectionZ * this.worldzero$freezePassSpeed;
        double nextY = this.getY();

        if (this.level() instanceof ServerLevel serverLevel) {
            int groundY = serverLevel.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Mth.floor(nextX),
                    Mth.floor(nextZ)
            );
            double maxStepUp = this.getY() + 1.05D;
            double clampedGroundY = Math.min((double) groundY, maxStepUp);
            if (clampedGroundY >= this.getY() - 1.5D) {
                nextY = clampedGroundY;
            }
        }

        this.setPos(nextX, nextY, nextZ);
        this.setSprinting(true);
        this.worldzero$freezePassTicksRemaining--;
        if (this.worldzero$freezePassTicksRemaining % WORLDZERO_FREEZE_PASS_FOOTSTEP_INTERVAL_TICKS == 0) {
            this.worldzero$playFreezePassFootstep();
        }

        if (this.worldzero$freezePassTicksRemaining <= 0) {
            this.discard();
        }
    }

    private void worldzero$playFreezePassFootstep() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockPos belowPos = this.blockPosition().below();
        BlockState belowState = serverLevel.getBlockState(belowPos);
        SoundType soundType = belowState.getSoundType(serverLevel, belowPos, this);
        serverLevel.playSound(
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                soundType.getStepSound(),
                SoundSource.PLAYERS,
                0.35F,
                0.95F + serverLevel.random.nextFloat() * 0.1F
        );
    }
}
