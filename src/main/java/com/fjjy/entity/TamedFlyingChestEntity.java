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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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

	private boolean			activeOwnerHasLosToBaseCache = false;
	private int				activeOwnerHasLosToBaseCacheTick = 0;
	private final int		ACTIVE_OWNER_HAS_LOS_TO_BASE_TICK_INTERVAL = 10;

	public TamedFlyingChestEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
		super(entityType, level);
	}

	@Override
	public boolean isAttackable() {
		return false;
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
		this.goalSelector.addGoal(0, new FollowOwnerGoal());
		this.goalSelector.addGoal(1, new ReturnToBaseGoal());
	}

	public void openCombinedInventory(ServerPlayer player) {
		this.openingManager.openCombined(player);
	}

	public void openInventory(ServerPlayer player) {
		this.openingManager.openRegular(player);
	}

	@Override
	public boolean isPickable() {
		return !this.isDocked() && allowRightClickWhileFlying.getAsBoolean();
	}

	public Vec3 getBaseStationPos() {
		Vector3fc v = this.entityData.get(BASE_STATION_POS);
		return Float.isNaN(v.y()) ? null : new Vec3(v.x(), v.y(), v.z());
	}

	private void setBaseStationPos(Vec3 pos) {
		this.entityData.set(BASE_STATION_POS, new Vector3f((float)pos.x, (float)pos.y, (float)pos.z));
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

	public void snapToBase() {
		this.setPos(this.getBaseStationPos());
		this.setDeltaMovement(Vec3.ZERO);
		this.setDocked(true);
	}

	private boolean activeOwnerHasLosToBase() {
		var basePos = BlockPos.containing(getBaseStationPos());

		Vec3 center = Vec3.atCenterOf(basePos);
		Vec3 eye = activeOwner.getEyePosition();

		// 3 faces of a half-cube (radius 0.25) pointing toward the player,
		// each subdivided into a 2×2 grid — 19 unique nodes, no duplicates.
		final double h = 0.25;
		double sx = eye.x >= center.x ? h : -h;
		double sy = eye.y >= center.y ? h : -h;
		double sz = eye.z >= center.z ? h : -h;
		double[] o = { -h, 0.0, h };

		Vec3[] targets = new Vec3[19];
		int i = 0;
		// X face — 9 points
		for (double dy : o) for (double dz : o)
			targets[i++] = new Vec3(center.x + sx, center.y + dy, center.z + dz);
		// Y face — 6 unique (skip dx==sx, already on X face)
		for (double dx : o) if (dx != sx) for (double dz : o)
			targets[i++] = new Vec3(center.x + dx, center.y + sy, center.z + dz);
		// Z face — 4 unique (skip dx==sx or dy==sy, already covered)
		for (double dx : o) if (dx != sx) for (double dy : o) if (dy != sy)
			targets[i++] = new Vec3(center.x + dx, center.y + dy, center.z + sz);

		for (Vec3 target : targets) {
			ClipContext ctx = new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, activeOwner);
			HitResult hit = this.level().clip(ctx);
			// Targets are inside the block — hitting basePos means the ray reached it unobstructed
			if (hit.getType() == HitResult.Type.MISS
					|| (hit instanceof BlockHitResult bhr && bhr.getBlockPos().equals(basePos))) {
				return true;
			}
		}
		return false;
	}

	private boolean activeOwnerHasLosToBaseCached() {
		if (activeOwner == null) return false;
		if (this.level().getGameTime() >= activeOwnerHasLosToBaseCacheTick + ACTIVE_OWNER_HAS_LOS_TO_BASE_TICK_INTERVAL) {
			activeOwnerHasLosToBaseCache = activeOwnerHasLosToBase();
			activeOwnerHasLosToBaseCacheTick = (int) this.level().getGameTime();
		}
		return activeOwnerHasLosToBaseCache;
	}

	private final class FollowOwnerGoal extends Goal {
		private int ticksRemaining;
		private boolean skippedPrevDirectionUpdate = false;
		private final RandomSource rng;
		private boolean lookAtOwner = false;

		private FollowOwnerGoal() {
			this.rng = getRandom();
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return activeOwnerHasLosToBaseCached()
				? activeOwner.distanceToSqr(getBaseStationPos()) > 20.0D
				: activeOwner != null; // if we don't have LOS but the owner is still in range, 
		}

		@Override
		public void start() {
			this.ticksRemaining = 0;
			setDocked(false);
		}

		@Override
		public void stop() {
			getNavigation().stop();
		}

		@Override
		public void tick() {
			if (--this.ticksRemaining <= 0) {
				this.ticksRemaining = calculateTicksRemaining();
				updateDirection();
				// chance to look at owner (instead of the target)
				this.lookAtOwner = this.rng.nextInt(3) == 0; // 1 in 3
			}
			if (this.lookAtOwner) {
				getLookControl().setLookAt(activeOwner, 45.0F, 90.0F);
			}
		}

		private void updateDirection(){
			{// if target/mob is close to player and not blocking their narrow fov, theres a chance to linger
				Vec3 currentTarget = getCurrentTarget();
				if (
					activeOwner.distanceToSqr(currentTarget) < 16.0D
					&& this.rng.nextBoolean()
					&& !this.skippedPrevDirectionUpdate
					&& !isWithinOwnerNarrowFov(activeOwner, currentTarget)
				) {
					this.skippedPrevDirectionUpdate = true;
					return;
				} else {
					this.skippedPrevDirectionUpdate = false;
				}
			}

			Vec3 target = null;
			for (int i = 0; i < 3; i++) {
				target = sampleCircle(activeOwner, 2.5D);

				//shift up from players feet
				target = target.add(0.0D, 2.0D, 0.0D);

				//apply gaussian blur
				target = target.add(this.rng.nextGaussian(), this.rng.nextGaussian(), this.rng.nextGaussian());

				// retry if target is blocking view of owner
				if (!isWithinOwnerNarrowFov(activeOwner, target)) {
					break;
				}
			}

			double distanceToTargetSqr = distanceToSqr(target);

			//random speed boost gets fed in linearly before the sqrting so it doesn't effect top speed/long range paths
			double speedBoost = this.rng.nextInt(8);
			double speed = Math.sqrt(Math.sqrt(distanceToTargetSqr + speedBoost)) / 2;

			getNavigation().moveTo(target.x, target.y, target.z, speed);
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
			final double distSqr = activeOwner.distanceToSqr(getCurrentTarget());
			//minimum ticks, half of this is also added pre randomization so distant points still get some randomization
			final int minTicks = 10;
			//max ticks before randomization, much higher when close
			final int maxTicks = (int) (2048 / Math.pow(distSqr + 8, 2)) + minTicks / 2;
			//randomize
			return this.rng.nextInt(maxTicks + 1) + minTicks;
		}

		private Vec3 getCurrentTarget() {
			BlockPos targetPos = getNavigation().getTargetPos();
			if (targetPos == null) {
				targetPos = blockPosition();
			}
			return Vec3.atCenterOf(targetPos);
		}
	}

	private final class ReturnToBaseGoal extends Goal {

		private ReturnToBaseGoal() {
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			// we don't need range/los checks because the follow owner goal does that and has priority
			// the goal is completed by docking so we can stop when docked
			return !isDocked();
		}

		@Override
		public void tick() {
			// Snap to base station position when docking finishes
			if (!getNavigation().isInProgress()) {
				snapToBase();
			}
		}

		@Override
		public void start() {
			double speed = 2.9D;
			Vec3 bsp = getBaseStationPos();
			if (bsp != null) getNavigation().moveTo(bsp.x, bsp.y, bsp.z, speed);
		}

		@Override
		public void stop() {
			getNavigation().stop();
		}
	}
}
