package com.fjjy.entity;

import java.util.EnumSet;
import java.util.UUID;

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

public class FlyingChestEntity extends PathfinderMob {
	// Follow goal stops once the chest is within this radius of the owner.
	private static final float CLOSE_ENOUGH_DISTANCE = 4.0F;
	// Distance threshold where follow speed switches to the faster catch-up speed.
	private static final float TOO_FAR_DISTANCE = 16.0F;
	// Owner must remain within this range of the base station for following to stay active.
	private static final float MAX_OWNER_RANGE_FROM_BASE = 32.0F;
	// Regular follow speed used while the owner is nearby.
	private static final double FOLLOW_SPEED_NEAR = 1.75D;
	// Increased follow speed used when the chest lags far behind.
	private static final double FOLLOW_SPEED_FAR = 2.25D;
	// Speed used when returning to the base hover position.
	private static final double RETURN_TO_BASE_SPEED = 2.25D;//todo: slow down near end of path

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
		private int recalcTicks;

		private FollowOwnerGoal(FlyingChestEntity mob) {
			this.mob = mob;
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
			this.recalcTicks = 0;
			this.mob.isDocked = false;
		}

		@Override
		public void stop() {
			this.mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			Player owner = this.mob.getNearbyOwner();
			if (owner != null && --this.recalcTicks <= 0) {
				this.recalcTicks = 10;
				this.mob.getLookControl().setLookAt(owner, 45.0F, 90.0F);
				double distanceToTargetSqr = this.mob.distanceToSqr(owner);
				double speed = distanceToTargetSqr > (double) (TOO_FAR_DISTANCE * TOO_FAR_DISTANCE)
					? FOLLOW_SPEED_FAR
					: FOLLOW_SPEED_NEAR;
				this.mob.getNavigation().moveTo(owner.getX(), owner.getY() + 1.0D, owner.getZ(), speed);
			}
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
			this.mob.getLookControl().setLookAt(baseHoverPosition.x, baseHoverPosition.y, baseHoverPosition.z, 45.0F, 90.0F);
			this.mob.getNavigation().moveTo(baseHoverPosition.x, baseHoverPosition.y, baseHoverPosition.z, RETURN_TO_BASE_SPEED);
		}

		@Override
		public void stop() {
			this.mob.isDocked = true;
			this.mob.getNavigation().stop();
		}
	}
}
