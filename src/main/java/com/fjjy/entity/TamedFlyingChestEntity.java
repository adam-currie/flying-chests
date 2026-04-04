package com.fjjy.entity;

import java.util.EnumSet;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.fjjy.FlyingChests;
import com.fjjy.block.FlyingChestBlock;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class TamedFlyingChestEntity extends FlyingChestEntity {

	private static final EntityDataAccessor<Boolean> IS_DOCKED =
		SynchedEntityData.defineId(TamedFlyingChestEntity.class, EntityDataSerializers.BOOLEAN);

	private static final EntityDataAccessor<Byte> BASE_DIRECTION =
		SynchedEntityData.defineId(TamedFlyingChestEntity.class, EntityDataSerializers.BYTE);

	// NaN y = not yet set
	private static final EntityDataAccessor<Vector3fc> BASE_STATION_POS =
		SynchedEntityData.defineId(TamedFlyingChestEntity.class, EntityDataSerializers.VECTOR3);

	private static final EntityDataAccessor<String> OWNER_UUID =
		SynchedEntityData.defineId(TamedFlyingChestEntity.class, EntityDataSerializers.STRING);

	private static final EntityDataAccessor<Boolean> IS_ACTIVE =
		SynchedEntityData.defineId(TamedFlyingChestEntity.class, EntityDataSerializers.BOOLEAN);

	// owner in operating range of the base station when it is the closest owned base to the player, otherwise null
	public Player activeOwner = null;

	// Set by client init code to avoid client-only imports in the main module
	public static Consumer<TamedFlyingChestEntity> onActiveStateChanged = null;
	public static BooleanSupplier allowRightClickWhileFlying = () -> false;

	public TamedFlyingChestEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
		super(entityType, level);
	}

	public void setActiveOwner(@Nullable Player player) {
		if (this.level().isClientSide()) throw new IllegalStateException("setActiveOwner must only be called on the server");
		this.activeOwner = player;
		this.entityData.set(IS_ACTIVE, player != null);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(IS_DOCKED, true);
		builder.define(BASE_DIRECTION, (byte) 2);
		builder.define(BASE_STATION_POS, new Vector3f(0.0f, Float.NaN, 0.0f));
		builder.define(OWNER_UUID, "");
		builder.define(IS_ACTIVE, false);
	}

	@Nullable
	public UUID getOwnerUuid() {
		String s = this.entityData.get(OWNER_UUID);
		return s.isEmpty() ? null : UUID.fromString(s);
	}

	public void setOwnerUuid(@Nullable UUID uuid) {
		this.entityData.set(OWNER_UUID, uuid == null ? "" : uuid.toString());
	}

	public boolean isActive() {
		return this.entityData.get(IS_ACTIVE);
	}

	@Override
	public Direction getBaseDirection() {
		return Direction.from3DDataValue(this.entityData.get(BASE_DIRECTION));
	}

	public void setBaseDirection(Direction dir) {
		this.entityData.set(BASE_DIRECTION, (byte) dir.get3DDataValue());
	}

	@Override
	public boolean isDocked() {
		return this.entityData.get(IS_DOCKED);
	}

	public void setDocked(boolean docked) {
		this.entityData.set(IS_DOCKED, docked);
		if (!this.level().isClientSide()) {
			Vec3 bsp = this.getBaseStationPos();
			if (bsp != null) {
				BlockPos blockPos = BlockPos.containing(bsp);
				BlockState state = this.level().getBlockState(blockPos);
				if (state.hasProperty(FlyingChestBlock.IS_DOCKED)) {
					this.level().setBlockAndUpdate(blockPos, state.setValue(FlyingChestBlock.IS_DOCKED, docked));
				}
			}
		}
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (key.equals(IS_ACTIVE) && this.level().isClientSide() && onActiveStateChanged != null) {
			onActiveStateChanged.accept(this);
		}
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FollowOwnerGoal(this));
		this.goalSelector.addGoal(1, new ReturnToBaseGoal(this));
	}

	public void openCombinedInventory(ServerPlayer player) {
		this.openingManager.openCombined(player);
	}

	public void openInventory(ServerPlayer player) {
		this.openingManager.openRegular(player);
	}

	@Override
	public boolean isPickable() {
		return this.isDocked() || allowRightClickWhileFlying.getAsBoolean();
	}

	public Vec3 getBaseStationPos() {
		Vector3fc v = this.entityData.get(BASE_STATION_POS);
		return Float.isNaN(v.y()) ? null : new Vec3(v.x(), v.y(), v.z());
	}

	private void setBaseStationPos(Vec3 pos) {
		this.entityData.set(BASE_STATION_POS, new Vector3f((float)pos.x, (float)pos.y, (float)pos.z));
	}

	/**
	 * follow range = in operating range but further than auto docking range
	 */
	private boolean isActiveOwnerInFollowRange() {
		return activeOwner != null && activeOwner.distanceToSqr(this.getBaseStationPos()) > 20.0D;
	}

	@Override
	protected void doPush(Entity entity) {
		if (isDocked() || !(entity instanceof Player)) {
			super.doPush(entity);
		}
	}

	@Override
	public void push(Entity entity) {
		if (isDocked() || !(entity instanceof Player)) {
			super.push(entity);
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		Vec3 bsp = this.getBaseStationPos();
		if (bsp != null) output.store("BaseStationPos", Vec3.CODEC, bsp);
		output.store("IsDocked", Codec.BOOL, this.isDocked());
		output.store("BaseDirection", Codec.BYTE, (byte) this.getBaseDirection().get3DDataValue());
		UUID ownerUuid = this.getOwnerUuid();
		if (ownerUuid != null) output.store("OwnerUuid", Codec.STRING, ownerUuid.toString());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		input.read("BaseStationPos", Vec3.CODEC).ifPresent(this::setBaseStationPos);
		input.read("IsDocked", Codec.BOOL).ifPresent(this::setDocked);
		input.read("BaseDirection", Codec.BYTE)
			.ifPresent(b -> setBaseDirection(Direction.from3DDataValue(b)));
		input.read("OwnerUuid", Codec.STRING)
			.ifPresent(s -> setOwnerUuid(UUID.fromString(s)));
	}

	public static TamedFlyingChestEntity spawnFromPlacement(ServerLevel level, BlockPos baseStationPos, Direction facing) {
		TamedFlyingChestEntity entity = new TamedFlyingChestEntity(FlyingChests.FLYING_CHEST_ENTITY_TYPE, level);
		Vec3 bsp = Vec3.atCenterOf(baseStationPos).subtract(0, .43, 0);
		entity.setBaseStationPos(bsp);
		final var yRot = facing.toYRot();
		entity.setBaseDirection(facing);
		entity.snapTo(bsp.x, bsp.y, bsp.z, facing.toYRot(), 0.0F);
		entity.yHeadRot = yRot;
		entity.finalizeSpawn((ServerLevelAccessor) level, level.getCurrentDifficultyAt(baseStationPos), EntitySpawnReason.MOB_SUMMONED, null);
		entity.setPersistenceRequired();
		level.addFreshEntity(entity);
		return entity;
	}

	private static final class FollowOwnerGoal extends Goal {
		private final TamedFlyingChestEntity mob;
		private int ticksRemaining;
		private boolean skippedPrevDirectionUpdate = false;
		private final RandomSource rng;
		private boolean lookAtOwner = false;

		private FollowOwnerGoal(TamedFlyingChestEntity mob) {
			this.mob = mob;
			this.rng = mob.getRandom();
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return this.mob.isActiveOwnerInFollowRange();
		}

		@Override
		public boolean canContinueToUse() {
			return this.mob.isActiveOwnerInFollowRange();
		}

		@Override
		public void start() {
			this.ticksRemaining = 0;
			this.mob.setDocked(false);
		}

		@Override
		public void stop() {
			this.mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			if (this.mob.isActiveOwnerInFollowRange()) {
				if (--this.ticksRemaining <= 0) {
					this.ticksRemaining = calculateTicksRemaining();
					updateDirection();
					// chance to look at owner (instead of the target)
					this.lookAtOwner = this.rng.nextInt(3) == 0; // 1 in 3
				}
				if (this.lookAtOwner) {
					this.mob.getLookControl().setLookAt(this.mob.activeOwner, 45.0F, 90.0F);
				}
			}
		}

		private void updateDirection(){
			{// if target/mob is close to player and not blocking their narrow fov, theres a chance to linger
				Vec3 currentTarget = getCurrentTarget();
				if (
					this.mob.activeOwner.distanceToSqr(currentTarget) < 16.0D
					&& this.rng.nextBoolean()
					&& !this.skippedPrevDirectionUpdate
					&& !isWithinOwnerNarrowFov(this.mob.activeOwner, currentTarget)
				) {
					this.skippedPrevDirectionUpdate = true;
					return;
				} else {
					this.skippedPrevDirectionUpdate = false;
				}
			}

			Vec3 target = null;
			for (int i = 0; i < 3; i++) {
				target = sampleCircle(this.mob.activeOwner, 2.5D);

				//shift up from players feet
				target = target.add(0.0D, 2.0D, 0.0D);

				//apply gaussian blur
				target = target.add(this.rng.nextGaussian(), this.rng.nextGaussian(), this.rng.nextGaussian());

				// retry if target is blocking view of owner
				if (!isWithinOwnerNarrowFov(this.mob.activeOwner, target)) {
					break;
				}
			}

			double distanceToTargetSqr = this.mob.distanceToSqr(target);

			//random speed boost gets fed in linearly before the sqrting so it doesn't effect top speed/long range paths
			double speedBoost = this.rng.nextInt(8);
			double speed = Math.sqrt(Math.sqrt(distanceToTargetSqr + speedBoost)) / 2;

			this.mob.getNavigation().moveTo(target.x, target.y, target.z, speed);
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

		private int calculateTicksRemaining() {
			final double distSqr = this.mob.activeOwner.distanceToSqr(getCurrentTarget());
			//minimum ticks, half of this is also added pre randomization so distant points still get some randomization
			final int minTicks = 10;
			//max ticks before randomization, much higher when close
			final int maxTicks = (int) (2048 / Math.pow(distSqr + 8, 2)) + minTicks / 2;
			//randomize
			return this.rng.nextInt(maxTicks + 1) + minTicks;
		}

		private Vec3 getCurrentTarget() {
			BlockPos targetPos = this.mob.getNavigation().getTargetPos();
			if (targetPos == null) {
				targetPos = this.mob.blockPosition();
			}
			return Vec3.atCenterOf(targetPos);
		}
	}

	private static final class ReturnToBaseGoal extends Goal {
		private final TamedFlyingChestEntity mob;

		private ReturnToBaseGoal(TamedFlyingChestEntity mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return !this.mob.isDocked();
		}

		@Override
		public boolean canContinueToUse() {
			return !this.mob.isDocked();
		}

		@Override
		public void tick() {
			// Snap to base station position when docking finishes
			if (!this.mob.getNavigation().isInProgress()) {
				Vec3 bsp = this.mob.getBaseStationPos();
				if (bsp != null) this.mob.setPos(bsp);
				this.mob.setDeltaMovement(Vec3.ZERO);
				this.mob.setDocked(true);
			}
		}

		@Override
		public void start() {
			double speed = 2.9D;
			Vec3 bsp = this.mob.getBaseStationPos();
			if (bsp != null) this.mob.getNavigation().moveTo(bsp.x, bsp.y, bsp.z, speed);
		}

		@Override
		public void stop() {
			this.mob.getNavigation().stop();
		}
	}
}
