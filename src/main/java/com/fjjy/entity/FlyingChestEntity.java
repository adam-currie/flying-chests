package com.fjjy.entity;

import java.util.EnumSet;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.fjjy.FlyingChests;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class FlyingChestEntity extends PathfinderMob implements MenuProvider {
	private static final float MAX_OWNER_RANGE_FROM_BASE = 32.0F;

	private static final EntityDataAccessor<Boolean> IS_DOCKED =
		SynchedEntityData.defineId(FlyingChestEntity.class, EntityDataSerializers.BOOLEAN);

	private static final EntityDataAccessor<Byte> BASE_DIRECTION =
		SynchedEntityData.defineId(FlyingChestEntity.class, EntityDataSerializers.BYTE);

	private static final EntityDataAccessor<Integer> OPEN_COUNT =
		SynchedEntityData.defineId(FlyingChestEntity.class, EntityDataSerializers.INT);

	private UUID ownerUuid;
	private Vec3 baseStationPos;

	// Client-side lid animation (0.0 = closed, 1.0 = fully open)
	public float lidAngle;
	public float lidAngleO;

	private final SimpleContainer inventory = new SimpleContainer(27);

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
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(IS_DOCKED, true);
		builder.define(BASE_DIRECTION, (byte) 2); // Default to NORTH (2)
		builder.define(OPEN_COUNT, 0);
	}
	public Direction getBaseDirection() {
		return Direction.from3DDataValue(this.entityData.get(BASE_DIRECTION));
	}

	public void setBaseDirection(Direction dir) {
		this.entityData.set(BASE_DIRECTION, (byte) dir.get3DDataValue());
	}

	public boolean isDocked() {
		return this.entityData.get(IS_DOCKED);
	}

	public void setDocked(boolean docked) {
		this.entityData.set(IS_DOCKED, docked);
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
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			this.lidAngleO = this.lidAngle;
			if (this.entityData.get(OPEN_COUNT) > 0) {
				this.lidAngle = Math.min(1.0F, this.lidAngle + 0.1F);
			} else {
				this.lidAngle = Math.max(0.0F, this.lidAngle - 0.1F);
			}
		}
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!this.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
			serverPlayer.openMenu(this);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("container.chest");
	}

	@Override
	@Nullable
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		int newCount = this.entityData.get(OPEN_COUNT) + 1;
		this.entityData.set(OPEN_COUNT, newCount);
		if (newCount == 1) {
			this.playSound(SoundEvents.CHEST_OPEN, 0.5F, this.level().random.nextFloat() * 0.1F + 0.9F);
		}
		return new ChestMenu(MenuType.GENERIC_9x3, containerId, playerInventory, this.inventory, 3) {
			@Override
			public void removed(Player p) {
				super.removed(p);
				if (!FlyingChestEntity.this.isRemoved()) {
					int count = Math.max(0, FlyingChestEntity.this.entityData.get(OPEN_COUNT) - 1);
					FlyingChestEntity.this.entityData.set(OPEN_COUNT, count);
					if (count == 0) {
						FlyingChestEntity.this.playSound(SoundEvents.CHEST_CLOSE, 0.5F,
							FlyingChestEntity.this.level().random.nextFloat() * 0.1F + 0.9F);
					}
				}
			}
		};
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
	 * gets the owner player if they are between 5 and 32 blocks
	 */
	private Player getOwnerInFollowRange() {
		if (this.ownerUuid == null) {
			return null;
		}
		Player owner = this.level().getPlayerByUUID(this.ownerUuid);
		if (owner == null || !owner.isAlive()) {
			return null;
		}
		final var distSqr = owner.distanceToSqr(this.baseStationPos);
		return 
			distSqr <= (double) (MAX_OWNER_RANGE_FROM_BASE * MAX_OWNER_RANGE_FROM_BASE)
			&& distSqr > 25.0D // if owner is close just go to the base station instead.
				? owner
				: null; 
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
		return this.isDocked();
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
		output.store("OwnerUuid", Codec.STRING, this.ownerUuid.toString());
		output.store("BaseStationPos", Vec3.CODEC, this.baseStationPos);
		output.store("Items", ItemContainerContents.CODEC,
			ItemContainerContents.fromItems(this.inventory.getItems()));
		output.store("BaseDirection", Codec.BYTE, (byte) this.getBaseDirection().get3DDataValue());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.ownerUuid = input.read("OwnerUuid", Codec.STRING)
			.map(UUID::fromString)
			.orElse(null);
		this.baseStationPos = input.read("BaseStationPos", Vec3.CODEC)
			.orElse(null);
		input.read("Items", ItemContainerContents.CODEC)
			.ifPresent(contents -> contents.copyInto(this.inventory.getItems()));
		input.read("BaseDirection", Codec.BYTE)
			.ifPresent(b -> setBaseDirection(Direction.from3DDataValue(b)));
	}

	public static FlyingChestEntity spawnFromPlacement(ServerLevel level, BlockPos baseStationPos, Direction facing, Player owner) {
		FlyingChestEntity entity = new FlyingChestEntity(FlyingChests.FLYING_CHEST_ENTITY_TYPE, level);
		entity.baseStationPos = Vec3.atCenterOf(baseStationPos).subtract(0, 0.4, 0);
		final var yRot = facing.toYRot();
		entity.setBaseDirection(facing);
		entity.snapTo(entity.baseStationPos.x, entity.baseStationPos.y, entity.baseStationPos.z, facing.toYRot(), 0.0F);
		entity.yHeadRot = yRot;
		entity.finalizeSpawn((ServerLevelAccessor) level, level.getCurrentDifficultyAt(baseStationPos), EntitySpawnReason.MOB_SUMMONED, null);
		entity.ownerUuid = owner.getUUID();
		entity.setPersistenceRequired();
		level.addFreshEntity(entity);
		return entity;
	}

	private static final class FollowOwnerGoal extends Goal {
		private final FlyingChestEntity mob;
		private int ticksRemaining;
		private boolean skippedPrevDirectionUpdate = false;
		private final RandomSource rng;
		private boolean lookAtOwner = false;

		private FollowOwnerGoal(FlyingChestEntity mob) {
			this.mob = mob;
			this.rng = mob.getRandom();
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return this.mob.getOwnerInFollowRange() != null;
		}

		@Override
		public boolean canContinueToUse() {
			return this.mob.getOwnerInFollowRange() != null;
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
			Player owner = this.mob.getOwnerInFollowRange();

			if (owner == null) {
				return;
			}

			if (--this.ticksRemaining <= 0) {
				this.ticksRemaining = calculateTicksRemaining(owner);
				updateDirection(owner);
				// chance to look at owner (instead of the target)
				this.lookAtOwner = this.rng.nextInt(3) == 0; // 1 in 3
			}

			if (this.lookAtOwner) {
				this.mob.getLookControl().setLookAt(owner, 45.0F, 90.0F);
			}
		}

		private void updateDirection(@NotNull Player owner){
			{// if target/mob is close to player and not blocking their narrow fov, theres a chance to linger
				Vec3 currentTarget = getCurrentTarget();
				if (
					owner.distanceToSqr(currentTarget) < 16.0D 
					&& this.rng.nextBoolean() 
					&& !this.skippedPrevDirectionUpdate
					&& !isWithinOwnerNarrowFov(owner, currentTarget)
				) {
					this.skippedPrevDirectionUpdate = true;
					return;
				} else {
					this.skippedPrevDirectionUpdate = false;
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

			double distanceToTargetSqr = this.mob.distanceToSqr(target);

			//random speed boost gets fed in linearly before the sqrting so it doesn't effect top speed/long range paths
			double speedBoost = this.rng.nextInt(8);
			double speed = Math.sqrt(Math.sqrt(distanceToTargetSqr+speedBoost))/2;

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
			return !this.mob.isDocked();
		}

		@Override
		public boolean canContinueToUse() {
			return !this.mob.isDocked();
		}

		@Override
		public void tick() {
			if (!this.mob.getNavigation().isInProgress()) {
				// Snap to base station position when docking finishes
				this.mob.setPos(this.mob.baseStationPos);
				this.mob.setDeltaMovement(Vec3.ZERO);
				this.mob.setDocked(true);
			}
		}

		@Override
		public void start() {
			double speed = 2.9D;
			this.mob.getNavigation().moveTo(this.mob.baseStationPos.x, this.mob.baseStationPos.y, this.mob.baseStationPos.z, speed);
		}

		@Override
		public void stop() {
			this.mob.getNavigation().stop();
		}
	}
}