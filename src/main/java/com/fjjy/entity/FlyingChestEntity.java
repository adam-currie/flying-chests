package com.fjjy.entity;

import java.util.EnumSet;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;

import com.fjjy.FlyingChests;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;

public class FlyingChestEntity extends PathfinderMob {
	private static final float MAX_OWNER_RANGE_FROM_BASE = 32.0F;

	private UUID ownerUuid;
	private BlockPos baseStationPos;
	private boolean isDocked = true;

	public FlyingChestEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
		super(entityType, level);
		this.moveControl = new FlyingMoveControl(this, 20, true);
		this.noPhysics = true;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.FLYING_SPEED, 0.10D)
			.add(Attributes.MOVEMENT_SPEED, 0.10D);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation pathNavigation = new FlyingPathNavigation(this, level);
		pathNavigation.setCanOpenDoors(false);
		pathNavigation.setCanFloat(true);
		pathNavigation.setRequiredPathLength(48.0F);
		return pathNavigation;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FollowOwnerGoal(this));
		this.goalSelector.addGoal(1, new ReturnToBaseGoal(this));
	}

	@Override
	public void travel(Vec3 input) {
		this.travelFlying(input, this.getSpeed());
	}

	/**
	 * gets the owner player if they are online and within operating range of the base station.
	 */
	private Player getNearbyOwner() {
		if (this.ownerUuid == null) {
			return null;
		}
		Player owner = this.level().getPlayerByUUID(this.ownerUuid);
		return 
			owner != null && owner.isAlive() && 
			owner.distanceToSqr(Vec3.atCenterOf(this.baseStationPos))
			<= (double) (MAX_OWNER_RANGE_FROM_BASE * MAX_OWNER_RANGE_FROM_BASE)
				? owner
				: null; 
	}

	private Vec3 getBaseHoverPosition() {
		return Vec3.atCenterOf(this.baseStationPos).add(0.0D, 0.6D, 0.0D);
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canCollideWith(Entity entity) {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.ownerUuid != null) {
			output.store("OwnerUuid", Codec.STRING, this.ownerUuid.toString());
		}
		if (this.baseStationPos != null) {
			output.putInt("BaseX", this.baseStationPos.getX());
			output.putInt("BaseY", this.baseStationPos.getY());
			output.putInt("BaseZ", this.baseStationPos.getZ());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.ownerUuid = input.read("OwnerUuid", Codec.STRING)
			.map(UUID::fromString)
			.orElse(null);
		int baseY = input.getIntOr("BaseY", Integer.MIN_VALUE);
		if (baseY != Integer.MIN_VALUE) {
			this.baseStationPos = new BlockPos(
				input.getIntOr("BaseX", 0),
				baseY,
				input.getIntOr("BaseZ", 0)
			);
		}
	}

	public static FlyingChestEntity spawnFromPlacement(ServerLevel level, BlockPos baseStationPos, Player owner) {
		FlyingChestEntity entity = new FlyingChestEntity(FlyingChests.FLYING_CHEST_ENTITY_TYPE, level);
		Vec3 spawnPosition = Vec3.atCenterOf(baseStationPos).add(0.0D, 0.6D, 0.0D);
		entity.snapTo(spawnPosition, owner.getYRot(), 0.0F);
		entity.finalizeSpawn((ServerLevelAccessor) level, level.getCurrentDifficultyAt(baseStationPos), EntitySpawnReason.MOB_SUMMONED, null);
		entity.ownerUuid = owner.getUUID();
		entity.baseStationPos = baseStationPos.immutable();
		entity.setPersistenceRequired();
		level.addFreshEntity(entity);
		return entity;
	}

	private static final class FollowOwnerGoal extends Goal {
		private final FlyingChestEntity mob;
		private int ticksRemaining;
		private boolean skippedPrev = false;
		private final RandomSource rng;

		private FollowOwnerGoal(FlyingChestEntity mob) {
			this.mob = mob;
			this.rng = mob.getRandom();
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			//todo: here and other places we should skip the checks for most of the ticks, maybe 9 out of 10 so it only checks once a second
			return this.mob.getNearbyOwner() != null;
		}

		@Override
		public boolean canContinueToUse() {
			return this.mob.getNearbyOwner() != null;
		}

		@Override
		public void start() {
			this.ticksRemaining = 0;
			this.mob.isDocked = false;
		}

		@Override
		public void stop() {
			this.mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			Player owner = this.mob.getNearbyOwner();
			if (owner != null && --this.ticksRemaining <= 0) {
				this.ticksRemaining = calculateTicksRemaining(owner);

				{// if target/mob is close to player and not blocking their narrow fov, theres a chance to linger
					Vec3 currentTarget = getCurrentTarget();
					if (
						owner.distanceToSqr(currentTarget) < 16.0D 
						&& true //this.rng.nextBoolean() 
						&& !this.skippedPrev
						&& !isWithinOwnerNarrowFov(owner, currentTarget)
					) {
						this.skippedPrev = true;

						//chance to look at player
						if (true) {
							this.mob.getLookControl().setLookAt(owner, 45.0F, 90.0F);
						}

						return;
					} else {
						this.skippedPrev = false;
					}
				}

				Vec3 target = null;
				for (int i = 0; i < 3; i++) {
					target = sampleCircle(owner, 2.5D);

					//shift up from players feet
					target = target.add(0.0D, 2.0D, 0.0D);
					
					//apply gaussian blur
					target = target.add(this.rng.nextGaussian(), this.rng.nextGaussian(), this.rng.nextGaussian());

					// retry if target is blocking view of owner
					if (!isWithinOwnerNarrowFov(owner, target)) {
						break;
					}
				}

				// 50/50 to look at player or target
				if (this.rng.nextBoolean()) {
					this.mob.getLookControl().setLookAt(owner, 45.0F, 90.0F);
				}	else {
					this.mob.getLookControl().setLookAt(target.x, target.y, target.z, 45.0F, 90.0F);
				}

				double distanceToTargetSqr = this.mob.distanceToSqr(target);

				//random speed boost gets fed in linearly before the sqrting so it doesn't effect top speed/long range paths
				double speedBoost = this.rng.nextInt(8);
				double speed = Math.sqrt(Math.sqrt(distanceToTargetSqr+speedBoost))/2;

				this.mob.getNavigation().moveTo(target.x, target.y, target.z, speed);
			}
		}

		/**
		 * samples a circle around the player
		 */
		private Vec3 sampleCircle(Player owner, double radius) {
			double yaw = Math.toRadians(owner.getYRot());

			double theta = yaw + this.rng.nextDouble() * 2.0D * Math.PI;

			double dx = Math.cos(theta) * radius;
			double dz = Math.sin(theta) * radius;
			return new Vec3(owner.getX() + dx, owner.getY(), owner.getZ() + dz);
		}

		private static boolean isWithinOwnerNarrowFov(@NotNull Player owner, Vec3 target) {
			Vec3 toChest = target.subtract(owner.getEyePosition());
			double toChestLengthSqr = toChest.lengthSqr();
			if (toChestLengthSqr < 1.0E-6D) {
				return true;
			}
			Vec3 directionToChest = toChest.scale(1.0D / Math.sqrt(toChestLengthSqr));
			double lookAlignment = owner.getLookAngle().dot(directionToChest);
			return lookAlignment >= Math.cos(Math.toRadians(20.0D));
		}

		private int calculateTicksRemaining(@NotNull Player owner) {
			final double distSqr = owner.distanceToSqr(getCurrentTarget());
			//minimum ticks, half of this is also added pre randomization so distant points still get some randomization
			final int minTicks = 10;
			//max ticks before randomization, much higher when close
			final int maxTicks = (int) (2048/Math.pow(distSqr + 8, 2)) + minTicks/2;
			//randomize
			return this.rng.nextInt(maxTicks + 1) + minTicks;
		}

		private Vec3 getCurrentTarget(){
			BlockPos targetPos = this.mob.getNavigation().getTargetPos();
			if (targetPos == null) {
				targetPos = this.mob.blockPosition();
			}
			return Vec3.atCenterOf(targetPos);
		}

	}

	private static final class ReturnToBaseGoal extends Goal {
		private final FlyingChestEntity mob;

		private ReturnToBaseGoal(FlyingChestEntity mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return !this.mob.isDocked;
		}

		@Override
		public boolean canContinueToUse() {
			return this.mob.getNavigation().isInProgress();
		}

		@Override
		public void start() {
			Vec3 baseHoverPosition = this.mob.getBaseHoverPosition();
			double distanceToTargetSqr = this.mob.distanceToSqr(baseHoverPosition);
			double speed = Math.sqrt(Math.sqrt(distanceToTargetSqr))/3;
			this.mob.getLookControl().setLookAt(baseHoverPosition.x, baseHoverPosition.y, baseHoverPosition.z, 45.0F, 90.0F);
			this.mob.getNavigation().moveTo(baseHoverPosition.x, baseHoverPosition.y, baseHoverPosition.z, speed);
		}

		@Override
		public void stop() {
			this.mob.isDocked = true;
			this.mob.getNavigation().stop();
		}
	}
}
